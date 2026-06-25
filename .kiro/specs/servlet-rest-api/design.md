# Design Document — servlet-rest-api

## Overview

This design describes the REST API layer that bridges the React/Vite frontend (port 5173) with
the OpenXava/PostgreSQL backend (port 8080, context `/Razonamiento`). The layer implements the
complete student-facing exam workflow for the **BFA: Razonamiento Forma A** psychometric test:

1. Session validation and question delivery
2. Student registration and answer-sheet initialization
3. Answer submission with automatic grading
4. Score retrieval

The API is implemented as four pure `javax.servlet.HttpServlet` classes in the package
`org.example.Razonamiento.api`, declared in `web.xml` and annotated with `@WebServlet`. A single
`CorsFilter` adds the required cross-origin headers to every response under `/api/*`.

No Spring or external JSON libraries (Jackson, Gson) are introduced. JSON is built using plain
string construction via a private `JsonBuilder` helper. The existing `JPAUtil`, `AutenticacionService`,
and `CorreccionAutomaticaService` are reused without modification.

---

## Architecture

### Deployment topology

```
React/Vite  (localhost:5173)
      │  HTTP/JSON (XHR / fetch)
      ▼
Tomcat (localhost:8080/Razonamiento)
  ├─ CorsFilter           → /api/*   (adds CORS headers, handles OPTIONS preflight)
  ├─ ExamenServlet        → /api/examen/preguntas  (GET)
  │                       → /api/examen/iniciar    (POST)
  ├─ RespuestasServlet    → /api/examen/guardar-respuestas (POST)
  └─ ResultadoServlet_API → /api/examen/resultado  (GET)
         │
         ▼ JPA (persistence unit "default")
       PostgreSQL
```

### Request lifecycle

```
Browser → [CorsFilter] → [HttpServlet.doGet/doPost] → [Service layer] → [JPA/PostgreSQL]
                                                              │
                                                     AutenticacionService
                                                     CorreccionAutomaticaService
```

1. Every incoming request under `/api/*` passes through `CorsFilter`, which adds the three CORS
   response headers unconditionally and short-circuits `OPTIONS` preflight requests with `200 OK`.
2. The matching servlet reads query parameters or parses the raw JSON request body.
3. Business logic is delegated to the existing service classes; direct JPA operations use
   `JPAUtil.createEntityManager()`.
4. The servlet writes the JSON response body via `PrintWriter` and sets the appropriate HTTP status
   code before returning.

### JPA access pattern

Each servlet follows the same transaction discipline copied from `PortalExamenServlet`:

```
EntityManager em = JPAUtil.createEntityManager();
EntityTransaction tx = em.getTransaction();
try {
    tx.begin();
    // ... mutations ...
    tx.commit();
} catch (Exception e) {
    if (tx.isActive()) tx.rollback();
    sendError(resp, 500, "Error interno ...");
} finally {
    em.close();
}
```

Read-only operations (`GET` endpoints) do not open transactions; they only call `em.find()` or
`em.createQuery()` and close the manager in a `finally` block.

---

## Components and Interfaces

### CorsFilter

| Attribute | Value |
|-----------|-------|
| Class | `org.example.Razonamiento.api.CorsFilter` |
| Implements | `javax.servlet.Filter` |
| Registration | `@WebFilter("/api/*")` + `<filter>` entry in `web.xml` |

**Responsibilities:**
- Add `Access-Control-Allow-Origin: http://localhost:5173`
- Add `Access-Control-Allow-Methods: GET, POST, OPTIONS`
- Add `Access-Control-Allow-Headers: Content-Type, Authorization`
- Respond with `200 OK` and return (skipping the filter chain) when method is `OPTIONS`
- Call `chain.doFilter(req, resp)` for all other methods

---

### ExamenServlet

| Attribute | Value |
|-----------|-------|
| Class | `org.example.Razonamiento.api.ExamenServlet` |
| Extends | `javax.servlet.http.HttpServlet` |
| URL pattern | `/api/examen/preguntas` (GET) and `/api/examen/iniciar` (POST) |
| Registration | `@WebServlet(name="ExamenServlet", urlPatterns={"/api/examen/preguntas", "/api/examen/iniciar"})` |

Both endpoints share this class because they concern the same "start exam" domain boundary. The
`doGet` / `doPost` override dispatches based on the request method.

**Dependencies (instantiated as instance fields):**
```java
private final AutenticacionService autenticacionService = new AutenticacionService();
```

**doGet — GET /api/examen/preguntas**

1. Read `codigoSesion` from query string.
2. Validate not blank → `400` if blank.
3. Call `autenticacionService.validarCodigoSesion(codigoSesion)` → `404` if null.
4. Query `PruebaRazonamientoFormaA` (first result) → `503` if none.
5. Serialize `limiteTiempoMinutos` and the question list (omitting `respuestaCorrecta`) → `200`.

**doPost — POST /api/examen/iniciar**

1. Parse JSON body into `codigoSesion`, `nombre`, `apellido`, `correo`.
2. Validate required fields (`codigoSesion`, `nombre`, `apellido`) → `400` if blank.
3. Call `autenticacionService.validarCodigoSesion(codigoSesion)` → `404` if null.
4. Open JPA transaction.
5. Re-attach `SesionPrueba` via `em.find`.
6. Find-or-create `Evaluado` by `correo`.
7. Create `HojaRespuesta`, set `horaInicio = LocalDateTime.now()`, persist.
8. For each `Pregunta` in the active `PruebaRazonamientoFormaA`, create `RespuestaDetalles`
   (`esOmitida = true`), link to `HojaRespuesta`, persist.
9. If session state is `PROGRAMADA`, set to `EN_PROGRESO` and merge.
10. Commit. Return `201` with `hojaRespuestaId`, `codigoSesion`, `limiteTiempoMinutos`.

---

### RespuestasServlet

| Attribute | Value |
|-----------|-------|
| Class | `org.example.Razonamiento.api.RespuestasServlet` |
| Extends | `javax.servlet.http.HttpServlet` |
| URL pattern | `/api/examen/guardar-respuestas` |
| Registration | `@WebServlet(name="RespuestasServlet", urlPatterns={"/api/examen/guardar-respuestas"})` |

**Dependencies:**
```java
private final CorreccionAutomaticaService correccionService = new CorreccionAutomaticaService();
```

**doPost flow:**

1. Parse JSON body: `hojaRespuestaId`, `horaFin`, `detalles[]`.
2. Validate `hojaRespuestaId` not blank → `400`.
3. Open JPA transaction.
4. `em.find(HojaRespuesta.class, UUID.fromString(hojaRespuestaId))` → `404` if null.
5. Iterate `detalles`: for each element call `rd.registrarRespuesta(opcionSeleccionada)` or
   mark `esOmitida = true` if `esOmitida` flag is true.
6. Set `hoja.setHoraFin(...)` from the received ISO-8601 `horaFin`.
7. Commit.
8. Call `correccionService.procesarHojaRespuesta(hoja)` → `500` if result is null.
9. Return `200` with `hojaRespuestaId`, `puntuacionDirecta`, `percentil`, `interpretacion`.

---

### ResultadoServlet_API

| Attribute | Value |
|-----------|-------|
| Class | `org.example.Razonamiento.api.ResultadoServlet_API` |
| Extends | `javax.servlet.http.HttpServlet` |
| URL pattern | `/api/examen/resultado` |
| Registration | `@WebServlet(name="ResultadoServlet_API", urlPatterns={"/api/examen/resultado"})` |

**doGet flow:**

1. Read `hojaRespuestaId` from query string → `400` if blank.
2. `em.find(HojaRespuesta.class, UUID.fromString(hojaRespuestaId))` → `404` if null.
3. `hoja.getResultado()` → `404` ("El resultado aún no está disponible") if null.
4. Return `200` with `hojaRespuestaId`, `puntuacionDirecta`, `percentil`, `interpretacion`.

---

### JsonBuilder (package-private utility)

| Attribute | Value |
|-----------|-------|
| Class | `org.example.Razonamiento.api.JsonBuilder` |
| Purpose | Centralise JSON string construction; no external library |

Key static methods:

```java
// Produce {"key":"value"} entries with proper escaping
static String str(String key, String value)
static String num(String key, int value)

// Wrap entries into a JSON object
static String obj(String... entries)

// Wrap a list of object strings into a JSON array
static String array(String key, List<String> items)

// Serialize UUID to standard string
static String uuid(UUID id)                   // "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"

// Serialize LocalDateTime to ISO-8601
static String iso(LocalDateTime dt)           // "yyyy-MM-dd'T'HH:mm:ss"

// Escape special characters for JSON string values
static String escape(String raw)
```

---

## Data Models

### Endpoint contracts

#### GET /api/examen/preguntas

**Request:**
```
GET /Razonamiento/api/examen/preguntas?codigoSesion=AB12CD34
```

**Response 200:**
```json
{
  "limiteTiempoMinutos": 10,
  "preguntas": [
    {
      "numero": 1,
      "serieIncompleta": "2, 4, 6, ?",
      "opcionA": "7",
      "opcionB": "8",
      "opcionC": "9",
      "opcionD": "10"
    }
  ]
}
```

> `respuestaCorrecta` is intentionally absent.

**Error responses:**
| Status | JSON body |
|--------|-----------|
| 400 | `{"error": "El parámetro codigoSesion es obligatorio"}` |
| 404 | `{"error": "Sesión no encontrada o no disponible"}` |
| 503 | `{"error": "La prueba no está configurada"}` |

---

#### POST /api/examen/iniciar

**Request body:**
```json
{
  "codigoSesion": "AB12CD34",
  "nombre": "Juan",
  "apellido": "Pérez",
  "correo": "juan@example.com"
}
```

**Response 201:**
```json
{
  "hojaRespuestaId": "550e8400-e29b-41d4-a716-446655440000",
  "codigoSesion": "AB12CD34",
  "limiteTiempoMinutos": 10
}
```

**Error responses:**
| Status | JSON body |
|--------|-----------|
| 400 | `{"error": "Campos obligatorios: codigoSesion, nombre, apellido"}` |
| 404 | `{"error": "Sesión no encontrada o no disponible"}` |
| 500 | `{"error": "Error interno al iniciar el examen"}` |

---

#### POST /api/examen/guardar-respuestas

**Request body:**
```json
{
  "hojaRespuestaId": "550e8400-e29b-41d4-a716-446655440000",
  "horaFin": "2024-06-15T10:45:00",
  "detalles": [
    { "numeroPregunta": 1, "opcionSeleccionada": "B", "esOmitida": false },
    { "numeroPregunta": 2, "opcionSeleccionada": "",  "esOmitida": true  }
  ]
}
```

**Response 200:**
```json
{
  "hojaRespuestaId": "550e8400-e29b-41d4-a716-446655440000",
  "puntuacionDirecta": 18,
  "percentil": 72,
  "interpretacion": "Promedio Alto (P 50-74)"
}
```

**Error responses:**
| Status | JSON body |
|--------|-----------|
| 400 | `{"error": "El campo hojaRespuestaId es obligatorio"}` |
| 404 | `{"error": "HojaRespuesta no encontrada"}` |
| 500 | `{"error": "Error al calcular el resultado"}` |
| 500 | `{"error": "Error interno al guardar respuestas"}` |

---

#### GET /api/examen/resultado

**Request:**
```
GET /Razonamiento/api/examen/resultado?hojaRespuestaId=550e8400-e29b-41d4-a716-446655440000
```

**Response 200:**
```json
{
  "hojaRespuestaId": "550e8400-e29b-41d4-a716-446655440000",
  "puntuacionDirecta": 18,
  "percentil": 72,
  "interpretacion": "Promedio Alto (P 50-74)"
}
```

**Error responses:**
| Status | JSON body |
|--------|-----------|
| 400 | `{"error": "El parámetro hojaRespuestaId es obligatorio"}` |
| 404 | `{"error": "HojaRespuesta no encontrada"}` |
| 404 | `{"error": "El resultado aún no está disponible"}` |

---

### Serialization rules

| Java type | JSON representation |
|-----------|-------------------|
| `UUID` | Standard string `"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"` via `uuid.toString()` |
| `LocalDateTime` | ISO-8601 string `"yyyy-MM-dd'T'HH:mm:ss"` via `DateTimeFormatter.ISO_LOCAL_DATE_TIME` |
| `int` / `long` | JSON number (no quotes) |
| `String` | JSON string with `\n`, `\r`, `\t`, `\"`, `\\`, and Unicode escaping |
| `char` (opcionSeleccionada) | Single-character JSON string `"B"` |
| `boolean` | JSON `true` / `false` |

### Flow diagram — full exam sequence

```
Frontend                          ExamenServlet        RespuestasServlet    ResultadoServlet_API
   │                                   │                      │                     │
   │── GET /preguntas?codigoSesion ───►│                      │                     │
   │                                   │── validarCodigo()    │                     │
   │◄─ 200 {preguntas[]} ─────────────│                      │                     │
   │                                   │                      │                     │
   │── POST /iniciar {nombre...} ─────►│                      │                     │
   │                                   │── find/create Evaluado                     │
   │                                   │── persist HojaRespuesta + Detalles         │
   │◄─ 201 {hojaRespuestaId} ─────────│                      │                     │
   │                                   │                      │                     │
   │── POST /guardar-respuestas ───────────────────────────►│                     │
   │                                            │── update RespuestaDetalles        │
   │                                            │── procesarHojaRespuesta()         │
   │◄── 200 {puntuacionDirecta, percentil} ────│                     │             │
   │                                                                  │             │
   │── GET /resultado?hojaRespuestaId ──────────────────────────────────────────►│
   │◄── 200 {puntuacionDirecta, percentil} ─────────────────────────────────────│
```

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a
system — essentially, a formal statement about what the system should do. Properties serve as the
bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Question serialization never exposes the correct answer

*For any* `PruebaRazonamientoFormaA` with any non-empty list of `Pregunta` instances, the JSON
string produced by the `ExamenServlet` serialization logic must contain the fields `numero`,
`serieIncompleta`, `opcionA`, `opcionB`, `opcionC`, and `opcionD` for each question, and must
**not** contain the key `"respuestaCorrecta"` anywhere in the response body.

**Validates: Requirements 2.2**

---

### Property 2: Blank session codes always yield a 400 response

*For any* string that is null, empty, or composed entirely of whitespace characters, submitting
it as the `codigoSesion` parameter to either `GET /api/examen/preguntas` or
`POST /api/examen/iniciar` must produce a response with HTTP status `400` and a JSON body
containing the `"error"` key.

**Validates: Requirements 2.4, 3.6**

---

### Property 3: Find-or-create Evaluado is idempotent on correo

*For any* valid `correo` string, calling `POST /api/examen/iniciar` twice with the same `correo`
must result in exactly one `Evaluado` record in the database with that `correo` — the second call
must find the existing record rather than create a duplicate.

**Validates: Requirements 3.2**

---

### Property 4: HojaRespuesta initialization covers all questions

*For any* `PruebaRazonamientoFormaA` containing N `Pregunta` instances, the `HojaRespuesta`
created by `POST /api/examen/iniciar` must have exactly N `RespuestaDetalles` records, each with
`esOmitida = true` and an `acierto` value of `0`.

**Validates: Requirements 3.3**

---

### Property 5: Session state transitions to EN_PROGRESO

*For any* `SesionPrueba` with state `PROGRAMADA`, after a successful call to
`POST /api/examen/iniciar`, the persisted `SesionPrueba` must have state `EN_PROGRESO`.

**Validates: Requirements 3.4**

---

### Property 6: Response UUID matches persisted HojaRespuesta

*For any* valid `POST /api/examen/iniciar` request, the `hojaRespuestaId` returned in the `201`
response must be a string representation of the UUID of the `HojaRespuesta` that was actually
persisted in the database.

**Validates: Requirements 3.5**

---

### Property 7: Answer details reflect submitted input

*For any* list of `detalles` submitted to `POST /api/examen/guardar-respuestas`, after a successful
save, each `RespuestaDetalles` record in the database must reflect the exact `opcionSeleccionada`
and `esOmitida` flag that was submitted for the corresponding `numeroPregunta`.

**Validates: Requirements 4.2**

---

### Property 8: Result response always contains all required fields

*For any* `Resultado` entity (with any `puntuacionDirecta`, `percentil`, and derived
`interpretacion`), the JSON response produced by both `POST /api/examen/guardar-respuestas` and
`GET /api/examen/resultado` must contain exactly the keys `hojaRespuestaId`,
`puntuacionDirecta`, `percentil`, and `interpretacion`, with the correct types (string UUID,
integer, integer, string).

**Validates: Requirements 4.4, 5.2**

---

### Property 9: UUID serialization always produces standard format

*For any* `java.util.UUID` value, the serialization method in `JsonBuilder` must produce a string
that matches the pattern `[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}`.

**Validates: Requirements 6.1**

---

### Property 10: ISO-8601 date serialization is correct for all dates

*For any* `java.time.LocalDateTime` value, the serialization method in `JsonBuilder` must produce a
string matching the pattern `\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}`, and parsing that string back
with `LocalDateTime.parse` must yield the original value (round-trip identity).

**Validates: Requirements 6.2**

---

### Property 11: All error responses carry the "error" key

*For any* request that triggers an error condition (blank parameter, entity not found, service
failure, malformed payload), every servlet in the `API_Layer` must return a JSON body that contains
the key `"error"` with a non-blank string value.

**Validates: Requirements 6.4**

---

### Property 12: JSON serialization round-trip

*For any* response object produced by the `API_Layer` (preguntas list, iniciar result, guardar
result, resultado), serializing it to a JSON string and then parsing that string to extract each
field must yield values equal to the original field values (i.e., no data is lost or corrupted
during serialization).

**Validates: Requirements 6.5**

---

## Error Handling

### Servlet-level error handling pattern

Every servlet uses a private helper method to write error responses consistently:

```java
private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
    resp.setStatus(status);
    resp.setContentType("application/json; charset=UTF-8");
    resp.getWriter().write(JsonBuilder.obj(JsonBuilder.str("error", message)));
}
```

This guarantees that the OWASP `<error-page>` entries in `web.xml` (which serve HTML) are never
reached for API paths, because the servlet always writes the response body before any exception
could propagate to the container's default error handling.

### Error classification

| Category | HTTP status | Trigger |
|----------|-------------|---------|
| Missing/blank parameter | `400` | `codigoSesion`, `hojaRespuestaId`, `nombre`, `apellido` blank |
| Malformed JSON body | `400` | JSON parse exception in `doPost` |
| Missing required field | `400` | `codigoSesion` absent in POST body |
| Entity not found | `404` | `em.find()` returns null; `AutenticacionService` returns null |
| Result not yet available | `404` | `hoja.getResultado()` is null |
| Service not configured | `503` | No `PruebaRazonamientoFormaA` exists |
| Grading failure | `500` | `CorreccionAutomaticaService` returns null |
| JPA transaction error | `500` | Any exception inside a JPA transaction (with rollback) |
| Unexpected runtime error | `500` | Catch-all around each doGet/doPost |

### UUID parsing guard

Both `RespuestasServlet` and `ResultadoServlet_API` receive a UUID as a string. Parsing is wrapped
in a try/catch for `IllegalArgumentException`:

```java
UUID id;
try {
    id = UUID.fromString(hojaRespuestaId);
} catch (IllegalArgumentException e) {
    sendError(resp, 400, "El campo hojaRespuestaId no es un UUID válido");
    return;
}
```

### Content-Type guarantee

Every response (success and error alike) sets `Content-Type: application/json; charset=UTF-8`
**before** writing the body. This is done at the top of each `doGet`/`doPost`:

```java
resp.setContentType("application/json; charset=UTF-8");
resp.setCharacterEncoding("UTF-8");
```

---

## Testing Strategy

### PBT applicability assessment

The API layer contains pure serialization logic (`JsonBuilder`), stateless validation logic
(blank-checking, UUID-format enforcement), and well-defined data transformations
(entity → JSON, payload → entity state). These are all excellent targets for property-based
testing. PBT is applicable and is included below.

The JPA integration and HTTP lifecycle are best covered by example-based unit tests using mocks
(`Mockito` or equivalent) for the `EntityManager` and service dependencies.

### Recommended testing library

**jqwik** (property-based testing for JUnit 5, JVM) — chosen because it integrates cleanly with
Maven/JUnit 5, requires no external process, and allows arbitraries over Java primitives, strings,
and custom domain objects. Configure each property to run a minimum of **100 tries**.

Each test must be tagged with a comment in the format:
```java
// Feature: servlet-rest-api, Property <N>: <property_text>
```

### Unit tests (example-based)

| Test class | What is covered |
|------------|-----------------|
| `CorsFilterTest` | Headers present on normal requests; `OPTIONS` returns 200 and halts chain |
| `ExamenServletGetTest` | 400 on blank code; 404 when service returns null; 503 when no prueba |
| `ExamenServletPostTest` | 400 on missing fields; 404 on invalid session; 201 on happy path |
| `RespuestasServletTest` | 400 on missing id; 404 on unknown hoja; 500 on null Resultado |
| `ResultadoServletApiTest` | 400 on blank id; 404 on missing hoja; 404 on missing resultado; 200 happy path |

### Property-based tests

| Test method | Property | Iterations |
|-------------|----------|-----------|
| `preguntasJsonNeverExposesRespuestaCorrecta` | Property 1 | 200 |
| `blankSessionCodeYields400` | Property 2 | 200 |
| `evaluadoFindOrCreateIsIdempotent` | Property 3 | 100 |
| `hojaRespuestaInitializesAllDetails` | Property 4 | 100 |
| `sessionTransitionsToEnProgreso` | Property 5 | 100 |
| `responseUuidMatchesPersistedHoja` | Property 6 | 100 |
| `answerDetailsReflectSubmittedInput` | Property 7 | 200 |
| `resultResponseContainsAllFields` | Property 8 | 100 |
| `uuidSerializationMatchesStandardFormat` | Property 9 | 500 |
| `dateSerializationRoundTrip` | Property 10 | 500 |
| `allErrorResponsesCarryErrorKey` | Property 11 | 200 |
| `jsonSerializationRoundTrip` | Property 12 | 200 |

### Integration tests

| Test | Coverage |
|------|----------|
| CORS headers present on `/api/preguntas` with live Tomcat | Requirements 1.2–1.4 |
| OPTIONS preflight returns 200 and no body | Requirement 1.5 |
| `Content-Type: application/json; charset=UTF-8` on all responses | Requirement 6.3 |

### Dual testing rationale

Property tests cover the *general* correctness of serialization, validation, and state transitions
across many randomly-generated inputs. Example-based unit tests cover specific HTTP interaction
scenarios (correct status codes, delegation to services, transaction rollback) where the interesting
variation is structural (mock returns null, transaction throws), not in the data values themselves.
Together they provide comprehensive coverage without redundancy.

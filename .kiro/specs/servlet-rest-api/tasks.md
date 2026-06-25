# Implementation Plan: servlet-rest-api

## Overview

Implement the pure Java Servlet REST API layer in the package `org.example.Razonamiento.api`.
The layer consists of five components — `CorsFilter`, `JsonBuilder`, `ExamenServlet`,
`RespuestasServlet`, and `ResultadoServlet_API` — registered in `web.xml` and wired to the
existing `AutenticacionService`, `CorreccionAutomaticaService`, and `JPAUTil`. Testing uses
**jqwik** (property-based, JUnit 5) alongside example-based unit tests with Mockito.

---

## Tasks

- [x] 0. Add jqwik and Mockito dependencies to `pom.xml`
  - Add to `<dependencies>` in `pom.xml`:
    ```xml
    <!-- JUnit 5 -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.10.2</version>
      <scope>test</scope>
    </dependency>
    <!-- jqwik property-based testing -->
    <dependency>
      <groupId>net.jqwik</groupId>
      <artifactId>jqwik</artifactId>
      <version>1.8.4</version>
      <scope>test</scope>
    </dependency>
    <!-- Mockito -->
    <dependency>
      <groupId>org.mockito</groupId>
      <artifactId>mockito-core</artifactId>
      <version>5.11.0</version>
      <scope>test</scope>
    </dependency>
    ```
  - In the `maven-surefire-plugin` configuration, change `<skipTests>true</skipTests>` to
    `<skipTests>false</skipTests>` and add JUnit Platform provider so tests run:
    ```xml
    <configuration>
      <skipTests>false</skipTests>
    </configuration>
    ```
  - _Requirements: 1.1 through 6.5 (prerequisite for all test tasks)_


- [x] 1. Create package structure, `web.xml` registrations, and `JsonBuilder` utility
  - Create the directory `src/main/java/org/example/Razonamiento/api/`
  - Create `JsonBuilder.java` in that package with static helpers:
    - `str(String key, String value)` — emits `"key":"escaped_value"`
    - `num(String key, Number value)` — emits `"key":number`
    - `obj(String... members)` — wraps members in `{ ... }`
    - `array(String key, String... items)` — emits `"key":[item1,item2,...]`
    - `uuid(UUID value)` — returns `value.toString()` (format `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`)
    - `iso(LocalDateTime value)` — returns `value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)` truncated to seconds
    - `escape(String value)` — escapes `"`, `\`, and control characters for safe JSON embedding
  - Add `<filter>` + `<filter-mapping>` for `CorsFilter` under `/api/*` in
    `src/main/webapp/WEB-INF/web.xml`
  - Add `<servlet>` + `<servlet-mapping>` entries for `ExamenServlet` (`/api/examen/preguntas`
    and `/api/examen/iniciar`), `RespuestasServlet` (`/api/examen/guardar-respuestas`), and
    `ResultadoServlet_API` (`/api/examen/resultado`) in `web.xml`
  - _Requirements: 6.1, 6.2, 6.3, 6.5_

  - [x] 1.1 Write property test for `JsonBuilder.uuid` — Property 9
    - **Property 9: UUID serialization always produces standard format**
    - **Validates: Requirements 6.1**
    - Create `src/test/java/org/example/Razonamiento/api/JsonBuilderTest.java`
    - Use jqwik `@Property` with `@ForAll UUID uuid`; call `JsonBuilder.uuid(uuid)`; assert
      result matches regex `[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}`;
      set `@Property(tries = 500)`
    - `// Feature: servlet-rest-api, Property 9: UUID serialization always produces standard format`

  - [x] 1.2 Write property test for `JsonBuilder.iso` — Property 10
    - **Property 10: ISO-8601 date serialization is correct for all dates**
    - **Validates: Requirements 6.2**
    - In `JsonBuilderTest`, add `@Property(tries = 500)` with `@ForAll` `LocalDateTime`
      (use jqwik `Arbitraries.of` or a custom `@Provide` method generating valid
      `LocalDateTime` values); call `JsonBuilder.iso(dt)`; assert result matches
      `\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}` and `LocalDateTime.parse(result)` equals the
      input truncated to seconds
    - `// Feature: servlet-rest-api, Property 10: ISO-8601 date serialization is correct for all dates`

  - [x] 1.3 Write property test for JSON escape round-trip — Property 12
    - **Property 12: JSON string escape is safe for arbitrary Unicode input**
    - **Validates: Requirements 6.5**
    - In `JsonBuilderTest`, generate arbitrary `String` values with
      `Arbitraries.strings().withCharRange('\u0000', '\uFFFF')`; call `JsonBuilder.escape(s)`;
      assert the result contains no unescaped `"` or `\` characters and that wrapping it in
      `"` + result + `"` is valid JSON (no parse error); min 200 tries
    - `// Feature: servlet-rest-api, Property 12: JSON string escape is safe for arbitrary Unicode input`


- [x] 2. Implement `CorsFilter`
  - Create `CorsFilter.java` in `org.example.Razonamiento.api` implementing
    `javax.servlet.Filter` annotated with `@WebFilter("/api/*")`
  - In `doFilter`: unconditionally set these three response headers:
    - `Access-Control-Allow-Origin: http://localhost:5173`
    - `Access-Control-Allow-Methods: GET, POST, OPTIONS`
    - `Access-Control-Allow-Headers: Content-Type, Authorization`
  - If `((HttpServletRequest) request).getMethod().equals("OPTIONS")`: write status `200` and
    return — do NOT call `chain.doFilter`
  - Otherwise call `chain.doFilter(request, response)`
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x] 2.1 Write unit tests for `CorsFilter`
    - Create `src/test/java/org/example/Razonamiento/api/CorsFilterTest.java`
    - Use Mockito to mock `HttpServletRequest`, `HttpServletResponse`, `FilterChain`
    - Test 1 (GET): set `request.getMethod()` → `"GET"`; call `filter.doFilter(...)`; verify
      all three CORS headers were set via `verify(response).setHeader(...)`; verify
      `chain.doFilter(request, response)` was called exactly once
    - Test 2 (OPTIONS): set `request.getMethod()` → `"OPTIONS"`; call `filter.doFilter(...)`;
      verify all three CORS headers were set; verify `response.setStatus(200)` was called;
      verify `chain.doFilter` was NOT called
    - _Requirements: 1.2, 1.3, 1.4, 1.5_


- [ ] 3. Implement `ExamenServlet` — GET `/api/examen/preguntas`
  - Create `ExamenServlet.java` in `org.example.Razonamiento.api` extending `HttpServlet` with
    `@WebServlet(name="ExamenServlet", urlPatterns={"/api/examen/preguntas", "/api/examen/iniciar"})`
  - Declare `private final AutenticacionService autenticacionService = new AutenticacionService();`
  - Add private helper `sendError(HttpServletResponse resp, int status, String message)` that:
    - calls `resp.setContentType("application/json; charset=UTF-8")`
    - calls `resp.setStatus(status)`
    - writes `{"error":"<escaped message>"}` to `resp.getWriter()`
  - In `doGet`:
    1. `resp.setContentType("application/json; charset=UTF-8")`
    2. Read `codigoSesion = req.getParameter("codigoSesion")`; if blank → `sendError(400, ...)`
    3. Call `autenticacionService.validarCodigoSesion(codigoSesion)`; if null → `sendError(404, ...)`
    4. Open `EntityManager em = JPAUTil.createEntityManager()` in a try-finally; query
       `"SELECT p FROM PruebaRazonamientoFormaA p"` with `setMaxResults(1)`; if empty list →
       close em, `sendError(503, ...)` and return
    5. Serialize response using `JsonBuilder`: `limiteTiempoMinutos` (int) and `preguntas`
       array where each element includes `numero`, `serieIncompleta`, `opcionA`, `opcionB`,
       `opcionC`, `opcionD` — **omit `respuestaCorrecta`**; write to `resp.getWriter()`; status 200
    6. Close em in `finally` block
  - Wrap the entire `doGet` body in `try/catch(Throwable t)` → `sendError(500, ...)`
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [ ] 3.1 Write property test for question serialization — Property 1
    - **Property 1: Question serialization never exposes the correct answer**
    - **Validates: Requirements 2.2**
    - Create `src/test/java/org/example/Razonamiento/api/ExamenServletGetTest.java`
    - Use jqwik `@Property(tries = 200)` with a `@Provide` method that generates
      `List<Pregunta>` with random `numero` (1–100), non-blank string fields, and a non-null
      `respuestaCorrecta` char from `{A, B, C, D}`
    - Extract the serialization logic from `ExamenServlet` into a package-visible static helper
      (or test it via a subclass); assert the resulting JSON string does NOT contain
      `"respuestaCorrecta"` and DOES contain `"numero"`, `"serieIncompleta"`, `"opcionA"`,
      `"opcionB"`, `"opcionC"`, `"opcionD"`
    - `// Feature: servlet-rest-api, Property 1: Question serialization never exposes the correct answer`

  - [ ] 3.2 Write property test for blank session codes — Property 2 (GET side)
    - **Property 2: Blank session codes always yield a 400 response**
    - **Validates: Requirements 2.4**
    - In `ExamenServletGetTest`, add `@Property(tries = 200)` generating blank/whitespace strings
      (including `null`) as `codigoSesion`; use Mockito to mock `HttpServletRequest` returning
      each generated value from `getParameter("codigoSesion")`; mock `HttpServletResponse` with
      a `StringWriter` captured via `getWriter()`; call `servlet.doGet(req, resp)`; assert
      `resp.getStatus()` is `400` and the captured body contains `"error"`
    - `// Feature: servlet-rest-api, Property 2: Blank session codes always yield a 400 response`

  - [ ] 3.3 Write unit tests for `ExamenServlet.doGet`
    - In `ExamenServletGetTest`, add JUnit 5 `@Test` methods (example-based):
      - `whenBlankCodigoSesion_returns400()`
      - `whenServiceReturnsNull_returns404()`
      - `whenNoPruebaConfigured_returns503()`
      - `whenHappyPath_returns200WithCorrectShape()` — assert JSON contains `limiteTiempoMinutos`
        and `preguntas` array, and does NOT contain `respuestaCorrecta`
    - Use Mockito for `HttpServletRequest`, `HttpServletResponse`, and spy/subclass for
      `AutenticacionService`
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_


- [ ] 4. Implement `ExamenServlet` — POST `/api/examen/iniciar`
  - Add `doPost` to `ExamenServlet`:
    1. `resp.setContentType("application/json; charset=UTF-8")`
    2. Read the request body as a String via `req.getReader()`; parse the JSON manually (no
       external library) to extract `codigoSesion`, `nombre`, `apellido`, `correo`
    3. Validate `codigoSesion`, `nombre`, `apellido` not blank → `sendError(400, "Campos obligatorios: codigoSesion, nombre, apellido")`
    4. Call `autenticacionService.validarCodigoSesion(codigoSesion)` → if null `sendError(404, ...)`
    5. Open `EntityManager em = JPAUTil.createEntityManager()`; begin transaction
    6. Query `PruebaRazonamientoFormaA` (first result, same pattern as `doGet`) → if none,
       rollback, close em, `sendError(503, ...)`
    7. Find `SesionPrueba sesion = em.find(SesionPrueba.class, validatedSesion.getId())`
    8. Find-or-create `Evaluado` by `correo`:
       - Try `em.createQuery("SELECT e FROM Evaluado e WHERE e.correo = :c", Evaluado.class)
          .setParameter("c", correo).getSingleResult()`
       - On `NoResultException`: create new `Evaluado`, set `nombre`, `apellido`, `correo`,
         persist it
    9. Create `HojaRespuesta`, set `evaluado`, `sesionPrueba = sesion`, call `hoja.iniciarPrueba()`
       (sets `horaInicio = LocalDateTime.now()`), persist
    10. For each `Pregunta` in `prueba.getPreguntas()`: create `RespuestaDetalles`, set
        `hojaRespuesta = hoja`, `pregunta`, `esOmitida = true`, persist
    11. If `sesion.getEstado() == EstadoSesion.PROGRAMADA`: call
        `sesion.setEstado(EstadoSesion.EN_PROGRESO)` and `em.merge(sesion)`
    12. `tx.commit()`; return `201` with JSON:
        `{"hojaRespuestaId":"<uuid>","codigoSesion":"<code>","limiteTiempoMinutos":<int>}`
    13. On any `Exception` in the transaction block: `if (tx.isActive()) tx.rollback();`
        close em, `sendError(500, "Error interno al iniciar el examen")`
    14. Close em in `finally`
  - Wrap the entire `doPost` body in `try/catch(Throwable t)` → `sendError(500, ...)`
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9_

  - [ ] 4.1 Write property test for blank session codes — Property 2 (POST side)
    - **Property 2: Blank session codes always yield a 400 response**
    - **Validates: Requirements 3.6**
    - Create `src/test/java/org/example/Razonamiento/api/ExamenServletPostTest.java`
    - Use jqwik `@Property(tries = 200)` generating blank/whitespace/null strings for
      `codigoSesion` in a minimal JSON body `{"codigoSesion":"<blank>","nombre":"A","apellido":"B"}`
    - Assert HTTP status `400` and body contains `"error"`
    - `// Feature: servlet-rest-api, Property 2: Blank session codes always yield a 400 response`

  - [ ] 4.2 Write property test for find-or-create idempotency — Property 3
    - **Property 3: Find-or-create Evaluado is idempotent on correo**
    - **Validates: Requirements 3.2**
    - Extract the find-or-create logic into a package-visible static method
      `findOrCreateEvaluado(EntityManager em, String correo, String nombre, String apellido)`
    - In `ExamenServletPostTest`, use jqwik `@Property(tries = 100)` generating valid
      `correo` strings; using an in-memory H2 `EntityManager` (or Mockito), call the method
      twice with the same `correo`; assert only one `Evaluado` record exists with that `correo`
    - `// Feature: servlet-rest-api, Property 3: Find-or-create Evaluado is idempotent on correo`

  - [ ] 4.3 Write property test for HojaRespuesta initialization — Property 4
    - **Property 4: HojaRespuesta initialization covers all questions**
    - **Validates: Requirements 3.3**
    - In `ExamenServletPostTest`, use jqwik `@Property(tries = 100)` generating a
      `PruebaRazonamientoFormaA` with N `Pregunta` instances (N ∈ [1, 50]); invoke the
      initialization loop from task 4 step 10; assert exactly N `RespuestaDetalles` were
      created, each with `esOmitida == true` and a non-null `pregunta` reference
    - `// Feature: servlet-rest-api, Property 4: HojaRespuesta initialization covers all questions`

  - [ ] 4.4 Write property test for session state transition — Property 5
    - **Property 5: Session state transitions to EN_PROGRESO only from PROGRAMADA**
    - **Validates: Requirements 3.4**
    - In `ExamenServletPostTest`, use jqwik `@Property(tries = 100)` with `@ForAll` drawing
      from all `EstadoSesion` values; apply the state-transition logic; assert that the state
      is `EN_PROGRESO` only when the starting state was `PROGRAMADA`, and is unchanged
      otherwise
    - `// Feature: servlet-rest-api, Property 5: Session state transitions to EN_PROGRESO`

  - [ ] 4.5 Write property test for response UUID matching persisted hoja — Property 6
    - **Property 6: Response UUID matches persisted HojaRespuesta**
    - **Validates: Requirements 3.5**
    - In `ExamenServletPostTest`, use jqwik `@Property(tries = 100)` generating a fresh
      `HojaRespuesta` with a random `UUID` id; serialize to JSON with `JsonBuilder.uuid(id)`;
      assert the string in the JSON at key `hojaRespuestaId` equals `id.toString()`
    - `// Feature: servlet-rest-api, Property 6: Response UUID matches persisted HojaRespuesta`

  - [ ] 4.6 Write unit tests for `ExamenServlet.doPost`
    - In `ExamenServletPostTest`, add JUnit 5 `@Test` methods:
      - `whenMissingRequiredFields_returns400()`
      - `whenInvalidSession_returns404()`
      - `whenHappyPath_returns201WithCorrectKeys()` — assert JSON body contains
        `hojaRespuestaId`, `codigoSesion`, `limiteTiempoMinutos`
      - `whenJpaThrows_returns500AndRollsBack()` — mock `EntityManager.persist` to throw;
        verify `tx.rollback()` was called
    - _Requirements: 3.1, 3.5, 3.6, 3.7, 3.8_


- [ ] 5. Checkpoint — Ensure all tests pass
  - Run tests from the IDE (do NOT use `mvn`); confirm all property-based and unit tests from
    tasks 1–4 are green before continuing
  - Fix any compilation or assertion failures before proceeding to task 6

- [ ] 6. Implement `RespuestasServlet` — POST `/api/examen/guardar-respuestas`
  - Create `RespuestasServlet.java` in `org.example.Razonamiento.api` extending `HttpServlet`
    with `@WebServlet(name="RespuestasServlet", urlPatterns={"/api/examen/guardar-respuestas"})`
  - Declare `private final CorreccionAutomaticaService correccionService = new CorreccionAutomaticaService();`
  - Add the same `sendError` helper as in `ExamenServlet` (or extract to a shared base class)
  - In `doPost`:
    1. `resp.setContentType("application/json; charset=UTF-8")`
    2. Parse JSON body to extract `hojaRespuestaId` (String), `horaFin` (String, ISO-8601),
       and `detalles` array (each element: `numeroPregunta` int, `opcionSeleccionada` char,
       `esOmitida` boolean)
    3. If `hojaRespuestaId` blank → `sendError(400, "El campo hojaRespuestaId es obligatorio")`
    4. Parse UUID: `UUID id = UUID.fromString(hojaRespuestaId)` in try/catch
       `IllegalArgumentException` → `sendError(400, "hojaRespuestaId con formato inválido")`
    5. Open em; begin transaction; `em.find(HojaRespuesta.class, id)` → if null rollback, close
       em, `sendError(404, "HojaRespuesta no encontrada")`
    6. For each element in `detalles`: find the matching `RespuestaDetalles` by
       `numeroPregunta`; if `esOmitida` is true call `rd.setEsOmitida(true)`; otherwise call
       `rd.registrarRespuesta(opcionSeleccionada)`; `em.merge(rd)`
    7. If `horaFin` not blank: `hoja.setHoraFin(LocalDateTime.parse(horaFin))`; `em.merge(hoja)`
    8. `tx.commit()`; close em
    9. Call `correccionService.procesarHojaRespuesta(hoja)`; if result is null →
       `sendError(500, "Error al calcular el resultado")`
    10. Return `200` with JSON:
        `{"hojaRespuestaId":"<uuid>","puntuacionDirecta":<int>,"percentil":<int>,"interpretacion":"<string>"}`
        — note: `interpretacion` comes from `resultado.getInterpretacion()` (computed `@Transient`)
    11. On JPA `Exception`: rollback, close em, `sendError(500, "Error interno al guardar respuestas")`
    12. Close em in `finally`
  - Wrap the entire `doPost` body in `try/catch(Throwable t)` → `sendError(500, ...)`
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9_

  - [ ] 6.1 Write property test for answer details reflecting submitted input — Property 7
    - **Property 7: Answer details reflect submitted input**
    - **Validates: Requirements 4.2**
    - Create `src/test/java/org/example/Razonamiento/api/RespuestasServletTest.java`
    - Use jqwik `@Property(tries = 200)` generating lists of `(opcionSeleccionada char, esOmitida boolean)` pairs; create mock `RespuestaDetalles` objects; apply the update logic; assert each detail's `opcionSeleccionada` and `esOmitida` match the submitted values
    - `// Feature: servlet-rest-api, Property 7: Answer details reflect submitted input`

  - [ ] 6.2 Write property test for result response fields — Property 8 (guardar side)
    - **Property 8: Result response always contains all required fields**
    - **Validates: Requirements 4.4**
    - In `RespuestasServletTest`, use jqwik `@Property(tries = 100)` generating arbitrary
      `Resultado` objects (random `puntuacionDirecta` 0–36, `percentil` 0–99); serialize via
      the `RespuestasServlet` response logic; assert JSON contains keys `hojaRespuestaId`
      (UUID string), `puntuacionDirecta` (int), `percentil` (int), `interpretacion` (non-blank string)
    - `// Feature: servlet-rest-api, Property 8: Result response always contains all required fields`

  - [ ] 6.3 Write unit tests for `RespuestasServlet`
    - In `RespuestasServletTest`, add JUnit 5 `@Test` methods:
      - `whenMissingHojaRespuestaId_returns400()`
      - `whenMalformedUUID_returns400()`
      - `whenHojaNotFound_returns404()`
      - `whenCorreccionReturnsNull_returns500()`
      - `whenHappyPath_returns200WithCorrectKeys()`
    - _Requirements: 4.1, 4.4, 4.5, 4.6, 4.7, 4.8_


- [ ] 7. Implement `ResultadoServlet_API` — GET `/api/examen/resultado`
  - Create `ResultadoServlet_API.java` in `org.example.Razonamiento.api` extending `HttpServlet`
    with `@WebServlet(name="ResultadoServlet_API", urlPatterns={"/api/examen/resultado"})`
  - Add the same `sendError` helper
  - In `doGet`:
    1. `resp.setContentType("application/json; charset=UTF-8")`
    2. Read `hojaRespuestaId = req.getParameter("hojaRespuestaId")`; if blank →
       `sendError(400, "El parámetro hojaRespuestaId es obligatorio")`
    3. Parse UUID in try/catch `IllegalArgumentException` → `sendError(400, "hojaRespuestaId con formato inválido")`
    4. Open em; `em.find(HojaRespuesta.class, id)` → if null close em, `sendError(404, "HojaRespuesta no encontrada")`
    5. `hoja.getResultado()` → if null close em, `sendError(404, "El resultado aún no está disponible")`
    6. Return `200` with JSON:
       `{"hojaRespuestaId":"<uuid>","puntuacionDirecta":<int>,"percentil":<int>,"interpretacion":"<string>"}`
       — `interpretacion` from `resultado.getInterpretacion()`
    7. Close em in `finally`
  - Wrap the entire `doGet` body in `try/catch(Throwable t)` → `sendError(500, ...)`
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

  - [ ] 7.1 Write property test for result response fields — Property 8 (resultado side)
    - **Property 8: Result response always contains all required fields**
    - **Validates: Requirements 5.2**
    - Create `src/test/java/org/example/Razonamiento/api/ResultadoServletApiTest.java`
    - Use jqwik `@Property(tries = 100)` generating arbitrary `Resultado` objects (random
      `puntuacionDirecta`, `percentil`); serialize via the `ResultadoServlet_API` response
      logic; assert JSON contains `hojaRespuestaId`, `puntuacionDirecta`, `percentil`,
      `interpretacion` with correct types
    - `// Feature: servlet-rest-api, Property 8: Result response always contains all required fields`

  - [ ] 7.2 Write unit tests for `ResultadoServlet_API`
    - In `ResultadoServletApiTest`, add JUnit 5 `@Test` methods:
      - `whenBlankHojaId_returns400()`
      - `whenMalformedUUID_returns400()`
      - `whenHojaNotFound_returns404()`
      - `whenResultadoNotYetAvailable_returns404WithMessage()`
      - `whenHappyPath_returns200WithCorrectShape()`
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [ ] 8. Implement error-response uniformity across all servlets — Property 11
  - Review every `doGet`/`doPost` in all three servlets; confirm every error path (400, 404,
    500, 503) calls `sendError(resp, status, message)` and that `sendError` always:
    - Sets `Content-Type: application/json; charset=UTF-8` before writing
    - Writes a body of the form `{"error":"<message>"}`
  - Confirm the catch-all `try/catch(Throwable t)` block is present at the outer level of each
    handler so no unhandled exception can leak an HTML error page
  - _Requirements: 6.3, 6.4_

  - [ ] 8.1 Write property test for error response uniformity — Property 11
    - **Property 11: All error responses carry the "error" key**
    - **Validates: Requirements 6.4**
    - Create `src/test/java/org/example/Razonamiento/api/ErrorUniformityTest.java`
    - For each of the three servlets, parameterize over all error-triggering input classes
      (blank param, null service return, JPA throw, malformed UUID); for each case assert:
      - `resp.getContentType()` contains `application/json`
      - The response body parses as JSON containing key `"error"` with a non-blank string value
    - Use `@ParameterizedTest` or jqwik `@Property(tries = 200)` as appropriate
    - `// Feature: servlet-rest-api, Property 11: All error responses carry the "error" key`

- [ ] 9. Final checkpoint — Ensure all tests pass
  - Run all tests from the IDE; confirm every property test and unit test from tasks 1–8 is green
  - Fix any failures before declaring the feature complete


---

## Notes

- The utility class is `JPAUTil` (capital T, lowercase i, lowercase l) — match this exactly in imports
- `Resultado.getInterpretacion()` is a `@Transient` computed getter — it is NOT stored in the DB; serialize its return value directly
- `RespuestaDetalles` is a full `@Entity` (not `@Embeddable`) — use `em.persist()` / `em.merge()` individually for each detail
- `HojaRespuesta.iniciarPrueba()` sets `horaInicio = LocalDateTime.now()` — call this instead of setting the field directly
- `HojaRespuesta` uses `@OneToMany(mappedBy = "hojaRespuesta")` for `respuestasDetalle` — you must set the back-reference `rd.setHojaRespuesta(hoja)` before persisting each `RespuestaDetalles`
- The design mandates **no Spring and no external JSON libraries** — all JSON is built via `JsonBuilder`
- Do NOT run tests with `mvn` — run from the IDE
- The UUID parse guard (`IllegalArgumentException` → 400) is required in both `RespuestasServlet` and `ResultadoServlet_API`
- `Content-Type: application/json; charset=UTF-8` must be set **before** writing any response body in every doGet/doPost
- Each property test must include the comment tag `// Feature: servlet-rest-api, Property N: ...`

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["0"] },
    { "id": 1, "tasks": ["1", "1.1", "1.2", "1.3"] },
    { "id": 2, "tasks": ["2", "2.1"] },
    { "id": 3, "tasks": ["3", "3.1", "3.2", "3.3"] },
    { "id": 4, "tasks": ["4", "4.1", "4.2", "4.3", "4.4", "4.5", "4.6"] },
    { "id": 5, "tasks": ["5"] },
    { "id": 6, "tasks": ["6", "6.1", "6.2", "6.3"] },
    { "id": 7, "tasks": ["7", "7.1", "7.2", "8", "8.1"] },
    { "id": 8, "tasks": ["9"] }
  ]
}
```

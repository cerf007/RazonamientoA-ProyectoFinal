# Design Document: student-portal-validation

## Overview

Este documento describe el diseño técnico para transformar `PortalExamen.jsx` de modo mock a un portal de examen funcional conectado al backend Java/OpenXava en `http://localhost:8080/Razonamiento`.

El flujo completo comprende tres etapas en cadena:

1. **IdentityForm** — captura y valida los datos del estudiante en el frontend, aplica la validación cruzada cédula/fecha de nacimiento en el módulo `Validator`, y llama a `POST /api/examen/iniciar` en el backend, que re-valida la misma regla.
2. **ExamView** — muestra las preguntas recibidas de `GET /api/examen/preguntas`, presenta el `Timer` de cuenta regresiva con estado de urgencia, y permite selección/deselección libre de respuestas.
3. **ResultsScreen** — muestra `puntuacionDirecta`, `percentil` e `interpretacion` tras el envío exitoso a `POST /api/examen/guardar-respuestas`.

El componente `PortalExamen.jsx` se refactoriza en sub-componentes con responsabilidades claras y se conecta al backend a través del proxy Vite ya configurado (`/api` → `http://localhost:8080/Razonamiento`). En el backend se introduce un nuevo servlet `ExamenApiServlet` en el paquete `org.example.Razonamiento.rest` junto con un `CorsFilter`, manteniendo los patrones establecidos en los servlets existentes (`PortalExamenServlet`, `PruebaServlet`).

---

## Architecture

### Diagrama de flujo general

```mermaid
sequenceDiagram
    actor Student as Estudiante
    participant IF as IdentityForm
    participant V as Validator (puro)
    participant API as fetch() / Proxy Vite
    participant BE as ExamenApiServlet (Java)
    participant DB as JPA / PostgreSQL

    Student->>IF: Llena campos + Submit
    IF->>IF: Validación de campos vacíos
    IF->>IF: Validación formato cédula
    IF->>IF: Validación formato correo
    IF->>V: validateCedula(cedula, fechaNacimiento)
    V-->>IF: válido / inválido
    IF->>API: GET /api/examen/preguntas?codigoSesion=...
    API->>BE: GET /api/examen/preguntas
    BE->>DB: AutenticacionService + PruebaRazonamientoFormaA
    DB-->>BE: preguntas + limiteTiempoMinutos
    BE-->>API: 200 {preguntas, limiteTiempoMinutos}
    API->>BE: POST /api/examen/iniciar {nombre, apellido, cedula, ...}
    BE->>BE: Re-validar cédula/fechaNacimiento
    BE->>DB: Crear Evaluado + HojaRespuesta
    DB-->>BE: hojaRespuestaId
    BE-->>API: 201 {hojaRespuestaId, limiteTiempoMinutos}
    API-->>IF: ok
    IF->>IF: Transición a ExamView (Timer inicia)

    Student->>IF: Selecciona respuestas
    Note over IF: Timer corre; selección/deselección libre

    alt Manual submit o Timer expira
        IF->>API: POST /api/examen/guardar-respuestas {payload}
        API->>BE: RespuestasServlet.doPost
        BE->>DB: Actualizar RespuestaDetalles + CorreccionAutomaticaService
        DB-->>BE: Resultado {pd, percentil, interpretacion}
        BE-->>API: 200 {puntuacionDirecta, percentil, interpretacion}
        API-->>IF: ok
        IF->>IF: Transición a ResultsScreen
    end
```

### Decisiones de arquitectura

| Decisión | Elección | Rationale |
|---|---|---|
| Estado global del portal | `useReducer` en `App.jsx` | El flujo tiene 4 pantallas (`identity`, `loading`, `exam`, `results`) con transiciones explícitas; un reducer es más seguro que múltiples `useState` |
| Módulo Validator | Función pura en `src/utils/validator.js` | Facilita las pruebas unitarias/property sin montar componentes React |
| Proxy CORS | Vite proxy ya configurado (`/api → :8080/Razonamiento`) | No requiere cambios de configuración; el backend solo necesita `CorsFilter` para preflight OPTIONS |
| Backend: nuevo paquete `rest` | `org.example.Razonamiento.rest` | Separa los servlets REST de los servlets JSP existentes en `servelet/` sin modificar código ya funcional |
| Serialización JSON | `javax.json` (API incluida en `javax.servlet`) o `org.json` | El proyecto ya usa Java EE; se usa `org.json` 20240303 añadido como dependencia en `pom.xml` si no está disponible |
| Payload `guardar-respuestas` | Incluye `hojaRespuestaId`, `horaFin`, `detalles[]` | El backend ya tiene `RespuestasServlet` documentado en el spec `servlet-rest-api`; se alinea con ese contrato |

---

## Components and Interfaces

### Frontend (React / Vite)

#### Estructura de archivos resultante

```
frontend-react/src/
├── App.jsx                    ← orchestrator; gestiona la vista activa y el estado global
├── PortalExamen.jsx           ← re-exporta App (punto de entrada compatible con el uso actual)
├── utils/
│   └── validator.js           ← Validator: funciones puras de validación
├── components/
│   ├── IdentityForm.jsx       ← formulario de datos personales
│   ├── ExamView.jsx           ← vista de la prueba con Timer y preguntas
│   ├── Timer.jsx              ← contador MM:SS con estado de urgencia
│   └── ResultsScreen.jsx      ← pantalla de resultado final
└── hooks/
    └── useSubmitHandler.js    ← lógica de envío con guard de duplicados
```

#### `validator.js` — interfaz pública

```js
// Retorna { valid: boolean, error: string|null }
export function validateCedula(cedula: string): ValidationResult

// Retorna { valid: boolean, error: string|null }
export function validateEmail(email: string): ValidationResult

// Retorna { valid: boolean, error: string|null }
// Compara Bloque_Central de cedula con DDMMYY de fechaNacimiento (Date)
export function validateCedulaFechaCruzada(cedula: string, fechaNacimiento: Date): ValidationResult

// Retorna string "DDMMYY" con zero-padding
export function formatFechaDDMMYY(fechaNacimiento: Date): string

// Extrae 6 chars en posiciones 4-9 tras quitar guiones
export function extractBloqueCentral(cedula: string): string
```

#### `useSubmitHandler.js` — interfaz pública

```js
// Retorna { submit, isSubmitting }
// submit(respuestas, hojaRespuestaId) → Promise<void>
// Guard interno: ignora llamadas mientras isSubmitting === true
export function useSubmitHandler(onSuccess: (result) => void, onError: (msg) => void)
```

#### `Timer.jsx` — props

```js
{
  totalSeconds: number,      // limiteTiempoMinutos * 60
  onExpire: () => void,      // callback cuando llega a 0
  frozen: boolean            // cuando Estado_Bloqueado; detiene el tick y congela en 00:00
}
```

#### Estado global (`useReducer` en `App.jsx`)

```js
{
  view: 'identity' | 'loading' | 'exam' | 'results',
  preguntas: Pregunta[],
  hojaRespuestaId: string | null,
  limiteTiempoMinutos: number | null,
  respuestas: Record<number, { opcionSeleccionada: string, esOmitida: boolean }>,
  resultado: { puntuacionDirecta: number, percentil: number, interpretacion: string } | null,
  error: string | null,
  isBlocked: boolean
}
```

#### Acciones del reducer

```
SET_LOADING
SET_PREGUNTAS_Y_HOJA  — payload: { preguntas, hojaRespuestaId, limiteTiempoMinutos }
SET_EXAM_VIEW
SELECT_OPTION         — payload: { numeroPregunta, opcion }
DESELECT_OPTION       — payload: { numeroPregunta }
BLOCK                 — congela la UI
SET_RESULTADO         — payload: { puntuacionDirecta, percentil, interpretacion }
SET_ERROR             — payload: string
```

### Backend (Java / OpenXava)

#### Nuevos archivos en `src/main/java/org/example/Razonamiento/rest/`

| Clase | Patrón URL | Responsabilidad |
|---|---|---|
| `CorsFilter` | `/api/*` | Agrega headers CORS para `http://localhost:5173`; responde 200 a preflight OPTIONS |
| `ExamenApiServlet` | `/api/examen/preguntas` (GET), `/api/examen/iniciar` (POST) | Entrega preguntas; registra evaluado + hoja de respuestas con re-validación de cédula |
| `RespuestasApiServlet` | `/api/examen/guardar-respuestas` (POST) | Persiste respuestas y delega a `CorreccionAutomaticaService` |
| `ResultadoApiServlet` | `/api/examen/resultado` (GET) | Consulta resultado por `hojaRespuestaId` (ya cubierto en spec `servlet-rest-api`) |

#### `CorsFilter` — lógica

```java
@WebFilter(urlPatterns = "/api/*")
public class CorsFilter implements Filter {
    // Agrega Access-Control-Allow-Origin: http://localhost:5173
    // Agrega Access-Control-Allow-Methods: GET, POST, OPTIONS
    // Agrega Access-Control-Allow-Headers: Content-Type, Authorization
    // IF método == OPTIONS → response.setStatus(200); return (no chain.doFilter)
    // ELSE → chain.doFilter(request, response)
}
```

#### `ExamenApiServlet` — lógica de re-validación cédula (Requisito 7)

```java
// En doPost("/api/examen/iniciar"):
// 1. Parsear JSON body
// 2. Validar presencia de cedula y fechaNacimiento → 400 si ausentes
// 3. String sinGuiones = cedula.replace("-", "")
//    String bloqueCentral = sinGuiones.substring(4, 10)
// 4. LocalDate fecha = LocalDate.parse(fechaNacimiento, ISO_LOCAL_DATE)
//    String ddmmyy = String.format("%02d%02d%02d",
//        fecha.getDayOfMonth(), fecha.getMonthValue(), fecha.getYear() % 100)
// 5. IF !bloqueCentral.equals(ddmmyy) → 422 {"error": "La fecha de nacimiento no coincide..."}
// 6. Continuar con AutenticacionService + HojaRespuesta (patrón de PortalExamenServlet)
// 7. Retornar 201 { hojaRespuestaId, codigoSesion, limiteTiempoMinutos }
```

Los servlets utilizan `JPAUTil.createEntityManager()`, `AutenticacionService` y `CorreccionAutomaticaService` exactamente como lo hacen `PortalExamenServlet` y `PruebaServlet`. El patrón de transacción (`tx.begin()` / `tx.commit()` / `tx.rollback()` en catch) se replica sin cambios.

---

## Data Models

### Frontend: tipos de datos en tiempo de ejecución

```ts
// Recibido de GET /api/examen/preguntas
interface Pregunta {
  numero: number
  serieIncompleta: string
  opcionA: string
  opcionB: string
  opcionC: string
  opcionD: string
  // respuestaCorrecta NO se incluye
}

interface PreguntasResponse {
  limiteTiempoMinutos: number
  preguntas: Pregunta[]
}

// Enviado a POST /api/examen/iniciar
interface IniciarPayload {
  codigoSesion: string
  nombre: string
  apellido: string
  correo: string
  cedula: string
  fechaNacimiento: string  // ISO-8601 "YYYY-MM-DD"
}

// Recibido de POST /api/examen/iniciar
interface IniciarResponse {
  hojaRespuestaId: string  // UUID string
  codigoSesion: string
  limiteTiempoMinutos: number
}

// Enviado a POST /api/examen/guardar-respuestas
interface GuardarPayload {
  hojaRespuestaId: string
  horaFin: string          // ISO-8601 "YYYY-MM-DDTHH:mm:ss.sssZ"
  detalles: DetalleRespuesta[]
}

interface DetalleRespuesta {
  numeroPregunta: number
  opcionSeleccionada: string  // "A"|"B"|"C"|"D"|""
  esOmitida: boolean
}

// Recibido de POST /api/examen/guardar-respuestas
interface ResultadoResponse {
  hojaRespuestaId: string
  puntuacionDirecta: number
  percentil: number
  interpretacion: string
}
```

### Backend: entidades JPA afectadas

Las entidades existentes no se modifican. La tabla siguiente resume qué campos escribe cada nuevo servlet:

| Entidad | Campo(s) escritos por ExamenApiServlet | Notas |
|---|---|---|
| `Evaluado` | `nombre`, `apellido`, `correo`, `cedula` (nuevo campo a agregar) | Se busca por correo antes de crear |
| `HojaRespuesta` | `evaluado`, `sesionPrueba`, `horaInicio` | vía `hoja.iniciarPrueba()` |
| `RespuestaDetalles` | `esOmitida=true`, `opcionSeleccionada='\0'` | Una entrada por pregunta al iniciar |
| `SesionPrueba` | `estado → EN_PROGRESO` | Solo si estaba en `PROGRAMADA` |

> **Campo `cedula` en `Evaluado`**: Se agrega `@Column(name="cedula", length=16) String cedula;` siguiendo las convenciones de `AGENTS.md` (package access, Lombok). Este campo no es obligatorio en el código existente; se popula desde la llamada REST nueva.

---

## Correctness Properties

*Una propiedad es una característica o comportamiento que debe mantenerse verdadero en todas las ejecuciones válidas del sistema — esencialmente, un enunciado formal sobre lo que el sistema debe hacer. Las propiedades sirven como puente entre las especificaciones legibles por humanos y las garantías de corrección verificables automáticamente.*

### Property 1: Campos vacíos siempre bloquean el envío del formulario

*Para cualquier* combinación de valores de campos del `IdentityForm` en que al menos uno sea una cadena vacía o compuesta solo de espacios en blanco, el intento de envío del formulario debe ser rechazado y el estado del formulario no debe cambiar.

**Validates: Requirements 1.3, 1.4**

---

### Property 2: Validación de formato de cédula es exhaustiva sobre el espacio de cadenas

*Para cualquier* cadena de texto, `validateCedula(s)` debe retornar `valid=true` si y solo si `s` cumple la expresión regular `^[A-Za-z]{3}-\d{6}-\d{4}[A-Za-z0-9]$`, y `valid=false` en cualquier otro caso.

**Validates: Requirements 1.5, 1.6**

---

### Property 3: Validación de formato de correo electrónico

*Para cualquier* cadena de texto que no contenga el carácter `@` seguido de al menos un carácter y un punto con dominio, `validateEmail(s)` debe retornar `valid=false`. *Para cualquier* cadena que sí contenga una estructura de correo electrónico válida (`local@domain.tld`), debe retornar `valid=true`.

**Validates: Requirements 1.7**

---

### Property 4: Validación cruzada cédula/fecha de nacimiento — invariante de coincidencia

*Para cualquier* cédula válida según el formato `AAA-DDMMYY-NNNNX` y *para cualquier* fecha de nacimiento en el rango 1900–2099: `validateCedulaFechaCruzada(cedula, fecha)` retorna `valid=true` si y solo si `extractBloqueCentral(cedula) === formatFechaDDMMYY(fecha)`. Equivalentemente, si los 6 dígitos centrales de la cédula (posiciones 4–9 después de eliminar guiones) coinciden exactamente con la cadena `DDMMYY` con zero-padding de la fecha, el resultado debe ser válido; si no coinciden, debe ser inválido.

**Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.6**

---

### Property 5: El payload de `POST /api/examen/iniciar` siempre contiene todos los campos requeridos

*Para cualquier* conjunto válido de datos del formulario de identidad (nombre, apellido, correo, cédula, fechaNacimiento, codigoSesion), el objeto payload construido para `POST /api/examen/iniciar` debe contener exactamente los campos `codigoSesion` (string), `nombre` (string), `apellido` (string), `correo` (string), `cedula` (string) y `fechaNacimiento` (string en formato ISO-8601 `YYYY-MM-DD`), sin campos adicionales no especificados.

**Validates: Requirements 3.2**

---

### Property 6: Inicialización de respuestas — todas las preguntas comienzan como omitidas

*Para cualquier* arreglo de N preguntas recibido de `GET /api/examen/preguntas` (donde N ≥ 1), después de la inicialización del estado de respuestas, el mapa `respuestas` debe contener exactamente N entradas, cada una con `esOmitida = true` y `opcionSeleccionada = ''`.

**Validates: Requirements 3.4**

---

### Property 7: `formatTime` siempre produce formato MM:SS

*Para cualquier* valor entero de segundos en el rango `[0, 3600]`, `formatTime(seconds)` debe retornar una cadena que cumpla la expresión regular `^\d{2}:\d{2}$`, donde la parte de minutos es `Math.floor(seconds / 60)` con zero-padding a dos dígitos y la parte de segundos es `seconds % 60` con zero-padding a dos dígitos.

**Validates: Requirements 4.3**

---

### Property 8: Estado de urgencia del Timer es correcto para cualquier valor de tiempo restante

*Para cualquier* valor de `timeLeft > 60`, el Timer debe renderizarse sin estilo de urgencia. *Para cualquier* valor de `timeLeft` en `[0, 60]`, el Timer debe renderizarse con el estilo de urgencia visual distinguible. La transición debe ser exactamente en el umbral de 60 segundos.

**Validates: Requirements 4.4, 4.5**

---

### Property 9: Selección y deselección de opciones actualiza el estado correctamente

*Para cualquier* número de pregunta `n` presente en el examen y *para cualquier* opción `opc ∈ {'A', 'B', 'C', 'D'}`, después de ejecutar la acción `SELECT_OPTION(n, opc)`, el estado de `respuestas[n]` debe ser `{ opcionSeleccionada: opc, esOmitida: false }`. Después de ejecutar `DESELECT_OPTION(n)` sobre cualquier pregunta que tenga una opción seleccionada, el estado debe ser `{ opcionSeleccionada: '', esOmitida: true }`.

**Validates: Requirements 5.4, 5.5**

---

### Property 10: `Estado_Bloqueado` deshabilita todos los controles de entrada

*Para cualquier* estado del examen con N preguntas, cuando `isBlocked = true`, todos los radio buttons de selección de respuesta y el botón de envío deben tener el atributo `disabled`. El número de controles deshabilitados debe ser igual al número total de opciones (4×N radio buttons) más el botón de envío.

**Validates: Requirements 5.6, 6.1, 6.2**

---

### Property 11: El payload de `guardar-respuestas` es completo y bien formado

*Para cualquier* estado de `respuestas` con N entradas (donde N es el número de preguntas del examen, N ≥ 1), el `Payload_Respuestas` construido por el `SubmitHandler` debe satisfacer: `detalles.length === N`; cada elemento de `detalles` contiene `numeroPregunta` (entero positivo), `opcionSeleccionada` (string, vacío si omitida), `esOmitida` (booleano); `horaFin` es una cadena ISO-8601 válida; `hojaRespuestaId` es la cadena UUID almacenada al iniciar. Las preguntas no respondidas deben aparecer con `esOmitida: true` y `opcionSeleccionada: ''`.

**Validates: Requirements 6.3, 6.4**

---

### Property 12: Re-validación backend — extracción de `Bloque_Central` es simétrica al frontend

*Para cualquier* cadena `cedula` que cumpla el formato `AAA-DDMMYY-NNNNX`, el método de extracción del `ExamenApiServlet` (Java: `cedula.replace("-","").substring(4,10)`) debe producir el mismo resultado que `extractBloqueCentral(cedula)` del frontend. *Para cualquier* par `(cedula, fechaNacimiento)` en que el bloque central no coincida con `DDMMYY`, el servlet debe retornar `422 Unprocessable Entity` con `{"error": "La fecha de nacimiento no coincide con la cédula de identidad"}`.

**Validates: Requirements 7.2, 7.3**

---

### Property 13: La pantalla de resultados muestra exactamente los valores recibidos con sus etiquetas

*Para cualquier* objeto `resultado` con campos `puntuacionDirecta` (entero), `percentil` (entero) e `interpretacion` (string no vacío), el componente `ResultsScreen` renderizado con ese objeto debe contener el texto de `puntuacionDirecta` junto a la etiqueta "Puntuación Directa", el texto de `percentil` junto a la etiqueta "Percentil", y el texto de `interpretacion` junto a la etiqueta "Interpretación". Además, no debe haber radio buttons activos ni botón de envío del examen en el DOM.

**Validates: Requirements 8.2, 8.3, 8.4**

---

## Error Handling

### Frontend — estrategia de errores por capa

| Escenario | Código/Condición | Mensaje mostrado | Estado resultante |
|---|---|---|---|
| Campo obligatorio vacío | — (validación local) | "El campo [X] es obligatorio" | Permanece en `IdentityForm` |
| Formato cédula inválido | — (validación local) | "Formato de cédula no válido. Ejemplo: ABC-011290-1234X" | Permanece en `IdentityForm` |
| Correo sin formato válido | — (validación local) | "El correo electrónico no tiene un formato válido" | Permanece en `IdentityForm` |
| Cédula/fecha no coinciden | — (Validator) | "Error de Validación: La fecha de nacimiento proporcionada no coincide con los registros de tu cédula de identidad. Por favor, verifica ambos campos antes de continuar." | Permanece en `IdentityForm` |
| `GET /preguntas` → 404 | HTTP 404 | "Sesión no encontrada. Verifica tu código de sesión." | Permanece en `IdentityForm` |
| `POST /iniciar` → 422 | HTTP 422 | Contenido del campo `error` del JSON | Permanece en `IdentityForm` |
| `POST /iniciar` → 400/404 | HTTP 400, 404 | Contenido del campo `error` del JSON | Permanece en `IdentityForm` |
| Error de red / 5xx | Network error o ≥500 | "Error de conexión. Verifica tu red e intenta de nuevo." | Permanece en `IdentityForm` |
| `limiteTiempoMinutos` inválido | ausente / <1 / no entero | "Error de configuración: el límite de tiempo de la sesión no es válido." | No transiciona a `ExamView` |
| Timer expira → `POST /guardar-respuestas` falla | HTTP 4xx/5xx | "Error [código] al enviar respuestas." | Permanece en `Estado_Bloqueado` |
| `POST /guardar-respuestas` → error de red | Network error | "Error de conexión al enviar respuestas. Contacta al evaluador." | Permanece en `Estado_Bloqueado` |
| `POST /guardar-respuestas` → 200 sin campos esperados | 200 pero sin `puntuacionDirecta` etc. | "El resultado no está disponible. Contacta al evaluador." | Permanece en `Estado_Bloqueado` |
| Envío duplicado (guard) | Segunda llamada mientras `isSubmitting=true` | — (silencioso; no se muestra error) | La primera llamada continúa |

### Backend — respuestas de error

Todos los errores del backend retornan JSON con la clave `"error"` y `Content-Type: application/json; charset=UTF-8`. Nunca se retorna HTML de error de Tomcat.

| Condición | Código | Cuerpo JSON |
|---|---|---|
| `codigoSesion` ausente | 400 | `{"error": "El parámetro codigoSesion es obligatorio"}` |
| Sesión no encontrada / estado incorrecto | 404 | `{"error": "Sesión no encontrada o no disponible"}` |
| Prueba no configurada en BD | 503 | `{"error": "La prueba no está configurada"}` |
| Campos `cedula`/`fechaNacimiento` ausentes en POST iniciar | 400 | `{"error": "Los campos cedula y fechaNacimiento son obligatorios"}` |
| Cédula/fecha no coinciden | 422 | `{"error": "La fecha de nacimiento no coincide con la cédula de identidad"}` |
| Campos obligatorios faltantes en POST iniciar | 400 | `{"error": "Campos obligatorios: codigoSesion, nombre, apellido"}` |
| `hojaRespuestaId` ausente en POST guardar | 400 | `{"error": "El campo hojaRespuestaId es obligatorio"}` |
| `HojaRespuesta` no encontrada | 404 | `{"error": "HojaRespuesta no encontrada"}` |
| Error de cálculo de resultado | 500 | `{"error": "Error al calcular el resultado"}` |
| Error JPA / excepción interna | 500 | `{"error": "Error interno al iniciar el examen"}` o `{"error": "Error interno al guardar respuestas"}` |

---

## Testing Strategy

### Enfoque dual: pruebas unitarias/ejemplo + pruebas basadas en propiedades

El módulo `Validator` (`validator.js`) es código de lógica pura con entradas bien definidas y sin efectos secundarios — ideal para property-based testing. Los componentes React se prueban con pruebas de ejemplo usando `@testing-library/react`. El backend Java se prueba con pruebas unitarias JUnit 4 sobre las clases de servicio y la lógica de extracción.

### Configuración de herramientas

**Frontend:**
- **Property-based testing**: [fast-check](https://fast-check.dev/) v3 — genera cientos de entradas aleatorias por propiedad
- **Unit/example tests**: [Vitest](https://vitest.dev/) + `@testing-library/react` + `@testing-library/user-event`
- Instalación: `npm install --save-dev vitest @testing-library/react @testing-library/user-event fast-check jsdom`
- Añadir `test: { environment: 'jsdom' }` a `vite.config.js`
- Cada prueba de propiedad corre mínimo **100 iteraciones** (default de fast-check)

**Backend:**
- **JUnit 4** (ya presente en el proyecto según `AGENTS.md`)
- Pruebas unitarias sobre `ExamenApiServlet.extractBloqueCentral()` y `formatFechaParaComparacion()`
- No se usan `mvn test` para correr — el usuario los corre desde el IDE

### Estructura de archivos de prueba

```
frontend-react/src/
└── __tests__/
    ├── validator.property.test.js   ← Property tests (fast-check)
    ├── validator.unit.test.js       ← Example/edge case tests
    ├── IdentityForm.test.jsx        ← Component tests
    ├── ExamView.test.jsx            ← Timer + selection tests
    ├── Timer.test.jsx               ← formatTime + urgency tests
    └── ResultsScreen.test.jsx       ← Results display tests

src/test/java/org/example/Razonamiento/rest/
└── ExamenApiValidationTest.java     ← JUnit 4 tests para extracción backend
```

### Mapeo propiedades → pruebas

Cada property test lleva un comentario de trazabilidad:
```js
// Feature: student-portal-validation, Property 4: Cross-validation cedula/fecha
```

| Property | Archivo de prueba | Descripción del generador |
|---|---|---|
| P1 — campos vacíos bloquean envío | `validator.property.test.js` | `fc.record` con al menos un campo en `fc.oneof(fc.constant(''), fc.string().filter(s => !s.trim()))` |
| P2 — formato cédula | `validator.property.test.js` | `fc.string()` para inválidas; `fc.tuple(fc.string({minLength:3,maxLength:3}), fc.date(), fc.nat(9999), fc.char())` para válidas |
| P3 — formato email | `validator.property.test.js` | `fc.emailAddress()` para válidas; `fc.string().filter(s => !s.includes('@'))` para inválidas |
| P4 — cross-validation | `validator.property.test.js` | Generar fecha aleatoria 1900–2099, construir cédula con DDMMYY embebido, verificar válido; mutar un dígito, verificar inválido |
| P5 — payload iniciar | `IdentityForm.test.jsx` | `fc.record` con campos de identidad válidos; verificar estructura del objeto fetch |
| P6 — inicialización omitida | `ExamView.test.jsx` | `fc.array(fc.nat(), {minLength:1, maxLength:50})` para simular N preguntas |
| P7 — formatTime MM:SS | `Timer.test.jsx` | `fc.integer({min:0, max:3600})` |
| P8 — urgencia Timer | `Timer.test.jsx` | `fc.integer({min:61, max:3600})` para neutro; `fc.integer({min:0, max:60})` para urgente |
| P9 — selección/deselección | `ExamView.test.jsx` | `fc.tuple(fc.integer({min:1,max:40}), fc.constantFrom('A','B','C','D'))` |
| P10 — bloqueado deshabilita todo | `ExamView.test.jsx` | `fc.array(fc.integer({min:1}), {minLength:1,maxLength:30})` para N preguntas |
| P11 — payload guardar completo | `validator.property.test.js` (o `useSubmitHandler.test.js`) | `fc.array(fc.record({...}), {minLength:1})` para N respuestas |
| P12 — extracción backend simétrica | `ExamenApiValidationTest.java` | Generar cédulas válidas aleatorias; comparar con resultado frontend |
| P13 — pantalla resultados | `ResultsScreen.test.jsx` | `fc.record({puntuacionDirecta: fc.integer(), percentil: fc.integer({min:0,max:99}), interpretacion: fc.string({minLength:1})})` |

### Pruebas de ejemplo (no-property)

- Cada ruta de error HTTP (`404`, `400`, `422`, `5xx`, network error) tiene exactamente una prueba de ejemplo con `fetch` mockeado
- La transición de vista (`identity → exam → results`) se verifica con pruebas de integración de componentes
- El estado `Estado_Bloqueado` activado por el Timer se verifica con `vi.useFakeTimers()`
- El guard de envío duplicado se verifica disparando `submit()` dos veces y confirmando que `fetch` se llama una sola vez

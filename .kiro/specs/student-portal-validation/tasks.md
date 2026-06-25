# Implementation Plan: student-portal-validation

## Overview

Transforma `PortalExamen.jsx` de modo mock a un portal de examen completamente conectado al backend Java/OpenXava. El plan sigue la arquitectura del diseño: refactorizar el estado global a `useReducer` en `App.jsx`, extraer la lógica de validación a `src/utils/validator.js`, crear los sub-componentes `IdentityForm`, `ExamView`, `Timer` y `ResultsScreen`, añadir el hook `useSubmitHandler`, e introducir los servlets `CorsFilter`, `ExamenApiServlet` y `RespuestasApiServlet` en el backend bajo `org.example.Razonamiento.rest`.

---

## Tasks

- [ ] 1. Configurar el entorno de pruebas frontend e instalar dependencias

  - Instalar `vitest`, `@testing-library/react`, `@testing-library/user-event`, `fast-check` y `jsdom` como `devDependencies` en `frontend-react/package.json`
  - Añadir la sección `test: { environment: 'jsdom' }` en `vite.config.js`
  - Crear el directorio `frontend-react/src/__tests__/` con un archivo `.gitkeep` para que git lo rastree
  - _Requirements: transversal — habilita todas las pruebas del plan_

- [ ] 2. Crear el módulo `validator.js` con lógica pura de validación

  - [ ] 2.1 Implementar las funciones de validación en `frontend-react/src/utils/validator.js`
    - `validateCedula(cedula)` — valida `^[A-Za-z]{3}-\d{6}-\d{4}[A-Za-z0-9]$`, retorna `{ valid, error }`
    - `validateEmail(email)` — valida presencia de `@` y dominio mínimo, retorna `{ valid, error }`
    - `extractBloqueCentral(cedula)` — elimina guiones, extrae posiciones 4–9 (6 chars)
    - `formatFechaDDMMYY(fechaNacimiento)` — formatea `Date` como `DDMMYY` con zero-padding
    - `validateCedulaFechaCruzada(cedula, fechaNacimiento)` — compara `extractBloqueCentral` con `formatFechaDDMMYY`, retorna `{ valid, error }` con el mensaje de requisito 2.4 cuando no coinciden
    - _Requirements: 1.5, 1.6, 1.7, 2.1, 2.2, 2.3, 2.4_

  - [ ]* 2.2 Escribir property test: Property 2 — formato de cédula exhaustivo
    - Archivo: `src/__tests__/validator.property.test.js`
    - Usar `fc.string()` para cadenas arbitrarias y verificar que `validateCedula` concuerda exactamente con la regex
    - Generar cédulas válidas sintéticas con `fc.tuple(letras×3, fecha, nat×4, char)` y verificar `valid=true`
    - **Property 2: Validación de formato de cédula es exhaustiva sobre el espacio de cadenas**
    - **Validates: Requirements 1.5, 1.6**

  - [ ]* 2.3 Escribir property test: Property 3 — formato de correo electrónico
    - Archivo: `src/__tests__/validator.property.test.js`
    - Usar `fc.emailAddress()` para válidos y `fc.string().filter(s => !s.includes('@'))` para inválidos
    - **Property 3: Validación de formato de correo electrónico**
    - **Validates: Requirements 1.7**

  - [ ]* 2.4 Escribir property test: Property 4 — validación cruzada cédula/fecha invariante
    - Archivo: `src/__tests__/validator.property.test.js`
    - Generar fecha aleatoria 1900–2099, construir cédula con DDMMYY embebido; verificar `valid=true`
    - Mutar un dígito del bloque central; verificar `valid=false`
    - **Property 4: Validación cruzada cédula/fecha de nacimiento — invariante de coincidencia**
    - **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.6**

  - [ ]* 2.5 Escribir pruebas unitarias de ejemplo para `validator.js`
    - Archivo: `src/__tests__/validator.unit.test.js`
    - Casos límite: cédula con longitud incorrecta, cédula con letras en posición de dígitos, fecha bisiesto 29/02, correo sin dominio
    - _Requirements: 1.5, 1.6, 1.7, 2.1–2.4_

- [ ] 3. Refactorizar `App.jsx` con `useReducer` y estado global

  - [ ] 3.1 Reescribir `App.jsx` con el reducer de vistas y estado global del diseño
    - Definir el estado inicial: `{ view: 'identity', preguntas: [], hojaRespuestaId: null, limiteTiempoMinutos: null, respuestas: {}, resultado: null, error: null, isBlocked: false }`
    - Implementar el reducer con las acciones: `SET_LOADING`, `SET_PREGUNTAS_Y_HOJA`, `SET_EXAM_VIEW`, `SELECT_OPTION`, `DESELECT_OPTION`, `BLOCK`, `SET_RESULTADO`, `SET_ERROR`
    - Eliminar los props mock `datosEvaluado` y `preguntasDelTest` del `App.jsx` actual
    - Renderizar condicionalmente `IdentityForm`, pantalla de carga, `ExamView` o `ResultsScreen` según `state.view`
    - _Requirements: 3.3, 3.4, 5.4, 5.5, 5.6_

  - [ ]* 3.2 Escribir property test: Property 6 — inicialización de respuestas todas omitidas
    - Archivo: `src/__tests__/ExamView.test.jsx` (o `reducer.property.test.js`)
    - Usar `fc.array(fc.nat(), {minLength:1, maxLength:50})` para simular N preguntas; despachar `SET_PREGUNTAS_Y_HOJA` y verificar que todas las entradas tienen `esOmitida=true` y `opcionSeleccionada=''`
    - **Property 6: Inicialización de respuestas — todas las preguntas comienzan como omitidas**
    - **Validates: Requirements 3.4**

  - [ ]* 3.3 Escribir property test: Property 9 — selección y deselección actualizan el estado
    - Archivo: `src/__tests__/ExamView.test.jsx`
    - Usar `fc.tuple(fc.integer({min:1,max:40}), fc.constantFrom('A','B','C','D'))` para `SELECT_OPTION`; verificar `opcionSeleccionada=opc` y `esOmitida=false`
    - Verificar `DESELECT_OPTION` produce `opcionSeleccionada=''` y `esOmitida=true`
    - **Property 9: Selección y deselección de opciones actualiza el estado correctamente**
    - **Validates: Requirements 5.4, 5.5**

- [ ] 4. Crear el componente `IdentityForm.jsx`

  - [ ] 4.1 Implementar `frontend-react/src/components/IdentityForm.jsx`
    - Campos: nombre (máx 100), apellido (máx 100), fecha de nacimiento (date picker), cédula (máx 16), correo, código de sesión (máx 8)
    - Validación local on-submit en orden: campos vacíos → formato cédula → formato correo → `validateCedulaFechaCruzada`
    - Mostrar mensajes de error específicos por campo según la tabla de errores del diseño
    - On validación exitosa: llamar a `GET /api/examen/preguntas?codigoSesion=...` y luego `POST /api/examen/iniciar`
    - Manejar todos los códigos de respuesta: 200, 201, 400, 404, 422, 5xx, error de red según la tabla de error handling
    - Mostrar indicador de carga y deshabilitar el botón de envío mientras esperan las llamadas API
    - Despachar acciones `SET_LOADING`, `SET_PREGUNTAS_Y_HOJA`, `SET_EXAM_VIEW` y `SET_ERROR` según el resultado
    - _Requirements: 1.1–1.8, 2.1–2.5, 3.1–3.8_

  - [ ]* 4.2 Escribir property test: Property 1 — campos vacíos bloquean envío
    - Archivo: `src/__tests__/validator.property.test.js`
    - Usar `fc.record` con al menos un campo como `fc.constant('')` o `fc.string().filter(s => !s.trim())`; verificar que el submit no produce llamada `fetch`
    - **Property 1: Campos vacíos siempre bloquean el envío del formulario**
    - **Validates: Requirements 1.3, 1.4**

  - [ ]* 4.3 Escribir property test: Property 5 — payload de iniciar contiene todos los campos requeridos
    - Archivo: `src/__tests__/IdentityForm.test.jsx`
    - Usar `fc.record` con campos de identidad válidos; interceptar `fetch` y verificar que el body contiene exactamente `codigoSesion`, `nombre`, `apellido`, `correo`, `cedula`, `fechaNacimiento` (ISO-8601 `YYYY-MM-DD`)
    - **Property 5: El payload de `POST /api/examen/iniciar` siempre contiene todos los campos requeridos**
    - **Validates: Requirements 3.2**

  - [ ]* 4.4 Escribir pruebas de ejemplo para rutas de error HTTP en `IdentityForm`
    - Archivo: `src/__tests__/IdentityForm.test.jsx`
    - Una prueba por escenario: 404 en preguntas, 422 en iniciar, 400 en iniciar, error de red, 5xx
    - Verificar que el mensaje correcto aparece en el DOM y el componente permanece en vista `identity`
    - _Requirements: 3.5, 3.6, 3.7, 3.8_

- [ ] 5. Crear el componente `Timer.jsx`

  - [ ] 5.1 Implementar `frontend-react/src/components/Timer.jsx`
    - Props: `totalSeconds`, `onExpire`, `frozen`
    - Usar `setInterval` de 1 segundo; cuando llega a 0: congelar y llamar `onExpire()`
    - Renderizar el tiempo con `formatTime` (función pura exportada desde el mismo archivo o desde `utils/`)
    - Aplicar estilo de urgencia cuando `timeLeft <= 60` (distinguible del estado neutro)
    - Posicionamiento fijo en pantalla (no desaparece al hacer scroll)
    - Cuando `frozen=true`: detener el tick, no llamar `onExpire` de nuevo
    - _Requirements: 4.1, 4.3, 4.4, 4.5, 4.6, 4.7_

  - [ ]* 5.2 Escribir property test: Property 7 — `formatTime` siempre produce `MM:SS`
    - Archivo: `src/__tests__/Timer.test.jsx`
    - Usar `fc.integer({min:0, max:3600})`; verificar que el resultado cumple `^\d{2}:\d{2}$` y que minutos = `Math.floor(s/60)`, segundos = `s%60` con zero-padding
    - **Property 7: `formatTime` siempre produce formato MM:SS**
    - **Validates: Requirements 4.3**

  - [ ]* 5.3 Escribir property test: Property 8 — estado de urgencia del Timer es correcto
    - Archivo: `src/__tests__/Timer.test.jsx`
    - `fc.integer({min:61, max:3600})` → sin estilo urgencia; `fc.integer({min:0, max:60})` → con estilo urgencia
    - Verificar la clase/estilo aplicado al elemento del Timer en el DOM renderizado
    - **Property 8: Estado de urgencia del Timer es correcto para cualquier valor de tiempo restante**
    - **Validates: Requirements 4.4, 4.5**

- [ ] 6. Crear el hook `useSubmitHandler.js` y el componente `ExamView.jsx`

  - [ ] 6.1 Implementar `frontend-react/src/hooks/useSubmitHandler.js`
    - Retorna `{ submit, isSubmitting }`
    - Guard interno: si `isSubmitting === true`, ignorar la invocación sin cancelar la llamada en curso
    - `submit(respuestas, hojaRespuestaId)`: construir `Payload_Respuestas` con `hojaRespuestaId`, `horaFin` (ISO-8601), `detalles[]` (una entrada por pregunta)
    - Las preguntas sin respuesta se incluyen con `esOmitida: true` y `opcionSeleccionada: ''`
    - Llamar `POST /api/examen/guardar-respuestas`; en 200: invocar `onSuccess(result)`; en 4xx/5xx: invocar `onError(msg)` con el mensaje de error del diseño; en error de red: invocar `onError("Error de conexión al enviar respuestas. Contacta al evaluador.")`
    - _Requirements: 6.1–6.8_

  - [ ]* 6.2 Escribir property test: Property 11 — payload de guardar-respuestas es completo y bien formado
    - Archivo: `src/__tests__/validator.property.test.js` (o `useSubmitHandler.test.js`)
    - Usar `fc.array(fc.record({numeroPregunta: fc.nat(), opcionSeleccionada: fc.constantFrom('A','B','C','D',''), esOmitida: fc.boolean()}), {minLength:1})` para N respuestas
    - Verificar `detalles.length === N`, estructura de cada detalle, `horaFin` válido ISO-8601, `hojaRespuestaId` presente
    - **Property 11: El payload de `guardar-respuestas` es completo y bien formado**
    - **Validates: Requirements 6.3, 6.4**

  - [ ] 6.3 Implementar `frontend-react/src/components/ExamView.jsx`
    - Recibe `preguntas`, `limiteTiempoMinutos`, `hojaRespuestaId`, `respuestas`, `isBlocked`, `dispatch` (del reducer)
    - Renderizar el `Timer` con `totalSeconds = limiteTiempoMinutos * 60`; en `onExpire`: despachar `BLOCK` e invocar `submit`
    - Renderizar las preguntas con radio buttons; `onChange` despacha `SELECT_OPTION`; click en opción ya seleccionada despacha `DESELECT_OPTION`
    - El botón "Enviar" llama `submit` manualmente; deshabilitar cuando `isBlocked` o `isSubmitting`
    - Cuando `isBlocked=true`: deshabilitar todos los radio buttons
    - Mostrar mensajes de error de envío dentro de la vista sin abandonar `Estado_Bloqueado`
    - _Requirements: 4.1, 4.6, 4.7, 4.8, 5.1–5.6, 6.1, 6.2_

  - [ ]* 6.4 Escribir property test: Property 10 — `Estado_Bloqueado` deshabilita todos los controles
    - Archivo: `src/__tests__/ExamView.test.jsx`
    - Usar `fc.array(fc.integer({min:1}), {minLength:1, maxLength:30})` para N preguntas; renderizar `ExamView` con `isBlocked=true`
    - Verificar que todos los radio buttons (4×N) y el botón "Enviar" tienen `disabled`
    - **Property 10: `Estado_Bloqueado` deshabilita todos los controles de entrada**
    - **Validates: Requirements 5.6, 6.1, 6.2**

  - [ ]* 6.5 Escribir prueba de ejemplo: guard de envío duplicado
    - Archivo: `src/__tests__/ExamView.test.jsx`
    - Disparar `submit()` dos veces rápidamente; verificar que `fetch` se llama exactamente una vez
    - _Requirements: 6.8_

- [ ] 7. Checkpoint — Verificar integración frontend hasta aquí
  - Asegurarse de que todas las pruebas de las tareas 1–6 pasan con `npx vitest --run`
  - Verificar que `App.jsx` renderiza `IdentityForm` al cargar y que el reducer transiciona vistas correctamente
  - Preguntar al usuario si hay dudas o ajustes antes de continuar con el backend

- [ ] 8. Crear el componente `ResultsScreen.jsx`

  - [ ] 8.1 Implementar `frontend-react/src/components/ResultsScreen.jsx`
    - Props: `resultado` (`{ puntuacionDirecta, percentil, interpretacion }`)
    - Mostrar con etiquetas exactas: "Puntuación Directa", "Percentil", "Interpretación"
    - Sin radio buttons ni botón de envío del examen en el DOM
    - Sin controles que permitan regresar al examen o modificar respuestas
    - _Requirements: 8.1–8.4_

  - [ ]* 8.2 Escribir property test: Property 13 — pantalla de resultados muestra los valores con sus etiquetas
    - Archivo: `src/__tests__/ResultsScreen.test.jsx`
    - Usar `fc.record({ puntuacionDirecta: fc.integer(), percentil: fc.integer({min:0,max:99}), interpretacion: fc.string({minLength:1}) })`
    - Verificar que cada valor aparece junto a su etiqueta y que no hay radio buttons ni botón de envío
    - **Property 13: La pantalla de resultados muestra exactamente los valores recibidos con sus etiquetas**
    - **Validates: Requirements 8.2, 8.3, 8.4**

- [ ] 9. Actualizar `PortalExamen.jsx` para mantener compatibilidad de importación

  - Reescribir `PortalExamen.jsx` para que simplemente re-exporte el nuevo `App` (o el estado orquestado desde `App.jsx`), eliminando la lógica de mock que actualmente contiene
  - Garantizar que `main.jsx` sigue funcionando sin cambios
  - _Requirements: 1.1 (punto de entrada del Portal)_

- [ ] 10. Implementar `CorsFilter` en el backend

  - [ ] 10.1 Crear `src/main/java/org/example/Razonamiento/rest/CorsFilter.java`
    - `@WebFilter(urlPatterns = "/api/*")` implementando `javax.servlet.Filter`
    - Agregar headers: `Access-Control-Allow-Origin: http://localhost:5173`, `Access-Control-Allow-Methods: GET, POST, OPTIONS`, `Access-Control-Allow-Headers: Content-Type, Authorization`
    - Si el método es `OPTIONS`: `response.setStatus(200)` y retornar sin continuar la cadena
    - En cualquier otro método: continuar con `chain.doFilter(request, response)`
    - _Requirements: 3.1, 3.2 (habilita llamadas CORS desde el frontend)_

- [ ] 11. Implementar `ExamenApiServlet` en el backend

  - [ ] 11.1 Crear `src/main/java/org/example/Razonamiento/rest/ExamenApiServlet.java`
    - `@WebServlet("/api/examen/*")` manejando `GET /api/examen/preguntas` y `POST /api/examen/iniciar`
    - **`doGet` — `/api/examen/preguntas`**: leer parámetro `codigoSesion`; si ausente → 400; buscar `SesionPrueba` vía `AutenticacionService`; si no encontrada → 404; retornar `{ limiteTiempoMinutos, preguntas[] }` en JSON (sin `respuestaCorrecta`)
    - **`doPost` — `/api/examen/iniciar`**: parsear JSON body; validar presencia de `cedula` y `fechaNacimiento` → 400 si ausentes; re-validar cruce cédula/fecha con `cedula.replace("-","").substring(4,10)` vs `String.format("%02d%02d%02d", dia, mes, anio%100)` → 422 si no coinciden; validar `codigoSesion`, `nombre`, `apellido` → 400 si ausentes; crear/buscar `Evaluado`; crear `HojaRespuesta`; retornar 201 `{ hojaRespuestaId, codigoSesion, limiteTiempoMinutos }`
    - Manejar excepciones JPA con `tx.rollback()` y retornar 500 con JSON `{"error": "..."}` (nunca HTML)
    - Seguir el patrón de transacción de `PortalExamenServlet` (`tx.begin()` / `tx.commit()` / catch+rollback)
    - Añadir campo `cedula` a la entidad `Evaluado` con `@Column(name="cedula", length=16)` siguiendo convenciones de `AGENTS.md` (package access, Lombok)
    - _Requirements: 3.1–3.8, 7.1–7.5_

  - [ ]* 11.2 Escribir prueba JUnit 4 para la re-validación backend
    - Archivo: `src/test/java/org/example/Razonamiento/rest/ExamenApiValidationTest.java`
    - Método unitario que prueba `extractBloqueCentral` y `formatFechaParaComparacion` directamente (extraer como métodos estáticos del servlet para facilitar testing)
    - Casos: cédula válida con fecha coincidente → coincide; cédula válida con fecha distinta → no coincide; cédula sin guiones → correcto
    - **Property 12: Re-validación backend — extracción de `Bloque_Central` es simétrica al frontend**
    - **Validates: Requirements 7.2, 7.3**
    - _AGENTS.md: JUnit 4, no usar `mvn` para correr — ejecutar desde el IDE_

- [ ] 12. Implementar `RespuestasApiServlet` en el backend

  - [ ] 12.1 Crear `src/main/java/org/example/Razonamiento/rest/RespuestasApiServlet.java`
    - `@WebServlet("/api/examen/guardar-respuestas")` manejando `POST`
    - Parsear JSON body; validar presencia de `hojaRespuestaId` → 400 si ausente
    - Buscar `HojaRespuesta` por UUID → 404 si no encontrada
    - Persistir cada elemento de `detalles[]` como `RespuestaDetalles` en la `HojaRespuesta`
    - Delegar a `CorreccionAutomaticaService` para calcular resultado
    - Retornar 200 `{ hojaRespuestaId, puntuacionDirecta, percentil, interpretacion }`
    - Manejar error de cálculo → 500 `{"error": "Error al calcular el resultado"}`
    - Seguir el mismo patrón de transacción JPA que `ExamenApiServlet`
    - _Requirements: 6.3–6.7, 8.1–8.5_

- [ ] 13. Registrar los servlets en `web.xml` o verificar auto-descubrimiento de anotaciones

  - Verificar si el `web.xml` del proyecto usa `metadata-complete="true"` (lo que desactivaría el escaneo de `@WebServlet`/`@WebFilter`)
  - Si es necesario, añadir los mappings de `CorsFilter`, `ExamenApiServlet` y `RespuestasApiServlet` en `src/main/webapp/WEB-INF/web.xml`
  - _Requirements: 3.1, 6.5 (accesibilidad de los endpoints)_

- [ ] 14. Checkpoint final — Verificar integración completa
  - Asegurarse de que todas las pruebas frontend pasan con `npx vitest --run`
  - Verificar que las pruebas JUnit backend se pueden ejecutar desde el IDE sin errores de compilación
  - Confirmar que el flujo completo (`IdentityForm → ExamView → ResultsScreen`) funciona con el backend activo
  - Preguntar al usuario si hay dudas o ajustes finales

---

## Notes

- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido
- Cada tarea referencia los requisitos específicos para trazabilidad
- Los servlets backend siguen **exactamente** el patrón de `PortalExamenServlet` y `PruebaServlet` (transacción JPA, `JPAUtil.createEntityManager()`, `AutenticacionService`, `CorreccionAutomaticaService`)
- Los campos de `Evaluado` nuevos (`cedula`) usan acceso de paquete sin `private` y anotaciones Lombok (`@Getter @Setter`), per `AGENTS.md`
- Las pruebas JUnit 4 se ejecutan desde el IDE, **no** con `mvn test`
- El proxy Vite (`/api → http://localhost:8080/Razonamiento`) ya está configurado; no requiere cambios en `vite.config.js` más allá de añadir la sección `test`
- `PortalExamen.jsx` se convierte en un thin re-export para no romper la importación en `main.jsx`
- Los property tests usan `fast-check` con mínimo 100 iteraciones por defecto

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1"] },
    { "id": 1, "tasks": ["2.1", "3.1", "10.1"] },
    { "id": 2, "tasks": ["2.2", "2.3", "2.4", "2.5", "3.2", "3.3", "5.1", "11.1"] },
    { "id": 3, "tasks": ["4.1", "5.2", "5.3", "6.1", "12.1"] },
    { "id": 4, "tasks": ["4.2", "4.3", "4.4", "6.2", "6.3", "11.2", "13"] },
    { "id": 5, "tasks": ["6.4", "6.5", "8.1"] },
    { "id": 6, "tasks": ["8.2", "9"] }
  ]
}
```

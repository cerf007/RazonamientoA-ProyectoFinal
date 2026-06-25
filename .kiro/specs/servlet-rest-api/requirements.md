# Requirements Document

## Introduction

Este documento describe los requisitos funcionales de la capa de APIs REST que actúa como puente entre el frontend React/Vite (puerto 5173) y el backend OpenXava/PostgreSQL (puerto 8080, contexto `/Razonamiento`).

El sistema implementa la prueba psicométrica **BFA: Razonamiento Forma A**. El frontend ya posee un Portal del Estudiante (`PortalExamen.jsx`) que actualmente opera con datos mock. Esta capa de APIs elimina los mocks y conecta el flujo completo: validación de sesión → entrega de preguntas → registro del evaluado → almacenamiento de respuestas → cálculo y consulta de resultado.

Los servlets se implementan como `javax.servlet.HttpServlet` puros, sin Spring, registrados mediante anotaciones `@WebServlet` y declarados en `web.xml`, en el paquete `org.example.Razonamiento.api`. Cada servlet aplica los encabezados CORS necesarios para permitir las peticiones originadas desde el origen `http://localhost:5173`.

---

## Glossary

- **API_Layer**: Conjunto de cuatro servlets HTTP que exponen los endpoints REST bajo el prefijo `/api/examen/`.
- **ExamenServlet**: Servlet responsable de los endpoints `GET /api/examen/preguntas` y `POST /api/examen/iniciar`.
- **RespuestasServlet**: Servlet responsable del endpoint `POST /api/examen/guardar-respuestas`.
- **ResultadoServlet_API**: Servlet responsable del endpoint `GET /api/examen/resultado`.
- **CorsFilter**: Filtro de servlet que agrega los encabezados CORS a todas las respuestas bajo `/api/*`.
- **SesionPrueba**: Entidad JPA que representa una sesión de prueba programada por un evaluador. Tiene un `codigoSesion` único y un estado (`PROGRAMADA`, `EN_PROGRESO`, `FINALIZADA`, `CORREGIDA`).
- **PruebaRazonamientoFormaA**: Entidad JPA que contiene la colección de `Pregunta` y el límite de tiempo configurado.
- **Pregunta**: Entidad JPA con `numero`, `serieIncompleta`, `opcionA`–`opcionD` y `respuestaCorrecta`.
- **Evaluado**: Entidad JPA (extiende `Usuario`) que representa al estudiante que rinde la prueba.
- **HojaRespuesta**: Entidad JPA que vincula un `Evaluado` con una `SesionPrueba` y contiene la lista de `RespuestaDetalles`.
- **RespuestaDetalles**: Entidad JPA que registra la opción seleccionada y el acierto para cada pregunta dentro de una `HojaRespuesta`.
- **Resultado**: Entidad JPA que almacena `puntuacionDirecta`, `percentil` e `interpretacion` vinculados a una `HojaRespuesta`.
- **CorreccionAutomaticaService**: Servicio existente que calcula el `Resultado` a partir de una `HojaRespuesta`.
- **AutenticacionService**: Servicio existente que valida un `codigoSesion` y retorna la `SesionPrueba` activa.
- **JPAUtil**: Utilidad existente que provee `EntityManager` usando la unidad de persistencia `default`.
- **Payload**: Cuerpo JSON de una petición o respuesta HTTP.
- **codigoSesion**: Cadena de 8 caracteres alfanuméricos en mayúsculas que identifica una `SesionPrueba`.
- **hojaRespuestaId**: UUID en formato de cadena que identifica una `HojaRespuesta`.

---

## Requirements

### Requisito 1: Filtro CORS global

**User Story:** Como desarrollador frontend, quiero que todas las respuestas del backend incluyan los encabezados CORS correctos, para que el navegador permita las llamadas desde `http://localhost:5173` sin errores de política de origen cruzado.

#### Criterios de Aceptación

1. THE `CorsFilter` SHALL registrarse como filtro de servlet con patrón `/api/*` mediante la anotación `@WebFilter` o declaración en `web.xml`.
2. WHEN el `CorsFilter` procesa una solicitud, THE `CorsFilter` SHALL agregar el encabezado `Access-Control-Allow-Origin: http://localhost:5173` a la respuesta.
3. WHEN el `CorsFilter` procesa una solicitud, THE `CorsFilter` SHALL agregar el encabezado `Access-Control-Allow-Methods: GET, POST, OPTIONS` a la respuesta.
4. WHEN el `CorsFilter` procesa una solicitud, THE `CorsFilter` SHALL agregar el encabezado `Access-Control-Allow-Headers: Content-Type, Authorization` a la respuesta.
5. WHEN la solicitud utiliza el método HTTP `OPTIONS` (preflight), THE `CorsFilter` SHALL responder con el código de estado `200 OK` y detener la cadena de filtros sin invocar el servlet destino.

---

### Requisito 2: Obtener preguntas de la prueba activa

**User Story:** Como estudiante, quiero que el frontend cargue las preguntas reales de la prueba al ingresar mi código de sesión, para que pueda responder el test BFA con el contenido oficial configurado por el evaluador.

#### Criterios de Aceptación

1. WHEN se recibe `GET /api/examen/preguntas` con el parámetro de consulta `codigoSesion`, THE `ExamenServlet` SHALL validar el código invocando `AutenticacionService.validarCodigoSesion(codigoSesion)`.
2. WHEN `AutenticacionService` retorna una `SesionPrueba` válida, THE `ExamenServlet` SHALL consultar la `PruebaRazonamientoFormaA` activa y retornar un JSON con el campo `limiteTiempoMinutos` (entero) y el arreglo `preguntas`, donde cada elemento incluye `numero`, `serieIncompleta`, `opcionA`, `opcionB`, `opcionC` y `opcionD`. La respuesta NO incluye el campo `respuestaCorrecta`.
3. WHEN `AutenticacionService` retorna `null` (código inválido, sesión inexistente o estado no permitido), THE `ExamenServlet` SHALL retornar el código de estado `404 Not Found` con un JSON `{"error": "Sesión no encontrada o no disponible"}`.
4. IF el parámetro `codigoSesion` está ausente o en blanco, THEN THE `ExamenServlet` SHALL retornar el código de estado `400 Bad Request` con un JSON `{"error": "El parámetro codigoSesion es obligatorio"}`.
5. IF no existe ninguna `PruebaRazonamientoFormaA` configurada en la base de datos, THEN THE `ExamenServlet` SHALL retornar el código de estado `503 Service Unavailable` con un JSON `{"error": "La prueba no está configurada"}`.
6. THE `ExamenServlet` SHALL establecer el encabezado `Content-Type: application/json; charset=UTF-8` en todas sus respuestas.

---

### Requisito 3: Iniciar examen y registrar evaluado

**User Story:** Como estudiante, quiero que al comenzar la prueba el sistema registre mis datos y cree una hoja de respuestas, para que mis respuestas queden vinculadas a mi identidad y a la sesión correcta.

#### Criterios de Aceptación

1. WHEN se recibe `POST /api/examen/iniciar` con un Payload JSON que contiene `codigoSesion`, `nombre`, `apellido` y `correo`, THE `ExamenServlet` SHALL validar la sesión con `AutenticacionService.validarCodigoSesion(codigoSesion)`.
2. WHEN la sesión es válida, THE `ExamenServlet` SHALL buscar un `Evaluado` existente por `correo`; si no existe, SHALL crear y persistir un nuevo `Evaluado` con los campos `nombre`, `apellido` y `correo` del Payload.
3. WHEN el `Evaluado` es localizado o creado, THE `ExamenServlet` SHALL crear y persistir una `HojaRespuesta` vinculada al `Evaluado` y a la `SesionPrueba`, inicializando `horaInicio` con la fecha/hora actual e inicializando un `RespuestaDetalles` en estado omitido por cada `Pregunta` de la `PruebaRazonamientoFormaA` activa.
4. WHEN la `SesionPrueba` tiene estado `PROGRAMADA`, THE `ExamenServlet` SHALL actualizarla a estado `EN_PROGRESO`.
5. WHEN la creación es exitosa, THE `ExamenServlet` SHALL retornar el código de estado `201 Created` con un JSON que contiene `hojaRespuestaId` (UUID del `HojaRespuesta` creado como cadena), `codigoSesion` y `limiteTiempoMinutos`.
6. IF el Payload JSON está ausente, malformado, o le faltan los campos `codigoSesion`, `nombre` o `apellido`, THEN THE `ExamenServlet` SHALL retornar el código de estado `400 Bad Request` con un JSON `{"error": "Campos obligatorios: codigoSesion, nombre, apellido"}`.
7. IF `AutenticacionService` retorna `null` para el `codigoSesion` recibido, THEN THE `ExamenServlet` SHALL retornar el código de estado `404 Not Found` con un JSON `{"error": "Sesión no encontrada o no disponible"}`.
8. IF ocurre una excepción durante la transacción JPA, THEN THE `ExamenServlet` SHALL realizar el rollback de la transacción y retornar el código de estado `500 Internal Server Error` con un JSON `{"error": "Error interno al iniciar el examen"}`.
9. THE `ExamenServlet` SHALL establecer el encabezado `Content-Type: application/json; charset=UTF-8` en todas sus respuestas.

---

### Requisito 4: Guardar respuestas y calcular resultado

**User Story:** Como estudiante, quiero que al finalizar el test mis respuestas se guarden y el sistema calcule automáticamente mi puntuación y percentil, para recibir retroalimentación inmediata sobre mi desempeño.

#### Criterios de Aceptación

1. WHEN se recibe `POST /api/examen/guardar-respuestas` con un Payload JSON que contiene `hojaRespuestaId` (UUID como cadena), `horaFin` (ISO-8601) y el arreglo `detalles` (con `numeroPregunta`, `opcionSeleccionada` y `esOmitida` por elemento), THE `RespuestasServlet` SHALL localizar la `HojaRespuesta` correspondiente mediante JPA.
2. WHEN la `HojaRespuesta` es encontrada, THE `RespuestasServlet` SHALL iterar sobre cada elemento de `detalles`, invocar `RespuestaDetalles.registrarRespuesta(opcionSeleccionada)` para las respuestas no omitidas y marcar `esOmitida = true` para las omitidas, persistiendo los cambios.
3. WHEN todas las respuestas son registradas, THE `RespuestasServlet` SHALL invocar `CorreccionAutomaticaService.procesarHojaRespuesta(hoja)` para calcular y persistir el `Resultado`.
4. WHEN la corrección es exitosa, THE `RespuestasServlet` SHALL retornar el código de estado `200 OK` con un JSON que contiene `hojaRespuestaId`, `puntuacionDirecta` (entero), `percentil` (entero) e `interpretacion` (cadena).
5. IF el Payload JSON está ausente, malformado, o le falta el campo `hojaRespuestaId`, THEN THE `RespuestasServlet` SHALL retornar el código de estado `400 Bad Request` con un JSON `{"error": "El campo hojaRespuestaId es obligatorio"}`.
6. IF no se encuentra ninguna `HojaRespuesta` con el `hojaRespuestaId` recibido, THEN THE `RespuestasServlet` SHALL retornar el código de estado `404 Not Found` con un JSON `{"error": "HojaRespuesta no encontrada"}`.
7. IF `CorreccionAutomaticaService` retorna `null`, THEN THE `RespuestasServlet` SHALL retornar el código de estado `500 Internal Server Error` con un JSON `{"error": "Error al calcular el resultado"}`.
8. IF ocurre una excepción durante la transacción JPA, THEN THE `RespuestasServlet` SHALL realizar el rollback de la transacción y retornar el código de estado `500 Internal Server Error` con un JSON `{"error": "Error interno al guardar respuestas"}`.
9. THE `RespuestasServlet` SHALL establecer el encabezado `Content-Type: application/json; charset=UTF-8` en todas sus respuestas.

---

### Requisito 5: Consultar resultado calculado

**User Story:** Como evaluador o estudiante, quiero poder consultar el resultado de una prueba mediante el ID de la hoja de respuestas, para revisar la puntuación directa, el percentil y la interpretación sin necesidad de recalcularlos.

#### Criterios de Aceptación

1. WHEN se recibe `GET /api/examen/resultado` con el parámetro de consulta `hojaRespuestaId`, THE `ResultadoServlet_API` SHALL localizar la `HojaRespuesta` correspondiente mediante JPA y luego acceder a su `Resultado` asociado.
2. WHEN el `Resultado` es encontrado, THE `ResultadoServlet_API` SHALL retornar el código de estado `200 OK` con un JSON que contiene `hojaRespuestaId`, `puntuacionDirecta` (entero), `percentil` (entero) e `interpretacion` (cadena).
3. IF el parámetro `hojaRespuestaId` está ausente o en blanco, THEN THE `ResultadoServlet_API` SHALL retornar el código de estado `400 Bad Request` con un JSON `{"error": "El parámetro hojaRespuestaId es obligatorio"}`.
4. IF no se encuentra ninguna `HojaRespuesta` con el `hojaRespuestaId` recibido, THEN THE `ResultadoServlet_API` SHALL retornar el código de estado `404 Not Found` con un JSON `{"error": "HojaRespuesta no encontrada"}`.
5. IF la `HojaRespuesta` existe pero aún no tiene un `Resultado` asociado (la corrección no ha sido ejecutada), THEN THE `ResultadoServlet_API` SHALL retornar el código de estado `404 Not Found` con un JSON `{"error": "El resultado aún no está disponible"}`.
6. THE `ResultadoServlet_API` SHALL establecer el encabezado `Content-Type: application/json; charset=UTF-8` en todas sus respuestas.

---

### Requisito 6: Serialización y contrato JSON

**User Story:** Como desarrollador frontend, quiero que los JSON de respuesta tengan una estructura estable y predecible, para poder implementar el cliente React sin sorpresas en los nombres de campos ni en los tipos de datos.

#### Criterios de Aceptación

1. THE `API_Layer` SHALL serializar todos los UUID como cadenas en formato estándar (`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`), no como objetos.
2. THE `API_Layer` SHALL serializar todas las fechas en formato ISO-8601 (`yyyy-MM-dd'T'HH:mm:ss`).
3. THE `API_Layer` SHALL retornar el JSON con el charset UTF-8, de modo que los caracteres especiales del español (tildes, ñ) se transmitan correctamente.
4. WHEN ocurre cualquier error no contemplado específicamente, THE `API_Layer` SHALL retornar siempre un JSON con la clave `"error"` y una descripción legible, en lugar de una página de error HTML.
5. THE `API_Layer` SHALL producir JSON válido: para verificar la propiedad de ida y vuelta (round-trip), un objeto serializado por la `API_Layer` y luego deserializado SHALL producir un objeto con valores equivalentes a los originales.

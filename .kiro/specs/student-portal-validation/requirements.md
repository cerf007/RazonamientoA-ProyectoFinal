# Requirements Document

## Introduction

Este documento describe los requisitos funcionales del **Portal del Estudiante** para la prueba psicométrica BFA: Razonamiento Forma A. La funcionalidad cubre tres áreas interconectadas que transforman el componente `PortalExamen.jsx` (actualmente en modo mock) en un flujo completo conectado al backend Java/OpenXava en `http://localhost:8080/Razonamiento`.

Las tres áreas son:

1. **Validación de identidad**: Verificación cruzada entre la cédula nicaragüense y la fecha de nacimiento antes de permitir el inicio del examen.
2. **Temporizador de cuenta regresiva**: Contador visible de 10 minutos que inicia al comenzar la prueba.
3. **Envío de respuestas**: Dos rutas de envío — automático al expirar el tiempo, y manual mediante el botón "Enviar" — con bloqueo de la UI y persistencia en el backend.

---

## Glossary

- **Portal**: El componente React (`PortalExamen.jsx`) que constituye la interfaz del estudiante en `http://localhost:5173`.
- **Validator**: Módulo de lógica pura (función o hook) en el frontend que ejecuta la validación cruzada cédula/fecha de nacimiento.
- **IdentityForm**: Vista del formulario de datos personales dentro del Portal (nombre, fecha de nacimiento, cédula, correo, código de sesión).
- **ExamView**: Vista del examen dentro del Portal que muestra las preguntas, el temporizador y el botón de envío.
- **Timer**: Mecanismo de cuenta regresiva en el Portal que descuenta en tiempo real desde el límite configurado.
- **SubmitHandler**: Lógica del Portal que recopila las respuestas, bloquea la UI y envía el payload al backend.
- **Cédula**: Documento de identidad nicaragüense con formato `AAA-DDMMYY-NNNNX`, donde `DDMMYY` es la fecha de nacimiento codificada.
- **Bloque_Central**: Los seis dígitos centrales de la cédula ubicados entre los dos guiones (`DDMMYY`).
- **API_Backend**: Los endpoints REST del backend Java/OpenXava consumidos por el Portal: `GET /api/examen/preguntas`, `POST /api/examen/iniciar`, `POST /api/examen/guardar-respuestas`, `GET /api/examen/resultado`.
- **codigoSesion**: Cadena de 8 caracteres alfanuméricos en mayúsculas que identifica una sesión de prueba programada.
- **hojaRespuestaId**: UUID en formato de cadena que identifica la hoja de respuestas del evaluado.
- **Payload_Respuestas**: Cuerpo JSON enviado a `POST /api/examen/guardar-respuestas`, que contiene `hojaRespuestaId`, `horaFin` y el arreglo `detalles`.
- **Estado_Bloqueado**: Estado del Portal en que todos los controles de entrada (radio buttons, botón de envío) quedan deshabilitados, impidiendo cualquier interacción adicional.
- **Omitida**: Clasificación de una pregunta no respondida; su `opcionSeleccionada` es cadena vacía y `esOmitida` es `true`.

---

## Requirements

### Requisito 1: Formulario de ingreso de datos personales

**User Story:** Como estudiante, quiero ingresar mi nombre completo, fecha de nacimiento, cédula de identidad, correo electrónico y código de sesión en un formulario antes de iniciar la prueba, para que el sistema pueda identificarme y registrarme correctamente.

#### Criterios de Aceptación

1. WHEN el estudiante accede al `Portal`, THE `Portal` SHALL presentar el `IdentityForm` como pantalla inicial antes de cargar las preguntas del examen.
2. THE `IdentityForm` SHALL incluir los campos: nombre (máx. 100 caracteres), apellido (máx. 100 caracteres), fecha de nacimiento (selector de calendario), cédula (campo de texto, máx. 16 caracteres), correo electrónico y código de sesión (máx. 8 caracteres).
3. WHEN el estudiante envía el `IdentityForm`, THE `Portal` SHALL verificar que los campos nombre, apellido, fecha de nacimiento, cédula, correo electrónico y código de sesión no estén vacíos antes de ejecutar cualquier validación adicional.
4. IF alguno de los campos obligatorios (nombre, apellido, fecha de nacimiento, cédula, correo electrónico, código de sesión) está vacío al enviar el formulario, THEN THE `IdentityForm` SHALL mostrar un mensaje de error indicando el campo faltante y detener el flujo.
5. WHEN el estudiante ingresa un valor en el campo cédula, THE `IdentityForm` SHALL aceptar únicamente valores que coincidan con la expresión regular `^[A-Za-z]{3}-\d{6}-\d{4}[A-Za-z0-9]$` (formato `AAA-DDMMYY-NNNNX`).
6. IF el valor del campo cédula no coincide con el formato `AAA-DDMMYY-NNNNX`, THEN THE `IdentityForm` SHALL mostrar un mensaje de error de formato de cédula no válido junto con un ejemplo correcto, y detener el flujo.
7. IF el valor del campo correo electrónico no contiene el carácter `@` seguido de al menos un carácter y un dominio, THEN THE `IdentityForm` SHALL mostrar un mensaje indicando que el correo electrónico no tiene un formato válido y detener el flujo.
8. WHEN todos los campos del `IdentityForm` pasan las validaciones de formato, THE `Portal` SHALL continuar hacia la verificación cruzada de cédula y fecha de nacimiento.

---

### Requisito 2: Validación cruzada cédula / fecha de nacimiento

**User Story:** Como administrador del sistema, quiero que el frontend verifique que la fecha de nacimiento ingresada por el estudiante coincida con el bloque central de su cédula, para detectar errores de captura antes de registrar al evaluado.

#### Criterios de Aceptación

1. WHEN el estudiante envía el `IdentityForm` con cédula y fecha de nacimiento completadas, THE `Validator` SHALL eliminar todos los guiones de la cadena de la cédula y extraer los caracteres en las posiciones 4–9 (base-0, 6 caracteres) como el `Bloque_Central`.
2. WHEN el `Validator` extrae el `Bloque_Central`, THE `Validator` SHALL formatear la fecha de nacimiento del selector de calendario como la cadena `DDMMYY` usando exactamente dos dígitos para el día (01–31), dos para el mes (01–12) y dos para el año (últimos dos dígitos, con cero a la izquierda si es necesario).
3. WHEN el `Bloque_Central` es léxicamente idéntico (comparación de cadenas, case-insensitive no aplica pues son dígitos) a la cadena `DDMMYY` formateada de la fecha de nacimiento, THE `Validator` SHALL retornar resultado válido y THE `Portal` SHALL continuar al siguiente paso del flujo.
4. WHEN el `Bloque_Central` no es léxicamente idéntico a la cadena `DDMMYY` de la fecha de nacimiento, THE `Validator` SHALL retornar resultado inválido y THE `IdentityForm` SHALL mostrar el mensaje: "Error de Validación: La fecha de nacimiento proporcionada no coincide con los registros de tu cédula de identidad. Por favor, verifica ambos campos antes de continuar."
5. WHEN el `Validator` retorna resultado inválido, THE `Portal` SHALL detener el flujo e impedir que se realice la llamada a `POST /api/examen/iniciar`.
6. FOR ALL pares `(cedula, fechaNacimiento)` donde los 6 dígitos centrales de la cédula (sin guiones, posiciones 4–9) sean igual a la fecha formateada como `DDMMYY`, THE `Validator` SHALL retornar resultado válido — esta propiedad debe mantenerse para cualquier fecha válida en el rango 1900–2099.

---

### Requisito 3: Llamada a la API para iniciar el examen

**User Story:** Como estudiante, quiero que al superar la validación de identidad el sistema registre mis datos en el backend y me entregue las preguntas reales de la prueba, para poder comenzar el examen con el contenido oficial.

#### Criterios de Aceptación

1. WHEN el `Validator` retorna resultado válido, THE `Portal` SHALL realizar la llamada `GET /api/examen/preguntas?codigoSesion=<codigoSesion>` al `API_Backend` para obtener el arreglo de preguntas y el campo `limiteTiempoMinutos`.
2. WHEN `GET /api/examen/preguntas` retorna código `200 OK`, THE `Portal` SHALL realizar la llamada `POST /api/examen/iniciar` con el payload JSON: `{ "codigoSesion": string, "nombre": string, "apellido": string, "correo": string, "cedula": string, "fechaNacimiento": string (ISO-8601) }`.
3. WHEN `POST /api/examen/iniciar` retorna código `201 Created`, THE `Portal` SHALL almacenar el `hojaRespuestaId` (UUID string) y el `limiteTiempoMinutos` (entero ≥ 1) recibidos para usarlos en el envío de respuestas y en la configuración del `Timer`.
4. WHEN `POST /api/examen/iniciar` retorna código `201 Created`, THE `Portal` SHALL inicializar el estado de respuestas marcando todas las preguntas como `Omitida` (`esOmitida: true, opcionSeleccionada: ''`) y transicionar a la `ExamView`.
5. IF `GET /api/examen/preguntas` retorna código `404`, THEN THE `Portal` SHALL mostrar el mensaje "Sesión no encontrada. Verifica tu código de sesión." y permanecer en el `IdentityForm`.
6. IF `POST /api/examen/iniciar` retorna código `404`, `400` o `422`, THEN THE `Portal` SHALL mostrar el mensaje contenido en el campo `error` del JSON de respuesta y permanecer en el `IdentityForm`.
7. IF `GET /api/examen/preguntas` o `POST /api/examen/iniciar` producen un error de red, un error `5xx`, o cualquier código de respuesta no contemplado específicamente, THEN THE `Portal` SHALL mostrar el mensaje "Error de conexión. Verifica tu red e intenta de nuevo." y permanecer en el `IdentityForm`.
8. WHILE el `Portal` espera respuesta de `GET /api/examen/preguntas` o `POST /api/examen/iniciar`, THE `Portal` SHALL mostrar un indicador de carga visible y deshabilitar el botón de inicio para evitar envíos duplicados.

---

### Requisito 4: Temporizador de cuenta regresiva

**User Story:** Como estudiante, quiero ver un temporizador visible que cuente hacia atrás desde el tiempo límite configurado para la sesión, para saber cuánto tiempo me queda y administrar mi ritmo de respuesta.

#### Criterios de Aceptación

1. WHEN el `Portal` transiciona a la `ExamView`, THE `Timer` SHALL inicializarse con el valor `limiteTiempoMinutos` recibido de `POST /api/examen/iniciar` y comenzar la cuenta regresiva en menos de 1 segundo desde la transición.
2. IF `limiteTiempoMinutos` está ausente, es menor a 1 o no es un entero, THEN THE `Portal` SHALL mostrar un mensaje de error y no transicionar a la `ExamView`.
3. WHILE el `Timer` está en curso, THE `ExamView` SHALL mostrar el tiempo restante en formato `MM:SS` actualizado exactamente cada segundo.
4. WHILE el tiempo restante es mayor a 60 segundos, THE `ExamView` SHALL mostrar el `Timer` sin ningún indicador visual de alerta o urgencia.
5. WHEN el tiempo restante llega a 60 segundos o menos y el `Timer` aún corre, THE `ExamView` SHALL cambiar el estilo del `Timer` a un estado de urgencia visual distinguible del estado neutro.
6. THE `Timer` SHALL estar posicionado de forma fija en la pantalla de la `ExamView` de modo que no desaparezca del viewport al hacer scroll vertical por la lista de preguntas.
7. WHEN el `Timer` llega a `00:00`, THE `Timer` SHALL congelarse mostrando `00:00` y THE `Portal` SHALL invocar al `SubmitHandler` de forma automática con el estado de respuestas en ese momento.
8. IF el `SubmitHandler` invocado por el `Timer` produce un error al enviar las respuestas, THEN THE `ExamView` SHALL mostrar un mensaje de error con instrucciones para contactar al evaluador, y el `Portal` SHALL permanecer en `Estado_Bloqueado`.

---

### Requisito 5: Navegación y selección de respuestas durante el examen

**User Story:** Como estudiante, quiero poder responder, omitir o cambiar mis respuestas libremente mientras el temporizador corra, para revisar y corregir mis selecciones antes de enviar.

#### Criterios de Aceptación

1. WHILE el `Timer` está en curso y el `Portal` no está en `Estado_Bloqueado`, THE `ExamView` SHALL permitir al estudiante seleccionar una opción (A, B, C o D) para cualquier pregunta, y SHALL reflejar visualmente la opción seleccionada en el control correspondiente.
2. WHILE el `Timer` está en curso y el `Portal` no está en `Estado_Bloqueado`, THE `ExamView` SHALL permitir al estudiante cambiar su selección en cualquier pregunta ya respondida haciendo clic en una opción distinta.
3. WHILE el `Timer` está en curso y el `Portal` no está en `Estado_Bloqueado`, THE `ExamView` SHALL permitir al estudiante desplazarse libremente por todas las preguntas sin restricción de orden.
4. WHILE el `Timer` está en curso y el `Portal` no está en `Estado_Bloqueado`, WHEN el estudiante selecciona la opción `opc` para la pregunta `n`, THE `Portal` SHALL actualizar el estado de respuestas con `opcionSeleccionada = opc` y `esOmitida = false` para la pregunta `n` en menos de 1 segundo.
5. WHILE el `Timer` está en curso y el `Portal` no está en `Estado_Bloqueado`, WHEN el estudiante activa la opción ya seleccionada en la pregunta `n` (deselección), THE `Portal` SHALL actualizar el estado de respuestas con `opcionSeleccionada = ''` y `esOmitida = true` para la pregunta `n` en menos de 1 segundo.
6. IF el `Portal` está en `Estado_Bloqueado`, THEN THE `ExamView` SHALL deshabilitar todos los controles de selección de respuesta y preservar el estado de respuestas registrado en el momento del bloqueo sin permitir ninguna modificación.

---

### Requisito 6: Envío de respuestas

**User Story:** Como estudiante, quiero que mis respuestas se envíen automáticamente al expirar el tiempo o manualmente al presionar "Enviar", para que el sistema registre y califique mi desempeño sin perder ninguna respuesta.

#### Criterios de Aceptación

1. WHEN el `Timer` llega a `00:00`, THE `SubmitHandler` SHALL poner el `Portal` en `Estado_Bloqueado` deshabilitando todos los radio buttons y el botón "Enviar" en menos de 300 ms.
2. WHEN el estudiante hace clic en el botón "Enviar" mientras el `Timer` está en curso, THE `SubmitHandler` SHALL poner el `Portal` en `Estado_Bloqueado` deshabilitando todos los radio buttons y el botón "Enviar" en menos de 300 ms.
3. WHEN el `Portal` entra en `Estado_Bloqueado`, THE `SubmitHandler` SHALL construir el `Payload_Respuestas` con: `hojaRespuestaId`, `horaFin` (timestamp ISO-8601 del momento exacto de envío) y el arreglo `detalles` donde cada elemento contiene `numeroPregunta` (entero), `opcionSeleccionada` (cadena, vacía si omitida) y `esOmitida` (booleano).
4. THE `SubmitHandler` SHALL incluir en `detalles` exactamente una entrada por cada pregunta del examen — las preguntas no respondidas se incluyen con `esOmitida: true` y `opcionSeleccionada: ''`.
5. WHEN el `Payload_Respuestas` está construido, THE `SubmitHandler` SHALL enviar `POST /api/examen/guardar-respuestas` con el `Payload_Respuestas` como cuerpo JSON; WHEN la respuesta retorna código `200 OK` con los campos `puntuacionDirecta`, `percentil` e `interpretacion`, THE `Portal` SHALL transicionar a la pantalla de resultado final mostrando dichos campos; IF alguno de esos campos está ausente en el JSON de respuesta, THE `Portal` SHALL mostrar un mensaje de error indicando que el resultado no está disponible.
6. IF `POST /api/examen/guardar-respuestas` retorna código `4xx` o `5xx`, THEN THE `Portal` SHALL mostrar un mensaje de error indicando el código de fallo y SHALL permanecer en `Estado_Bloqueado` para evitar reenvíos accidentales.
7. IF `POST /api/examen/guardar-respuestas` produce un error de red, THEN THE `Portal` SHALL mostrar el mensaje "Error de conexión al enviar respuestas. Contacta al evaluador.", SHALL permanecer en `Estado_Bloqueado`, y la petición HTTP en curso (si existe) no SHALL ser cancelada, permitiendo que complete si la conexión se restablece.
8. WHEN el `SubmitHandler` es invocado mientras una llamada previa al mismo endpoint ya está en curso, THE `SubmitHandler` SHALL ignorar la invocación duplicada, sin cancelar la petición en curso, hasta que esta complete.

---

### Requisito 7: Re-validación de identidad en el backend

**User Story:** Como administrador del sistema, quiero que el backend re-valide la coincidencia cédula/fecha de nacimiento al recibir `POST /api/examen/iniciar`, para que la validación no pueda ser eludida deshabilitando el frontend.

#### Criterios de Aceptación

1. WHEN el `API_Backend` recibe `POST /api/examen/iniciar`, THE `API_Backend` SHALL verificar que los campos `cedula` y `fechaNacimiento` están presentes y no son cadenas vacías; IF alguno está ausente o en blanco, THE `API_Backend` SHALL retornar `400 Bad Request` con un JSON `{"error": "Los campos cedula y fechaNacimiento son obligatorios"}`.
2. WHEN los campos `cedula` y `fechaNacimiento` están presentes, THE `API_Backend` SHALL eliminar todos los guiones de `cedula` y extraer los 6 caracteres en las posiciones 4–9 (base-0) como el `Bloque_Central`, luego formatear `fechaNacimiento` (ISO-8601) como `DDMMYY` para la comparación.
3. IF el `Bloque_Central` no es idéntico a la fecha de nacimiento formateada como `DDMMYY`, THEN THE `API_Backend` SHALL retornar el código de estado `422 Unprocessable Entity` con un JSON `{"error": "La fecha de nacimiento no coincide con la cédula de identidad"}`.
4. IF el `Portal` recibe código `422` de `POST /api/examen/iniciar`, THEN THE `Portal` SHALL mostrar el mensaje contenido en el campo `error` del JSON de respuesta y regresar al `IdentityForm`.
5. WHEN el `Bloque_Central` coincide con la fecha de nacimiento formateada y los campos `codigoSesion`, `nombre`, `apellido`, `cedula` y `fechaNacimiento` son todos válidos, THE `API_Backend` SHALL continuar el flujo de registro del evaluado y creación de la `HojaRespuesta` según los criterios del Requisito 3 del spec `servlet-rest-api`.

---

### Requisito 8: Pantalla de resultado final

**User Story:** Como estudiante, quiero ver mi puntuación directa, percentil e interpretación al finalizar la prueba, para conocer mi desempeño de forma inmediata.

#### Criterios de Aceptación

1. WHEN el `Portal` recibe código `200 OK` de `POST /api/examen/guardar-respuestas`, THE `Portal` SHALL transicionar a la pantalla de resultado final en menos de 2 segundos desde la recepción de la respuesta.
2. WHEN el `Portal` transiciona a la pantalla de resultado final, THE `Portal` SHALL mostrar los valores de `puntuacionDirecta`, `percentil` e `interpretacion` obtenidos directamente del cuerpo de la respuesta `200 OK` de `POST /api/examen/guardar-respuestas`, sin requerir navegación adicional ni recarga de página.
3. WHILE el `Portal` está en la pantalla de resultado final, THE `Portal` SHALL mostrar el campo `puntuacionDirecta` con la etiqueta "Puntuación Directa", el campo `percentil` con la etiqueta "Percentil" y el campo `interpretacion` con la etiqueta "Interpretación".
4. WHILE el `Portal` está en la pantalla de resultado final, THE `Portal` SHALL deshabilitar u ocultar todos los controles de navegación que permitan regresar al examen o modificar respuestas.
5. IF `POST /api/examen/guardar-respuestas` retorna un código distinto de `200 OK`, THEN THE `Portal` SHALL mostrar un mensaje de error visible en la pantalla actual indicando que el resultado no pudo obtenerse, sin transicionar a la pantalla de resultado final.

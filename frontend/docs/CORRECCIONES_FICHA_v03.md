> Integración v08: base develop 7ce01a5. Se conservan V3 de sesiones, V4 de secciones y la carga masiva. La ficha utiliza V5. Las verificaciones históricas siguientes corresponden a versiones anteriores. Consultar INTEGRACION_FICHA_DEVELOP.md.

# AsisControl — Correcciones de ficha de estudiante v0.3

Fecha: 22/10/2026 (Perú). Base: el avance FE2 v0.2, integrado con `develop` en `5f26d46`. La consulta remota confirmó la misma base y la rama `feature/frontend-inicial` en `3a96031`. Se mantiene el header y la organización del equipo.

## Causas confirmadas

| Incidencia | Causa en el código anterior | Solución |
|---|---|---|
| La fecha captura el foco al escribir en otro campo | El efecto dependía de `errors`; `change()` creaba otro objeto `errors` en cada tecla. El efecto reenfocaba el primer error restante. | El foco solo se solicita al intentar guardar o recibir errores de ese envío. Blur valida el campo abandonado sin enfocar otro. |
| Se aceptan letras en teléfono o números en apellidos | Faltaban filtros de entrada; la validación anterior solo revisaba longitudes en nombres. | Filtros al escribir y pegar; el pegado se limpia antes de aplicar la longitud máxima. El servidor rechaza esos caracteres por API directa. |
| Opcionales que el servidor exige | `PersonaRequest` exigía segundo apellido, documento, sexo y correo; las columnas también eran NOT NULL. | Validación específica de ficha, columnas anulables y normalización a null. Las reglas obligatorias de los demás perfiles se mantienen mediante grupos de validación. |
| Error genérico sin campos | El backend exigía `codigoAlumno`, que el frontend no envía porque el código es automático. El mapeo descartaba ese campo y reemplazaba todos los HTTP 400 por un texto genérico. | Código automático en el alta web, errores visibles por campo y explicación del contrato antiguo si se sigue usando ese backend. |
| Posible autofill no registrado | El color azul no demuestra un fallo; el formulario solo tomaba el estado React al enviar. | El envío toma una instantánea FormData del formulario real y la valida. Se prueba autocompletado sin eventos React. |

La captura 3 incluye apellidos con números y nacimiento en octubre de 2026. Esos valores se rechazan correctamente con las reglas nuevas. Un ejemplo válido para probar es Diego / Ibarra / López, nacimiento 07/10/2012, DNI 12345678 y celular 999742200, siempre que el documento y correo no estén registrados en otra persona.

## Reglas aplicadas

| Campo | Obligatorio | Regla |
|---|---|---|
| Nombres | Sí | 2–100 caracteres, letras Unicode (tildes, ñ, ü), espacios, guion y apóstrofo recto o curvo. Debe contener letras. |
| Primer apellido | Sí | 2–80 caracteres; mismos caracteres permitidos. |
| Segundo apellido | No | Hasta 80 caracteres; mismos caracteres permitidos si se informa. |
| Nacimiento | Sí | Fecha válida; 10 a 20 años cumplidos, calculados por cumpleaños con fecha de Lima en servidor. No se admiten fechas futuras. |
| Tipo y número de documento | Condicional | Ambos vacíos son válidos. Informar uno obliga a completar el otro; los asteriscos cambian con esa condición. |
| DNI | Si se elige DNI | Exactamente 8 dígitos; conserva ceros iniciales. |
| Carné de extranjería | Si se elige CE | Exactamente 9 dígitos. Referencia: guía de SBS sobre documentos para población refugiada, https://www.sbs.gob.pe/Portals/3/GuiaPracticaPoblacionRefugiada.pdf . |
| Pasaporte | Si se elige pasaporte | 8–12 letras o números; mayúsculas al guardar, sin espacios ni guiones. Es la regla del proyecto, no una afirmación sobre todos los pasaportes del mundo. |
| Sexo | No | Opción del catálogo o null. |
| Correo de contacto | No | Formato de correo y máximo 150 caracteres cuando se informa. |
| Teléfono | No | Celular peruano: exactamente 9 dígitos, empieza con 9; entrada limitada a 9. |
| Dirección | No | Hasta 250 caracteres. |
| Motivo | Condicional | Corrección de identidad: obligatorio, 10–1000 caracteres. La API lo comprueba y conserva auditoría de anterior/nuevo, actor y fecha. |
| Código institucional | Automático | La API web genera ESTU-000001, etc., con contador bloqueado en transacción e índice único. No se modifica al editar. La importación interna conserva sus códigos legados. |

### Cambios frente a decisiones anteriores

- El rango **10–20** es una propuesta para el alcance de secundaria solicitada en esta corrección, no una norma oficial ni un valor que ya estuviera validado en el backlog. Sustituye la validación anterior de cualquier fecha hasta hoy de CA-05/OBS-037; actualizar la matriz de reglas/CA antes de aceptar la historia.
- CE de 9 dígitos sustituye la propuesta anterior de CE alfanumérico 8–12 de RN-137. El pasaporte mantiene 8–12 alfanuméricos.
- Celular de 9 dígitos comenzando con 9 sustituye el formato telefónico general 7–20 caracteres.
- Los apellidos se alinean al límite existente de 80 en DTO/base de datos; nombres mantiene 100.
- Las fichas consultadas se siguen mostrando aunque su edad actual esté fuera del rango. Guardar una ficha sí aplica la regla 10–20. Si se requiere corregir contacto de egresados mayores, hay que definir expresamente esa excepción.

## Instalación y pruebas (Windows / PowerShell)

El ZIP completo conserva `AsisControl/frontend` y `AsisControl/backend`. Extraer en una carpeta nueva para probar esta entrega. No incluye node_modules, compilados, bases de datos ni configuraciones privadas. Conserva tu configuración local del backend y frontend.

**Importante:** esta corrección incluye backend. Reemplazar solo frontend no resuelve los rechazos del servidor antiguo. Para integrar por archivos, ver `backend/docs/BE2_FICHA_VALIDACIONES.md`.

Desde `frontend`:

```powershell
npm.cmd ci
npm.cmd test
npm.cmd run lint
npm.cmd run build
```

Si no existe `.env.local`, copiar `.env.example` y ajustar `NEXT_PUBLIC_API_BASE_URL` al backend actualizado. Después:

```powershell
npm.cmd run dev
```

Desde `backend`, con JDK 21:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests
```

Para arrancar, usar el mismo perfil y variables de conexión descritos en su README. En el perfil `mysql`, Flyway aplica automáticamente `V5__ficha_alumno_opcionales.sql`; no modificar V1/V2 ni ejecutar V3 dos veces de forma manual. El perfil `local` existente utiliza H2 en memoria, no acredita persistencia en MySQL.

Las siete alertas npm reportadas anteriormente siguen pendientes de la actualización de dependencias del equipo. Esta entrega no cambia versiones ni aplica `audit fix --force`.

## Comprobación manual

1. Dejar nacimiento vacío, completar nombres y primer apellido, pulsar Guardar. El foco llega a nacimiento una vez.
2. Sin corregir nacimiento, escribir y pegar nombres, apellidos, teléfono y DNI. El cursor permanece en el campo que se está editando.
3. Probar `Muñoz`, `Güemes`, `D’Ávila` y `Pérez-López`; números y símbolos ajenos al formato no permanecen en estos campos.
4. Probar teléfono de ocho dígitos o que empiece con 8. El mensaje aparece al salir del campo; escribir no ejecuta la validación completa.
5. Probar documento vacío, tipo solo, número solo y cada uno de los tres formatos; revisar asteriscos y ayudas.
6. Guardar únicamente con los tres obligatorios y una fecha dentro del rango: la API devuelve 201, código y ficha persistida.
7. Probar documento/correo duplicado, fecha futura e inválida. La respuesta debe identificar el campo y la interfaz mostrarlo y enfocarlo solo tras Guardar.
8. Corregir el dato y reenviar: no debe quedar el mensaje general del intento anterior.
9. Editar identidad: exigir motivo, conservar código y registrar anterior/nuevo con actor y fecha en auditoría.

## Alcance y validación

- Frontend: 53 pruebas de contrato/reglas/DOM, lint y build.
- Backend: 63 pruebas, incluyendo 9 nuevas de API/H2: mínimos, duplicados, formatos, permisos, motivo/auditoría, cumpleaños límite y dos altas simultáneas.
- Navegador Chromium con la aplicación compilada y API simulada: foco con fecha inválida, caracteres, longitud/pegado y captura de autofill; envío y vista de confirmación.
- Los tests de API sí usan los controladores, servicios y persistencia reales sobre H2. La migración V3 y la ejecución completa sobre MySQL deben verificarse en el entorno del equipo.
- US-09 sigue abierta: homonimia real, ciclo de vida/acciones permitidas, consultas históricas y aceptación completa permanecen pendientes de BE2. Esta entrega no presenta esos pendientes como terminados.

## Control de cambios

| Versión | Fecha | Cambio |
|---|---|---|
| 0.3 | 22/10/2026 | Corrección de foco, blur, restricciones, edad, opcionales, autofill, código automático y errores de API; migración y pruebas integradas de backend. |

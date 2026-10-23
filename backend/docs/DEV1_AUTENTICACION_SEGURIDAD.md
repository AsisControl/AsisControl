# Autenticación y seguridad de la iteración 1

Esta entrega del backend cubre US-01, US-03, US-43 y US-59. Reutiliza la gestión
de accesos de Dev 2 y las migraciones de la rama. No modifica el frontend ni
requiere una migración adicional de estructura.

## Identificación y reglas

- El identificador es el nombre de usuario institucional o el correo del acceso,
  sin distinguir mayúsculas. No se añade otro código institucional ni se admite
  autorregistro.
- Solo se habilitan ADMINISTRADOR, DOCENTE, ALUMNO y APODERADO. El frontend puede
  mostrar ALUMNO como Estudiante. Los accesos separados de una misma persona no
  combinan sus permisos.
- La matriz se inicializa en `BootstrapDataInitializer`. Los roles históricos
  adicionales se conservan inactivos y sus sesiones se revocan al inicializar.
  No se reasignan cuentas a otro rol ni se eliminan registros históricos.
- Se conservan cinco intentos fallidos y quince minutos de bloqueo, contraseñas
  de 8 a 64 caracteres con mayúscula, minúscula y número, sin espacios, distintas
  del nombre de usuario y de la contraseña actual. BCrypt admite hasta 72 bytes
  UTF-8; también se valida ese límite.
- Las temporales vencen a las 24 horas. Durante el cambio obligatorio solo se
  permiten identidad actual, cambio de contraseña y cierre de sesión entre las
  operaciones autenticadas; las demás funciones se rechazan.

## Contrato HTTP

Todas las rutas siguientes usan JSON. Las protegidas reciben
`Authorization: Bearer <accessToken>`. Los errores mantienen el formato común
con `status`, `code`, `message`, `path` y `correlationId`.

| Método y ruta | Solicitud o comportamiento | Respuesta |
| --- | --- | --- |
| POST /api/auth/login | `identifier`, `password` | 200: token, vencimiento, usuario, rol, permisos y obligación de cambio. Cookie HttpOnly de renovación. |
| GET /api/auth/me | Sesión vigente | 200: resumen del usuario autenticado. |
| GET /api/roles | Solo administrador | 200: cuatro roles y sus permisos. |
| GET /api/roles/permisos | Solo administrador | 200: catálogo de permisos. |
| PUT /api/usuarios/{id}/contrasena-temporal | Solo administrador; `identidadVerificada: true` y `motivo` no vacío | 200: `usuarioId`, `temporaryPassword`, `expiresAt`. `Cache-Control: no-store`. |
| POST /api/auth/change-password | `currentPassword`, `newPassword`, `confirmPassword` | 200: mensaje que exige iniciar sesión nuevamente. |
| POST /api/auth/logout | Bearer y/o cookie de renovación | 204: sesión revocada y cookie eliminada. El cierre sin credenciales es seguro e idempotente. |
| POST /api/auth/refresh | Cookie de renovación y `Origin` permitido | 200: nuevo token de acceso y cookie rotada. |

### Restablecimiento administrado

Se mantiene la ruta de Dev 2, pero el contrato de restablecimiento cambia:

```json
{
  "identidadVerificada": true,
  "motivo": "Identidad verificada presencialmente"
}
```

El cliente ya no elige ni envía `temporaryPassword`. El servidor genera una
temporal aleatoria de 20 caracteres con `SecureRandom`, guarda únicamente su
hash BCrypt y devuelve la temporal en esa respuesta exclusiva al administrador.
El administrador la entrega por el procedimiento institucional; no se envían
correos, SMS ni solicitudes a servicios externos. No se puede volver a consultar
la contraseña desde el catálogo de usuarios. La respuesta no debe almacenarse en
logs, cachés ni herramientas de analítica del cliente.

Se exige un acceso habilitado; un bloqueo temporal puede restablecerse tras
verificar identidad. Un acceso desactivado o pendiente debe habilitarse mediante
el flujo de Dev 2, no mediante recuperación. Se revocan sesiones y enlaces de
recuperación anteriores, se reinician los intentos y se exige el cambio inicial.
La creación y reactivación de accesos de Dev 2 conservan sus contratos.

### Cambio obligatorio

`confirmPassword` es obligatorio y debe coincidir exactamente con `newPassword`.
Una confirmación ausente produce 400; una diferencia o incumplimiento de política
produce 422 y conserva la obligación de cambio. Un cambio correcto invalida
todas las sesiones y requiere autenticarse con la nueva contraseña.

### Errores y responsabilidades del consumidor

- 401: credenciales rechazadas, sesión inválida, revocada o vencida. El mensaje
  no distingue identificador inexistente de contraseña incorrecta.
- 403: operación fuera de permiso o cambio obligatorio pendiente; este último
  se identifica con `CAMBIO_CONTRASENA_REQUERIDO`.
- 400: campos obligatorios ausentes o formato inválido.
- 422: regla de negocio incumplida, por ejemplo confirmación diferente o intento
  de editar la matriz fija.
- El frontend decide la pantalla según el rol y el indicador de cambio, y limpia
  sus datos temporales al cerrar sesión. El servidor aplica autorización aun
  cuando la solicitud no procede de la interfaz.
- Para usar renovación desde un navegador en otro origen, enviar credenciales
  con las solicitudes y un `Origin` permitido. La cookie requiere HTTPS por
  defecto; en desarrollo HTTP local se puede configurar
  la opción de máquina virtual `-Dapp.security.refresh-cookie.secure=false`.
  No cambiar el valor de
  producción para facilitar una prueba local.

## Verificación

Desde `backend`, `mvnw.cmd verify` compila y ejecuta las pruebas. El perfil `test`
usa H2 aislado de MySQL. `AutenticacionSeguridadHttpTest` prueba rechazos HTTP,
restablecimiento generado, cambio obligatorio, confirmación, revocación de sesión
y deshabilitación de roles históricos. Las pruebas existentes de usuarios,
renovación, recuperación y consultas restringidas comprueban la integración con
Dev 2 y el alcance por propietario.

Las pruebas se deben complementar con el arranque del perfil `mysql` sobre las
migraciones V1, V1.5, V2 y V3 y la consulta de la matriz. No borrar el historial de
Flyway ni recrear una base con datos para aplicar estos cambios de código.

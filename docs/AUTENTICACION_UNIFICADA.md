# Autenticación unificada de AsisControl

Cada persona tiene una cuenta con un identificador `AAAANNNN`. El año se calcula en la zona horaria de Lima y el correlativo anual va de `0001` a `9999`. La cuenta conserva su identificador y su contraseña al incorporar otro rol. Los códigos propios de las fichas de Administración, Docencia, Estudiante y Apoderado se conservan.

La cuenta vive en `AccesoUsuario`. Cada asignación se guarda en `AccesoUsuarioRol`, y cada sesión guarda el rol activo y la versión de ese contexto. El servidor valida la cuenta, la asignación y el perfil institucional habilitado. Los cuatro roles disponibles son Administrador, Docente, Estudiante (`ALUMNO` en la API) y Apoderado.

## Ingreso y gestión de accesos

1. Ingresa con el identificador de ocho dígitos y tu contraseña. Si la contraseña es temporal, cámbiala e ingresa nuevamente.
2. Con un solo rol disponible, la aplicación abre su inicio. Con varios, elige uno. Una cuenta sin roles disponibles muestra el aviso y permite cerrar sesión.
3. Para cambiar de rol, abre el menú de la cuenta y selecciona el rol. La aplicación abre el inicio del rol elegido y sincroniza el contexto en las demás pestañas. El servidor invalida los JWT del contexto anterior. Los permisos de otra asignación requieren cambiar primero al rol correspondiente.
4. Desde **Administración → Usuarios**, el administrador consulta las cuentas, revisa y administra las asignaciones, cambia el estado de la cuenta o restablece la contraseña según el caso. La revocación de un rol afecta las sesiones que lo tenían activo. Si quedan otras asignaciones habilitadas, la cuenta puede volver a operar con ellas. El sistema protege al último administrador habilitado.

`POST /api/auth/login`, `POST /api/auth/refresh` y `GET /api/auth/me` devuelven el estado de la cuenta para la pantalla correspondiente. `PUT /api/auth/rol-activo` autoriza el rol solicitado y emite un JWT para el contexto activo. Antes de elegirlo, el servidor restringe la sesión a las operaciones de cuenta permitidas.

## Altas y credenciales temporales

Las altas individuales y las cargas masivas crean la cuenta en el primer registro, o reutilizan la cuenta de la persona identificada. La ficha nueva de estudiante requiere un tipo y un número válidos de documento, tanto en el formulario como en Excel. Se admiten DNI, carné de extranjería y pasaporte. Las fichas existentes sin documento se conservan y pueden seguir editándose. Ante datos contradictorios para un documento existente, se informa el conflicto para corregir el registro.

La temporal aparece en el resultado del alta confirmada, únicamente para cuentas nuevas. En las cargas masivas, el operador autorizado entrega a cada titular su código y su temporal por el canal que defina el colegio. Si se imprime, entrega cada credencial por separado y retira las copias sobrantes. Evita compartir el listado completo, guardarlo en una carpeta común o copiarlo en tickets. Si se pierde, restablécela desde Usuarios después de verificar la identidad y registrar el motivo.

La recuperación por correo pertenece a la cuenta. Requiere un correo de recuperación confirmado. El contacto de la ficha sirve para las comunicaciones del perfil. El backend conserva la contraseña mediante su hash y registra las operaciones administrativas y los cambios de rol en auditoría.

## Prueba local

- Con H2 y un administrador inicial configurado, toma el identificador de ocho dígitos que aparece en el log de arranque. Usa la temporal que configuraste en `BOOTSTRAP_ADMIN_PASSWORD`.
- Si activaste la demo local de pagos, los logs identifican el código asignado a cada cuenta de demostración. Usa la clave configurada para esa demo. Los textos `demo.pagos.1` y `demo.pagos.2` identifican los datos de la demostración.
- Para probar el cambio de rol, registra como Docente a una persona que ya tenga perfil de Apoderado, con el mismo documento y los datos de identidad correspondientes. Inicia sesión con el código original y abre una segunda pestaña antes de cambiar de rol.

## Migración y puesta en servicio

`V22__autenticacion_unificada.sql` se aplica con Flyway sobre el esquema versionado. La migración agrupa por `id_persona`, conserva una credencial por persona, asigna los códigos nuevos y revoca sesiones, renovaciones y enlaces de recuperación anteriores. Conserva el mapa de identificadores históricos y las referencias de notificaciones por rol. Director, Secretaría y Tesorería quedan deshabilitados y sus referencias históricas se conservan.

1. Ensaya la migración sobre una copia de MySQL 8 con el mismo historial Flyway que el destino. Revisa los accesos por persona, los roles habilitados, la vigencia de la credencial del administrador, los correos verificados distintos en una misma persona y la capacidad de cada correlativo anual. Comprueba la restauración del respaldo antes del corte.
2. Durante el corte, detén las instancias que escriben con el esquema anterior y toma un respaldo de la base. Inicia la versión nueva del backend para que Flyway ejecute las migraciones y Hibernate compruebe el esquema. Despliega el frontend compatible en el mismo corte.
3. Desde la cuenta administrativa comprobada, verifica el acceso con su código nuevo y la contraseña conservada, o usa el procedimiento de restablecimiento previsto para el corte. Entrega individualmente los códigos nuevos. La recuperación por correo conserva la confirmación cuando hay un único correo verificado elegible por persona. Si hay correos verificados distintos, confirma uno institucionalmente.
4. Si el corte falla antes de habilitar tráfico, restaura el respaldo anterior y vuelve a desplegar las versiones anteriores de backend y frontend. MySQL confirma operaciones DDL de forma implícita. Revisa los cambios aplicados antes de repetir una migración fallida. Si ya hubo operaciones con la versión nueva, conserva una copia de esa base y acuerda cómo reconciliar sus cambios antes de restaurar.

## Verificación de regresión

| Fase | Evidencia esperada |
|---|---|
| Migración | Una cuenta por persona, estado original de cada asignación, historial y notificaciones conservados, correlativos por año y enlaces legados revocados. |
| Autenticación | Cuentas con cero, uno y varios roles. Cambio inicial, recuperación, cambio de rol y revocación con dos pestañas. Petición directa denegada cuando depende de otro rol. |
| Altas | Fichas nuevas individuales y masivas con documento válido. Rechazo de documento faltante o contradictorio, reutilización de cuenta y entrega de una temporal solo en el alta nueva. |
| Administración | Protección del último administrador ante bajas concurrentes. Revocar un rol conserva la cuenta y las otras asignaciones. |
| Integración | Suite de Maven con `clean verify`, pruebas, lint y compilación de frontend. Ensayo de Flyway y del recorrido de acceso sobre MySQL 8. |

## Trazabilidad

- **US-01 y US-59** · Login con un identificador institucional, recuperación por cuenta y cambio obligatorio de la temporal.
- **US-02 y US-03** · Administración de cuentas y asignaciones, catálogo fijo de cuatro roles y autorización bajo un solo contexto.
- **US-04** · Historial de accesos y auditoría de cambios con el rol activo del actor.
- **US-09 y US-10** · Alta de perfiles y cuenta, con documento obligatorio para fichas nuevas de estudiante. Las asociaciones familiares conservan su flujo.
- **US-37, US-51 y US-52** · Alta individual de docentes y provisión o reutilización de cuentas durante el registro masivo.
- **RN-113 a RN-116** · Cuatro roles, una cuenta por persona, identificador anual y activación mediante el proceso institucional. **RN-138** sigue aplicándose a los códigos de perfil.

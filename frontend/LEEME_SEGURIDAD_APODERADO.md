# Actualización de AsisControl: acceso, recuperación y solicitudes del apoderado

Versión 1.4 · 2 de octubre de 2026.

Esta entrega amplía la versión Administración y Matrícula. Conserva el login, header con nombre y rol, navegación y estilos del coordinador, las fichas, los accesos centralizados y los módulos de configuración, auditoría y logs que ya veníamos trabajando.

## 1. Qué contiene

| Parte | Comportamiento implementado |
|---|---|
| Login | Cuatro roles, credenciales inválidas con respuesta genérica, cinco intentos fallidos y bloqueo de 15 minutos sin prolongarlo con intentos posteriores. |
| Contraseña | Política compartida: 8–64 caracteres, mayúscula, minúscula, número, sin espacios y diferente al usuario; máximo 72 bytes UTF-8 por BCrypt. Cambio inicial obligatorio; temporal de 24 horas. |
| Recuperación por correo | Solicitar instrucciones, recibir enlace, validar enlace, elegir contraseña y confirmarla. Token aleatorio; únicamente su hash en la base. Enlace de un uso y 15 minutos; una nueva solicitud reemplaza al anterior. |
| Sesiones | Revocación al recuperar/cambiar/restablecer contraseña, desactivar o cambiar el correo; 30 minutos sin actividad y máximo absoluto de ocho horas. No hay recordar sesión ni tokens en almacenamiento del navegador. |
| Respaldo administrativo | Restablecimiento temporal después de confirmar identidad y registrar motivo; reactivación con nueva temporal. Protección de autodesactivación y último administrador. Los accesos se conservan: se cambia el estado con motivo, no se eliminan. |
| Correo institucional | Crear/editar acceso incluye confirmación de titularidad por la institución. Cambiar el correo desmarca esa confirmación en la pantalla. Sin correo confirmado no se envía recuperación. El administrador puede editar y confirmar el correo de su propio acceso. |
| Apoderado | Ve solamente sus estudiantes habilitados y asociados. Puede guardar un borrador con año y grado, editar observaciones, enviar, consultar estado, subsanar una observación, reenviar o cancelar donde corresponde. No asigna sección ni finaliza matrícula. |
| Administración de matrícula | Recibe las solicitudes del apoderado, revisa, observa o rechaza; asigna sección del grado solicitado y finaliza verificando vacantes. La sección puede estar pendiente mientras la familia solicita. |
| Admisión | Administración abre/cierra la recepción por año activo con motivo e historial; únicamente grados habilitados. No se equiparan automáticamente las fechas académicas al plazo de postulación. |
| Roles y permisos | Matriz de consulta con los cuatro roles fijos, descripción de alcances, permisos permitidos/denegados, búsqueda, filtro por módulo, comparación de un rol y contadores. El apoderado tiene SOLICITUDES_ESCRIBIR; no MATRÍCULA_ESCRIBIR. |

## 2. Actualizar como veníamos haciendo

1. Detén frontend y backend con `Ctrl+C` en sus terminales.
2. Extrae ambos ZIP en tu carpeta `AsisControl-prueba`. Cada uno contiene su carpeta `frontend` o `backend` completa.
3. Copia **el contenido** de `frontend` dentro de tu carpeta habitual `asiscontrol-frontend`. Evita una carpeta anidada `asiscontrol-frontend/frontend`.
4. Copia **el contenido** de `backend` a la carpeta donde ejecutas el backend. Esta versión necesita ambos paquetes porque amplía los contratos de la API.
5. Conserva tus archivos privados de entorno, credenciales y datos. Los ZIP no incluyen `node_modules`, `.next`, `target`, almacenamiento ni secretos.
6. Si usas MySQL, respalda la base y aplica `backend/migrations/003_seguridad_solicitudes_apoderado.sql` **después** de las migraciones 001 y 002 que corresponden a las versiones previas. No ejecutes 001/002 de nuevo si ya se aplicaron. La migración 003 conserva los datos y revoca sesiones del esquema anterior; debes iniciar sesión nuevamente.
7. Las cuentas anteriores quedan con correo sin confirmar. Confírmalo en Administración → Usuarios → Editar, después de verificar su titularidad. Las temporales antiguas sin fecha de emisión confiable se consideran vencidas 24 h después de crear el acceso; usa el respaldo administrativo si ya vencieron.

El perfil `local` usa H2 en memoria y elimina sus datos de prueba al reiniciar. Para conservar usuarios, solicitudes y recuperaciones entre ejecuciones, utiliza la base MySQL del equipo. El backend no lee automáticamente `.env`: las variables deben estar en la terminal o en la configuración de ejecución de su IDE.

## 3. Ejecutar el frontend

En PowerShell, dentro de `asiscontrol-frontend` (la carpeta que contiene `package.json`):

```powershell
npm ci
npm run dev
```

Abre http://localhost:3000. Conserva tu `.env.local`; `NEXT_PUBLIC_API_BASE_URL` debe apuntar al backend, por ejemplo `http://localhost:8080`.

## 4. Configurar el correo y ejecutar el backend

Usa el servidor SMTP autorizado del colegio o el proveedor acordado con el equipo. No inventes el host ni publiques la contraseña en GitHub. Las siguientes variables se configuran en **la misma terminal donde iniciarás Java**:

```powershell
$env:APP_MAIL_ENABLED = "true"
$env:APP_MAIL_FROM = "correo-institucional@tu-dominio.pe"
$env:APP_FRONTEND_URL = "http://localhost:3000"
$env:SMTP_HOST = "host-smtp-de-tu-proveedor"
$env:SMTP_PORT = "587"
$env:SMTP_USERNAME = "usuario-smtp-autorizado"
$env:SMTP_AUTH = "true"
$env:SMTP_STARTTLS = "true"
$asiscontrolCorreoClave = Read-Host "Contraseña SMTP o de aplicación autorizada" -AsSecureString
$env:SMTP_PASSWORD = [System.Net.NetworkCredential]::new("", $asiscontrolCorreoClave).Password
```

El remitente debe estar autorizado por el proveedor; las opciones de puerto/TLS/autenticación deben coincidir con su configuración. En el despliegue, usa la URL HTTPS real del frontend. El enlace usa un fragmento `#token=...` para que el secreto no llegue como parte de la URL al servidor web.

Dentro de `backend` (donde están `pom.xml` y `mvnw.cmd`):

```powershell
.\mvnw.cmd clean package
java -jar .\controller	arget\asiscontrol-controller-1.0.0-SNAPSHOT.jar
```

Si su proyecto se ejecuta con Maven instalado, reemplaza `.\mvnw.cmd` por `mvn`. Usa el JDK compatible con el `pom.xml` del equipo; la verificación de esta entrega se realizó con Java 21. El paquete integra el nuevo componente de correo y Maven descargará sus dependencias.

Conserva la configuración de MySQL y `JWT_SECRET` que ya usa el equipo. El administrador inicial se crea solo si la base no tiene ese acceso y están definidos `BOOTSTRAP_ADMIN_DOCUMENT`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` y `BOOTSTRAP_ADMIN_USERNAME`. Esa temporal debe cumplir la política; no está incluida en el ZIP.

**Sin SMTP configurado no llegará un correo externo.** La pantalla conserva una respuesta genérica y el administrador puede consultar un error seguro en Logs del sistema. No existe un modo que muestre enlaces de recuperación, contraseñas o destinatarios en los logs. Para desarrollo sin correo, mantén `APP_MAIL_ENABLED=false` y utiliza el respaldo administrativo con identidad y motivo.

Referencia técnica: https://docs.spring.io/spring-boot/4.0/reference/io/email.html.

## 5. Probar la recuperación por correo

1. Ingresa como administrador. En Usuarios, crea o edita un acceso activo con un correo real al que el titular tenga acceso.
2. Verifica su titularidad y marca la confirmación institucional. No marques correos de terceros sin comprobarlos.
3. Cierra sesión y selecciona **¿Olvidaste tu contraseña?**.
4. Ingresa el usuario de ese acceso y confirma. El mensaje es igual si la cuenta no existe, está desactivada, no tiene correo confirmado o se llegó al límite.
5. Revisa el correo y la carpeta no deseada. Abre el enlace antes de 15 minutos.
6. Escribe una contraseña distinta a la actual y su confirmación. Debe cumplir la política.
7. Inicia sesión con la nueva contraseña. La anterior, el enlace usado y las sesiones previas quedan invalidados.
8. Comprueba que pedir otro enlace invalida el anterior. Se admiten tres solicitudes por hora por usuario y por IP; el límite de IP se comparte cuando varias personas salen por la misma red.

Si el titular no puede acceder al correo, el administrador usa **Restablecer contraseña**, verifica identidad, registra motivo y entrega la temporal una sola vez. Vence en 24 horas y obliga al cambio al ingresar.

## 6. Probar la solicitud del apoderado de punta a punta

### Preparación del administrador

1. Configura el año, sus períodos, los grados, las secciones y sus capacidades. Activa el año.
2. En el detalle del año, pulsa **Abrir admisión**, registra motivo y confirma.
3. Registra la ficha del estudiante y vincúlale un apoderado habilitado; puedes mantener varios vínculos y estudiantes.
4. Crea el acceso del apoderado desde el flujo centralizado. Si es nuevo, primero cambia su contraseña temporal.

### Recorrido de la familia

1. Ingresa como apoderado y abre **Matrícula**.
2. Pulsa **Nueva solicitud** y selecciona estudiante asociado, año con admisión abierta y grado ofrecido.
3. Guarda el borrador. Revisa su detalle y pulsa **Enviar solicitud**; confirma el envío.
4. Consulta su estado y las fechas de envío/revisión. La familia no elige sección ni reserva vacante.
5. Si la administración observa la solicitud, corrige sus observaciones y vuelve a enviarla.
6. Para corregir estudiante, año o grado de un borrador, cancélalo con motivo y registra el correcto. Si ya está en revisión, coordina la corrección con administración. No se editan datos críticos después de enviar.

### Cierre administrativo

1. Ingresa como administrador y abre Matrícula → Solicitudes.
2. Inicia revisión. Puedes observar con detalle a subsanar o rechazar con motivo.
3. Usa **Editar sección / observaciones** para asignar sección del mismo año y grado solicitado.
4. Finaliza la matrícula. Se verifican en el servidor estado, versión del registro y última vacante; no se finaliza sin sección.
5. La familia verá **Matrícula finalizada** en el detalle. Las fichas permanecen disponibles en administración.

Una solicitud del apoderado no cancelada bloquea otra del mismo estudiante y año, incluso si fue rechazada. La familia debe consultar a administración cuando corresponda una reconsideración. El flujo administrativo de reinscripción después de anulación/retiro que existía se conserva; no se habilita esa excepción automáticamente para el apoderado.

## 7. Trazabilidad y decisiones del incremento

| Fuente | Aplicación en esta entrega |
|---|---|
| US-01, CA-US-01-01 a CA-US-01-05 y CA-US-01-07 a CA-US-01-10 | Login, errores genéricos, bloqueo, alcance del rol, revocación, cambio obligatorio y protección de secretos. |
| US-01, CA-US-01-06 + Fase 2 de seguridad | El backlog original definía recuperación únicamente administrada, sin correo. La Fase 2 y la petición actual incorporan correo confirmado y enlace. Se conserva el respaldo manual; CA-06 requiere actualizarse para reflejar esta decisión. |
| US-02 y US-59 | Accesos separados por rol, temporal, cambio inicial, identidad y motivo en respaldo, reactivación, protección de administrador e historial. |
| US-03 | Cuatro roles fijos y matriz de consulta; no roles arbitrarios ni permisos individuales. |
| US-04 / US-28 | Auditoría del acceso y solicitudes, separada de errores técnicos; sin contraseñas, enlaces ni tokens. |
| US-05 / US-06 | Oferta académica existente; control explícito de apertura/cierre de admisión añadido para US-40. |
| US-40, CA-01 a CA-08 | Borrador familiar, envío fechado, validación de alumno/año/grado, duplicidad, asociación, oferta, estado y protección después de enviar. |
| US-11 | El administrador asigna sección y finaliza con control de vacantes y concurrencia. |

**Precisiones para el profesor:**

- Conservamos los estados del incremento anterior: Borrador → Enviada → En revisión → Observada/Rechazada o Matrícula finalizada. No añadimos otra aprobación independiente a la creación atómica de matrícula; el texto de US-40 debe armonizarse con estos nombres.
- No se añadieron documentos obligatorios de admisión: el backlog aún pide validar cuáles son. Los datos obligatorios implementados son estudiante, año y grado; la ficha y su asociación deben estar habilitadas.
- La recepción se abre/cierra explícitamente. El calendario automático de postulaciones y los requisitos documentales requieren una decisión del profesor antes de implementarlos.
- Se conservan los identificadores actuales y el flujo Crear acceso del equipo; no se sustituyeron por un generador de identificadores institucionales nuevo.
- El correo se confirma mediante comprobación institucional; no hay una segunda campaña automática de verificación de correo.

## 8. Evidencia y límites de verificación

- Backend: 54 pruebas automatizadas aprobadas, incluidas recuperación de un uso bajo concurrencia, enlaces reemplazados/vencidos, cuentas no elegibles, límite por usuario/IP, temporales vencidas, intentos fallidos e inactividad.
- Frontend: 12 pruebas de contratos/política aprobadas; lint sin errores ni advertencias de código y build de producción aprobado.
- Integración: 181 comprobaciones HTTP y ocho rutas/fuente del frontend. Tres correos enviados por el cliente SMTP del backend y recibidos por un servidor SMTP de prueba; el enlace recibido se utilizó en la recuperación por HTTP.
- H2 en modo MySQL fue el entorno de integración. La migración 003 no se ejecutó en una instancia MySQL real en esta sesión.
- No se verificó recepción en un buzón externo ni autenticación con su proveedor: necesita las credenciales del equipo. No se realizó validación visual interactiva en navegador; comprueba el aspecto final con el GUI de AsisControl y tus pantallas.
- Las tres imágenes de este mensaje no estaban disponibles; esta versión usa el código integrado y el GUI propio de AsisControl disponible anteriormente.

La evidencia técnica resumida se encuentra en `docs/VERIFICACION_INCREMENTO_1_4.json`. Los ZIP son código completo. No se hizo push a GitHub ni se modificaron las ramas del equipo remotamente.

## 9. Control de cambios

| Versión | Fecha | Cambio |
|---|---|---|
| 1.3 | 2026-10-02 | Base integrada: administración, configuración académica, matriz inicial, logs, fichas y matrícula administrativa. |
| 1.4 | 2026-10-02 | Recuperación por SMTP, controles de contraseña/sesión, respaldo con identidad, solicitud del apoderado, apertura de admisión y mejora de la matriz. |

# AsisControl — Accesos centralizados (reconstrucción 1.2-r)

Fecha: 01/10/2026. Los ZIP originales no estaban disponibles. Esta es una reconstrucción funcional basada en las decisiones acordadas y en la última versión del equipo, no una copia idéntica de los archivos perdidos.

## Base y contenido

- Frontend del coordinador: `feature/frontend-inicial`, commit `5c7a6b955a33c2ce5a8ca054d6db50be98124332`.
- Backend del equipo: `feature/backend-inicial`, commit `c606cf6249bd1b4ea4298b8020a8dd23b5702f89`.
- Se conservan el diseño de login, marca Colegio PUCP, los menús de los cuatro perfiles y el header con nombre y rol. Se incorporan Inter local, colores y componentes de `GUI(1).pdf` de AsisControl.
- Login HTTP real con JWT en memoria; cambio inicial obligatorio; logout que revoca la sesión; cambio de contraseña desde el menú de perfil.
- Matrícula contiene fichas reales: registro, búsqueda, paginación, detalle y edición. Una ficha no crea credenciales automáticamente.
- Administración contiene Usuarios y Auditoría reales. Se elimina de la navegación la demostración con usuarios simulados. Los archivos mock del coordinador permanecen como referencia, sin ser consumidos por la aplicación.
- Un único formulario Crear acceso permite elegir una persona existente o registrar una nueva, y crear/reutilizar su perfil de Administrador, Docente, Estudiante o Apoderado.
- Una persona puede tener varias cuentas, una por rol. Cada cuenta tiene usuario y correo de acceso únicos. El correo de contacto de la persona es independiente.
- Docente: código, fecha de ingreso no futura y especialidad cuando falta el perfil. Estudiante: código cuando falta el perfil. Apoderado: selección opcional de varios estudiantes. Sin asociaciones no tiene acceso a estudiantes ajenos.
- Persona, perfil, acceso y nuevas asociaciones se guardan en una única transacción. Si falla una validación, se revierte la operación completa.
- Contraseña temporal: entre 10 y 64 caracteres ASCII imprimibles, mayúscula, minúscula, número y sin espacios. El generador utiliza Web Crypto. No se guarda ni se vuelve a mostrar en la interfaz después de crear el acceso.
- La contraseña se entrega al titular mediante el procedimiento institucional; no hay envío automático de correo en esta versión.

## Instalar el frontend en tu carpeta actual

1. Detén `npm run dev` con Ctrl+C.
2. Guarda una copia de tu carpeta `asiscontrol-frontend` antes de reemplazar el código.
3. Extrae `Actualizacion_Frontend_Accesos_Centralizados.zip` dentro de `AsisControl-prueba`.
4. Abre la carpeta `frontend` extraída. Su contenido va directamente dentro de tu carpeta `asiscontrol-frontend`: no crees una segunda carpeta frontend dentro de ella.
5. Sustituye la carpeta `src` por la nueva completa para evitar que queden pantallas de actualizaciones anteriores. Copia también `public`, `tests` y los archivos de configuración del paquete. Conserva tu configuración local del servidor.
6. Abre una terminal en `asiscontrol-frontend` y ejecuta:

```powershell
npm ci
npm run dev
```

7. Abre http://localhost:3000. El backend debe estar iniciado en http://localhost:8080. Si utiliza otra dirección, define `NEXT_PUBLIC_API_BASE_URL` en tu configuración local y reinicia el frontend. La dirección incluye solo el origen del backend, sin `/api` al final.

El token está en memoria: al recargar la página se solicita iniciar sesión nuevamente. Esto evita almacenar JWT en localStorage. No uses las credenciales mock del README original: el acceso se valida en el backend.

## Instalar el backend

1. Extrae `Actualizacion_Backend_Accesos_Por_Rol.zip` en una carpeta de prueba.
2. Guarda una copia de tu carpeta backend actual. Copia el contenido de la carpeta `backend` extraída dentro de la carpeta backend del proyecto, conservando tus datos, almacenamiento de archivos y configuración externa.
3. Mantén la estructura Maven del equipo: `entity`, `dto`, `repository`, `service`, `controller` y el `pom.xml` padre. Se conserva Java 21 como objetivo del POM existente.
4. Desde la carpeta `backend`, ejecuta `mvn clean install`.
5. Si necesitas crear un administrador inicial para una base de prueba nueva, configura los valores reales que elijas, antes de iniciar. Este es solo un ejemplo para una prueba local:

```powershell
$env:BOOTSTRAP_ADMIN_DOCUMENT="12345678"
$env:BOOTSTRAP_ADMIN_EMAIL="admin@colegio.test"
$env:BOOTSTRAP_ADMIN_USERNAME="admin"
$env:BOOTSTRAP_ADMIN_PASSWORD="TemporalAdmin123!"
mvn -pl controller spring-boot:run
```

6. Inicia sesión con ese acceso y cambia la contraseña cuando lo solicite.

El perfil `local` del equipo utiliza H2 en memoria: sus datos son temporales y desaparecen cuando se reinicia el backend. Para MySQL conserva tu perfil `mysql`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `JWT_SECRET`. Antes de utilizar multirol sobre una base MySQL existente, revisa y aplica `migrations/001_accesos_por_rol.sql` sobre esa base con una copia de respaldo. La migración no se ejecuta automáticamente.

## Comprobación manual recomendada

1. Login del administrador y cambio inicial de contraseña.
2. Matrícula → registrar una ficha de estudiante, buscarla y abrir su detalle.
3. Administración → Usuarios → Crear acceso → persona existente → perfil Estudiante. Seleccionar la persona correspondiente a la ficha, no crearla otra vez.
4. Crear un docente nuevo con su perfil y acceso.
5. Crear un acceso de Apoderado para esa misma persona existente, con otro usuario y correo de acceso, asociando un estudiante.
6. Confirmar que aparecen dos cuentas para esa persona, con roles distintos.
7. Comprobar que repetir el mismo rol produce un rechazo y que Usuarios no permite cambiar el rol de una cuenta existente.
8. Abrir Auditoría y verificar las operaciones.
9. Ingresar como estudiante o apoderado, cambiar la contraseña inicial y cerrar sesión.

## Validación de la reconstrucción

- ESLint y compilación de producción del frontend.
- 8 pruebas de contrato frontend: login, mapeo de roles, Bearer, 401/403, logout y generador de contraseñas.
- 34 pruebas del backend, incluidas 8 pruebas nuevas de accesos por rol.
- 30 solicitudes HTTP reales: login, cambio obligatorio, fichas, multirol, vínculo de apoderado, rechazo de duplicado, reversión de una creación inválida, autorización por registro y revocación al salir.
- 6 verificaciones HTTP de rutas del frontend y su fuente local.
- El backend se verificó sobre H2. La migración MySQL requiere verificación en una copia de la base del equipo.
- No se pudo completar la revisión visual automatizada en un navegador en este entorno.

## Alcance pendiente

La recuperación por correo aún no está implementada: el login indica contactar a la administración como respaldo. Los módulos sin avance muestran su condición pendiente y no inventan datos.

Este archivo documenta la reconstrucción anterior. El incremento actual agrega configuración académica, matriz de roles, logs y gestión de matrícula: consulta `LEEME_ADMINISTRACION_MATRICULA.md` y utiliza los nombres de ZIP e instrucciones de esa guía.

El incremento actual restringe la consulta a los cuatro roles y bloquea su mantenimiento ordinario, conservando los registros heredados. Su alcance se describe en la guía actual.

La gestión completa del ciclo de vida de asociaciones (finalización/reactivación con motivo) no forma parte de esta reconstrucción; aquí se crean asociaciones durante la provisión de un acceso de apoderado. Se añade `NO_DECLARADO` para no atribuir un parentesco que no se ha informado.

# AsisControl

## Rama para revisión del equipo

`feature/login-matricula-apoderado` integra las bases de `feature/frontend-inicial` y `feature/backend-inicial` con la entrega 1.4. Incluye frontend y backend; todavía debe probarse y revisarse antes de fusionar a una rama compartida.

Contiene login y cambio inicial, recuperación por correo, gestión de accesos, fichas, configuración académica, solicitudes y matrícula, auditoría, logs y consulta de los cuatro roles fijos y sus permisos. Conserva el header con nombre y rol y la estructura del coordinador.

La autenticación unificada asigna una cuenta por persona e incorpora la selección de rol activo. Consulta el flujo vigente, los códigos de acceso y el procedimiento de migración. [Autenticación unificada](docs/AUTENTICACION_UNIFICADA.md)

## Obtener esta versión sin reemplazar tu carpeta de trabajo

En una terminal, desde una carpeta donde quieras crear una copia nueva:

```powershell
git clone --branch feature/login-matricula-apoderado --single-branch https://github.com/arom12321/AsisControl.git AsisControl-pruebas
cd AsisControl-pruebas
```

Requisitos: Git, Node.js compatible con Next.js 16 y JDK 21 conforme al `backend/pom.xml`. Maven se ejecuta mediante el wrapper incluido; no necesitas instalarlo aparte.

## Iniciar el frontend

En una terminal, dentro de `frontend`:

```powershell
Copy-Item .env.example .env.local
npm ci
npm test
npm run dev
```

Abre http://localhost:3000. El ejemplo apunta al backend en http://localhost:8080. Si ya tienes un `.env.local` propio, conserva tu configuración.

## Iniciar el backend para una prueba local

En otra terminal, dentro de `backend`, configura el administrador inicial antes de arrancar:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
$env:APP_MAIL_ENABLED = "false"
$env:BOOTSTRAP_ADMIN_DOCUMENT = Read-Host "DNI del administrador (8 dígitos)"
$env:BOOTSTRAP_ADMIN_EMAIL = Read-Host "Correo del administrador"
$asiscontrolTemporal = Read-Host "Contraseña temporal (mayúscula, minúscula y número; 8-64 caracteres)" -AsSecureString
$env:BOOTSTRAP_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new("", $asiscontrolTemporal).Password
.\mvnw.cmd clean package
java -jar ./controller/target/asiscontrol-controller-1.0.0-SNAPSHOT.jar
```

El perfil `local` usa H2 en memoria y reinicia los datos al detener el backend. Al crearse el administrador inicial, el log de arranque muestra su identificador generado de ocho dígitos. Ingresa con ese código y cambia la contraseña temporal. Define las variables del backend en la terminal o en el IDE.

Para probar **correo real**, configura el SMTP autorizado. Para una base MySQL existente, ensaya el respaldo y la migración de autenticación antes del corte. [Configuración de correo](backend/LEEME_SEGURIDAD_APODERADO.md#4-configurar-el-correo-y-ejecutar-el-backend) · [Migración de autenticación](docs/AUTENTICACION_UNIFICADA.md#migración-y-puesta-en-servicio)

## Recorrido mínimo de revisión

1. Ingresar como administrador, cambiar la temporal y cerrar/iniciar sesión.
2. Configurar y activar un año con períodos, grados, secciones y capacidades; abrir admisión.
3. Registrar las fichas del estudiante y del apoderado, entregar las credenciales de las cuentas nuevas y registrar la asociación familiar.
4. Ingresar como apoderado, cambiar la temporal, crear un borrador y enviar la solicitud.
5. Volver como administrador, revisar, asignar sección y finalizar con control de vacantes.
6. Comprobar que la familia consulta el resultado y no accede a estudiantes ajenos.
7. Consultar roles y permisos, auditoría y logs del sistema.
8. Con SMTP configurado y un correo confirmado por la institución, probar recuperación y reutilización del enlace.

El procedimiento detallado y los límites de alcance están en [LEEME_SEGURIDAD_APODERADO.md](backend/LEEME_SEGURIDAD_APODERADO.md). La evidencia de la entrega original está en `backend/docs/VERIFICACION_INCREMENTO_1_4.json` y `frontend/docs/VERIFICACION_INCREMENTO_1_4.json`: sus resultados y el indicador de publicación describen la entrega de los ZIP, anterior a la preparación de esta rama.

No publiques archivos de entorno privados, credenciales SMTP/JWT, bases de datos ni archivos generados. Los ejemplos de entorno sin secretos sí están incluidos.

# AsisControl Backend

Backend del **Sistema de Información Web para la Gestión Integral de un Colegio de Educación Secundaria**. El proyecto implementa el modelo de clases de Lucidchart, los flujos de los diagramas de estado y los contratos requeridos por las pantallas de Figma.

## Tecnología

- Java 21 LTS.
- Spring Boot 4.1.1.
- Spring Web, Validation, Data JPA y Security.
- JWT firmado con HMAC-SHA256 y sesiones revocables almacenadas como hash.
- MySQL para el entorno real.
- H2 en modo de compatibilidad MySQL para desarrollo y pruebas.
- OpenAPI/Swagger.
- Maven.

El backend es un proyecto Maven multimódulo. Las capas principales son módulos independientes y el
`pom.xml` de la raíz actúa como agregador:

```text
backend/
├── pom.xml                 Agregador Maven
├── entity/                 Entidades JPA y enumeraciones
├── dto/                    Contratos de entrada y salida
├── repository/             Persistencia con Spring Data JPA
├── service/                Reglas de negocio y transacciones
│   └── src/main/java/com/asiscontrol/{service,security,exception,util}
└── controller/             API REST y aplicación ejecutable
    └── src/main/java/com/asiscontrol/{controller,config}
```

Las dependencias siguen una sola dirección: `controller` consume `service`; `service` consume
`repository`, `dto` y `entity`; `repository` consume `entity`; y `dto` consume los tipos del modelo
que necesita para sus contratos. Esto evita dependencias circulares entre capas.

## Requisitos y apertura del proyecto

Antes de ejecutar, verifica que Java 21 esté activo:

```powershell
java -version
```

Debe mostrar la versión 21. Maven no necesita instalarse globalmente: se usa el wrapper incluido
(`mvnw.cmd`). Para IntelliJ IDEA, abre el `pom.xml` de `backend`, selecciona JDK 21 como
**Project SDK** y habilita *annotation processing* si el IDE lo solicita para Lombok.

Los comandos siguientes se ejecutan desde la carpeta `backend`:

```powershell
cd C:\ruta\al\repositorio\AsisControl\backend
```

> `.env.example` es una plantilla; Spring Boot no la carga automáticamente. Define las variables en
> la terminal actual o en la configuración de ejecución del IDE. Nunca guardes contraseñas, JWT ni
> secretos reales en Git.

## Elegir el perfil de ejecución

Antes de levantar el backend, elige **solo uno** de estos perfiles en PowerShell:

- `local`: usa H2 en memoria. Es la opción recomendada para desarrollar o hacer pruebas rápidas;
  no requiere MySQL y los datos se eliminan al detener el servidor.
- `mysql`: usa una base MySQL y conserva los datos. Requiere definir conexión, contraseña y
  `JWT_SECRET`.

No copies los bloques de ambos perfiles en la misma terminal. Si cambias de perfil, detén el backend
con `Ctrl+C` y ejecuta el bloque completo del nuevo perfil.

## Opción A: ejecutar con `SPRING_PROFILES_ACTIVE=local` (H2)

Para el primer arranque no se requiere MySQL. El perfil `local` es el predeterminado, crea una base
H2 temporal en memoria y se reinicia al detener la aplicación.

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
$env:BOOTSTRAP_ENABLED = "true"
$env:BOOTSTRAP_ADMIN_DOCUMENT = "00000000"
$env:BOOTSTRAP_ADMIN_EMAIL = "admin@colegio.test"
$env:BOOTSTRAP_ADMIN_USERNAME = "admin"
$env:BOOTSTRAP_ADMIN_PASSWORD = "Temporal2026Clave"

.\mvnw.cmd -DskipTests install
.\mvnw.cmd -f controller\pom.xml spring-boot:run
```

La contraseña temporal debe tener entre 8 y 64 caracteres, mayúscula, minúscula y número; no puede
tener espacios ni contener el nombre de usuario. Al iniciar sesión, el usuario debe cambiarla antes
de acceder al resto de módulos.

Cuando el log muestre que Tomcat inició en el puerto `8080`, verifica:

- Salud: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- API: `http://localhost:8080/api`
- Consola H2 local: `http://localhost:8080/h2-console`

Detén el servidor con `Ctrl+C`. Al volver a iniciarlo en perfil `local`, se perderán los datos de
H2; esto es normal.

## Opción B: ejecutar con `SPRING_PROFILES_ACTIVE=mysql` (MySQL)

Usa MySQL cuando necesites conservar los datos entre reinicios. Para pruebas, crea una base nueva;
no ejecutes manualmente `V1__baseline_schema.sql` ni crees tablas a mano:

```sql
CREATE DATABASE asiscontrol_pruebas
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

En PowerShell, configura el perfil y las credenciales. El comando solicita la clave de MySQL sin
mostrarla en pantalla:

```powershell
$env:SPRING_PROFILES_ACTIVE = "mysql"
$env:DB_URL = "jdbc:mysql://localhost:3306/asiscontrol_pruebas?useSSL=false&serverTimezone=America/Lima&allowPublicKeyRetrieval=true"
$env:DB_USERNAME = "root"
$mysqlPassword = Read-Host "Contraseña de MySQL" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $mysqlPassword).Password

$jwtBytes = New-Object byte[] 48
$rng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
$rng.GetBytes($jwtBytes)
$rng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)

$env:BOOTSTRAP_ENABLED = "true"
$env:BOOTSTRAP_ADMIN_DOCUMENT = "00000000"
$env:BOOTSTRAP_ADMIN_EMAIL = "admin@colegio.test"
$env:BOOTSTRAP_ADMIN_USERNAME = "admin"
$env:BOOTSTRAP_ADMIN_PASSWORD = "Temporal2026Clave"

.\mvnw.cmd -DskipTests install
.\mvnw.cmd -f controller\pom.xml spring-boot:run
```

`JWT_SECRET` es obligatorio en MySQL. Generarlo nuevamente invalida los tokens emitidos antes, lo
cual es apropiado para pruebas. Si cierras PowerShell, vuelve a definir todas las variables antes
del siguiente arranque.

El perfil `mysql` usa Flyway para versionar el esquema y `ddl-auto=validate` para que Hibernate solo
compruebe la compatibilidad. En una base vacía, Flyway aplica
`V1__baseline_schema.sql`, que es la versión oficial actual del esquema. No modifiques una migración
ya aplicada: Flyway valida su checksum.

Los archivos recibidos externamente, incluidos SQL que no estén dentro de
`controller/src/main/resources/db/migration`, no se ejecutan automáticamente y no deben mezclarse
con la ruta oficial sin revisión del equipo responsable de datos.

## Probar la asistencia en otra fecha

La asistencia solo se toma el día de la clase, por lo que un curso de otro año no muestra
**Tomar asistencia**. Para probarla en desarrollo, indica la fecha de una de sus clases antes de
arrancar el backend, en la misma terminal:

```powershell
$env:ASISTENCIA_FECHA_SIMULADA = "2027-03-01"
```

La fecha debe tener el formato `AAAA-MM-DD`; una fecha inválida impide arrancar. La hora sigue
siendo la real y el log muestra `FECHA SIMULADA ACTIVA` al iniciar. Para volver a la fecha real,
ejecuta `Remove-Item Env:ASISTENCIA_FECHA_SIMULADA` y reinicia el backend.

Lo que registres queda guardado en la base. Nunca definas esta variable en producción.

## Problemas frecuentes

| Mensaje o síntoma | Causa habitual | Qué hacer |
| --- | --- | --- |
| `JWT_SECRET es obligatorio para este perfil` | Falta la variable en el perfil `mysql`. | Ejecuta el bloque que genera y asigna `JWT_SECRET` en la misma terminal. |
| `RandomNumberGenerator does not contain a method named Fill` | PowerShell/.NET antiguo. | Usa el bloque con `RNGCryptoServiceProvider` incluido arriba. |
| `Unknown database` | La base indicada en `DB_URL` no existe. | Crea solo la base con `CREATE DATABASE`; Flyway crea las tablas. |
| `Access denied for user` | Usuario, contraseña o permisos de MySQL incorrectos. | Revisa `DB_USERNAME`, vuelve a ingresar la contraseña y valida el acceso en MySQL. |
| `Schema validation` o error de Flyway | Se mezclaron scripts manuales, faltan migraciones o la base tiene un esquema previo distinto. | Usa una base de pruebas vacía; para una base con datos, respáldala y no la modifiques sin revisar su historial. |

Para cada cambio posterior de estructura, agregue un archivo nuevo en `controller/src/main/resources/db/migration`, por ejemplo:

```text
V2__agregar_columna_telefono_emergencia.sql
V3__crear_indice_busqueda_alumno.sql
```

No edite una migración que ya haya sido aplicada. Flyway valida sus checksums y ejecuta las pendientes en orden.

## Módulos de la API

- `/api/auth`: inicio/cierre de sesión, usuario actual y cambio de contraseña.
- `/api/usuarios`, `/api/roles`: cuentas, roles y permisos.
- `/api/alumnos`, `/api/docentes`, `/api/apoderados`: personas, perfiles y vínculos familiares.
- `/api/catalogos`: nacionalidades y enumeraciones para combos del frontend.
- `/api/cursos`, `/api/competencias`, `/api/secciones`, `/api/aulas`, `/api/asignaciones-cursos`, `/api/horarios`: estructura académica.
- `/api/solicitudes-matricula`, `/api/matriculas`: solicitud, revisión, subsanación y matrícula.
- `/api/asistencias/alumnos`, `/api/asistencias/docentes`, `/api/justificaciones-asistencia`: asistencia, QR y justificaciones.
- `/api/tareas`, `/api/entregas-tarea`: asignaciones, archivos, entregas y calificación.
- `/api/evaluaciones`, `/api/calificaciones`: notas AD/A/B/C y consolidado por competencia.
- `/api/compromisos-pago`, `/api/transacciones-pago`, `/api/comprobantes`, `/api/simulaciones-pago`: obligaciones y pagos.
- `/api/criterios-riesgo`, `/api/alertas-riesgo`: detección y seguimiento de riesgo.
- `/api/evaluaciones-riesgo`: ejecución controlada de las reglas de detección.
- `/api/archivos`: carga y descarga autorizada de adjuntos.
- `/api/auditoria`: trazabilidad y seguimiento de errores.
- `/api/dashboard`: indicadores consumidos por los paneles de Figma.

Todos los listados grandes admiten paginación y filtros. Las entidades JPA nunca se exponen directamente: cada endpoint utiliza DTO de solicitud y respuesta.

El contrato de recálculo por alumno y período, las fuentes de los indicadores,
el historial y el resumen institucional se describen en
[Evaluación de riesgo por período](docs/riesgo-criterios.md).

## Formato uniforme de errores

```json
{
  "timestamp": "2026-09-29T12:00:00Z",
  "status": 409,
  "code": "VACANTE_NO_DISPONIBLE",
  "message": "No existen vacantes disponibles para la sección",
  "path": "/api/solicitudes-matricula/15/estado",
  "correlationId": "f8e44bb7-283f-449f-b836-636973cf4124",
  "fieldErrors": []
}
```

La API nunca devuelve stack traces, nombres de excepciones internas ni mensajes SQL.

## Referencia de los compromisos de pago

Las emisiones sin código explícito reciben una referencia corta `CP-000001`,
`CP-000002`, etc. El correlativo es global, se guarda en `ContadorCodigoCompromiso`
y no se reinicia al cambiar de año, reiniciar el backend o anular un compromiso.
La referencia es independiente del número de cuota, la matrícula y el identificador
interno del registro. Los seis dígitos son un ancho mínimo: la secuencia puede crecer.

La migración Flyway `V10__correlativo_codigo_compromiso.sql` agrega únicamente el
contador. No renumera compromisos existentes ni modifica sus transacciones o
auditoría. La primera emisión recupera el mayor sufijo numérico legado bajo bloqueo;
si solo existen UUID o referencias externas, la nueva serie comienza en uno.

El contador se bloquea en base de datos dentro de la misma transacción de emisión,
para que dos administradores reciban referencias diferentes. Las anulaciones
conservan su código y no liberan números. Una emisión revertida tampoco confirma el
avance del contador. No se utiliza un contador en memoria ni `MAX + 1` sin bloqueo.

Por compatibilidad con integraciones anteriores, el campo opcional
`codigoCompromiso` sigue admitiendo códigos externos únicos. Estas emisiones también
toman el bloqueo; si proporcionan un código numérico `CP-000100`, la siguiente
referencia automática continuará en `CP-000101`. Por ello pueden existir saltos por
referencias externas o históricas. Los identificadores numéricos legados que exceden
el rango de un entero de 64 bits se conservan como referencias opacas. La interfaz
web omite este campo y siempre obtiene la referencia automática del backend.

Para producción se debe desplegar el nuevo backend y permitir que Flyway ejecute
V10 sobre la base existente. En el perfil local con `ddl-auto=update`, JPA crea la
tabla y su fila se inicializa al emitir; los datos persistidos se conservan.

## Verificación

```powershell
.\mvnw.cmd clean verify
```

Este comando compila todos los módulos, ejecuta las pruebas y genera el reporte agregado de JaCoCo
en `controller/target/site/jacoco-aggregate/index.html`.

## Seguridad

- Las contraseñas se almacenan con BCrypt.
- Un usuario se bloquea temporalmente tras cinco intentos fallidos.
- Los JWT tienen duración corta y su sesión puede revocarse.
- En base de datos solo se conserva el hash SHA-256 del token.
- Los roles y permisos son aplicados tanto en controladores como en reglas de propiedad.
- Los listados de alumnos, matrículas, asistencias, tareas, evaluaciones, entregas, pagos y alertas
  se restringen automáticamente al docente, alumno o apoderado autenticado cuando corresponde.
- CORS, secretos, conexión y almacenamiento se configuran mediante variables de entorno.
- Los archivos usan nombres UUID, lista blanca MIME, límite de tamaño, propietario y descarga controlada.
- El borrado funcional es lógico para preservar el historial.

## Flujo Git que se usará al integrar

Esta carpeta todavía no modifica el repositorio compartido. Cuando el equipo autorice la integración:

1. actualizar `develop`;
2. crear una rama `feature/backend-inicial`;
3. copiar el proyecto dentro de `backend/`;
4. ejecutar `mvn clean verify`;
5. crear commits con el formato `feat: ...`, `test: ...`, `docs: ...`;
6. abrir un Pull Request hacia `develop`.

No se debe trabajar directamente sobre `main` ni `develop`.

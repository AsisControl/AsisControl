# US-20: modelo persistente de informes

Las entidades y el esquema estan conectados a generacion, publicacion y PDF.
El flujo y sus limites se describen en US20_PDF_AVANCE.md.

## Modelo

- `InformeProgreso`: alumno, asignacion de curso (incluye seccion), periodo
  academico, numero de version, estado BORRADOR/PUBLICADA, docente responsable
  y fecha de publicacion. Hereda auditoria y bloqueo optimista.
- `DetalleInformeProgreso`: una fila por evaluacion y competencia dentro de
  una version. Conserva nombre de competencia, titulo y fecha de evaluacion,
  ponderacion, nivel de logro y observacion como valores propios. Referencia
  informe, competencia, evaluacion y, cuando exista, calificacion de origen.

`numeroVersion` identifica la version funcional de la libreta. El atributo
`version` heredado de `AuditableEntity` sirve exclusivamente para concurrencia
JPA. La clave alumno/asignacion/periodo/numeroVersion no se puede duplicar.

El nivel y la calificacion de origen pueden estar vacios en un borrador.
Esto NO elimina la restriccion de notas completas para publicar. El servicio valida tambien que todas las relaciones correspondan al
mismo alumno, curso y periodo y mantiene las versiones publicadas sin editar sus detalles.
Las FK no tienen eliminacion en cascada.

Los detalles mantienen la granularidad del PDF actual (evaluacion/competencia);
no introducen una regla nueva de consolidacion. Las referencias a calificacion
y evaluacion identifican el origen, pero no implementan por si solas la consulta
de evidencias autorizadas. Ese acceso y la copia de los datos de cabecera para
reproducir nombres historicos no estan incluidos en esta implementacion.

## Base de datos

Las versiones V16 a V19 se conservan exactamente como en origin/develop.
V16 corresponde a codigos institucionales (checksum -1423649557), ya aplicada
en la base compartida. La migracion de informes usa V20 para evitar colisiones.
No se requiere reparar ni modificar el historial de Flyway.


Script: `controller/src/main/resources/db/migration/V20__informes_progreso.sql`.
Flyway lo aplica despues de V19 al iniciar con las migraciones habilitadas.
El perfil `mysql` ya habilita Flyway y valida el esquema con Hibernate; no
requiere cambiar esa configuracion. El perfil `local` utiliza H2 y crea el
esquema desde las entidades, sin ejecutar Flyway.
No modificar scripts ya aplicados ni ejecutar V20 manualmente antes de Flyway:
eso crearia las tablas fuera de su historial y provocaria un conflicto.
No requiere borrar datos ni modificar las tablas anteriores.

La prueba `InformeProgresoPersistenciaTest` ejecuta este mismo SQL sobre H2
en modo MySQL, persiste con JPA, verifica versiones distintas y clave unica,
y comprueba que cambiar una nota no cambia el resultado copiado al detalle.
Esta comprobacion no sustituye ejecutar la migracion en un MySQL de pruebas.

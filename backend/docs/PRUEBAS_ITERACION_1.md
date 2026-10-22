# Pruebas y evidencia del backend para la Iteración 1

## Ejecución reproducible

Desde la carpeta backend, con JDK 21:

```powershell
.\mvnw.cmd clean verify
```

La orden elimina resultados anteriores, compila los cinco módulos, ejecuta JUnit,
empaqueta la aplicación, genera JaCoCo y comprueba el umbral global. No necesita
credenciales de AWS: las pruebas de integración usan el perfil test con H2 y se
ejecutan aisladas de la base productiva.

Abrir `controller/target/site/jacoco-aggregate/index.html` para mostrar la cobertura.
El resumen numérico queda en `coverage-summary.md` en esa misma carpeta. Los resultados
JUnit y los fallos detallados quedan en `controller/target/surefire-reports`.

Evidencia del 22 de octubre de 2026: la suite completa ejecutó 182 pruebas sin fallos,
errores ni omisiones. Después de la última comprobación del JWT real se ejecutaron
además las 33 regresiones de accesos y renovación, también aprobadas. El reporte
agregado final registra 91.01 % de instrucciones, 92.37 % de líneas y 70.32 % de ramas.
Maven terminó con BUILD SUCCESS y el umbral global de 90 % aprobado. Las cifras deben
regenerarse al cambiar el código o integrar otras ramas.

El reporte incluye entity, dto, repository, service y **controller**. El proyecto
controller no se incluía en el reporte anterior de report-aggregate; ahora
includeCurrentProject está activado. No se excluyeron clases para elevar el porcentaje.
Las implementaciones generadas por Spring Data no tienen bytecode propio en repository;
sus consultas se prueban a través de servicios y pruebas de integración.

CoverageGate comprueba los contadores globales del XML producido por JaCoCo. `verify`
falla cuando instrucciones o líneas quedan por debajo de 90 %. El porcentaje de ramas
se informa por separado. Este umbral no significa que se haya probado cada combinación
posible de datos ni sustituye la revisión de criterios de aceptación con el backlog.

## Qué es una prueba unitaria

Una prueba unitaria ejecuta una clase con entradas conocidas y comprueba su resultado.
Las dependencias externas se sustituyen con Mockito. Por ejemplo, en una importación
con correo inválido se comprueba que la fila se rechaza y que PersonaService no recibe
ninguna orden de guardado. Eso evita confundir «el método se ejecutó» con «se verificó
que hace lo correcto».

Los tests siguen Preparar, Ejecutar y Comprobar. `@Test` identifica el caso; los asserts
comprueban valores, estados, excepciones y efectos sobre dependencias. Las pruebas
parametrizadas repiten una regla con distintas entradas, por ejemplo fechas inválidas,
tipos de archivo o roles sin acceso. Mockito no sustituye el código de la clase probada.

Una prueba de integración usa varias capas reales. Las pruebas SpringBootTest arrancan
Spring y Hibernate, y utilizan H2 para verificar persistencia, consultas, transacciones,
relaciones y concurrencia. Las pruebas HTTP incorporan MockMvc, el filtro de seguridad,
las validaciones de entrada, los controladores y los servicios. MockMvc no necesita
abrir un puerto. El flujo real de login y renovación también se prueba con contraseñas,
JWT y cookies del backend, sin depender del frontend.

## Casos automatizados y dónde encontrarlos

Todas las rutas indicadas a continuación son relativas a
`controller/src/test/java/com/asiscontrol`.

| Funcionalidad | Evidencia principal |
| --- | --- |
| US-02, accesos institucionales y perfiles | service/UsuariosEstudiantesIntegrationTest y controller/UsuariosEstudiantesHttpTest |
| US-09, ficha e importación de estudiantes | service/ImportacionAlumnoServiceTest y las pruebas de usuarios y estudiantes |
| US-10, apoderados y asociaciones | UsuariosEstudiantesIntegrationTest, AccesosPorRolIntegrationTest y UsuariosEstudiantesHttpTest |
| Login, contraseñas, recuperación y bloqueo | service/SeguridadRecuperacionIntegrationTest y security/PasswordPolicyTest |
| Recuperación de sesión al recargar | service/RenovacionSesionIntegrationTest y security/RefreshCookieSupportTest |
| Autorización y separación de datos por persona | service/ModuloAccessGuardTest, ModuloAccessGuardIntegrationTest y repository/ScopedRepositoryIntegrationTest |
| Cursos, competencias, aulas, asignaciones y horarios | controller/AcademicoFlujosHttpTest |
| Tareas, entregas y evaluación por competencias | controller/AcademicoFlujosHttpTest y service/CalificacionServiceTest |
| Asistencia de alumnos, QR y justificaciones | controller/AsistenciaFlujosHttpTest |
| Pagos, idempotencia, comprobantes y simulaciones | controller/PagosRiesgoFlujosHttpTest y service/PagoServiceTest |
| Criterios, evaluación y seguimiento de riesgos | controller/PagosRiesgoFlujosHttpTest y service/RiesgoServiceTest |
| Año, periodos, grados, secciones y calendario | controller/AdministracionFlujosHttpTest y service/AdministracionMatriculaIntegrationTest |
| Solicitudes, matrícula y última vacante | controller/MatriculaFlujosHttpTest y service/MatriculaConcurrencyIntegrationTest |
| Auditoría, errores, avisos y familia | controller/AdministracionFlujosHttpTest |
| Subida, descarga y validación de archivos | service/ArchivoStorageServiceTest y controller/AdministracionFlujosHttpTest |
| Respuestas de error y recursos inexistentes | exception/GlobalExceptionHandlerTest y controller/BackendErroresHttpTest |

Las pruebas de Dev 2 verifican, entre otros casos, duplicados de documento/correo/código,
reactivación del acceso con nueva contraseña, motivo obligatorio, rol fijo, un único
apoderado principal, reactivación de un vínculo retirado y conservación del acceso de
docente cuando se retira el perfil de apoderado de la misma persona.

La importación valida las restricciones del DTO por fila, fechas reales, tipos,
cabeceras, tamaño y formato Excel. Una nacionalidad decimal no se convierte en otro
identificador y los errores técnicos no se devuelven como mensajes SQL al usuario.

## Correspondencia con la revisión del JP y la rúbrica

La rúbrica del laboratorio 5 asigna tres puntos a los casos que cubren la Iteración 1,
dos a automatización y tres a que cada funcionalidad tenga un caso asociado: ocho en
total. Este trabajo aporta casos automatizados, comprobaciones funcionales y la tabla
de trazabilidad anterior. La nota depende de la revisión del JP y de los criterios
de aceptación que el grupo haya comprometido en su backlog actualizado.

Además quedan configurados el reporte de cobertura y su control obligatorio, pruebas
visibles en el árbol del proyecto, ejecución con Java 21, validaciones y manejo uniforme
de errores. La API de renovación está documentada en Swagger y en SESIONES_Y_RENOVACION.md.

La comprobación local no acredita despliegue, integración del frontend, responsive,
aprobación formal del Figma ni el registro de esfuerzo real del equipo. El JP exige
despliegue y la rúbrica no evalúa funcionalidades sin integrar: esos puntos deben
comprobarse también en el entorno compartido.

## MySQL y migraciones

El dump SCRIPTS.sql aportado contiene 50 tablas de negocio y no incluye INSERT.
Las migraciones contemplan esas tablas y agregan TokenRenovacion mediante V3.
En el perfil mysql Hibernate usa ddl-auto=validate; Flyway administra los cambios.
V1 y V1_5 completan instalaciones nuevas, V2 formaliza planificación y V3 añade
renovación. Las bases canónicas preexistentes sin historial se baselinen en V2.

Antes de desplegar sobre una base existente, revisar flyway_schema_history y respaldarla.
Una base que ya tenga un historial parcial o cambios manuales debe reconciliarse con
ese historial; no ejecutar repair, borrar el historial ni reimportar el dump con
DROP TABLE para forzar el arranque. No se ejecutaron migraciones ni se cargaron datos
en AWS durante estas pruebas. La compatibilidad final con el servidor MySQL debe
validarse allí o en un esquema MySQL aislado con la misma versión.

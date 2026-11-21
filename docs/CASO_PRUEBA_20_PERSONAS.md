# Caso de prueba: 20 personas entre alumnos, apoderados y docentes

La prueba automatizada está en
`backend/controller/src/test/java/com/asiscontrol/service/VeintePersonasFamiliaDocenteIntegrationTest.java`.
Se ejecuta con la base H2 del perfil `test`; no inserta personas en la base de la aplicación.

## Composición

| Identidades | Perfiles | Caso |
| --- | --- | --- |
| 10 | Alumno | Fichas nuevas desde el Excel de estudiantes. |
| 7 | Apoderado | Fichas desde el Excel de apoderados. |
| 2 de los 7 apoderados | Docente y apoderado | Uno se registra primero como docente; el otro recibe el perfil docente después de la carga familiar. |
| 3 | Docente | Fichas docentes sin vínculo familiar. |

**Resultado esperado:** 20 personas, 20 cuentas, 10 perfiles de alumno, 7 perfiles de apoderado, 5 perfiles de docente y 14 parentescos activos. Los dos docentes que son apoderados comparten cuenta y pueden usar ambos roles. No reciben una segunda contraseña temporal.

Cada alumno tiene un apoderado principal. Cuatro alumnos tienen además un segundo apoderado. El caso cubre `MADRE`, `PADRE`, `TUTOR_LEGAL` y `OTRO_FAMILIAR`, además de un apoderado con varios hijos.

## Ejecución en PowerShell

Desde `backend`, con JDK 21 instalado:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
./mvnw.cmd -pl controller -am '-Dtest=VeintePersonasFamiliaDocenteIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Los documentos se generan de forma única en cada ejecución para que la prueba pueda repetirse. Los tres archivos Excel se crean en memoria y se procesan juntos por el mismo servicio de carga masiva utilizado por la aplicación.

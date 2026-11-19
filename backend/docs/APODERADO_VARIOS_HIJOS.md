# Un apoderado con varios hijos

El modelo actual es suficiente: una Persona, un Apoderado y un AccesoUsuario
con rol APODERADO pueden tener varios vínculos AlumnoApoderado. El parentesco
y la condición de principal pertenecen a cada vínculo, no a toda una familia.
No se agrega una entidad Familia.

## Carga masiva

- Familia nueva con dos hijos: dos filas de estudiantes, una fila de apoderado
  y dos filas de parentescos con el mismo documento de apoderado.
- Otro hijo de un apoderado existente: archivo de estudiantes y archivo de
  parentescos; omitir el archivo de apoderados.
- Si ambos perfiles existen: enviar únicamente parentescos.
- Reimportar un apoderado con la misma identidad reutiliza su perfil y su
  acceso. Si hay datos de identidad incompatibles, se revierte todo el lote.
- La importación confirmada genera credenciales temporales para los accesos
  nuevos. Agregar hijos conserva la cuenta existente del apoderado.

## Compatibilidad de parentesco

El aprovisionamiento de acceso ya utiliza NO_DECLARADO cuando no se ha solicitado
un parentesco. V22 amplía el ENUM de MySQL para aceptar ese valor Java existente,
conservando los cuatro valores anteriores. No asigna MADRE, PADRE u otro parentesco
por inferencia y no modifica las migraciones ya aplicadas. Se mantiene la lista
de parentescos explícitos de las plantillas de importación.

## Validación automatizada

- ApoderadoVariosHijosHttpTest: alta de perfiles y acceso, login real, cambio de
  contraseña temporal, matrícula del primer hijo y adición/matrícula del segundo
  en otro grado con la misma sesión. Comprueba identidades, vínculos y cuentas,
  además de denegar acceso a un alumno no asociado.
- CargaMasivaFamiliasIntegrationTest: familia nueva con dos hijos, adición sin
  archivo de apoderados y rollback si la identidad del apoderado es incompatible.
- ParentescoMigracionTest: esquema anterior, ampliación del ENUM y preservación
  de los parentescos registrados, ejecutado con H2 en modo MySQL.

Estas pruebas no sustituyen ejecutar Flyway y un recorrido de aceptación contra
una instancia MySQL del entorno de destino. V22 debe aplicarse antes de usar
el aprovisionamiento de vínculos sin parentesco declarado en ese entorno.

# ASISCTRL-93: regresión de validaciones de horarios

Base: `develop` en `db224bc`. Rama: `feature/ASISCTRL-93-regresion-horarios`.
Trabajo realizado el 4 de noviembre de 2026. Esta tarea prueba y corrige el
backend de US-23; la validación visual del frontend pertenece a sus otras subtareas.

## 1. Qué significa esta tarea

Una prueba de regresión comprueba que una función siga funcionando después de
integrar cambios de otros compañeros. Aquí se comprueban las solicitudes a
`/api/horarios` con profesores, cursos, secciones, aulas y años guardados en la
base de pruebas. Los datos son ficticios y representan situaciones del colegio;
no se copiaron datos personales de la base compartida.

Ejemplo: Matemática de 1.º A ocupa el aula 301 los lunes de 08:00 a 09:00.
El sistema debe rechazar otra clase que use esa aula de 08:30 a 09:30 durante
el mismo año. Puede aceptar una clase que empiece a las 09:00, otra en martes
o una correspondiente a otro año académico.

## 2. Cómo recorre el código una solicitud

```text
Frontend / Swagger
  -> HorarioController: recibe el JSON y exige permisos
  -> HorarioService: aplica las reglas y controla la transacción
  -> HorarioRepository: consulta y guarda mediante JPA
  -> MySQL: almacena Horario y sus relaciones
  <- HorarioResponse: devuelve los datos que necesita el frontend
```

- **Controller**: la puerta de entrada HTTP. `POST /api/horarios` crea un bloque;
  `PUT /api/horarios/{id}` lo edita; `DELETE` realiza una baja lógica.
- **DTO**: el formulario de datos que entra o sale. `CrearHorarioRequest` recibe
  asignación, aula, día y horas. `HorarioResponse` devuelve IDs y nombres.
- **AsignacionCurso**: vincula curso, sección y docente. El año del bloque procede
  de su sección; el cliente no puede enviar un año falso para eludir los cruces.
- **Service**: decide si la operación es válida antes de guardarla.
- **Repository**: consulta entidades con parámetros enlazados; no concatena SQL
  con texto proporcionado por el usuario.
- **Transacción**: guarda el horario, su auditoría y los cambios del calendario
  como una sola operación. Si falla, se revierte la operación.

## 3. Correcciones realizadas

| Antes | Ahora |
| --- | --- |
| Un horario de un año anterior podía bloquear el mismo docente o aula en otro año. | `buscarConflictos` compara exclusivamente el año de la sección de la asignación solicitada. |
| Una asignación histórica retirada podía seguir bloqueando recursos si conservaba filas de horario activas. | La consulta exige tanto horario activo como asignación activa. |
| Bastaba con que la asignación estuviera activa, aunque su curso, sección o docente ya estuvieran inhabilitados. | Se comprueban curso, sección, perfil docente y Persona del docente. |
| No se impedía modificar horarios de una planificación cerrada o inhabilitada. | Se comprueba el año y se rechaza con `ANIO_NO_VIGENTE`. El año en borrador permite planificar. |
| Dos guardados podían comprobar simultáneamente que un recurso estaba libre. | Se bloquea la planificación del año hasta confirmar o revertir la transacción. El segundo guardado espera y revalida. |
| Los errores de cruce solo informaban un mensaje genérico. | Conservan los códigos HTTP y de negocio, identifican el bloque y agregan `details` con los horarios involucrados. |

La consulta compartida también se usa al cambiar el docente de una asignación.
Se actualizó ese llamado para que no confunda clases de otros años.

## 4. Cómo se detecta un cruce

Para dos intervalos del mismo día existe superposición si:

```text
inicioExistente < finNuevo Y finExistente > inicioNuevo
```

Los signos son estrictos: 08:00–09:00 y 09:00–10:00 son contiguos y no se cruzan.
Además, debe coincidir al menos un recurso: aula, docente o sección. En una
edición se excluye el propio ID del bloque para evitar un falso conflicto.

El aforo se comprueba contra la **capacidad máxima de la sección**, no contra
la cantidad de alumnos matriculados hoy: un aula para 29 no admite una sección
con capacidad máxima 30. Un aula para 30 sí la admite.

## 5. Cómo se protege el guardado simultáneo

`AnioAcademicoRepository.bloquear` ya existía y utiliza `PESSIMISTIC_WRITE`.
HorarioService lo reutiliza antes de comprobar los cruces. El bloqueo se mantiene
hasta el commit o rollback, incluyendo la sincronización del calendario de asistencia.

1. El administrador A reserva temporalmente la planificación del año.
2. Comprueba recursos y guarda el bloque.
3. El administrador B espera si intenta guardar en esa misma planificación.
4. Cuando A confirma, B vuelve a consultar. Si el recurso ya está ocupado, recibe 409.

Se usa `READ_COMMITTED` para que B vea lo que A acaba de confirmar. En MySQL una
lectura anterior de la transacción no debe ocultar ese nuevo bloque al revalidar.
Al mover un bloque entre años, los bloqueos se toman en orden numérico para
reducir el riesgo de espera cruzada. Las escrituras del mismo año se serializan;
es una decisión conservadora para mantener esta regla. No se utiliza un `synchronized`
de Java: el bloqueo está en la base y también coordina conexiones de distintos procesos.

El campo `version` conserva la protección existente frente a formularios antiguos:
si alguien editó el mismo registro, un guardado con la versión anterior recibe 409.

## 6. Pruebas añadidas

`controller/src/test/java/com/asiscontrol/controller/HorarioValidacionesHttpTest.java`
contiene 23 ejecuciones, incluidas las variantes parametrizadas:

- Alta válida, datos persistidos, auditoría de éxito y sesiones generadas.
- Cruces separados de aula, docente y sección; respuesta con detalle del conflicto.
- Horas iguales o invertidas, aforo insuficiente y aforo exacto.
- Bloques contiguos, otro día y otro año.
- Reasignación de docente sin falsos conflictos de otro año.
- Filas históricas de asignaciones retiradas.
- Curso, sección, docente, Persona y año inhabilitados; año cerrado.
- Aula o asignación inactiva.
- Edición que genera un conflicto: el bloque original permanece intacto.
- Edición sin autoconflicto y rechazo de versión antigua.
- Baja lógica que libera el recurso y conserva la asistencia ya tomada.
- Ausencia de autenticación y falta de permiso de escritura.

`HorarioConcurrenciaHttpTest.java` contiene una prueba con **tres escenarios**:
competencia por aula, por docente y por sección. Usa conexiones y transacciones
independientes. Mantiene el bloqueo del primer guardado, comprueba que el segundo
espera, confirma el primero y verifica que el segundo recibe 409. Solo queda un
bloque por escenario. El contexto de prueba se descarta después porque aquí es
necesario confirmar datos para que la otra conexión los vea.

Son pruebas de **integración HTTP**, no pruebas unitarias con repositorios simulados.
MockMvc ejecuta la API sin abrir un puerto web; servicios y repositorios son reales.
Spring Security Test simula el JWT, por lo que estas pruebas no validan su firma
criptográfica ni sustituyen una prueba del login.

## 7. Verificación con MySQL

Se ejecutaron estas cinco clases sobre una instancia privada de **MySQL 8.0.46**
en `127.0.0.1:33393`, base `asiscontrol_regresion93`:

| Clase | Ejecuciones | Fallos / errores |
| --- | ---: | --- |
| AsignacionDocenteHttpTest | 5 | 0 / 0 |
| CalendarioSesionHttpTest | 6 | 0 / 0 |
| HorarioConcurrenciaHttpTest | 1, con tres escenarios | 0 / 0 |
| HorarioFiltrosHttpTest | 18 | 0 / 0 |
| HorarioValidacionesHttpTest | 23 | 0 / 0 |
| Total | 53 | 0 / 0 |

Resultado: **BUILD SUCCESS**. La instancia temporal se detuvo al finalizar.
Se usó un esquema desechable generado por Hibernate bajo el perfil de pruebas;
esta ejecución verifica las reglas con el motor MySQL, no certifica las migraciones
de Flyway. No se modificaron el servicio MySQL80 habitual ni la base de AWS.

## 8. Cómo repetir las pruebas habituales

Desde la raíz `backend`, con Java 21:

```powershell
.\mvnw.cmd -B -ntp -pl controller -am '-Dtest=HorarioValidacionesHttpTest,HorarioConcurrenciaHttpTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd clean verify
```

El primer comando ejecuta esta regresión con H2. El segundo ejecuta todo el backend
y genera el informe global de cobertura. No aplicar opciones `create-drop` sobre
una base compartida: la instancia MySQL utilizada arriba fue creada exclusivamente
para estas pruebas y no es la base del equipo.

### Resultado de la verificación completa

`clean verify` finalizó el 4 de noviembre de 2026 a las 20:28 (UTC-5) con
**BUILD SUCCESS**: 362 pruebas, 0 fallos, 0 errores y 0 omitidas. Esta ejecución
completa utilizó H2; las 53 ejecuciones sobre MySQL se realizaron por separado.

| Métrica global JaCoCo | Cubierto / total | Cobertura |
| --- | ---: | ---: |
| Instrucciones | 32674 / 35285 | 92,60 % |
| Líneas | 6762 / 7227 | 93,57 % |
| Ramas de decisión | 1896 / 2534 | 74,82 % |

El control existente exige al menos 90 % en instrucciones y líneas. Pasó sin
cambiar umbrales ni exclusiones. Las ramas se informan, pero no alcanzan 90 %;
no corresponde presentar ese porcentaje como si todas las métricas lo superaran.
La cobertura mide código ejecutado durante las pruebas, no garantiza por sí sola
que no existan errores ni demuestra la aceptación visual del frontend.

El informe se encuentra en `controller/target/site/jacoco-aggregate/index.html`.
Los resultados individuales están en `controller/target/surefire-reports/`.

## 9. Límite del modelo que debe conocer el equipo

La historia menciona un período inhabilitado. En el modelo actual ni Horario ni
AsignacionCurso tienen un `periodoId`: el horario es semanal y pertenece al año
de la sección. Esta tarea protege el año cerrado o inhabilitado, pero **no puede
demostrar una restricción por período individual que no está representado en el
bloque**. El equipo debe acordar esa interpretación o modelar esa relación antes
de afirmar que todo el criterio de períodos de US-23 está completo.

No se agregaron tablas ni columnas. La comprobación visual del frontend y de un
despliegue real sigue correspondiendo a las tareas de integración del equipo.

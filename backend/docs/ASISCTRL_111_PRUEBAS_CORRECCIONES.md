# ASISCTRL-111: pruebas de corrección de calificaciones (US-50)

Base: `develop`, commit `6c3a583`. Rama: `feature/ASISCTRL-111-pruebas-correcciones`.
Trabajo del 5 de noviembre de 2026, limitado al backend.

## 1. Qué se comprueba

US-50 permite que el docente responsable corrija una calificación publicada
indicando un motivo. Deben conservarse el nivel anterior, el nuevo, el autor y la
fecha; la auditoría y el consolidado deben reflejar una corrección confirmada.

Ya existía la implementación en `CalificacionService` y ocho pruebas generales de
US-18/US-50 en `EvaluacionCompetenciasHttpTest`. Esta tarea añade una regresión
centrada en el docente y en los efectos que deja cada intento en la base.

## 2. Recorrido del código explicado desde cero

```text
POST /api/calificaciones/correcciones
    -> CalificacionDto.CorreccionRequest valida los datos recibidos
    -> CalificacionController exige CALIFICACIONES_ESCRIBIR
    -> CalificacionService.corregir controla la transacción del lote
    -> ModuloAccessGuard comprueba quién es el docente responsable
    -> se actualiza Calificacion y se guarda CorreccionCalificacion
    -> AuditoriaService registra la corrección exitosa
    -> la respuesta devuelve el nivel y la versión vigentes
```

- **DTO:** describe el JSON que recibe la API, con motivo y lista de correcciones.
- **Permiso:** permite entrar en la operación; no autoriza a cambiar notas ajenas.
- **Alcance del docente:** comprueba la asignación concreta de la evaluación.
- **Versión:** permite detectar que el formulario contiene una nota antigua.
- **Historial:** conserva nivel anterior/nuevo, motivo, autor, fecha y número de corrección.
- **Auditoría:** registra quién realizó la operación y qué cambió.
- **Consolidado:** se calcula consultando los niveles vigentes; no existe una nota
  consolidada duplicada que esta operación deba sobrescribir manualmente.
- **Transacción:** si un elemento del lote falla, se revierten todas sus notas,
  correcciones y auditorías de éxito. El registro independiente de un rechazo
  puede conservarse; no es una corrección exitosa.

## 3. Matriz de las nuevas pruebas automatizadas

Archivo: `controller/src/test/java/com/asiscontrol/controller/CorreccionCalificacionHttpTest.java`.
Los métodos parametrizados se ejecutan una vez por cada variante indicada.

| Caso | Variantes / ejecuciones | Comprobación principal |
| --- | ---: | --- |
| Corrección propia | 4: AD, A, B, C | HTTP 200, nota y versión persistidas, historial, autor docente, auditoría y consolidado. |
| Motivo inválido | 6: omitido, null, vacío, espacios, tabulación/salto de línea, 501 caracteres | HTTP 400 y nota, versión, historial y auditoría de éxito sin cambios. |
| Motivo en el límite | 1: 500 caracteres | Se acepta y se conserva completo. |
| Falta de autorización | 5: otro docente, solo lectura, asignación retirada, alumno, apoderado | HTTP 403, sin cambios. Los casos de alumno/apoderado incluyen deliberadamente permiso de escritura para comprobar también la defensa del servicio. |
| Sin autenticación | 1 | HTTP 401, sin cambios. |
| Solicitud incompleta | 5: lista omitida, null, vacía, elemento null, versión ausente | HTTP 400, sin cambios. |
| Nivel no admitido | 2: Z, 20 | HTTP 400, sin cambios. |
| Versión antigua | 1 | HTTP 409 y conservación de la corrección ya confirmada. |
| Nivel sin cambio | 1 | HTTP 422 y ausencia de una nueva versión o historial. |
| Acceso al historial | 1, con varios actores | Docente asignado y administrador acceden; otro docente, alumno, apoderado y anónimo no acceden. |
| Atomicidad del lote | 1, con cinco escenarios | Si el segundo elemento es ajeno, duplicado, inexistente, de versión incorrecta o no cambia el nivel, se revierte también el primero. |
| **Total nuevo** | **28 ejecuciones** | Se suman a la suite existente. |

Las reglas de publicación/borrador/cierre ya se comprueban en
`EvaluacionCompetenciasHttpTest`; se ejecutan junto con esta regresión.

## 4. Cómo se realizan y qué límites tienen

Son **pruebas de integración HTTP** con JUnit 5, Spring Boot, MockMvc, Spring
Security Test, JPA y H2 bajo el perfil `test`. MockMvc invoca el backend sin abrir
un puerto web. Los servicios y repositorios son reales, no mocks.

Los escenarios crean un alumno matriculado, docentes, cursos, una evaluación
publicada y sus notas ficticias. El acceso del docente también se persiste para
comprobar que la auditoría identifica su usuario y rol, no al administrador usado
para preparar los datos. La identidad HTTP se simula con JWT de prueba: no se
prueban aquí la contraseña, la firma criptográfica del JWT ni el frontend.

Habitualmente la transacción de cada prueba se revierte al terminar. La prueba de
lotes confirma únicamente su escenario en H2 y termina esa transacción de prueba
antes de llamar a la API. Así cada solicitud utiliza su propia transacción real:
la comprobación de rollback no queda enmascarada por una transacción exterior.
`@DirtiesContext` descarta ese contexto después y Hibernate elimina su esquema
de prueba. Ningún comando debe apuntar ese perfil a la base compartida.

Las pruebas negativas comprueban que no aparezca una auditoría **exitosa** de
corrección. No exigen borrar la auditoría del intento rechazado que el manejador
global puede guardar por separado.

## 5. Cómo repetirlo en IntelliJ o PowerShell

En IntelliJ, abrir la clase `CorreccionCalificacionHttpTest` y ejecutar su icono
verde. Java debe ser 21 y el proyecto Maven debe estar sincronizado.

Desde la raíz `backend`:

```powershell
.\mvnw.cmd -B -ntp -pl controller -am '-Dtest=CorreccionCalificacionHttpTest,EvaluacionCompetenciasHttpTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd clean verify
```

El primer comando comprueba la regresión específica y las pruebas anteriores de
evaluaciones. El segundo ejecuta todo el backend y genera JaCoCo. No hacen falta
AWS, contraseñas compartidas ni levantar el frontend.

Evidencias generadas:

- `controller/target/surefire-reports/TEST-com.asiscontrol.controller.CorreccionCalificacionHttpTest.xml`.
- `controller/target/surefire-reports/com.asiscontrol.controller.CorreccionCalificacionHttpTest.txt`.
- `controller/target/site/jacoco-aggregate/index.html`, después de `clean verify`.

## 6. Resultados de la ejecución

La regresión dirigida terminó con **BUILD SUCCESS**: 36 pruebas (28 nuevas y
8 existentes), 0 fallos, 0 errores y 0 omitidas. Se ejecutaron sobre H2 con el
perfil `test`.

La verificación completa `clean verify` terminó el 5 de noviembre de 2026 a las
20:54 (UTC-5) con **BUILD SUCCESS**: **450 pruebas, 0 fallos, 0 errores y 0 omitidas**.
La suite completa también usa H2; esta tarea no certifica la interfaz del
frontend ni la ejecución de migraciones en MySQL.

| Métrica global JaCoCo | Cubierto / total | Porcentaje |
| --- | ---: | ---: |
| Instrucciones | 35028 / 37607 | 93,14 % |
| Líneas | 7183 / 7653 | 93,86 % |
| Ramas de decisión | 2031 / 2672 | 76,01 % |

Se superó el control existente de 90 % para instrucciones y líneas sin modificar
umbrales ni exclusiones. Las ramas no alcanzan 90 %. La cobertura indica qué
código se ejecutó, no garantiza por sí sola que todos los requisitos estén
completos o que no queden errores.

### Defecto detectado y corregido

Antes, `{"motivo":"Revisión","correcciones":[null]}` superaba la validación
de la lista: `@NotEmpty` comprueba que contenga elementos, pero no que cada uno
sea distinto de null; `@Valid` tampoco impone esa restricción. El servicio
intentaba acceder al elemento y devolvía HTTP 500 por `NullPointerException`.

Se agregó `@NotNull` al tipo de cada elemento en
`CalificacionDto.CorreccionRequest`. Ahora Bean Validation lo rechaza con HTTP
400 antes de entrar en el servicio. Se mantiene el contrato de las solicitudes
válidas y no se modifican tablas, permisos ni reglas de calificación.

La primera ejecución de las ocho pruebas existentes pasó. La nueva prueba del
elemento null reprodujo el 500 antes de esta corrección y pasó después de agregar
la restricción.

## 7. Cierre en Git y Jira

Crear un PR de esta rama a `develop`, revisar las pruebas y vincular el PR a
ASISCTRL-111. Registrar en Jira los resultados efectivos de esta regresión.
La finalización de esta subtarea no demuestra por sí sola que las ventanas de
corrección e historial del frontend estén integradas ni permite cerrar el trabajo
de otra persona sin revisarlo.

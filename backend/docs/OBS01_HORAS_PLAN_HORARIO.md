# OBS-01 · Horas semanales del plan en el horario

El horario de un curso en una sección no puede programar más horas que las que define el plan de
estudios de su año y grado (`CursoEnPlan.horasSemanales`, no las horas del catálogo del curso).

## Decisiones acordadas

| Tema | Decisión |
|---|---|
| Unidad (criterio 5) | 1 hora del plan = 60 minutos de horario (`HorarioService.MINUTOS_POR_HORA`). Con bloques de 30 minutos, una hora pedagógica de 45 minutos no encaja en horas impares. |
| Qué se suma | Minutos de los bloques **vigentes** del curso en la sección, sin importar el docente. Los bloques retirados (US-27) y los de asignaciones retiradas no cuentan. |
| Límite | Llegar exacto al plan se permite; pasarlo se rechaza con `HORARIO_EXCEDE_PLAN` (422), indicando horas del plan, programadas, del bloque y disponibles. |
| Edición (criterio 2) | Se excluye el propio bloque y se suma su nueva duración. Si el bloque cambia de curso o sección, se valida contra el destino. |
| Sección ya excedida | Si el plan se redujo y la sección quedó por encima, se permiten ediciones que mantienen o bajan sus horas (cambiar aula, día o acortar). Se rechaza todo lo que las sube, incluido crear bloques. |
| Plan reducido (criterio 4) | Se **avisa sin bloquear**: el plan se guarda y `PlanEstudios.seccionesExcedidas` lista cada sección y curso con sus horas del plan y minutos programados. El aviso sigue apareciendo al consultar el plan hasta que se ajuste el horario. |
| Concurrencia | La validación corre después de `bloquearPlanificacion`, el mismo bloqueo del año que usa `PlanEstudiosService.guardar`: dos guardados simultáneos se serializan y el segundo ve las horas del primero. |
| Cargas masivas | La carga de horarios usa `HorarioService.crear` y la del plan usa `PlanEstudiosService.guardar`, así que aplican las mismas reglas. |

## Contrato

- `AsignacionCursoResponse.horasPlan` (`Integer`, `null` si el plan no incluye el curso): el formulario
  del bloque calcula programadas y disponibles con los bloques de la sección que ya tiene cargados.
- `PlanEstudios.seccionesExcedidas`: lista de `SeccionExcedida(seccionId, seccion, cursoId, curso,
  horasPlan, minutosProgramados)`; vacía si ninguna sección supera el plan.

## Pruebas

- `HorarioHorasPlanHttpTest`: límite exacto, exceso, bloques de 30 minutos, edición, bloques retirados,
  otra sección, sección excedida por plan reducido, aviso del plan, horas en la asignación y borrador.
- `HorarioHorasPlanConcurrenciaHttpTest`: dos guardados simultáneos con base de datos propia.
- `HorarioValidacionesHttpTest.aceptaTurnosDeMananaYTardeEnIntervalosDeMediaHora` ahora suma 4 h,
  dentro del plan de 4 h del curso de prueba.
# Preguntas para acordar las reglas de pagos — US-05 y US-12

Guía de conversación para el equipo y el Product Owner/profesor. Estas preguntas son propuestas para refinar el backlog; sus respuestas todavía no constituyen reglas aprobadas ni funcionalidades implementadas.

## Punto de partida

- US-05 configura el año, los períodos y los días no lectivos. El backlog deja pendiente confirmar su relación con pagos.
- US-12 permite emitir compromisos manuales para una matrícula, con concepto, cuota, monto y vencimiento. Deja pendiente confirmar conceptos, número de cuotas, pagos parciales, recargos y descuentos.
- Actualmente, el control de duplicados compara estudiante, año, concepto normalizado y cuota entre compromisos activos. Un concepto escrito de otra manera puede representar el mismo cobro sin que el sistema lo identifique como tal.
- Los períodos académicos y las cuotas no tienen por qué coincidir. No debe deducirse una cuota por bimestre sin una decisión del instituto.
- US-13 contempla el pago simulado; US-44, la situación financiera; US-48, la consulta familiar. Las decisiones que afecten a esas historias deben trasladarse también a sus criterios y pruebas.

## 1. Conceptos de cobro

1. ¿Qué conceptos puede cobrar el instituto: matrícula, pensión u otros? ¿Cuál es la lista autorizada?
2. ¿Se elegirá un concepto de un catálogo o se permitirá escribir uno nuevo? Si se permite crear conceptos, ¿quién puede hacerlo?
3. ¿Qué conceptos se cobran una sola vez y cuáles se repiten en cuotas?
4. ¿El monto de cada concepto es igual para todos o depende del año, grado, servicio o acuerdo con la familia?
5. ¿Cómo se nombrará una pensión para que todos usen el mismo concepto y el mes o la cuota se registre por separado?

**Debe quedar claro:** catálogo e identificador de cada concepto, responsables de mantenerlo, periodicidad y criterios para fijar el importe. La descripción libre no debería cambiar la identidad del cobro.

## 2. Plan de pagos

1. ¿Cuántas pensiones o cuotas corresponden a una matrícula durante el año? ¿Se cuentan aparte los derechos de matrícula?
2. ¿El plan será único por año o habrá planes distintos por grado, servicio o estudiante?
3. ¿Las cuotas tendrán el mismo monto? Si se divide un total, ¿cómo se resuelve la diferencia de céntimos?
4. ¿Cada cuota corresponde a un mes, un período académico o una fecha independiente?
5. ¿Se admiten acuerdos de fraccionamiento o pagos parciales? ¿Quién los autoriza y cómo se distinguen de las cuotas originales?

**Debe quedar claro:** plan aplicable a cada matrícula, total y cantidad de cuotas, numeración, montos, relación con períodos y versiones del plan. También qué sucede con compromisos ya emitidos cuando cambia el plan.

## 3. Fechas

1. ¿Qué día vence cada cuota y cuál es la fecha del primer vencimiento?
2. Si el vencimiento cae en un día no lectivo, feriado o fin de semana, ¿se mantiene o se traslada? ¿A qué día?
3. ¿Se permite que una obligación venza antes del inicio o después del cierre del año académico?
4. Si cambian las fechas de un período académico, ¿deben cambiar los vencimientos financieros? ¿También los ya emitidos?
5. ¿Desde qué momento se considera vencida una cuota? ¿Existe un plazo de gracia y afecta al estado, al recargo o a ambos?

**Debe quedar claro:** calendario financiero, relación explícita con US-05, momento de vencimiento en la hora de Lima y condiciones para cambiar fechas, con responsable y motivo.

## 4. Generación de compromisos

1. ¿Se generan todos los compromisos al finalizar la matrícula, por mes o mediante una emisión manual autorizada?
2. Antes de emitir, ¿se mostrará una vista previa con conceptos, cuotas, importes y vencimientos para confirmar?
3. Si se repite la generación por error o por un reintento, ¿cómo se garantiza que no se dupliquen obligaciones?
4. Si solo se emite una parte del plan, ¿cómo se consultan y generan después las cuotas pendientes?
5. ¿Cuándo podrá ver el apoderado cada compromiso: inmediatamente después de emitirlo o después de una publicación o aprobación?

**Debe quedar claro:** evento que dispara la generación, permisos, confirmación, tratamiento de reintentos, manejo de fallos parciales y visibilidad familiar. Si la matrícula genera compromisos, deberá ajustarse también el alcance actual de US-11, que no los genera.

## 5. Excepciones

1. Si un estudiante ingresa a mitad de año, ¿qué cuotas le corresponden? ¿Se cobra un mes completo o un importe proporcional?
2. Si se retira, cambia de sección o anula la matrícula, ¿qué obligaciones permanecen y cuáles se anulan? ¿Desde qué fecha?
3. ¿Habrá becas, descuentos o exoneraciones? ¿A qué conceptos y cuotas se aplican y quién las aprueba?
4. ¿Habrá recargos por atraso? Si los hay, ¿cómo se calculan, cuándo se aplican y quién puede retirarlos?
5. Si un compromiso tiene pagos aprobados y debe corregirse, ¿cuál es el procedimiento autorizado para ajustar el saldo o devolver un importe sin borrar la historia?

**Debe quedar claro:** condiciones, importes o fórmulas, vigencia, autorizaciones y documentación de cada excepción. Reprogramaciones, ajustes y devoluciones requieren su alcance y pruebas; no se asumen incluidos en la emisión manual.

## 6. Control

1. ¿Qué combinación identifica una obligación única: matrícula o estudiante/año, concepto del catálogo, cuota y, si corresponde, plan o período?
2. ¿Es obligatorio emitir las cuotas en orden o se permite emitir la cuota 3 antes de la 2? Si se permite, ¿cómo se advierte que falta una cuota?
3. ¿Debe bloquearse cualquier cuota que no exista en el plan? ¿Qué ocurre con cobros extraordinarios autorizados?
4. Cuando se anula un compromiso, ¿puede emitirse un reemplazo para la misma obligación? ¿Debe quedar enlazado al anterior y con qué motivo?
5. ¿Qué controles se necesitan para detectar faltantes, duplicados, diferencias de montos o cambios concurrentes de dos administradores?
6. ¿Quién puede emitir, modificar, anular o aprobar excepciones? ¿Qué información debe quedar en auditoría y qué puede consultar el apoderado?

**Debe quedar claro:** identidad de la obligación, reglas de numeración, controles que bloquean o solo advierten, tratamiento de anulados, permisos y trazabilidad.

## Cómo registrar las decisiones

Para cada respuesta, anotar:

| Dato | Qué registrar |
|---|---|
| Decisión | Regla concreta, evitando expresiones como «según corresponda». |
| Ejemplo válido | Un caso con matrícula, concepto, cuota, importe y fechas. |
| Ejemplo no permitido | Qué operación debe rechazarse y qué mensaje verá la persona. |
| Excepciones | Cuándo se admite otra conducta y quién la autoriza. |
| Responsable y fecha | Quién aprobó la regla y cuándo. |
| Historia y criterio | US-05, US-11, US-12, US-13, US-44 o US-48 afectadas; una nueva historia si el alcance lo requiere. |
| Prueba | Resultado observable y evidencia que demostrará el cumplimiento. |

## Ejemplos de criterios por completar tras acordar las respuestas

- Dado un plan aprobado de **N** cuotas, cuando se emiten las obligaciones de una matrícula, se crean únicamente las cuotas correspondientes con sus importes y vencimientos acordados.
- Dada una obligación activa para la misma identidad de cobro, cuando se repite su emisión, se rechaza la operación aunque cambie la descripción.
- Dada una generación ya procesada, cuando se repite la solicitud, no se crean obligaciones adicionales.
- Dado un cambio de calendario académico, cuando se guarda, los vencimientos financieros se mantienen o ajustan conforme a la política expresamente aprobada.
- Dado un apoderado con asociación vigente, cuando consulta pagos, solo ve las obligaciones publicadas de sus estudiantes asociados.

Estos ejemplos son borradores para acordar y completar; no prueban que el sistema ya cumpla esos comportamientos.

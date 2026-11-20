# Propuestas de flujo, navegación y usabilidad

## Alcance

Este documento propone mejoras para las funcionalidades implementadas en los portales de administrador, docente, alumno y apoderado. Describe el recorrido esperado, la organización de las interfaces, los estados que deben comunicarse y los criterios para comprobar cada experiencia. **Es una propuesta; no implica cambios de implementación.**

El contexto activo —año, grado, curso, sección, período y estudiante, según corresponda— debe permanecer visible. Cuando sea útil para volver atrás o retomar una tarea, también debe conservarse en la navegación. Cada pantalla debe diferenciar carga, ausencia real de datos, falta de publicación, error y falta de permisos.

## 1. Administrador → Académico → Plan de estudios

**Recorrido propuesto:** `Académico → Plan de estudios → año → grado → revisar cursos → guardar`.

### Pantalla y navegación

1. Al entrar, mostrar el año académico y su estado (*Borrador*, *Activo* o *Cerrado*). Permitir buscar o elegir el año sin recorrer páginas innecesarias. Al volver desde otra sección de Académico, conservar el año y el grado elegidos.
2. Tras elegir grado, mostrar un encabezado como “Plan de estudios · 2027 · 3.° grado”. Debajo, separar **Cursos incluidos** de **Agregar cursos**. Mostrar el número de cursos incluidos y el código de cada uno.
3. El catálogo debe permitir buscar por nombre o código, indicar qué cursos ya están incluidos y conservar la lista seleccionada al cambiar de página. Añadir o quitar un curso debe marcar el plan como **Cambios sin guardar**.
4. Ofrecer **Descartar cambios** y **Guardar plan**. Si quitar un curso está impedido por asignaciones activas, explicar la causa y dirigir a la gestión de esas asignaciones.
5. En el primer guardado de un año activo, abrir una confirmación con año, grado y lista definitiva. Usar una acción explícita, **Confirmar y definir plan**, e indicar que después quedará en solo lectura.
6. Tras guardar, mostrar el plan vigente en modo consulta, con su estado y fecha de actualización si está disponible. No mostrar acciones de edición que ya no se pueden ejecutar.

### Usabilidad y excepciones

- Si faltan años, grados o cursos, explicar qué configuración previa se necesita y ofrecer un enlace a ella.
- Si otro administrador cambió el plan, conservar los cambios locales y ofrecer comparar con la versión nueva antes de descartarlos.
- Ante un fallo de carga, mostrar **Reintentar** sin borrar año y grado.
- En móvil, apilar las dos listas y mantener visibles el conteo y la acción de guardado.

**Comprobación:** el administrador puede formar un plan y conoce las consecuencias del primer guardado antes de confirmarlo.

## 2. Docente → Evaluaciones → Libretas

**Recorrido propuesto:** `Mis evaluaciones → calificar → Libretas → curso/sección y período → generar borradores → revisar → publicar → consultar como alumno`.

### Pantalla y navegación

1. En **Mis evaluaciones**, nombrar la acción **Publicar evaluación** y explicar que esto aún no publica la libreta del período. Tras completarla, ofrecer **Ir a Libretas** conservando curso, sección y período.
2. En **Libretas**, mostrar un encabezado contextual, por ejemplo: “Matemática · 3.° A · Bimestre I”. Bajo él, resumir alumnos matriculados, evaluaciones incluidas, niveles faltantes, borradores existentes y última versión publicada.
3. Mostrar los pendientes antes de generar: qué evaluación o competencia necesita calificación, con enlace directo a su planilla. Evitar que el docente descubra los errores solo después de intentar publicar.
4. **Generar borradores** debe explicar cuántos se generarán y si reemplazará borradores previos. Después, mostrar una fila por estudiante con estado *Completo*, *Incompleto* o *Requiere revisión*.
5. Al abrir un borrador, mostrar la vista que recibirá la familia: curso, período, competencias, evaluaciones, niveles, ponderaciones, observaciones y versión. Permitir volver al listado sin perder el contexto. Si la revisión individual es obligatoria, registrar qué borradores fueron revisados y bloquear la publicación hasta completar el requisito.
6. Antes de **Publicar libretas del período**, mostrar la cantidad de informes, destinatarios y versión. Tras publicar, mostrar resultado, fecha y acceso a una vista de ejemplo del informe publicado.

### Usabilidad y excepciones

- Diferenciar visual y verbalmente **Publicar evaluación** de **Publicar libretas**. Son operaciones distintas.
- Al volver a la pestaña, recuperar el estado persistido de borradores y versiones; no depender solo de lo que permanece en memoria durante esa visita.
- Si cambiaron las notas después de generar, mostrar **Borradores desactualizados** y el paso para regenerarlos.
- Si se corrigieron notas después de publicar, indicar que la libreta visible para la familia conserva la versión anterior hasta una nueva publicación.
- Usar mensajes que identifiquen el registro que impide publicar, no solo un error general.

**Comprobación:** el docente y el profesor revisor pueden seguir una calificación hasta la libreta que efectivamente ve el alumno.

## 3. Docente → Evaluaciones → Evidencias

**Recorrido propuesto:** `Evidencias → curso/sección → estudiante → listado → crear o vincular evidencia`.

### Pantalla y navegación

1. Elegir curso y sección. La lista de estudiantes debe venir de las matrículas de esa asignación, aunque todavía no exista una evaluación.
2. Después de elegir estudiante, mostrar el encabezado con nombre, curso y sección. Competencia y período deben funcionar como filtros adicionales; cambiar un filtro no debe obligar a repetir las selecciones anteriores.
3. El listado debe presentar descripción, competencia, período, archivo y estado de vínculo: **Vinculada a una evaluación** o **Sin evaluación vinculada**. Permitir abrir el detalle desde cada fila.
4. **Nueva evidencia** debe conservar alumno y curso. Pedir competencia y período, y permitir descripción, archivo o ambos. Diferenciar el estado **Subiendo archivo** del estado **Guardando evidencia**.
5. Tras guardar, ofrecer **Vincular a evaluación**. Mostrar únicamente evaluaciones compatibles con el curso y la competencia. Permitir iniciar la misma acción desde una evaluación.
6. Antes de inactivar, explicar si la evidencia está vinculada y qué efecto tendrá. Si el backend impide inactivarla, indicar la relación que debe resolverse.

### Usabilidad y excepciones

- No mostrar “Pendiente de backend” al docente. Si falta una condición previa, explicarla en términos de su tarea.
- Si no hay competencias o períodos, indicar cuál falta y enlazar a su configuración.
- Si falla la carga de un dato, ofrecer reintentar ese dato sin ocultar los otros que sí cargaron.
- Tras crear o editar, volver al mismo alumno y filtros y destacar el registro afectado.
- Informar tipos y tamaño de archivo antes de seleccionar uno; mantener el formulario si falla la carga.

**Comprobación:** se puede registrar una evidencia antes de crear evaluaciones y luego rastrear su relación con una evaluación concreta.

## 4. Docente → Progreso

**Recorrido recomendado:** `Progreso → curso/sección → período → estudiantes → detalle por competencia → evaluación concreta`.

### Pantalla y navegación

1. La primera vista debe resumir el curso y la sección: estudiantes, competencias evaluadas, evaluaciones recientes y alumnos sin resultados. Permitir buscar un estudiante.
2. Al elegir un alumno, mostrar una ruta visible, por ejemplo: `Progreso > Matemática 3.° A > Ana`. Mantener período y alumno al volver del detalle.
3. Organizar el detalle por competencia. Para cada una, mostrar los resultados en orden temporal con evaluación, fecha, nivel y observación. Evitar presentar un promedio numérico si las reglas académicas no definen cómo calcularlo desde AD/A/B/C.
4. Separar **Resultados actuales o preliminares** de **Última libreta publicada**. Indicar si una corrección todavía no aparece en la libreta de la familia.
5. Añadir enlaces contextuales **Abrir planilla para calificar** e **Ir a Libretas del período**, conservando curso y sección.

### Alcance alternativo y usabilidad

Si no se desarrollará un seguimiento entre evaluaciones, renombrar el menú y encabezado a **Resultados por evaluación**. La interfaz actual muestra una evaluación y un alumno a la vez; su nombre no debería sugerir una evolución que aún no presenta.

Sustituir parte de la cadena de desplegables por listas con fecha y estado. Tras guardar una corrección en la planilla, actualizar el detalle con los datos recién cargados. Distinguir “aún sin resultados” de un error de consulta y ofrecer **Reintentar** ante este último.

**Comprobación:** el profesor puede pedir la evolución de una competencia y obtenerla, o encuentra una consulta puntual nombrada con precisión.

## 5. Alumno → Progreso

**Recorrido propuesto:** `Progreso → período → cursos con libreta → libreta de un curso → detalle y PDF`.

### Pantalla y navegación

1. Al entrar, mostrar el período más reciente con informes publicados y permitir elegir anteriores. Presentar los cursos como tarjetas o filas con **Ver libreta**, en lugar de exigir una segunda selección en un desplegable.
2. Al abrir un curso, mostrar curso, período, versión y fecha de publicación. Incluir una leyenda visible: **AD = Logro destacado; A = Logro esperado; B = En proceso; C = En inicio**.
3. Presentar primero un resumen por competencia. Debajo, mostrar las evaluaciones que lo sustentan, con fecha, nivel, peso y observaciones. Mantener la tabla completa como detalle cuando sea necesaria.
4. Situar **Descargar PDF** en el encabezado de la libreta abierta. El archivo debe identificarse con curso y período, no únicamente con IDs internos.

### Usabilidad y excepciones

- Diferenciar “todavía no se publicó ninguna libreta” de “no hay cursos para este período” y de “falló la consulta”.
- No mostrar borradores al alumno ni insinuar que una nota preliminar ya es definitiva.
- Explicar qué representa el peso de una evaluación y mostrar el período al desplazarse por el detalle.
- Hacer legible el detalle en móvil sin exigir desplazamiento horizontal para entender competencia, nivel y observación.

**Comprobación:** el alumno entiende qué resultado ve, a qué período pertenece y cómo obtener su informe publicado.

## 6. Alumno → Asistencia

**Recorrido propuesto:** `Asistencia → Hoy o Historial → detalle de clase`. La pantalla permanece en modo consulta; el apoderado realiza las solicitudes de justificación.

### Pantalla y navegación

1. **Hoy** debe mostrar fecha del colegio y clases en orden horario. Distinguir *Presente*, *Tardanza*, *Inasistencia*, *Sin marcar* y *Pendiente de registro*. Explicar que una clase pendiente de registro no significa inasistencia.
2. **Resumen** debe indicar el período de sus cifras. Junto al porcentaje, explicar que presentes y tardanzas cuentan como asistencia y “sin marcar” queda fuera del cálculo.
3. **Historial** debe permitir año, período, curso y estado. Al abrir un registro, mostrar fecha, hora de la clase, comentario del docente y estado de la justificación.
4. Ante una inasistencia sin justificar, indicar cómo pedir ayuda al apoderado: ingresar a su portal, abrir Asistencia y localizar esa fecha. No mostrar al alumno una acción de envío que no puede realizar.

### Usabilidad y exactitud

- Si falla el horario, mostrar **No se pudieron cargar las clases de hoy** y **Reintentar**; mantener visible el historial. No convertir un error en “Hoy no tienes clases”.
- Calcular “Hoy” y “Este mes” con la zona horaria del colegio.
- Obtener el historial completo o totales del servidor antes de calcular el porcentaje.
- Indicar cuántos registros adicionales mostrará **Ver más** y cuántos quedan.

**Comprobación:** el alumno distingue una falta real de una clase aún no registrada y las cifras coinciden con el historial completo.

## 7. Apoderado → Progreso

**Recorrido propuesto:** `Progreso → hijo → período → cursos publicados → libreta → PDF`.

### Pantalla y navegación

1. Si hay un hijo, abrir directamente sus libretas y mostrar su nombre. Si hay varios, mostrar tarjetas con nombre, código y **Ver progreso**.
2. Dentro de un hijo, mantener un selector compacto para cambiar. Repetir su identidad en el encabezado del detalle: “Libreta de Ana · Matemática · Bimestre I”.
3. Al cambiar de hijo, ocultar de inmediato el informe anterior. Conservar el período si existe para ambos; en caso contrario, elegir el más reciente disponible y avisar del cambio.
4. Permitir volver a la selección de hijos y usar Atrás sin perder el período. La URL puede reflejar hijo y período para recuperar el contexto, mientras el backend sigue verificando la vinculación familiar.
5. Reutilizar la misma explicación de niveles y el mismo formato de libreta que ve el alumno.

### Usabilidad y excepciones

- Diferenciar “este hijo no tiene libreta publicada” de “no se pudo consultar”.
- Cargar lista de hijos e informes con errores y reintentos independientes.
- Si no hay hijos asociados, explicar que debe revisarse la vinculación en el colegio.
- Evitar que un informe del hijo anterior permanezca visible durante la carga del nuevo.

**Comprobación:** cambiar entre dos hijos nunca mezcla datos y el informe coincide con la vista del alumno correspondiente.

## 8. Apoderado → Asistencia → Ver asistencia de un hijo

**Recorrido propuesto:** `Asistencia → hijo → resumen e historial → registro → solicitar justificación → seguir solicitud`.

### Pantalla y navegación

1. Con varios hijos, mostrar tarjetas de elección. Con uno, abrir directamente. Mantener nombre y código del estudiante en el encabezado y permitir cambiarlo desde allí.
2. Mostrar porcentaje y conteos del período elegido. En cada registro, separar **estado de asistencia** de **estado de justificación**: *Sin solicitud*, *En revisión*, *Aprobada* o *Rechazada*.
3. Permitir filtrar por año, período, curso y estado. Mostrar los filtros activos y permitir retirarlos individualmente. Diferenciar “sin registros” de “ningún registro coincide con estos filtros”.
4. En el detalle de una inasistencia, mostrar hijo, curso, fecha, hora, estado y comentario. Si corresponde, ofrecer **Solicitar justificación**.
5. El formulario solicita motivo, observaciones cuando sean obligatorias y sustento opcional. Antes de enviar, explicar que el colegio revisará la solicitud: enviarla no equivale a aprobarla.
6. Después del envío, regresar al registro y mostrar **Pendiente de revisión**, fecha o número de solicitud si están disponibles y dónde consultar la respuesta.
7. Si se rechaza, mostrar el motivo y la siguiente acción permitida por la política real. No prometer un nuevo envío si el sistema no lo admite.

### Usabilidad y excepciones

- Limpiar errores anteriores al cambiar de hijo o reintentar; no mostrar una alerta vieja junto a datos nuevos.
- Validar que **Desde** no sea posterior a **Hasta** y mostrar el error junto a los campos.
- Mantener filtros, posición y registro seleccionado después de cerrar un diálogo.
- En móvil, mostrar registros como tarjetas legibles sin ocultar el estado de justificación ni la acción disponible.
- Calcular los resúmenes con todos los registros pertinentes, no con una página parcial.

**Comprobación:** se puede seguir claramente el recorrido *sin justificar → enviada → revisada* sin confundir hijos ni estados.

## 9. Recuperar contraseña

Se debe cambiar el diseño de la pantalla de **Recuperar contraseña** porque todavía utiliza el diseño anterior y no el nuevo diseño de la pantalla de inicio de sesión.

## Criterios transversales y orden sugerido

1. Mantener visible el contexto activo y conservarlo al volver atrás o cambiar de pantalla.
2. Usar mensajes distintos para carga, vacío, error, falta de permisos y falta de publicación.
3. Nombrar cada acción según su efecto real: **Publicar evaluación**, **Publicar libreta**, **Solicitar justificación** y **Descargar PDF**.
4. Confirmar acciones que cierran o publican información y mostrar a quiénes y cuántos registros afectan.
5. Mostrar totales completos, versión, período y estado de los informes.
6. Dar soporte a teclado, foco visible, etiquetas de campos, errores cercanos al control y lectura en móvil.

**Orden recomendado:** primero el recorrido Evaluaciones → Libretas → Progreso de alumno/apoderado; después Evidencias; luego exactitud y estados de Asistencia; finalmente Plan de estudios y la actualización visual de Recuperar contraseña.

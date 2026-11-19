# Subtareas del Sprint 2

**Fechas:** 26 oct – 6 nov  
**Historias:** 12 · **Puntos:** 84 · **Subtareas:** 49

Cada subtarea lleva un prefijo según el área: **[Backend]**, **[Frontend]** o **[Pruebas]**. Todas empiezan en *Por hacer*.

## Admisión, matrícula y gestión financiera

### US-12 — Emitir, consultar y anular compromisos de pago en soles asociados a una matrícula

*5 puntos · Prioridad Alta*

- [ ] [Backend] Verificar que solo se emita sobre una matrícula vigente y que la anulación exija motivo
- [ ] [Frontend] Menú Finanzas: listado de compromisos por estudiante, matrícula y estado
- [ ] [Frontend] Emitir compromiso en soles: concepto, monto y fecha de vencimiento
- [ ] [Frontend] Detalle del compromiso y anulación con motivo
- [ ] [Pruebas] Pruebas de emisión, consulta y anulación con backend simulado

### US-49 — Consultar el estado y el historial de matrícula de un estudiante asociado

*3 puntos · Prioridad Media*

- [ ] [Backend] Permitir /solicitudes-matricula/{id}/historial al apoderado asociado (403 si no lo está)
- [ ] [Frontend] Línea de tiempo real en Detalle de mi solicitud
- [ ] [Pruebas] Prueba de acceso de un apoderado no asociado

## Control y seguimiento de asistencia

### US-14 — Registrar y finalizar la asistencia de estudiantes por clase o sesión

*8 puntos · Prioridad Alta*

- [ ] [Backend] Endpoint para finalizar la sesión de asistencia
- [ ] [Backend] Restringir el registro a cursos y secciones asignados al docente
- [ ] [Frontend] Pantalla del docente: elegir sesión, marcar y finalizar
- [ ] [Pruebas] Pruebas de backend y de la pantalla

### US-15 — Marcar mi propia asistencia general mediante un QR institucional dinámico

*8 puntos · Prioridad Alta*

- [ ] [Frontend] Pantalla del administrador: QR vigente, renovar y cancelar
- [ ] [Frontend] Marcado del docente con el código QR
- [ ] [Pruebas] Pruebas de QR vencido, cancelado y marcado tardío

### US-32 — Corregir un registro finalizado de asistencia de estudiante o docente indicando un motivo

*5 puntos · Prioridad Alta*

- [ ] [Backend] Corrección de un registro finalizado con motivo obligatorio y auditoría
- [ ] [Frontend] Ventana de corrección con valor anterior, nuevo y motivo
- [ ] [Pruebas] Pruebas de permisos y auditoría de la corrección

## Gestión curricular, evaluación y aprendizaje

### US-18 — Registrar evaluaciones cualitativas AD, A, B o C por competencia

*8 puntos · Prioridad Alta*

- [ ] [Backend] Verificar que el docente solo evalúe en sus asignaciones y competencias del curso
- [ ] [Frontend] Menú Evaluaciones: elegir curso y sección, y crear la evaluación con sus competencias
- [ ] [Frontend] Registro de niveles AD, A, B o C por estudiante y competencia con guardado por lote
- [ ] [Frontend] Publicar y cerrar la evaluación
- [ ] [Pruebas] Pruebas de registro de niveles, publicación y evaluación cerrada

### US-22 — Crear, publicar y hacer seguimiento de tareas académicas para mis cursos y secciones

*8 puntos · Prioridad Alta*

- [ ] [Frontend] Menú Tareas del docente: listado por curso y sección con su estado
- [ ] [Frontend] Crear y editar tarea: descripción, adjuntos, fecha límite y puntaje
- [ ] [Frontend] Publicar, cerrar y archivar la tarea
- [ ] [Frontend] Seguimiento de entregas por estudiante y calificación
- [ ] [Pruebas] Pruebas del ciclo de la tarea con backend simulado

### US-41 — Presentar una entrega para una tarea publicada

*5 puntos · Prioridad Alta*

- [ ] [Frontend] Menú Tareas del estudiante: tareas publicadas de su sección
- [ ] [Frontend] Presentar y editar la entrega con archivos y comentario antes de la fecha límite
- [ ] [Frontend] Estado, nota y comentario del docente sobre la entrega
- [ ] [Pruebas] Pruebas de entrega válida, fuera de plazo y de otra sección

### US-50 — Corregir una evaluación indicando un motivo

*8 puntos · Prioridad Alta*

- [ ] [Backend] Corrección de una calificación publicada con motivo obligatorio, auditoría y recálculo del consolidado
- [ ] [Frontend] Ventana de corrección con nivel anterior, nuevo y motivo
- [ ] [Frontend] Historial de correcciones de la calificación
- [ ] [Pruebas] Pruebas de corrección propia, ajena y sin motivo

## Planificación académica, recursos y horarios

### US-23 — Asignar manualmente bloques de horario y validar en tiempo real cruces y capacidad del aula

*13 puntos · Prioridad Alta*

- [ ] [Frontend] Matriz semanal por sección (solo lectura)
- [ ] [Frontend] Ventana Nuevo bloque: crear, editar y eliminar
- [ ] [Frontend] Mostrar los conflictos de docente, aula y aforo que devuelve el servidor
- [ ] [Pruebas] Pruebas de la matriz y de la ventana de bloque
- [ ] [Backend] Regresión de las validaciones de /api/horarios con datos reales

### US-24 — Visualizar una matriz gráfica de horarios por sección, aula, docente y curso

*8 puntos · Prioridad Alta*

- [ ] [Frontend] Selector de perspectiva: sección, docente, aula y curso
- [ ] [Frontend] Detalle del bloque al hacer clic y estado vacío
- [ ] [Pruebas] Pruebas de las cuatro perspectivas
- [ ] [Backend] Filtros por año, sección, docente, aula y curso en GET /api/horarios

### US-58 — Asignar docentes activos a cursos, secciones y períodos

*5 puntos · Prioridad Alta*

- [ ] [Backend] Verificar la regla PLA-06: no retirar docente con bloques de horario futuros
- [ ] [Frontend] Pestaña Horarios > Asignaciones docentes: listado y alta
- [ ] [Frontend] Edición con versión y retiro con motivo
- [ ] [Pruebas] Pruebas de la pestaña con backend simulado

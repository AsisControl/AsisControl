# Subtareas del Sprint 3

**Fechas:** 9 nov – 20 nov  
**Historias:** 15 · **Subtareas:** 60

Cada subtarea lleva un prefijo según el área: **[Backend]**, **[Frontend]** o **[Pruebas]**.

## Admisión, matrícula y gestión financiera

### US-13 · Pagar un compromiso mediante tarjeta o QR en una pasarela simulada (ASISCTRL-25)

- [ ] [Frontend] Menú Pagos del apoderado: compromisos pendientes de sus estudiantes
- [ ] [Frontend] Pasarela simulada: pago con tarjeta o QR y resultado aprobado o rechazado
- [ ] [Frontend] Comprobante interno después de un pago aprobado
- [ ] [Backend] Verificar que el apoderado solo pague compromisos de sus estudiantes y sin pagos duplicados
- [ ] [Pruebas] Pruebas de pago aprobado, rechazado y de compromiso ajeno

### US-44 · Consultar compromisos, transacciones e indicadores de morosidad (ASISCTRL-49)

- [ ] [Backend] Verificar el cálculo de los estados Pendiente, Parcial, Pagado y Vencido
- [ ] [Frontend] Finanzas: transacciones con filtros por estudiante, estado y fecha
- [ ] [Frontend] Indicadores de morosidad: monto vencido, compromisos vencidos y estudiantes morosos
- [ ] [Pruebas] Pruebas de los cuatro estados y de los indicadores

### US-48 · Consultar los compromisos, transacciones y comprobantes internos de un estudiante asociado (ASISCTRL-53)

- [ ] [Frontend] Menú Pagos: compromisos, transacciones y comprobantes del estudiante asociado
- [ ] [Frontend] Detalle del comprobante interno
- [ ] [Pruebas] Pruebas de estudiante asociado y no asociado

## Control y seguimiento de asistencia

### US-16 · Consultar informes e indicadores institucionales de inasistencias y tardanzas de estudiantes y docentes (ASISCTRL-28)

- [ ] [Backend] Indicadores de inasistencias y tardanzas por período, sección y docente
- [ ] [Frontend] Menú Asistencia del administrador: reporte de estudiantes por período y sección
- [ ] [Frontend] Reporte de asistencia docente con tardanzas e inasistencias
- [ ] [Pruebas] Pruebas de los reportes y sus filtros

### US-45 · Consultar mis registros de asistencia (ASISCTRL-50)

- [ ] [Frontend] Menú Asistencia del estudiante: registros por período y curso
- [ ] [Frontend] Resumen de asistencias, inasistencias y tardanzas
- [ ] [Pruebas] Pruebas de consulta propia y sin registros

### US-47 · Consultar la asistencia de un estudiante asociado a mi perfil (ASISCTRL-52)

- [ ] [Frontend] Menú Asistencia del apoderado: selector de estudiante asociado y período
- [ ] [Frontend] Detalle de los registros con sus justificaciones
- [ ] [Pruebas] Pruebas de estudiante asociado y no asociado

## Gestión curricular, evaluación y aprendizaje

### US-17 · Registrar y clasificar evidencias pedagógicas por estudiante, curso y competencia (ASISCTRL-29)

- [ ] [Backend] Endpoints de evidencias pedagógicas con archivo, estudiante, curso y competencia
- [ ] [Backend] Restringir el registro de evidencias a las asignaciones del docente
- [ ] [Frontend] Registrar evidencia: descripción, archivo y competencia
- [ ] [Frontend] Listado de evidencias por estudiante y competencia
- [ ] [Pruebas] Pruebas de registro, archivo inválido y contexto ajeno

### US-19 · Configurar y versionar las reglas institucionales de consolidación del logro por competencia (ASISCTRL-31)

- [ ] [Backend] Reglas de consolidación versionadas: borrador, activación y versiones anteriores
- [ ] [Backend] Calcular el consolidado con la versión vigente de la regla
- [ ] [Frontend] Pestaña Académico > Reglas académicas: versiones y regla vigente
- [ ] [Frontend] Crear y activar una nueva versión de la regla
- [ ] [Pruebas] Pruebas de versionado y de cálculo reproducible

### US-20 · Generar y publicar la libreta o informe de progreso por competencias de cada estudiante (ASISCTRL-32)

- [ ] [Backend] Generar la libreta por estudiante y período a partir del consolidado
- [ ] [Backend] Publicar la libreta e impedir cambios sin corrección registrada
- [ ] [Frontend] Vista previa de la libreta por estudiante y competencia
- [ ] [Frontend] Publicar las libretas de una sección
- [ ] [Pruebas] Pruebas de generación, publicación y estudiante sin calificaciones

### US-21 · Que el sistema detecte y muestre estudiantes en riesgo con sus motivos e indicadores (ASISCTRL-33)

- [ ] [Backend] Verificar la ejecución de los criterios de riesgo y el motivo de cada alerta
- [ ] [Frontend] Pestaña Académico > Estudiantes en riesgo: listado con motivos e indicadores
- [ ] [Frontend] Seguimiento de la alerta: cambio de estado con observación
- [ ] [Pruebas] Pruebas de detección, filtros y seguimiento

### US-31 · Consultar el progreso publicado de un estudiante asociado a mi perfil (ASISCTRL-41)

- [ ] [Backend] Progreso publicado visible solo para apoderados del estudiante
- [ ] [Frontend] Menú Progreso del apoderado: selector de estudiante y período
- [ ] [Frontend] Niveles por competencia de la libreta publicada
- [ ] [Pruebas] Pruebas de estudiante asociado, no asociado y sin libreta

### US-46 · Consultar mi progreso académico publicado por competencias (ASISCTRL-51)

- [ ] [Frontend] Menú Progreso del estudiante: libreta publicada por período
- [ ] [Frontend] Niveles por competencia y comentarios del docente
- [ ] [Pruebas] Pruebas de consulta propia y sin libreta publicada

## Gobierno institucional, configuración y servicios transversales

### US-51 · Cargar masivamente perfiles docentes (ASISCTRL-56)

- [ ] [Backend] Plantilla Excel e importación masiva de docentes con las validaciones de la ficha
- [ ] [Frontend] Carga masiva en Fichas académicas > Docentes
- [ ] [Frontend] Resumen de filas importadas y errores por fila
- [ ] [Pruebas] Pruebas de archivo válido, duplicados y errores de formato

## Planificación académica, recursos y horarios

### US-27 · Consultar el historial de cambios de cada bloque de horario (ASISCTRL-37)

- [ ] [Backend] Historial inmutable de cada bloque: valor anterior, nuevo, motivo, actor y fecha
- [ ] [Backend] Exigir motivo al modificar o retirar un bloque de horario
- [ ] [Frontend] Pestaña Horarios > Historial de cambios con filtros por período, sección, docente, aula y fecha
- [ ] [Pruebas] Pruebas de creación, modificación, retiro e intento de editar el historial

### US-30 · Consultar mi horario vigente (ASISCTRL-40)

- [ ] [Backend] Restringir el horario del estudiante a su propia sección
- [ ] [Frontend] Menú Horario del estudiante: matriz semanal de su sección
- [ ] [Frontend] Detalle del bloque y mensaje «Sin horarios asignados»
- [ ] [Pruebas] Pruebas de horario propio, ajeno y vacío

# AsisControl · Seguimiento de la retroalimentación del JP

## Evaluaciones y límites del alcance

La planilla docente registra los niveles **AD, A, B y C** por competencia. El docente recoge las evidencias de aprendizaje en el aula, en una entrega o mediante un instrumento externo. AsisControl conserva el resultado, su publicación y las correcciones con motivo. Rendir exámenes dentro de la plataforma requeriría instrumentos, intentos, tiempos, respuestas y reglas de calificación. El equipo puede sustentar que la prioridad actual es el seguimiento académico y la visibilidad de resultados. [Documentación de importación de notas de Moodle](https://docs.moodle.org/502/en/Grade_import)

La planilla ya muestra cuántos niveles faltan y resume los cambios pendientes. Para mejorarla, priorizaría un filtro de estudiantes incompletos, acceso directo a la siguiente celda pendiente y pegado desde hoja de cálculo con vista previa. La carga de notas debería respetar los niveles admitidos, las matrículas de la sección y el flujo de corrección de resultados ya publicados.

## Reutilización después del primer registro

La guía docente de Paideia contempla replicar contenidos de semestres anteriores. Moodle permite elegir recursos o actividades de otro curso y excluye datos de usuarios en esa importación. AsisControl aplica ese principio a las asociaciones del catálogo de competencias. [Guía docente de Paideia PUCP](https://sites.google.com/pucp.edu.pe/guia-del-docente-ghot/paideia) · [Importación entre cursos en Moodle](https://docs.moodle.org/400/en/Import_course_data)

| Proceso | Qué reutilizar y revisar |
| --- | --- |
| Competencias por curso | En Competencias base se pueden copiar competencias entre fichas de cursos distintos. Al agregar un curso del catálogo, el plan anual parte de estas competencias y guarda su propia selección. Al importar el plan del año anterior, los cursos agregados traen sus competencias activas y los cursos que ya están en el destino conservan su selección. |
| Plan de estudios del año siguiente | Importar en bloque los cursos del mismo grado, revisar los faltantes y ajustar sus nombres y horas en el plan de destino. El código del curso conserva su identidad entre años. |
| Tareas y evaluaciones del docente | Copiar como borrador el enunciado, competencias, ponderación y materiales autorizados. Pedir fechas nuevas. Cada sección empieza con sus propias entregas y calificaciones. |
| Configuración del año | Reutilizar la estructura de grados y períodos. Ajustar fechas, secciones, capacidad y responsables antes de activar. |
| Horarios | Usar los bloques anteriores como propuesta. Volver a validar docentes, aulas, cruces y disponibilidad antes de publicar. |
| Tarifas de pensiones | Proponer conceptos y tarifas por grado para el nuevo año. Confirmar importes y vigencia. Las becas individuales requieren una decisión para el nuevo período. |

## Notificaciones y correo institucional

La publicación de resultados y la corrección de calificaciones publicadas generan un aviso en AsisControl por estudiante y evaluación, con destino al estudiante y a sus apoderados vinculados y habilitados. Con SMTP habilitado, el correo se envía a las direcciones verificadas después de confirmar la transacción. El correo contiene un enlace a Progreso. El detalle permanece en el portal autenticado.

Eventos para una entrega posterior: tarea publicada y cambio de cierre para el estudiante, renovación aprobada o rechazada para el apoderado, corrección de asistencia que afecte al estudiante, y constancia disponible tras un pago. Para recordatorios de vencimiento se debe definir frecuencia, preferencia de canal y criterio de deuda con el colegio.

### Evidencia local del envío

1. Ejecutar Mailpit en un entorno local. Configurar AsisControl con `APP_MAIL_ENABLED=true`, un remitente ficticio en `APP_MAIL_FROM`, `SMTP_HOST=127.0.0.1`, `SMTP_PORT=1025`, `SMTP_AUTH=false` y `SMTP_STARTTLS=false` para ese SMTP local.
2. Usar una cuenta de prueba y confirmar su dirección ficticia en Administración → Usuarios. Desde Docente, publicar y luego corregir una nota. Comprobar el aviso dentro de AsisControl, su enlace a Progreso y el mensaje capturado en la interfaz de Mailpit. [Configuración SMTP de Mailpit](https://mailpit.axllent.org/docs/configuration/smtp/) · [Funciones de Mailpit](https://mailpit.axllent.org/docs/)

### Buzones que realmente funcionen

La dirección generada por AsisControl es un identificador de cuenta. El colegio necesita un dominio y un proveedor de correo administrado que entregue el buzón. Con Google Workspace, el flujo sería: confirmar el dominio y los permisos del administrador, aprovisionar al usuario con Directory API, aplicar la política de licencias para Gmail, comprobar que el buzón esté disponible y entregar al titular el acceso inicial por un canal individual. La contraseña de correo pertenece al proveedor. AsisControl mantiene su propio acceso salvo que luego se integre el inicio de sesión institucional. [Alta de usuarios de Google Workspace](https://developers.google.com/workspace/admin/directory/v1/guides/manage-users) · [Licencias de Google Workspace](https://knowledge.workspace.google.com/admin/billing/assign-remove-and-reassign-licenses)

Para automatizarlo, conviene una cola de aprovisionamiento que registre el estado, reutilice la misma dirección al reintentar y conserve la referencia del proveedor. Si todavía no hay dominio y permisos, el escenario de Mailpit permite evidenciar una entrega SMTP real en el entorno de prueba, con direcciones ficticias y sin crear buzones externos.

## Usuarios y relaciones propuestos para la base de prueba

| Cuenta ficticia | Relaciones y flujos |
| --- | --- |
| Rosa Acosta, administradora | Configuración de dos años, catálogo, planes, accesos, aprobaciones, vacantes, finanzas, auditoría y avisos. |
| Valeria Ríos, docente y apoderada | Docente de Comunicación en 2.º A y apoderada de Emilia y Mateo. Demuestra selección de rol, evaluación, publicación, corrección, tareas y consulta familiar. |
| Luis Torres, docente | Docente de Matemática en 2.º B. Permite contrastar permisos, horarios y asignaciones entre secciones. |
| Emilia Ríos, estudiante | Matrícula previa válida y promoción acreditada. Tiene asignación, horario, asistencia, evaluación publicada, envío de tarea y pensiones. Caso apto para renovar con vacante. |
| Mateo Ríos, estudiante | Segundo estudiante de Valeria. Tiene un compromiso vencido pendiente y un cargo opcional disponible. Permite mostrar filtro familiar, bloqueo por deuda y cobro adicional. |
| Ana López, apoderada de Camila | Cuenta familiar independiente. Valida que Valeria solo puede acceder a sus dos estudiantes. |
| Camila López, estudiante | Matrícula en 2.º B, evaluación y asistencia propias. Puede cubrir el caso de promoción denegada y contrastar la visibilidad con la familia Ríos. |
| Bruno Díaz y un apoderado de respaldo | Estudiante promovido, al día, con solicitud sobre la última vacante. Aprobar otro trámite primero para demostrar el rechazo por cupo. |
| Julia Soto, apoderada de Nicolás | Nicolás tiene ficha y parentesco, y aún no tiene matrícula anterior. Sirve para mostrar el primer ingreso, la revisión de la solicitud, la asignación de sección y el alta del plan anual. |
| Diego Vargas, usuario con acceso temporal | Ficha y un único perfil habilitado. Útil para entrega de acceso, cambio de contraseña al primer ingreso y restablecimiento con correo de prueba verificado. Una cuenta adicional con acceso desactivado permite probar el mensaje de entrada. |

**Relaciones que conviene preparar**

- Dos años consecutivos y secciones A y B con capacidades distintas. Vincular ámbitos de curso, competencia, aula, docente y período. Preparar un horario válido, un cruce y un bloque cuyo historial se pueda consultar.
- Una sesión de asistencia por clase en borrador y otra finalizada con una corrección. Preparar una marcación general por QR y una falta que el apoderado pueda justificar.
- Una planilla con niveles pendientes y una evaluación ya publicada que admita corrección. Añadir una tarea publicada, una entrega y un caso pendiente de entrega para mostrar la vista docente y la del estudiante.
- Para finanzas, reservar en el mismo año una pensión pendiente para tarjeta, otra obligación para QR, una orden vencida, una rechazada y una constancia aprobada. Dejar también una propuesta opcional sin aceptar y un compromiso administrativo que se pueda anular.
- Usar una dirección verificada de prueba para Emilia y otra para Valeria. Mantener una cuenta ajena a la sección y al vínculo familiar para comprobar que la publicación y la corrección quedan visibles para los destinatarios autorizados.

## Datos cargados para las pruebas locales

El perfil local puede activar los datos con `PAYMENTS_DEMO_ENABLED=true` y `RETRO_JP_DEMO_ENABLED=true`, además del administrador inicial y de una contraseña de prueba en `PAYMENTS_DEMO_PASSWORD`. El arranque muestra los códigos de acceso en el log sin imprimir contraseñas. En la configuración H2 en memoria, un reinicio vuelve a crear los datos ficticios.

| Pantalla | Qué probar |
| --- | --- |
| Académico → Competencias | Usa la plantilla de **Importar Excel** sin editar para probar la fila de ejemplo. Para comprobar un duplicado, agrega una fila con el código existente `JP-COM-2` y un nombre. El catálogo arranca con 21 competencias para probar que **Siguiente** aparezca en la primera página y **Anterior** en la segunda. |
| Académico → Competencias base | En **Reutilizar competencias**, elige **Comunicación · curso anterior** como origen y **Comunicación · curso actual** como destino. Una competencia ya está en la base del destino y quedan dos disponibles para copiar. |
| Académico → Plan de estudios | Selecciona el año vigente y primer grado, entra por **Editar plan** y usa **Importar plan del año anterior**. Agrega los cursos faltantes, ajusta el nombre o las horas y guarda con un motivo. Comprueba el PDF del año vigente, el plan anterior y la operación en **Administración → Auditoría**. |
| Plan de estudios → Competencias del curso | Cambia la selección anual, guarda con motivo y verifica que el plan anterior conserve su propia lista. En el perfil docente, elige el curso y la sección al crear una evaluación o evidencia para ver las competencias de su plan. |
| Matrícula → Renovaciones | Emilia Ramos es elegible. Sofía Ramos no fue promovida. Diego Torres tiene deuda vencida. Bruno Torres tiene como destino una sección llena. Los cuatro expedientes empiezan en revisión. La ficha de Nicolás Ramos permite probar el primer ingreso. |
| Inicio y Pagos del apoderado | Carla Ramos muestra sus indicadores familiares, pensiones pendientes, pagos previos y una **Visita educativa** opcional. Jorge Torres permite comprobar que su cuenta solo ve sus propios estudiantes. |
| Evaluaciones y tareas del docente | Carla Ramos también es docente de Comunicación. Tiene **Comprensión de textos** publicada, **Análisis de una noticia** en borrador, una tarea publicada y una entrega de Mateo. Luis Vega tiene una asignación distinta. |
| Asistencia, Horario y Progreso | Lucía y Mateo tienen clase en el horario, asistencia registrada e informe de Comunicación publicado. Lucía tiene una falta apta para probar una justificación. |

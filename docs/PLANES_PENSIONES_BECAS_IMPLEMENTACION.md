# Plan anual de pensiones, conceptos, tarifas y becas

Ampliación acordada con el Product Owner e implementada a partir del 11 de noviembre de 2026.
Esta guía reúne los cambios de frontend, backend y base de datos para integrarlos juntos.

## Reglas del plan escolar

- Cada matrícula anual tiene **diez pensiones**, de marzo a diciembre, sin prorrateo.
- Cada mensualidad vence el **último día de su mes**. Se considera vencida desde el
  día siguiente, según la fecha de Perú.
- Las diez obligaciones se registran al finalizar la matrícula y quedan visibles
  para sus apoderados asociados. Se pueden pagar una, varias o todas por adelantado.
- Finalizar la matrícula no cobra una tarjeta ni confirma un QR: únicamente registra
  las obligaciones. Se conserva el checkout y su protección de pagos pendientes.
- El precio procede de una **tarifa configurada por año académico y grado**. No se
  incluye un precio escolar predeterminado en el código ni en la migración.
- Cambiar de sección o volver a matricular al mismo estudiante en el mismo año no
  vuelve a generar diez pensiones. La identidad es estudiante, año, concepto y mes.
- Falta de tarifa, datos inválidos o fallo al emitir provocan la reversión de toda
  la finalización: no queda una matrícula creada con un plan parcial.

## Pantallas y uso para administración

En **Finanzas** se mantienen los compromisos y se añaden las herramientas de
configuración, planes y cobros opcionales.

1. **Conceptos:** definir código, nombre, descripción, periodicidad, importe
   sugerido opcional y si requiere aceptación familiar. Es un catálogo general;
   materiales y viajes no son categorías cerradas ni nombres escritos en código.
2. **Tarifas:** elegir año, grado e importe mensual de la pensión. La pensión tiene
   un concepto propio y permanente; su nombre puede personalizarse.
3. **Becas y descuentos:** buscar al estudiante, incluso antes de matricularlo,
   elegir año y concepto, porcentaje o importe fijo, meses de vigencia y motivo.
   Solo un beneficio puede estar vigente para el mismo estudiante, concepto y mes.
4. **Finalizar matrícula:** revisar las diez filas, vencimientos y beneficios. La
   confirmación envía la firma del resumen. Si la tarifa o beca cambió, se exige
   actualizarlo antes de volver a confirmar.
5. **Planes existentes:** revisar las pensiones de una matrícula y generar solo
   las que falten. Los compromisos anteriores sin catálogo se clasifican
   explícitamente antes de generar: mes de pensión u otro concepto. No se
   adivina su identidad por su descripción ni se duplican pagos antiguos.

La tarifa identifica un precio base; cada compromiso conserva una copia del
importe original, el descuento aplicado, el monto final y sus referencias de
origen. Editar la tarifa o beca no cambia silenciosamente obligaciones anteriores.

## Becas y exoneraciones

- Puede asignarse un porcentaje de hasta 100 % o una cantidad fija por obligación.
- Una cantidad fija que supere el importe regular se rechaza; no produce montos
  negativos ni se transforma silenciosamente en otro beneficio.
- El cálculo monetario usa dos decimales y redondeo HALF_UP en el servidor.
- La vigencia mensual permite, por ejemplo, aplicar una beca solo de abril a junio
  y conservar las otras mensualidades con su importe correspondiente.
- Una beca completa genera una obligación **Exonerada**, con importe final cero.
  No se puede seleccionar para pagar ni genera una transacción o comprobante de
  dinero recibido. Se conservan el importe regular y el beneficio aplicado.
- Para modificar obligaciones ya emitidas se utiliza la vista previa de ajuste
  y una aplicación explícita con motivo. Las obligaciones con abonos o anuladas
  se muestran como omitidas. Un pago vigente bloquea el ajuste hasta que termine
  o venza; no se cambian los importes de una orden en curso.
- Una firma del ajuste evita aplicar importes que cambiaron desde su revisión.
  Los pagos anticipados ya realizados no se recalculan. Créditos, devoluciones o
  compensaciones necesitan su propio procedimiento aprobado.

El apoderado ve el monto final y, en el detalle, el importe regular y el beneficio
aplicado. El estado Exonerado se distingue de Pagado y de Anulado.

## Otros conceptos y opciones familiares

Los cobros únicos del catálogo no exigen un número de cuota ficticio. Se emiten
con una referencia de actividad o servicio, descripción, importe y vencimiento.
La referencia identifica la emisión y evita duplicarla para un mismo estudiante,
año y concepto; dos actividades distintas pueden usar un mismo concepto.

Si el concepto **requiere aceptación**, el flujo es:

1. Administración publica la propuesta para una o varias matrículas vigentes.
2. El apoderado la consulta en **Actividades y servicios** y revisa sus condiciones.
3. Puede aceptar o no aceptar. La publicación o el rechazo no crean una obligación.
4. Aceptar crea una única obligación, con los beneficios del concepto que correspondan.
   Luego puede pagarse por los medios ya existentes; una aceptación no realiza un pago.
5. La aceptación tiene versión y cotización firmada. Si cambia el beneficio o el
   importe, se exige revisar y aceptar de nuevo. Dos apoderados que aceptan a la vez
   no generan dos compromisos.

Los importes aceptados quedan guardados. Una propuesta aceptada no se cancela
administrativamente como si no hubiera generado una obligación. La anulación del
compromiso sigue el procedimiento de Finanzas con sus restricciones y motivo.

## Backend y migraciones que debe integrar el equipo

| Parte | Cambio |
|---|---|
| **V13** | Tablas ConceptoCobro, TarifaPension y BeneficioEstudiante; campos de catálogo, mes, referencia y cálculo en CompromisoPago; estado Exonerado. |
| **V14** | Tabla OfertaCobro, destinatario, referencia única, respuesta familiar y compromiso resultante. |
| Matrícula | MatriculaService invoca la generación del plan en la misma transacción. CrearMatriculaRequest admite firmaPlan. |
| Configuración | API institucional `/api/configuracion-pagos` para conceptos, tarifas, beneficios, búsquedas y planes. |
| Opcionales | API institucional de ofertas y `/api/apoderado/ofertas` para consulta, detalle, aceptación y rechazo. |
| Compromisos | API ampliada con idConcepto, referencia y datos del beneficio; conserva registros anteriores y rutas de pago. |
| Frontend | Configuración de Finanzas, revisión del plan al matricular, ajustes explícitos y opciones familiares. |

Aplicar **V13 y después V14**, sobre una base que ya tenga V10–V12. No modificar
los SQL de versiones que ya se aplicaron ni usar create/create-drop sobre datos
del equipo. V13 conserva los importes anteriores y los inicializa como importe
regular sin descuento cuando no existía esa información.

Los importes de pruebas son datos ficticios creados expresamente para comprobar
los flujos. Los ejemplos no se convierten en tarifas del servidor compartido.

### Integración con el backend del equipo

Publicar juntos el frontend, el backend y las migraciones. La confirmación HTTP
de matrícula ahora requiere `firmaPlan`, obtenida al consultar la vista previa
de la solicitud. La generación de un plan existente requiere `{firmaPlan}` y la
aplicación de un beneficio `{motivo, version, firmaAjuste}`. Omitir la firma devuelve
400; una revisión desactualizada devuelve 409 y exige volver a revisar.

Antes de finalizar la primera matrícula de cada año/grado, registrar su tarifa en
**Finanzas → Configuración de pagos → Tarifas por año y grado**. Asignar primero las
becas que deban incluirse al matricular. Una beca registrada después utiliza el
ajuste explícito de obligaciones. Sin tarifa, la matrícula no se finaliza.

Las matrículas anteriores no reciben diez cargos al reiniciar. Se revisan desde
**Plan anual de pensiones** y se clasifican sus compromisos anteriores antes de
confirmar los faltantes. Esta decisión evita duplicar cargos ya registrados.

## Verificación y acceso local

- Backend: `clean verify`, **586 pruebas**, sin fallos, errores ni omisiones.
  Cobertura agregada: 93,62 % de instrucciones y 94,38 % de líneas. Incluye
  concurrencia, aislamiento familiar, firmas desactualizadas, reversión real de
  matrícula, cuotas históricas, redondeo y exoneraciones.
- Frontend: **706 pruebas en 58 archivos**, todas aprobadas. Cobertura global:
  95,07 % de sentencias, 96,91 % de líneas, 90,31 % de ramas y 94,04 % de funciones;
  se mantienen los umbrales existentes. Compilación de producción y TypeScript
  correctos. Lint sin errores; conserva una advertencia previa en una prueba de
  horarios por una variable sin usar.
- Base local: respaldo previo y ejecución de V13 y V14 sobre H2 en modo MySQL.
  Comparación de todas las columnas anteriores: se conservaron íntegros los
  12 compromisos, 7 órdenes, 8 detalles, 11 transacciones y 9 comprobantes previos.
- API local real: **31 comprobaciones aprobadas** en el recorrido final. Se
  configuró una tarifa ficticia de S/ 400, una beca del 25 % de marzo a noviembre
  y una beca completa en diciembre para una estudiante nueva. Al finalizar su
  matrícula se crearon diez obligaciones, por S/ 2 700 en total.
- Se verificó la selección anticipada de las nueve pensiones con importe,
  cálculo del total en el servidor y bloqueo de otra operación concurrente.
  El rechazo conservó las pensiones pendientes. Un cobro opcional aceptado
  generó una sola obligación sin cuota y pudo pagarse y descargar su constancia.
- Revisión visual: tarifas, plan completo, estados financieros, detalle de beca
  parcial, exoneración sin selección de pago y opción familiar aceptada.
  La reactivación de conceptos, tarifas y beneficios se identifica explícitamente
  antes de guardar; un plan ya completo deshabilita la generación de faltantes.

La aplicación local se consulta en **http://localhost:3001** y usa el backend del
puerto 8081 con base persistente. En esta copia, buscar a **Valeria Rojas Suárez**
en el plan anual o con su apoderado local permite revisar el ejemplo anterior.
Los scripts y credenciales de QA están fuera del repositorio; no forman parte
de la configuración ni de las tarifas del servidor del equipo.

Las migraciones se comprobaron con H2 en modo MySQL, además de las pruebas de
integración. No se ejecutó esta ampliación contra una instancia MySQL/RDS real.
La pasarela conserva su funcionamiento simulado y las constancias internas
existentes; esta ampliación no incorpora un proveedor bancario ni emisión SUNAT.

Estas comprobaciones corresponden al entorno local. Integrar esta ampliación
mediante un pull request hacia `develop`; el despliegue del servidor se realiza
por separado, junto con las migraciones y la configuración institucional.

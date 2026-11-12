# US-13 y US-48 — pagos y consulta del apoderado

Guía para integrar frontend, backend y base de datos en la versión del equipo.
Implementación y comprobaciones locales terminadas el 8 de noviembre de 2026.
La actualización posterior de importes, estados, códigos, historial completo y
constancias se detalla en [US13_US48_REFINAMIENTO.md](US13_US48_REFINAMIENTO.md).
El plan automático de diez mensualidades, tarifas por año y grado, becas y cobros
opcionales se documenta en [PLANES_PENSIONES_BECAS_IMPLEMENTACION.md](PLANES_PENSIONES_BECAS_IMPLEMENTACION.md).

## Decisiones sobre la propuesta

- Se reutilizan **Persona, Apoderado, Alumno, AlumnoApoderado, Matricula,
  CompromisoPago, TransaccionPago y Comprobante**. No se crean tablas paralelas
  llamadas estudiantes u obligaciones con los mismos datos.
- US-12 sigue emitiendo las obligaciones. US-48 permite al apoderado consultarlas,
  revisar movimientos y comprobantes de sus estudiantes asociados.
- US-13 usa una **pasarela local simulada**. Se pueden reproducir aprobación,
  fondos insuficientes, rechazo y un desafío de autenticación de prueba. No hay
  llamadas a Stripe ni movimientos de dinero real.
- El QR es un código escaneable que abre una **billetera de demostración propia**,
  no una cuenta de Yape, Plin ni de un banco. La pantalla principal consulta el
  estado cada dos segundos y recibe el resultado de la confirmación móvil.
- El comprobante es **interno, no tributario**. Puede consultarse y descargarse
  en HTML; no constituye boleta electrónica ni una integración SUNAT.
- Se paga el saldo restante completo de las obligaciones seleccionadas. Se pueden
  terminar de cancelar obligaciones con pagos parciales previos. No se añade un
  acuerdo nuevo de fraccionamiento manual durante el checkout.
- La mora permanece en **S/ 0.00**: no se inventó una tasa o penalidad para un
  colegio sin una política aprobada. Configurar un recargo requiere acordar sus
  condiciones, fórmula, vigencia y registro contable; no basta cambiar una cifra.

La documentación oficial de [Stripe sobre pruebas](https://docs.stripe.com/testing)
recomienda utilizar identificadores de métodos de pago en código, evitando enviar
números de tarjeta al servidor. Aquí se seleccionan escenarios y se muestran solo
referencias enmascaradas: no se solicitan PAN, CVV ni vencimientos reales.
Una futura integración externa necesitará otro adaptador y verificación de firmas
según [los webhooks de Stripe](https://docs.stripe.com/webhooks).

La decisión de mantener mora cero también evita asumir condiciones económicas
que la institución no ha acordado. [Indecopi](https://consumidor.gob.pe/2025/01/23/los-colegios-privados-pueden-incrementar-las-pensiones-de-los-alumnos/)
explica que esas condiciones deben informarse previamente a las familias.

## Cambios visibles en el frontend

1. El apoderado tiene una pantalla funcional en **Pagos**.
2. Consulta obligaciones filtradas por estudiante asociado y estado; las anuladas
   y pagadas se conservan como historial y no son seleccionables para pagar.
3. Selecciona una o varias cuotas y revisa su total en soles.
4. El checkout permite elegir tarjeta simulada o QR dinámico con vigencia.
5. En tarjeta, se puede demostrar aprobación, fondos insuficientes, rechazo y
   autenticación adicional, completando o cancelando el desafío.
6. En QR, se puede escanear desde otro dispositivo o abrir el simulador en la
   misma computadora. Confirmar o rechazar actualiza el resultado en la pantalla
   del apoderado sin recargar toda la página.
7. El detalle de una obligación muestra sus transacciones y comprobantes; las
   órdenes guardan el historial de intentos y muestran su comprobante al aprobarse.

Las pantallas móviles de `/pagar-qr/{token}` están **fuera del layout que exige
iniciar sesión**. El token firmado permite consultar y confirmar únicamente esa
orden durante su vigencia. No habilita acceso a la cuenta ni al historial familiar.

## Cambios que deben conocer quienes integran el backend

### Identidad y autorización

- La identidad del apoderado se obtiene de la sesión/JWT validado, nunca de un
  `apoderadoId` recibido libremente desde el navegador.
- Las lecturas cruzan los compromisos con la matrícula, el estudiante y las
  asociaciones familiares activas. Filtrar por otro estudiante no amplía el acceso.
- Antes de iniciar y confirmar un pago se revisan la asociación vigente, el
  estado administrativo, la moneda y el saldo de todas las obligaciones del lote.
- La selección de una obligación ajena rechaza la operación completa. Un estado
  de pago mostrado en el frontend no autoriza por sí mismo una operación.
- La vía anterior que permitía a un apoderado registrar directamente una
  transacción aprobada en `POST /api/transacciones-pago` se cierra para ese rol.
  La operación manual institucional permanece disponible para roles autorizados.
  **Si otro frontend del equipo usa esa vía como apoderado, debe migrar al checkout.**

### Integridad del dinero y reintentos

- El navegador envía IDs de obligaciones y método; el total se calcula con los
  saldos actuales de la base. Cambiar un importe en el navegador no reduce el pago.
- La orden conserva una instantánea de sus cuotas e importes y crea transacciones
  pendientes; iniciar o mostrar un QR no disminuye los saldos.
- La confirmación bloquea los registros, revalida los importes y aplica el lote
  completo en una transacción de base de datos.
- Si otro pago o una modificación cambió el saldo desde el inicio, esta orden no
  aplica un cobro parcial silencioso: se rechaza y se debe revisar la nueva situación.
- Un `Idempotency-Key` estable protege los reintentos de creación. Repetir una
  confirmación de una orden ya completada devuelve el resultado anterior y no
  duplica pagos ni comprobantes.
- Rechazos y expiraciones quedan registrados sin reducir el saldo.
- La aprobación actualiza las entidades existentes: por eso el administrador ve
  también los saldos y estados resultantes desde Finanzas.

### QR y comprobantes

- La URL usa una firma HMAC acotada a la orden y una vigencia de cinco minutos.
  Se reutiliza la clave estable del servidor con un dominio específico para QR;
  no se almacenan tokens QR en claro ni se registran en auditoría.
- No hay un webhook bancario público que apruebe arbitrariamente por ID. La ruta
  pública exige el token firmado y solo corresponde al simulador QR.
- Después de cinco minutos, la respuesta pública conserva únicamente información
  de estado: retira los nombres, cuotas e importes, incluso si el pago ya terminó.
  La cuenta autenticada del apoderado mantiene su historial y comprobante.
- Se emite un `Comprobante` interno por `TransaccionPago` aprobada, conservando el
  modelo del proyecto. La pantalla ofrece una vista consolidada de la orden.
- El HTML de descarga escapa los valores de presentación. La descarga y la
  consulta del comprobante de la orden requieren autorización.

El contrato de rutas y JSON se detalla en
[US13_US48_CONTRATO.md](US13_US48_CONTRATO.md).

## Base de datos y migraciones

- Conservar todas las migraciones existentes. **V10**, añadida en la mejora previa
  de US-12, mantiene el correlativo corto de los compromisos y debe integrarse
  también si todavía no está en la rama del equipo.
- La nueva **V11** incorpora las órdenes de pago y sus detalles, con relaciones a
  los compromisos y transacciones existentes, índices y restricciones de unicidad.
- Los medios simulados se distinguen de los medios bancarios reales. La migración
  adapta `TransaccionPago.medio_pago` a `VARCHAR(40)` para admitir los nuevos valores
  sin perder las transacciones ya registradas. Esto es relevante porque V1 usaba ENUM.
- No se renumeran compromisos ni se reemplazan IDs, asociaciones o comprobantes
  anteriores. Las órdenes nuevas complementan el modelo de US-12.
- En MySQL se aplica Flyway al iniciar la nueva versión. Hacer primero un respaldo
  y revisar el resultado en una copia de la base del equipo. No sustituir la base
  ni usar `create`/`create-drop` sobre sus datos persistentes.
- El perfil QA local de esta carpeta usa H2 persistente con `ddl-auto=update` y
  Flyway desactivado. Es un entorno de prueba distinto de MySQL/RDS.

En la base H2 persistente preexistente fue necesario aplicar explícitamente la
adaptación del ENUM, con el backend detenido y respaldo previo: `ddl-auto=update`
no había convertido esa columna. Es la primera instrucción de V11:

```sql
ALTER TABLE TransaccionPago MODIFY COLUMN medio_pago VARCHAR(40) NOT NULL;
```

En MySQL este cambio lo ejecuta Flyway como parte de V11; no debe omitirse al
integrar el backend. No se reemplazaron los valores ni las filas anteriores.

## Semillas de demostración

`PagosDemoDataInitializer` solo funciona con el perfil **local** y la opción
explícita `PAYMENTS_DEMO_ENABLED=true`. No se habilita por defecto ni se ejecuta
con el perfil `mysql` del despliegue.

- `demo.pagos.1`: dos estudiantes asociados.
- `demo.pagos.2`: un estudiante diferente, sin acceso a los dos anteriores.
- Cada estudiante tiene tres obligaciones ficticias: una pagada con comprobante,
  una vencida y una pendiente. Los importes de ejemplo son S/ 350.00.
- Se requiere `PAYMENTS_DEMO_PASSWORD` mediante el entorno. No hay una contraseña
  predeterminada en el código ni se escriben contraseñas en logs.
- Al reiniciar, las cuentas existentes y sus pagos se conservan; la semilla no
  restablece contraseñas ni vuelve a crear obligaciones ya preparadas.
- Si un documento ficticio ya está ocupado, se elige el siguiente libre. La
  semilla nunca toma ni modifica una persona existente para crear una familia demo.
- Los datos incluyen claramente «DEMO» o «Demostración». No corresponden a familias
  reales ni al respaldo del equipo.

Ejemplo para una terminal local de pruebas, usando una contraseña propia:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
$env:PAYMENTS_DEMO_ENABLED = "true"
$env:PAYMENTS_DEMO_PASSWORD = "<contraseña privada de 12 a 64 caracteres>"
```

La contraseña debe tener mayúsculas, minúsculas y números, sin espacios. Debe
mantenerse fuera de Git. Para conservar el avance al reiniciar hay que usar una
base local persistente; el perfil H2 en memoria del proyecto empieza vacío.

## Uso del QR desde un celular en la misma red

Para el simulador de la misma pantalla se puede usar localhost. **Desde un celular,
localhost identifica al propio celular**, por lo que debe utilizarse la IP de red
de la computadora o la dirección pública de la aplicación.

1. Conectar ambos dispositivos a la misma Wi-Fi y obtener la IPv4 de la computadora
   con `ipconfig`. Ejemplo: `192.168.1.50`.
2. Backend, para una prueba local de red:

   ```powershell
   $env:SERVER_ADDRESS = "0.0.0.0"
   $env:SERVER_PORT = "8080"
   $env:FRONTEND_URL = "http://192.168.1.50:3000"
   $env:CORS_ALLOWED_ORIGINS = "http://192.168.1.50:3000"
   $env:AUTH_COOKIE_SECURE = "false"
   ```

3. Frontend `.env.local`, usando la IP y puerto reales del backend:

   ```dotenv
   NEXT_PUBLIC_API_BASE_URL=http://192.168.1.50:8080
   ```

4. Iniciar el frontend desde su carpeta:

   ```powershell
   npm.cmd run dev -- --hostname 0.0.0.0 --port 3000
   ```

5. Abrir `http://192.168.1.50:3000` desde la computadora, iniciar sesión como
   apoderado y crear un pago QR. La URL del QR debe comenzar con esa misma dirección.
6. Escanear con el celular y confirmar o rechazar en la billetera de demostración.
   Si el sistema solicita permiso de firewall, habilitar únicamente los puertos
   necesarios para esta prueba en la red privada.

Si se utilizan los puertos QA 3001/8081, sustituirlos en todos los valores. Un
script que pasa `--server.address=127.0.0.1` o `--app.frontend-url=...` tiene prioridad
sobre las variables: adaptar esos argumentos o iniciar otra instancia con la
configuración de red; no basta editar `.env`.

En el despliegue se debe usar la URL pública real, y HTTPS para cookies seguras.
Las variables públicas de Next se incorporan al compilar: un cambio de IP o URL
puede requerir reconstruir el frontend.

## Stripe y variables

Esta entrega funciona sin cuenta Stripe ni claves `pk_test_...` o `sk_test_...`.
El identificador `pi_sim_...` pertenece al simulador local y no existe en Stripe.
El desafío 3DS es una representación de prueba, no una autenticación bancaria real.

`STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY` y `STRIPE_WEBHOOK_SECRET` se documentan
como requisitos de un adaptador externo futuro; asignarles valores no activa una
integración real en esta versión. Nunca publicar una clave secreta como
`NEXT_PUBLIC_STRIPE_SECRET_KEY`.

## Orden recomendado para integrar en develop y main

1. Revisar juntos este documento y el contrato de API con quienes mantienen el backend.
2. Integrar los cambios sobre una rama de trabajo que incluya la última versión
   acordada de develop. Conservar los cambios de US-12 y el correlativo V10.
3. Incorporar producción, migraciones, pruebas, configuración y frontend en la misma
   entrega. Un frontend nuevo con un backend anterior no tendrá las rutas de pagos.
4. Ejecutar las pruebas y compilaciones descritas en la sección de verificación.
5. Respaldar y comprobar las migraciones en una copia de MySQL antes del despliegue.
6. Configurar URL pública, CORS y secretos del entorno; mantener las semillas demo
   desactivadas en el servidor compartido.
7. Abrir el Pull Request hacia develop detallando V10/V11/V12 y el cierre de la ruta
   manual del apoderado. La incorporación a main sigue el flujo de revisión del equipo.

## Archivos que no deben quedar fuera de la integración

| Parte | Archivos principales y finalidad |
|---|---|
| Modelo y persistencia | `backend/entity/.../OrdenPago.java`, `OrdenPagoDetalle.java`, estados y método simulado; repositorios correspondientes en `backend/repository`. |
| Contrato HTTP | `backend/dto/.../PagosFamiliaDto.java`; controladores `ObligacionesApoderadoController`, `PagosFamiliaController` y `PagoQrController`. |
| Reglas del pago | `ConsultaObligacionesService`, `PagosFamiliaService` y `TokenQrPagoService` en `backend/service`. |
| Seguridad existente | `SecurityConfig`, controladores de transacciones y comprobantes, guards y consultas financieras, y tratamiento seguro de rutas con token en errores/autenticación. |
| SQL | `V10__correlativo_codigo_compromiso.sql` si falta en main, y `V11__pagos_familia_simulados.sql`. |
| Demo opcional | `PagosDemoDataInitializer.java` y sus variables; solo para perfil local. |
| Pantallas | Todo `frontend/src/modules/guardian-payments`, ruta `frontend/src/app/pagar-qr/[token]` e integración en navegación/permisos. |
| Utilidades compartidas | El cliente API no cierra una sesión al recibir 401 de una petición pública; Finanzas expone sus tipos y formatos por su API pública. |
| Configuración | `application.properties`, ejemplos de entorno del backend/frontend/raíz/despliegue y variables de los dos archivos Compose. No copiar secretos reales. |
| Pruebas | Clases `PagoApoderado...`, `OrdenPagoMigracionTest`, `PagosDemoDataInitializerIntegrationTest` y pruebas de pagos, billetera, contratos y navegación del frontend. |

## Reproducir las comprobaciones

Con las dependencias y el JDK del proyecto disponibles:

```powershell
# Desde backend; detener primero un proceso local que mantenga abierto el JAR target.
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify

# Desde frontend.
npm.cmd run test:coverage
npm.cmd run lint
npm.cmd run build
```

Las pruebas comprueban identidades distintas, filtros por asociación, rechazo de
IDs ajenos, importes calculados en servidor, aprobación/rechazo/3DS, vencimiento,
privacidad del QR, comprobantes, reintentos y carreras simultáneas. No basta con
comprobar que se muestra una pantalla de éxito.

## Verificación de esta entrega

Resultados ejecutados el **8 de noviembre de 2026**:

| Comprobación | Resultado |
|---|---|
| Backend | 527 pruebas en 79 reportes finales, sin fallos, errores ni omisiones. Se ejecutó la suite completa y, después de las correcciones, se repitieron las regresiones afectadas; el recuento corresponde al conjunto de reportes finales, no a una única ejecución de 527 casos. |
| Compilación y cobertura del backend | Empaquetado correcto. Cobertura: instrucciones 93.65 %, líneas 94.23 %, ramas 77.38 %. Supera el control existente de instrucciones y líneas, sin bajar sus umbrales. |
| Frontend | 655 pruebas aprobadas en 52 archivos. Cobertura: instrucciones 95.14 %, líneas 96.79 %, ramas 90.15 % y funciones 94.01 %. Se conservaron los umbrales del proyecto. |
| Compilación del frontend | `npm.cmd run build` correcto, incluida la ruta pública `/pagar-qr/[token]`. |
| Revisión de estilo del frontend | Sin errores. Permanece una advertencia previa por una variable no utilizada en `schedules/tests/timetable.test.jsx:580`. |
| API y base local reales | 14 comprobaciones aprobadas: dos familias aisladas, consultas ajenas rechazadas, lote mezclado rechazado, total calculado en servidor, reintentos, QR sin sesión, rechazo sin reducir saldo, autenticación adicional y comprobantes. |
| Interfaz: tarjeta | Pago conjunto de S/ 700.00 correspondiente a dos estudiantes, con desafío de autenticación simulado, saldos actualizados y descarga del comprobante HTML. |
| Interfaz: QR | Orden por S/ 350.00 abierta en la billetera pública, revisión a 390 × 844 y confirmación de prueba. La pantalla del apoderado detectó la aprobación automáticamente y mostró el comprobante sin recargar. |
| Datos anteriores | Los tres compromisos previos conservaron sus IDs, códigos, montos, saldos, estados y versiones. Se añadieron nueve compromisos ficticios de demostración. |

La migración V11 se comprobó en H2 con modo MySQL, incluidos datos anteriores,
relaciones y restricciones. **No se ejecutó contra una instancia real de MySQL/RDS**;
esa comprobación sigue siendo necesaria en una copia de la base del equipo antes
de integrar y desplegar. Tampoco se probó un escaneo con un teléfono físico: se
verificaron el QR, la apertura de su URL, el diseño móvil y la confirmación en otra
pestaña. Los pasos de red anteriores permiten hacer esa prueba con el celular.

### Acceso a la demostración preparada en esta computadora

- Frontend: **http://localhost:3001/pagos**.
- Backend: **http://localhost:8081**.
- Accesos de las dos familias: `qa-us12-evidencias/ACCESO_PAGOS_LOCAL.txt`, en la
  carpeta de trabajo que contiene el repositorio. Este archivo privado está fuera
  de Git y no debe adjuntarse al Pull Request.
- Para reiniciar el entorno preparado, los iniciadores locales están en esa misma
  carpeta: `iniciar-backend-local.ps1` e `iniciar-frontend-local.ps1`.
- Las capturas y el resumen de las 14 comprobaciones están en
  `outputs/us13-us48`, también fuera del repositorio.

Las pruebas realizadas **se conservaron en la base local**. Después de la primera
verificación, el usuario también pagó la cuota 3 de Mateo (`CP-000007`); ambas
familias tienen ya todas sus cuotas de ejemplo pagadas. Para
repetir más pagos, emitir nuevos compromisos ficticios desde US-12 para esos
estudiantes. Reiniciar la aplicación no borra pagos ni vuelve a dejar sus saldos
pendientes.

La entrega se publica mediante una rama de trabajo y un Pull Request hacia
**develop**. La validación descrita corresponde al entorno local; no implica un
despliegue en el servidor del equipo.

# Publicar AsisControl en EC2

## 1. Que prepara este cambio

La rama se crea desde develop. No cambia controllers, services, entidades, frontend ni migraciones. Usa el RDS existente; no crea MySQL en Docker.

En cada push a develop o main, `publish.yml` llama primero al CI completo y solo publica si pasa. `ci.yml` no se dispara ademas por esos pushes, para no duplicar las pruebas. Los PR siguen ejecutando CI sin permisos de publicacion.

`publish.yml` publica backend y frontend en GHCR usando el GITHUB_TOKEN temporal del workflow. No necesita claves AWS ni contrasenas de RDS. Cada imagen se etiqueta con el SHA completo del commit; no se usa latest. Si una publicacion falla, no despliegues ese SHA hasta que ambas esten disponibles.

`deploy/compose.yaml` descarga esas imagenes en EC2 sin compilar. Solo Caddy publica 80/443; backend y frontend se comunican por la red interna Docker. Los limites iniciales de memoria estan pensados para probar t3.small y deben ajustarse segun consumo real; no garantizan capacidad bajo carga.

`Caddyfile` conserva /api al enviar peticiones a Spring Boot y dirige el resto a Next.js. Con la IPv4 publica configurada, Caddy solicita a Let's Encrypt certificados de IP con el perfil shortlived y los renueva automaticamente. Los certificados y archivos adjuntos se conservan en volumenes. /healthz devuelve la salud del backend sin detalles, no verifica login ni todas las funcionalidades.

El despliegue a EC2 es manual por SSH mediante deploy.sh en esta primera etapa. No se instala un runner en EC2, no se abre SSH a todos los runners de GitHub y no hay despliegue automatico remoto todavia. El script no confirma salud automaticamente ni hace rollback: hay comandos explicitos mas abajo.

## 2. HTTPS con IP publica, sin dominio

Usar la IPv4 elastica actual de EC2. Abrir TCP 80 y 443 desde Internet en el grupo de seguridad; mantener SSH 22 restringido a administracion. El servidor debe tener salida a Internet para contactar a Let's Encrypt.

Configurar PUBLIC_APP_URL en GitHub como `https://IP_PUBLICA_EC2`, sin barra final ni puerto. En deploy/.env configurar `APP_URL=https://IP_PUBLICA_EC2` y `APP_HOST=IP_PUBLICA_EC2` (solo la IP). Compose fuerza AUTH_COOKIE_SECURE=true. No se necesitan claves privadas de certificado manuales ni cambios en MySQL/S3.

Caddy 2.11.9 usa el perfil ACME shortlived para certificados publicos de IP. Estos duran unas 160 horas y se renuevan automaticamente. Conservar el volumen certificados. Si EC2 permanece apagado hasta vencer, HTTPS puede fallar al encender mientras Caddy obtiene otro certificado. Una IP distinta requiere actualizar ambas variables, PUBLIC_APP_URL y reconstruir el frontend.

Para migrar el despliegue HTTP existente:

1. Abrir 80/443 y confirmar la IP elastica en EC2.
2. Cambiar PUBLIC_APP_URL a HTTPS ANTES de ejecutar el workflow del nuevo commit. Integrar los cambios y esperar pruebas y publicacion de ambas imagenes.
3. En EC2 guardar el .env actual en un respaldo privado; no sustituirlo por .env.example.
4. Traer el commit publicado con git fetch origin y git checkout SHA_COMPLETO_PUBLICADO; entrar a deploy.
5. Editar .env: cambiar APP_URL, agregar APP_HOST y actualizar IMAGE_TAG al mismo SHA. Conservar las demas variables y secretos. La antigua AUTH_COOKIE_SECURE puede eliminarse: Compose fija true.
6. Ejecutar sudo bash deploy.sh. Valida Caddy antes de reemplazar los servicios. Esta validacion comprueba configuracion, no la emision real del certificado.
7. Revisar logs del proxy y backend, probar HTTPS sin omitir validacion de certificados, login y cierre de sesion. HTTP debe redirigir a HTTPS.

No basta con cambiar el .env: el frontend incorpora PUBLIC_APP_URL durante la construccion. No volver a HTTP dejando las cookies seguras, porque el login dejaria de funcionar. Un retorno temporal requiere restaurar conjuntamente los archivos de despliegue anteriores, su configuracion HTTP y las imagenes correspondientes, comprobando compatibilidad de migraciones.

Fuentes: https://letsencrypt.org/2026/01/15/6day-and-ip-general-availability y https://caddyserver.com/docs/caddyfile/directives/tls .
## 3. GitHub y publicacion

1. Revisar, commitear, subir esta rama y crear PR a develop. Integrar segun el flujo del equipo; main sigue siendo la version estable.
2. En Settings > Secrets and variables > Actions > Variables, crear PUBLIC_APP_URL con `https://IP_PUBLICA_EC2`, sin puerto, barra final, rutas ni claves. Es una variable publica.
3. Al fusionar el PR en develop, su push ejecuta automaticamente Publicar imagenes de despliegue: no requiere Run workflow ni integrar en main. Buscar la ejecucion en Actions > All workflows. Si falta el boton manual, el disparador por push funciona igualmente. El equipo puede validar y publicar desde develop; main tambien publica cuando el equipo decida integrarlo. No hacer push directo para saltarse revisiones.
4. Esperar a que las pruebas y ambas publicaciones terminen en verde. Copiar el SHA de 40 caracteres y los nombres indicados en el resumen.
5. Los paquetes son `ghcr.io/asiscontrol/asiscontrol-backend:SHA` y `ghcr.io/asiscontrol/asiscontrol-frontend:SHA` si el repositorio conserva el nombre AsisControl/AsisControl.

La organizacion debe permitir GitHub Packages y que el workflow publique. Los paquetes pueden ser privados: no asumir acceso anonimo. PUBLIC_APP_URL se incorpora al frontend durante el build; cambiar el dominio requiere reconstruir/publicar y actualizar APP_URL. Nunca poner localhost, el endpoint RDS o el nombre Docker backend como URL publica.

## 4. Preparar archivos en EC2

Docker y Compose ya fueron instalados. En EC2 obtener la version aprobada del repositorio (para repositorio privado configurar acceso de solo lectura, por ejemplo una deploy key; no introducir tokens en la URL):

```bash
git clone https://github.com/AsisControl/AsisControl.git
cd AsisControl
git checkout SHA_DEL_COMMIT_PUBLICADO
cd deploy
umask 077
cp .env.example .env
nano .env
```

Completar APP_URL, APP_HOST, IMAGE_TAG y las credenciales. DB_URL apunta al endpoint RDS completo y esquema `asiscontrol`. El usuario ideal es exclusivo de la aplicacion con los permisos acordados para sus migraciones; no cambiar el admin compartido. Para una primera prueba solo usar credenciales autorizadas por el equipo. Generar JWT_SECRET con `openssl rand -hex 32` y guardarlo en .env; no compartir su valor ni rotarlo en cada reinicio. Esto invalida tokens existentes si se cambia.

Los valores con $ o # se escriben entre comillas simples; no ejecutar `source .env`. No imprimir `docker compose config` sin --quiet: mostraria secretos. No subir .env, claves SSH ni credenciales a GitHub.

## 5. Autenticar descargas privadas de GHCR

Para paquetes publicos no hace falta login. Para privados usar un token personal classic con read:packages, acceso al paquete y autorizacion SSO si la organizacion la exige. No usar un token con permisos de escritura para EC2. El GITHUB_TOKEN de Actions no se copia al servidor.

```bash
read -rp 'Usuario de GitHub: ' GHCR_USER
read -rsp 'Token de lectura de paquetes: ' GHCR_READ_TOKEN; echo
printf '%s' "$GHCR_READ_TOKEN" | sudo docker login ghcr.io -u "$GHCR_USER" --password-stdin
unset GHCR_READ_TOKEN
```

Docker guarda la autenticacion en /root/.docker/config.json al usar sudo; restringir el acceso al servidor. El login se hace con sudo igual que los pulls siguientes. Si se decide hacer publicos los paquetes, coordinarlo con el equipo antes.

## 6. Revisar la base y arrancar

El RDS revisado tiene baseline V1 y V2/V3 exitosas. Antes de iniciar comparar migraciones del commit elegido, coordinar el uso compartido y disponer de un respaldo recuperable (snapshot RDS o respaldo aprobado). El backend puede aplicar migraciones pendientes al arrancar. No borrar datos ni editar flyway_schema_history. Se desactiva crear baselines automaticamente: un esquema sin historial requerira revision explicita.

Despues del respaldo y la revision:

```bash
sudo bash deploy.sh
sudo docker compose --env-file .env -f compose.yaml logs --tail 100 backend
sudo docker compose --env-file .env -f compose.yaml logs --tail 100 proxy
curl --fail --retry 20 --retry-delay 5 --retry-all-errors https://IP_PUBLICA_EC2/healthz
curl --fail --head https://IP_PUBLICA_EC2
```

Puede tardar el arranque y la emision del certificado. No usar curl -k: ocultaria errores de certificado. Esperar salud UP y probar desde un navegador login, cierre de sesion, permisos y las funciones que use la demostracion. No se crea administrador automaticamente: usar un acceso existente acordado con el equipo. Correo SMTP esta desactivado; si se necesitan correos reales falta configurar el modulo. No requiere instalar Java ni Node directamente en EC2.

## 7. Operacion y vuelta a una version anterior

```bash
sudo docker compose --env-file .env -f compose.yaml ps
sudo docker stats --no-stream
```

Para actualizar elegir otro SHA publicado/aprobado, actualizar archivos deploy desde ese mismo commit y cambiar IMAGE_TAG en .env sin perder secretos. Antes guardar el SHA previo y revisar migraciones. Ejecutar sudo bash deploy.sh otra vez y comprobar salud/login. No usar git reset --hard sobre configuraciones privadas.

Si falla la aplicacion, volver al SHA anterior en archivos e IMAGE_TAG y repetir el despliegue SOLO si el esquema sigue siendo compatible. Volver una imagen no revierte migraciones: una base incompatible requiere el plan de recuperacion acordado. No hay rollback automatico.

No usar `docker compose down -v`: elimina volumenes con certificados y adjuntos. Los volumenes no son un respaldo; falta configurar copias externas. El almacenamiento S3 es optativo: ver [configuración, permisos y activación](../backend/docs/ALMACENAMIENTO_S3.md). Mantener el volumen `archivos` incluso al activar S3, para leer adjuntos anteriores. No detener/eliminar el RDS compartido para ahorrar sin coordinar con el equipo. Revisar credito y ciclo de vida de AWS Academy.

## 8. Limites actuales

No se desplego nada desde esta computadora ni se modifico RDS. Falta confirmar la IP publica actual, publicar la rama revisada, configurar la variable, publicar imagenes y arrancarlas en EC2. La configuracion HTTPS de IP esta preparada; falta verificar la emision real y el acceso en EC2. Despues se puede automatizar el paso SSH mediante un mecanismo autorizado (por ejemplo SSM con permisos del laboratorio); requerira una propuesta adicional y no almacenar la llave del equipo sin acordarlo.

Las pruebas de CI usan H2 y respuestas simuladas/componentes; no certifican compatibilidad de migraciones MySQL ni el despliegue real. Revisar tambien las vulnerabilidades de dependencias reportadas y restringir con el equipo la regla MySQL abierta a 0.0.0.0/0, conservando los accesos necesarios.

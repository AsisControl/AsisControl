# Publicar AsisControl en EC2

## 1. Que prepara este cambio

La rama se crea desde develop. No cambia controllers, services, entidades, frontend ni migraciones. Usa el RDS existente; no crea MySQL en Docker.

En cada push a develop o main, `publish.yml` llama primero al CI completo y solo publica si pasa. `ci.yml` no se dispara ademas por esos pushes, para no duplicar las pruebas. Los PR siguen ejecutando CI sin permisos de publicacion.

`publish.yml` publica backend y frontend en GHCR usando el GITHUB_TOKEN temporal del workflow. No necesita claves AWS ni contrasenas de RDS. Cada imagen se etiqueta con el SHA completo del commit; no se usa latest. Si una publicacion falla, no despliegues ese SHA hasta que ambas esten disponibles.

`deploy/compose.yaml` descarga esas imagenes en EC2 sin compilar. Solo Caddy publica 80/443; backend y frontend se comunican por la red interna Docker. Los limites iniciales de memoria estan pensados para probar t3.small y deben ajustarse segun consumo real; no garantizan capacidad bajo carga.

`Caddyfile` conserva /api al enviar peticiones a Spring Boot y dirige el resto a Next.js. Con dominio y DNS configurados, Caddy obtiene y renueva certificados. Los certificados y archivos adjuntos se conservan en volumenes. /healthz devuelve la salud del backend sin detalles, no verifica login ni todas las funcionalidades.

El despliegue a EC2 es manual por SSH mediante deploy.sh en esta primera etapa. No se instala un runner en EC2, no se abre SSH a todos los runners de GitHub y no hay despliegue automatico remoto todavia. El script no confirma salud automaticamente ni hace rollback: hay comandos explicitos mas abajo.

## 2. Acceso temporal por IP y cambio posterior a dominio

Para la demostracion se permite APP_URL=http://IP_PUBLICA_EC2 y AUTH_COOKIE_SECURE=false. Abrir 80 desde Internet y mantener SSH 22 solo desde IPs de administracion. HTTP no cifra credenciales: usar cuentas y datos de prueba. No hay HTTPS activo en este modo.

Despues, con un dominio controlado por el equipo, crear un registro DNS A hacia EC2 y abrir 80/443. Cambiar PUBLIC_APP_URL en GitHub a https://TU_DOMINIO y volver a publicar las imagenes; en EC2 cambiar APP_URL a la misma direccion y AUTH_COOKIE_SECURE=true. Actualizar IMAGE_TAG y volver a ejecutar deploy.sh. Caddy obtendra el certificado. Si cambia la IP publica tras detener EC2, actualizar configuracion y reconstruir el frontend (o DNS cuando haya dominio).

## 3. GitHub y publicacion

1. Revisar, commitear, subir esta rama y crear PR a develop. Integrar segun el flujo del equipo; main sigue siendo la version estable.
2. En Settings > Secrets and variables > Actions > Variables, crear PUBLIC_APP_URL con `http://IP_PUBLICA_EC2` por ahora, sin barra final, rutas ni claves; despues usar `https://TU_DOMINIO`. Es una variable publica.
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

Completar APP_URL, AUTH_COOKIE_SECURE, IMAGE_TAG y las credenciales. DB_URL apunta al endpoint RDS completo y esquema `asiscontrol`. El usuario ideal es exclusivo de la aplicacion con los permisos acordados para sus migraciones; no cambiar el admin compartido. Para una primera prueba solo usar credenciales autorizadas por el equipo. Generar JWT_SECRET con `openssl rand -hex 32` y guardarlo en .env; no compartir su valor ni rotarlo en cada reinicio. Esto invalida tokens existentes si se cambia.

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
curl --fail --retry 20 --retry-delay 5 --retry-all-errors http://IP_PUBLICA_EC2/healthz
curl --fail --head http://IP_PUBLICA_EC2
```

Puede tardar el arranque y la emision del certificado. En HTTP no hay emision de certificados. Esperar salud UP y probar desde un navegador login, cierre de sesion, permisos y las funciones que use la demostracion. No se crea administrador automaticamente: usar un acceso existente acordado con el equipo. Correo SMTP esta desactivado; si se necesitan correos reales falta configurar el modulo. No requiere instalar Java ni Node directamente en EC2.

## 7. Operacion y vuelta a una version anterior

```bash
sudo docker compose --env-file .env -f compose.yaml ps
sudo docker stats --no-stream
```

Para actualizar elegir otro SHA publicado/aprobado, actualizar archivos deploy desde ese mismo commit y cambiar IMAGE_TAG en .env sin perder secretos. Antes guardar el SHA previo y revisar migraciones. Ejecutar sudo bash deploy.sh otra vez y comprobar salud/login. No usar git reset --hard sobre configuraciones privadas.

Si falla la aplicacion, volver al SHA anterior en archivos e IMAGE_TAG y repetir el despliegue SOLO si el esquema sigue siendo compatible. Volver una imagen no revierte migraciones: una base incompatible requiere el plan de recuperacion acordado. No hay rollback automatico.

No usar `docker compose down -v`: elimina volumenes con certificados y adjuntos. Los volumenes no son un respaldo; falta configurar copias externas y el modulo S3 previsto en la arquitectura. No detener/eliminar el RDS compartido para ahorrar sin coordinar con el equipo. Revisar credito y ciclo de vida de AWS Academy.

## 8. Limites actuales

No se desplego nada desde esta computadora ni se modifico RDS. Falta confirmar la IP publica actual, publicar la rama revisada, configurar la variable, publicar imagenes y arrancarlas en EC2. El dominio y HTTPS se configuraran despues. Despues se puede automatizar el paso SSH mediante un mecanismo autorizado (por ejemplo SSM con permisos del laboratorio); requerira una propuesta adicional y no almacenar la llave del equipo sin acordarlo.

Las pruebas de CI usan H2 y respuestas simuladas/componentes; no certifican compatibilidad de migraciones MySQL ni el despliegue real. Revisar tambien las vulnerabilidades de dependencias reportadas y restringir con el equipo la regla MySQL abierta a 0.0.0.0/0, conservando los accesos necesarios.

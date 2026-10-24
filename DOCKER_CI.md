# Docker y pruebas automaticas

## Que se agrega

- `backend/Dockerfile`: compila los modulos Maven con Java 21 y ejecuta el JAR con un usuario sin privilegios.
- `frontend/Dockerfile`: compila Next.js con Node 22 y ejecuta su servidor de produccion. `next.config.ts` activa la salida standalone.
- `.dockerignore`: excluye dependencias locales, resultados anteriores, claves y configuraciones privadas de las imagenes.
- `.github/workflows/ci.yml`: ejecuta pruebas y cobertura Maven, pruebas de contratos del frontend, lint y compilacion. Si pasan, construye ambas imagenes sin publicarlas.
- `compose.yaml`: permite probar los contenedores localmente con MySQL existente. No crea otra base de datos.

## Primero: construir sin conectarse a ninguna base

En una terminal desde la raiz del repositorio, con Docker Desktop abierto:

```powershell
docker build -t asiscontrol-backend:local ./backend
docker build -t asiscontrol-frontend:local ./frontend
```

La primera construccion descarga herramientas y dependencias. No necesitas cuenta de Docker Hub.

## Ejecutar en tu computadora

1. Copia `.env.example` a `.env`: `Copy-Item .env.example .env`.
2. Completa `.env` con tus credenciales locales y un JWT_SECRET aleatorio privado (al menos 32 caracteres). No pegues claves en archivos versionados ni capturas.
3. MySQL debe estar encendido y el esquema debe existir. Usa `host.docker.internal` para conectar desde un contenedor a MySQL instalado en Windows; `localhost` dentro del backend apunta al propio contenedor.
4. Ejecuta `docker compose up --build -d`.
5. Abre http://localhost:3000. El backend atiende en http://localhost:8080 y su estado en http://localhost:8080/actuator/health.

```powershell
docker compose ps
docker compose logs --tail 100 backend
docker compose logs --tail 100 frontend
docker compose down
```

`down` conserva los volumenes de archivos y registros. No uses `down -v` si necesitas conservarlos. El administrador inicial no se crea automaticamente en este Compose: usa un acceso existente. Flyway ejecuta las migraciones pendientes al iniciar contra MySQL; revisa el historial y acuerda una copia de seguridad antes de apuntar a RDS compartido.

El backend puede tardar mas que el frontend en iniciar; espera a que su salud responda antes de iniciar sesion.

## GitHub Actions

Al subir esta rama se ejecuta CI; tambien en pull requests hacia main/develop, en pushes a main y mediante Run workflow. No requiere claves AWS ni de MySQL. Las pruebas existentes del backend usan H2 aislado; no verifican las migraciones reales de MySQL. El frontend tiene pruebas de contratos, no pruebas completas de navegador.

En GitHub abre Actions > Pruebas y Docker. Los informes del backend quedan como artefactos durante siete dias. Un fallo conserva la comprobacion en rojo: debe revisarse antes de integrar. Para impedir merges con fallos hay que configurar las reglas de proteccion de main en GitHub.

## Pendiente para publicar en AWS

Este cambio prepara imagenes y CI; no publica la web ni despliega en EC2. El Compose incluido solo expone puertos en la computadora local y usa cookies para HTTP local.

Para EC2 falta configurar proxy HTTPS/dominio, cookies seguras, direccion publica del frontend y API, acceso EC2 a RDS, secretos privados del servidor, revision de migraciones y publicacion/despliegue de imagenes. `NEXT_PUBLIC_API_BASE_URL` se fija al construir el frontend: cambiarla requiere reconstruir su imagen. Nunca debe ser el nombre Docker `backend`, porque la utiliza el navegador del usuario.

Los archivos adjuntos se conservan en un volumen local; la integracion con S3 indicada en la arquitectura sigue pendiente y requiere trabajo del modulo correspondiente.

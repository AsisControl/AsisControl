#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
if [[ ! -f .env ]]; then
  echo 'Falta deploy/.env. Copia .env.example y completa los valores privados.' >&2
  exit 1
fi
chmod 600 .env
compose=(docker compose --env-file .env -f compose.yaml)
# No imprimir config: contiene las credenciales interpoladas.
"${compose[@]}" config --quiet
# Leer solo las variables publicas de Caddy, sin imprimir secretos del backend.
"${compose[@]}" run --rm --no-deps --entrypoint sh proxy -ec '
  case "$APP_URL" in
    "https://$APP_HOST") ;;
    *) echo "APP_URL debe ser https:// seguido exactamente de APP_HOST." >&2; exit 1 ;;
  esac
  caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
'
echo 'Descargando las dos imagenes antes de reemplazar contenedores...'
"${compose[@]}" pull
"${compose[@]}" up -d --remove-orphans
"${compose[@]}" ps
echo 'Contenedores iniciados. Comprueba HTTPS y /healthz con los comandos de la guia.'

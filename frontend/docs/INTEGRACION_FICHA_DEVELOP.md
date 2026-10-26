# Integración de ficha con develop — v08

Fecha: 25 de octubre de 2026 (Lima).

- Base develop: `7ce01a5e992f909d35f17b58b28fc3bfa44babeb`.
- Feature: `7d3dab4578cf33c07c58bda2c2c3047cb553123a`.
- Rama de trabajo: `fix/integracion-ficha-develop-v08`.

Esta integración parte del develop actualizado y combina la ficha con los
avances del equipo. La fusión anterior de la feature en main no sustituye este
trabajo ni incorpora las correcciones a develop.

## Resoluciones de la integración

Se resuelven seis conflictos: package.json, package-lock.json, el cliente de la
API, students.tsx, types.ts y GlobalExceptionHandler.java.

El cliente conserva renovación y reintento de sesión, tiempos de espera y
descargas con apiBlob. ApiError conserva status/code/fieldErrors/details para
la ficha. fieldErrorsOf transforma esos errores en el mapa que consumen los
formularios de configuración académica y aulas, conservando el primer mensaje
de cada campo. Acepta los alias campo/mensaje sin cambiar el contrato del backend.

La ficha mantiene PanelHeading, PersonCell, StatusPill y la carga masiva de
develop: descargar plantilla, enviar FormData y actualizar el listado tras
importar. Los tipos de importación y de ficha conviven en el mismo módulo.

El backend conserva detalles de conflictos, errores de formulario, errores por
campo de reglas de negocio, auditoría y manejadores de archivos y autenticación.
Las pruebas de importación utilizan nombres válidos y un caso explícito de
rechazo de nombres numéricos, conforme a las reglas de la ficha.

V5__ficha_alumno_opcionales.sql sustituye el nombre V3 de la feature. Se conservan
sin cambios V3 de sesiones y V4 de secciones de develop. Si una base ya aplicó la
antigua V3 de ficha, el DBA debe reconciliar su historial antes del arranque.

Se conservan Vitest y los umbrales de develop. Las pruebas FE2 importan código
real y participan en la cobertura. Se añaden recorridos DOM de carga masiva y
una regresión que comprueba los errores de ficha después de renovar la sesión.

## Límites de esta entrega

La validación automatizada usa H2 para el backend y una API simulada para las
pruebas del frontend. Los resultados exactos, comandos y entorno están en
RESULTADOS_VERIFICACION.json del paquete v08.

Pendientes: MySQL/Flyway V5 sobre una base de pruebas, recorrido manual en
navegador contra API real y CI de la rama publicada. Homonimia, control de versión
y ciclo de vida completo requieren capacidades de backend aún no implementadas;
los mocks del frontend no acreditan esos endpoints. No se declara cerrada US-09.

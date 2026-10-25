> Integración v08, 25/10/2026: base develop 7ce01a5. Conservar V3 de sesiones y V4 de secciones; la ficha utiliza V5. El manejador combina errores por campo de reglas de negocio con los errores y detalles de ficha. Consultar frontend/docs/INTEGRACION_FICHA_DEVELOP.md y RESULTADOS_VERIFICACION.json del paquete. Las cifras siguientes son históricas.

# Actualización de estilo v0.4 — 22/10/2026

Se aplicó el estándar de programación al parche de backend: nombres descriptivos, métodos separados por responsabilidad, constantes, formato Java de cuatro espacios e imports revisados. El contrato y la migración V3 permanecen iguales a v0.3. Las 63 pruebas de backend pasan con JDK 21 y H2.

La descripción funcional siguiente continúa vigente. V3 modifica una tabla compartida y sigue siendo propuesta de integración para BE2/DBA; no se ejecutó en el MySQL del usuario.

---

# BE2 — Integración de correcciones de ficha v0.3

Fecha: 22/10/2026. Cambios preparados sobre el backend de develop 5f26d46; el frontend FE2 también es necesario.

## Qué usar

- `PersonaDtos.FichaAlumno`: grupo de validación de las fichas de secundaria. `@ConvertGroup` en los requests de alumno aplica estas reglas a Persona sin convertir los campos opcionales en obligatorios ni quitar los obligatorios de docentes/apoderados.
- `@ValidAlumnoPersona` y `AlumnoPersonaValidator`: validación por tipo de documento, nombres y edad por cumpleaños. Cada violación usa `addPropertyNode` para devolver `persona.campo`.
- `PersonaService`: comprueba las reglas también en llamadas de servicio; normaliza opcionales a null; omite consultas de duplicados para null; mantiene código al editar y audita la corrección de identidad con motivo.
- `ContadorCodigoAlumno` y su repositorio: contador transaccional con bloqueo pesimista para la generación automática. La API web ignora códigos manuales. Los consumidores internos de importación pueden conservar el código legado; el generador evita reutilizar códigos ya ocupados.
- `Persona` y migración V3: cinco columnas pasan a admitir null, sin eliminar datos ni índices. El nombre completo omite valores ausentes.
- `GlobalExceptionHandler` y `FormFieldException`: errores estructurados de validación, formato JSON, motivos y duplicados; sin devolver SQL ni valores personales en el mensaje.

## Contrato del alta

`POST /api/alumnos`:

```json
{
  "persona": {
    "nombres": "Diego",
    "apellidoPaterno": "Ibarra",
    "apellidoMaterno": null,
    "tipoDocumento": null,
    "numeroDocumento": null,
    "fechaNacimiento": "2012-10-07",
    "sexo": null,
    "correo": null,
    "telefono": null,
    "direccion": null,
    "nacionalidadId": null
  }
}
```

Devuelve 201 con `id`, `codigoAlumno`, `persona`, `apoderados`, `activo`. No crea credenciales ni matrícula. El segundo apellido, sexo, contacto y documento completo son opcionales en la ficha. Teléfono informado: `9[0-9]{8}`. Nombres: 2–100; primer apellido: 2–80; segundo: hasta 80. DNI: 8 dígitos; CE: 9; pasaporte: 8–12 alfanuméricos. Edad: 10–20 años cumplidos con fecha de Lima.

`PUT /api/alumnos/{id}` mantiene el código existente y admite el mismo objeto, con `motivo` de 10–1000 cuando cambie identidad. La auditoría se conserva dentro de la transacción del cambio. No habilitar todavía `accionesPermitidas`: el ciclo de vida del backend antiguo tiene pendientes independientes.

## Errores por campo

Se conserva el contrato de errores existente para no romper a otros módulos:

```json
{
  "status": 400,
  "code": "DATOS_INVALIDOS",
  "message": "La solicitud contiene datos invalidos",
  "fieldErrors": [
    {"field": "persona.telefono", "message": "El teléfono debe tener exactamente 9 dígitos y empezar con 9"}
  ]
}
```

Documento/correo duplicado devuelve 409 con campo específico. Tipo de documento o fecha que no puede deserializarse devuelve 400 con su campo. Motivo inválido devuelve 400 con `field: motivo`. El frontend también admite alias `campo/mensaje`, pero el backend mantiene `field/message` para compatibilidad.

El cliente reconoce un error de `codigoAlumno` obligatorio del backend antiguo y explica que falta actualizar el backend, en lugar de señalar un campo invisible.

## Integración

1. Revisar los archivos del paquete BE2 sobre la rama propia; el ZIP de cambios solo contiene rutas de backend modificadas y nuevas, no todo el backend.
2. En la integración actual, incorporar V5__ficha_alumno_opcionales.sql con Flyway en el perfil mysql sobre una base de pruebas respaldada. V3 de sesiones y V4 de secciones permanecen intactas. Si ya se aplicó la antigua V3 de ficha, el DBA debe reconciliar el historial antes de arrancar. No editar migraciones aplicadas ni ejecutar V5 manualmente y luego mediante Flyway.
3. Ejecutar `mvnw.cmd test` con JDK 21. En este entorno se utilizó el agente explícito de Mockito porque la conexión dinámica de agentes no estaba disponible; no se modificó el pom ni se requiere esa ruta del entorno en los equipos del grupo.
4. Ejecutar el frontend actualizado contra el backend y MySQL del equipo; probar mínimo, documento opcional, duplicado, cumpleaños, pegado/autofill y corrección motivada.
5. Actualizar la matriz: edad 10–20, CE de 9 dígitos y celular peruano sustituyen los valores anteriores. Son cambios solicitados en esta revisión; no están presentados como aprobación previa del profesor.

## Validación y pendientes

Las 63 pruebas del backend pasan sobre H2 con JDK 21; nueve cubren esta corrección, incluida concurrencia de dos altas. La migración MySQL V3 se entrega para verificar en la base del equipo; no se ejecutó sobre una instancia MySQL aquí.

Quedan para el desarrollo de BE2: detección/confirmación real de homónimos, contrato de versión de ficha, acciones permitidas y semántica segura de inactivación/reactivación/eliminación, consulta de inactivos y cierre de todas las AT de US-09. No son funciones resueltas por este parche.

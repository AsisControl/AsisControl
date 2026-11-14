# Evidencias pedagógicas (US-17)

Una evidencia registra un estudiante, una asignación de curso, una competencia y
un período académico. El servidor obtiene el curso desde la asignación y
registra al docente autenticado como autor. Se requiere una descripción de hasta
1000 caracteres, un archivo existente cargado por ese docente, o ambos.

| Método | Ruta | Resultado |
|---|---|---|
| POST | `/api/evidencias-pedagogicas` | 201. Registra la evidencia y devuelve su ID. |
| GET | `/api/evidencias-pedagogicas?asignacionId=…&alumnoId=…` | 200. Lista paginada, con filtros opcionales `competenciaId` y `periodoId`. |
| GET | `/api/evidencias-pedagogicas/{id}` | 200. Detalle con autor, fechas y metadatos del archivo. |
| PUT | `/api/evidencias-pedagogicas/{id}` | 200. Reemplaza descripción y adjunto sin cambiar el contexto. |
| DELETE | `/api/evidencias-pedagogicas/{id}` | 204. Inactiva una evidencia que aún no sustenta una evaluación. |
| PUT | `/api/evidencias-pedagogicas/{id}/evaluaciones/{evaluacionId}` | 204. Vincula una evaluación del mismo contexto, competencia y período. Es idempotente. |

## Recorrido de demostración

1. En Swagger, iniciar sesión como docente y autorizar con su token.
2. Identificar una asignación activa, un alumno con matrícula activa en su
   sección, una competencia activa del curso y un período de ese año académico.
3. Si se quiere adjuntar un PDF, cargarlo como `archivo` en `POST /api/archivos`
   y copiar el `id` devuelto. Puede omitirse el archivo si hay descripción.
4. Enviar `POST /api/evidencias-pedagogicas` con ese contexto. Los números de
   este ejemplo son ilustrativos y deben sustituirse por los de la base de pruebas.

```json
{
  "asignacionId": 5,
  "alumnoId": 275,
  "competenciaId": 3,
  "periodoId": 1,
  "descripcion": "Resolución de problemas en clase",
  "archivoId": null
}
```

5. Consultar el detalle y el listado filtrado. Para descargar el archivo usar
   `GET /api/archivos/{id}/descarga`.
6. En la base de pruebas, cambiar a un docente con los mismos permisos pero
   asignado a otro curso o sección. El registro y la consulta ajenos devuelven 403.
7. Con el docente titular, vincular una evaluación compatible y comprobar que
   inactivar o reemplazar la evidencia devuelve 409. La eliminación directa del
   archivo también devuelve 409 mientras pertenezca a una evidencia activa.

La migración `V10__evidencias_pedagogicas.sql` agrega la evidencia y su relación
con las evaluaciones. Los eventos de auditoría registran actor, fecha y referencia
sin almacenar el contenido ni la descripción.

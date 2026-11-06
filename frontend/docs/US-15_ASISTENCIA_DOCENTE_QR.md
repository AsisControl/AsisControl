# US-15 — Asistencia general del docente con QR dinámico (frontend)

Subcarpeta `src/modules/attendance/teacher-check-in/` dentro del módulo de asistencia que comparte
con la US-14. Se expone por `attendance/index.ts` (`CheckInPoint`, `TeacherAttendance`, `TeacherCheckIn`)
y se registra en la opción **Asistencia** de `core/navigation/module-outlet.tsx`.

## Pantallas

| Rol | Pantalla | Qué hace |
|---|---|---|
| Administrador | `CheckInPoint` (punto de marcación) | Elige al docente, emite su QR, muestra la cuenta regresiva, lo renueva 10 s antes de vencer y detecta cuándo fue utilizado. |
| Docente | `TeacherAttendance` → pestaña *Mi marcación* (`/asistencia/mi-marcacion`) con `TeacherCheckIn` | Escanea el QR con la cámara (o ingresa el código), muestra Presente/Tardanza con la hora del servidor y su historial. |

## Contrato usado (`/api/asistencias/docentes`)

`POST /qr` (emitir) · `POST /qr/{id}/renovar` · `GET /qr/{id}` · `POST /qr/{id}/cancelar` ·
`POST /marcar` con `{ token }` · `GET /` (marcaciones propias). Docentes: `GET /api/docentes`.

## Criterios de aceptación

1. QR de vigencia limitada: 60 s, con renovación automática.
2. Marcación dentro del sistema, con la sesión del docente.
3. El frontend envía **solo el token**; la hora y el estado los asigna el servidor.
4. Duplicado: se muestra el error del backend y el registro existente de la jornada.
5. QR vencido: mensaje `QR_EXPIRADO`.
6. Sin permiso `ASISTENCIA_ESCRIBIR` o con 401/403: se rechaza y se explica.
7. `SIN_MARCACION` lo asigna el backend; el historial lo muestra.

## Dependencias nuevas

`qrcode.react` (dibujar el QR) y `jsqr` (leerlo desde la cámara, compatible con todos los navegadores).

## Pendientes a coordinar

- El backend emite el QR **por docente** (`docenteId`) y cada código sirve una vez. La historia habla de
  un QR institucional único; confirmar con BE si se mantiene el flujo por docente.
- Confirmar que `/marcar` verifica que el QR pertenezca al docente autenticado.
- La cámara del navegador exige HTTPS fuera de `localhost`: el despliegue debe tener certificado.
- La opción Asistencia del docente se comparte con la US-14: `/asistencia` sigue mostrando la bandeja de
  justificaciones y `/asistencia/mi-marcacion` la marcación con QR.

## Verificación

`npm run lint`, `npm run test:coverage`, `npm run build`. Pruebas en `attendance/tests/` (`teacher-check-in`, `check-in-point`, `teacher-check-in-service`, `teacher-attendance`; 23 casos).

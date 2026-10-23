# Integración de la renovación de sesión

El backend permite recuperar la sesión después de recargar la página sin guardar el JWT
en localStorage ni sessionStorage. El equipo de frontend debe incorporar las llamadas
descritas aquí. La modificación del backend por sí sola no cambia su SessionProvider.

## Contrato HTTP

| Operación | Solicitud | Resultado |
| --- | --- | --- |
| Iniciar | `POST /api/auth/login` con `{ "identifier": "...", "password": "..." }` | Mismo JSON AuthResponse de antes y cookie `asiscontrol_refresh` |
| Recuperar o renovar | `POST /api/auth/refresh`, sin cuerpo ni Authorization | AuthResponse con JWT nuevo y cookie rotada |
| Salir | `POST /api/auth/logout`, sin cuerpo ni Authorization | 204, revocación de la sesión y eliminación de la cookie |
| Consultar usuario | `GET /api/auth/me` con `Authorization: Bearer <accessToken>` | UserSummary |

La cookie es HttpOnly, de host (sin Domain), Path=/api/auth, SameSite=Strict y Secure
por defecto. Su secreto aleatorio tiene 256 bits y solo se almacena su SHA-256.
El JSON y los logs no deben contener el token de renovación. Login y refresh usan
Cache-Control: no-store. Los JWT duran como máximo 15 minutos; una configuración antigua
de JWT_EXPIRATION_MINUTES=480 también se limita a 15 en el servicio.

La sesión tiene un límite absoluto de ocho horas y vence tras treinta minutos sin
actividad. Renovarla no extiende el límite absoluto. La actividad se verifica en el
servidor tanto al usar el JWT como al renovar. Una renovación automática periódica
sin actividad del usuario prolongaría la actividad: no se debe implementar un heartbeat
para mantener indefinidamente a un usuario ausente.

Cada renovación consume el token anterior y emite uno nuevo. Se conserva el historial
de hashes en TokenRenovacion, asociado a una SesionAcceso. Reutilizar un token consumido
revoca esa sesión, incluso el JWT más reciente. Un bloqueo pesimista serializa las
renovaciones concurrentes. Cerrar sesión, desactivar el acceso o cambiar/restablecer
la contraseña impide renovar las sesiones afectadas.

## Cambios que debe hacer el frontend

1. Usar `credentials: "include"` en **login, refresh y logout**. En un login entre
   puertos diferentes también es necesario para que el navegador acepte Set-Cookie.
2. Al montar el proveedor de autenticación, mostrar un estado de carga y llamar una
   sola vez a refresh. Guardar accessToken, expiresAt y user en memoria. Redirigir al
   login únicamente si la recuperación falla con 401; un error de red debe mostrarse
   como un error de conexión y permitir reintentar.
3. Para las demás peticiones, mantener Authorization: Bearer y el JWT en memoria.
4. Si vence el JWT y la persona está utilizando la aplicación, ejecutar una única
   renovación compartida por todas las peticiones pendientes y reintentar cada
   petición una sola vez. Evitar renovaciones duplicadas por React Strict Mode,
   varias solicitudes simultáneas o varias pestañas. Usar Web Locks/BroadcastChannel
   para coordinar pestañas; dos consumos del mismo token se tratan como reutilización.
5. Llamar a logout con la cookie, aunque ya no exista JWT en memoria. Limpiar la memoria
   al terminar. No enviar un Authorization vencido a refresh/logout: el filtro JWT
   lo rechazaría antes de que se procese la cookie.
6. Después de cambiar o recuperar una contraseña, limpiar la sesión y volver al login.

Ejemplo de recuperación al recargar:

```ts
const response = await fetch(`${API_URL}/api/auth/refresh`, {
  method: "POST",
  credentials: "include",
});
if (response.ok) {
  const session = await response.json();
  saveSession(session); // memoria, no almacenamiento persistente
} else if (response.status === 401) {
  clearSession();
} else {
  throw new Error("No se pudo recuperar la sesión");
}
```

El navegador añade Origin. No se configura manualmente en fetch. Refresh y logout
con cookie exigen un Origin exacto presente en CORS_ALLOWED_ORIGINS o el propio
origen del backend, lo que también protege contra solicitudes CSRF. Para probar con
curl/Postman se debe enviar explícitamente `Origin: http://localhost:3000`.
HttpOnly reduce la exposición del secreto a JavaScript; no evita que un XSS ejecute
acciones desde la propia página, por lo que siguen siendo necesarias las defensas XSS.

## Configuración y base de datos

- Desarrollo local, http://localhost:3000 y http://localhost:8080:
  CORS_ALLOWED_ORIGINS=http://localhost:3000 y AUTH_COOKIE_SECURE=false.
  El perfil local ya usa Secure=false. Mantener el mismo nombre de host en ambos:
  no mezclar localhost con 127.0.0.1.
- MySQL por HTTP en desarrollo: configurar AUTH_COOKIE_SECURE=false explícitamente.
- Producción: HTTPS, AUTH_COOKIE_SECURE=true y orígenes exactos. Preferir frontend
  y API bajo el mismo sitio o un proxy /api. Strict no envía cookies entre sitios
  diferentes. Si la infraestructura exige sitios diferentes, usar
  AUTH_COOKIE_SAME_SITE=None con HTTPS y conservar la comprobación Origin; algunos
  navegadores restringen cookies de terceros incluso así.
- JWT_SECRET estable, de al menos 32 bytes y suministrado por entorno en MySQL.
  El perfil local H2 es efímero: al reiniciar se pierde la base y sus sesiones.
- La migración Flyway V3 crea TokenRenovacion sin borrar SesionAcceso ni datos.
  El SQL exportado anterior no contiene esta tabla nueva. No se debe volver a
  importar el dump completo sobre una base con datos: contiene DROP TABLE.
- Tras desplegar esta versión, iniciar sesión una vez para recibir la cookie;
  una sesión antigua sin token de renovación no puede recuperarse tras perder el JWT.

## Verificación

RenovacionSesionIntegrationTest prueba rotación, almacenamiento de hashes, expiración,
inactividad, contraseña temporal vencida, cuenta desactivada, cierre por cookie,
cambio/restablecimiento de contraseña, reutilización y concurrencia. Sus pruebas HTTP
comprueban Set-Cookie/HttpOnly, recuperación sin contraseña, rechazo de Origin y borrado
de cookie. RefreshCookieSupportTest prueba además Secure y SameSite de producción.

Estas pruebas se ejecutan con H2 aislado y no modifican AWS. El flujo en el navegador
debe comprobarse después de integrar el proveedor de autenticación del frontend.

Referencia del diseño: [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).
HttpOnly, Secure y SameSite son controles complementarios; no sustituyen la prevención XSS
ni los límites de sesión comprobados en el servidor.

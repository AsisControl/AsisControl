# Adjuntos locales y Amazon S3

El backend conserva la misma API de archivos y sus permisos. MySQL guarda los
metadatos de `ArchivoAdjunto`; el contenido puede estar en disco o en un bucket
privado. No se agregan tablas ni migraciones.

## Configuración

Por defecto `FILE_STORAGE_PROVIDER=local`. Los desarrolladores no necesitan
cuenta de AWS ni credenciales para trabajar con su base y archivos locales.

Para activar S3 en el backend desplegado, completar `deploy/.env`:

```dotenv
FILE_STORAGE_PROVIDER=s3
FILE_STORAGE_S3_BUCKET=asiscontrol-archivos
FILE_STORAGE_S3_REGION=us-east-1
FILE_STORAGE_S3_PREFIX=asiscontrol/adjuntos
```

El prefijo no lleva barras iniciales o finales. No cambiar bucket o prefijo tras
subir archivos sin planificar la migración: las ubicaciones guardadas los incluyen.
En modo S3, una configuración o permiso incorrecto provoca un error; la aplicación
no guarda silenciosamente el archivo en disco como alternativa.

El SDK obtiene credenciales mediante la cadena predeterminada de AWS. En EC2 se
debe usar el rol de la instancia (`LabRole` en el laboratorio), sin claves en el
código ni en las imágenes. Para probar S3 desde una computadora, se necesita
configurar credenciales externas válidas; las temporales requieren también el
token de sesión y deben renovarse al expirar. No subirlas a Git.

## Bucket y permisos

- Bucket privado, bloqueo completo de acceso público y ACL deshabilitadas.
- Cifrado de las cargas con SSE-S3 (AES256).
- El rol de EC2 necesita `s3:PutObject`, `s3:GetObject` y `s3:DeleteObject` para
  `arn:aws:s3:::asiscontrol-archivos/asiscontrol/adjuntos/*`. DeleteObject compensa
  cargas nuevas que no pudieron registrarse en la base; la baja ordinaria sigue
  siendo lógica y conserva el objeto.
- Las consultas o copias administrativas con AWS CLI pueden requerir además
  `s3:ListBucket` sobre el bucket. No necesitan hacerse públicas las descargas.
- Como el backend está en un contenedor, comprobar acceso al servicio de metadatos
  de EC2: IMDSv2 habilitado y límite de saltos de respuesta 2 para Docker en bridge.
  El éxito de AWS CLI en el host no demuestra acceso desde el contenedor.
- En AWS Academy, utilizar el rol permitido por el laboratorio. Si falta permiso,
  coordinarlo con su administrador; no se requiere crear un usuario IAM con claves.

## Qué sucede al subir y descargar

Se conservan las validaciones de formato real, tamaño, nombre y hash SHA-256. El
backend utiliza un temporal para comprobar el contenido antes de enviarlo a S3 y
lo elimina al terminar. La clave remota usa UUID, sin nombres de alumnos.

Las cargas nuevas en S3 guardan `s3://bucket/prefijo/xx/uuid` en `ruta_archivo`.
Las rutas locales antiguas permanecen relativas. Al descargar, el backend reconoce
la ubicación y transmite el contenido tras comprobar los permisos existentes.
No entrega URLs públicas ni cambia las pantallas de tareas o evidencias.

Si falla el guardado de metadatos o se revierte la transacción de BD, se intenta
eliminar la carga nueva. La limpieza es de mejor esfuerzo: si AWS no permite
borrar o está caído, puede quedar un objeto huérfano que requiere revisión. Si el
proceso se interrumpe abruptamente también es posible un huérfano; no se promete
una transacción distribuida entre MySQL y S3.

## Archivos existentes y despliegue

No borrar el volumen Docker `archivos`. El modo S3 sigue leyendo sus adjuntos
locales anteriores, y lo usa para temporales. Esta entrega no migra archivos
automáticamente ni elimina originales. Para trasladarlos, copiar cada contenido
a la clave equivalente, verificar tamaño/hash y solo después actualizar su
ubicación en la base, conservando respaldo y originales hasta comprobar descargas.

Una base compartida con ubicaciones S3 necesita la configuración S3 para leerlas.
El modo local sin configuración AWS no puede acceder a esos objetos. Tampoco se
debe volver a una imagen antigua que desconozca las ubicaciones `s3://`.

Después de publicar las imágenes y actualizar los archivos de despliegue:

1. Configurar `.env`, conservar sus secretos anteriores y el SHA previo.
2. Ejecutar el despliegue habitual y comprobar los logs y la salud del backend.
3. Subir un PDF desde una tarea/entrega o evidencia con un usuario autorizado.
4. Comprobar en S3 que apareció bajo `asiscontrol/adjuntos/` y descargarlo desde
   AsisControl; comparar su contenido con el original.
5. Verificar que otro usuario sin permiso no pueda descargarlo y que un adjunto
   local anterior siga accesible.
6. Reiniciar/recrear el backend y repetir la descarga del nuevo adjunto.

Las pruebas automatizadas usan un cliente S3 simulado, además del modo local real;
no certifican permisos, red ni disponibilidad del bucket desplegado. La subida y
descarga realizadas con AWS CLI en el host EC2 son una comprobación independiente,
no una prueba de extremo a extremo de la aplicación.

Referencias oficiales:
- https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html
- https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/java_s3_code_examples.html
- https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-instance-metadata-options.html

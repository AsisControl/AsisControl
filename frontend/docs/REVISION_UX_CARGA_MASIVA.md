# Textos de ficha y carga masiva de estudiantes

Fecha: 25 de octubre de 2026.

La carga masiva y los textos de la ficha se actualizaron en la copia de trabajo
de la integración v08. La propuesta de redacción fue aceptada y aplicada al
formulario de registro y edición.

## Textos aplicados a la ficha

Mantener las etiquetas visibles. Usar ejemplos solo cuando aclaren el formato;
las condiciones de validación se explican con mensajes concretos cuando hay un
error. Los ejemplos no sustituyen las etiquetas ni las indicaciones necesarias.

| Texto anterior | Texto aplicado para el usuario |
|---|---|
| Los campos con * son obligatorios. La ficha conserva la identidad del estudiante a lo largo de los años académicos. | Completa los datos del estudiante. Los campos con * son obligatorios. |
| El código institucional se asigna automáticamente al guardar la ficha. | El código se asignará al guardar. |
| El código institucional es permanente y se conserva al actualizar la ficha. | Este código identifica al estudiante. |
| Secundaria: de 10 a 20 años cumplidos. Usa día, mes y año. | Sin ayuda permanente; mantener la etiqueta y el calendario. Explicar la edad permitida si la fecha ingresada no es válida. |
| Sin documento / pendiente | Sin documento por el momento |
| Ambos campos son opcionales juntos. Si completas uno, debes completar también el otro. | Puedes registrar el documento más adelante. Mostrar esta ayuda solo cuando no se haya elegido un tipo. |
| Selecciona el tipo si vas a registrar un documento; también puedes dejar ambos vacíos. | Retirar esta ayuda. Mostrar un ejemplo al elegir el tipo de documento. |
| DNI: exactamente 8 dígitos. | Placeholder: Ej. 12345678 |
| Carné de extranjería: exactamente 9 dígitos. | Placeholder: Ej. 001234567 |
| Pasaporte: de 8 a 12 letras o números, sin espacios ni guiones. | Placeholder: Ej. AB123456 |
| Sin registrar (sexo) | Selecciona una opción. Indicar «opcional» en la etiqueta del campo. |
| Este correo pertenece a la ficha; no crea ni modifica un acceso al sistema. | Retirar la ayuda. Placeholder: Ej. estudiante@correo.com |
| Celular peruano: 9 dígitos, empieza con 9. Opcional. | Placeholder: Ej. 987654321 |
| Dirección sin ejemplo | Placeholder: Ej. Av. Los Olivos 123 |
| Explica el cambio de identidad en 10 a 1000 caracteres. El historial conservará la corrección. | Explica qué dato necesitas corregir y por qué. Ejemplo en el campo: Corrección del apellido según su documento. |
| Guardar esta ficha no crea credenciales, una matrícula ni una asociación familiar. | La matrícula se realiza por separado. |

No se cambia ninguna regla de validación con esta redacción. En campos de fecha,
el calendario nativo conserva su formato local; no se simula un placeholder que
el navegador no permite controlar de forma consistente.

Referencias: [W3C, instrucciones de formularios](https://www.w3.org/WAI/tutorials/forms/instructions/)
y [GOV.UK, carga de archivos](https://design-system.service.gov.uk/components/file-upload/).

## Cambios aplicados a la carga masiva

- La plantilla generada por el backend contiene once columnas y omite nacionalidad.
  Se mantiene la lectura por nombre de columna para no desplazar sexo, nacimiento
  ni contacto. Se conserva la compatibilidad del lector con plantillas anteriores.
- La plantilla mantiene un ejemplo importable, cabecera visible al desplazarse y
  formato de texto en las columnas para conservar ceros iniciales de documentos.
- El selector muestra «Seleccionar archivo Excel», «Cambiar archivo» y el nombre
  seleccionado. Formato .xlsx y hasta 5 MB permanecen visibles antes de elegir.
- La ventana presenta dos pasos, una indicación para reemplazar la fila de ejemplo
  y una explicación del registro parcial antes de importar.
- Los controles se bloquean durante la importación. Los errores se asocian al
  selector y un intento inválido dirige el foco al botón para corregirlo.

## Revisión del estándar de programación

Fuente: Estándar de programación.docx, proyecto AsisControl, facilitado por el
usuario. También se contrastaron las reglas de frontend/docs/ESTRUCTURA_FRONTEND.md.
El alcance de esta revisión son los archivos modificados de plantilla, carga
masiva, selector, servicio y sus pruebas; no certifica todo el repositorio.

| Criterio del estándar | Comprobación en esta corrección |
|---|---|
| Responsabilidades separadas | Composición en import-dialog; selector en student-import-file; peticiones en services/student-import-service. |
| Nombres descriptivos y convenciones | Componentes y clase en PascalCase; funciones en camelCase; constantes en MAYÚSCULAS_CON_GUIONES. |
| Java con cuatro espacios | Aplicado al servicio modificado y a PlantillaAlumnoTest. |
| Métodos con una responsabilidad | La generación separa cabecera, ejemplo y escritura del archivo. |
| Estructura por capas | El controlador conserva su contrato y delega la generación en el servicio. |
| Servicios HTTP del frontend | Las peticiones usan el cliente compartido desde services; mantienen token, errores y tiempos de espera. |
| Estilos del módulo | CSS Module de students; sin reglas globales nuevas ni dependencias añadidas. |
| Errores controlados y sin detalles internos | Mensajes de selección comprensibles; manejo mediante el cliente HTTP existente y el manejador central del backend. |
| Imports y variables sin uso | Revisión estática y lint aprobados. |
| Pruebas y compilación | 272 pruebas frontend; 25 de backend enfocadas en plantilla/importación; lint y build frontend aprobados. |
| Git | Se conserva la rama fix/integracion-ficha-develop-v08. Mensaje sugerido: fix: mejorar carga masiva y plantilla de estudiantes. |

Cobertura frontend tras aplicar los textos: 98,16 % de líneas y 93,32 % de ramas.
Se repitieron las 272 pruebas del frontend, lint y compilación con resultado
correcto. Las pruebas adicionales de carga masiva cubren:
selección por teclado, nombre anunciado, rechazo antes de enviar y bloqueo
durante una importación. La plantilla se vuelve a abrir como Excel en las pruebas,
se importa y se comprueba la correspondencia de los datos.

La presentación del selector se revisó con el componente real y los estilos del
proyecto en una vista local aislada, a tamaño de escritorio y 390 px de ancho.
En la prueba estrecha, documento y ventana midieron 390 px, sin desbordamiento.

## Cómo ver los cambios en la aplicación

Trabajar en la misma carpeta AsisControl-integracion-v08 que ya se probó.
El frontend en modo desarrollo recoge los cambios al refrescar la página.
La plantilla la genera el backend: una instancia arrancada con el JAR anterior
seguirá entregando la plantilla antigua hasta recompilar y reiniciar.

Para actualizar el backend local, cerrar su terminal de ejecución con Ctrl+C,
compilar desde backend y volver a usar la configuración local del paso 8 de la
guía. Si se utiliza H2 en memoria, sus datos temporales se pierden al reiniciar.
Esta corrección no ejecuta SQL, no modifica MySQL ni publica cambios en GitHub.

## Revisión del avance anterior de ficha

Se amplió la revisión al código de la ficha que precedía a las correcciones de
carga masiva: listado, formulario, detalle, hook de estado, reglas, errores,
servicio HTTP y sus tipos. También se contrastaron las incorporaciones de backend
de ficha: controlador, DTO y validador, PersonaService, contador, repositorio,
excepciones y migración.

La organización anterior ya sigue los criterios de responsabilidades separadas,
servicios del frontend, arquitectura por capas del backend, DTO, nombres de
clases y funciones, constantes, tablas en PascalCase y columnas en snake_case.
Los errores se centralizan y los comentarios explican decisiones de integración.
Los endpoints de ficha usan sustantivos en plural y métodos HTTP coherentes.

Se corrigieron detalles de presentación del código anterior en students.tsx y
student-detail.tsx: indentación JSX, líneas extensas y varias instrucciones en una
misma línea. Las variables abreviadas de la fila y del evento se sustituyeron por
student y event. No se modificó su comportamiento.

El alcance es el avance de ficha e integración revisado. Esto no constituye una
auditoría completa de los demás módulos, del historial de commits ni de la base
MySQL desplegada. Las comprobaciones técnicas acreditan compilación y pruebas;
la validación funcional pendiente de US-09 continúa documentada por separado.

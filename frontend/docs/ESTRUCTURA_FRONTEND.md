# Estructura del frontend — Sprint 1

Organización del código pensada para que los 5 developers frontend trabajen en paralelo
(US-05, US-06, US-09 y US-37) sin editar los mismos archivos.

## Árbol de carpetas y responsables

```text
src/
├── app/                          Rutas de Next.js (archivos delgados)           FE 1
├── core/                         Base de la aplicación
│   ├── api/client.ts             Cliente HTTP, token JWT y errores 401/403       FE 5
│   ├── auth/                     Login, sesión, cambio y recuperación de clave   FE 1
│   ├── layout/                   Shell (menú, cuenta), login y marca             FE 1
│   └── navigation/
│       ├── navigation.ts         Opciones de menú por rol                        FE 1
│       └── module-outlet.tsx     Qué pantalla abre cada opción del menú          FE 1
├── shared/                       Piezas reutilizables, sin lógica de negocio     FE 5
│   ├── ui/                       Feedback, Field, PasswordField, ReasonField,
│   │                             Pager, Dialog, FormDialog, Eye (index.ts)
│   ├── forms/person-fields.tsx   Campos de persona (estudiantes, docentes, accesos)
│   ├── hooks/use-paged.ts        Listados paginados con búsqueda
│   ├── lib/                      pagination.ts, formatters.ts
│   └── types/                    Page<T>, Persona
└── modules/                      Un módulo por área funcional
    ├── academic-config/          US-05 y US-06                                   FE 3 (+ FE 1)
    │   ├── academic-configuration.tsx   Años académicos y año seleccionado       FE 3
    │   ├── periods/periods-panel.tsx    Períodos (US-05)                         FE 1
    │   ├── periods/non-teaching-days-panel.tsx  Días no lectivos (US-05)         FE 1
    │   ├── sections/grades-panel.tsx    Grados y capacidad sugerida (US-06)      FE 3
    │   ├── sections/sections-panel.tsx  Secciones, capacidad y vacantes (US-06)  FE 3
    │   └── types.ts                     AcademicYear, Period, Grade, Section, YearPanelProps
    ├── students/                 US-09 Ficha única de estudiante                 FE 2
    ├── teachers/                 US-37 Perfil institucional de docente           FE 4
    ├── administration/           Usuarios, accesos, auditoría, roles y logs      FE 1
    └── enrollment/               Matrícula y solicitudes (fuera del sprint)      FE 2
```

Cada módulo tiene un `index.ts` con su API pública. Es lo único que otros módulos pueden importar.

## Dónde trabaja cada developer

| Developer | Historia | Archivos principales | Rama sugerida |
|---|---|---|---|
| FE 1 | Seguridad, navegación y apoyo a US-05 | `core/auth`, `core/layout`, `core/navigation`, `modules/academic-config/periods/` | `feature/us05-academic-years` (periods) |
| FE 2 | US-09 | `modules/students/` | `feature/us09-students` |
| FE 3 | US-05 y US-06 | `modules/academic-config/` (excepto `periods/`) | `feature/us05-academic-years`, `feature/us06-sections` |
| FE 4 | US-37 | `modules/teachers/` | `feature/us37-teachers` |
| FE 5 | Componentes comunes, integración y calidad | `shared/`, `core/api/`, `tests/`, `app/globals.css` | `feature/frontend-shared` |

### Puntos de entrada ya preparados

- **US-05 / US-06:** la pestaña *Administración → Configuración académica* ya monta
  `AcademicConfiguration`, que muestra cada panel del año. Cada panel recibe `YearPanelProps`
  (año, si es editable, `onYearChange`, `onReload`, `onSuccess`), así que FE 1 y FE 3 pueden
  cambiar sus paneles sin tocar los del otro.
- **Días no lectivos (FE 1):** `non-teaching-days-panel.tsx` es un marcador. El backend aún no
  expone endpoints para días no lectivos: acordar el contrato con BE 3.
- **US-09 (FE 2):** `modules/students/students.tsx` ya tiene listado, registro, detalle y edición
  sobre `/api/alumnos`. Completar con los campos y validaciones oficiales del backlog.
- **US-37 (FE 4):** la pestaña *Administración → Docentes* ya monta `Teachers`
  (`modules/teachers/teachers.tsx`, marcador). Los tipos `Teacher` y `TeacherInput` en
  `modules/teachers/types.ts` siguen el DTO de `/api/docentes`. Tomar como modelo `students.tsx`.

## Reglas para evitar conflictos

1. **Cada uno trabaja dentro de su carpeta.** Si necesitas cambiar un archivo de otro, coordínalo
   antes con su responsable (o pídele el cambio).
2. **Importar otros módulos solo por su `index.ts`:** `import { Students } from "@/modules/students"`.
   ESLint rechaza `@/modules/<modulo>/<archivo>`. Dentro del propio módulo usa rutas relativas.
3. **`shared/` y `core/api` los modifica FE 5.** Si necesitas un componente nuevo, primero
   revisa `@/shared/ui`. Si lo necesitan dos módulos, pídelo a FE 5. Si solo lo usa tu módulo,
   créalo dentro de tu módulo.
4. **Navegación:** las pestañas de Administración se declaran en la lista `sections` de
   `modules/administration/administration.tsx`, y las opciones del menú principal en
   `core/navigation/`. Las entradas del sprint ya están registradas; un cambio adicional es una
   línea y lo coordina FE 1.
5. **Estilos:** `app/globals.css` es compartido (FE 5). Para estilos propios de tu módulo crea un
   CSS Module junto al componente (`students.module.css`) en lugar de agregar reglas globales.
   Reutiliza primero las clases existentes (`user-table`, `form-grid`, `field`, `panel`, etc.).
6. **Llamadas al backend:** usa siempre `api()` y `message()` de `@/core/api/client` (token,
   errores 401/403 y tiempo de espera ya resueltos). Para listados paginados, `usePaged`.
7. **Mocks temporales:** si un endpoint no existe, crea `mock-<recurso>.ts` dentro de tu módulo,
   márcalo como temporal y reemplázalo por la API real antes de cerrar la historia.
8. **Dependencias y `package-lock.json`:** instala con `npm ci`. No hagas commit del lockfile salvo
   que agregues o actualices una dependencia (versiones distintas de npm lo reescriben y generan
   conflictos).
9. **Ramas y PR:** no trabajar en `main`. Haz PR pequeños y frecuentes desde el día 1, con historia
   asociada, captura y resultado de `npm run lint`, `npm test` y `npm run build`.

## Verificación antes de abrir un PR

```bash
npm run lint
npm test
npm run build
```

## Aplicación del estándar en FE2 — v0.4

El módulo `students` conserva esta organización por responsabilidad:

- `services/student-service.ts`: llamadas HTTP del dominio mediante `core/api/client`.
- `student-errors.ts`: interpretación y mensajes de errores del dominio.
- `student-rules.ts`: normalización, construcción del DTO y validaciones por grupo de campos.
- `use-student-form.ts`: estado, foco controlado y coordinación del guardado.
- `student-field.tsx` y `student-form-fields.tsx`: campos visuales y secciones del formulario.
- `student-form.tsx`: composición del formulario y confirmación de descarte.
- `student-api.ts`: reexportaciones compatibles con las importaciones anteriores de FE2.

La recomendación `services/` del estándar se aplica dentro del módulo. La estructura general `core/shared/modules` y la API pública `index.ts` del equipo se conservan.

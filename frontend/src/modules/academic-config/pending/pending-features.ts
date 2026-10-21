/** Funciones del diseño sin respaldo en el backend: se muestran deshabilitadas. */
export const pendingFeatures = {
  editPeriod: {
    label: "Editar período",
    hint: "El backend aún no expone la edición de nombre y fechas.",
  },
  periodOnActiveYear: {
    label: "Nuevo período con año activo",
    hint: "El backend solo acepta períodos nuevos con el año en borrador.",
  },
  sectionClassroom: {
    label: "Aula asignada",
    hint: "La sección todavía no tiene aula en el backend.",
  },
  nonTeachingDays: {
    label: "Días no lectivos",
    hint: "Falta GET /anios/{anio}/dias-no-lectivos. Está en la rama origin/feature/backend-estructura-academica, aún no en main.",
  },
  addNonTeachingDay: {
    label: "Agregar día no lectivo",
    hint: "Falta POST /anios/{anio}/dias-no-lectivos. Está en la rama origin/feature/backend-estructura-academica, aún no en main.",
  },
  editNonTeachingDay: {
    label: "Editar descripción",
    hint: "Falta PATCH /dias-no-lectivos/{id} (solo la descripción, con version). Está en la rama origin/feature/backend-estructura-academica, aún no en main.",
  },
  deleteNonTeachingDay: {
    label: "Eliminar día no lectivo",
    hint: "Sin endpoint en el diseño de backend actual: la rama origin/feature/backend-estructura-academica no define DELETE.",
  },
} as const;

export type PendingFeatureKey = keyof typeof pendingFeatures;

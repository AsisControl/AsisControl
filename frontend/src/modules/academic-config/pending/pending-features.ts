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
  deleteNonTeachingDay: {
    label: "Eliminar día no lectivo",
    hint: "El backend no expone DELETE de días no lectivos; solo se puede editar la descripción.",
  },
} as const;

export type PendingFeatureKey = keyof typeof pendingFeatures;

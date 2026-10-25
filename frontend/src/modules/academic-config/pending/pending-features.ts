/**
 * Funciones del diseño que todavía no están disponibles: se muestran
 * deshabilitadas con una explicación pensada para quien usa la pantalla.
 */
export const pendingFeatures = {
  editPeriod: {
    label: "Editar período",
    hint: "La edición del nombre y las fechas de un período estará disponible próximamente.",
  },
  sectionClassroom: {
    label: "Aula asignada",
    hint: "La asignación de aulas a las secciones estará disponible próximamente.",
  },
  deleteNonTeachingDay: {
    label: "Eliminar día no lectivo",
    hint: "Por ahora solo se puede editar la descripción; la eliminación estará disponible próximamente.",
  },
} as const;

export type PendingFeatureKey = keyof typeof pendingFeatures;

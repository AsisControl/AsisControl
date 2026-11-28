/**
 * Funciones del diseño que todavía no están disponibles: se muestran
 * deshabilitadas con una explicación pensada para quien usa la pantalla.
 */
export const pendingFeatures = {
  sectionClassroom: {
    label: "Aula asignada",
    hint: "La asignación de aulas a las secciones estará disponible próximamente.",
  },
} as const;

export type PendingFeatureKey = keyof typeof pendingFeatures;

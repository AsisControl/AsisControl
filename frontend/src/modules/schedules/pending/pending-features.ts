/** Partes del diseño de Horarios que aún no están disponibles: se muestran deshabilitadas. */
export const pendingFeatures = {
  classroomEquipment: {
    label: "Equipamiento",
    hint: "El registro del equipamiento de cada aula estará disponible más adelante.",
  },
} as const;

export type PendingFeatureKey = keyof typeof pendingFeatures;

import type { PromotionResult, RenewalState } from "../services/renewal-service";

export const renewalLabels: Record<RenewalState, string> = {
  EN_REVISION: "En revisión",
  APROBADA: "Aprobada",
  RECHAZADA: "Rechazada",
};

export const promotionLabels: Record<PromotionResult, string> = {
  PROMOVIDO: "Grado aprobado",
  NO_PROMOVIDO: "Grado no aprobado",
};

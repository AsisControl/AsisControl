import { api } from "@/core/api/client";
import type { Section } from "../../types";

const PATH = "/api/secciones/propuestas";
type Context = { anioAcademico: number; grado: number };
export type SectionProposal = Pick<Section, "nombre" | "capacidadMaxima">;

export function generateSectionProposals(input: Context & { cantidad: number; capacidadMaxima: number }) {
  return api<SectionProposal[]>(PATH, { method: "POST", body: JSON.stringify(input) });
}

export function confirmSectionProposals(input: Context & { secciones: SectionProposal[]; motivo: string | null }) {
  return api<Section[]>(`${PATH}/confirmar`, { method: "POST", body: JSON.stringify(input) });
}

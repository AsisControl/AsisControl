import type { Persona } from "@/shared/types/person";
export type Student = {
  id: number;
  codigoAlumno: string;
  persona: Persona;
  activo: boolean;
  apoderados: {
    id: number;
    apoderadoId: number;
    nombreCompleto: string;
    parentesco: string;
    principal: boolean;
  }[];
};

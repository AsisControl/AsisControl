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
/** Respuesta de POST /api/alumnos/importaciones (PersonaDtos.ImportResult). */
export type ImportResult = {
  totalRows: number;
  importedRows: number;
  rejectedRows: number;
  issues: { row: number; field: string | null; message: string }[];
};

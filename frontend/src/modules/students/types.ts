import type { Persona } from "@/shared/types/person";
// La ficha tiene datos opcionales propios; no cambia el contrato compartido de docentes/apoderados.
export type StudentPerson = Omit<
  Persona,
  "apellidoMaterno" | "tipoDocumento" | "numeroDocumento" | "sexo" | "correo"
> & {
  apellidoMaterno: string | null;
  tipoDocumento: string | null;
  numeroDocumento: string | null;
  sexo: string | null;
  correo: string | null;
  fechaCreacion?: string | null;
  fechaActualizacion?: string | null;
};
export type Student = {
  id: number;
  codigoAlumno: string;
  persona: StudentPerson;
  activo: boolean;
  fechaCreacion?: string | null;
  fechaActualizacion?: string | null;
  version?: number;
  // BE2 debe exponerlas después de implementar el ciclo de vida seguro (OBS-039).
  accionesPermitidas?: {
    editar?: boolean;
    inactivar?: boolean;
    reactivar?: boolean;
    eliminar?: boolean;
  };
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

export type StudentDraft = {
  nombres: string;
  apellidoPaterno: string;
  apellidoMaterno: string;
  tipoDocumento: string;
  numeroDocumento: string;
  fechaNacimiento: string;
  sexo: string;
  correo: string;
  telefono: string;
  direccion: string;
  motivo: string;
};
export type StudentErrors = Partial<Record<keyof StudentDraft, string>>;
export type StudentInput = {
  persona: {
    nombres: string;
    apellidoPaterno: string;
    apellidoMaterno: string | null;
    tipoDocumento: string | null;
    numeroDocumento: string | null;
    fechaNacimiento: string;
    sexo: string | null;
    correo: string | null;
    telefono: string | null;
    direccion: string | null;
    nacionalidadId: number | null;
  };
  motivo?: string;
  confirmarHomonimia?: boolean;
  version?: number;
};
export type StudentAction = "inactivar" | "reactivar" | "eliminar";
export type Homonym = {
  id: number;
  codigoAlumno: string;
  nombreCompleto: string;
  fechaNacimiento: string;
};

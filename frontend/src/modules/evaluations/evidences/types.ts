import type { CourseCompetency } from "../types";

export type EvidenceCompetency = CourseCompetency;
/** Contratos de EvidenciaPedagogicaDto. */
export type EvidenceFile = {
  id: number;
  nombreOriginal: string;
  tipoMime: string;
  tamanioBytes: number;
};
export type Evidence = {
  id: number;
  asignacionId: number;
  alumnoId: number;
  cursoId: number;
  competenciaId: number;
  competenciaCodigo?: string;
  competenciaNombre?: string;
  periodoId: number;
  autorId: number;
  descripcion: string | null;
  archivo: EvidenceFile | null;
  fechaCreacion: string;
  fechaActualizacion: string;
};
export type EvidenceInput = {
  asignacionId: number;
  alumnoId: number;
  competenciaId: number;
  periodoId: number;
  descripcion: string | null;
  archivoId: number | null;
};
/** La edición reemplaza descripción y adjunto; el contexto no cambia. */
export type EvidenceEdit = { descripcion: string | null; archivoId: number | null };
export type EvidencePeriod = { id: number; nombre: string };
export type EvidenceStudent = { id: number; nombre: string };

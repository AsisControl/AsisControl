/** Contratos de lectura de TareaDtos y ArchivoDtos; fechas ISO sin convertir. */
export type EstadoPublicacionTarea =
  | "BORRADOR"
  | "PUBLICADA"
  | "CERRADA"
  | "ARCHIVADA";

export type CrearTareaRequest = {
  asignacionCursoId: number;
  nombre: string;
  descripcion: string;
  fechaPublicacion: string | null;
  fechaLimite: string;
  puntajeMaximo: number;
  maxArchivosEntrega: number;
  archivoIds: number[];
};

export type ActualizarTareaRequest = Omit<CrearTareaRequest, "asignacionCursoId"> & {
  version: number;
};

/** TareaDtos.EstadoTarea y EntregaTareaResponse. */
export type EstadoEntrega = "PENDIENTE" | "ENTREGADO" | "ENTREGADO_TARDE" | "CALIFICADO" | "ANULADO";

export type EntregaTareaResponse = {
  id: number;
  tareaId: number;
  alumnoId: number;
  fechaHoraEntrega: string;
  archivos: ArchivoResponse[];
  nota: number | null;
  comentario: string | null;
  estado: EstadoEntrega;
  version: number;
};

export type CalificarEntregaRequest = {
  nota: number;
  comentario: string | null;
  version: number;
};

/** Proyección de PersonaDtos.AlumnoResponse; solo se consumen identidad y código. */
export type AlumnoResumen = {
  id: number;
  codigoAlumno: string | null;
  persona: { nombreCompleto: string };
};

export type ArchivoResponse = {
  id: number;
  nombreOriginal: string;
  tipoMime: string;
  tamanioBytes: number;
  sha256: string;
  cargadoPorPersonaId: number;
  fechaSubida: string;
};

export type TareaResponse = {
  id: number;
  asignacionCursoId: number;
  nombre: string;
  descripcion: string;
  fechaPublicacion: string | null;
  fechaLimite: string;
  puntajeMaximo: number;
  maxArchivosEntrega: number;
  estado: EstadoPublicacionTarea;
  archivos: ArchivoResponse[];
  version: number;
};

/** AcademicoDtos.AsignacionCursoResponse. No representa una asignación propia por sí sola. */
export type AsignacionCursoResponse = {
  id: number;
  cursoId: number;
  curso: string;
  seccionId: number;
  seccion: string;
  anioAcademico: number;
  docenteId: number;
  docente: string;
  activo: boolean;
  version: number;
};

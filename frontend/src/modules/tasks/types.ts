/** Contratos de lectura de TareaDtos y ArchivoDtos; fechas ISO sin convertir. */
export type EstadoPublicacionTarea =
  | "BORRADOR"
  | "PUBLICADA"
  | "CERRADA"
  | "ARCHIVADA";

export type CrearTareaRequest = {
  nuevaEvaluacion?: { tipoEvaluacion: string; idsCompetencia: number[] };
  competenciasSinPeso?: { idsCompetencia: number[] };
  cuentaParaResultadoAcademico?: boolean;
  idEvaluacionAcademica?: number | null;
  asignacionCursoId: number;
  nombre: string;
  descripcion: string;
  fechaPublicacion: string | null;
  fechaLimite: string;
  puntajeMaximo?: number;
  maxArchivosEntrega: number;
  archivoIds: number[];
};

export type ActualizarTareaRequest = Omit<CrearTareaRequest, "asignacionCursoId"> & {
  version: number;
  /** Obligatorio si la tarea publicada ya tiene entregas y cambia la fecha límite. */
  motivo?: string | null;
};

/** TareaDtos.SeguimientoAlumno: PENDIENTE con entregaId nulo significa "sin entrega". */
export type SeguimientoAlumno = {
  alumnoId: number;
  codigoAlumno: string | null;
  nombreCompleto: string;
  /** Falso si entregó pero ya no está matriculado en la sección (no suma al total). */
  matriculaActiva: boolean;
  entregaId: number | null;
  estado: EstadoEntrega;
  fechaHoraEntrega: string | null;
  nota: number | null;
  nivelLogro?: "AD" | "A" | "B" | "C" | null;
  tieneRespuesta: boolean;
  cantidadArchivos: number;
  competenciasCalificadas: number;
  totalCompetencias: number;
};

/** Respuesta de GET /api/tareas/{id}/seguimiento: padrón completo de la sección. */
export type SeguimientoTareaResponse = {
  tareaId: number;
  totalAlumnos: number;
  entregados: number;
  calificados: number;
  pendientes: number;
  alumnos: SeguimientoAlumno[];
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
  nivelLogro?: "AD" | "A" | "B" | "C" | null;
  comentario: string | null;
  respuesta?: string | null;
  estado: EstadoEntrega;
  version: number;
};

export type CalificarEntregaRequest = {
  nota?: number;
  nivelLogro?: "AD" | "A" | "B" | "C";
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
  puntajeMaximoFijo?: boolean;
  modeloCualitativo?: boolean;
  evaluacionAcademica?: { id: number; titulo: string; estado: string; cuentaParaResultadoAcademico: boolean; componente: string | null; pesoComponente: number | null } | null;
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

/** Respuesta de GET /api/dashboard/docente. Horas "08:00:00"; fecha "2026-11-17". */
export type MomentoClase = "PROXIMA" | "EN_CURSO" | "FINALIZADA";
export type EstadoSesion = "PENDIENTE" | "BORRADOR" | "TOMADA";
export type TipoPendiente = "EVALUACION" | "TAREA" | "JUSTIFICACION";
export type Prioridad = "ALTA" | "MEDIA";

export type ClaseHoy = {
  sesionId: number;
  asignacionCursoId: number;
  curso: string;
  seccion: string;
  aula: string;
  horaInicio: string;
  horaFin: string;
  estado: EstadoSesion;
  momento: MomentoClase;
  puedeTomar: boolean;
};

export type Pendiente = {
  tipo: TipoPendiente;
  /** Tarea o evaluación; null en las justificaciones, que llegan agrupadas. */
  id: number | null;
  asignacionCursoId: number | null;
  titulo: string;
  detalle: string;
  /** Entregas sin calificar, niveles por registrar o justificaciones por revisar. */
  cantidad: number;
  prioridad: Prioridad;
};

export type InicioDocente = {
  fecha: string;
  anioAcademico: number | null;
  periodo: string | null;
  clasesHoy: ClaseHoy[];
  asistenciasPendientes: number;
  porRevisar: number;
  bloquesSemanales: number;
  pendientes: Pendiente[];
};

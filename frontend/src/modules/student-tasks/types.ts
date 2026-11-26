export type TaskFile = { id: number; nombreOriginal: string; tipoMime: string; tamanioBytes: number };
export type TaskDelivery = {
  id: number; tareaId: number; alumnoId: number; fechaHoraEntrega: string;
  archivos: TaskFile[]; nota: number | null; comentario: string | null; respuesta?: string | null;
  estado: "PENDIENTE" | "ENTREGADO" | "ENTREGADO_TARDE" | "CALIFICADO" | "ANULADO";
  version: number;
};
export type StudentTask = {
  resultados?: { competencia: string; nivel: "AD" | "A" | "B" | "C"; pesoEfectivo: number | null }[];
  alumnoId: number; curso: string; docente: string; seccion: string;
  tarea: {
    id: number; nombre: string; descripcion: string; fechaLimite: string;
    fechaPublicacion: string | null; puntajeMaximo: number; maxArchivosEntrega: number;
    estado: "BORRADOR" | "PUBLICADA" | "CERRADA" | "ARCHIVADA";
    archivos: TaskFile[];
    evaluacionAcademica?: { id: number; titulo: string; cuentaParaResultadoAcademico: boolean; componente: string | null; pesoComponente: number | null } | null;
  };
  entrega: TaskDelivery | null;
};

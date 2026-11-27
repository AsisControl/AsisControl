import type { StudentTask } from "./types";

export function taskGrades(task: StudentTask) {
  const evaluation = task.tarea.evaluacionAcademica;
  return {
    components: evaluation?.estado === "PUBLICADA" || evaluation?.estado === "CERRADA"
      ? task.resultados ?? [] : [],
    general: evaluation ? null : task.entrega?.estado === "CALIFICADO"
      ? task.entrega.nivelLogro : task.entrega ? null : task.nivelSinEntrega,
  };
}

export function taskStatus(task: StudentTask) {
  if (task.entrega) return {
    PENDIENTE: "Entrega pendiente", ENTREGADO: "Entregada", ENTREGADO_TARDE: "Entregada tarde",
    CALIFICADO: "Calificada", ANULADO: "Anulada",
  }[task.entrega.estado];
  return task.tarea.estado === "CERRADA" ? "Cerrada sin entrega"
    : task.tarea.estado === "ARCHIVADA" ? "Archivada sin entrega"
    : new Date(task.tarea.fechaLimite) <= new Date() ? "Plazo vencido" : "Pendiente";
}

export function canSubmit(task: StudentTask) {
  return !task.entrega && task.tarea.estado === "PUBLICADA" &&
    task.tarea.evaluacionAcademica?.estado !== "PUBLICADA" && task.tarea.evaluacionAcademica?.estado !== "CERRADA" &&
    new Date(task.tarea.fechaLimite) > new Date() &&
    (!task.tarea.fechaPublicacion || new Date(task.tarea.fechaPublicacion) <= new Date());
}

export function canEditDelivery(task: StudentTask) {
  return !!task.entrega && task.entrega.estado !== "CALIFICADO" && task.entrega.estado !== "ANULADO" &&
    task.tarea.estado === "PUBLICADA" &&
    task.tarea.evaluacionAcademica?.estado !== "PUBLICADA" && task.tarea.evaluacionAcademica?.estado !== "CERRADA" &&
    new Date(task.tarea.fechaLimite) > new Date() &&
    (!task.tarea.fechaPublicacion || new Date(task.tarea.fechaPublicacion) <= new Date());
}

export function taskDate(value: string) {
  return new Date(value).toLocaleString("es-PE", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

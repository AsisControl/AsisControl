import type { StudentTask } from "./types";

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
    new Date(task.tarea.fechaLimite) > new Date() &&
    (!task.tarea.fechaPublicacion || new Date(task.tarea.fechaPublicacion) <= new Date());
}

export function canEditDelivery(task: StudentTask) {
  return !!task.entrega && task.entrega.estado !== "CALIFICADO" && task.entrega.estado !== "ANULADO" &&
    task.tarea.estado === "PUBLICADA" &&
    new Date(task.tarea.fechaLimite) > new Date() &&
    (!task.tarea.fechaPublicacion || new Date(task.tarea.fechaPublicacion) <= new Date());
}

export function taskDate(value: string) {
  return new Date(value).toLocaleString("es-PE", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

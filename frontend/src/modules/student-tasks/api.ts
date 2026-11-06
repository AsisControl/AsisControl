import { api, apiBlob } from "@/core/api/client";
import { normalizePage } from "@/shared/lib/pagination";
import type { Page } from "@/shared/types/api";
import type { StudentTask, TaskDelivery, TaskFile } from "./types";

export async function listStudentTasks(page: number, search: string, status: string): Promise<Page<StudentTask>> {
  const query = new URLSearchParams({ page: String(page), size: "20", busqueda: search.trim() });
  if (status) query.set("entregada", String(status === "submitted"));
  return normalizePage(await api<Page<StudentTask>>(`/api/alumnos/me/tareas?${query}`));
}

export function uploadTaskFile(file: File) {
  const body = new FormData();
  body.append("archivo", file);
  return api<TaskFile>("/api/archivos", { method: "POST", body, timeoutMs: 120000 });
}

export function submitTask(task: StudentTask, archivoIds: number[]) {
  return api<TaskDelivery>(`/api/tareas/${task.tarea.id}/entregas`, {
    method: "POST", body: JSON.stringify({ alumnoId: task.alumnoId, archivoIds }),
  });
}

export function getDelivery(id: number) {
  return api<TaskDelivery>(`/api/entregas/${id}`);
}

export function updateDelivery(delivery: TaskDelivery, archivoIds: number[]) {
  return api<TaskDelivery>(`/api/entregas/${delivery.id}`, {
    method: "PUT", body: JSON.stringify({ archivoIds, version: delivery.version }),
  });
}

export async function findOwnDelivery(taskId: number): Promise<TaskDelivery | null> {
  const page = await api<Page<TaskDelivery>>(`/api/tareas/${taskId}/entregas`);
  return page.content[0] ?? null;
}

export async function downloadTaskFile(file: TaskFile) {
  const blob = await apiBlob(`/api/archivos/${file.id}/descarga`);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = file.nombreOriginal;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

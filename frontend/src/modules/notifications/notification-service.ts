import { api } from "@/core/api/client";

export const NOTIFICATIONS_PATH = "/api/notificaciones";
export const NOTIFICATIONS_CHANGED = "asiscontrol:notificaciones-actualizadas";
export type Notification = {
  id: number;
  titulo: string;
  mensaje: string;
  leida: boolean;
  urlDestino: string | null;
  fechaCreacion: string;
};

export function unreadNotifications() {
  return api<{ noLeidas: number }>(`${NOTIFICATIONS_PATH}/resumen`);
}

export async function markNotificationsRead(id?: number) {
  const action = id === undefined ? "/lectura" : `/${id}/leida`;
  await api(`${NOTIFICATIONS_PATH}${action}`, { method: "PUT" });
  window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED));
}

export function notificationDestination(notice: Notification) {
  const path = notice.urlDestino;
  if (!path || !/^\/[a-z0-9-]+(?:\/[a-z0-9-]+)*$/.test(path)) return null;
  if (/^\/progreso\/evaluaciones\/\d+\/\d+$/.test(path)) {
    return { path: `${path}/${notice.id}`, readOnOpen: false };
  }
  return { path, readOnOpen: true };
}

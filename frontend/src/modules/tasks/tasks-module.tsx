"use client";
import Link from "next/link";
import { Tasks } from "./tasks";
import { TaskDeliveries } from "./task-deliveries";

/** Rutas frontend; no representan nuevos endpoints del backend. */
export function TasksModule({ subpath, canWrite, canUpload }: { subpath: string[]; canWrite: boolean; canUpload: boolean }) {
  if (!subpath.length) return <Tasks canWrite={canWrite} canUpload={canUpload} />;
  const id = Number(subpath[0]);
  if (subpath.length === 2 && subpath[1] === "entregas" && /^\d+$/.test(subpath[0]) && Number.isSafeInteger(id) && id > 0)
    return <TaskDeliveries key={id} taskId={id} canWrite={canWrite} />;
  return <><p>La dirección de seguimiento no es válida.</p><Link href="/tareas">Volver a tareas</Link></>;
}

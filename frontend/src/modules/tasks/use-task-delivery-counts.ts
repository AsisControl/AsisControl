"use client";
import { useEffect, useRef, useState } from "react";
import type { Page } from "@/shared/types/api";
import type { SeguimientoTareaResponse, TareaResponse } from "./types";
import { obtenerSeguimiento } from "./services/entrega-service";

type Counts = Pick<SeguimientoTareaResponse, "entregados" | "totalAlumnos">;

/** Caché de esta pantalla: solo página visible, sin descargar alumnos individualmente. */
export function useTaskDeliveryCounts(data: Page<TareaResponse> | null, loading: boolean, error: string, revision: number) {
  const cache = useRef({ revision, source: data, entries: new Map<string, Promise<Counts | null>>() });
  const [resolved, setResolved] = useState<{
    source: typeof data; revision: number; values: Map<number, Counts | null>;
  } | null>(null);

  useEffect(() => {
    if (!data || loading || error) return;
    if (cache.current.revision !== revision) {
      // Espera al nuevo listado para no consultar filas eliminadas o una página anterior.
      if (cache.current.source === data) return;
      cache.current = { revision, source: data, entries: new Map() };
    } else cache.current.source = data;
    const entries = cache.current.entries;
    const tasks = [...new Map(data.content.filter((task) => task.estado !== "BORRADOR")
      .map((task) => [task.id, task])).values()];
    const values = new Map<number, Counts | null>();
    let active = true;
    let next = 0;
    async function worker() {
      while (active && next < tasks.length) {
        const task = tasks[next++];
        const key = `${task.id}:${task.version}:${task.estado}`;
        let request = entries.get(key);
        if (!request) {
          request = obtenerSeguimiento(task.id).then((value) => value.tareaId === task.id
            ? { entregados: value.entregados, totalAlumnos: value.totalAlumnos } : null)
            .catch(() => null);
          entries.set(key, request);
        }
        const counts = await request;
        if (!active) return;
        values.set(task.id, counts);
        setResolved({ source: data, revision, values: new Map(values) });
      }
    }
    // No hay endpoint por lote: se limita la concurrencia a tres tareas.
    for (let index = 0; index < Math.min(3, tasks.length); index++) void worker();
    return () => { active = false; };
  }, [data, loading, error, revision]);

  return resolved?.source === data && resolved.revision === revision ? resolved.values : null;
}

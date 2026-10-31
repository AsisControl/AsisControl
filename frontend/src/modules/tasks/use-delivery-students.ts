"use client";
import { useEffect, useRef, useState } from "react";
import type { Page } from "@/shared/types/api";
import { obtenerAlumnoResumen } from "./services/entrega-service";
import type { AlumnoResumen, EntregaTareaResponse } from "./types";

/** Caché limitada a esta pantalla: no comparte datos personales entre sesiones. */
export function useDeliveryStudents(data: Page<EntregaTareaResponse> | null) {
  const cache = useRef(new Map<number, Promise<AlumnoResumen | null>>());
  const [resolved, setResolved] = useState<{
    source: typeof data; values: Map<number, AlumnoResumen | null>;
  } | null>(null);
  useEffect(() => {
    if (!data) return;
    let active = true;
    const ids = [...new Set(data.content.map((delivery) => delivery.alumnoId))];
    Promise.all(ids.map(async (id) => {
      let pending = cache.current.get(id);
      if (!pending) {
        pending = obtenerAlumnoResumen(id).then((value) => value.id === id ? value : null).catch(() => {
          // Se permite reintentar los fallos al actualizar o regresar a la página.
          cache.current.delete(id);
          return null;
        });
        cache.current.set(id, pending);
      }
      return [id, await pending] as const;
    })).then((entries) => {
      if (active) setResolved({ source: data, values: new Map(entries) });
    });
    return () => { active = false; };
  }, [data]);
  return resolved?.source === data ? resolved.values : null;
}

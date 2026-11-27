"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { academicYears } from "./lib/academic-year";
import {
  listarEvaluaciones,
  obtenerAnioVigente,
  type EvaluationFilters,
} from "./services/evaluation-service";
import type { Evaluation } from "./types";

type Loaded = {
  key: string;
  items: Evaluation[] | null;
  /** Años vistos en las respuestas: incluyen los de asignaciones ya retiradas. */
  years: number[];
  error: string;
};

/** Todas las evaluaciones que cumplen los filtros del servidor, junto con el año vigente. */
export function useEvaluationList({ periodo, idCurso, idSeccion, estado, busqueda }: EvaluationFilters) {
  const [tick, setTick] = useState(0);
  const key = JSON.stringify([periodo, idCurso, idSeccion, estado, busqueda, tick]);
  const [loaded, setLoaded] = useState<Loaded>({ key: "", items: null, years: [], error: "" });
  const [active, setActive] = useState<{ year: number | null; error: string } | null>(null);
  useEffect(() => {
    let current = true;
    obtenerAnioVigente()
      .then((year) => {
        if (current) setActive({ year, error: "" });
      })
      .catch((e) => {
        if (current) setActive({ year: null, error: message(e) });
      });
    return () => {
      current = false;
    };
  }, []);
  useEffect(() => {
    let current = true;
    listarEvaluaciones({ periodo, idCurso, idSeccion, estado, busqueda })
      .then((items) => {
        if (current)
          setLoaded((previous) => ({
            key,
            items,
            years: academicYears(...previous.years, ...items.map((item) => item.anioAcademico)),
            error: "",
          }));
      })
      .catch((e) => {
        if (current) setLoaded((previous) => ({ ...previous, key, error: message(e) }));
      });
    return () => {
      current = false;
    };
  }, [key, periodo, idCurso, idSeccion, estado, busqueda]);
  return {
    items: loaded.items,
    years: loaded.years,
    error: loaded.key === key ? loaded.error : "",
    activeYear: active?.year ?? null,
    activeYearError: active?.error ?? "",
    /** Ya se conoce el año vigente y respondió el primer listado. */
    ready: active !== null && loaded.key !== "",
    loading: active === null || loaded.key !== key,
    refresh: () => setTick((value) => value + 1),
  };
}

"use client";
import { useEffect, useState } from "react";
import { api, message } from "@/core/api/client";
import { normalizePage } from "@/shared/lib/pagination";
import type { Page } from "@/shared/types/api";
export function usePaged<T>(path: string, query = "", filters = "") {
  const [page, setPage] = useState(0);
  const [tick, setTick] = useState(0);
  const [data, setData] = useState<Page<T> | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  /** Momento de la última respuesta correcta, para "Última actualización". */
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  useEffect(() => {
    let active = true;
    const timer = setTimeout(() => {
      setLoading(true);
      setError("");
      api<Page<T>>(
        `${path}${path.includes("?") ? "&" : "?"}page=${page}&size=20${query ? `&search=${encodeURIComponent(query)}` : ""}${filters ? `&${filters}` : ""}`,
      )
        .then((d) => {
          if (!active) return;
          setData(normalizePage(d));
          setUpdatedAt(new Date());
        })
        .catch((e) => {
          if (active) setError(message(e));
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    }, 0);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [path, query, filters, page, tick]);
  return {
    data,
    error,
    loading,
    updatedAt,
    page,
    setPage,
    refresh: () => setTick((t) => t + 1),
  };
}

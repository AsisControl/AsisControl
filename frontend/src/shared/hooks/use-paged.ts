"use client";
import { useEffect, useState } from "react";
import { api, message } from "@/core/api/client";
import { normalizePage } from "@/shared/lib/pagination";
import type { Page } from "@/shared/types/api";
export function usePaged<T>(path: string, query = "", filters = "", pageSize = 20) {
  const requestKey = JSON.stringify([path, query, filters, pageSize]);
  const [page, setPage] = useState(0);
  const [previousKey, setPreviousKey] = useState(requestKey);
  if (previousKey !== requestKey) {
    setPreviousKey(requestKey);
    setPage(0);
  }
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
        `${path}${path.includes("?") ? "&" : "?"}page=${page}&size=${pageSize}${query ? `&search=${encodeURIComponent(query)}` : ""}${filters ? `&${filters}` : ""}`,
      )
        .then((d) => {
          if (!active) return;
          const result = normalizePage(d);
          const lastPage = Math.max(0, result.totalPages - 1);
          if (page > lastPage) {
            setPage(lastPage);
            return;
          }
          setData(result);
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
  }, [path, query, filters, page, pageSize, tick]);
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

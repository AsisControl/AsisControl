"use client";

import { useEffect, useState, type ReactNode } from "react";
import { message } from "@/core/api/client";
import { Feedback, Loading } from "@/shared/ui";
import styles from "./evaluations.module.css";

/** Montar con una key por contexto y una función load estable para descartar respuestas anteriores. */
export function ReadPanel<T>({ load, children }: { load: () => Promise<T>; children: (data: T) => ReactNode }) {
  const [result, setResult] = useState<{ data: T } | { error: string } | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    load().then(
      (data) => { if (active) setResult({ data }); },
      (error) => { if (active) setResult({ error: message(error) }); },
    );
    return () => { active = false; };
  }, [load, attempt]);
  if (!result) return <Loading>Cargando progreso…</Loading>;
  if ("error" in result) return <div className="panel">
    <Feedback error={result.error} />
    <button type="button" className="secondary-action" onClick={() => {
      setResult(null);
      setAttempt((value) => value + 1);
    }}>Reintentar</button>
  </div>;
  return children(result.data);
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className={`panel ${styles.emptyState}`}>{children}</div>;
}

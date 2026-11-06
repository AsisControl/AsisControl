"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { qrLifetimeMs } from "./lib/format";
import { qrErrorMessage } from "./lib/messages";
import {
  QR_VIGENCIA_SEGUNDOS,
  cancelarQr,
  consultarQr,
  emitirQr,
  renovarQr,
} from "./services/asistencia-docente-service";
import type { CodigoQR } from "./types";

/** Segundos antes del vencimiento en que se pide el código siguiente. */
export const RENEW_MARGIN_SECONDS = 10;
/** Cada cuánto se consulta si el docente ya escaneó el código. */
export const USED_POLL_MS = 3000;

export type DynamicQrStatus = "idle" | "emitting" | "active" | "used" | "error";

/**
 * Mantiene un QR vigente para un docente: lo emite, lo renueva antes de que
 * venza y detecta cuándo fue utilizado. El backend genera y valida el token.
 */
export function useDynamicQr() {
  const [code, setCode] = useState<CodigoQR | null>(null);
  const [status, setStatus] = useState<DynamicQrStatus>("idle");
  const [error, setError] = useState("");
  const [remaining, setRemaining] = useState(0);
  const codeRef = useRef<CodigoQR | null>(null);
  const deadline = useRef(0);
  const busy = useRef(false);

  const activate = useCallback((next: CodigoQR) => {
    const lifetime = qrLifetimeMs(next, QR_VIGENCIA_SEGUNDOS);
    codeRef.current = next;
    deadline.current = Date.now() + lifetime;
    setCode(next);
    setRemaining(Math.ceil(lifetime / 1000));
    setStatus("active");
    setError("");
  }, []);

  /** Cierra el código actual sin esperar: si ya venció o se usó, no importa. */
  const release = useCallback(() => {
    const current = codeRef.current;
    codeRef.current = null;
    if (current) void cancelarQr(current.id).catch(() => undefined);
  }, []);

  const start = useCallback(
    async (docenteId: number) => {
      release();
      busy.current = true;
      setCode(null);
      setError("");
      setStatus("emitting");
      try {
        activate(await emitirQr(docenteId));
      } catch (problem) {
        setStatus("error");
        setError(qrErrorMessage(problem));
      } finally {
        busy.current = false;
      }
    },
    [activate, release],
  );

  const stop = useCallback(() => {
    release();
    setCode(null);
    setError("");
    setStatus("idle");
  }, [release]);

  // Cuenta regresiva y renovación automática antes del vencimiento.
  useEffect(() => {
    if (status !== "active") return;
    const timer = window.setInterval(() => {
      const left = Math.max(0, Math.ceil((deadline.current - Date.now()) / 1000));
      setRemaining(left);
      const current = codeRef.current;
      if (!current || busy.current || left > RENEW_MARGIN_SECONDS) return;
      busy.current = true;
      renovarQr(current.id)
        .then((next) => {
          if (codeRef.current?.id === current.id) activate(next);
        })
        .catch(async (problem) => {
          if (codeRef.current?.id !== current.id) return;
          // El docente pudo escanear justo antes de la renovación.
          const latest = await consultarQr(current.id).catch(() => null);
          if (codeRef.current?.id !== current.id) return;
          codeRef.current = null;
          if (latest?.estado === "UTILIZADO") setStatus("used");
          else {
            setStatus("error");
            setError(qrErrorMessage(problem));
          }
        })
        .finally(() => {
          busy.current = false;
        });
    }, 1000);
    return () => window.clearInterval(timer);
  }, [status, activate]);

  // Detecta que el docente ya marcó con el código mostrado.
  useEffect(() => {
    if (status !== "active") return;
    const timer = window.setInterval(() => {
      const current = codeRef.current;
      if (!current || busy.current) return;
      consultarQr(current.id)
        .then((latest) => {
          if (codeRef.current?.id !== current.id || latest?.estado !== "UTILIZADO") return;
          codeRef.current = null;
          setStatus("used");
        })
        .catch(() => undefined);
    }, USED_POLL_MS);
    return () => window.clearInterval(timer);
  }, [status]);

  // Al salir de la pantalla no se deja un código vigente.
  useEffect(() => release, [release]);

  return { code, status, error, remaining, start, stop };
}

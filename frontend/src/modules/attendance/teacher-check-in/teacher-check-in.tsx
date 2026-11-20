"use client";
import { useCallback, useEffect, useEffectEvent, useState, type FormEvent } from "react";
import { ESTADO_LABEL, VERIFICACION_LABEL, dateOf, timeOf, todayInLima, tokenFromScan } from "./format";
import { checkInErrorMessage } from "./messages";
import { QrScanner } from "./qr-scanner";
import {
  listarMisMarcaciones,
  marcarAsistencia,
} from "./services/asistencia-docente-service";
import type { AsistenciaDocente } from "./types";
import { StatusPill, type StatusTone } from "@/shared/ui";
import styles from "./teacher-attendance.module.css";

const MAX_TOKEN_LENGTH = 500;
const TONOS: Record<string, StatusTone> = {
  PRESENTE: "success",
  TARDANZA: "warning",
  TARDANZA_JUSTIFICADA: "success",
  INASISTENCIA_JUSTIFICADA: "success",
  INASISTENCIA: "danger",
  SIN_MARCACION: "danger",
};

/** US-15: el docente marca su asistencia general escaneando el QR institucional. */
export function TeacherCheckIn({ canWrite }: { canWrite: boolean }) {
  const [scanning, setScanning] = useState(false);
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<AsistenciaDocente | null>(null);
  const [error, setError] = useState("");
  const [existing, setExisting] = useState<AsistenciaDocente | null>(null);
  const [history, setHistory] = useState<AsistenciaDocente[]>([]);
  const [manualCode, setManualCode] = useState("");
  const [manualError, setManualError] = useState("");

  const refreshHistory = useCallback(async () => {
    try {
      const list = await listarMisMarcaciones();
      setHistory(list);
      return list;
    } catch {
      return null;
    }
  }, []);

  useEffect(() => {
    let alive = true;
    listarMisMarcaciones()
      .then((list) => alive && setHistory(list))
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, []);

  async function checkIn(rawToken: string) {
    const token = tokenFromScan(rawToken);
    if (!token || sending) return;
    setScanning(false);
    setSending(true);
    setError("");
    setResult(null);
    setExisting(null);
    try {
      setResult(await marcarAsistencia(token));
      setManualCode("");
      void refreshHistory();
    } catch (problem) {
      setError(checkInErrorMessage(problem));
      // Si ya marcó en esta jornada, se muestra su registro existente.
      const list = await refreshHistory();
      const today = todayInLima();
      setExisting(
        list?.find((item) => item.fechaJornada === today && item.estado !== "SIN_MARCACION") ??
          null,
      );
    } finally {
      setSending(false);
    }
  }

  // Al escanear el QR del punto de marcación con la cámara del celular, el enlace trae el código.
  const checkInLinked = useEffectEvent((token: string) => void checkIn(token));
  useEffect(() => {
    if (!canWrite) return;
    const linked = new URLSearchParams(window.location.search).get("codigo");
    if (!linked) return;
    const timer = window.setTimeout(() => {
      const params = new URLSearchParams(window.location.search);
      params.delete("codigo");
      const rest = params.toString();
      window.history.replaceState(null, "", window.location.pathname + (rest ? `?${rest}` : ""));
      checkInLinked(linked);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [canWrite]);

  function submitManual(event: FormEvent) {
    event.preventDefault();
    if (!manualCode.trim()) {
      setManualError("Ingresa el código que aparece en el punto de marcación.");
      return;
    }
    void checkIn(manualCode);
  }

  return (
    <>
      <div className={styles.header}>
        <h1>Asistencia</h1>
        <p>Marca tu asistencia de la jornada con el QR institucional</p>
      </div>

      {!canWrite ? (
        <article className="role-message">
          <span className="message-mark">!</span>
          <div>
            <p className="eyebrow">MARCACIÓN DOCENTE</p>
            <h2>Tu perfil no está habilitado para marcar asistencia.</h2>
            <p>Comunícate con la administración de la institución.</p>
          </div>
        </article>
      ) : (
        <div className={styles.layout}>
          <section className={`${styles.card} ${styles.block}`} aria-labelledby="marcar-title">
            <h2 id="marcar-title" className={styles.blockTitle}>
              Marcar mi asistencia
            </h2>
            <p className={styles.muted}>
              Puedes marcar desde una hora antes de tu primera clase del día. Hasta 15 minutos antes
              de su inicio se registra Presente; después, Tardanza. La marcación cierra al terminar
              tu última clase. Si no tienes clases programadas, solicita revisión administrativa.
            </p>

            {result && (
              <div role="status" className={`${styles.result} ${styles[result.estado] ?? ""}`}>
                <p className={styles.resultState}>{ESTADO_LABEL[result.estado]}</p>
                <p>
                  Hora registrada: <strong>{timeOf(result.fechaHoraMarcada)}</strong> · Jornada{" "}
                  {dateOf(result.fechaJornada)}
                </p>
                <p className={styles.muted}>La hora la asigna el servidor de la institución.</p>
              </div>
            )}

            {error && (
              <div role="alert" className={styles.errorBox}>
                <p>{error}</p>
                {existing && (
                  <p>
                    Tu registro de hoy: <strong>{ESTADO_LABEL[existing.estado]}</strong> a las{" "}
                    {timeOf(existing.fechaHoraMarcada)}.
                  </p>
                )}
              </div>
            )}

            {sending && <p className={styles.muted}>Registrando tu marcación…</p>}

            {scanning ? (
              <QrScanner onDetected={(text) => void checkIn(text)} onCancel={() => setScanning(false)} />
            ) : (
              <button
                type="button"
                className={`${styles.primary} ${styles.scan}`}
                disabled={sending}
                onClick={() => setScanning(true)}
              >
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M3 7V5a2 2 0 0 1 2-2h2M17 3h2a2 2 0 0 1 2 2v2M21 17v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2M7 12h10" />
                </svg>
                Escanear código QR
              </button>
            )}

            <p className={styles.divider} aria-hidden="true">o</p>
            <form className={styles.manualForm} onSubmit={submitManual} noValidate>
              <label className={styles.field}>
                <span>¿La cámara no funciona? Ingresa el código</span>
                <input
                  className={styles.input}
                  value={manualCode}
                  maxLength={MAX_TOKEN_LENGTH}
                  aria-invalid={manualError ? true : undefined}
                  aria-describedby={manualError ? "codigo-error" : undefined}
                  onChange={(event) => {
                    setManualCode(event.target.value);
                    setManualError("");
                  }}
                />
              </label>
              {manualError && (
                <p id="codigo-error" className={styles.error}>
                  {manualError}
                </p>
              )}
              <button type="submit" className={styles.outline} disabled={sending}>
                Registrar con código
              </button>
            </form>
          </section>

          <section className={`${styles.card} ${styles.historyCard}`} aria-labelledby="historial-title">
            <div className={styles.historyHead}>
              <h2 id="historial-title" className={styles.blockTitle}>
                Mis últimas marcaciones
              </h2>
            </div>
            {history.length === 0 ? (
              <p className={`${styles.muted} ${styles.historyHead}`}>Aún no tienes marcaciones registradas.</p>
            ) : (
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th scope="col">Jornada</th>
                    <th scope="col">Hora</th>
                    <th scope="col">Estado</th>
                    <th scope="col">Clase</th>
                  </tr>
                </thead>
                <tbody>
                  {history.map((item) => (
                    <tr key={item.id}>
                      <td>{dateOf(item.fechaJornada)}</td>
                      <td>{timeOf(item.fechaHoraMarcada)}</td>
                      <td>
                        <StatusPill
                          state={item.estado}
                          label={ESTADO_LABEL[item.estado] ?? item.estado}
                          tone={TONOS[item.estado] ?? "neutral"}
                        />
                      </td>
                      <td>{VERIFICACION_LABEL[item.verificacion] ?? "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </div>
      )}
    </>
  );
}

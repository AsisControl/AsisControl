"use client";
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { ESTADO_LABEL, dateOf, timeOf, todayInLima } from "./format";
import { checkInErrorMessage } from "./messages";
import { QrScanner } from "./qr-scanner";
import {
  listarMisMarcaciones,
  marcarAsistencia,
} from "./services/asistencia-docente-service";
import type { AsistenciaDocente } from "./types";
import styles from "./teacher-check-in.module.css";

const MAX_TOKEN_LENGTH = 500;

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
    const token = rawToken.trim();
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
      <div className="page-title">
        <div>
          <h1>Asistencia</h1>
          <p>Marca tu asistencia de la jornada con el QR institucional</p>
        </div>
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
          <section className={`panel ${styles.card}`} aria-labelledby="marcar-title">
            <h2 id="marcar-title" className={styles.cardTitle}>
              Marcar mi asistencia
            </h2>

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
                className="primary-action"
                disabled={sending}
                onClick={() => setScanning(true)}
              >
                Escanear código QR
              </button>
            )}

            <form className={styles.manualForm} onSubmit={submitManual} noValidate>
              <label className={styles.field}>
                <span>¿La cámara no funciona? Ingresa el código</span>
                <input
                  value={manualCode}
                  maxLength={MAX_TOKEN_LENGTH}
                  aria-invalid={manualError ? true : undefined}
                  aria-describedby={manualError ? "codigo-error" : undefined}
                  className={manualError ? styles.inputError : undefined}
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
              <button type="submit" className={styles.secondaryButton} disabled={sending}>
                Registrar con código
              </button>
            </form>
          </section>

          <section className={`panel ${styles.card}`} aria-labelledby="historial-title">
            <h2 id="historial-title" className={styles.cardTitle}>
              Mis últimas marcaciones
            </h2>
            {history.length === 0 ? (
              <p className={styles.muted}>Aún no tienes marcaciones registradas.</p>
            ) : (
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th scope="col">Jornada</th>
                    <th scope="col">Hora</th>
                    <th scope="col">Estado</th>
                  </tr>
                </thead>
                <tbody>
                  {history.map((item) => (
                    <tr key={item.id}>
                      <td>{dateOf(item.fechaJornada)}</td>
                      <td>{timeOf(item.fechaHoraMarcada)}</td>
                      <td>{ESTADO_LABEL[item.estado] ?? item.estado}</td>
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

"use client";
import { useEffect, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { QR_VIGENCIA_SEGUNDOS } from "./services/asistencia-docente-service";
import { markingLink } from "./format";
import { useDynamicQr } from "./use-dynamic-qr";
import styles from "./teacher-check-in.module.css";

/**
 * Punto de marcación (administración): un único QR para toda la institución, pensado para una
 * pantalla en la entrada. El docente lo escanea con su celular y marca con su propia cuenta.
 */
export function CheckInPoint({ canWrite }: { canWrite: boolean }) {
  const qr = useDynamicQr();
  const [enlarged, setEnlarged] = useState(false);
  const { start } = qr;

  useEffect(() => {
    if (canWrite) void start();
  }, [canWrite, start]);

  useEffect(() => {
    if (!enlarged) return;
    const close = (event: KeyboardEvent) => event.key === "Escape" && setEnlarged(false);
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, [enlarged]);

  if (!canWrite) {
    return (
      <section className={`panel ${styles.card}`}>
        <h2 className={styles.cardTitle}>Punto de marcación</h2>
        <p className={styles.muted}>Tu usuario no tiene permiso para emitir códigos de asistencia.</p>
      </section>
    );
  }

  const link = qr.code ? markingLink(window.location.origin, qr.code.token) : "";

  return (
    <section className={`panel ${styles.card} ${styles.qrCard}`} aria-labelledby="qr-title">
      <h2 id="qr-title" className={styles.cardTitle}>
        Punto de marcación
      </h2>
      {qr.status === "emitting" && <p className={styles.muted}>Generando código…</p>}
      {qr.status === "active" && qr.code && (
        <>
          <div className={styles.qrFrame}>
            <QRCodeSVG value={link} size={512} level="M" marginSize={2} className={styles.qrImage} />
          </div>
          <p className={styles.countdown}>
            Se renueva en <strong>{qr.remaining} s</strong>
          </p>
          <progress
            className={styles.progress}
            max={QR_VIGENCIA_SEGUNDOS}
            value={Math.min(qr.remaining, QR_VIGENCIA_SEGUNDOS)}
            aria-label="Tiempo restante del código"
          />
          <button type="button" className="primary-action" onClick={() => setEnlarged(true)}>
            Ampliar para la entrada
          </button>
          <details className={styles.manual}>
            <summary>Código para ingreso manual</summary>
            <code>{qr.code.token}</code>
          </details>
        </>
      )}
      {qr.status === "error" && (
        <div role="alert" className={styles.errorBox}>
          <p>{qr.error}</p>
          <button type="button" className="primary-action" onClick={() => void start()}>
            Reintentar
          </button>
        </div>
      )}

      {enlarged && qr.code && (
        <div className={styles.entrance} role="dialog" aria-modal="true" aria-label="Código QR de marcación">
          <div className={styles.entranceQr}>
            <QRCodeSVG value={link} size={1024} level="M" marginSize={2} className={styles.qrImage} />
          </div>
          <p className={styles.entranceTitle}>Escanea con la cámara de tu celular</p>
          <p className={styles.entranceHint}>Inicia sesión como docente para registrar tu asistencia</p>
          <p className={styles.entranceHint}>Se renueva en {qr.remaining} s</p>
          <button type="button" className={styles.secondaryButton} onClick={() => setEnlarged(false)}>
            Cerrar
          </button>
        </div>
      )}
    </section>
  );
}

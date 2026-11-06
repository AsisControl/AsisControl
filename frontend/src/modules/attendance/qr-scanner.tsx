"use client";
import { useEffect, useEffectEvent, useRef, useState } from "react";
import jsQR from "jsqr";
import styles from "./attendance.module.css";

type QrScannerProps = {
  onDetected: (text: string) => void;
  onCancel: () => void;
};

/** El navegador solo entrega la cámara en https:// o en localhost. */
function cameraProblem() {
  if (typeof window === "undefined") return "";
  if (window.isSecureContext === false)
    return "La cámara solo funciona en una conexión segura (https:// o localhost). Ingresa el código manualmente.";
  if (!navigator.mediaDevices?.getUserMedia)
    return "Este navegador no permite usar la cámara. Ingresa el código manualmente.";
  return "";
}

/** Lee un QR con la cámara trasera y avisa una sola vez con su contenido. */
export function QrScanner({ onDetected, onCancel }: QrScannerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [problem, setProblem] = useState(cameraProblem);
  const notify = useEffectEvent((text: string) => onDetected(text));

  useEffect(() => {
    if (problem) return;
    let stream: MediaStream | null = null;
    let frame = 0;
    let stopped = false;

    const release = () => {
      cancelAnimationFrame(frame);
      stream?.getTracks().forEach((track) => track.stop());
    };

    const scan = () => {
      if (stopped) return;
      const video = videoRef.current;
      const canvas = canvasRef.current;
      if (video && canvas && video.readyState >= 2 && video.videoWidth > 0) {
        const scale = Math.min(1, 640 / video.videoWidth);
        canvas.width = Math.round(video.videoWidth * scale);
        canvas.height = Math.round(video.videoHeight * scale);
        const context = canvas.getContext("2d", { willReadFrequently: true });
        if (context) {
          context.drawImage(video, 0, 0, canvas.width, canvas.height);
          const image = context.getImageData(0, 0, canvas.width, canvas.height);
          const result = jsQR(image.data, image.width, image.height, {
            inversionAttempts: "dontInvert",
          });
          if (result?.data) {
            stopped = true;
            release();
            notify(result.data);
            return;
          }
        }
      }
      frame = requestAnimationFrame(scan);
    };

    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: "environment" }, audio: false })
      .then(async (opened) => {
        stream = opened;
        const video = videoRef.current;
        if (stopped || !video) {
          release();
          return;
        }
        video.srcObject = opened;
        await video.play().catch(() => undefined);
        frame = requestAnimationFrame(scan);
      })
      .catch((error: unknown) => {
        if (stopped) return;
        setProblem(
          error instanceof DOMException && error.name === "NotAllowedError"
            ? "No diste permiso para usar la cámara. Actívalo en el navegador o ingresa el código manualmente."
            : "No se pudo abrir la cámara. Ingresa el código manualmente.",
        );
      });

    return () => {
      stopped = true;
      release();
    };
  }, [problem]);

  return (
    <div className={styles.scanner}>
      {problem ? (
        <p role="alert" className={styles.error}>
          {problem}
        </p>
      ) : (
        <>
          <video
            ref={videoRef}
            className={styles.video}
            muted
            playsInline
            aria-label="Vista de la cámara para leer el código QR"
          />
          <canvas ref={canvasRef} hidden />
          <p className={styles.muted}>Apunta la cámara al código QR del punto de marcación.</p>
        </>
      )}
      <button type="button" className={styles.secondaryButton} onClick={onCancel}>
        Cerrar cámara
      </button>
    </div>
  );
}

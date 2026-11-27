"use client";
import { useRef, useState } from "react";
import { message } from "./task-error-copy";
import { Feedback } from "@/shared/ui";
import { descargarArchivo } from "./services/archivo-service";
import type { ArchivoResponse } from "./types";
import styles from "./tasks.module.css";

export function TaskDeliveryFiles({ files }: { files: ArchivoResponse[] }) {
  const lock = useRef(new Set<number>());
  const [busy, setBusy] = useState<number[]>([]);
  const [error, setError] = useState("");
  async function download(file: ArchivoResponse) {
    if (lock.current.has(file.id)) return;
    lock.current.add(file.id);
    setBusy([...lock.current]);
    setError("");
    try { await descargarArchivo(file); }
    catch (e) { setError(`No se pudo descargar «${file.nombreOriginal}»: ${message(e)}`); }
    finally {
      lock.current.delete(file.id);
      setBusy([...lock.current]);
    }
  }
  if (!files.length) return <span>Sin adjuntos</span>;
  return <>
    <ul className={styles.fileList}>{files.map((file) => <li key={file.id} className={styles.reviewFile}>
      <span aria-hidden="true">▤</span>
      <span>{file.nombreOriginal}<small>{file.tamanioBytes > 0 ? `${Math.ceil(file.tamanioBytes / 1024)} KB` : file.tipoMime}</small></span>
      <button type="button" className="secondary-action" disabled={busy.includes(file.id)}
        aria-label={`Descargar ${file.nombreOriginal}`} onClick={() => download(file)}>
        {busy.includes(file.id) ? "Descargando…" : "Descargar"}
      </button>
    </li>)}</ul>
    <Feedback error={error} />
  </>;
}

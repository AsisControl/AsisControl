"use client";
import { useRef, useState } from "react";
import { message } from "@/core/api/client";
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
  return <>
    {files.length ? <ul className={styles.fileList}>{files.map((file) => <li key={file.id}>
      <button type="button" className={styles.fileButton} disabled={busy.includes(file.id)}
        aria-label={`Descargar ${file.nombreOriginal}`} onClick={() => download(file)}>
        {busy.includes(file.id) ? "Descargando…" : file.nombreOriginal}
      </button>
    </li>)}</ul> : <span>Sin adjuntos</span>}
    <Feedback error={error} />
  </>;
}

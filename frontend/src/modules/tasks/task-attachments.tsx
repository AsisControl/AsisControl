import type { ArchivoResponse } from "./types";
import { FILE_ACCEPT } from "./task-form-rules";
import styles from "./tasks.module.css";

export type NewAttachment = { file: File; uploaded?: ArchivoResponse };

export function TaskAttachments({ existing, pending, disabled, canUpload, error, onSelect, onRemoveExisting, onRemoveNew }: {
  existing: ArchivoResponse[];
  pending: NewAttachment[];
  disabled: boolean;
  canUpload: boolean;
  error?: string;
  onSelect: (files: File[]) => void;
  onRemoveExisting: (id: number) => void;
  onRemoveNew: (index: number) => void;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const hintId = useId();
  const errorId = useId();
  return <div className={`full-field ${styles.attachments}`}>
    <label className="field">Adjuntos de la tarea
      <input ref={inputRef} className={styles.srOnly} tabIndex={-1} type="file" multiple accept={FILE_ACCEPT} disabled={disabled || !canUpload}
        aria-describedby={`${hintId}${error ? ` ${errorId}` : ""}`} aria-invalid={!!error} onChange={(event) => {
          onSelect(Array.from(event.target.files ?? []));
          event.target.value = "";
          queueMicrotask(() => buttonRef.current?.focus());
        }} />
    </label>
    <button ref={buttonRef} className={styles.addFiles} type="button" disabled={disabled || !canUpload}
      aria-describedby={`${hintId}${error ? ` ${errorId}` : ""}`} onClick={() => inputRef.current?.click()}>Agregar archivos</button>
    <p id={hintId} className="hint">Hasta 20 adjuntos. PDF, imágenes JPEG/PNG y documentos Office; hasta 10 MiB por archivo con la configuración actual.</p>
    {!canUpload && <p className="hint">No tienes permiso para subir archivos nuevos.</p>}
    {error && <p id={errorId} className="form-error" role="alert">{error}</p>}
    <ul>{existing.map((file) => <li key={file.id}>
      <span>{file.nombreOriginal} · Existente</span>
      <button type="button" disabled={disabled} onClick={() => onRemoveExisting(file.id)} aria-label={`Quitar ${file.nombreOriginal}`}>Quitar</button>
    </li>)}{pending.map((item, index) => <li key={index}>
      <span>{item.file.name} · {item.uploaded ? "Subido" : "Pendiente de subir"}</span>
      <button type="button" disabled={disabled} onClick={() => onRemoveNew(index)} aria-label={`Quitar ${item.file.name}`}>Quitar</button>
    </li>)}</ul>
    <p className="hint">Quitar desvincula el adjunto al guardar; no elimina el archivo. Los archivos nuevos ya subidos pueden quedar sin vincular si cancelas.</p>
  </div>;
}
import { useId, useRef } from "react";

import type { ReactNode } from "react";
import styles from "./evaluations.module.css";

function fileType(mime: string, name: string) {
  const extension = name.split(".").pop()?.toLowerCase();
  if (mime === "application/pdf" || extension === "pdf") return ["PDF", "PDF"];
  if (mime.startsWith("image/") || /^(jpe?g|png)$/.test(extension ?? "")) return ["IMG", "Imagen"];
  if (mime.includes("word") || /^docx?$/.test(extension ?? "")) return ["DOC", "Word"];
  if (/excel|spreadsheet/.test(mime) || /^xlsx?$/.test(extension ?? "")) return ["XLS", "Excel"];
  if (/powerpoint|presentation/.test(mime) || /^pptx?$/.test(extension ?? "")) return ["PPT", "PowerPoint"];
  return ["ARC", mime || "Archivo"];
}

export function EvidenceFileCard({ name, mime, bytes, children }: {
  name: string;
  mime: string;
  bytes: number;
  children?: ReactNode;
}) {
  const [icon, type] = fileType(mime, name);
  const units = bytes >= 1024 * 1024 ? "MiB" : bytes >= 1024 ? "KiB" : "B";
  const size = bytes / (units === "MiB" ? 1024 * 1024 : units === "KiB" ? 1024 : 1);
  return <div className={styles.evidenceFileCard}>
    <span className={styles.evidenceFileIcon} aria-hidden="true">{icon}</span>
    <div className={styles.evidenceFileInfo}>
      <strong>{name}</strong>
      <span>{type} · {new Intl.NumberFormat("es-PE", { maximumFractionDigits: 1 }).format(size)} {units}</span>
    </div>
    {children && <div className={styles.evidenceFileActions}>{children}</div>}
  </div>;
}

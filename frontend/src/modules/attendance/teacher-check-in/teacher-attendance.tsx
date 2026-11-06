"use client";
import Link from "next/link";
import { JustificationInbox } from "../justification-inbox";
import { TeacherCheckIn } from "./teacher-check-in";
import styles from "./teacher-check-in.module.css";

/** Pestañas de Asistencia del docente: /asistencia y /asistencia/mi-marcacion. */
const TABS = [
  { slug: "", label: "Justificaciones" },
  { slug: "mi-marcacion", label: "Mi marcación" },
] as const;

/**
 * Asistencia del docente: la bandeja de justificaciones (US-14) se mantiene en
 * /asistencia y la marcación con QR (US-15) está en /asistencia/mi-marcacion.
 */
export function TeacherAttendance({ tab, canCheckIn }: { tab?: string; canCheckIn: boolean }) {
  const current = tab === "mi-marcacion" ? "mi-marcacion" : "";
  return (
    <>
      <nav aria-label="Secciones de asistencia" className={styles.tabs}>
        {TABS.map((item) => (
          <Link
            key={item.slug}
            href={item.slug ? `/asistencia/${item.slug}` : "/asistencia"}
            aria-current={current === item.slug ? "page" : undefined}
            className={styles.tab}
          >
            {item.label}
          </Link>
        ))}
      </nav>
      {current === "mi-marcacion" ? <TeacherCheckIn canWrite={canCheckIn} /> : <JustificationInbox />}
    </>
  );
}

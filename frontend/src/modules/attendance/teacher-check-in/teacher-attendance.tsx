"use client";
import Link from "next/link";
import { JustificationInbox } from "../justification-inbox";
import { TeacherCheckIn } from "./teacher-check-in";
import { TeacherContingencies } from "./teacher-contingencies";
import styles from "./teacher-attendance.module.css";

const TABS = [
  { slug: "", label: "Justificaciones" },
  { slug: "mi-marcacion", label: "Mi marcación" },
  { slug: "contingencias", label: "Contingencias de marcación" },
] as const;

/**
 * Asistencia del docente: la bandeja de justificaciones (US-14) se mantiene en
 * /asistencia y la marcación con QR (US-15) está en /asistencia/mi-marcacion.
 */
export function TeacherAttendance({ tab, canCheckIn }: { tab?: string; canCheckIn: boolean }) {
  const current = TABS.find((item) => item.slug === tab)?.slug ?? "";
  return (
    <div className={styles.shell}>
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
      {current === "mi-marcacion" && <TeacherCheckIn canWrite={canCheckIn} />}
      {current === "contingencias" && <TeacherContingencies canWrite={canCheckIn} />}
      {!current && <JustificationInbox />}
    </div>
  );
}

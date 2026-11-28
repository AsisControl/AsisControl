"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { CheckInPoint } from "./check-in-point";
import { todayInLima } from "./format";
import { resumenMarcaciones } from "./services/asistencia-docente-service";
import { TeacherCheckInReview } from "./teacher-check-in-review";
import { TeacherContingencies } from "./teacher-contingencies";
import type { ResumenMarcacion } from "./types";
import styles from "./teacher-check-in.module.css";
import { TabNav } from "@/shared/ui";
import { AttendanceReports } from "../reports/attendance-reports";

/** Cada cuánto se refrescan el resumen y la tabla mientras la pantalla está abierta. */
export const REFRESH_MS = 30000;

/** Asistencia docente (administración): resumen del día, marcaciones y punto de marcación con QR. */
export function AttendanceAdmin({ canWrite }: { canWrite: boolean }) {
  const [tab, setTab] = useState("marcacion");
  return <>
    <div className="page-title"><div><h1>Asistencia</h1><p>Marcación docente e informes institucionales</p></div></div>
    <TabNav label="Secciones de asistencia" current={tab} onSelect={setTab} tabs={[
      { id: "marcacion", label: "Marcación docente" },
      { id: "contingencias", label: "Solicitudes de contingencia" },
      { id: "reportes", label: "Reportes de asistencia" },
    ]} />
    {tab === "reportes" && <AttendanceReports canCorrect={canWrite} />}
    {tab === "contingencias" && <TeacherContingencies administration canWrite={canWrite} />}
    {tab === "marcacion" && <TeacherMarkingAdmin canWrite={canWrite} />}
  </>;
}

function TeacherMarkingAdmin({ canWrite }: { canWrite: boolean }) {
  const [day, setDay] = useState(todayInLima);
  const [refreshKey, setRefreshKey] = useState(0);
  const [summary, setSummary] = useState<ResumenMarcacion | null>(null);
  const [summaryError, setSummaryError] = useState("");

  useEffect(() => {
    const timer = window.setInterval(() => setRefreshKey((key) => key + 1), REFRESH_MS);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    let alive = true;
    resumenMarcaciones(day)
      .then((data) => {
        if (!alive) return;
        setSummary(data);
        setSummaryError("");
      })
      .catch((problem) => alive && setSummaryError(message(problem)));
    return () => {
      alive = false;
    };
  }, [day, refreshKey]);

  return (
    <>
      {summaryError && <p role="alert" className={styles.error}>{summaryError}</p>}
      <div className={styles.summary} aria-label="Resumen del día">
        <div className={styles.metric}>
          <span>Marcaron</span>
          <strong>{summary ? `${summary.marcaron} de ${summary.totalDocentes}` : "—"}</strong>
        </div>
        <div className={styles.metric}>
          <span>Pendientes de confirmación</span>
          <strong className={styles.pendingNumber}>{summary ? summary.pendientesConfirmacion : "—"}</strong>
        </div>
        <div className={styles.metric}>
          <span>Sin marcar</span>
          <strong>{summary ? summary.sinMarcar : "—"}</strong>
        </div>
      </div>

      <div className={styles.adminLayout}>
        <TeacherCheckInReview day={day} onDayChange={setDay} refreshKey={refreshKey} />
        <CheckInPoint canWrite={canWrite} />
      </div>
    </>
  );
}

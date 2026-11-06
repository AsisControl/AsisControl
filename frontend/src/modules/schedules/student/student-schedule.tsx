"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback, PageHeader, PanelHeading } from "@/shared/ui";
import { countLabel } from "@/shared/lib/formatters";
import type { ScheduleBlock } from "../types";
import styles from "../schedules.module.css";
import { BlockDetailDialog } from "../timetable/block-detail-dialog";
import { TimetableGrid } from "../timetable/timetable-grid";
import { listAll, listarHorariosPropios } from "../timetable/services/horarioService";

type Enrollment = {
  id: number;
  seccionId: number;
  seccion: string;
  grado: number;
  anioAcademico: number;
  estado: string;
};

/** Horario de las secciones donde el estudiante tiene una matrícula activa. */
export function StudentSchedule() {
  const [enrollments, setEnrollments] = useState<Enrollment[] | null>(null);
  const [selectedYear, setSelectedYear] = useState<number | null>(null);
  const [blocks, setBlocks] = useState<ScheduleBlock[] | null>(null);
  const [setupError, setSetupError] = useState("");
  const [loadError, setLoadError] = useState("");
  const [tick, setTick] = useState(0);
  const [detail, setDetail] = useState<ScheduleBlock | null>(null);

  useEffect(() => {
    let active = true;
    listAll<Enrollment>("/api/matriculas")
      .then((loaded) => {
        if (!active) return;
        setEnrollments(loaded.filter((item) => item.estado === "ACTIVA"));
        setSetupError("");
      })
      .catch((error) => {
        if (active) setSetupError(message(error));
      });
    return () => { active = false; };
  }, [tick]);

  const years = [...new Set((enrollments ?? []).map((item) => item.anioAcademico))]
    .sort((a, b) => b - a);
  const currentYear = new Date().getFullYear();
  const year = selectedYear ?? (years.includes(currentYear) ? currentYear : (years[0] ?? null));

  useEffect(() => {
    if (year === null) return;
    let active = true;
    listarHorariosPropios(year)
      .then((loaded) => {
        if (!active) return;
        setBlocks(loaded);
        setLoadError("");
      })
      .catch((error) => {
        if (active) setLoadError(message(error));
      });
    return () => { active = false; };
  }, [year, tick]);

  const sectionOf = (block: ScheduleBlock) => {
    const enrollment = enrollments?.find((item) => item.seccionId === block.seccionId);
    return enrollment ? `${enrollment.grado}° ${enrollment.seccion}` : block.seccion;
  };
  const courses = new Set((blocks ?? []).map((block) => block.cursoId)).size;
  return (
    <section>
      <PageHeader title="Mi horario" description="Cursos de tu sección durante la semana." />
      <div className="panel">
        <PanelHeading
          title="Horario semanal"
          summary={blocks
            ? `${countLabel(blocks.length, "bloque de clase", "bloques de clase")} · ${countLabel(courses, "curso", "cursos")}`
            : "Solo de consulta"}
          actions={<button type="button" onClick={() => setTick((value) => value + 1)}>Actualizar</button>}
        />
        <Feedback error={setupError || loadError} />
        {enrollments === null ? (
          !setupError && <p role="status">Cargando tu horario…</p>
        ) : !enrollments.length ? (
          <p className="empty">No tienes una matrícula activa con horario disponible.</p>
        ) : (
          <>
            {years.length > 1 && (
              <div className={styles.filters}>
                <label className="field">
                  Año académico
                  <select value={year ?? ""} onChange={(event) => {
                    setBlocks(null);
                    setSelectedYear(Number(event.target.value));
                  }}>
                    {years.map((value) => <option key={value} value={value}>{value}</option>)}
                  </select>
                </label>
              </div>
            )}
            {!blocks ? (
              !loadError && <p role="status">Cargando tu horario…</p>
            ) : (
              <TimetableGrid
                blocks={blocks}
                label="Mi horario semanal"
                emptyMessage={`Aún no hay bloques de horario para tu sección en ${year}.`}
                sectionOf={sectionOf}
                onOpen={setDetail}
              />
            )}
          </>
        )}
      </div>
      {detail && (
        <BlockDetailDialog block={detail} section={`${sectionOf(detail)} · ${year}`}
          onClose={() => setDetail(null)} />
      )}
    </section>
  );
}

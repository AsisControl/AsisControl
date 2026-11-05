"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback, PageHeader, PanelHeading } from "@/shared/ui";
import { countLabel } from "@/shared/lib/formatters";
import type { CourseAssignment, ScheduleBlock } from "../types";
import styles from "../schedules.module.css";
import { BlockDetailDialog } from "../timetable/block-detail-dialog";
import { TimetableGrid } from "../timetable/timetable-grid";
import { listarAsignacionesPropias, listarHorarios } from "../timetable/services/horarioService";
const assignmentSection = (item: CourseAssignment) => `${item.grado}° ${item.seccion}`;
/**
 * Horario del docente autenticado, solo de consulta. Sus asignaciones identifican al docente y
 * sus años; la matriz no ofrece acciones de edición y el backend exige ACADEMICO_ESCRIBIR para ellas.
 */
export function TeacherSchedule() {
  const [assignments, setAssignments] = useState<CourseAssignment[] | null>(null);
  const [selectedYear, setSelectedYear] = useState<number | null>(null);
  const [blocks, setBlocks] = useState<ScheduleBlock[] | null>(null);
  const [setupError, setSetupError] = useState("");
  const [loadError, setLoadError] = useState("");
  const [tick, setTick] = useState(0);
  const [detail, setDetail] = useState<ScheduleBlock | null>(null);
  useEffect(() => {
    let active = true;
    listarAsignacionesPropias()
      .then((loaded) => {
        if (active) setAssignments(loaded);
      })
      .catch((e) => {
        if (active) setSetupError(message(e));
      });
    return () => {
      active = false;
    };
  }, []);
  const years = [...new Set((assignments ?? []).map((item) => item.anioAcademico))].sort((a, b) => b - a);
  const currentYear = new Date().getFullYear();
  const year = selectedYear ?? (years.includes(currentYear) ? currentYear : (years[0] ?? null));
  const docenteId = assignments?.[0]?.docenteId ?? null;
  useEffect(() => {
    if (docenteId === null || year === null) return;
    let active = true;
    listarHorarios({ anioAcademico: year, docenteId })
      .then((loaded) => {
        if (!active) return;
        setBlocks(loaded);
        setLoadError("");
      })
      .catch((e) => {
        if (active) setLoadError(message(e));
      });
    return () => {
      active = false;
    };
  }, [docenteId, year, tick]);
  const blockSection = (block: ScheduleBlock) => {
    const found = assignments?.find((item) => item.seccionId === block.seccionId);
    return found ? assignmentSection(found) : block.seccion;
  };
  const courses = new Set((blocks ?? []).map((block) => block.cursoId)).size;
  return (
    <section>
      <PageHeader title="Mi horario" description="Clases que tienes asignadas durante la semana." />
      <div className="panel">
        <PanelHeading
          title="Horario semanal"
          summary={
            blocks
              ? `${countLabel(blocks.length, "bloque de clase", "bloques de clase")} · ${countLabel(courses, "curso", "cursos")}`
              : "Solo de consulta"
          }
          actions={
            <button
              type="button"
              disabled={docenteId === null}
              onClick={() => setTick((value) => value + 1)}
            >
              Actualizar
            </button>
          }
        />
        <Feedback error={setupError || loadError} />
        {assignments === null ? (
          !setupError && <p role="status">Cargando tu horario…</p>
        ) : !assignments.length ? (
          <p className="empty">Aún no tienes cursos asignados. Consulta con la coordinación académica.</p>
        ) : (
          <>
            {years.length > 1 && (
              <div className={styles.filters}>
                <label className="field">
                  Año académico
                  <select
                    value={year ?? ""}
                    onChange={(e) => {
                      setBlocks(null);
                      setSelectedYear(Number(e.target.value));
                    }}
                  >
                    {years.map((value) => (
                      <option key={value} value={value}>
                        {value}
                      </option>
                    ))}
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
                emptyMessage={`Aún no tienes bloques de horario asignados en ${year}.`}
                sectionOf={blockSection}
                showTeacher={false}
                onOpen={setDetail}
              />
            )}
          </>
        )}
      </div>
      {detail && (
        <BlockDetailDialog
          block={detail}
          section={`${blockSection(detail)} · ${year}`}
          onClose={() => setDetail(null)}
        />
      )}
    </section>
  );
}

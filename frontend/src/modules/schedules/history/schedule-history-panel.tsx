"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback, PanelHeading, Pager, StatusPill } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import {
  countLabel,
  dateLabel,
  roleLabel,
  stateLabel,
  timeLabel,
} from "@/shared/lib/formatters";
import { sectionLabel } from "../assignments/assignment-form-dialog";
import { docentesActivos } from "../assignments/services/asignacionService";
import { listarSeccionesActivas } from "../timetable/services/horarioService";
import type {
  Classroom,
  ScheduleHistoryEntry,
  ScheduleSection,
  TeacherOption,
} from "../types";
import styles from "../schedules.module.css";
import {
  changedFields,
  fieldLabel,
  fieldValue,
  historyAction,
  historySubject,
} from "./history-format";
import {
  HISTORIAL_PATH,
  historyFilters,
  listarTodasLasAulas,
  type ScheduleHistoryFilters,
} from "./services/historialHorarioService";
const NO_FILTERS: ScheduleHistoryFilters = {
  anioAcademico: "",
  seccionId: "",
  docenteId: "",
  aulaId: "",
  desde: "",
  hasta: "",
};
function HistoryChanges({ entry }: { entry: ScheduleHistoryEntry }) {
  const changes = changedFields(entry);
  if (!changes.length) return <span className={styles.historyMuted}>Sin cambios en los datos</span>;
  return (
    <ul className={styles.historyChanges}>
      {changes.map(({ campo, anterior, nuevo }) => (
        <li key={campo}>
          <strong>{fieldLabel(campo)}:</strong>{" "}
          <span className={styles.historyBefore}>{fieldValue(campo, anterior)}</span>
          {" → "}
          <span>{fieldValue(campo, nuevo)}</span>
        </li>
      ))}
    </ul>
  );
}
/** Pestaña Historial de cambios (US-27): bloques de horario y asignaciones docentes. */
export function ScheduleHistoryPanel() {
  const [filters, setFilters] = useState(NO_FILTERS);
  const history = usePaged<ScheduleHistoryEntry>(
    HISTORIAL_PATH,
    "",
    historyFilters(filters),
  );
  const [sections, setSections] = useState<ScheduleSection[]>([]);
  const [teachers, setTeachers] = useState<TeacherOption[]>([]);
  const [classrooms, setClassrooms] = useState<Classroom[]>([]);
  const [optionsError, setOptionsError] = useState("");
  useEffect(() => {
    let active = true;
    Promise.all([listarSeccionesActivas(), docentesActivos(), listarTodasLasAulas()])
      .then(([sectionList, teacherList, classroomList]) => {
        if (!active) return;
        setSections(sectionList);
        setTeachers(teacherList);
        setClassrooms(classroomList);
      })
      .catch((error) => {
        if (active) setOptionsError(message(error));
      });
    return () => {
      active = false;
    };
  }, []);
  function applyFilters(changes: Partial<ScheduleHistoryFilters>) {
    setFilters((current) => ({ ...current, ...changes }));
    history.setPage(0);
  }
  const yearSections = sections.filter(
    (section) =>
      !filters.anioAcademico || String(section.anioAcademico) === filters.anioAcademico,
  );
  const rows = history.data?.content ?? [];
  const total = history.data?.totalElements ?? 0;
  return (
    <section className="panel">
      <PanelHeading
        title="Historial de cambios"
        summary={
          <>
            {history.data
              ? countLabel(total, "cambio", "cambios")
              : "Quién cambió los horarios y las asignaciones docentes"}
            {history.updatedAt &&
              ` · Última actualización: ${timeLabel(history.updatedAt)}`}
          </>
        }
        actions={
          <button type="button" disabled={history.loading} onClick={history.refresh}>
            Actualizar
          </button>
        }
      />
      <div className={styles.filters}>
        <input
          type="number"
          aria-label="Filtrar por año académico"
          placeholder="Año académico"
          min={2000}
          max={2100}
          value={filters.anioAcademico}
          onChange={(e) => applyFilters({ anioAcademico: e.target.value, seccionId: "" })}
        />
        <select
          aria-label="Filtrar por sección"
          value={filters.seccionId}
          onChange={(e) => applyFilters({ seccionId: e.target.value })}
        >
          <option value="">Todas las secciones</option>
          {yearSections.map((section) => (
            <option key={section.id} value={section.id}>
              {sectionLabel(section.grado, section.nombre)} · {section.anioAcademico}
            </option>
          ))}
        </select>
        <select
          aria-label="Filtrar por docente"
          value={filters.docenteId}
          onChange={(e) => applyFilters({ docenteId: e.target.value })}
        >
          <option value="">Todos los docentes</option>
          {teachers.map((teacher) => (
            <option key={teacher.id} value={teacher.id}>
              {teacher.persona.nombreCompleto}
            </option>
          ))}
        </select>
        <select
          aria-label="Filtrar por aula"
          value={filters.aulaId}
          onChange={(e) => applyFilters({ aulaId: e.target.value })}
        >
          <option value="">Todas las aulas</option>
          {classrooms.map((classroom) => (
            <option key={classroom.id} value={classroom.id}>
              {classroom.nombre}
            </option>
          ))}
        </select>
        <input
          type="date"
          aria-label="Desde la fecha"
          value={filters.desde}
          onChange={(e) => applyFilters({ desde: e.target.value })}
        />
        <input
          type="date"
          aria-label="Hasta la fecha"
          value={filters.hasta}
          onChange={(e) => applyFilters({ hasta: e.target.value })}
        />
      </div>
      <Feedback error={history.error || optionsError} />
      {history.loading ? (
        <p role="status">Cargando historial…</p>
      ) : rows.length ? (
        <div className={styles.tableWrap}>
          <table className={`user-table ${styles.table}`}>
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Usuario</th>
                <th>Acción</th>
                <th>Registro</th>
                <th>Valor anterior → nuevo</th>
                <th>Motivo</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((entry) => {
                const action = historyAction(entry.accion);
                return (
                  <tr key={entry.id}>
                    <td data-label="Fecha">{dateLabel(entry.fecha)}</td>
                    <td data-label="Usuario">
                      <strong>{entry.actorNombre ?? entry.actorIdentificador}</strong>
                      {entry.rolActor && (
                        <small className={styles.historyMuted}>{roleLabel(entry.rolActor)}</small>
                      )}
                    </td>
                    <td data-label="Acción">
                      <StatusPill
                        state={entry.accion}
                        label={action?.label ?? stateLabel(entry.accion)}
                        tone={action?.tone}
                      />
                    </td>
                    <td data-label="Registro">{historySubject(entry) || "—"}</td>
                    <td data-label="Valor anterior → nuevo">
                      <HistoryChanges entry={entry} />
                    </td>
                    <td data-label="Motivo">{entry.motivo ?? "—"}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : (
        !history.error && (
          <p role="status">No hay cambios registrados con estos filtros.</p>
        )
      )}
      <Pager data={history.data} loading={history.loading} setPage={history.setPage} />
    </section>
  );
}

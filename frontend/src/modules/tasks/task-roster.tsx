"use client";
import { useEffect, useState, type ReactNode } from "react";
import { Loading, Pager } from "@/shared/ui";
import { message } from "./task-error-copy";
import { obtenerSeguimiento } from "./services/entrega-service";
import type { SeguimientoAlumno, SeguimientoTareaResponse } from "./types";
import styles from "./tasks.module.css";

const PAGE_SIZE = 20;
const deliveryLabels = { PENDIENTE: "Sin entrega", ENTREGADO: "Entregado", ENTREGADO_TARDE: "Entregado tarde",
  CALIFICADO: "Entregado", ANULADO: "Anulado" };
const normalize = (text: string) => text.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();

function gradeState(student: SeguimientoAlumno) {
  if (student.totalCompetencias > 0) {
    if (student.competenciasCalificadas === student.totalCompetencias) return "graded";
    if (student.competenciasCalificadas > 0) return "partial";
  }
  return student.nota != null || student.nivelLogro != null ? "graded" : "ungraded";
}

export function TaskRoster({ taskId, reload, maximo, canGradePractice, canGradeWithoutDelivery, onReview, onGrade, fallback }: {
  taskId: number; reload: number; maximo: number; canGradePractice: boolean;
  canGradeWithoutDelivery: boolean;
  onReview: (student: SeguimientoAlumno) => void; onGrade: (student: SeguimientoAlumno) => void; fallback: ReactNode;
}) {
  const [data, setData] = useState<SeguimientoTareaResponse | null>(null);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [delivery, setDelivery] = useState("all");
  const [grade, setGrade] = useState("all");
  const [page, setPage] = useState(0);
  useEffect(() => {
    let active = true;
    obtenerSeguimiento(taskId).then(value => {
      if (active) { setData(value); setError(""); }
    }).catch(reason => { if (active) setError(message(reason)); });
    return () => { active = false; };
  }, [taskId, reload]);

  if (error) return <><p className="hint">No se pudo cargar el padrón de la sección: {error}</p>{fallback}</>;
  if (!data) return <Loading variant="inline">Cargando el padrón de la sección…</Loading>;
  const { totalAlumnos, entregados, calificados, pendientes, alumnos } = data;
  const filtered = alumnos.filter(student => (student.matriculaActiva || student.entregaId != null)
    && normalize(`${student.nombreCompleto} ${student.codigoAlumno ?? ""}`).includes(normalize(search.trim()))
    && (delivery === "all" || (delivery === "delivered" ? student.entregaId != null && student.estado !== "ANULADO"
      : delivery === "late" ? student.estado === "ENTREGADO_TARDE" : student.entregaId == null))
    && (grade === "all" || gradeState(student) === grade));
  const totalPages = Math.ceil(filtered.length / PAGE_SIZE);
  const index = Math.min(page, Math.max(0, totalPages - 1));
  const content = filtered.slice(index * PAGE_SIZE, (index + 1) * PAGE_SIZE);
  const selectDelivery = (filter: string) => { setDelivery(filter); setPage(0); };
  return <div className={styles.roster} role="region" aria-label="Padrón de la sección">
    <div className={styles.rosterSummary}>
      <button type="button" aria-pressed={delivery === "all"} onClick={() => selectDelivery("all")}>Total: {totalAlumnos}</button>
      <button type="button" aria-pressed={delivery === "delivered"} onClick={() => selectDelivery("delivered")}>{entregados} de {totalAlumnos} estudiantes entregaron</button>
      <button type="button" aria-pressed={delivery === "missing"} onClick={() => selectDelivery("missing")}>Sin entrega ({pendientes})</button>
      <span>{calificados} {calificados === 1 ? "calificado" : "calificados"}</span>
    </div>
    <div className={styles.rosterFilters}>
      <label className="field">Buscar estudiante<input type="search" placeholder="Nombre o código" value={search}
        onChange={event => { setSearch(event.target.value); setPage(0); }} /></label>
      <label className="field">Estado de entrega<select value={delivery} onChange={event => selectDelivery(event.target.value)}>
        <option value="all">Todos</option><option value="delivered">Entregados</option>
        <option value="missing">Sin entrega</option><option value="late">Entregados tarde</option>
      </select></label>
      <label className="field">Estado de calificación<select value={grade} onChange={event => { setGrade(event.target.value); setPage(0); }}>
        <option value="all">Todos</option><option value="graded">Calificados</option>
        <option value="ungraded">Sin calificar</option><option value="partial">Calificación parcial</option>
      </select></label>
    </div>
    <p role="status">{filtered.length} {filtered.length === 1 ? "estudiante encontrado" : "estudiantes encontrados"}</p>
    {content.length ? <div className={styles.tableWrap}>
      <table className={`user-table ${styles.table} ${styles.deliveryTable}`}>
        <thead><tr><th>Estudiante</th><th>Entrega</th><th>Presentación</th><th>Contenido</th><th>Calificación</th><th>Acciones</th></tr></thead>
        <tbody>{content.map(student => {
          const summary = [student.tieneRespuesta ? "Respuesta escrita" : "",
            student.cantidadArchivos ? `${student.cantidadArchivos} ${student.cantidadArchivos === 1 ? "archivo" : "archivos"}` : ""]
            .filter(Boolean).join(" · ");
          return <tr key={student.alumnoId}>
            <td data-label="Estudiante"><strong>{student.nombreCompleto}</strong><small className={styles.taskContext}>{student.codigoAlumno}</small></td>
            <td data-label="Entrega">{deliveryLabels[student.estado]}</td>
            <td data-label="Presentación">{student.fechaHoraEntrega ? <time dateTime={student.fechaHoraEntrega}>{student.fechaHoraEntrega.replace("T", " ").slice(0, 16)}</time> : "—"}</td>
            <td data-label="Contenido">{summary || "—"}</td>
            <td data-label="Calificación">{student.competenciasCalificadas > 0
              ? `${student.competenciasCalificadas} de ${student.totalCompetencias} competencias calificadas`
              : student.nivelLogro ?? (student.nota == null ? "Sin calificar" : `${student.nota} / ${maximo}`)}</td>
            <td data-label="Acciones">
              {student.entregaId != null && <button type="button" onClick={() => onReview(student)}>Ver entrega</button>}
              {student.matriculaActiva && student.entregaId == null && student.estado === "PENDIENTE" && (canGradeWithoutDelivery || gradeState(student) !== "ungraded") &&
                <button type="button" onClick={() => onReview(student)} aria-label={`${canGradeWithoutDelivery ? "Calificar" : "Ver notas de"} ${student.nombreCompleto}`}>
                  {canGradeWithoutDelivery ? "Calificar" : "Ver notas"}
                </button>}
              {canGradePractice && student.entregaId != null && student.estado !== "ANULADO" &&
                <button type="button" onClick={() => onGrade(student)} aria-label={`Calificar ${student.nombreCompleto}`}>Calificar</button>}
            </td>
          </tr>;
        })}</tbody>
      </table>
    </div> : <div className={styles.emptyRoster}><p>No hay estudiantes para estos filtros.</p>
      {(search || delivery !== "all" || grade !== "all") && <button type="button" className="secondary-action"
        onClick={() => { setSearch(""); setDelivery("all"); setGrade("all"); setPage(0); }}>Limpiar filtros</button>}
    </div>}
    {totalPages > 1 && <Pager data={{ content, totalElements: filtered.length, page: index, totalPages,
      first: index === 0, last: index + 1 === totalPages }} loading={false} setPage={setPage} />}
  </div>;
}

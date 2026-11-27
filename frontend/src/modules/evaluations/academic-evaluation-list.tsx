"use client";

import { useState } from "react";
import { usePaged } from "@/shared/hooks/use-paged";
import { Feedback, Loading, Pager } from "@/shared/ui";
import { EvaluationCells, EvaluationHeaders } from "./evaluation-cells";
import { GradeSheet } from "./grade-sheet";
import type { Evaluation } from "./types";
import styles from "./evaluations.module.css";

type Row = { evaluacion: Evaluation; registradas: number; esperadas: number };

export function AcademicEvaluationList({ query }: { query: string }) {
  const list = usePaged<Row>("/api/evaluaciones/academicas", "", query);
  const [selected, setSelected] = useState<number | null>(null);
  if (list.loading) return <Loading>Cargando evaluaciones.</Loading>;
  if (list.error) return <Feedback error={list.error} />;
  if (!list.data?.content.length) return <p className="empty">No hay evaluaciones con estos filtros.</p>;
  return <>
    <div className="user-table-wrap">
      <table className={`user-table ${styles.evaluationTable}`} aria-label="Evaluaciones del curso">
        <thead><tr><EvaluationHeaders /><th scope="col">Avance de calificación</th><th scope="col">Detalle</th></tr></thead>
        <tbody>{list.data.content.map(({ evaluacion, registradas, esperadas }) => <tr key={evaluacion.id}>
          <EvaluationCells item={evaluacion} />
          <td data-label="Avance de calificación">{esperadas > 0 ? <>
            <strong>{Math.round(100 * registradas / esperadas)} %</strong>
            <small>{registradas} de {esperadas} calificaciones</small>
          </> : "Sin calificaciones previstas"}</td>
          <td data-label="Detalle"><button type="button" className="secondary-action"
            aria-label={`Ver planilla de ${evaluacion.titulo}`} aria-expanded={selected === evaluacion.id}
            onClick={() => setSelected(selected === evaluacion.id ? null : evaluacion.id)}>
            {selected === evaluacion.id ? "Cerrar" : "Ver planilla"}
          </button></td>
        </tr>)}</tbody>
      </table>
    </div>
    <Pager data={list.data} loading={list.loading} setPage={page => { setSelected(null); list.setPage(page); }} />
    {selected !== null && <GradeSheet key={selected} evaluationId={selected} competencyId={null} canWrite={false} />}
  </>;
}

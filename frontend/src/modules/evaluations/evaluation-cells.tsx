import { StatusPill } from "@/shared/ui";
import { evaluationDate, PERIODS, STATE_TONES } from "./lib/labels";
import type { Evaluation } from "./types";
import styles from "./evaluations.module.css";

export const stateName = (state: string) => state.charAt(0) + state.slice(1).toLowerCase();

export function EvaluationHeaders() {
  return <>{["Evaluación", "Curso", "Sección", "Fecha", "Competencias", "Componente y peso", "Estado"].map(label =>
    <th key={label} scope="col">{label}</th>)}</>;
}

export function EvaluationCells({ item }: { item: Evaluation }) {
  return <>
    <td data-label="Evaluación">
      <strong>{item.titulo}</strong>
      <small>{PERIODS[item.periodoEvaluacion]}</small>
    </td>
    <td data-label="Curso">{item.curso}</td>
    <td data-label="Sección">
      <strong>{item.seccion}</strong>
    </td>
    <td data-label="Fecha" className={styles.evaluationDate}>{evaluationDate(item.fechaEvaluacion)}</td>
    <td data-label="Competencias">
      <span className={styles.count}>{item.competencias.length}</span>{" "}
      {item.competencias.length === 1 ? "competencia" : "competencias"}
    </td>
    <td data-label="Componente y peso">
      {item.pesoComponente != null
        ? <>{item.componente}<br /><small>Peso del componente: {item.pesoComponente} %</small></>
        : item.reglaVigente ? "Sin peso" : "Sin regla vigente"}
    </td>
    <td data-label="Estado">
      <StatusPill
        state={item.estado}
        label={item.idTareaVinculada && item.estado === "BORRADOR" ? "Calificaciones en borrador" : stateName(item.estado)}
        tone={STATE_TONES[item.estado]}
      />
      {item.idTareaVinculada && <small className={styles.evaluationStateDetail}>{item.estadoActividadVinculada === "BORRADOR"
        ? "Actividad en borrador" : item.estadoActividadVinculada === "PUBLICADA" ? "Actividad publicada"
          : item.estadoActividadVinculada === "CERRADA" ? "Recepción cerrada"
            : item.estadoActividadVinculada === "ARCHIVADA" ? "Actividad archivada" : "Tarea vinculada"}</small>}
      {item.entregaPorConfigurar && <small className={styles.evaluationStateDetail}>Entrega por configurar</small>}
    </td>
  </>;
}

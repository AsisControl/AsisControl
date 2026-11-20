"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Loading, StatusPill } from "@/shared/ui";
import { financialLabels, financialToneForStatus, moneyLabel } from "./commitment-format";
import { getCommitmentSummary } from "./services/commitment-service";
import type { CommitmentSummary } from "./types";
import styles from "./finance.module.css";

/**
 * Indicadores de morosidad y resumen por estado de los compromisos del filtro.
 * Se recalcula cada vez que cambian los filtros o se actualiza el listado.
 */
export function DelinquencyIndicators({ filters, refreshKey }: { filters: string; refreshKey: number }) {
  const [result, setResult] = useState<{ key: string; summary?: CommitmentSummary; error?: string } | null>(null);
  const [retry, setRetry] = useState(0);
  const key = `${filters}|${refreshKey}|${retry}`;

  useEffect(() => {
    let active = true;
    getCommitmentSummary(filters)
      .then((summary) => {
        if (active) setResult({ key, summary });
      })
      .catch((caught) => {
        if (active) setResult({ key, error: message(caught) });
      });
    return () => { active = false; };
  }, [filters, key]);

  const current = result?.key === key ? result : null;
  const summary = current?.summary;
  const visibleStates = summary?.porEstado.filter((row) => row.cantidad > 0 || ["PENDIENTE", "PARCIAL", "PAGADO", "VENCIDO"].includes(row.estado)) ?? [];

  return (
    <section className={`panel ${styles.indicators}`} aria-labelledby="indicadores-morosidad">
      <div className={styles.indicatorsHeading}>
        <h3 id="indicadores-morosidad">Indicadores de morosidad</h3>
        {summary && <small>Calculado con los pagos aprobados al {summary.fechaCalculo.split("-").reverse().join("/")}</small>}
      </div>
      {!current && <Loading skeleton="metrics" rows={4} columns={5}>Calculando indicadores…</Loading>}
      {current?.error && (
        <div className={styles.notice}>
          <p>No pudimos calcular los indicadores. {current.error}</p>
          <button type="button" className="secondary-action" onClick={() => setRetry((value) => value + 1)}>
            Reintentar indicadores
          </button>
        </div>
      )}
      {summary && (
        <>
          <dl className={styles.kpis}>
            <div className={styles.kpiDanger}>
              <dt>Monto vencido</dt>
              <dd>{moneyLabel(summary.morosidad.montoVencido)}</dd>
            </div>
            <div>
              <dt>Compromisos vencidos</dt>
              <dd>{summary.morosidad.compromisosVencidos}</dd>
            </div>
            <div>
              <dt>Estudiantes morosos</dt>
              <dd>{summary.morosidad.estudiantesMorosos}</dd>
            </div>
            <div>
              <dt>Saldo por cobrar</dt>
              <dd>{moneyLabel(summary.saldo)}</dd>
            </div>
          </dl>
          <div className="user-table-wrap">
            <table className={`user-table ${styles.summaryTable}`} aria-label="Resumen por estado">
              <thead>
                <tr>
                  <th>Estado</th>
                  <th>Cantidad</th>
                  <th>Monto</th>
                  <th>Pagado</th>
                  <th>Saldo</th>
                </tr>
              </thead>
              <tbody>
                {visibleStates.map((row) => (
                  <tr key={row.estado}>
                    <td>
                      <StatusPill state={row.estado} label={financialLabels[row.estado]} tone={financialToneForStatus(row.estado)} />
                    </td>
                    <td>{row.cantidad}</td>
                    <td className={styles.money}>{moneyLabel(row.montoTotal)}</td>
                    <td className={styles.money}>{moneyLabel(row.montoPagado)}</td>
                    <td className={styles.money}>{moneyLabel(row.saldo)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <th scope="row">Total</th>
                  <td>{summary.cantidad}</td>
                  <td className={styles.money}>{moneyLabel(summary.montoTotal)}</td>
                  <td className={styles.money}>{moneyLabel(summary.montoPagado)}</td>
                  <td className={styles.money}>{moneyLabel(summary.saldo)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        </>
      )}
    </section>
  );
}

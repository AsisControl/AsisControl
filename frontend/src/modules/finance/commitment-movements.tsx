"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Loading, StatusPill } from "@/shared/ui";
import {
  momentLabel,
  moneyLabel,
  paymentMethodLabel,
  transactionLabels,
  transactionTone,
} from "./commitment-format";
import { getCommitmentMovements } from "./services/commitment-service";
import type { CommitmentMovement } from "./types";
import styles from "./finance.module.css";

/** Secuencia de transacciones de un compromiso y los comprobantes internos emitidos. */
export function CommitmentMovements({ id, currency }: { id: number; currency: string }) {
  const [result, setResult] = useState<{ key: string; movements?: CommitmentMovement[]; error?: string } | null>(null);
  const [retry, setRetry] = useState(0);
  const key = `${id}|${retry}`;

  useEffect(() => {
    let active = true;
    getCommitmentMovements(id)
      .then((detail) => {
        if (active) setResult({ key, movements: detail.movimientos });
      })
      .catch((caught) => {
        if (active) setResult({ key, error: message(caught) });
      });
    return () => { active = false; };
  }, [id, key]);

  const current = result?.key === key ? result : null;
  const count = current?.movements?.length ?? 0;

  return (
    <section className={styles.movements} aria-labelledby={`movimientos-${id}`}>
      <div className={styles.movementsHeading}>
        <h4 id={`movimientos-${id}`}>Historial de pagos</h4>
        {count > 0 && <span>{count === 1 ? "1 transacción" : `${count} transacciones`}</span>}
      </div>
      {!current && <Loading skeleton="list" rows={3}>Cargando transacciones…</Loading>}
      {current?.error && (
        <div className={styles.notice}>
          <p>No pudimos cargar las transacciones. {current.error}</p>
          <button type="button" className="secondary-action" onClick={() => setRetry((value) => value + 1)}>
            Reintentar transacciones
          </button>
        </div>
      )}
      {current?.movements && count === 0 && (
        <p className={styles.movementsEmpty}>Este compromiso aún no registra transacciones.</p>
      )}
      {current?.movements && count > 0 && (
        <ol className={styles.timeline}>
          {current.movements.map((movement) => {
            const transaction = movement.transaccion;
            return (
              <li
                key={transaction.id}
                aria-label={`Transacción ${movement.secuencia}`}
                className={movement.aplicaAlSaldo ? undefined : styles.timelineMuted}
              >
                <div className={styles.timelineCard}>
                  <div className={styles.movementHead}>
                    <div>
                      <strong className={styles.movementAmount}>{moneyLabel(transaction.monto, currency)}</strong>
                      <span className={styles.movementDate}>
                        Pago {movement.secuencia} · {momentLabel(transaction.fechaTransaccion)}
                      </span>
                    </div>
                    <StatusPill
                      state={transaction.estado}
                      label={transactionLabels[transaction.estado]}
                      tone={transactionTone(transaction.estado)}
                    />
                  </div>
                  <dl className={styles.movementFacts}>
                    <div>
                      <dt>Medio de pago</dt>
                      <dd>{paymentMethodLabel(transaction.medioPago)}</dd>
                    </div>
                    <div>
                      <dt>N.° de operación</dt>
                      <dd className={styles.codeText}>{transaction.numeroOperacion}</dd>
                    </div>
                    <div>
                      <dt>Saldo después</dt>
                      <dd className={styles.money}>
                        {movement.aplicaAlSaldo
                          ? moneyLabel(movement.saldoResultante, currency)
                          : `Sin cambio · ${moneyLabel(movement.saldoResultante, currency)}`}
                      </dd>
                    </div>
                    <div>
                      <dt>Comprobante</dt>
                      <dd>
                        {movement.comprobante
                          ? `${movement.comprobante.serie}-${movement.comprobante.numero}`
                          : movement.aplicaAlSaldo ? "Sin emitir" : "No corresponde"}
                      </dd>
                    </div>
                  </dl>
                  {!movement.aplicaAlSaldo && (
                    <p className={styles.movementNote}>No se aplica al saldo porque la transacción no fue aprobada.</p>
                  )}
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}

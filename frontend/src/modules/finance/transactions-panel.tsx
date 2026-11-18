"use client";
import { useState, type FormEvent } from "react";
import { usePaged } from "@/shared/hooks/use-paged";
import { Feedback, Field, PanelHeading, Pager, PersonCell, StatusPill } from "@/shared/ui";
import {
  momentLabel,
  moneyLabel,
  paymentMethodLabel,
  transactionLabels,
  transactionTone,
} from "./commitment-format";
import { TRANSACTIONS_PATH } from "./services/commitment-service";
import type { PaymentTransaction } from "./types";
import styles from "./finance.module.css";

const FILTER_NAMES = ["busqueda", "estado", "fechaDesde", "fechaHasta"] as const;

/** Transacciones de pago filtradas por estudiante, estado y fecha (US-44). */
export function TransactionsPanel() {
  const [filters, setFilters] = useState("");
  const [rangeError, setRangeError] = useState("");
  const list = usePaged<PaymentTransaction>(TRANSACTIONS_PATH, "", filters);

  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const desde = String(data.get("fechaDesde") || "");
    const hasta = String(data.get("fechaHasta") || "");
    if (desde && hasta && desde > hasta) {
      setRangeError("La fecha inicial no puede ser posterior a la final.");
      return;
    }
    setRangeError("");
    const query = new URLSearchParams();
    for (const name of FILTER_NAMES) {
      const value = String(data.get(name) || "").trim();
      if (value) query.set(name, value);
    }
    setFilters(query.toString());
    list.setPage(0);
  }

  return (
    <section className="panel">
      <PanelHeading
        title="Transacciones de pago"
        summary="Solo las transacciones aprobadas se aplican al saldo de los compromisos."
        actions={
          <button type="button" className="secondary-action" disabled={list.loading} onClick={list.refresh}>
            Actualizar transacciones
          </button>
        }
      />
      <Feedback error={list.error} />
      <form className={`${styles.filters} ${styles.transactionFilters}`} onSubmit={apply} aria-label="Filtros de transacciones">
        <Field
          label="Estudiante u operación"
          name="busqueda"
          placeholder="Nombre, código, compromiso u operación"
        />
        <label className="field">
          Estado de la transacción
          <select name="estado" defaultValue="">
            <option value="">Todos</option>
            {Object.entries(transactionLabels).map(([key, label]) => (
              <option key={key} value={key}>{label}</option>
            ))}
          </select>
        </label>
        <Field label="Desde" name="fechaDesde" type="date" />
        <Field label="Hasta" name="fechaHasta" type="date" error={rangeError || undefined} />
        <button type="submit" className="primary-action">Filtrar</button>
      </form>
      {list.loading ? (
        <p role="status">Cargando transacciones…</p>
      ) : list.error ? (
        <p className="empty">No pudimos cargar las transacciones. Intenta actualizar la lista.</p>
      ) : !list.data?.content.length ? (
        <p className="empty">No hay transacciones que coincidan con la consulta.</p>
      ) : (
        <div className="user-table-wrap">
          <table className={`user-table ${styles.table}`}>
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Estudiante</th>
                <th>Compromiso</th>
                <th>Operación</th>
                <th>Monto</th>
                <th>Estado</th>
              </tr>
            </thead>
            <tbody>
              {list.data.content.map((transaction) => (
                <tr key={transaction.id}>
                  <td>{momentLabel(transaction.fechaTransaccion)}</td>
                  <td>
                    <PersonCell name={transaction.alumno ?? "Estudiante"} detail={transaction.codigoAlumno ?? ""} />
                  </td>
                  <td>
                    <strong>{transaction.concepto ?? "Compromiso"}</strong>
                    <small>{transaction.codigoCompromiso}</small>
                  </td>
                  <td>
                    <span className={styles.codeText}>{transaction.numeroOperacion}</span>
                    <small>{paymentMethodLabel(transaction.medioPago)}</small>
                  </td>
                  <td className={styles.money}>{moneyLabel(transaction.monto)}</td>
                  <td>
                    <div className={styles.statusColumn}>
                      <StatusPill
                        state={transaction.estado}
                        label={transactionLabels[transaction.estado]}
                        tone={transactionTone(transaction.estado)}
                      />
                      {transaction.estado !== "APROBADA" && <small>No se aplica al saldo</small>}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
    </section>
  );
}

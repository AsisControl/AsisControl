"use client";
import { useState, type FormEvent } from "react";
import { usePaged } from "@/shared/hooks/use-paged";
import {
  Feedback,
  Field,
  PageHeader,
  Pager,
  PanelHeading,
  PersonCell,
  StatusPill,
} from "@/shared/ui";
import { CommitmentDetail } from "./commitment-detail";
import { CommitmentForm } from "./commitment-form";
import {
  dueDateLabel,
  financialLabel,
  financialLabels,
  financialStatus,
  financialTone,
  quotaLabel,
  moneyLabel,
} from "./commitment-format";
import { COMMITMENTS_PATH } from "./services/commitment-service";
import type { PaymentCommitment } from "./types";
import styles from "./finance.module.css";

type SelectedCommitment = {
  id: number;
  action: "detail" | "edit" | "annul";
  record?: PaymentCommitment;
};

export function Finance({ canWrite }: { canWrite: boolean }) {
  const [filters, setFilters] = useState("");
  const list = usePaged<PaymentCommitment>(COMMITMENTS_PATH, "", filters);
  const [creating, setCreating] = useState(false);
  const [selected, setSelected] = useState<SelectedCommitment | null>(null);
  const [success, setSuccess] = useState("");

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const query = new URLSearchParams();
    const names = ["busqueda", "anioAcademico", "estado"];
    for (const name of names) {
      const value = String(data.get(name) || "").trim();
      if (value) query.set(name, value);
    }
    setFilters(query.toString());
    list.setPage(0);
  }

  function saved(record: PaymentCommitment) {
    if (creating) {
      setSuccess(`Compromiso ${record.codigoCompromiso} emitido.`);
    } else if (selected?.action === "annul") {
      setSuccess("Compromiso anulado. El registro se conserva para su consulta.");
    } else {
      setSuccess("Cambios guardados en el compromiso.");
    }
    setCreating(false);
    setSelected(null);
    list.refresh();
  }

  return (
    <section className={styles.finance}>
      <PageHeader
        title="Compromisos de pago"
        description="Consulta y administra las obligaciones económicas de cada estudiante en soles."
        actions={canWrite && (
          <button
            className="primary-action"
            onClick={() => {
              setCreating(true);
              setSuccess("");
            }}
          >
            Emitir compromiso
          </button>
        )}
      />
      <section className="panel">
        <PanelHeading
          title="Compromisos registrados"
          summary="Los compromisos anulados se conservan para su consulta."
          actions={
            <button type="button" className="secondary-action" disabled={list.loading} onClick={list.refresh}>
              Actualizar
            </button>
          }
        />
        <Feedback error={list.error} success={success} />
        <form className={styles.filters} onSubmit={applyFilters}>
          <Field
            label="Buscar estudiante o compromiso"
            name="busqueda"
            placeholder="Nombre, código o concepto"
          />
          <Field
            label="Año académico"
            name="anioAcademico"
            type="number"
            min={1900}
            max={9999}
            placeholder="Ej. 2026"
          />
          <label className="field">
            Estado del compromiso
            <select name="estado" defaultValue="">
              <option value="">Todos</option>
              {Object.entries(financialLabels)
                .map(([key, label]) => (
                  <option key={key} value={key}>{label}</option>
                ))}
            </select>
          </label>
          <button type="submit" className="primary-action">Buscar</button>
        </form>
        {list.loading ? (
          <p role="status">Cargando compromisos…</p>
        ) : list.error ? (
          <p className="empty">
            No pudimos cargar los compromisos. Intenta actualizar la lista.
          </p>
        ) : !list.data?.content.length ? (
          <p className="empty">No hay compromisos que coincidan con la consulta.</p>
        ) : (
          <div className="user-table-wrap">
            <table className={`user-table ${styles.table}`}>
              <thead>
                <tr>
                  <th>Estudiante</th>
                  <th>Concepto y cuota</th>
                  <th>Monto</th>
                  <th>Vencimiento</th>
                  <th>Estado</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {list.data.content.map((record) => (
                  <tr key={record.id}>
                    <td>
                      <PersonCell
                        name={record.alumno}
                        detail={`${record.codigoAlumno} · ${record.anioAcademico}`}
                      />
                    </td>
                    <td>
                      <strong>{record.concepto}</strong>
                      <small>
                        {quotaLabel(record.numeroCuota)} · {record.codigoCompromiso}
                      </small>
                    </td>
                    <td className={styles.money}>
                      {moneyLabel(record.montoTotal, record.moneda)}
                    </td>
                    <td>{dueDateLabel(record.fechaVencimiento)}</td>
                    <td>
                      <div className={styles.statusColumn}>
                        <StatusPill
                          state={financialStatus(record)}
                          label={financialLabel(record)}
                          tone={financialTone(record)}
                        />
                      </div>
                    </td>
                    <td>
                      <button
                        type="button"
                        className="secondary-action"
                        onClick={() => setSelected({ id: record.id, action: "detail" })}
                        aria-label={`Ver compromiso ${record.codigoCompromiso}`}
                      >
                        Ver detalle
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
      </section>
      {creating && (
        <CommitmentForm onClose={() => setCreating(false)} onSaved={saved} />
      )}
      {selected?.action === "detail" && (
        <CommitmentDetail
          key={selected.id}
          id={selected.id}
          canWrite={canWrite}
          onClose={() => setSelected(null)}
          onAction={(action, record) => setSelected({ id: record.id, action, record })}
        />
      )}
      {selected && selected.action !== "detail" && (
        <CommitmentForm
          commitment={selected.record}
          action={selected.action}
          onClose={() => setSelected({ id: selected.id, action: "detail" })}
          onSaved={saved}
        />
      )}
    </section>
  );
}

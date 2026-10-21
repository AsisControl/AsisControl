"use client";
import { useEffect, useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { Dialog, Feedback, Field, Pager } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { dateLabel, stateLabel } from "@/shared/lib/formatters";
type SystemError = {
  id: number;
  fechaHora: string;
  severidad: string;
  componente: string;
  mensajeSeguro: string;
  correlationId: string;
  estado: string;
  observacionSeguimiento: string | null;
  version: number;
};
type Followup = {
  id: number;
  actor: string;
  fecha: string;
  anterior: string;
  nuevo: string;
  observacion: string;
};
const states = ["PENDIENTE", "EN_SEGUIMIENTO", "RESUELTO"];
export function SystemLogs() {
  const [filters, setFilters] = useState("");
  const logs = usePaged<SystemError>("/api/auditoria/errores", "", filters);
  const [selected, setSelected] = useState<SystemError | null>(null);
  const [history, setHistory] = useState<Followup[] | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [success, setSuccess] = useState("");
  useEffect(() => {
    let active = true;
    if (selected)
      api<Followup[]>(`/api/auditoria/errores/${selected.id}/seguimientos`)
        .then((value) => {
          if (active) setHistory(value);
        })
        .catch((e) => {
          if (active) setError(message(e));
        });
    return () => {
      active = false;
    };
  }, [selected]);
  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const query = new URLSearchParams();
    for (const [name, value] of values)
      if (String(value).trim()) query.set(name, String(value).trim());
    setFilters(query.toString());
    logs.setPage(0);
  }
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) return;
    const form = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    try {
      const result = await api<SystemError>(
        `/api/auditoria/errores/${selected.id}`,
        {
          method: "PUT",
          body: JSON.stringify({
            estado: form.get("estado"),
            observacionSeguimiento: String(
              form.get("observacion") || "",
            ).trim(),
            version: selected.version,
          }),
        },
      );
      setSelected(result);
      setSuccess("Seguimiento registrado.");
      logs.refresh();
      event.currentTarget?.reset();
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <p className="hint">
        Errores técnicos registrados por el servidor. La auditoría conserva las
        operaciones de negocio en su apartado propio.
      </p>
      <Feedback error={logs.error} success={success} />
      <form className="filter-grid" onSubmit={filter}>
        <label className="field">
          Estado
          <select name="estado">
            <option value="">Todos</option>
            {states.map((s) => (
              <option key={s} value={s}>
                {stateLabel(s)}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Severidad
          <select name="severidad">
            <option value="">Todas</option>
            {["INFORMATIVA", "ADVERTENCIA", "ERROR", "CRITICA"].map((s) => (
              <option key={s} value={s}>
                {stateLabel(s)}
              </option>
            ))}
          </select>
        </label>
        <Field label="Componente" name="componente" maxLength={120} />
        <Field
          label="Identificador de correlación"
          name="correlacion"
          maxLength={100}
        />
        <Field label="Desde (hora de Lima)" type="date" name="desde" />
        <Field label="Hasta (hora de Lima)" type="date" name="hasta" />
        <div className="row-actions">
          <button disabled={logs.loading}>Filtrar</button>
          <button
            type="reset"
            onClick={() => {
              setFilters("");
              logs.setPage(0);
            }}
          >
            Limpiar
          </button>
          <button type="button" disabled={logs.loading} onClick={logs.refresh}>
            Actualizar
          </button>
        </div>
      </form>
      {logs.loading ? (
        <p role="status">Cargando logs…</p>
      ) : logs.data?.content.length ? (
        <div className="user-table-wrap">
          <table className="user-table">
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Severidad / Componente</th>
                <th>Mensaje</th>
                <th>Estado</th>
                <th>Acción</th>
              </tr>
            </thead>
            <tbody>
              {logs.data.content.map((log) => (
                <tr key={log.id}>
                  <td data-label="Fecha">{dateLabel(log.fechaHora)}</td>
                  <td data-label="Componente">
                    <strong>{stateLabel(log.severidad)}</strong>
                    <small>{log.componente}</small>
                  </td>
                  <td data-label="Mensaje">{log.mensajeSeguro}</td>
                  <td data-label="Estado">
                    <span className="status">{stateLabel(log.estado)}</span>
                  </td>
                  <td data-label="Acción">
                    <button
                      onClick={() => {
                        setHistory(null);
                        setError("");
                        setSelected(log);
                      }}
                    >
                      Ver y dar seguimiento
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        !logs.error && (
          <p className="empty">No hay errores con estos filtros.</p>
        )
      )}
      <Pager data={logs.data} loading={logs.loading} setPage={logs.setPage} />
      {selected && (
        <Dialog
          title="Detalle y seguimiento del error"
          onClose={() => setSelected(null)}
          busy={busy}
        >
          <Feedback error={error} />
          <dl className="detail-grid">
            <div>
              <dt>Registrado</dt>
              <dd>{dateLabel(selected.fechaHora)}</dd>
            </div>
            <div>
              <dt>Componente</dt>
              <dd>{selected.componente}</dd>
            </div>
            <div>
              <dt>Severidad</dt>
              <dd>{stateLabel(selected.severidad)}</dd>
            </div>
            <div>
              <dt>Correlación técnica</dt>
              <dd>{selected.correlationId || "—"}</dd>
            </div>
          </dl>
          <p className="technical-message">{selected.mensajeSeguro}</p>
          <form onSubmit={save}>
            <label className="field">
              Estado *
              <select name="estado" defaultValue={selected.estado}>
                {states.map((s) => (
                  <option key={s} value={s}>
                    {stateLabel(s)}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              Observación de seguimiento *
              <textarea name="observacion" required maxLength={1000} rows={3} />
            </label>
            <div className="form-actions">
              <button className="primary-action" disabled={busy}>
                {busy ? "Guardando…" : "Registrar seguimiento"}
              </button>
            </div>
          </form>
          <h4>Historial de seguimiento</h4>
          {history ? (
            history.length ? (
              <ol className="history-list">
                {history.map((item) => (
                  <li key={item.id}>
                    <strong>
                      {stateLabel(item.anterior)} → {stateLabel(item.nuevo)}
                    </strong>
                    <small>
                      {dateLabel(item.fecha)} · {item.actor}
                    </small>
                    <p>{item.observacion}</p>
                  </li>
                ))}
              </ol>
            ) : (
              <p className="empty">Sin seguimientos registrados.</p>
            )
          ) : (
            <p role="status">Cargando historial…</p>
          )}
        </Dialog>
      )}
    </>
  );
}

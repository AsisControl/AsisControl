"use client";

import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { usePaged } from "@/shared/hooks/use-paged";
import { dateLabel } from "@/shared/lib/formatters";
import { Field, Feedback, FormDialog, isoDateLabel, Loading, Pager, PanelHeading, StatusPill } from "@/shared/ui";
import { todayInLima } from "./format";
import {
  arrivalStates, CONTINGENCIES_PATH, contingencyStates, reportContingency, resolveContingency, type Contingency,
} from "./services/contingency-service";

type Action = { kind: "report" } | { kind: "approve" | "reject"; item: Contingency };

export function TeacherContingencies({ administration = false, canWrite }: {
  administration?: boolean; canWrite: boolean;
}) {
  const [filter, setFilter] = useState(administration ? "PENDIENTE" : "");
  const [action, setAction] = useState<Action | null>(null);
  const [success, setSuccess] = useState("");
  const list = usePaged<Contingency>(CONTINGENCIES_PATH, "", filter ? `estado=${filter}` : "");
  const title = administration ? "Solicitudes de contingencia" : "Mis solicitudes de contingencia";
  return <section className="panel" aria-label={title}>
    <PanelHeading title={title}
      summary={administration ? "Revisa los reportes de ingreso institucional de los docentes."
        : "Reporta un problema al registrar tu llegada al colegio y consulta su estado."}
      actions={!administration && canWrite && <button type="button" className="primary-action"
        onClick={() => setAction({ kind: "report" })}>Reportar fallo de marcación</button>} />
    <div className="toolbar">
      <label className="field">Estado
        <select value={filter} onChange={(event) => setFilter(event.target.value)}>
          <option value="">Todos los estados</option>
          {Object.entries(contingencyStates).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
      <button type="button" disabled={list.loading} onClick={list.refresh}>Actualizar</button>
    </div>
    <Feedback error={list.error} success={success} />
    {list.loading ? <Loading>Cargando solicitudes…</Loading> : !list.error && (
      list.data?.content.length ? <>
        <div className="user-table-wrap">
          <table className="user-table" aria-label={title}>
            <thead><tr>
              {administration && <th scope="col">Docente</th>}
              <th scope="col">Jornada y hora reportada</th><th scope="col">Motivo del reporte</th>
              <th scope="col">Envío</th><th scope="col">Estado</th>
              {administration && canWrite && <th scope="col">Acciones</th>}
            </tr></thead>
            <tbody>{list.data.content.map((item) => <tr key={item.id}>
              {administration && <td data-label="Docente">{item.docente}</td>}
              <td data-label="Jornada y hora reportada">{isoDateLabel(item.fechaJornada)} · {item.horaReportada.slice(0, 5)}</td>
              <td data-label="Motivo del reporte">{item.motivo}</td>
              <td data-label="Envío">{dateLabel(item.fechaEnvio)}</td>
              <td data-label="Estado">
                <StatusPill state={item.estado} label={contingencyStates[item.estado]} />
                {item.estadoRegistrado && <div>Marcación: {arrivalStates[item.estadoRegistrado]}</div>}
                {item.motivoRechazo && <div>Motivo del rechazo: {item.motivoRechazo}</div>}
                {item.fechaResolucion && <small>Resuelta el {dateLabel(item.fechaResolucion)}</small>}
              </td>
              {administration && canWrite && <td data-label="Acciones">
                {item.estado === "PENDIENTE" && <div className="form-actions">
                  <button type="button" onClick={() => setAction({ kind: "approve", item })}>Aprobar</button>
                  <button type="button" onClick={() => setAction({ kind: "reject", item })}>Rechazar</button>
                </div>}
              </td>}
            </tr>)}</tbody>
          </table>
        </div>
        <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
      </> : <p className="empty">No hay solicitudes para este estado.</p>
    )}
    {action && <ContingencyDialog action={action} onClose={() => setAction(null)} onSaved={(text) => {
      setSuccess(text);
      setAction(null);
      list.refresh();
    }} />}
  </section>;
}

function ContingencyDialog({ action, onClose, onSaved }: {
  action: Action; onClose: () => void; onSaved: (text: string) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const today = todayInLima();
  const item = action.kind === "report" ? null : action.item;
  const approving = action.kind === "approve";
  const rejecting = action.kind === "reject";

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const form = new FormData(event.currentTarget);
    const value = (name: string) => String(form.get(name) ?? "").trim();
    setBusy(true);
    setError("");
    try {
      if (action.kind === "report") {
        const motivo = value("motivo");
        if (!motivo) { setError("Indica el motivo del reporte."); return; }
        await reportContingency({ fechaJornada: value("fecha"), horaReportada: value("hora"), motivo });
        onSaved("Solicitud enviada para revisión.");
      } else if (action.kind === "approve") {
        await resolveContingency(action.item.id, { decision: "APROBAR", estadoSinVentana: value("estado") || null });
        onSaved("Solicitud aprobada. Se registró la marcación docente.");
      } else {
        const motivo = value("motivo");
        if (!motivo) { setError("Indica el motivo del rechazo."); return; }
        await resolveContingency(action.item.id, { decision: "RECHAZAR", motivo });
        onSaved("Solicitud rechazada.");
      }
    } catch (cause) {
      setError(message(cause));
    } finally { setBusy(false); }
  }

  const title = approving ? "Aprobar contingencia" : rejecting ? "Rechazar contingencia" : "Reportar fallo de marcación";
  return <FormDialog title={title} description={item?.docente}
    busy={busy} error={error} onClose={onClose} onSubmit={submit} danger={rejecting}
    submitLabel={approving ? "Aprobar solicitud" : rejecting ? "Rechazar solicitud" : "Enviar solicitud"}>
    {item ? <p className="full-field">{isoDateLabel(item.fechaJornada)} a las {item.horaReportada.slice(0, 5)} · {item.motivo}</p> : <>
      <Field label="Fecha de la jornada" name="fecha" type="date" defaultValue={today} max={today} required />
      <Field label="Hora de llegada" name="hora" type="time" required />
    </>}
    {!approving && <label className="field full-field">
      {rejecting ? "Motivo del rechazo *" : "Motivo del reporte *"}
      <textarea name="motivo" rows={3} maxLength={1000} required
        placeholder={rejecting ? undefined : "Ej. El lector QR no funcionó al llegar."} />
    </label>}
    {approving && (item?.estadoSugerido ? <p className="full-field">
      Se registrará como <strong>{arrivalStates[item.estadoSugerido]}</strong> según el horario de puntualidad.
    </p> : <label className="field full-field">Estado de la marcación *
      <select name="estado" required defaultValue="">
        <option value="">Selecciona el estado</option>
        {Object.entries(arrivalStates).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select>
      <small className="field-hint">Esta jornada no tiene un horario de puntualidad configurado.</small>
    </label>)}
  </FormDialog>;
}

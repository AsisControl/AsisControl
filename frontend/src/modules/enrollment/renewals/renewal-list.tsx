"use client";

import { useState } from "react";
import { Feedback, Pager, PanelHeading, PersonCell, StatusPill } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { shortDateLabel } from "@/shared/lib/formatters";
import { RENEWALS_PATH, type RenewalRecord, type RenewalState } from "../services/renewal-service";
import { renewalLabels } from "./renewal-labels";
import { RenewalRequestDialog } from "./renewal-request-dialog";
import { RenewalReviewDialog } from "./renewal-review-dialog";

export function RenewalList({ role, canWrite }: { role: "APODERADO" | "ADMINISTRADOR"; canWrite: boolean }) {
  const family = role === "APODERADO";
  const [filter, setFilter] = useState("");
  const [creating, setCreating] = useState(false);
  const [selected, setSelected] = useState<RenewalRecord | null>(null);
  const [success, setSuccess] = useState("");
  const list = usePaged<RenewalRecord>(RENEWALS_PATH, "", filter ? `estado=${filter}` : "", 10);

  function submitted(record: RenewalRecord) {
    setCreating(false);
    setSelected(record);
    setSuccess("Solicitud enviada. La administración revisará las condiciones para renovar.");
    setFilter("");
    list.setPage(0);
    list.refresh();
  }

  return (
    <section className="panel">
      <PanelHeading
        title={family ? "Mis solicitudes de renovación" : "Solicitudes de renovación"}
        summary={family ? "Consulta el estado de cada trámite y el motivo de la decisión." : "Verifica el grado anterior, los pagos vencidos y la vacante antes de decidir."}
        actions={
          <div className="row-actions">
            <button type="button" className="secondary-action" onClick={list.refresh}>Actualizar</button>
            {family && canWrite && (
              <button type="button" className="primary-action" onClick={() => { setSuccess(""); setCreating(true); }}>
                Solicitar renovación
              </button>
            )}
          </div>
        }
      />
      <Feedback error={list.error} success={success} />
      <label className="field">
        Estado del trámite
        <select value={filter} onChange={(event) => { setFilter(event.target.value); list.setPage(0); }}>
          <option value="">Todos los estados</option>
          {(Object.keys(renewalLabels) as RenewalState[]).map((state) =>
            <option key={state} value={state}>{renewalLabels[state]}</option>,
          )}
        </select>
      </label>
      {list.loading ? <p role="status">Cargando renovaciones…</p> : list.data?.content.length ? (
        <div className="user-table-wrap">
          <table className="user-table" aria-label="Trámites de renovación">
            <thead>
              <tr>
                <th>Estudiante</th>
                <th>Destino solicitado</th>
                <th>Fecha de solicitud</th>
                <th>Estado</th>
                <th>Acción</th>
              </tr>
            </thead>
            <tbody>
              {list.data.content.map((item) => (
                <tr key={item.id}>
                  <td><PersonCell name={item.alumno} detail={item.codigoAlumno} /></td>
                  <td>{item.anioDestino} · {item.gradoDestino}.° {item.seccion}</td>
                  <td>{shortDateLabel(item.fechaCreacion)}</td>
                  <td>
                    <StatusPill
                      state={item.estado}
                      label={renewalLabels[item.estado]}
                      tone={item.estado === "APROBADA" ? "success" : undefined}
                    />
                    {item.motivo && item.estado === "RECHAZADA" && <small>{item.motivo}</small>}
                  </td>
                  <td>
                    <button type="button" className="secondary-action" onClick={() => setSelected(item)}>
                      {family || item.estado !== "EN_REVISION" || !canWrite ? "Ver detalle" : "Revisar trámite"}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : !list.error && <p className="empty">No hay solicitudes de renovación para mostrar.</p>}
      {list.data && <Pager data={list.data} loading={list.loading} setPage={list.setPage} />}
      {creating && <RenewalRequestDialog onClose={() => setCreating(false)} onSaved={submitted} />}
      {selected && (
        <RenewalReviewDialog
          key={selected.id}
          initial={selected}
          canDecide={!family && canWrite}
          onClose={() => setSelected(null)}
          onChanged={list.refresh}
        />
      )}
    </section>
  );
}

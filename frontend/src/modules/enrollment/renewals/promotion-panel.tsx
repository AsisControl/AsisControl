"use client";

import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Dialog, Feedback, Loading, Pager, PanelHeading, PersonCell, StatusPill } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { PROMOTIONS_PATH, savePromotion, type PromotionRecord, type PromotionResult } from "../services/renewal-service";
import { promotionLabels } from "./renewal-labels";

export function PromotionPanel({ canWrite }: { canWrite: boolean }) {
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<PromotionRecord | null>(null);
  const [success, setSuccess] = useState("");
  const filters = search ? `busqueda=${encodeURIComponent(search)}` : "";
  const list = usePaged<PromotionRecord>(PROMOTIONS_PATH, "", filters, 10);

  function searchRecords(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSearch(String(new FormData(event.currentTarget).get("busqueda") ?? "").trim());
    list.setPage(0);
    list.refresh();
  }

  return (
    <section className="panel">
      <PanelHeading title="Resultados de promoción" summary="Registra el resultado del acta de cierre que se usará para revisar la renovación del próximo año." />
      <Feedback error={list.error} success={success} />
      <form className="filter-grid" onSubmit={searchRecords}>
        <label className="field">Estudiante<input name="busqueda" placeholder="Nombre o código" /></label>
        <div className="row-actions"><button className="primary-action" type="submit">Buscar</button></div>
      </form>
      {list.loading ? <Loading skeleton="table" columns={5} rows={4}>Cargando resultados…</Loading> : list.data?.content.length ? (
        <div className="user-table-wrap">
          <table className="user-table" aria-label="Promoción del grado anterior">
            <thead>
              <tr>
                <th>Estudiante</th>
                <th>Grado y año</th>
                <th>Resultado</th>
                <th>Sustento</th>
                {canWrite && <th>Acción</th>}
              </tr>
            </thead>
            <tbody>
              {list.data.content.map((item) => (
                <tr key={item.idMatriculaAnterior}>
                  <td><PersonCell name={item.alumno} detail={item.codigoAlumno} /></td>
                  <td>{item.gradoAnterior}.° · {item.anioAnterior}</td>
                  <td>
                    <StatusPill
                      state={item.promocion ?? "PENDIENTE"}
                      label={item.promocion ? promotionLabels[item.promocion] : "Por registrar"}
                      tone={item.promocion === "PROMOVIDO" ? "success" : item.promocion === "NO_PROMOVIDO" ? "danger" : "neutral"}
                    />
                  </td>
                  <td>{item.motivo || "—"}</td>
                  {canWrite && (
                    <td>
                      <button
                        type="button"
                        className="secondary-action"
                        onClick={() => { setSuccess(""); setSelected(item); }}
                      >
                        {item.promocion ? "Editar resultado" : "Registrar resultado"}
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : !list.error && <p className="empty">No se encontraron matrículas para registrar la promoción.</p>}
      {list.data && <Pager data={list.data} loading={list.loading} setPage={list.setPage} />}
      {selected && (
        <PromotionDialog
          key={selected.idMatriculaAnterior}
          record={selected}
          onClose={() => setSelected(null)}
          onSaved={() => { setSelected(null); setSuccess("Resultado de promoción guardado."); list.refresh(); }}
        />
      )}
    </section>
  );
}

function PromotionDialog({ record, onClose, onSaved }: { record: PromotionRecord; onClose: () => void; onSaved: () => void }) {
  const [value, setValue] = useState<PromotionResult | "">(record.promocion ?? "");
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!value || busy) return;
    if (!reason.trim()) {
      setError("Indica el acta o sustento del resultado.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await savePromotion(record, value, reason.trim());
      onSaved();
    } catch (caught) {
      setError(message(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog title={record.promocion ? "Editar resultado de promoción" : "Registrar resultado de promoción"} description={`${record.alumno} · ${record.gradoAnterior}.° · ${record.anioAnterior}`} onClose={onClose} busy={busy}>
      <Feedback error={error} />
      <form onSubmit={submit}>
        <label className="field">
          Resultado del acta
          <select required value={value} onChange={(event) => setValue(event.target.value as PromotionResult | "")} disabled={busy}>
            <option value="">Selecciona un resultado</option>
            <option value="PROMOVIDO">Grado aprobado</option>
            <option value="NO_PROMOVIDO">Grado no aprobado</option>
          </select>
        </label>
        <label className="field">
          Acta o sustento
          <textarea rows={3} required maxLength={700} value={reason} onChange={(event) => setReason(event.target.value)} disabled={busy} />
        </label>
        <div className="form-actions">
          <button type="button" className="secondary-action" disabled={busy} onClick={onClose}>Cancelar</button>
          <button type="submit" className="primary-action" disabled={busy}>{busy ? "Guardando…" : "Guardar resultado"}</button>
        </div>
      </form>
    </Dialog>
  );
}

"use client";

import { useEffect, useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Dialog, Feedback } from "@/shared/ui";
import { getRenewalOptions, requestRenewal, type RenewalOption, type RenewalRecord } from "../services/renewal-service";
import { promotionLabels } from "./renewal-labels";

export function RenewalRequestDialog({ onClose, onSaved }: {
  onClose: () => void;
  onSaved: (record: RenewalRecord) => void;
}) {
  const [choices, setChoices] = useState<RenewalOption[] | null>(null);
  const [loadingError, setLoadingError] = useState("");
  const [saveError, setSaveError] = useState("");
  const [source, setSource] = useState("");
  const [section, setSection] = useState("");
  const [busy, setBusy] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const selected = choices?.find((option) => String(option.idMatriculaAnterior) === source);
  useEffect(() => {
    let active = true;
    getRenewalOptions().then(
      (options) => { if (active) setChoices(options); },
      (error) => { if (active) setLoadingError(message(error)); },
    );
    return () => { active = false; };
  }, [attempt]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected || !section || busy) return;
    setBusy(true);
    setSaveError("");
    try {
      const result = await requestRenewal(selected.idMatriculaAnterior, Number(section));
      onSaved(result);
    } catch (error) {
      setSaveError(message(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog title="Solicitar renovación anual" description="Elige al estudiante y la sección para el próximo año." onClose={onClose} busy={busy}>
      <Feedback error={loadingError || saveError} />
      {!choices && !loadingError && <p role="status">Consultando las opciones de renovación…</p>}
      {loadingError && (
        <button type="button" className="secondary-action" onClick={() => { setLoadingError(""); setAttempt((value) => value + 1); }}>Volver a consultar</button>
      )}
      {choices?.length === 0 && <p className="hint">No hay opciones de renovación para tus estudiantes. Consulta las fechas de convocatoria con la institución.</p>}
      {Boolean(choices?.length) && (
        <form onSubmit={submit}>
          <label className="field">
            Estudiante
            <select required value={source} disabled={busy} onChange={(event) => { setSource(event.target.value); setSection(""); setSaveError(""); }}>
              <option value="">Selecciona un estudiante</option>
              {choices?.map((option) => (
                <option key={option.idMatriculaAnterior} value={option.idMatriculaAnterior}>{option.alumno} · {option.anioDestino}</option>
              ))}
            </select>
          </label>
          {selected && (
            <>
              <dl className="detail-grid">
                <div><dt>Grado anterior</dt><dd>{selected.gradoAnterior}.° · {selected.anioAnterior}</dd></div>
                <div><dt>Grado de destino</dt><dd>{selected.gradoDestino}.° · {selected.anioDestino}</dd></div>
                <div><dt>Promoción registrada</dt><dd>{selected.promocion ? promotionLabels[selected.promocion] : "Por registrar"}</dd></div>
              </dl>
              <label className="field">
                Sección solicitada
                <select required value={section} onChange={(event) => setSection(event.target.value)} disabled={busy}>
                  <option value="">Selecciona una sección</option>
                  {selected.secciones.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.nombre} · {item.vacantes > 0 ? `${item.vacantes} ${item.vacantes === 1 ? "vacante" : "vacantes"}` : "Sin vacantes"}
                    </option>
                  ))}
                </select>
              </label>
              {!selected.secciones.length && <p className="hint">La institución todavía no habilitó secciones para este grado.</p>}
            </>
          )}
          <p className="hint">La administración verificará la promoción, los pagos vencidos y la vacante disponible al revisar el trámite.</p>
          <div className="form-actions">
            <button type="button" className="secondary-action" disabled={busy} onClick={onClose}>Cancelar</button>
            <button type="submit" className="primary-action" disabled={!selected || !section || busy}>{busy ? "Enviando…" : "Enviar solicitud"}</button>
          </div>
        </form>
      )}
    </Dialog>
  );
}

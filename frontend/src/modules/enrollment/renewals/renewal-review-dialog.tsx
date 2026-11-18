"use client";

import { useEffect, useState, type FormEvent } from "react";
import { ApiError, message } from "@/core/api/client";
import { Dialog, Feedback, StatusPill } from "@/shared/ui";
import { dateLabel } from "@/shared/lib/formatters";
import { EnrollmentPaymentPlanAmounts } from "../enrollment-payment-preview";
import type { EnrollmentPaymentPlan } from "../types";
import { approveRenewal, getRenewal, getRenewalPlan, rejectRenewal, type RenewalRecord } from "../services/renewal-service";
import { renewalLabels } from "./renewal-labels";

type Action = "aprobar" | "rechazar" | null;

export function RenewalReviewDialog({ initial, canDecide, onClose, onChanged }: {
  initial: RenewalRecord;
  canDecide: boolean;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [record, setRecord] = useState(initial);
  const [detailLoading, setDetailLoading] = useState(true);
  const [detailError, setDetailError] = useState("");
  const [actionError, setActionError] = useState("");
  const [success, setSuccess] = useState("");
  const [action, setAction] = useState<Action>(null);
  const [plan, setPlan] = useState<EnrollmentPaymentPlan | null>(null);
  const [planError, setPlanError] = useState("");
  const [planLoading, setPlanLoading] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    getRenewal(initial.id).then(
      (updated) => { if (active) setRecord(updated); },
      (error) => { if (active) setDetailError(message(error)); },
    ).finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; };
  }, [initial.id]);

  async function loadPlan() {
    setPlan(null);
    setPlanError("");
    setPlanLoading(true);
    try {
      setPlan(await getRenewalPlan(record.id));
    } catch (error) {
      setPlanError(message(error));
    } finally {
      setPlanLoading(false);
    }
  }

  function begin(next: Action) {
    setAction(next);
    setActionError("");
    setSuccess("");
    setReason("");
    if (next === "aprobar") void loadPlan();
  }

  async function refreshDetail() {
    setDetailLoading(true);
    setDetailError("");
    try {
      setRecord(await getRenewal(record.id));
    } catch (error) {
      setDetailError(message(error));
    } finally {
      setDetailLoading(false);
    }
  }

  async function decide(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!action || busy || detailLoading || detailError) return;
    if (action === "rechazar" && !reason.trim()) {
      setActionError("Indica por qué se rechaza la renovación.");
      return;
    }
    if (action === "aprobar" && !plan) return;

    setBusy(true);
    setActionError("");
    try {
      const updated = action === "aprobar"
        ? await approveRenewal(record, plan?.firmaPlan || undefined)
        : await rejectRenewal(record, reason.trim());
      setRecord(updated);
      setAction(null);
      setSuccess(updated.estado === "APROBADA"
        ? "Renovación aprobada. La matrícula y el plan de pensiones quedaron registrados."
        : "La decisión se guardó y el apoderado podrá consultar el motivo.");
      onChanged();
    } catch (error) {
      setActionError(message(error));
      if (error instanceof ApiError && error.status === 409) {
        await refreshDetail();
        onChanged();
        if (action === "aprobar") await loadPlan();
      }
    } finally {
      setBusy(false);
    }
  }

  const mayReview = canDecide && record.estado === "EN_REVISION" && !detailLoading && !detailError;
  return (
    <Dialog title="Detalle de la renovación" description={record.alumno} size="lg" onClose={onClose} busy={busy}>
      <Feedback error={detailError || actionError} success={success} />
      {detailLoading && <p role="status">Consultando el trámite…</p>}
      {detailError && (
        <button type="button" className="secondary-action" onClick={refreshDetail}>
          Volver a consultar
        </button>
      )}
      <dl className="detail-grid">
        <div>
          <dt>Estado</dt>
          <dd>
            <StatusPill
              state={record.estado}
              label={renewalLabels[record.estado]}
              tone={record.estado === "APROBADA" ? "success" : undefined}
            />
          </dd>
        </div>
        <div>
          <dt>Estudiante</dt>
          <dd>{record.alumno}<small>{record.codigoAlumno}</small></dd>
        </div>
        <div>
          <dt>Matrícula anterior</dt>
          <dd>{record.gradoAnterior}.° · {record.anioAnterior}</dd>
        </div>
        <div>
          <dt>Destino solicitado</dt>
          <dd>{record.gradoDestino}.° {record.seccion} · {record.anioDestino}</dd>
        </div>
        <div>
          <dt>Solicitud enviada</dt>
          <dd>{dateLabel(record.fechaCreacion)}</dd>
        </div>
        {record.fechaDecision && (
          <div>
            <dt>Fecha de decisión</dt>
            <dd>{dateLabel(record.fechaDecision)}</dd>
          </div>
        )}
        {record.motivo && (
          <div>
            <dt>Motivo de la decisión</dt>
            <dd>{record.motivo}</dd>
          </div>
        )}
      </dl>
      {record.estado === "EN_REVISION" && (
        <p className="hint">
          La institución revisará la promoción del grado anterior, los pagos vencidos y la disponibilidad de la sección.
        </p>
      )}
      {mayReview && action && (
        <form
          onSubmit={decide}
          aria-label={action === "aprobar" ? "Aprobación de la renovación" : "Rechazo de la renovación"}
        >
          {action === "aprobar" ? (
            <section aria-label="Plan de pensiones de renovación">
              <h4>Plan de pensiones del nuevo año</h4>
              {planLoading && <p role="status">Consultando la tarifa y los beneficios…</p>}
              <Feedback error={planError} />
              {planError && (
                <button type="button" className="secondary-action" onClick={loadPlan}>
                  Volver a consultar el plan
                </button>
              )}
              {plan && !plan.puedeGenerar && (
                <p className="dialog-note">
                  {plan.mensaje || "La tarifa del nuevo año aún no está disponible. Puedes verificar las condiciones del trámite. Para aprobarlo, configura primero la tarifa del grado."}
                </p>
              )}
              {plan && <EnrollmentPaymentPlanAmounts plan={plan} />}
              <p>
                La aprobación registra la matrícula y sus pensiones si las tres condiciones se cumplen. Si alguna falla, se guarda el rechazo con su motivo.
              </p>
            </section>
          ) : (
            <label className="field">
              Motivo del rechazo
              <textarea
                required
                maxLength={700}
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                disabled={busy}
                rows={3}
              />
            </label>
          )}
          <div className="form-actions">
            <button type="button" className="secondary-action" disabled={busy} onClick={() => begin(null)}>Volver al detalle</button>
            <button type="submit" className="primary-action" disabled={busy || (action === "aprobar" && !plan)}>
              {busy ? "Guardando…" : action === "aprobar" ? "Confirmar revisión" : "Confirmar rechazo"}
            </button>
          </div>
        </form>
      )}
      {(!mayReview || !action) && (
        <div className="form-actions">
          {mayReview && <>
            <button type="button" className="secondary-action" onClick={() => begin("rechazar")}>Rechazar</button>
            <button type="button" className="primary-action" onClick={() => begin("aprobar")}>Revisar y aprobar</button>
          </>}
          <button type="button" className="secondary-action" disabled={busy} onClick={onClose}>Cerrar</button>
        </div>
      )}
    </Dialog>
  );
}

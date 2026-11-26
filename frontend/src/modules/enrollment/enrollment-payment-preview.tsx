"use client";
import { useEffect, useState } from "react";
import { api, message } from "@/core/api/client";
import { Feedback } from "@/shared/ui";
import { EnrollmentPaymentPlanAmounts } from "@/modules/direct-enrollment";
import type { EnrollmentPaymentPlan } from "./types";

export function EnrollmentPaymentPreview({ requestId, revision, onPlan }: {
  requestId: number;
  revision: number;
  onPlan: (plan: EnrollmentPaymentPlan | null) => void;
}) {
  const [plan, setPlan] = useState<EnrollmentPaymentPlan | null>(null);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    Promise.resolve().then(async () => {
      if (!active) return;
      onPlan(null); setPlan(null); setError("");
      const value = await api<EnrollmentPaymentPlan>(`/api/configuracion-pagos/planes/solicitudes/${requestId}/preview`);
      if (active) { setPlan(value); onPlan(value); }
    })
      .catch((caught) => { if (active) setError(message(caught)); });
    return () => { active = false; };
  }, [requestId, revision, retry, onPlan]);

  return <section aria-label="Pensiones de la matrícula">
    <h4>Plan de pensiones</h4>
    <p>Al finalizar se registrarán las diez mensualidades de marzo a diciembre. El apoderado podrá consultarlas y elegir cuáles pagar.</p>
    <Feedback error={error} />
    {!plan && !error && <p role="status">Consultando las pensiones y los beneficios del estudiante…</p>}
    {(error || (plan && !plan.puedeGenerar)) && <>
      {plan?.mensaje && <p role="alert">{plan.mensaje}</p>}
      <p>Revisa la tarifa del año y grado en Finanzas antes de finalizar la matrícula.</p>
    </>}
    <button type="button" className="secondary-action" onClick={() => setRetry((value) => value + 1)}>Actualizar plan de pensiones</button>
    {plan && <EnrollmentPaymentPlanAmounts plan={plan} />}
  </section>;
}

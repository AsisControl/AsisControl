"use client";
import { useEffect, useState } from "react";
import { api, message } from "@/core/api/client";
import { Feedback } from "@/shared/ui";
import { dueDateLabel, moneyLabel } from "@/modules/finance";
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

export function EnrollmentPaymentPlanAmounts({ plan }: { plan: EnrollmentPaymentPlan }) {
  if (!plan.items?.length) return null;
  return (
    <>
      <div className="user-table-wrap">
        <table className="user-table" aria-label="Pensiones que se asignarán">
          <thead>
            <tr>
              <th>Mensualidad</th>
              <th>Vencimiento</th>
              <th>Importe regular</th>
              <th>Beneficio</th>
              <th>Monto final</th>
            </tr>
          </thead>
          <tbody>
            {plan.items.map((item) => (
              <tr key={item.mes}>
                <td>{item.concepto}</td>
                <td>{dueDateLabel(item.fechaVencimiento)}</td>
                <td>{moneyLabel(item.montoBase)}</td>
                <td>
                  {item.beneficioNombre ? (
                    <>
                      {item.beneficioNombre}
                      <small>−{moneyLabel(item.montoDescuento)}</small>
                    </>
                  ) : "Sin beneficio"}
                </td>
                <td>{item.montoTotal === 0 ? "Exonerado" : moneyLabel(item.montoTotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p>
        <strong>Total de las diez pensiones: {moneyLabel(plan.items.reduce((sum, item) => sum + item.montoTotal, 0))}</strong>
      </p>
      <p className="hint">
        Cada pensión vence el último día de su mes. Finalizar la matrícula no realiza un cobro automático.
      </p>
    </>
  );
}

import { dueDateLabel, moneyLabel } from "@/modules/finance";
import type { EnrollmentPaymentPlan } from "./types";

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

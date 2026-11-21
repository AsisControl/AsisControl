"use client";
import { useRef, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { ApiError, message } from "@/core/api/client";
import { Dialog, Feedback, StatusPill } from "@/shared/ui";
import type { PaymentCommitment } from "@/modules/finance";
import { authenticateCard, confirmCard, getOrder, startOrder } from "./services/payment-service";
import { countdownLabel, isPending, moneyLabel, orderLabels, paymentLine, walletUrl } from "./payment-format";
import { useCountdown, useOrderMonitor } from "./use-order-monitor";
import { CardPaymentForm } from "./card-payment-form";
import { OrderSummary } from "./order-summary";
import { ReceiptPanel } from "./receipt-panel";
import { PaymentTabs } from "./payment-tabs";
import { paymentRequestKey } from "./payment-request-key";
import type { PaymentMethod, PaymentOrder } from "./types";
import styles from "./guardian-payments.module.css";

type PaymentAction = "start" | "confirm" | "lookup";

export function PaymentCheckout({ obligations, initialOrder, onClose, onSettled, onFollowPayments, onExpired }: {
  obligations: PaymentCommitment[];
  initialOrder?: PaymentOrder;
  onClose: () => void;
  onSettled: (order: PaymentOrder) => void;
  onFollowPayments?: () => void;
  onExpired?: () => void;
}) {
  const [method, setMethod] = useState<PaymentMethod>(initialOrder?.metodo ?? "TARJETA");
  const [order, setOrder] = useState<PaymentOrder | null>(initialOrder ?? null);
  const [activeAction, setActiveAction] = useState<PaymentAction | null>(null);
  const busy = activeAction !== null;
  const [error, setError] = useState("");
  const [cardReferenceError, setCardReferenceError] = useState("");
  const [awaitingResult, setAwaitingResult] = useState(false);
  const [conflict, setConflict] = useState(false);
  const inFlight = useRef(false);
  const startButton = useRef<HTMLButtonElement>(null);
  const latest = useRef(order);
  const notified = useRef<number | null>(null);
  const keys = useRef<Partial<Record<PaymentMethod, string>>>({});
  function update(next: PaymentOrder) {
    // Una consulta iniciada antes de confirmar no debe devolver la pantalla a un paso anterior.
    const previous = latest.current;
    const bypassesAuthentication = previous?.estado === "REQUIERE_AUTENTICACION" && next.estado === "PENDIENTE";
    if (previous?.id === next.id && isPending(next.estado)
      && (!isPending(previous.estado) || bypassesAuthentication)) return;
    latest.current = next; setOrder(next);
    setMethod(next.metodo);
    if (!isPending(next.estado)) {
      setError("");
      setAwaitingResult(false);
    }
    if (!isPending(next.estado) && notified.current !== next.id) {
      notified.current = next.id; onSettled(next);
    }
  }
  const pollError = useOrderMonitor(order, () => getOrder(order!.id), update);
  const pending = !!order && isPending(order.estado);
  const expired = order?.estado === "EXPIRADO";
  const remaining = useCountdown(order?.expiraEn ?? null, pending);
  async function perform(action: () => Promise<PaymentOrder>, kind: PaymentAction) {
    if (inFlight.current) return;
    inFlight.current = true;
    setActiveAction(kind);
    setError("");
    setCardReferenceError("");
    setConflict(false);
    try {
      const next = await action();
      if (kind === "lookup") setAwaitingResult(false);
      update(next);
    } catch (caught) {
      if (!latest.current || isPending(latest.current.estado)) {
        const failure = caught instanceof ApiError ? caught : null;
        const current = latest.current;
        const field = failure?.fieldErrors.find(({ field }) => field === "tarjetaPrueba");
        if (kind === "confirm" && current && (!failure || failure.status >= 500)) {
          setAwaitingResult(true);
          if (failure?.code === "PAGO_SERVICIO_NO_DISPONIBLE") setError(failure.message);
        } else if (current && failure?.code === "TRANSICION_PAGO_INVALIDA") {
          setAwaitingResult(true);
          try {
            update(await getOrder(current.id));
            setAwaitingResult(false);
          } catch { /* El sondeo volverá a consultar. */ }
        } else if (field) {
          setCardReferenceError(field.message);
        } else if (failure?.code === "PAGO_EN_CURSO") {
          setConflict(true);
        } else {
          if (kind === "lookup") setError("No pudimos consultar el estado. Inténtalo nuevamente.");
          else if (!failure && keys.current[method]) setError("No pudimos preparar la operación. Revisa tu conexión e inténtalo nuevamente.");
          else setError(message(caught));
        }
      }
    }
    finally { inFlight.current = false; setActiveAction(null); }
  }
  function begin() {
    return perform(() => {
      const key = keys.current[method] ??= paymentRequestKey();
      return startOrder(obligations.map((item) => item.id), method, key);
    }, "start");
  }
  function retry() {
    keys.current = {}; latest.current = null; notified.current = null;
    setOrder(null); setError(""); setCardReferenceError(""); setAwaitingResult(false); setConflict(false);
  }
  function closeConflict() {
    setConflict(false);
    requestAnimationFrame(() => startButton.current?.focus());
  }
  function leaveExpiredPayment() {
    onClose();
    onExpired?.();
  }
  const url = walletUrl(order?.qrUrl ?? null);
  const validTotal = !!order && Number.isFinite(order.montoTotal) && order.montoTotal > 0 && order.items.length > 0;
  const checkout = <Dialog title={order?.estado === "COMPLETADO" ? "Pago aprobado" : "Pagar obligaciones"}
    description={order ? "Consulta el importe y el estado de tu operación." : "Revisa tus obligaciones y elige cómo pagar."}
    size="lg" onClose={onClose} busy={busy} active={!conflict && !expired}>
    <Feedback error={error} />
    {!order ? <>
      <PaymentTabs label="Medio de pago" selected={method} onSelect={(value) => setMethod(value as PaymentMethod)} disabled={busy}
        items={[{ id: "TARJETA", label: "Tarjeta" }, { id: "QR", label: "Código QR" }]}>
      <ul className={styles.itemList}>{obligations.map((item) => <li key={item.id}><span><strong>{item.alumno}</strong><small>{paymentLine(item)}</small></span><strong>{moneyLabel(item.saldo, "PEN")}</strong></li>)}</ul>
      <dl className={styles.totals}>
        <div className={styles.total}><dt>Total seleccionado</dt><dd>{moneyLabel(obligations.reduce((sum, item) => sum + item.saldo, 0), "PEN")}</dd></div>
      </dl>
      <p className={styles.hint}>Pago simulado. No se realizará un cargo bancario.</p>
      <div className="form-actions"><button type="button" className="secondary-action" disabled={busy} onClick={onClose}>Cancelar</button>
        <button ref={startButton} type="button" className="primary-action" disabled={busy || !obligations.length} onClick={begin}>{busy ? "Preparando…" : method === "QR" ? "Generar código QR" : "Continuar con tarjeta"}</button></div>
      </PaymentTabs>
    </> : <>
      {pending && initialOrder && <p role="status" className={styles.notice}>Retomaste el pago {order.codigo} que ya estaba pendiente. Conserva su medio de pago y el tiempo disponible.</p>}
      <StatusPill state={order.estado} label={orderLabels[order.estado]} tone={order.estado === "COMPLETADO" ? "success" : order.estado === "RECHAZADO" ? "danger" : "info"} />
      {order.estado === "COMPLETADO" ? <ReceiptPanel orderId={order.id} /> : <>
        <OrderSummary order={order} />
        {!expired && order.mensaje && <p role={order.estado === "RECHAZADO" ? "alert" : "status"} className={styles.notice}>{order.mensaje}</p>}
        {pending && <>
          <p className={styles.timer}>Tiempo disponible: <strong>{countdownLabel(remaining)}</strong></p>
          {remaining === 0 ? <p role="status" className={styles.notice}>Estamos verificando el estado de la operación…</p>
          : order.metodo === "QR" ? <div className={styles.qrPanel}>
            {url ? <><div className={styles.qrCode}><QRCodeSVG value={url} size={208} title="Escanea para abrir la billetera" level="M" /></div>
              <p role="status">Esperando confirmación en la billetera…</p>
              <p className={styles.hint}>Escanea el código con tu celular o abre la billetera en esta pantalla.</p>
              <a className="secondary-action" href={url} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer">Abrir billetera en esta pantalla</a></>
              : <p role="alert">No pudimos preparar el enlace del QR. Cierra la ventana e inténtalo nuevamente.</p>}
          </div> : awaitingResult ? <div role="status" className={styles.notice}>
            <strong>Todavía estamos verificando tu pago.</strong>
            <p>Consulta el estado de la operación {order.codigo} antes de volver a pagar.</p>
          </div> : order.estado === "REQUIERE_AUTENTICACION" ? <section className={styles.challenge} aria-label="Autenticación adicional">
            <h4>Confirma la autenticación adicional</h4><p>Completa la verificación adicional para continuar.</p>
            <div className={styles.actions}><button type="button" className="primary-action" disabled={busy || remaining === 0} onClick={() => perform(() => authenticateCard(order.id, true), "confirm")}>Completar autenticación</button>
              <button type="button" className="secondary-action" disabled={busy} onClick={() => perform(() => authenticateCard(order.id, false), "confirm")}>Cancelar autenticación</button></div>
          </section> : <>
            {!validTotal && <Feedback error="No pudimos validar el importe. Vuelve al estado de cuenta y revisa tu selección." />}
            <CardPaymentForm key={`${order.id}-${remaining > 0}`} total={moneyLabel(order.montoTotal, "PEN")}
              enabled={!busy && remaining > 0 && order.simulador && validTotal} busy={activeAction === "confirm"}
              referenceError={cardReferenceError}
              onChange={() => { setCardReferenceError(""); setError(""); }}
              onConfirm={(reference) => perform(() => confirmCard(order.id, reference), "confirm")} />
          </>}
          {pollError && <p role="status" className={styles.notice}>No pudimos actualizar el estado. Seguiremos consultando; evita iniciar otro pago mientras confirmamos el resultado.</p>}
          <button type="button" className={awaitingResult ? "primary-action" : "secondary-action"} disabled={busy} onClick={() => perform(() => getOrder(order.id), "lookup")}>Consultar estado</button>
          <p className={styles.hint}>Puedes retomar esta operación desde Seguimiento de pagos mientras siga vigente.</p>
        </>}
      </>}
      <div className={styles.actions}>
        {order.estado === "RECHAZADO" && obligations.length > 0 && <button type="button" className="primary-action" onClick={retry}>Intentar de nuevo</button>}
        <button type="button" className={`secondary-action ${styles.closeButton}`} disabled={busy} onClick={onClose}>Cerrar</button>
      </div>
    </>}
  </Dialog>;

  return (
    <>
      {checkout}
      {expired ? (
        <Dialog
          title="Se acabó el tiempo para pagar"
          description="La operación expiró. Vuelve a Obligaciones para iniciar un nuevo pago."
          role="alertdialog"
          onClose={leaveExpiredPayment}
        >
          <div className="form-actions">
            <button type="button" className="primary-action" onClick={leaveExpiredPayment}>Volver a Obligaciones</button>
          </div>
        </Dialog>
      ) : conflict && (
        <Dialog
          title="Pago en curso"
          description="Ya hay un pago en curso para uno o más cargos seleccionados. Consulta su estado o espera a que termine antes de volver a intentarlo."
          role="alertdialog"
          onClose={closeConflict}
        >
          <div className="form-actions">
            <button type="button" className="secondary-action" onClick={closeConflict}>Volver al pago</button>
            {onFollowPayments && (
              <button type="button" className="primary-action" onClick={onFollowPayments}>Ir a Seguimiento de pagos</button>
            )}
          </div>
        </Dialog>
      )}
    </>
  );
}

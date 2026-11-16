"use client";
import { useEffect, useEffectEvent, useState } from "react";
import { message } from "@/core/api/client";
import { isPending } from "./payment-format";
import type { PaymentOrder } from "./types";

/** Una consulta a la vez; las respuestas se descartan al cerrar o cambiar de orden. */
export function useOrderMonitor(order: PaymentOrder | null, fetchOrder: () => Promise<PaymentOrder>, onUpdate: (next: PaymentOrder) => void) {
  const [pollError, setPollError] = useState("");
  const fetchLatest = useEffectEvent(fetchOrder);
  const update = useEffectEvent(onUpdate);
  const pending = !!order && isPending(order.estado);
  const id = order?.id;
  useEffect(() => {
    if (!pending || id == null) return;
    let active = true, inFlight = false;
    const timer = setInterval(async () => {
      if (inFlight) return;
      inFlight = true;
      try {
        const next = await fetchLatest();
        if (active) { setPollError(""); update(next); }
      } catch (error) {
        if (active) setPollError(message(error));
      } finally { inFlight = false; }
    }, 2000);
    return () => { active = false; clearInterval(timer); };
  }, [pending, id]);
  return pollError;
}

export function useCountdown(expiresAt: string | null, active: boolean) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!active || !expiresAt) return;
    const refresh = setTimeout(() => setNow(Date.now()), 0);
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => { clearTimeout(refresh); clearInterval(timer); };
  }, [expiresAt, active]);
  return expiresAt ? Math.max(0, Math.ceil((Date.parse(expiresAt) - now) / 1000)) : 0;
}

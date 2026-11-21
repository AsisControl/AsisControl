"use client";

import { useEffect, useId, useState, type ChangeEvent, type FormEvent, type InputHTMLAttributes } from "react";
import styles from "./guardian-payments.module.css";

// Son números ficticios de integración. Solo se comunica la referencia al simulador.
const TEST_CARDS: Record<string, { reference: string; code: string }> = {
  "4242424242424242": { reference: "TP-001", code: "123" },
  "378282246310005": { reference: "TP-001", code: "1234" },
  "4000000000009995": { reference: "TP-002", code: "123" },
  "4000000000000069": { reference: "TP-003", code: "123" },
  "4000000000000002": { reference: "TP-004", code: "123" },
  "4000000000000127": { reference: "TP-005", code: "123" },
  "4000000000000119": { reference: "TP-006", code: "123" },
  "4000000000000341": { reference: "TP-007", code: "123" },
  "4100000000000019": { reference: "TP-008", code: "123" },
  "4000002500003155": { reference: "TP-009", code: "123" },
  "4000000000000259": { reference: "TP-010", code: "123" },
  "4000000000000077": { reference: "TP-011", code: "123" },
};

const EMPTY_CARD = { number: "", holder: "", expiration: "", securityCode: "" };
type CardFieldName = keyof typeof EMPTY_CARD;
type CardErrors = Partial<Record<CardFieldName, string>>;
const CARD_FIELDS: CardFieldName[] = ["number", "holder", "expiration", "securityCode"];
const CARD_MONTH = new Intl.DateTimeFormat("en-US", {
  timeZone: "America/Lima", year: "numeric", month: "numeric",
});

function normalizeCardField(field: CardFieldName, input: string) {
  if (field === "holder") return input.replace(/[^\p{L}\p{M} .’'-]/gu, "").slice(0, 80);

  const digits = input.replace(/\D/g, "");
  if (field === "number") return digits.slice(0, 16);
  if (field === "securityCode") return digits.slice(0, 4);

  const expiration = digits.slice(0, 4);
  return expiration.length > 2 ? expiration.slice(0, 2) + "/" + expiration.slice(2) : expiration;
}

function validateCard(values: typeof EMPTY_CARD) {
  const errors: CardErrors = {};
  const number = values.number.replace(/[\s-]/g, "");
  const card = Object.hasOwn(TEST_CARDS, number) ? TEST_CARDS[number] : undefined;
  const holder = values.holder.trim();
  const expiration = /^(0[1-9]|1[0-2])\/(\d{2})$/.exec(values.expiration.trim());
  const code = values.securityCode.trim();

  if (!card) errors.number = "Revisa el número e ingresa una tarjeta de prueba válida.";
  if (holder.length < 2 || holder.length > 80 || !/^\p{L}[\p{L}\p{M} .’'\-]*$/u.test(holder)) {
    errors.holder = "Ingresa el nombre que aparece en la tarjeta de prueba.";
  }
  if (!expiration) {
    errors.expiration = "Ingresa una fecha válida en formato MM/AA.";
  } else {
    const current = CARD_MONTH.formatToParts(new Date());
    const currentMonth = Number(current.find((part) => part.type === "month")?.value);
    const currentYear = Number(current.find((part) => part.type === "year")?.value);
    const expiryYear = 2000 + Number(expiration[2]);
    if (expiryYear * 12 + Number(expiration[1]) < currentYear * 12 + currentMonth) {
      errors.expiration = "La tarjeta está vencida. Usa otra.";
    }
  }
  if (!/^\d{3,4}$/.test(code) || (card && code.length !== card.code.length)) {
    errors.securityCode = "Revisa el código de seguridad de la tarjeta.";
  }
  const reference = card && (card.code === code ? card.reference : "TP-005");
  return { errors, reference };
}

function CardField({ id, label, error, hint, ...input }: InputHTMLAttributes<HTMLInputElement> & {
  id: string;
  label: string;
  error?: string;
  hint?: string;
}) {
  return <div className="field">
    <label htmlFor={id}>{label}</label>
    <input {...input} id={id} required autoComplete="off" spellCheck={false}
      aria-invalid={error ? true : undefined}
      aria-describedby={error || hint ? `${id}-description` : undefined} />
    {(error || hint) && <small id={`${id}-description`} className={error ? "field-error" : styles.hint}
      role={error ? "alert" : undefined}>{error || hint}</small>}
  </div>;
}

export function CardPaymentForm({ total, enabled, busy, referenceError, onChange, onConfirm }: {
  total: string;
  enabled: boolean;
  busy: boolean;
  referenceError: string;
  onChange: () => void;
  onConfirm: (reference: string) => void;
}) {
  const formId = useId();
  const [values, setValues] = useState(EMPTY_CARD);
  const [touched, setTouched] = useState<Partial<Record<CardFieldName, boolean>>>({});
  const validation = validateCard(values);
  const firstError = CARD_FIELDS.find((field) => validation.errors[field]);

  useEffect(() => {
    document.getElementById(`${formId}-number`)?.focus({ preventScroll: true });
  }, [formId]);

  useEffect(() => {
    if (referenceError) document.getElementById(`${formId}-number`)?.focus();
  }, [formId, referenceError]);

  function bindField(field: CardFieldName) {
    return {
      id: `${formId}-${field}`,
      value: values[field],
      disabled: !enabled,
      error: field === "number" && referenceError ? referenceError : touched[field] ? validation.errors[field] : undefined,
      onChange: (event: ChangeEvent<HTMLInputElement>) => {
        const value = normalizeCardField(field, event.currentTarget.value);
        setValues((current) => ({ ...current, [field]: value }));
        onChange();
      },
      onBlur: () => {
        setTouched((current) => ({ ...current, [field]: true }));
        if (field === "number") {
          const compact = values.number.replace(/[\s-]/g, "");
          if (/^\d+$/.test(compact)) setValues((current) => ({ ...current, number: compact.match(/.{1,4}/g)?.join(" ") ?? compact }));
        }
      },
    };
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!enabled || referenceError) return;
    if (firstError || !validation.reference) {
      setTouched({ number: true, holder: true, expiration: true, securityCode: true });
      if (firstError) document.getElementById(`${formId}-${firstError}`)?.focus();
      return;
    }
    setValues(EMPTY_CARD);
    setTouched({});
    onConfirm(validation.reference);
  }

  return <section className={styles.cardPanel} aria-label="Pago con tarjeta">
    <div className={styles.cardMethod}>
      <span className={styles.cardIcon} aria-hidden="true" />
      <div>
        <h4>Pago con tarjeta</h4>
        <p>Usa una tarjeta de prueba. Esta operación es simulada.</p>
      </div>
    </div>
    <form className={styles.cardForm} autoComplete="off" noValidate onSubmit={submit}>
      <CardField {...bindField("number")} label="Número de tarjeta" inputMode="numeric" />
      <CardField {...bindField("holder")} label="Nombre del titular" />
      <div className={styles.cardFieldRow}>
        <CardField {...bindField("expiration")} label="Vencimiento" hint="MM/AA" inputMode="numeric" />
        <CardField {...bindField("securityCode")} label="Código de seguridad (CVV)" type="password" inputMode="numeric" />
      </div>
      <div className={styles.cardSubmit}>
        {busy ? <p role="status" className={styles.hint}>Procesando tu pago…</p>
          : enabled && firstError && <p id={`${formId}-complete`} className={styles.hint}>Completa los datos de la tarjeta para continuar.</p>}
        <button type="submit" className="primary-action" disabled={!enabled || !!firstError || !!referenceError}
          aria-describedby={enabled && firstError ? `${formId}-complete` : undefined}>{`Pagar ${total}`}</button>
      </div>
    </form>
  </section>;
}

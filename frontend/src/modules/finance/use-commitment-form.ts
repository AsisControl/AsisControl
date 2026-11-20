"use client";
import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { ApiError, message } from "@/core/api/client";
import { annulCommitment, issueCommitment, updateCommitment } from "./services/commitment-service";
import type { EligibleEnrollment, PaymentCommitment } from "./types";
import { getConcepts, type ChargeConcept } from "@/modules/payment-settings";

type Draft = {
  concepto: string;
  idConcepto: string;
  referenciaCobro: string;
  numeroCuota: string;
  montoTotal: string;
  fechaVencimiento: string;
  descripcion: string;
  motivo: string;
};
type FieldErrors = Partial<Record<keyof Draft | "matricula", string>>;
export type CommitmentFormProps = {
  commitment?: PaymentCommitment;
  action?: "create" | "edit" | "annul";
  onClose: () => void;
  onSaved: (commitment: PaymentCommitment) => void;
};

function validateDraft(draft: Draft, isCreate: boolean, isAnnul: boolean, hasEnrollment: boolean) {
  const errors: FieldErrors = {};
  if (isCreate && !hasEnrollment) {
    errors.matricula = "Selecciona la matrícula del estudiante.";
  }
  if (isCreate && !draft.idConcepto) {
    errors.idConcepto = "Selecciona el concepto del compromiso.";
  }
  if (isCreate && !draft.referenciaCobro.trim()) {
    errors.referenciaCobro = "Indica una referencia para identificar este cobro.";
  }
  const amount = Number(draft.montoTotal);
  if (!isAnnul && (
    !/^\d+(\.\d{1,2})?$/.test(draft.montoTotal.trim()) ||
    amount <= 0 || amount > 9999999999.99
  )) {
    errors.montoTotal = "Ingresa un monto mayor que cero, con hasta dos decimales.";
  }
  if (!isAnnul && !draft.fechaVencimiento) {
    errors.fechaVencimiento = "Selecciona la fecha de vencimiento.";
  }
  if (!isCreate && !draft.motivo.trim()) {
    errors.motivo = "Explica el motivo para registrar este cambio.";
  }
  return errors;
}

export function useCommitmentForm({
  commitment, action = "create", onClose, onSaved,
}: CommitmentFormProps) {
  const [enrollment, setEnrollment] = useState<EligibleEnrollment | null>(null);
  const [draft, setDraft] = useState<Draft>({
    concepto: commitment?.concepto ?? "",
    idConcepto: String(commitment?.idConcepto ?? ""),
    referenciaCobro: commitment?.referenciaCobro ?? "",
    numeroCuota: String(commitment?.numeroCuota ?? ""),
    montoTotal: String(commitment?.montoTotal ?? ""),
    fechaVencimiento: commitment?.fechaVencimiento ?? "",
    descripcion: commitment?.descripcion ?? "",
    motivo: "",
  });
  const [dirty, setDirty] = useState(false);
  const [discard, setDiscard] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [errors, setErrors] = useState<FieldErrors>({});
  const formRef = useRef<HTMLFormElement>(null);
  const errorRef = useRef<HTMLDivElement>(null);
  const isCreate = action === "create";
  const isAnnul = action === "annul";
  const [concepts, setConcepts] = useState<ChargeConcept[]>([]);
  const [conceptLoading, setConceptLoading] = useState(action === "create");
  const [conceptError, setConceptError] = useState("");
  const [conceptRevision, setConceptRevision] = useState(0);
  useEffect(() => {
    if (!isCreate) return;
    let active = true;
    getConcepts().then((items) => { if (active) { setConcepts(items.filter((item) => item.activo && !item.esPension && !item.requiereAceptacion)); setConceptError(""); setConceptLoading(false); } })
      .catch((caught) => { if (active) { setConceptError(message(caught)); setConceptLoading(false); } });
    return () => { active = false; };
  }, [isCreate, conceptRevision]);
  function selectConcept(id: string) {
    const item = concepts.find((value) => value.id === Number(id));
    setDraft((previous) => ({ ...previous, idConcepto: id, concepto: item?.nombre ?? "", montoTotal: item?.montoSugerido == null ? "" : String(item.montoSugerido) }));
    setErrors((previous) => ({ ...previous, idConcepto: undefined })); setDirty(true); setDiscard(false);
  }

  function change(field: keyof Draft, value: string) {
    setDraft((previous) => ({ ...previous, [field]: value }));
    setErrors((previous) => ({ ...previous, [field]: undefined }));
    setDirty(true);
    setDiscard(false);
  }

  function selectEnrollment(value: EligibleEnrollment | null) {
    setEnrollment(value);
    setErrors((previous) => ({ ...previous, matricula: undefined }));
    setDirty(true);
    setDiscard(false);
  }

  function requestClose() {
    if (busy) return;
    if (dirty) setDiscard(true);
    else onClose();
  }

  function focusFirst(fieldErrors: FieldErrors) {
    const first = Object.keys(fieldErrors)[0];
    const selector = first === "matricula" ? "[data-enrollment-control]" : `[name="${first}"]`;
    const target = formRef.current?.querySelector<HTMLElement>(selector);
    if (target) target.focus();
    else errorRef.current?.focus();
  }

  async function save() {
    if (isCreate) {
      return issueCommitment({
        idMatricula: enrollment!.id,
        concepto: draft.concepto.trim(),
        idConcepto: Number(draft.idConcepto),
        referenciaCobro: draft.referenciaCobro.trim(),
        numeroCuota: null,
        montoTotal: Number(draft.montoTotal),
        moneda: "PEN",
        fechaVencimiento: draft.fechaVencimiento,
        descripcion: draft.descripcion.trim() || null,
      });
    }
    if (isAnnul) {
      return annulCommitment(commitment!.id, draft.motivo.trim(), commitment!.version);
    }
    return updateCommitment(commitment!.id, {
      montoTotal: Number(draft.montoTotal),
      fechaVencimiento: draft.fechaVencimiento,
      motivo: draft.motivo.trim(),
      version: commitment!.version,
    });
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const nextErrors = validateDraft(draft, isCreate, isAnnul, !!enrollment);
    setErrors(nextErrors);
    setError("");
    if (Object.keys(nextErrors).length) {
      focusFirst(nextErrors);
      return;
    }
    setBusy(true);
    try {
      onSaved(await save());
    } catch (caught) {
      setError(message(caught));
      if (caught instanceof ApiError && caught.fieldErrors.length) {
        const fields = Object.fromEntries(caught.fieldErrors.map((entry) => [
          entry.field === "idMatricula" ? "matricula" : entry.field === "concepto" ? "idConcepto" : entry.field,
          entry.message,
        ]));
        setErrors(fields);
        requestAnimationFrame(() => focusFirst(fields));
      } else {
        requestAnimationFrame(() => errorRef.current?.focus());
      }
    } finally {
      setBusy(false);
    }
  }

  function field(name: keyof Draft) {
    return {
      name,
      value: draft[name],
      disabled: busy,
      error: errors[name],
      onChange: (event: ChangeEvent<HTMLInputElement>) => change(name, event.target.value),
    };
  }

  return {
    draft, enrollment, errors, error, busy, discard, setDiscard,
    isCreate, isAnnul, formRef, errorRef, change, field,
    selectEnrollment, requestClose, submit,
    concepts, conceptLoading, conceptError, selectConcept,
    retryConcepts() { setConceptLoading(true); setConceptRevision((value) => value + 1); },
  };
}

"use client";
import { Dialog, Feedback, Field } from "@/shared/ui";
import { EnrollmentPicker } from "./enrollment-picker";
import { DiscardCommitmentDialog } from "./discard-commitment-dialog";
import { useCommitmentForm, type CommitmentFormProps } from "./use-commitment-form";
import { dueDateLabel, quotaLabel, moneyLabel } from "./commitment-format";
import styles from "./finance.module.css";

export function CommitmentForm(props: CommitmentFormProps) {
  const { commitment, onClose } = props;
  const {
    draft, enrollment, errors, error, busy, discard, setDiscard,
    isCreate, isAnnul, formRef, errorRef, change, field,
    selectEnrollment, requestClose, submit,
  } = useCommitmentForm(props);
  const title = isCreate
    ? "Emitir compromiso de pago"
    : isAnnul ? "Anular compromiso" : "Modificar compromiso";
  const submitLabel = isCreate
    ? "Emitir compromiso"
    : isAnnul ? "Confirmar anulación" : "Guardar cambios";

  return (
    <>
      <Dialog
        title={title}
        size={isCreate ? "lg" : "md"}
        onClose={requestClose}
        busy={busy}
        active={!discard}
        description={isCreate
          ? "Registra una obligación en soles para una matrícula vigente."
          : <span className={styles.codeText}>{commitment!.alumno} · {commitment!.codigoCompromiso}</span>}
      >
        <form ref={formRef} noValidate onSubmit={submit} aria-busy={busy}>
          <div ref={errorRef} tabIndex={-1}><Feedback error={error} /></div>
          <div className="form-grid">
            {isCreate && (
              <EnrollmentPicker
                selected={enrollment}
                disabled={busy}
                onSelect={selectEnrollment}
              />
            )}
            {errors.matricula && (
              <p className={`full-field ${styles.fieldError}`} role="alert">
                {errors.matricula}
              </p>
            )}
            {isCreate && (
              <>
                <h4 className={`full-field ${styles.pickerTitle}`}>
                  2. Completa el compromiso
                </h4>
                <Field
                  label="Concepto"
                  required
                  maxLength={160}
                  placeholder="Ej. Pensión de marzo"
                  {...field("concepto")}
                />
                <Field
                  label="Número de cuota"
                  required
                  type="number"
                  min={1}
                  step={1}
                  placeholder="Ej. 1"
                  {...field("numeroCuota")}
                />
              </>
            )}
            {isAnnul ? (
              <div className="full-field">
                <p className={styles.amountSummary}>
                  <strong>{commitment!.concepto}</strong> · {quotaLabel(commitment!.numeroCuota)}
                </p>
                <p>
                  {moneyLabel(commitment!.montoTotal, commitment!.moneda)} · Vence el{" "}
                  {dueDateLabel(commitment!.fechaVencimiento)}
                </p>
                <p className={styles.notice}>
                  Al confirmar, el compromiso quedará anulado. Podrás seguir consultándolo
                  junto con su motivo de anulación.
                </p>
              </div>
            ) : (
              <>
                <Field
                  label="Monto en soles (S/)"
                  required
                  type="number"
                  min="0.01"
                  max="9999999999.99"
                  step="0.01"
                  placeholder="Ej. 350.00"
                  {...field("montoTotal")}
                />
                <Field
                  label="Fecha de vencimiento"
                  required
                  type="date"
                  {...field("fechaVencimiento")}
                />
                {isCreate && (
                  <label className="field full-field">
                    Descripción (opcional)
                    <textarea
                      name="descripcion"
                      value={draft.descripcion}
                      maxLength={600}
                      rows={2}
                      disabled={busy}
                      placeholder="Añade información que ayude a reconocer este compromiso."
                      onChange={(event) => change("descripcion", event.target.value)}
                    />
                  </label>
                )}
              </>
            )}
            {!isCreate && (
              <label className={`field ${styles.reason}`}>
                {isAnnul ? "Motivo de anulación" : "Motivo del cambio"} *
                <textarea
                  name="motivo"
                  required
                  maxLength={500}
                  rows={3}
                  value={draft.motivo}
                  disabled={busy}
                  aria-invalid={!!errors.motivo}
                  aria-describedby={errors.motivo ? "commitment-reason-error" : undefined}
                  placeholder={isAnnul
                    ? "Explica por qué corresponde anular este compromiso."
                    : "Explica qué necesitas corregir y por qué."}
                  onChange={(event) => change("motivo", event.target.value)}
                />
                {errors.motivo && (
                  <small id="commitment-reason-error" className={styles.fieldError} role="alert">
                    {errors.motivo}
                  </small>
                )}
              </label>
            )}
          </div>
          {isCreate && (
            <p className="hint">El código del compromiso se asignará al emitirlo.</p>
          )}
          <div className="form-actions">
            <button type="button" disabled={busy} onClick={requestClose}>Cancelar</button>
            <button
              className={isAnnul ? "primary-action danger" : "primary-action"}
              disabled={busy}
            >
              {busy ? "Guardando…" : submitLabel}
            </button>
          </div>
        </form>
      </Dialog>
      {discard && (
        <DiscardCommitmentDialog
          onContinue={() => setDiscard(false)}
          onDiscard={onClose}
        />
      )}
    </>
  );
}

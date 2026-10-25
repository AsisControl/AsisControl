"use client";
import { StudentField } from "./student-field";
import {
  birthLabel,
  STUDENT_DOCUMENT_RULES,
  studentBirthRange,
} from "./student-rules";
import type { Homonym } from "./types";
import type { StudentFormState } from "./use-student-form";
import styles from "./students.module.css";

type IdentityFieldsProps = Pick<
  StudentFormState,
  "draft" | "errors" | "bindField" | "changeField" | "validateFieldOnBlur"
>;

export function StudentIdentityFields({
  draft,
  errors,
  bindField,
  changeField,
  validateFieldOnBlur,
}: IdentityFieldsProps) {
  const birthRange = studentBirthRange();
  const documentRule =
    STUDENT_DOCUMENT_RULES[
      draft.tipoDocumento as keyof typeof STUDENT_DOCUMENT_RULES
    ];
  return (
    <fieldset className={styles.formSection}>
      <legend>Datos de identidad</legend>
      <div className="form-grid">
        <StudentField
          label="Nombres"
          {...bindField("nombres")}
          required
          minLength={2}
          maxLength={100}
          autoComplete="given-name"
        />
        <StudentField
          label="Primer apellido"
          {...bindField("apellidoPaterno")}
          required
          minLength={2}
          maxLength={80}
          autoComplete="family-name"
        />
        <StudentField
          label="Segundo apellido"
          {...bindField("apellidoMaterno")}
          maxLength={80}
          autoComplete="additional-name"
        />
        <StudentField
          label="Fecha de nacimiento"
          {...bindField("fechaNacimiento")}
          required
          type="date"
          min={birthRange.min}
          max={birthRange.max}
          autoComplete="bday"
        />
        <div>
          <label className="field">
            Tipo de documento{draft.numeroDocumento ? " *" : ""}
            <select
              name="tipoDocumento"
              value={draft.tipoDocumento}
              onChange={(event) =>
                changeField("tipoDocumento", event.target.value)
              }
              onBlur={(event) =>
                validateFieldOnBlur("tipoDocumento", event.currentTarget.form)
              }
              required={!!draft.numeroDocumento}
              className={errors.tipoDocumento ? styles.invalid : undefined}
              aria-invalid={!!errors.tipoDocumento}
              aria-describedby={
                [
                  !draft.tipoDocumento ? "student-document-help" : "",
                  errors.tipoDocumento ? "student-document-type-error" : "",
                ]
                  .filter(Boolean)
                  .join(" ") || undefined
              }
            >
              <option value="">Sin documento por el momento</option>
              <option value="DNI">DNI</option>
              <option value="CARNET_EXTRANJERIA">Carné de extranjería</option>
              <option value="PASAPORTE">Pasaporte</option>
            </select>
          </label>
          {!draft.tipoDocumento && (
            <small id="student-document-help" className={styles.helper}>
              Puedes registrar el documento más adelante.
            </small>
          )}
          <small id="student-document-type-error" className={styles.fieldError}>
            {errors.tipoDocumento}
          </small>
        </div>
        <StudentField
          label="Número de documento"
          {...bindField("numeroDocumento")}
          required={!!draft.tipoDocumento}
          maxLength={documentRule?.max ?? 12}
          inputMode={draft.tipoDocumento !== "PASAPORTE" ? "numeric" : "text"}
          placeholder={documentRule?.placeholder}
        />
        <div>
          <label className="field">
            Sexo (opcional)
            <select
              name="sexo"
              value={draft.sexo}
              onChange={(event) => changeField("sexo", event.target.value)}
              onBlur={(event) =>
                validateFieldOnBlur("sexo", event.currentTarget.form)
              }
              className={errors.sexo ? styles.invalid : undefined}
              aria-invalid={!!errors.sexo}
              aria-describedby="student-sex-error"
            >
              <option value="">Selecciona una opción</option>
              <option value="FEMENINO">Femenino</option>
              <option value="MASCULINO">Masculino</option>
              <option value="OTRO">Otro</option>
              <option value="PREFIERE_NO_INDICAR">Prefiere no indicar</option>
            </select>
          </label>
          <small id="student-sex-error" className={styles.fieldError}>
            {errors.sexo}
          </small>
        </div>
      </div>
    </fieldset>
  );
}

export function StudentContactFields({
  bindField,
}: Pick<StudentFormState, "bindField">) {
  return (
    <fieldset className={styles.formSection}>
      <legend>Contacto y domicilio (opcionales)</legend>
      <div className="form-grid">
        <StudentField
          label="Correo de contacto"
          {...bindField("correo")}
          type="email"
          autoComplete="email"
          maxLength={150}
          placeholder="Ej. estudiante@correo.com"
        />
        <StudentField
          label="Teléfono"
          {...bindField("telefono")}
          type="tel"
          inputMode="numeric"
          autoComplete="tel-national"
          maxLength={9}
          placeholder="Ej. 987654321"
        />
        <div className={styles.full}>
          <StudentField
            label="Dirección"
            {...bindField("direccion")}
            maxLength={250}
            placeholder="Ej. Av. Los Olivos 123"
          />
        </div>
      </div>
    </fieldset>
  );
}

export function StudentCorrectionReason({
  draft,
  errors,
  changeField,
  validateFieldOnBlur,
}: Pick<
  IdentityFieldsProps,
  "draft" | "errors" | "changeField" | "validateFieldOnBlur"
>) {
  return (
    <label className="field">
      Motivo de la corrección *
      <textarea
        name="motivo"
        value={draft.motivo}
        onChange={(event) => changeField("motivo", event.target.value)}
        onBlur={(event) =>
          validateFieldOnBlur("motivo", event.currentTarget.form)
        }
        className={`${styles.reason} ${errors.motivo ? styles.invalid : ""}`}
        required
        minLength={10}
        maxLength={1000}
        rows={3}
        placeholder="Ej. Corrección del apellido según su documento."
        aria-invalid={!!errors.motivo}
        aria-describedby="student-reason-help student-reason-error"
      />
      <small id="student-reason-help" className={styles.helper}>
        Explica qué dato necesitas corregir y por qué.
      </small>
      <small id="student-reason-error" className={styles.fieldError}>
        {errors.motivo}
      </small>
    </label>
  );
}

export function StudentHomonymReview({
  matches,
  confirmed,
  onConfirm,
}: {
  matches: Homonym[];
  confirmed: boolean;
  onConfirm: (confirmed: boolean) => void;
}) {
  return (
    <div
      className={styles.warning}
      role="region"
      aria-label="Revisión de posibles coincidencias"
    >
      <strong>Revisa estas posibles coincidencias</strong>
      {matches.length ? (
        <>
          <ul>
            {matches.map((match) => (
              <li key={match.id}>
                {match.codigoAlumno} · {match.nombreCompleto} ·{" "}
                {birthLabel(match.fechaNacimiento)}
              </li>
            ))}
          </ul>
          <label className={styles.confirmation}>
            <input
              type="checkbox"
              checked={confirmed}
              onChange={(event) => onConfirm(event.target.checked)}
            />
            He revisado las coincidencias y confirmo que se trata de otra
            persona.
          </label>
        </>
      ) : (
        <p>
          Las coincidencias no están disponibles para revisión. Consulta el
          listado antes de continuar.
        </p>
      )}
    </div>
  );
}

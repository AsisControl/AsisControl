"use client";
import { passwordHint } from "@/core/auth/password-policy";
import { Field, PasswordField } from "@/shared/ui";
import { StepCard } from "./step-parts";
import styles from "./new-user.module.css";
/** Paso 3: usuario, correo y contraseña temporal del acceso. */
export function CredentialsStep({
  password,
  onPasswordChange,
  onGeneratePassword,
  done,
}: {
  password: string;
  onPasswordChange: (password: string) => void;
  onGeneratePassword: () => void;
  done: boolean;
}) {
  return (
    <StepCard
      number={3}
      title="Acceso"
      description="Usuario y correo únicos. La contraseña temporal se cambia al primer ingreso."
      done={done}
    >
      <div className="form-grid">
        <Field
          label="Usuario"
          name="username"
          required
          minLength={4}
          maxLength={80}
          autoComplete="off"
          placeholder="pmorales"
        />
        <Field
          label="Correo del acceso"
          name="correo"
          required
          type="email"
          maxLength={150}
          autoComplete="off"
          placeholder="nombre@colegio.edu.pe"
        />
      </div>
      <div className={styles.passwordRow}>
        <PasswordField
          label="Contraseña temporal"
          required
          value={password}
          onChange={(e) => onPasswordChange(e.target.value)}
          minLength={8}
          maxLength={64}
          autoComplete="new-password"
        />
        <button
          type="button"
          className={styles.generateButton}
          onClick={onGeneratePassword}
        >
          Generar contraseña
        </button>
      </div>
      <p className="hint">
        {passwordHint} Entrega la contraseña a su titular antes de guardar;
        deberá cambiarla al ingresar. Vence en 24 horas.
      </p>
      <label className="check-field">
        <input type="checkbox" name="correoVerificado" /> La institución
        verificó que este correo pertenece al titular.
      </label>
      <p className="hint">
        Sin esta confirmación, la recuperación por correo queda deshabilitada.
      </p>
    </StepCard>
  );
}

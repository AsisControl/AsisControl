"use client";
import { passwordHint } from "@/core/auth/password-policy";
import { PasswordField } from "@/shared/ui";
import { StepCard } from "./step-parts";
import styles from "./new-user.module.css";
/** Paso 3: contraseña temporal del acceso; usuario y correo los genera el sistema. */
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
      description="El usuario y el correo se generan al guardar. La contraseña temporal se cambia al primer ingreso."
      done={done}
    >
      <p className="hint">
        Usuario: primer nombre y apellido paterno (maria.quispe; si ya existe,
        maria.quispe2). Correo: el código del perfil con el dominio
        institucional (est20260001@asiscontrol.edu.pe).
      </p>
      <div className={styles.passwordRow}>
        <PasswordField
          label="Contraseña temporal"
          name="password"
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
        deberá cambiarla al ingresar. Vence en 14 días.
      </p>
      <p className="hint">
        El correo institucional no tiene buzón propio: si la persona olvida su
        contraseña, restablécela desde Usuarios.
      </p>
    </StepCard>
  );
}

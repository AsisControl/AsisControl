"use client";
import { useEffect, useState, type FormEvent } from "react";
import { passwordError } from "@/core/auth/password-policy";
import { api, message } from "@/core/api/client";
import { Feedback } from "@/shared/ui";
import { personData } from "@/shared/forms/person-fields";
import { roleLabel } from "@/shared/lib/formatters";
import { CredentialsStep } from "./new-user/credentials-step";
import { NewUserSuccess } from "./new-user/new-user-success";
import { NewUserSummary } from "./new-user/new-user-summary";
import { PersonStep, type PersonMode } from "./new-user/person-step";
import { RoleStep } from "./new-user/role-step";
import { StudentsStep } from "./new-user/students-step";
import styles from "./new-user/new-user.module.css";
import type { Account, PersonChoice, Role } from "./types";
const INSTITUTIONAL_ROLES = [
  "ADMINISTRADOR",
  "DOCENTE",
  "ALUMNO",
  "ESTUDIANTE",
  "APODERADO",
];
/** Valores del formulario que se muestran en el resumen mientras se escribe. */
type Draft = { personName: string; username: string; email: string };
const EMPTY_DRAFT: Draft = { personName: "", username: "", email: "" };
export function temporaryPassword(): string {
  const alphabet =
    "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
  let result = "";
  do {
    result = Array.from(
      crypto.getRandomValues(new Uint32Array(16)),
      (v) => alphabet[v % alphabet.length],
    ).join("");
  } while (
    !/[A-Z]/.test(result) ||
    !/[a-z]/.test(result) ||
    !/[0-9]/.test(result)
  );
  return result;
}
/** Nuevo usuario: persona, rol y perfil, acceso y (apoderado) estudiantes. */
export function CreateAccess({ onBack }: { onBack: () => void }) {
  const [formKey, setFormKey] = useState(0);
  return (
    <NewUserForm
      key={formKey}
      onBack={onBack}
      onCreateAnother={() => setFormKey((key) => key + 1)}
    />
  );
}
function NewUserForm({
  onBack,
  onCreateAnother,
}: {
  onBack: () => void;
  onCreateAnother: () => void;
}) {
  const [roles, setRoles] = useState<Role[]>([]);
  const [roleId, setRoleId] = useState("");
  const [mode, setMode] = useState<PersonMode>("existing");
  const [person, setPerson] = useState<PersonChoice | null>(null);
  const [selected, setSelected] = useState<Map<number, string>>(new Map());
  const [password, setPassword] = useState("");
  const [draft, setDraft] = useState<Draft>(EMPTY_DRAFT);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState<Account | null>(null);
  useEffect(() => {
    let active = true;
    api<Role[]>("/api/roles")
      .then((all) => {
        if (active)
          setRoles(all.filter((r) => INSTITUTIONAL_ROLES.includes(r.nombre)));
      })
      .catch((e) => {
        if (active) setError(message(e));
      });
    return () => {
      active = false;
    };
  }, []);
  const role = roles.find((r) => String(r.id) === roleId)?.nombre || "";
  const needsProfile = mode === "new" || !person?.perfiles.includes(role);
  const rolesWithAccess =
    mode === "existing" ? (person?.rolesConAcceso ?? []) : [];
  const roleTaken = rolesWithAccess.includes(role);
  const missing = [
    mode === "existing" && !person && "persona",
    !role && "rol",
    roleTaken && "otro rol",
    !draft.username && "usuario",
    !draft.email && "correo",
    !password && "contraseña",
  ].filter((item): item is string => Boolean(item));
  function readDraft(form: HTMLFormElement) {
    const data = new FormData(form);
    const str = (key: string) => String(data.get(key) || "").trim();
    setDraft({
      personName: [
        str("nombres"),
        str("apellidoPaterno"),
        str("apellidoMaterno"),
      ]
        .filter(Boolean)
        .join(" "),
      username: str("username"),
      email: str("correo"),
    });
  }
  function changeMode(next: PersonMode) {
    setMode(next);
    setError("");
  }
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    if (mode === "existing" && !person) {
      setError("Selecciona una persona existente.");
      return;
    }
    if (roleTaken) {
      setError("Esta persona ya tiene un acceso para el rol elegido.");
      return;
    }
    const data = new FormData(e.currentTarget);
    const str = (key: string) => String(data.get(key) || "").trim();
    const policy = passwordError(password, str("username"));
    if (policy) {
      setError(policy);
      return;
    }
    setBusy(true);
    try {
      const result = await api<Account>("/api/usuarios/provisionar", {
        method: "POST",
        body: JSON.stringify({
          personaId: mode === "existing" ? person?.id : null,
          persona: mode === "new" ? personData(data) : null,
          rolId: Number(roleId),
          username: str("username"),
          correo: str("correo"),
          correoVerificado: data.get("correoVerificado") === "on",
          password,
          codigoAlumno: str("codigoAlumno") || null,
          codigoDocente: str("codigoDocente") || null,
          fechaIngreso: str("fechaIngreso") || null,
          especialidad: str("especialidad") || null,
          alumnoIds: role === "APODERADO" ? [...selected.keys()] : [],
        }),
      });
      setPassword("");
      setSaved(result);
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  if (saved)
    return (
      <NewUserSuccess
        account={saved}
        onBack={onBack}
        onCreateAnother={onCreateAnother}
      />
    );
  const personName =
    mode === "existing" ? (person?.nombreCompleto ?? "") : draft.personName;
  return (
    <section>
      <button type="button" className={styles.backLink} onClick={onBack}>
        ← Volver a usuarios
      </button>
      <div className={styles.heading}>
        <h2>Nuevo usuario</h2>
        <p>
          Una cuenta por persona y rol. Cada acceso utiliza usuario y correo
          únicos.
        </p>
      </div>
      <Feedback error={error} />
      <form onSubmit={submit} onInput={(e) => readDraft(e.currentTarget)}>
        <fieldset className={styles.layout} disabled={busy}>
          <div className={styles.steps}>
            <PersonStep
              mode={mode}
              onModeChange={changeMode}
              person={person}
              onPersonChange={setPerson}
            />
            <RoleStep
              roles={roles}
              roleId={roleId}
              onRoleChange={setRoleId}
              needsProfile={needsProfile}
              rolesWithAccess={rolesWithAccess}
            />
            <CredentialsStep
              password={password}
              onPasswordChange={setPassword}
              onGeneratePassword={() => setPassword(temporaryPassword())}
              done={Boolean(draft.username && draft.email && password)}
            />
            {role === "APODERADO" && (
              <StudentsStep
                selected={selected}
                onSelectedChange={setSelected}
              />
            )}
          </div>
          <NewUserSummary
            personName={personName}
            role={role}
            profileAction={
              role
                ? needsProfile
                  ? `Se creará el perfil de ${roleLabel(role).toLowerCase()}`
                  : "Se reutiliza el perfil existente"
                : ""
            }
            username={draft.username}
            email={draft.email}
            studentCount={role === "APODERADO" ? selected.size : null}
            missing={missing}
            busy={busy}
            onCancel={onBack}
          />
        </fieldset>
      </form>
    </section>
  );
}

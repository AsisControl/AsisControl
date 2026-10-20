"use client";
import { useEffect, useState, type FormEvent } from "react";
import { passwordError, passwordHint } from "@/core/auth/password-policy";
import { api, message } from "@/core/api/client";
import { Feedback, Field, PasswordField, Pager } from "@/shared/ui";
import { PersonFields, personData } from "@/shared/forms/person-fields";
import { usePaged } from "@/shared/hooks/use-paged";
import { roleLabel } from "@/shared/lib/formatters";
import type { Student } from "@/modules/students";
import { teacherSpecialties } from "@/modules/teachers";
import type { Account, PersonChoice, Role } from "./types";
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
export function CreateAccess({ onBack }: { onBack: () => void }) {
  const [roles, setRoles] = useState<Role[]>([]);
  const [roleId, setRoleId] = useState("");
  const [mode, setMode] = useState("existing");
  const [person, setPerson] = useState<PersonChoice | null>(null);
  const [search, setSearch] = useState("");
  const people = usePaged<PersonChoice>("/api/usuarios/personas", search);
  const [studentSearch, setStudentSearch] = useState("");
  const students = usePaged<Student>("/api/alumnos", studentSearch);
  const [selected, setSelected] = useState<Map<number, string>>(new Map());
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState<Account | null>(null);
  useEffect(() => {
    let active = true;
    api<Role[]>("/api/roles")
      .then((r) => {
        if (active) {
          setRoles(
            r.filter((x) =>
              [
                "ADMINISTRADOR",
                "DOCENTE",
                "ALUMNO",
                "ESTUDIANTE",
                "APODERADO",
              ].includes(x.nombre),
            ),
          );
        }
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
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    if (mode === "existing" && !person) {
      setError("Selecciona una persona existente.");
      return;
    }
    if (person?.rolesConAcceso.includes(role) && mode === "existing") {
      setError("Esta persona ya tiene un acceso para el rol elegido.");
      return;
    }
    const policy = passwordError(
      password,
      String(new FormData(e.currentTarget).get("username") || ""),
    );
    if (policy) {
      setError(policy);
      return;
    }
    setBusy(true);
    const data = new FormData(e.currentTarget);
    const str = (key: string) => String(data.get(key) || "").trim();
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
      <article className="panel">
        <h2>Acceso creado</h2>
        <p className="action-feedback" role="status">
          {saved.nombreCompleto} · {roleLabel(saved.rol)} · Usuario:{" "}
          {saved.username}
        </p>
        <p>
          La persona deberá cambiar la contraseña temporal al ingresar. La
          contraseña no se conserva ni se vuelve a mostrar en esta pantalla.
        </p>
        <button onClick={onBack}>Volver a usuarios</button>
      </article>
    );
  return (
    <section>
      <div className="management-heading">
        <div>
          <p className="eyebrow">ADMINISTRACIÓN</p>
          <h2>Crear acceso</h2>
          <p>
            Una cuenta por persona y rol. Cada acceso utiliza usuario y correo
            únicos.
          </p>
        </div>
        <button onClick={onBack}>Volver a usuarios</button>
      </div>
      <Feedback error={error} />
      <div className="panel">
        <label className="field">
          Persona
          <select
            value={mode}
            disabled={busy}
            onChange={(e) => setMode(e.target.value)}
          >
            <option value="existing">Seleccionar persona existente</option>
            <option value="new">Registrar persona nueva</option>
          </select>
        </label>
        {mode === "existing" && (
          <>
            <form
              className="toolbar"
              onSubmit={(e) => {
                e.preventDefault();
                setSearch(String(new FormData(e.currentTarget).get("q") || ""));
                people.setPage(0);
              }}
            >
              <Field label="Buscar por nombre o documento" name="q" />
              <button>Buscar</button>
            </form>
            <Feedback error={people.error} />
            {people.loading ? (
              <p>Cargando personas…</p>
            ) : (
              <div className="choice-list">
                {people.data?.content.map((p) => (
                  <button
                    type="button"
                    disabled={busy}
                    className={person?.id === p.id ? "selected" : ""}
                    aria-pressed={person?.id === p.id}
                    key={p.id}
                    onClick={() => setPerson(p)}
                  >
                    <strong>{p.nombreCompleto}</strong>
                    <small>
                      Documento: {p.numeroDocumento} · Perfiles:{" "}
                      {p.perfiles.map(roleLabel).join(", ") || "Sin perfil"}
                    </small>
                  </button>
                ))}
                {people.data?.content.length === 0 && (
                  <p>No hay personas para esta búsqueda.</p>
                )}
              </div>
            )}
            <Pager
              data={people.data}
              loading={people.loading}
              setPage={people.setPage}
            />
            {person && (
              <p role="status">
                Seleccionada: <strong>{person.nombreCompleto}</strong>
              </p>
            )}
          </>
        )}
      </div>
      <form className="panel" onSubmit={submit}>
        <fieldset disabled={busy}>
          {mode === "new" && (
            <>
              <h3>Datos de la persona</h3>
              <PersonFields />
            </>
          )}
          <h3>Perfil institucional</h3>
          <label className="field">
            Rol *
            <select
              required
              value={roleId}
              onChange={(e) => setRoleId(e.target.value)}
            >
              <option value="">Seleccionar rol</option>
              {roles.map((r) => (
                <option key={r.id} value={r.id}>
                  {roleLabel(r.nombre)}
                </option>
              ))}
            </select>
          </label>
          {role === "DOCENTE" && needsProfile && (
            <div className="form-grid">
              <Field
                label="Código docente"
                name="codigoDocente"
                maxLength={30}
                required
              />
              <Field
                label="Fecha de ingreso"
                name="fechaIngreso"
                type="date"
                required
                max={new Date().toISOString().slice(0, 10)}
              />
              <label className="field">
                Especialidad *
                <select name="especialidad" required>
                  <option value="">Seleccionar</option>
                  {teacherSpecialties.map((s) => (
                    <option key={s} value={s}>
                      {s.replaceAll("_", " ")}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          )}
          {["ALUMNO", "ESTUDIANTE"].includes(role) && needsProfile && (
            <Field
              label="Código de estudiante"
              name="codigoAlumno"
              required
              maxLength={30}
            />
          )}
          {!needsProfile && role && (
            <p>Se utilizará el perfil institucional existente.</p>
          )}
          <h3>Credenciales del acceso</h3>
          <div className="form-grid">
            <Field
              label="Usuario"
              name="username"
              required
              minLength={4}
              maxLength={80}
              autoComplete="off"
            />
            <Field
              label="Correo del acceso"
              name="correo"
              required
              type="email"
              maxLength={150}
              autoComplete="off"
            />
            <PasswordField
              label="Contraseña temporal"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              minLength={8}
              maxLength={64}
              autoComplete="new-password"
            />
            <button
              className="generate"
              type="button"
              onClick={() => setPassword(temporaryPassword())}
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
            Sin esta confirmación, la recuperación por correo queda
            deshabilitada.
          </p>
          <div className="form-actions">
            <button type="button" onClick={onBack}>
              Cancelar
            </button>
            <button
              className="primary-action"
              disabled={busy || !roleId || (mode === "existing" && !person)}
            >
              {busy ? "Creando…" : "Crear acceso"}
            </button>
          </div>
        </fieldset>
      </form>
      {role === "APODERADO" && (
        <article className="panel">
          <h3>Estudiantes asociados</h3>
          <p>
            Puede asociar varios estudiantes. Sin asociaciones activas, el
            apoderado verá una experiencia vacía.
          </p>
          <form
            className="toolbar"
            onSubmit={(e) => {
              e.preventDefault();
              setStudentSearch(
                String(new FormData(e.currentTarget).get("q") || ""),
              );
              students.setPage(0);
            }}
          >
            <Field label="Buscar estudiante" name="q" />
            <button>Buscar</button>
          </form>
          <Feedback error={students.error} />
          {students.loading ? (
            <p>Cargando estudiantes…</p>
          ) : (
            <div className="choice-list">
              {students.data?.content.map((s) => (
                <label key={s.id} className="check-choice">
                  <input
                    type="checkbox"
                    disabled={busy}
                    checked={selected.has(s.id)}
                    onChange={(e) => {
                      const next = new Map(selected);
                      if (e.target.checked)
                        next.set(s.id, s.persona.nombreCompleto);
                      else next.delete(s.id);
                      setSelected(next);
                    }}
                  />
                  {s.persona.nombreCompleto} · {s.codigoAlumno}
                </label>
              ))}
            </div>
          )}
          <Pager
            data={students.data}
            loading={students.loading}
            setPage={students.setPage}
          />
          <p>
            {selected.size} seleccionados:{" "}
            {[...selected.values()].join(", ") || "Ninguno"}
          </p>
        </article>
      )}
    </section>
  );
}

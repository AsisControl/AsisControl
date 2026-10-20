"use client";
import { useState, type FormEvent, type ReactNode } from "react";
import { api, message } from "@/core/api/client";
import { passwordError } from "@/core/auth/password-policy";
import { Feedback, Field, PasswordField, Pager } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { roleLabel } from "@/shared/lib/formatters";
import { AcademicConfiguration } from "@/modules/academic-config";
import { Teachers } from "@/modules/teachers";
import { CreateAccess } from "./create-access";
import { RolesMatrix } from "./roles-matrix";
import { SystemLogs } from "./system-logs";
import { AuditLog } from "./audit-log";
import type { Account } from "./types";
type AdminSection = {
  id: string;
  label: string;
  description: string;
  /** Sin render se muestra la gestión de usuarios de este archivo. */
  render?: () => ReactNode;
};
/**
 * Pestañas de Administración. Cada módulo agrega aquí su entrada una sola vez;
 * el contenido vive en su propia carpeta dentro de src/modules.
 */
const sections: AdminSection[] = [
  {
    id: "users",
    label: "Usuarios",
    description: "Accesos institucionales separados por persona y rol.",
  },
  {
    id: "audit",
    label: "Auditoría",
    description: "Historial de operaciones registrado por el servidor.",
    render: () => <AuditLog />,
  },
  {
    id: "academic",
    label: "Configuración académica",
    description: "Años, períodos, grados, secciones y capacidades.",
    render: () => <AcademicConfiguration />,
  },
  {
    id: "teachers",
    label: "Docentes",
    description: "Perfiles institucionales de los docentes.",
    render: () => <Teachers />,
  },
  {
    id: "roles",
    label: "Roles y permisos",
    description: "Matriz de los cuatro roles institucionales.",
    render: () => <RolesMatrix />,
  },
  {
    id: "logs",
    label: "Logs del sistema",
    description: "Errores técnicos y seguimiento.",
    render: () => <SystemLogs />,
  },
];
export function Administration({
  currentUserId,
  startCreate = false,
}: {
  currentUserId: string;
  startCreate?: boolean;
}) {
  const [sectionId, setSectionId] = useState("users");
  const section = sections.find((s) => s.id === sectionId) ?? sections[0];
  const [creating, setCreating] = useState(startCreate);
  const [search, setSearch] = useState("");
  const users = usePaged<Account>("/api/usuarios", search);
  const [editing, setEditing] = useState<Account | null>(null);
  const [reset, setReset] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  async function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!editing) return;
    const form = new FormData(e.currentTarget);
    const password = String(form.get("password") || "");
    const activating =
      !reset &&
      ["DESACTIVADO", "PENDIENTE"].includes(editing.estado) &&
      form.get("estado") === "ACTIVO";
    if (reset || activating) {
      const policy = passwordError(password, editing.username);
      if (policy) {
        setError(policy);
        return;
      }
    }
    setBusy(true);
    setError("");
    setSuccess("");
    try {
      await api(
        reset
          ? `/api/usuarios/${editing.id}/contrasena-temporal`
          : `/api/usuarios/${editing.id}`,
        {
          method: "PUT",
          body: JSON.stringify(
            reset
              ? {
                  temporaryPassword: password,
                  identidadVerificada: form.get("identidad") === "on",
                  motivo: form.get("motivo"),
                }
              : {
                  correo: form.get("correo"),
                  estado: form.get("estado"),
                  rolId: editing.rolId,
                  correoVerificado: form.get("correoVerificado") === "on",
                  temporaryPassword: activating ? password : null,
                  motivo: form.get("motivo"),
                },
          ),
        },
      );
      setEditing(null);
      users.refresh();
      setSuccess(
        reset
          ? "Contraseña temporal restablecida. Las sesiones anteriores fueron revocadas."
          : "Acceso actualizado.",
      );
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  if (creating)
    return (
      <CreateAccess
        onBack={() => {
          setCreating(false);
          users.refresh();
        }}
      />
    );
  return (
    <section>
      <div className="management-heading">
        <div>
          <p className="eyebrow">ADMINISTRACIÓN</p>
          <h2>{section.label}</h2>
          <p>{section.description}</p>
        </div>
        {section.id === "users" && (
          <button
            className="primary-action"
            onClick={() => {
              setCreating(true);
              setError("");
              setSuccess("");
            }}
          >
            Crear acceso
          </button>
        )}
      </div>
      <div className="management-tabs">
        {sections.map(({ id, label }) => (
          <button
            key={id}
            className={section.id === id ? "active" : ""}
            onClick={() => {
              setSectionId(id);
              setError("");
              setSuccess("");
            }}
          >
            {label}
          </button>
        ))}
      </div>
      <Feedback error={error} success={success} />
      {section.render ? (
        section.render()
      ) : (
        <>
          <form
            className="toolbar"
            onSubmit={(e) => {
              e.preventDefault();
              setSearch(
                String(new FormData(e.currentTarget).get("search") || ""),
              );
              users.setPage(0);
            }}
          >
            <Field label="Buscar por nombre, usuario o correo" name="search" />
            <button disabled={users.loading}>Buscar</button>
          </form>
          <Feedback error={users.error} />
          {users.loading ? (
            <p role="status">Cargando usuarios…</p>
          ) : users.data?.content.length ? (
            <div className="user-table-wrap">
              <table className="user-table">
                <thead>
                  <tr>
                    <th>Persona / Usuario</th>
                    <th>Rol</th>
                    <th>Estado</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {users.data.content.map((u) => (
                    <tr key={u.id}>
                      <td data-label="Persona">
                        <strong>{u.nombreCompleto}</strong>
                        <small>
                          {u.username} · {u.correo}
                        </small>
                        <small>
                          {u.correoVerificado
                            ? "Correo confirmado para recuperación"
                            : "Correo pendiente de confirmar"}
                        </small>
                      </td>
                      <td data-label="Rol">{roleLabel(u.rol)}</td>
                      <td data-label="Estado">
                        <span
                          className={`status ${u.estado === "ACTIVO" ? "status-active" : "status-inactive"}`}
                        >
                          {u.estado.replaceAll("_", " ")}
                        </span>
                        {u.requiereCambioContrasena && (
                          <small>
                            Cambio de contraseña pendiente
                            {u.contrasenaTemporalExpira
                              ? ` · Temporal vence: ${new Date(u.contrasenaTemporalExpira).toLocaleString("es-PE", { timeZone: "America/Lima" })}`
                              : ""}
                          </small>
                        )}
                      </td>
                      <td data-label="Acciones">
                        {String(u.id) !== currentUserId ? (
                          <div className="row-actions">
                            <button
                              onClick={() => {
                                setEditing(u);
                                setReset(false);
                                setError("");
                              }}
                            >
                              Editar
                            </button>
                            <button
                              onClick={() => {
                                setEditing(u);
                                setReset(true);
                                setError("");
                              }}
                            >
                              Restablecer contraseña
                            </button>
                          </div>
                        ) : (
                          <div className="row-actions">
                            <small>Tu acceso actual</small>
                            <button
                              onClick={() => {
                                setEditing(u);
                                setReset(false);
                                setError("");
                              }}
                            >
                              Editar correo
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            !users.error && <p className="empty">No se encontraron accesos.</p>
          )}
          <Pager
            data={users.data}
            loading={users.loading}
            setPage={users.setPage}
          />
        </>
      )}
      {editing && (
        <div className="modal-backdrop">
          <form
            className="user-form-card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="edit-access-title"
            onSubmit={save}
          >
            <h3 id="edit-access-title">
              {reset ? "Restablecer contraseña" : "Editar acceso"}
            </h3>
            <p>
              {editing.nombreCompleto} · {roleLabel(editing.rol)}
            </p>
            <Feedback error={error} />
            {reset ? (
              <>
                <label className="check-field">
                  <input type="checkbox" name="identidad" required /> Verifiqué
                  la identidad del titular antes de restablecer su acceso.
                </label>
                <label className="field">
                  Motivo *
                  <textarea name="motivo" required maxLength={700} rows={3} />
                </label>
                <PasswordField
                  label="Contraseña temporal"
                  name="password"
                  required
                  minLength={8}
                  maxLength={64}
                  autoComplete="new-password"
                />
                <p className="hint">
                  Vence en 24 horas y requiere cambio al ingresar. Entrega esta
                  contraseña al titular una sola vez. Sus sesiones anteriores
                  serán revocadas.
                </p>
              </>
            ) : (
              <>
                <Field
                  label="Correo del acceso"
                  name="correo"
                  type="email"
                  required
                  defaultValue={editing.correo}
                  onChange={(event) => {
                    const confirmation =
                      event.currentTarget.form?.elements.namedItem(
                        "correoVerificado",
                      ) as HTMLInputElement | null;
                    if (confirmation) confirmation.checked = false;
                  }}
                />
                <label className="field">
                  Estado
                  <select name="estado" defaultValue={editing.estado}>
                    <option value="ACTIVO">Activo</option>
                    {String(editing.id) !== currentUserId && (
                      <option value="DESACTIVADO">Desactivado</option>
                    )}
                    {!["ACTIVO", "DESACTIVADO"].includes(editing.estado) && (
                      <option value={editing.estado}>{editing.estado}</option>
                    )}
                  </select>
                </label>
                <label className="check-field">
                  <input
                    type="checkbox"
                    name="correoVerificado"
                    defaultChecked={editing.correoVerificado}
                  />{" "}
                  La institución verificó que este correo pertenece al titular.
                </label>
                {["DESACTIVADO", "PENDIENTE"].includes(editing.estado) && (
                  <PasswordField
                    label="Nueva contraseña temporal (para reactivar)"
                    name="password"
                    minLength={8}
                    maxLength={64}
                    autoComplete="new-password"
                  />
                )}
                <label className="field">
                  Motivo del cambio *
                  <textarea name="motivo" required maxLength={700} rows={3} />
                </label>
                <p className="hint">
                  Al cambiar el correo, confirma nuevamente su titularidad. Para
                  otro rol, crea un acceso separado.
                </p>
              </>
            )}
            <div className="form-actions">
              <button
                type="button"
                disabled={busy}
                onClick={() => setEditing(null)}
              >
                Cancelar
              </button>
              <button className="primary-action" disabled={busy}>
                {busy ? "Guardando…" : "Confirmar"}
              </button>
            </div>
          </form>
        </div>
      )}
    </section>
  );
}

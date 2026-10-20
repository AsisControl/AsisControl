"use client";
import { useEffect, useState } from "react";
import { api, message } from "@/core/api/client";
import { Feedback } from "@/shared/ui";
import { roleLabel } from "@/shared/lib/formatters";
import type { Role, Permission } from "./types";
const fixedRoles = ["ADMINISTRADOR", "DOCENTE", "ALUMNO", "APODERADO"];
const scopes: Record<string, string> = {
  ADMINISTRADOR: "Información institucional y gestión de accesos.",
  DOCENTE: "Cursos y secciones de sus asignaciones docentes.",
  ALUMNO: "Su información académica y sus propias entregas.",
  APODERADO: "Información y solicitudes de sus estudiantes asociados.",
};
export function RolesMatrix() {
  const [roles, setRoles] = useState<Role[] | null>(null);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [focus, setFocus] = useState("");
  useEffect(() => {
    let active = true;
    Promise.all([
      api<Role[]>("/api/roles"),
      api<Permission[]>("/api/roles/permisos"),
    ])
      .then(([r, p]) => {
        if (active) {
          setRoles(r);
          setPermissions(p);
          setError("");
        }
      })
      .catch((e) => {
        if (active) setError(message(e));
      });
    return () => {
      active = false;
    };
  }, [reload]);
  const categories = [
    ...new Set(permissions.map((p) => p.categoria || "General")),
  ].sort();
  const shownRoles = focus ? [focus] : fixedRoles;
  const term = search.trim().toLocaleLowerCase("es");
  const visible = permissions
    .filter(
      (p) =>
        (!category || (p.categoria || "General") === category) &&
        `${p.nombre} ${p.descripcion || ""} ${p.categoria || ""} ${p.codigo}`
          .toLocaleLowerCase("es")
          .includes(term),
    )
    .sort(
      (a, b) =>
        (a.categoria || "General").localeCompare(b.categoria || "General") ||
        a.nombre.localeCompare(b.nombre),
    );
  return (
    <>
      <div className="section-heading">
        <p className="hint">
          Matriz institucional de consulta · US-03. Los cuatro roles son fijos.
          Los permisos se comprueban en el servidor junto con la relación con
          cada registro.
        </p>
        <button onClick={() => setReload((v) => v + 1)}>
          Actualizar matriz
        </button>
      </div>
      <Feedback error={error} />
      {!roles && !error && <p role="status">Cargando roles y permisos…</p>}
      {roles && (
        <>
          <div className="role-cards">
            {fixedRoles.map((name) => {
              const role = roles.find((r) => r.nombre === name);
              return (
                <button
                  type="button"
                  className={`role-card ${focus === name ? "selected" : ""}`}
                  aria-pressed={focus === name}
                  key={name}
                  onClick={() => setFocus(focus === name ? "" : name)}
                >
                  <strong>{roleLabel(name)}</strong>
                  <span>{scopes[name]}</span>
                  <small>
                    {role
                      ? `${role.permisos.length} permisos habilitados`
                      : "Sin configurar"}
                  </small>
                </button>
              );
            })}
          </div>
          <div className="toolbar role-filters">
            <label className="field">
              Buscar permiso o módulo
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Ej. matrícula, consultar, usuarios"
              />
            </label>
            <label className="field">
              Módulo
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
              >
                <option value="">Todos los módulos</option>
                {categories.map((c) => (
                  <option key={c}>{c}</option>
                ))}
              </select>
            </label>
            <label className="field">
              Comparar rol
              <select value={focus} onChange={(e) => setFocus(e.target.value)}>
                <option value="">Los cuatro roles</option>
                {fixedRoles.map((r) => (
                  <option key={r} value={r}>
                    {roleLabel(r)}
                  </option>
                ))}
              </select>
            </label>
            <button
              onClick={() => {
                setSearch("");
                setCategory("");
                setFocus("");
              }}
            >
              Limpiar filtros
            </button>
          </div>
          <div className="permission-legend">
            <span className="permission-yes">✓ Permitido</span>
            <span className="permission-no">— Sin permiso</span>
            <p>
              {visible.length} de {permissions.length} permisos. Tener permiso
              no concede acceso a registros de otras personas.
            </p>
          </div>
          {visible.length ? (
            <div className="user-table-wrap">
              <table className="user-table permission-matrix">
                <caption>Permisos por módulo y rol institucional</caption>
                <thead>
                  <tr>
                    <th scope="col">Módulo / funcionalidad</th>
                    {shownRoles.map((r) => (
                      <th scope="col" key={r}>
                        {roleLabel(r)}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {visible.map((p) => (
                    <tr key={p.codigo}>
                      <th scope="row">
                        <span className="permission-module">
                          {p.categoria || "General"}
                        </span>
                        <strong>{p.descripcion || p.nombre}</strong>

                        <details>
                          <summary>Identificador del permiso</summary>
                          <code>{p.codigo}</code>
                        </details>
                      </th>
                      {shownRoles.map((r) => {
                        const allowed = roles
                          .find((role) => role.nombre === r)
                          ?.permisos.some((v) => v.codigo === p.codigo);
                        return (
                          <td data-label={roleLabel(r)} key={r}>
                            <span
                              className={
                                allowed ? "permission-yes" : "permission-no"
                              }
                            >
                              {allowed ? "✓ Permitido" : "— Sin permiso"}
                            </span>
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="empty">
              No hay permisos que coincidan con estos filtros.
            </p>
          )}
          <article className="notice">
            <strong>Alcance de cada acceso</strong>
            <p>
              El apoderado puede crear y enviar sus solicitudes; la revisión, la
              sección y la matrícula final pertenecen al administrador. Una
              persona con varios roles usa accesos separados. Esta pantalla no
              modifica roles ni concede permisos individuales.
            </p>
          </article>
        </>
      )}
    </>
  );
}

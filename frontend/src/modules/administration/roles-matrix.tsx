"use client";
import { useEffect, useState } from "react";
import { api, message } from "@/core/api/client";
import { Feedback, PanelHeading } from "@/shared/ui";
import { countLabel, roleLabel, timeLabel } from "@/shared/lib/formatters";
import type { Role, Permission } from "./types";
import styles from "./roles-matrix.module.css";
const fixedRoles = ["ADMINISTRADOR", "DOCENTE", "ALUMNO", "APODERADO"];
const scopes: Record<string, string> = {
  ADMINISTRADOR: "Información institucional y gestión de accesos.",
  DOCENTE: "Cursos y secciones de sus asignaciones docentes.",
  ALUMNO: "Su información académica y sus propias entregas.",
  APODERADO: "Información y solicitudes de sus estudiantes asociados.",
};
const GENERAL = "General";
const categoryOf = (permission: Permission) =>
  permission.categoria || GENERAL;
const CheckBox = ({ checked }: { checked: boolean }) => (
  <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
    <rect
      x="0.5"
      y="0.5"
      width="15"
      height="15"
      rx="3.5"
      fill={checked ? "currentColor" : "#fff"}
      stroke="currentColor"
    />
    {checked && (
      <path
        d="M4 8.2 6.7 11 12 5"
        fill="none"
        stroke="#fff"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    )}
  </svg>
);
/**
 * Roles y permisos (US-03): consulta de la matriz fija. El backend rechaza
 * cualquier cambio de roles, por eso la pantalla no ofrece guardar.
 */
export function RolesMatrix() {
  const [roles, setRoles] = useState<Role[] | null>(null);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const [selected, setSelected] = useState(fixedRoles[0]);
  const [search, setSearch] = useState("");
  useEffect(() => {
    let active = true;
    Promise.all([
      api<Role[]>("/api/roles"),
      api<Permission[]>("/api/roles/permisos"),
    ])
      .then(([r, p]) => {
        if (!active) return;
        setRoles(r);
        setPermissions(p);
        setError("");
        setUpdatedAt(new Date());
      })
      .catch((e) => {
        if (active) setError(message(e));
      });
    return () => {
      active = false;
    };
  }, [reload]);
  const role = roles?.find((item) => item.nombre === selected);
  const granted = new Set(role?.permisos.map((item) => item.codigo));
  const term = search.trim().toLocaleLowerCase("es");
  const visible = permissions.filter((p) =>
    `${p.nombre} ${p.descripcion || ""} ${categoryOf(p)} ${p.codigo}`
      .toLocaleLowerCase("es")
      .includes(term),
  );
  const groups = [...new Set(visible.map(categoryOf))]
    .sort((a, b) => a.localeCompare(b, "es"))
    .map((category) => ({
      category,
      items: visible
        .filter((p) => categoryOf(p) === category)
        .sort((a, b) => a.nombre.localeCompare(b.nombre, "es")),
    }));
  return (
    <>
      <Feedback error={error} />
      {!roles && !error && <p role="status">Cargando roles y permisos…</p>}
      {!roles && error && (
        <button
          type="button"
          className="primary-action"
          onClick={() => setReload((v) => v + 1)}
        >
          Reintentar
        </button>
      )}
      {roles && (
        <div className={styles.layout}>
          <aside className={`panel ${styles.roleList}`}>
            <p className={styles.listTitle}>Roles del sistema</p>
            {fixedRoles.map((name) => {
              const current = roles.find((item) => item.nombre === name);
              return (
                <button
                  key={name}
                  type="button"
                  aria-pressed={selected === name}
                  onClick={() => setSelected(name)}
                >
                  <span>
                    <strong>{roleLabel(name)}</strong>
                    <small>
                      {current
                        ? countLabel(current.permisos.length, "permiso", "permisos")
                        : "Sin configurar"}
                    </small>
                  </span>
                  <span aria-hidden="true">›</span>
                </button>
              );
            })}
          </aside>
          <section className={`panel ${styles.permissions}`}>
            <PanelHeading
              title={`Permisos — ${roleLabel(selected)}`}
              summary={
                <>
                  {scopes[selected]}
                  {updatedAt &&
                    ` · Última actualización: ${timeLabel(updatedAt)}`}
                </>
              }
              actions={
                <button type="button" onClick={() => setReload((v) => v + 1)}>
                  Actualizar
                </button>
              }
            />
            <input
              type="search"
              className={styles.search}
              aria-label="Buscar permiso o módulo"
              placeholder="Buscar permiso o módulo…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
            <p className={styles.count}>
              {role
                ? `${visible.filter((p) => granted.has(p.codigo)).length} de ${visible.length} permisos habilitados para este rol.`
                : "Este rol todavía no está configurado en el servidor."}
            </p>
            {groups.length ? (
              <div className={styles.groups}>
                {groups.map(({ category, items }) => (
                  <section key={category} className={styles.group}>
                    <h4>{category}</h4>
                    <ul>
                      {items.map((permission) => {
                        const allowed = granted.has(permission.codigo);
                        return (
                          <li
                            key={permission.codigo}
                            className={allowed ? styles.allowed : styles.denied}
                            title={permission.codigo}
                          >
                            <CheckBox checked={allowed} />
                            {permission.descripcion || permission.nombre}
                            <span className={styles.srOnly}>
                              {allowed ? " (permitido)" : " (sin permiso)"}
                            </span>
                          </li>
                        );
                      })}
                    </ul>
                  </section>
                ))}
              </div>
            ) : (
              <p className="empty">
                No hay permisos que coincidan con la búsqueda.
              </p>
            )}
            <p className={styles.notice}>
              Los cuatro roles y sus permisos son configuración controlada del
              servidor y no se modifican desde la aplicación. Tener un permiso
              no concede acceso a registros de otras personas; una persona con
              varios roles usa accesos separados.
            </p>
          </section>
        </div>
      )}
    </>
  );
}

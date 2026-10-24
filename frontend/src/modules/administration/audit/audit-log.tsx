"use client";
import { useEffect, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { message } from "@/core/api/client";
import { Eye, Feedback, Pager } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { roleLabel } from "@/shared/lib/formatters";
import type { Audit, Role } from "../types";
import { listarRoles } from "../users/services/usuarioService";
import { dateTimeLabel, initialsOf, rolePillClass } from "../users/user-format";
import { AuditDetailDialog } from "./audit-detail-dialog";
import {
  actionLabel,
  actorDisplayName,
  entityLabel,
  modulePillClass,
  moduleLabel,
  resultLabels,
} from "./audit-format";
import {
  AUDITORIA_REGISTROS_PATH,
  listarAcciones,
  listarModulos,
} from "./services/auditoriaService";
import styles from "./audit.module.css";
const SEARCH_DELAY_MS = 300;
const INSTITUTIONAL_ROLES = ["ADMINISTRADOR", "DOCENTE", "ALUMNO", "APODERADO"];
/** Parámetro de la URL del registro abierto con el ojo: ?ver=15. */
const DETAIL_PARAM = "ver";
function filtersQuery(filters: Record<string, string>) {
  return Object.entries(filters)
    .filter(([, value]) => value)
    .map(([key, value]) => `${key}=${encodeURIComponent(value)}`)
    .join("&");
}
/** Registro de auditoría: solo lectura, con filtros y detalle por registro. */
export function AuditLog() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [searchText, setSearchText] = useState("");
  const [query, setQuery] = useState("");
  const [roleId, setRoleId] = useState("");
  const [module, setModule] = useState("");
  const [action, setAction] = useState("");
  const [roles, setRoles] = useState<Role[]>([]);
  const [modules, setModules] = useState<string[]>([]);
  const [actions, setActions] = useState<string[]>([]);
  const [error, setError] = useState("");
  const openedInApp = useRef(false);
  const list = usePaged<Audit>(
    AUDITORIA_REGISTROS_PATH,
    query,
    filtersQuery({ rolId: roleId, modulo: module, accion: action }),
  );
  const { setPage } = list;
  const detailId = Number(searchParams.get(DETAIL_PARAM));
  const detail = list.data?.content.find((audit) => audit.id === detailId);
  useEffect(() => {
    let active = true;
    Promise.all([listarRoles(), listarModulos(), listarAcciones()])
      .then(([allRoles, moduleOptions, actionOptions]) => {
        if (!active) return;
        setRoles(
          allRoles.filter((role) => INSTITUTIONAL_ROLES.includes(role.nombre)),
        );
        setModules(moduleOptions);
        setActions(actionOptions);
      })
      .catch((e) => {
        if (active) setError(message(e));
      });
    return () => {
      active = false;
    };
  }, []);
  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(searchText.trim());
      setPage(0);
    }, SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [searchText, setPage]);
  function openDetail(id: number) {
    openedInApp.current = true;
    router.push(`${pathname}?${DETAIL_PARAM}=${id}`, { scroll: false });
  }
  function closeDetail() {
    if (openedInApp.current) router.back();
    else router.replace(pathname, { scroll: false });
    openedInApp.current = false;
  }
  function filterSelect(
    label: string,
    allLabel: string,
    value: string,
    onChange: (value: string) => void,
    options: { value: string; label: string }[],
  ) {
    return (
      <select
        aria-label={label}
        value={value}
        onChange={(e) => {
          onChange(e.target.value);
          setPage(0);
        }}
      >
        <option value="">{allLabel}</option>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    );
  }
  return (
    <section>
      <Feedback error={error || list.error} />
      <div className="panel">
        <div className={styles.listHeading}>
          <h3>Registro de auditoría</h3>
          <p>Solo lectura · Trazabilidad completa de acciones del sistema</p>
        </div>
        <div className={styles.filters}>
          <input
            type="search"
            aria-label="Buscar usuario"
            placeholder="Buscar usuario…"
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
          />
          {filterSelect(
            "Filtrar por rol",
            "Todos los roles",
            roleId,
            setRoleId,
            roles.map((role) => ({
              value: String(role.id),
              label: roleLabel(role.nombre),
            })),
          )}
          {filterSelect(
            "Filtrar por módulo",
            "Todos los módulos",
            module,
            setModule,
            modules.map((code) => ({ value: code, label: moduleLabel(code) })),
          )}
          {filterSelect(
            "Filtrar por acción",
            "Todas las acciones",
            action,
            setAction,
            actions.map((code) => ({ value: code, label: actionLabel(code) })),
          )}
        </div>
        {list.loading ? (
          <p role="status">Cargando auditoría…</p>
        ) : list.data?.content.length ? (
          <div className={styles.tableWrap}>
            <table className={`user-table ${styles.table}`}>
              <thead>
                <tr>
                  <th>Fecha y hora</th>
                  <th>Usuario</th>
                  <th>Rol</th>
                  <th>Módulo</th>
                  <th>Acción</th>
                  <th>Registro afectado</th>
                  <th>Detalle</th>
                </tr>
              </thead>
              <tbody>
                {list.data.content.map((audit) => {
                  const actorName = actorDisplayName(audit);
                  return (
                    <tr key={audit.id}>
                      <td data-label="Fecha y hora" className={styles.date}>
                        {dateTimeLabel(audit.fechaHora)}
                      </td>
                      <td data-label="Usuario">
                        <div className={styles.userCell}>
                          <span className={styles.initials} aria-hidden="true">
                            {initialsOf(actorName)}
                          </span>
                          <div>
                            <strong>{actorName}</strong>
                            {audit.actorNombre && (
                              <small>{audit.actorIdentificador}</small>
                            )}
                          </div>
                        </div>
                      </td>
                      <td data-label="Rol">
                        {audit.rolActor ? (
                          <span className={rolePillClass(audit.rolActor)}>
                            {roleLabel(audit.rolActor)}
                          </span>
                        ) : (
                          "—"
                        )}
                      </td>
                      <td data-label="Módulo">
                        <span className={modulePillClass(audit.modulo)}>
                          {moduleLabel(audit.modulo)}
                        </span>
                      </td>
                      <td data-label="Acción" className={styles.nowrap}>
                        <span className={styles.action}>
                          {actionLabel(audit.accion)}
                        </span>
                        {audit.resultado !== "EXITOSO" && (
                          <span className={styles.failed}>
                            {resultLabels[audit.resultado]}
                          </span>
                        )}
                      </td>
                      <td
                        data-label="Registro afectado"
                        className={styles.nowrap}
                      >
                        {entityLabel(audit.entidad)}
                        {audit.registroId ? ` #${audit.registroId}` : ""}
                      </td>
                      <td data-label="Detalle" className={styles.detailCell}>
                        <button
                          type="button"
                          className={styles.iconButton}
                          aria-label={`Ver detalle del registro de ${actorName}`}
                          title="Ver detalle"
                          onClick={() => openDetail(audit.id)}
                        >
                          <Eye />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          !list.error && (
            <p className="empty">No hay operaciones con estos filtros.</p>
          )
        )}
        <Pager data={list.data} loading={list.loading} setPage={setPage} />
      </div>
      {detail && <AuditDetailDialog audit={detail} onClose={closeDetail} />}
    </section>
  );
}

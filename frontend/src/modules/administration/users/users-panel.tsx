"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Eye, Feedback, Pager } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { roleLabel } from "@/shared/lib/formatters";
import type { Account, Role } from "../types";
import { DeactivateAccessDialog } from "./deactivate-access-dialog";
import { EditAccessDialog } from "./edit-access-dialog";
import { ResetPasswordDialog } from "./reset-password-dialog";
import { listarRoles, USUARIOS_PATH } from "./services/usuarioService";
import {
  accessStates,
  dateTimeLabel,
  initialsOf,
  rolePillClass,
  stateLabels,
  statePillClass,
} from "./user-format";
import { UserDetailDialog } from "./user-detail-dialog";
import { UserRowMenu, type MenuOption } from "./user-row-menu";
import { useUserDialog, type UserDialogKind } from "./use-user-dialog";
import styles from "./users.module.css";
const SEARCH_DELAY_MS = 300;
const INSTITUTIONAL_ROLES = ["ADMINISTRADOR", "DOCENTE", "ALUMNO", "APODERADO"];
function filtersQuery(roleId: string, state: string) {
  return [roleId && `rolId=${roleId}`, state && `estado=${state}`]
    .filter(Boolean)
    .join("&");
}
/** Gestión de usuarios: listado, detalle (ojo) y acciones (menú ⋮). */
export function UsersPanel({
  currentUserId,
  onCreate,
}: {
  currentUserId: string;
  onCreate: () => void;
}) {
  const [searchText, setSearchText] = useState("");
  const [query, setQuery] = useState("");
  const [roleId, setRoleId] = useState("");
  const [state, setState] = useState("");
  const [roles, setRoles] = useState<Role[]>([]);
  const users = usePaged<Account>(
    USUARIOS_PATH,
    query,
    filtersQuery(roleId, state),
  );
  const { setPage } = users;
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const dialog = useUserDialog(setError);
  useEffect(() => {
    let active = true;
    listarRoles()
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
  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(searchText.trim());
      setPage(0);
    }, SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [searchText, setPage]);
  function openDialog(kind: UserDialogKind, account: Account) {
    setError("");
    setSuccess("");
    dialog.open(kind, account.id);
  }
  function finish(feedback: string) {
    dialog.close();
    users.refresh();
    setSuccess(feedback);
  }
  function menuOptions(account: Account): MenuOption[] {
    const open = (kind: UserDialogKind) => () => openDialog(kind, account);
    if (String(account.id) === currentUserId)
      return [{ label: "Editar correo", onSelect: open("edit") }];
    return [
      { label: "Editar", onSelect: open("edit") },
      { label: "Restablecer contraseña", onSelect: open("reset") },
      ...(account.estado === "DESACTIVADO"
        ? []
        : [
            { label: "Desactivar", onSelect: open("deactivate"), danger: true },
          ]),
    ];
  }
  const total = users.data?.totalElements ?? 0;
  return (
    <section>
      <Feedback error={error || users.error} success={success} />
      <div className="panel">
        <div className={styles.listHeading}>
          <div>
            <h3>Gestión de usuarios</h3>
            <p>
              {users.data
                ? `${users.data.content.length} de ${total} ${total === 1 ? "usuario" : "usuarios"}`
                : "Accesos institucionales"}
            </p>
          </div>
          <button className="primary-action" onClick={onCreate}>
            + Nuevo usuario
          </button>
        </div>
        <div className={styles.filters}>
          <label className={styles.searchBox}>
            <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
              <circle
                cx="11"
                cy="11"
                r="7"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
              />
              <path d="m20 20-3.5-3.5" stroke="currentColor" strokeWidth="2" />
            </svg>
            <input
              type="search"
              aria-label="Buscar usuarios"
              placeholder="Buscar por nombre, usuario o correo…"
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
            />
          </label>
          <select
            className={styles.filterSelect}
            aria-label="Filtrar por rol"
            value={roleId}
            onChange={(e) => {
              setRoleId(e.target.value);
              setPage(0);
            }}
          >
            <option value="">Todos los roles</option>
            {roles.map((role) => (
              <option key={role.id} value={role.id}>
                {roleLabel(role.nombre)}
              </option>
            ))}
          </select>
          <select
            className={styles.filterSelect}
            aria-label="Filtrar por estado"
            value={state}
            onChange={(e) => {
              setState(e.target.value);
              setPage(0);
            }}
          >
            <option value="">Todos los estados</option>
            {accessStates.map((option) => (
              <option key={option} value={option}>
                {stateLabels[option]}
              </option>
            ))}
          </select>
        </div>
        {users.loading ? (
          <p role="status">Cargando usuarios…</p>
        ) : users.data?.content.length ? (
          <div className={styles.tableWrap}>
            <table className={`user-table ${styles.table}`}>
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Usuario</th>
                  <th>Rol</th>
                  <th>Estado</th>
                  <th>Último acceso</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {users.data.content.map((account) => (
                  <tr key={account.id}>
                    <td data-label="Nombre">
                      <div className={styles.userCell}>
                        <span className={styles.initials} aria-hidden="true">
                          {initialsOf(account.nombreCompleto)}
                        </span>
                        <strong>{account.nombreCompleto}</strong>
                      </div>
                    </td>
                    <td data-label="Usuario">{account.username}</td>
                    <td data-label="Rol">
                      <span className={rolePillClass(account.rol)}>
                        {roleLabel(account.rol)}
                      </span>
                    </td>
                    <td data-label="Estado">
                      <span className={statePillClass(account.estado)}>
                        {stateLabels[account.estado] ?? account.estado}
                      </span>
                    </td>
                    <td data-label="Último acceso">
                      {dateTimeLabel(account.ultimoAcceso)}
                    </td>
                    <td data-label="Acciones">
                      <div className={styles.actions}>
                        <button
                          type="button"
                          className={styles.iconButton}
                          aria-label={`Ver detalle de ${account.nombreCompleto}`}
                          title="Ver detalle"
                          onClick={() => openDialog("detail", account)}
                        >
                          <Eye />
                        </button>
                        <UserRowMenu
                          userName={account.nombreCompleto}
                          options={menuOptions(account)}
                        />
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          !users.error && <p className="empty">No se encontraron usuarios.</p>
        )}
        <Pager data={users.data} loading={users.loading} setPage={setPage} />
      </div>
      {dialog.account && dialog.kind === "detail" && (
        <UserDetailDialog
          account={dialog.account}
          onClose={dialog.close}
          onEdit={() => dialog.switchTo("edit", dialog.account!.id)}
        />
      )}
      {dialog.account && dialog.kind === "edit" && (
        <EditAccessDialog
          account={dialog.account}
          isCurrentUser={String(dialog.account.id) === currentUserId}
          onClose={dialog.close}
          onSaved={() => finish("Acceso actualizado.")}
        />
      )}
      {dialog.account && dialog.kind === "reset" && (
        <ResetPasswordDialog
          account={dialog.account}
          onClose={dialog.close}
          onSaved={() =>
            finish(
              "Contraseña temporal restablecida. Las sesiones anteriores fueron revocadas.",
            )
          }
        />
      )}
      {dialog.account && dialog.kind === "deactivate" && (
        <DeactivateAccessDialog
          account={dialog.account}
          onClose={dialog.close}
          onDeactivated={() => finish("Usuario desactivado.")}
        />
      )}
    </section>
  );
}

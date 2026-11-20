import { roleLabel } from "@/shared/lib/formatters";
import type { Account } from "../types";
import { accountRoles, rolePillClass } from "./user-format";
import styles from "./users.module.css";

export function AccountRoles({ account }: { account: Account }) {
  const roles = accountRoles(account);
  if (!roles.length) return <span>Sin roles</span>;
  return (
    <div className={styles.pillRow}>
      {roles.map((role) => (
        <span key={role.id} className={rolePillClass(role.activo ? role.nombre : "")}>
          {roleLabel(role.nombre)}{role.activo ? "" : " (inactivo)"}
        </span>
      ))}
    </div>
  );
}

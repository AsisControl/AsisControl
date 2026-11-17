import type { UserRole } from "@/core/auth/types";
import { roleSpaces } from "./role-space";
import styles from "./role-context.module.css";

function SpaceIcon({ icon }: { icon: (typeof roleSpaces)[UserRole]["icon"] }) {
  return <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {icon === "panel" && <><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></>}
    {icon === "book" && <><path d="M12 6v15M3 3h4a5 5 0 0 1 5 3 5 5 0 0 1 5-3h4v15h-4a5 5 0 0 0-5 3 5 5 0 0 0-5-3H3z" /></>}
    {icon === "graduation" && <><path d="m2 9 10-5 10 5-10 5zM6 11v6c4 3 8 3 12 0v-6M22 9v7" /></>}
    {icon === "people" && <><circle cx="9" cy="8" r="3" /><path d="M3 21v-2a6 6 0 0 1 12 0v2M16 5a3 3 0 0 1 0 6M21 21v-2a6 6 0 0 0-4-5.65" /></>}
  </svg>;
}

export function RoleContext({ role }: { role: UserRole }) {
  const space = roleSpaces[role];
  return <span className={styles.identity} role="group" aria-label="Espacio del usuario" title={space.title}>
    <SpaceIcon icon={space.icon} />
    <span className={styles.label}>{space.title}</span>
  </span>;
}

"use client";
import { useEffect, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { AcademicConfiguration } from "@/modules/academic-config";
import { CreateAccess } from "./create-access";
import { RolesMatrix } from "./roles-matrix";
import { SystemLogs } from "./system-logs";
import { AuditLog } from "./audit/audit-log";
import { UsersPanel } from "./users/users-panel";
import styles from "./administration.module.css";
type AdminSection = {
  /** Segmento de la URL: /administracion/{id}. */
  id: string;
  label: string;
  /** Sin render se muestra la gestión de usuarios (./users). */
  render?: () => ReactNode;
};
/**
 * Pestañas de Administración en el orden del prototipo. Cada módulo agrega
 * aquí su entrada una sola vez; el contenido vive en su carpeta de src/modules.
 */
const sections: AdminSection[] = [
  {
    id: "configuracion-academica",
    label: "Configuración académica",
    render: () => <AcademicConfiguration />,
  },
  { id: "usuarios", label: "Usuarios" },
  {
    id: "roles-y-permisos",
    label: "Roles y permisos",
    render: () => <RolesMatrix />,
  },
  { id: "auditoria", label: "Auditoría", render: () => <AuditLog /> },
  {
    id: "logs-del-sistema",
    label: "Logs del sistema",
    render: () => <SystemLogs />,
  },
];
export const ADMINISTRATION_PATH = "/administracion";
const USERS_PATH = `${ADMINISTRATION_PATH}/usuarios`;
const CREATE_VIEW = "nuevo";
/** La pestaña y la vista salen de la URL: /administracion/{tab}/{view}. */
export function Administration({
  currentUserId,
  tab,
  view,
}: {
  currentUserId: string;
  tab?: string;
  view?: string;
}) {
  const router = useRouter();
  const section = sections.find((s) => s.id === tab);
  const isCreating = section?.id === "usuarios" && view === CREATE_VIEW;
  const isValidPath = Boolean(section) && (!view || isCreating);
  useEffect(() => {
    if (!isValidPath) router.replace(USERS_PATH);
  }, [isValidPath, router]);
  if (!section || !isValidPath) return null;
  if (isCreating)
    return <CreateAccess onBack={() => router.push(USERS_PATH)} />;
  return (
    <section>
      <div className={styles.heading}>
        <h2>Administración</h2>
        <p>Configuración académica, usuarios, roles y auditoría del sistema</p>
      </div>
      <nav className={styles.tabs} aria-label="Secciones de administración">
        {sections.map(({ id, label }) => (
          <button
            key={id}
            aria-current={section.id === id ? "page" : undefined}
            onClick={() => router.push(`${ADMINISTRATION_PATH}/${id}`)}
          >
            {label}
          </button>
        ))}
      </nav>
      {section.render ? (
        section.render()
      ) : (
        <UsersPanel
          currentUserId={currentUserId}
          onCreate={() => router.push(`${USERS_PATH}/${CREATE_VIEW}`)}
        />
      )}
    </section>
  );
}

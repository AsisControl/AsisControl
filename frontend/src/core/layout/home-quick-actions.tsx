"use client";

import { usePathname, useRouter } from "next/navigation";
import type { AuthenticatedUser } from "@/core/auth/types";
import { canAccess } from "@/core/navigation/access-policy";
import { navPath } from "@/core/navigation/navigation";
import { NavIcon } from "@/core/navigation/nav-icons";
import { roleSpaces } from "./role-space";
import styles from "./home-quick-actions.module.css";

/** Acciones de contenido de Inicio; no crea una segunda navegación del shell. */
export function HomeQuickActions({ user, embedded = false }: { user: AuthenticatedUser; embedded?: boolean }) {
  const pathname = usePathname();
  const router = useRouter();
  const shortcuts = roleSpaces[user.role].shortcuts.filter((label) => canAccess(user, label));
  if (pathname !== "/" || !shortcuts.length) return null;
  return <section className={`${styles.card} ${embedded ? styles.embedded : ""}`} aria-label="Acciones rápidas">
    <h3>Acciones rápidas</h3>
    <p>{roleSpaces[user.role].description}</p>
    <div className={styles.actions}>
      {shortcuts.map((label) => <a key={label} href={navPath(label)} onClick={(event) => {
        if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        router.push(navPath(label));
      }}><NavIcon label={label} />{label}<span className={styles.arrow} aria-hidden="true">›</span></a>)}
    </div>
  </section>;
}

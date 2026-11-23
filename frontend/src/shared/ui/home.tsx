import Link from "next/link";
import type { ReactNode } from "react";
import { Skeleton } from "./skeleton";
import styles from "./home.module.css";

/**
 * Piezas comunes de la pantalla de Inicio de todos los roles (administrador, docente, estudiante y
 * apoderado): mismos indicadores, mismas tarjetas y misma distribución. Cada rol aporta su contenido.
 */
export type HomeTone = "primary" | "success" | "warning" | "danger" | "info";

const TONES: Record<HomeTone, string> = {
  primary: styles.primary,
  success: styles.success,
  warning: styles.warning,
  danger: styles.danger,
  info: styles.info,
};

/** Fila de indicadores resumen. */
export function HomeIndicators({ children }: { children: ReactNode }) {
  return <div className={styles.indicators}>{children}</div>;
}

/** Indicador: icono en un recuadro de color suave y cifra con el color de su estado. */
export function HomeIndicator({
  icon,
  value,
  label,
  tone = "primary",
  href,
}: {
  icon: ReactNode;
  value: string | number;
  label: string;
  tone?: HomeTone;
  /** Si lo trae, el indicador lleva a la pantalla donde se atiende. */
  href?: string;
}) {
  const content = (
    <>
      <span className={`${styles.indicatorIcon} ${TONES[tone]}`} aria-hidden="true">
        {icon}
      </span>
      <div>
        <strong className={`${TONES[tone]} ${typeof value === "string" && value.length > 8 ? styles.compact : ""}`}>
          {value}
        </strong>
        <span>{label}</span>
      </div>
    </>
  );
  if (!href) return <article className={styles.indicator}>{content}</article>;
  return (
    <Link className={`${styles.indicator} ${styles.indicatorLink}`} href={href}>
      {content}
    </Link>
  );
}

/** Mismas tarjetas que los indicadores reales mientras llegan los datos. */
export function HomeIndicatorsSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className={styles.indicators} aria-hidden="true">
      {Array.from({ length: count }, (_, indicador) => (
        <article className={styles.indicator} key={indicador}>
          <Skeleton width={40} height={40} radius={8} />
          <div className={styles.skeletonText}>
            <Skeleton width={36} height={24} />
            <Skeleton width={120} height={12} />
          </div>
        </article>
      ))}
    </div>
  );
}

/**
 * Tarjetas de contenido bajo los indicadores. "wide" reparte 5/4/3 en pantallas anchas (clases,
 * pendientes, acciones); "equal" reparte tres partes iguales.
 */
export function HomeColumns({ layout = "wide", children }: { layout?: "wide" | "equal"; children: ReactNode }) {
  return <div className={`${styles.columns} ${layout === "equal" ? styles.equal : styles.wide}`}>{children}</div>;
}

/** Tarjeta de contenido: título, resumen opcional, enlace a la pantalla completa y contenido. */
export function HomeCard({
  title,
  action,
  as: Tag = "article",
  children,
}: {
  title: string;
  action?: { label: string; href: string };
  as?: "article" | "section";
  children: ReactNode;
}) {
  return (
    <Tag className={styles.card}>
      <div className={styles.cardHead}>
        <h3>{title}</h3>
        {action && (
          <Link href={action.href}>
            {action.label} <span aria-hidden="true">→</span>
          </Link>
        )}
      </div>
      {children}
    </Tag>
  );
}

/** Tarjetas de contenido en espera, con la misma cabecera y filas de dos líneas. */
export function HomeCardsSkeleton({ count = 3, layout = "wide" }: { count?: number; layout?: "wide" | "equal" }) {
  return (
    <div className={`${styles.columns} ${layout === "equal" ? styles.equal : styles.wide}`} aria-hidden="true">
      {Array.from({ length: count }, (_, tarjeta) => (
        <article className={styles.card} key={tarjeta}>
          <Skeleton width="62%" height={18} />
          <div className={styles.skeletonRows}>
            {[0, 1, 2].map((fila) => (
              <div className={styles.skeletonRow} key={fila}>
                <Skeleton width={fila === 1 ? "54%" : "72%"} height={14} />
                <Skeleton width="42%" height={11} />
              </div>
            ))}
          </div>
        </article>
      ))}
    </div>
  );
}

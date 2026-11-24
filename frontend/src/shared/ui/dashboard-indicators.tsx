import Link from "next/link";
import { NavIcon } from "@/core/navigation/nav-icons";
import { Skeleton } from "./skeleton";
import type { Indicador } from "@/core/api/dashboard";
import styles from "./dashboard-indicators.module.css";

export type DestinoIndicador = { icono: string; enlace?: string };

const CLASE_TONO = {
  neutral: styles.neutral,
  success: styles.exito,
  warning: styles.advertencia,
  danger: styles.peligro,
};

export function IndicadoresSkeleton({ cantidad }: { cantidad: number }) {
  return (
    <div className={styles.indicadores} aria-hidden="true">
      {Array.from({ length: cantidad }, (_, posicion) => (
        <article className={styles.indicador} key={posicion}>
          <Skeleton width={40} height={40} radius={8} />
          <div className={styles.esqueletoTexto}>
            <Skeleton width={36} height={24} />
            <Skeleton width={120} height={12} />
          </div>
        </article>
      ))}
    </div>
  );
}

export function DashboardIndicators({ indicadores, destino }: {
  indicadores: Indicador[];
  destino: (codigo: string) => DestinoIndicador;
}) {
  return (
    <div className={styles.indicadores}>
      {indicadores.map(({ codigo, tono, valor, etiqueta }) => {
        const { icono, enlace } = destino(codigo);
        const color = CLASE_TONO[tono] ?? styles.neutral;
        const contenido = (
          <>
            <span className={`${styles.indicadorIcono} ${color}`} aria-hidden="true">
              <NavIcon label={icono} />
            </span>
            <div>
              <strong className={color}>{valor}</strong>
              <span>{etiqueta}</span>
            </div>
          </>
        );
        return enlace
          ? <Link key={codigo} className={`${styles.indicador} ${styles.indicadorEnlace}`} href={enlace}>{contenido}</Link>
          : <article key={codigo} className={styles.indicador}>{contenido}</article>;
      })}
    </div>
  );
}

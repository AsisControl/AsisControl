"use client";
import type { ReactNode } from "react";
import { ESTADOS_MARCA } from "./lib";
import { CheckIcon, ClockIcon, CrossIcon } from "./session-ui";
import styles from "./attendance.module.css";
import type { EstadoMarca } from "./types";

const ICONOS: Record<EstadoMarca, ReactNode> = {
  PRESENTE: <CheckIcon />,
  TARDANZA: <ClockIcon />,
  INASISTENCIA: <CrossIcon />,
};

/** Control segmentado: el estado elegido lleva fondo suave, negrita y su icono. */
export function MarkButtons({
  nombre,
  valor,
  disabled,
  onChange,
}: {
  nombre: string;
  valor: EstadoMarca | null | undefined;
  disabled?: boolean;
  onChange: (estado: EstadoMarca) => void;
}) {
  return (
    <div className={styles.segmentado} role="group" aria-label={`Asistencia de ${nombre}`}>
      {ESTADOS_MARCA.map(({ estado, etiqueta }) => (
        <button
          key={estado}
          type="button"
          disabled={disabled}
          aria-pressed={valor === estado}
          className={valor === estado ? styles[`marca-${estado}`] : undefined}
          onClick={() => onChange(estado)}
        >
          {valor === estado && ICONOS[estado]}
          {etiqueta}
        </button>
      ))}
    </div>
  );
}

/** Solo Tardanza e Inasistencia llevan píldora, para que las excepciones destaquen. */
export function MarkStatus({ estado }: { estado: EstadoMarca }) {
  if (estado === "PRESENTE")
    return (
      <span className={styles.presente}>
        <CheckIcon /> Presente
      </span>
    );
  const etiqueta = ESTADOS_MARCA.find((item) => item.estado === estado)?.etiqueta;
  return <span className={`${styles.chip} ${styles[`chip-${estado}`]}`}>{etiqueta}</span>;
}

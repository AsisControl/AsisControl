// asistencia alumnos
"use client";
import { useId, useState } from "react";
import { enmascararFecha, parsearFecha } from "./lib";
import styles from "./guardian-attendance.module.css";

/**
 * Fecha escrita siempre como dd/mm/aaaa (el navegador decide el formato de type="date").
 * Hacia afuera entrega ISO yyyy-mm-dd, o "" mientras esté vacía o incompleta/inválida.
 */
export function DateField({ label, onChange }: { label: string; onChange: (iso: string) => void }) {
  const [texto, setTexto] = useState("");
  const errorId = useId();
  const invalida = texto.length === 10 && parsearFecha(texto) === null;
  return (
    <label className={styles.etiqueta}>
      <span>{label}</span>
      <input
        className={styles.control}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        placeholder="dd/mm/aaaa"
        maxLength={10}
        value={texto}
        aria-invalid={invalida}
        aria-describedby={invalida ? errorId : undefined}
        onChange={(event) => {
          const nuevo = enmascararFecha(event.target.value);
          setTexto(nuevo);
          onChange(parsearFecha(nuevo) ?? "");
        }}
      />
      {invalida && <small id={errorId} role="alert" className={styles.errorCampo}>Fecha no válida</small>}
    </label>
  );
}

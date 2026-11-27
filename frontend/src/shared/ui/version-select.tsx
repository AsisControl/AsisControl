"use client";

import { useId, useRef, useState } from "react";
import styles from "./checkbox-select.module.css";

export function VersionSelect({ value, options, onChange }: {
  value: string; options: { value: string; label: string }[]; onChange: (value: string) => void;
}) {
  const id = useId();
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const trigger = useRef<HTMLButtonElement>(null);
  function close() { setOpen(false); setSearch(""); }
  return <div className={styles.root} onBlur={event => {
    if (!event.currentTarget.contains(event.relatedTarget)) close();
  }} onKeyDown={event => {
    if (open && event.key === "Escape") {
      event.stopPropagation(); close(); trigger.current?.focus();
    }
  }}>
    <span id={id + "-label"} className={styles.fieldLabel}>Configuración de partida</span>
    <button type="button" ref={trigger} className={styles.trigger} aria-expanded={open}
      aria-labelledby={id + "-label " + id + "-value"} aria-controls={id}
      onClick={() => open ? close() : setOpen(true)}>
      <span id={id + "-value"}>{options.find(option => option.value === value)?.label}</span><span aria-hidden="true">⌄</span>
    </button>
    <div id={id} className={styles.panel} hidden={!open}>
      {options.length > 5 && <label className="field">Buscar versión<input type="search" value={search}
        onChange={event => setSearch(event.target.value)} /></label>}
      <div className={styles.options} role="group" aria-label="Versiones disponibles">
        {options.filter(option => option.label.toLocaleLowerCase("es").includes(search.trim().toLocaleLowerCase("es")))
          .map(option => <button type="button" key={option.value} className={styles.optionButton}
            aria-current={option.value === value || undefined} onClick={() => {
              close(); trigger.current?.focus(); onChange(option.value);
            }}>{option.label}</button>)}
      </div>
    </div>
  </div>;
}

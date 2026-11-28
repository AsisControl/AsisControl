"use client";
import { useId, useRef, useState } from "react";
import styles from "./checkbox-select.module.css";

type Option = { id: number; label: string };
const normalize = (text: string) => text.normalize("NFD").replace(/\p{M}/gu, "").toLocaleLowerCase("es");

export function CheckboxSelect({ singular, plural, options, selected, onChange }: {
  singular: string; plural: string; options: Option[]; selected: number[]; onChange: (ids: number[]) => void;
}) {
  const [open, setOpen] = useState(false);
  const [above, setAbove] = useState(false);
  const [search, setSearch] = useState("");
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const id = useId();
  const shown = options.filter(option => normalize(option.label).includes(normalize(search.trim())));
  const count = options.filter(option => selected.includes(option.id)).length;
  const summary = count === 0 ? `Seleccionar ${plural}`
    : count === options.length ? `Todas las ${plural}`
      : count === 1 ? `1 ${singular} seleccionada` : `${count} ${plural} seleccionadas`;
  return <div ref={root} className={styles.root} onBlur={event => {
    if (!root.current?.contains(event.relatedTarget)) setOpen(false);
  }} onKeyDown={event => {
    if (open && event.key === "Escape") {
      event.stopPropagation();
      setOpen(false);
      trigger.current?.focus();
    }
  }}>
    <button ref={trigger} type="button" className={styles.trigger}
      aria-expanded={open} aria-controls={id} onClick={event => {
        if (!open) {
          const button = event.currentTarget.getBoundingClientRect();
          const dialog = event.currentTarget.closest("[role='dialog']")?.getBoundingClientRect();
          const below = Math.min(window.innerHeight, dialog?.bottom ?? window.innerHeight) - button.bottom;
          const spaceAbove = button.top - Math.max(0, dialog?.top ?? 0);
          setAbove(below < 145 + Math.min(shown.length, 5) * 36 && spaceAbove > below);
        }
        setOpen(!open);
      }}>{summary}<span aria-hidden="true">⌄</span></button>
    <div id={id} className={styles.panel} data-above={above ? "true" : undefined} hidden={!open}>
      <label className="field">Buscar {plural}<input type="search" value={search} onChange={event => setSearch(event.target.value)} /></label>
      <div className={styles.actions}>
        <button type="button" className={styles.listAction} disabled={!options.length} onClick={() => onChange([...new Set([...selected, ...options.map(item => item.id)])])}
          title="Incluye todas las opciones, incluso fuera de la búsqueda">Seleccionar todas</button>
        <button type="button" className={styles.listAction} disabled={!count} onClick={() => onChange(selected.filter(item => !options.some(option => option.id === item)))}>Limpiar selección</button>
        <small aria-live="polite">{count} de {options.length}</small>
      </div>
      <div className={styles.options} role="group" aria-label={`Lista de ${plural}`}>
        {shown.map(option => <label key={option.id}>
          <input type="checkbox" checked={selected.includes(option.id)} onChange={event =>
            onChange(event.target.checked ? [...selected, option.id] : selected.filter(item => item !== option.id))} />
          <span title={option.label}>{option.label}</span>
        </label>)}
        {!shown.length && <p className="hint">No hay {plural} que coincidan con la búsqueda.</p>}
      </div>
    </div>
  </div>;
}

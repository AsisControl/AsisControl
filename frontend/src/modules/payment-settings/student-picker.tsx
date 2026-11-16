"use client";
import { useState } from "react";
import { Feedback, Pager } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { SETTINGS_PATH } from "./services/settings-service";
import type { StudentOption } from "./types";
import styles from "./payment-settings.module.css";

export function PaymentStudentPicker({ value, disabled = false, allowChange = true, onSelect }: { value: StudentOption | null; disabled?: boolean; allowChange?: boolean; onSelect: (value: StudentOption | null) => void }) {
  const [search, setSearch] = useState(""), [query, setQuery] = useState("");
  const list = usePaged<StudentOption>(`${SETTINGS_PATH}/estudiantes`, "", `busqueda=${encodeURIComponent(query)}`, 5);
  function find() { setQuery(search.trim()); list.setPage(0); }
  return <div className="full-field">
    {value ? <div className={styles.selected}><span><strong>{value.nombreCompleto}</strong>{value.codigoAlumno && <small> · {value.codigoAlumno}</small>}</span>{allowChange && <button type="button" className="secondary-action" disabled={disabled} onClick={() => onSelect(null)}>Cambiar estudiante</button>}</div> : <>
      <div className={styles.toolbar}><label className="field">Buscar estudiante<input value={search} disabled={disabled} placeholder="Nombre o código" onChange={(e) => setSearch(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); find(); } }} /></label><button type="button" className="secondary-action" disabled={disabled} onClick={find}>Buscar estudiante</button></div>
      <Feedback error={list.error} />
      {list.loading ? <p role="status">Buscando estudiantes…</p> : list.error ? <button type="button" className="secondary-action" onClick={list.refresh}>Reintentar estudiantes</button> : !list.data?.content.length ? <p className={styles.empty}>No hay estudiantes que coincidan con la búsqueda.</p> : <ul className={styles.choices}>{list.data.content.map((student) => <li key={student.id}><span><strong>{student.nombreCompleto}</strong><small>{student.codigoAlumno}</small></span><button type="button" className="secondary-action" disabled={disabled} aria-label={`Elegir a ${student.nombreCompleto}`} onClick={() => onSelect(student)}>Elegir</button></li>)}</ul>}
      <Pager data={list.data} loading={list.loading || disabled} setPage={list.setPage} />
    </>}
  </div>;
}

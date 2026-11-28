"use client";

import type { FormEvent } from "react";
import { useState } from "react";
import { usePaged } from "@/shared/hooks/use-paged";
import { Feedback, Field, Loading, Pager } from "@/shared/ui";
import type { Kinship } from "./services/relationship-service";
import styles from "./academic-records.module.css";

const kinships: Record<Kinship, string> = {
  PADRE: "Padre",
  MADRE: "Madre",
  TUTOR_LEGAL: "Tutor legal",
  OTRO_FAMILIAR: "Otro familiar",
  NO_DECLARADO: "Sin declarar",
};

export function kinshipLabel(value: string) {
  return kinships[value as Kinship] ?? "Sin declarar";
}

export function RelationshipFields({ kinship = "NO_DECLARADO", principal = false }: {
  kinship?: Kinship;
  principal?: boolean;
}) {
  return <>
    <label className="field">
      Parentesco
      <select name="parentesco" defaultValue={kinship}>
        {Object.entries(kinships).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select>
    </label>
    <label className={styles.principalField}>
      <input type="checkbox" name="principal" defaultChecked={principal} />
      Apoderado principal
    </label>
  </>;
}

export function readRelationshipFields(form: FormData) {
  return {
    parentesco: String(form.get("parentesco")) as Kinship,
    principal: form.has("principal"),
  };
}

type RecordOption = {
  id: number;
  activo: boolean;
  persona: { nombreCompleto: string; activo: boolean };
};

export function RecordPicker<T extends RecordOption>({ title, searchLabel, path, code, selection, onSelect }: {
  title: string;
  searchLabel: string;
  path: string;
  code: (item: T) => string | null;
  selection: T | null;
  onSelect: (item: T | null) => void;
}) {
  const [search, setSearch] = useState("");
  const list = usePaged<T>(path, search, "", 10);
  const items = list.data?.content.filter(item => item.activo && item.persona.activo) ?? [];
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSearch(String(new FormData(event.currentTarget).get("search") ?? "").trim());
    list.setPage(0);
    list.refresh();
  }

  return <section className={styles.relationshipSection} aria-label={title}>
    <h4>{title}</h4>
    {selection ? <div className={styles.selectedRecord}>
      <span><strong>{selection.persona.nombreCompleto}</strong><br />{code(selection)}</span>
      <button type="button" className="secondary-action" onClick={() => onSelect(null)}>Cambiar {title.toLowerCase()}</button>
    </div> : <>
      <form className="toolbar" onSubmit={submit}>
        <Field label={searchLabel} name="search" />
        <button disabled={list.loading}>Buscar {title.toLowerCase()}</button>
      </form>
      {list.loading ? <Loading>Buscando fichas…</Loading> : list.error ? <>
        <Feedback error={list.error} />
        <button type="button" className="secondary-action" onClick={list.refresh}>Reintentar búsqueda</button>
      </> : <>
        {items.length ? <label className="field">
          {title} encontrado
          <select value="" onChange={event => onSelect(items.find(item => String(item.id) === event.target.value) ?? null)}>
            <option value="">Selecciona una ficha</option>
            {items.map(item => <option key={item.id} value={item.id}>
              {item.persona.nombreCompleto}{code(item) ? ` · ${code(item)}` : ""}
            </option>)}
          </select>
        </label> : <p className="empty">No hay fichas activas en esta página.</p>}
        <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
      </>}
    </>}
  </section>;
}

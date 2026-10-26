import type { ReactNode } from "react";
import { stateLabel } from "@/shared/lib/formatters";
import { StatusPill } from "./status-pill";
import styles from "./record-sheet.module.css";

/** "2012-05-14" → "14/05/2012" sin pasar por Date (evita el desfase de zona horaria). */
export const isoDateLabel = (date: string | null | undefined) =>
  date ? date.split("-").reverse().join("/") : "—";

/** Valores del enum TipoDocumento del backend. */
const DOCUMENT_LABELS: Record<string, string> = {
  DNI: "DNI",
  PASAPORTE: "Pasaporte",
  CARNET_EXTRANJERIA: "Carné de extranjería",
};

/** PersonaDtos.PersonaResponse: lo que muestran todas las fichas. */
export type RecordSheetPerson = {
  nombres: string;
  apellidoPaterno: string;
  apellidoMaterno: string | null;
  nombreCompleto: string;
  tipoDocumento: string | null;
  numeroDocumento: string | null;
  nacionalidad?: string | null;
  sexo: string | null;
  fechaNacimiento: string;
  telefono: string | null;
  correo: string | null;
  direccion: string | null;
};

/** "DNI 12345678" para listados y fichas; "—" si falta el tipo o el número. */
export const documentLabel = (persona: RecordSheetPerson) =>
  persona.tipoDocumento && persona.numeroDocumento
    ? `${DOCUMENT_LABELS[persona.tipoDocumento] ?? persona.tipoDocumento} ${persona.numeroDocumento}`
    : "—";

export function RecordItem({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{children || "—"}</dd>
    </div>
  );
}

export function RecordStatus({ active }: { active: boolean }) {
  return (
    <StatusPill
      state={active ? "ACTIVO" : "INACTIVO"}
      label={active ? "Activo" : "Inactivo"}
      tone={active ? "success" : "neutral"}
    />
  );
}

/** Nombre y estado en la cabecera de la ficha. */
export function RecordHeader({
  name,
  active,
}: {
  name: string;
  active: boolean;
}) {
  return (
    <div className={styles.detailHeader}>
      <strong>{name}</strong>
      <RecordStatus active={active} />
    </div>
  );
}

/** Título de cada bloque de la ficha ("Datos personales", "Contacto"…). */
export function RecordSection({ title }: { title: string }) {
  return <h4 className={styles.detailTitle}>{title}</h4>;
}

/** Datos personales y de contacto, comunes a todas las fichas. */
export function PersonSections({ persona }: { persona: RecordSheetPerson }) {
  return (
    <>
      <RecordSection title="Datos personales" />
      <dl className="detail-grid">
        <RecordItem label="Nombres">{persona.nombres}</RecordItem>
        <RecordItem label="Apellidos">
          {[persona.apellidoPaterno, persona.apellidoMaterno]
            .filter(Boolean)
            .join(" ")}
        </RecordItem>
        <RecordItem label="Documento">
          {persona.tipoDocumento && documentLabel(persona)}
        </RecordItem>
        <RecordItem label="Fecha de nacimiento">
          {isoDateLabel(persona.fechaNacimiento)}
        </RecordItem>
        <RecordItem label="Sexo">
          {persona.sexo && stateLabel(persona.sexo)}
        </RecordItem>
        <RecordItem label="Nacionalidad">{persona.nacionalidad}</RecordItem>
      </dl>
      <RecordSection title="Contacto" />
      <dl className="detail-grid">
        <RecordItem label="Correo">{persona.correo}</RecordItem>
        <RecordItem label="Teléfono">{persona.telefono}</RecordItem>
        <RecordItem label="Dirección">{persona.direccion}</RecordItem>
      </dl>
    </>
  );
}

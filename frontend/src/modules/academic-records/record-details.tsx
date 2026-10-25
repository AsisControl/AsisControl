"use client";
import { useEffect, useState, type ReactNode } from "react";
import { api, message } from "@/core/api/client";
import { Dialog, Feedback, StatusPill } from "@/shared/ui";
import { specialtyLabel, stateLabel } from "@/shared/lib/formatters";
import type { Guardian, RecordPerson, Teacher } from "./types";
import styles from "./academic-records.module.css";

/** "2012-05-14" → "14/05/2012" sin pasar por Date (evita el desfase de zona horaria). */
export const isoDateLabel = (date: string | null | undefined) =>
  date ? date.split("-").reverse().join("/") : "—";

/** Valores del enum TipoDocumento del backend. */
const DOCUMENT_LABELS: Record<string, string> = {
  DNI: "DNI",
  PASAPORTE: "Pasaporte",
  CARNET_EXTRANJERIA: "Carné de extranjería",
};

/** Consulta la ficha al abrirla: el listado puede estar desactualizado. */
function useRecord<T>(path: string) {
  const [record, setRecord] = useState<T | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    api<T>(path)
      .then((value) => active && setRecord(value))
      .catch((cause) => active && setError(message(cause)));
    return () => {
      active = false;
    };
  }, [path]);
  return { record, error };
}

function Item({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{children || "—"}</dd>
    </div>
  );
}

function StatusOf({ active }: { active: boolean }) {
  return (
    <StatusPill
      state={active ? "ACTIVO" : "INACTIVO"}
      label={active ? "Activo" : "Inactivo"}
      tone={active ? "success" : "neutral"}
    />
  );
}

/** Datos personales y de contacto, comunes a todas las fichas. */
function PersonSections({ persona }: { persona: RecordPerson }) {
  return (
    <>
      <h4 className={styles.detailTitle}>Datos personales</h4>
      <dl className="detail-grid">
        <Item label="Nombres">{persona.nombres}</Item>
        <Item label="Apellidos">
          {[persona.apellidoPaterno, persona.apellidoMaterno]
            .filter(Boolean)
            .join(" ")}
        </Item>
        <Item label="Documento">
          {persona.tipoDocumento &&
            `${DOCUMENT_LABELS[persona.tipoDocumento] ?? persona.tipoDocumento} ${persona.numeroDocumento ?? ""}`}
        </Item>
        <Item label="Fecha de nacimiento">
          {isoDateLabel(persona.fechaNacimiento)}
        </Item>
        <Item label="Sexo">{persona.sexo && stateLabel(persona.sexo)}</Item>
        <Item label="Nacionalidad">{persona.nacionalidad}</Item>
      </dl>
      <h4 className={styles.detailTitle}>Contacto</h4>
      <dl className="detail-grid">
        <Item label="Correo">{persona.correo}</Item>
        <Item label="Teléfono">{persona.telefono}</Item>
        <Item label="Dirección">{persona.direccion}</Item>
      </dl>
    </>
  );
}

function RecordDialog<T>({
  title,
  path,
  onClose,
  children,
}: {
  title: string;
  path: string;
  onClose: () => void;
  children: (record: T) => ReactNode;
}) {
  const { record, error } = useRecord<T>(path);
  return (
    <Dialog title={title} size="lg" onClose={onClose}>
      {error ? (
        <Feedback error={error} />
      ) : record ? (
        children(record)
      ) : (
        <p role="status">Cargando ficha…</p>
      )}
      <div className="form-actions">
        <button type="button" onClick={onClose}>
          Cerrar
        </button>
      </div>
    </Dialog>
  );
}

export function GuardianDetail({
  id,
  onClose,
}: {
  id: number;
  onClose: () => void;
}) {
  return (
    <RecordDialog<Guardian>
      title="Ficha del apoderado"
      path={`/api/apoderados/${id}`}
      onClose={onClose}
    >
      {(guardian) => (
        <>
          <div className={styles.detailHeader}>
            <strong>{guardian.persona.nombreCompleto}</strong>
            <StatusOf active={guardian.activo} />
          </div>
          <PersonSections persona={guardian.persona} />
          <h4 className={styles.detailTitle}>Estudiantes a cargo</h4>
          {guardian.alumnos.length ? (
            <div className="user-table-wrap">
              <table className="user-table" aria-label="Estudiantes a cargo">
                <thead>
                  <tr>
                    <th scope="col">Código</th>
                    <th scope="col">Estudiante</th>
                    <th scope="col">Parentesco</th>
                    <th scope="col">Principal</th>
                  </tr>
                </thead>
                <tbody>
                  {guardian.alumnos.map((link) => (
                    <tr key={link.id}>
                      <td data-label="Código">{link.codigoAlumno}</td>
                      <td data-label="Estudiante">{link.nombreCompleto}</td>
                      <td data-label="Parentesco">
                        {stateLabel(link.parentesco)}
                      </td>
                      <td data-label="Principal">
                        {link.principal ? "Sí" : "No"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="empty">No tiene estudiantes vinculados.</p>
          )}
        </>
      )}
    </RecordDialog>
  );
}

export function TeacherDetail({
  id,
  onClose,
}: {
  id: number;
  onClose: () => void;
}) {
  return (
    <RecordDialog<Teacher>
      title="Ficha del docente"
      path={`/api/docentes/${id}`}
      onClose={onClose}
    >
      {(teacher) => (
        <>
          <div className={styles.detailHeader}>
            <strong>{teacher.persona.nombreCompleto}</strong>
            <StatusOf active={teacher.activo} />
          </div>
          <h4 className={styles.detailTitle}>Datos del docente</h4>
          <dl className="detail-grid">
            <Item label="Código">{teacher.codigoDocente}</Item>
            <Item label="Especialidad">
              {specialtyLabel(teacher.especialidad)}
            </Item>
            <Item label="Fecha de ingreso">
              {isoDateLabel(teacher.fechaIngreso)}
            </Item>
          </dl>
          <PersonSections persona={teacher.persona} />
        </>
      )}
    </RecordDialog>
  );
}

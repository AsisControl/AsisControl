"use client";
import { useEffect, useState, type ReactNode } from "react";
import { api, message } from "@/core/api/client";
import {
  Dialog,
  Feedback,
  isoDateLabel,
  PersonSections,
  RecordHeader,
  RecordItem,
  RecordSection,
} from "@/shared/ui";
import { specialtyLabel, stateLabel } from "@/shared/lib/formatters";
import type { Guardian, Teacher } from "./types";

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
          <RecordHeader
            name={guardian.persona.nombreCompleto}
            active={guardian.activo}
          />
          <PersonSections persona={guardian.persona} />
          <RecordSection title="Estudiantes a cargo" />
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
          <RecordHeader
            name={teacher.persona.nombreCompleto}
            active={teacher.activo}
          />
          <RecordSection title="Datos del docente" />
          <dl className="detail-grid">
            <RecordItem label="Código">{teacher.codigoDocente}</RecordItem>
            <RecordItem label="Especialidad">
              {specialtyLabel(teacher.especialidad)}
            </RecordItem>
            <RecordItem label="Fecha de ingreso">
              {isoDateLabel(teacher.fechaIngreso)}
            </RecordItem>
          </dl>
          <PersonSections persona={teacher.persona} />
        </>
      )}
    </RecordDialog>
  );
}

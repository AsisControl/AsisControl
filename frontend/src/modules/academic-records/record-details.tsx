"use client";
import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { fieldErrorsOf, message } from "@/core/api/client";
import { PersonFields, personData } from "@/shared/forms/person-fields";
import {
  Dialog,
  Feedback,
  Field,
  FormDialog,
  isoDateLabel,
  PersonSections,
  RecordHeader,
  RecordItem,
  RecordSection,
  Loading,
} from "@/shared/ui";
import { SPECIALTY_LABELS, specialtyLabel, stateLabel } from "@/shared/lib/formatters";
import { actualizarPerfil, obtenerPerfil } from "./services/profile-service";
import type { Guardian, Teacher } from "./types";

/** Consulta la ficha al abrirla: el listado puede estar desactualizado. */
function useRecord<T>(path: string) {
  const [record, setRecord] = useState<T | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    obtenerPerfil<T>(path)
      .then((value) => active && setRecord(value))
      .catch((cause) => active && setError(message(cause)));
    return () => {
      active = false;
    };
  }, [path]);
  return { record, error, setRecord };
}

function RecordDialog<T extends Guardian | Teacher>({
  title,
  path,
  onClose,
  onSaved,
  children,
}: {
  title: string;
  path: string;
  onClose: () => void;
  onSaved: () => void;
  children: (record: T) => ReactNode;
}) {
  const { record, error, setRecord } = useRecord<T>(path);
  const [editing, setEditing] = useState(false);
  const [saved, setSaved] = useState(false);
  if (editing && record) {
    return <EditRecord path={path} record={record} onClose={() => setEditing(false)}
      onSaved={(updated) => {
        setRecord(updated);
        setEditing(false);
        setSaved(true);
        onSaved();
      }} />;
  }
  return (
    <Dialog title={title} size="lg" onClose={onClose}>
      {saved && <Feedback success="Ficha actualizada." />}
      {error ? (
        <Feedback error={error} />
      ) : record ? (
        children(record)
      ) : (
        <Loading skeleton="detail" rows={8}>Cargando ficha…</Loading>
      )}
      <div className="form-actions">
        <button type="button" onClick={onClose}>
          Cerrar
        </button>
        {record?.activo && <button type="button" className="primary-action" onClick={() => setEditing(true)}>
          Editar ficha
        </button>}
      </div>
    </Dialog>
  );
}

function EditRecord<T extends Guardian | Teacher>({ path, record, onClose, onSaved }: {
  path: string; record: T; onClose: () => void; onSaved: (value: T) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const teacher = asTeacher(record);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const form = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    setErrors({});
    try {
      onSaved(await actualizarPerfil<T>(path, {
        persona: personData(form, record.persona.nacionalidadId ?? null),
        ...(teacher && {
          codigoDocente: teacher.codigoDocente,
          fechaIngreso: String(form.get("fechaIngreso")),
          especialidad: String(form.get("especialidad")),
        }),
      }));
    } catch (cause) {
      setErrors(fieldErrorsOf(cause));
      setError(message(cause));
    } finally {
      setBusy(false);
    }
  }

  return <FormDialog title={teacher ? "Editar ficha del docente" : "Editar ficha del apoderado"}
    description={record.persona.nombreCompleto} size="lg" busy={busy} error={error}
    onClose={onClose} onSubmit={save} submitLabel="Guardar cambios">
    <div className="full-field">
      <PersonFields person={record.persona} requireSecondSurname={false} errors={errors} />
    </div>
    {teacher && <>
      <Field label="Fecha de ingreso" name="fechaIngreso" type="date" required
        defaultValue={teacher.fechaIngreso} error={errors.fechaIngreso} />
      <label className="field">Especialidad *
        <select name="especialidad" required defaultValue={teacher.especialidad}>
          {Object.entries(SPECIALTY_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
    </>}
  </FormDialog>;
}

function asTeacher(record: Guardian | Teacher) {
  return "codigoDocente" in record ? record : null;
}

export function GuardianDetail({
  id,
  onClose,
  onSaved,
}: {
  id: number;
  onClose: () => void;
  onSaved: () => void;
}) {
  return (
    <RecordDialog<Guardian>
      title="Ficha del apoderado"
      path={`/api/apoderados/${id}`}
      onClose={onClose}
      onSaved={onSaved}
    >
      {(guardian) => (
        <>
          <RecordHeader
            name={guardian.persona.nombreCompleto}
            active={guardian.activo}
          />
          <RecordSection title="Datos del apoderado" />
          <dl className="detail-grid">
            <RecordItem label="Código">
              {guardian.codigoApoderado ?? "Sin código"}
            </RecordItem>
          </dl>
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
  onSaved,
}: {
  id: number;
  onClose: () => void;
  onSaved: () => void;
}) {
  return (
    <RecordDialog<Teacher>
      title="Ficha del docente"
      path={`/api/docentes/${id}`}
      onClose={onClose}
      onSaved={onSaved}
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

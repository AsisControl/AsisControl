"use client";
import { useEffect, useRef, useState } from "react";
import { readSession } from "@/core/auth/session-storage";
import {
  Feedback,
  Field,
  Pager,
  PanelHeading,
  PersonCell,
  StatusPill,
} from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { countLabel, timeLabel } from "@/shared/lib/formatters";
import { getStudent } from "./services/student-service";
import { studentErrorMessage } from "./student-errors";
import { StudentForm } from "./student-form";
import { StudentDetail } from "./student-detail";
import { studentName } from "./student-rules";
import { StudentImportDialog } from "./import-dialog";
import type { Student } from "./types";
import styles from "./students.module.css";

export function Students() {
  // La autorización definitiva y el alcance por registro siempre se validan en backend.
  if (readSession()?.user.role !== "ADMINISTRADOR")
    return (
      <Feedback error="No tienes permiso para gestionar las fichas de estudiantes." />
    );
  return <StudentManagement />;
}

function StudentManagement() {
  const [query, setQuery] = useState("");
  const list = usePaged<Student>("/api/alumnos", query);
  const [detail, setDetail] = useState<Student | null>(null);
  const [formMode, setFormMode] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [opening, setOpening] = useState(false);
  const request = useRef(0);
  useEffect(
    () => () => {
      request.current++;
    },
    [],
  );

  async function open(id: number) {
    const current = ++request.current;
    setError("");
    setSuccess("");
    setOpening(true);
    try {
      const loaded = await getStudent(id);
      if (current === request.current) setDetail(loaded);
    } catch (cause) {
      if (current === request.current) setError(studentErrorMessage(cause));
    } finally {
      if (current === request.current) setOpening(false);
    }
  }
  function saved(student: Student) {
    setDetail(student);
    setFormMode(false);
    list.refresh();
    setSuccess("Ficha guardada correctamente.");
  }
  const total = list.data?.totalElements ?? 0;
  return (
    <section className="panel" aria-label="Fichas de estudiantes">
      {(detail || formMode) && <Feedback error={error} success={success} />}
      {formMode ? (
        <StudentForm
          key={detail?.id ?? "new"}
          student={detail ?? undefined}
          onSaved={saved}
          onCancel={() => setFormMode(false)}
        />
      ) : detail ? (
        <StudentDetail
          key={detail.id}
          student={detail}
          onBack={() => {
            setDetail(null);
            setSuccess("");
            setError("");
          }}
          onEdit={() => {
            setSuccess("");
            setFormMode(true);
          }}
          onChanged={(updated) => {
            setDetail(updated);
            list.refresh();
            setSuccess(
              updated
                ? "Estado de la ficha actualizado."
                : "Ficha eliminada correctamente.",
            );
          }}
        />
      ) : (
        <>
          <PanelHeading
            title="Fichas de estudiantes"
            summary={
              <>
                {list.data
                  ? countLabel(total, "estudiante", "estudiantes")
                  : "Datos institucionales y apoderados asociados"}
                {list.updatedAt &&
                  ` · Última actualización: ${timeLabel(list.updatedAt)}`}
              </>
            }
            actions={
              <>
                <button
                  disabled={list.loading || opening}
                  onClick={list.refresh}
                >
                  Actualizar
                </button>
                <button
                  disabled={opening}
                  onClick={() => {
                    setError("");
                    setSuccess("");
                    setImportOpen(true);
                  }}
                >
                  Carga masiva
                </button>
                <button
                  className="primary-action"
                  disabled={opening}
                  onClick={() => {
                    setError("");
                    setSuccess("");
                    setDetail(null);
                    setFormMode(true);
                  }}
                >
                  + Registrar estudiante
                </button>
              </>
            }
          />
          <Feedback error={error} success={success} />
          <form
            className="toolbar"
            onSubmit={(event) => {
              event.preventDefault();
              setError("");
              setSuccess("");
              setQuery(
                String(
                  new FormData(event.currentTarget).get("search") || "",
                ).trim(),
              );
              list.setPage(0);
              list.refresh();
            }}
          >
            <Field
              label="Buscar por nombre, código o documento"
              name="search"
            />
            <button disabled={list.loading || opening}>Buscar</button>
          </form>
          {list.loading ? (
            <p role="status">Cargando fichas…</p>
          ) : list.error ? (
            <>
              <Feedback error="No se pudo cargar el listado. Comprueba la conexión e inténtalo nuevamente." />
              <div className={styles.actions}>
                <button type="button" onClick={list.refresh}>
                  Reintentar carga
                </button>
              </div>
            </>
          ) : list.data?.content.length ? (
            <>
              {opening && <p role="status">Consultando ficha…</p>}
              <div className="user-table-wrap">
                <table
                  className="user-table"
                  aria-label="Listado de fichas de estudiantes"
                >
                  <thead>
                    <tr>
                      <th scope="col">Código</th>
                      <th scope="col">Estudiante</th>
                      <th scope="col">Documento</th>
                      <th scope="col">Estado</th>
                      <th scope="col">Acción</th>
                    </tr>
                  </thead>
                  <tbody>
                    {list.data.content.map((student) => (
                      <tr key={student.id}>
                        <td data-label="Código">{student.codigoAlumno}</td>
                        <td data-label="Estudiante">
                          <PersonCell
                            name={studentName(student)}
                            detail={
                              student.persona.correo || "Sin correo de contacto"
                            }
                          />
                        </td>
                        <td data-label="Documento">
                          {student.persona.tipoDocumento &&
                          student.persona.numeroDocumento ? (
                            student.persona.numeroDocumento
                          ) : (
                            <span className={styles.pending}>
                              Documento pendiente
                            </span>
                          )}
                        </td>
                        <td data-label="Estado">
                          <StatusPill
                            state={student.activo ? "ACTIVO" : "INACTIVO"}
                            label={student.activo ? "Habilitada" : "Inactiva"}
                            tone={student.activo ? "success" : "neutral"}
                          />
                        </td>
                        <td data-label="Acción">
                          <button
                            type="button"
                            disabled={opening}
                            onClick={() => open(student.id)}
                            aria-label={`Ver ficha de ${studentName(student)}`}
                          >
                            Ver ficha
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pager
                data={list.data}
                loading={list.loading || opening}
                setPage={list.setPage}
              />
            </>
          ) : (
            <p className="empty">
              {query
                ? "No se encontraron estudiantes para esta búsqueda."
                : "Todavía no hay fichas de estudiantes registradas."}
            </p>
          )}
        </>
      )}
      {importOpen && (
        <StudentImportDialog
          onClose={() => setImportOpen(false)}
          onImported={list.refresh}
        />
      )}
    </section>
  );
}

"use client";
import { useEffect, useRef, useState } from "react";
import { readSession } from "@/core/auth/session-storage";
import { Feedback, Field, Pager } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { getStudent } from "./services/student-service";
import { studentErrorMessage } from "./student-errors";
import { StudentForm } from "./student-form";
import { StudentDetail } from "./student-detail";
import { studentName } from "./student-rules";
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
  return (
    <section aria-label="Fichas de estudiantes">
      <div className="management-heading">
        <div>
          <p className="eyebrow">MATRÍCULA</p>
          <h2>Fichas de estudiantes</h2>
          <p>Identidad institucional y datos de contacto del estudiante.</p>
        </div>
        {!detail && !formMode && (
          <button
            className="primary-action"
            disabled={opening}
            onClick={() => {
              setError("");
              setSuccess("");
              setFormMode(true);
            }}
          >
            Registrar estudiante
          </button>
        )}
      </div>
      <Feedback error={error} success={success} />
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
          <form
            className="toolbar"
            onSubmit={(e) => {
              e.preventDefault();
              setError("");
              setSuccess("");
              setQuery(
                String(
                  new FormData(e.currentTarget).get("search") || "",
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
                    {list.data.content.map((s) => (
                      <tr key={s.id}>
                        <td data-label="Código">{s.codigoAlumno}</td>
                        <td data-label="Estudiante">
                          <strong>{studentName(s)}</strong>
                          <small>
                            {s.persona.correo || "Sin correo de contacto"}
                          </small>
                        </td>
                        <td data-label="Documento">
                          {s.persona.tipoDocumento &&
                          s.persona.numeroDocumento ? (
                            s.persona.numeroDocumento
                          ) : (
                            <span className={styles.pending}>
                              Documento pendiente
                            </span>
                          )}
                        </td>
                        <td data-label="Estado">
                          <span
                            className={`${styles.badge} ${s.activo ? "" : styles.inactive}`}
                          >
                            {s.activo ? "Habilitada" : "Inactiva"}
                          </span>
                        </td>
                        <td data-label="Acción">
                          <button
                            type="button"
                            disabled={opening}
                            onClick={() => open(s.id)}
                            aria-label={`Ver ficha de ${studentName(s)}`}
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
    </section>
  );
}

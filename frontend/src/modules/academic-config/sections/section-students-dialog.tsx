"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { message } from "@/core/api/client";
import { getStudent, StudentDetail, type Student } from "@/modules/students";
import { usePaged } from "@/shared/hooks/use-paged";
import { Dialog, Feedback, Loading, Pager, PersonCell, StatusPill } from "@/shared/ui";
import { vacancies } from "../lib/section-metrics";
import type { Section } from "../types";

type SectionStudent = {
  id: number;
  alumnoId: number;
  codigoAlumno: string;
  alumno: string;
  numeroDocumento: string | null;
  estado: string;
};

/** Alumnos con matrícula activa o finalizada en la sección, con acceso a su ficha. */
export function SectionStudentsDialog({ section, editable, onClose, onChanged }: {
  section: Section;
  editable: boolean;
  onClose: () => void;
  /** Se llama al volver de una ficha, por si el estudiante cambió de sección. */
  onChanged: () => Promise<void>;
}) {
  const router = useRouter();
  const list = usePaged<SectionStudent>(
    "/api/matriculas",
    "",
    `seccionId=${section.id}&vigentes=true&sort=alumno.persona.apellidoPaterno`,
    10,
  );
  const [student, setStudent] = useState<Student | null>(null);
  const [opening, setOpening] = useState(false);
  const [error, setError] = useState("");
  const full = vacancies(section) <= 0;

  async function open(id: number) {
    setError("");
    setOpening(true);
    try {
      setStudent(await getStudent(id));
    } catch (cause) {
      setError(message(cause));
    } finally {
      setOpening(false);
    }
  }

  async function backToList() {
    setStudent(null);
    list.refresh();
    try {
      await onChanged();
    } catch (cause) {
      setError(message(cause));
    }
  }

  // La ficha reemplaza a la lista: nunca hay dos ventanas apiladas.
  if (student) return <StudentDetail student={student} onClose={backToList} />;
  return (
    <Dialog
      title={`Alumnos de ${section.grado}° Grado, Sección ${section.nombre}`}
      size="lg"
      onClose={onClose}
    >
      <Feedback error={error || list.error} />
      {opening && <p role="status">Consultando ficha…</p>}
      {list.loading ? (
        <Loading>Cargando alumnos…</Loading>
      ) : list.data?.content.length ? (
        <div className="user-table-wrap">
          <table className="user-table" aria-label="Alumnos de la sección">
            <thead>
              <tr>
                <th scope="col">Código</th>
                <th scope="col">Estudiante</th>
                <th scope="col">Documento</th>
                <th scope="col">Matrícula</th>
                <th scope="col">Acción</th>
              </tr>
            </thead>
            <tbody>
              {list.data.content.map((item) => (
                <tr key={item.id}>
                  <td data-label="Código">{item.codigoAlumno}</td>
                  <td data-label="Estudiante"><PersonCell name={item.alumno} /></td>
                  <td data-label="Documento">{item.numeroDocumento || "—"}</td>
                  <td data-label="Matrícula"><StatusPill state={item.estado} /></td>
                  <td data-label="Acción">
                    <button
                      type="button"
                      disabled={opening}
                      aria-label={`Ver ficha de ${item.alumno}`}
                      onClick={() => open(item.alumnoId)}
                    >
                      Ver ficha
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        !list.error && <p className="empty">Esta sección todavía no tiene alumnos.</p>
      )}
      <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
      {editable && section.activo && full && (
        <p className="hint">La sección no tiene vacantes para agregar alumnos.</p>
      )}
      <div className="form-actions">
        <button type="button" onClick={onClose}>Salir</button>
        {editable && section.activo && (
          <button
            type="button"
            className="primary-action"
            disabled={full}
            onClick={() => router.push(`/matricula/fichas/estudiantes?seccion=${section.id}`)}
          >
            Agregar alumno
          </button>
        )}
      </div>
    </Dialog>
  );
}

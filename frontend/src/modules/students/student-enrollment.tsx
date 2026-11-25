"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback, RecordSection } from "@/shared/ui";
import { getStudentEnrollments, type SectionEnrollment } from "@/modules/section-change";

/** Matrículas vigentes del estudiante, con el acceso directo para cambiar de sección. */
export function StudentEnrollment({ studentId, onChangeSection }: {
  studentId: number;
  onChangeSection: (enrollment: SectionEnrollment) => void;
}) {
  const [enrollments, setEnrollments] = useState<SectionEnrollment[] | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    getStudentEnrollments(studentId).then(
      (result) => { if (active) setEnrollments(result.content.filter((item) => item.estado === "ACTIVA")); },
      (cause) => { if (active) setError(message(cause)); },
    );
    return () => { active = false; };
  }, [studentId]);

  return (
    <>
      <RecordSection title="Matrícula vigente" />
      <Feedback error={error} />
      {!enrollments ? (
        !error && <p role="status">Consultando la matrícula…</p>
      ) : enrollments.length ? (
        <ul className="choice-list">
          {enrollments.map((enrollment) => (
            <li key={enrollment.id}>
              <span>
                {enrollment.grado}° {enrollment.seccion}
                <small>Año {enrollment.anioAcademico}</small>
              </span>
              <button type="button" onClick={() => onChangeSection(enrollment)}>
                Cambiar sección
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="empty">No tiene una matrícula activa.</p>
      )}
    </>
  );
}

"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback, RecordSection } from "@/shared/ui";
import { getStudentEnrollments, type SectionEnrollment } from "@/modules/section-change";
import type { DestinationSection } from "./destination-section";

/** Matrículas vigentes del estudiante, con el acceso directo para cambiar de sección. */
export function StudentEnrollment({ studentId, destination, onChangeSection, onEnroll }: {
  studentId: number;
  destination?: DestinationSection | null;
  /** Sin esta acción (estudiante inactivo) no se ofrece matricular. */
  onEnroll?: () => void;
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
              <EnrollmentAction
                enrollment={enrollment}
                destination={destination}
                onChangeSection={onChangeSection}
              />
            </li>
          ))}
        </ul>
      ) : (
        <p className="empty">No tiene una matrícula activa.</p>
      )}
      {onEnroll && enrollments && needsEnrollment(enrollments, destination) && (
        <div className="row-actions">
          <button type="button" className="primary-action" onClick={onEnroll}>
            {destination ? `Matricular en la sección ${destination.nombre}` : "Matricular"}
          </button>
        </div>
      )}
    </>
  );
}

/** Sin matrícula activa, o sin ella en el año de la sección a la que se quiere agregar. */
const needsEnrollment = (enrollments: SectionEnrollment[], destination?: DestinationSection | null) =>
  destination
    ? !enrollments.some((item) => item.anioAcademico === destination.anioAcademico)
    : !enrollments.length;

function EnrollmentAction({ enrollment, destination, onChangeSection }: {
  enrollment: SectionEnrollment;
  destination?: DestinationSection | null;
  onChangeSection: (enrollment: SectionEnrollment) => void;
}) {
  const sameYear = destination?.anioAcademico === enrollment.anioAcademico;
  if (destination && sameYear && enrollment.seccionId === destination.id)
    return <small>Ya está en la sección {destination.nombre}</small>;
  if (destination && sameYear && enrollment.grado !== destination.grado)
    return <small>La sección {destination.nombre} es de {destination.grado}°: no se puede cambiar de grado</small>;
  return (
    <button type="button" onClick={() => onChangeSection(enrollment)}>
      {destination && sameYear ? `Cambiar a la sección ${destination.nombre}` : "Cambiar sección"}
    </button>
  );
}

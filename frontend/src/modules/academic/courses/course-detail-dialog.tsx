"use client";
import { Dialog } from "@/shared/ui";
import type { Course, CourseCompetency } from "../types";
import styles from "../academic.module.css";
import { courseContextLabel } from "./course-context";
/** Ventana de solo lectura con los datos del curso y sus competencias. */
export function CourseDetailDialog({
  course,
  competencies,
  onClose,
  onEdit,
}: {
  course: Course;
  /** null si no se pudieron consultar las asociaciones. */
  competencies: CourseCompetency[] | null;
  onClose: () => void;
  /** Sin permiso de escritura no se ofrece la edición. */
  onEdit?: () => void;
}) {
  return (
    <Dialog
      title={course.nombre}
      description={`Curso · ${course.codigo}`}
      onClose={onClose}
    >
      <dl className="detail-grid">
        <div>
          <dt>Código</dt>
          <dd>{course.codigo}</dd>
        </div>
        <div>
          <dt>Horas semanales</dt>
          <dd>{course.horasSemanales}</dd>
        </div>
        <div>
          <dt>Año y grado</dt>
          <dd>{courseContextLabel(course)}</dd>
        </div>
        <div>
          <dt>Estado</dt>
          <dd>
            <span
              className={`status ${course.activo ? "status-active" : "status-inactive"}`}
            >
              {course.activo ? "Activo" : "Inactivo"}
            </span>
          </dd>
        </div>
        <div className="full-field">
          <dt>Descripción</dt>
          <dd>{course.descripcion || "Sin descripción"}</dd>
        </div>
      </dl>
      <h4>Competencias asociadas</h4>
      {competencies === null ? (
        <p className="empty">No se pudieron consultar las competencias.</p>
      ) : competencies.length ? (
        <ol className={styles.competencyList}>
          {competencies.map((item) => (
            <li key={item.id}>
              <strong>{item.competenciaNombre}</strong>
              <small>{item.competenciaCodigo}</small>
            </li>
          ))}
        </ol>
      ) : (
        <p className="empty">Este curso aún no tiene competencias asociadas.</p>
      )}
      <div className="form-actions">
        <button type="button" onClick={onClose}>
          Cerrar
        </button>
        {onEdit && (
          <button type="button" className="primary-action" onClick={onEdit}>
            Editar curso
          </button>
        )}
      </div>
    </Dialog>
  );
}

"use client";
import { Dialog } from "@/shared/ui";
import type { Course, CourseCompetency } from "../types";
import styles from "../academic.module.css";
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
    <Dialog title="Detalle del curso" onClose={onClose}>
      <h3 className={styles.detailTitle}>{course.nombre}</h3>
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
          <dt>Estado</dt>
          <dd>{course.activo ? "Activo" : "Inactivo"}</dd>
        </div>
        <div>
          <dt>Descripción</dt>
          <dd>{course.descripcion || "Sin descripción"}</dd>
        </div>
      </dl>
      <p className={styles.sectionTitle}>Competencias asociadas</p>
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

"use client";
import { Dialog } from "@/shared/ui";
import type { Competency, CourseCompetency } from "../types";
import styles from "../academic.module.css";
/** Ventana de solo lectura con la competencia y los cursos que la trabajan. */
export function CompetencyDetailDialog({
  competency,
  courses,
  onClose,
  onEdit,
}: {
  competency: Competency;
  /** null si no se pudieron consultar las asociaciones. */
  courses: CourseCompetency[] | null;
  onClose: () => void;
  /** Sin permiso de escritura no se ofrece la edición. */
  onEdit?: () => void;
}) {
  return (
    <Dialog
      title={competency.nombre}
      description={`Competencia · ${competency.codigo}`}
      onClose={onClose}
    >
      <dl className="detail-grid">
        <div>
          <dt>Código</dt>
          <dd>{competency.codigo}</dd>
        </div>
        <div>
          <dt>Estado</dt>
          <dd>
            <span
              className={`status ${competency.activo ? "status-active" : "status-inactive"}`}
            >
              {competency.activo ? "Activa" : "Inactiva"}
            </span>
          </dd>
        </div>
        <div className="full-field">
          <dt>Descripción</dt>
          <dd>{competency.descripcion || "Sin descripción"}</dd>
        </div>
      </dl>
      <h4>Cursos que la trabajan</h4>
      {courses === null ? (
        <p className="empty">No se pudieron consultar los cursos asociados.</p>
      ) : courses.length ? (
        <ol className={styles.competencyList}>
          {courses.map((item) => (
            <li key={item.id}>
              <strong>{item.cursoNombre}</strong>
              <small>
                {item.cursoCodigo} · Orden {item.orden} en el curso
              </small>
            </li>
          ))}
        </ol>
      ) : (
        <p className="empty">Todavía no está asociada a ningún curso.</p>
      )}
      <div className="form-actions">
        <button type="button" onClick={onClose}>
          Cerrar
        </button>
        {onEdit && (
          <button type="button" className="primary-action" onClick={onEdit}>
            Editar competencia
          </button>
        )}
      </div>
    </Dialog>
  );
}

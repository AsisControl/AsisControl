import type { ReactNode } from "react";
import type { PlanCourse } from "./types";
import styles from "./study-plans.module.css";

export type DraftCourse = Omit<PlanCourse, "horasSemanales"> & { horasSemanales: number | "" };

export function PlanCourseList<T extends DraftCourse>({ courses, label, action, actionLabel = "Acción", onEdit }: {
  courses: T[];
  label: string;
  action?: (course: T) => ReactNode;
  actionLabel?: string;
  onEdit?: (course: DraftCourse) => void;
}) {
  return (
    <div className={styles.courseList}>
      <table aria-label={label}>
        <thead><tr>
          <th scope="col">Curso</th>
          <th scope="col">Horas por semana</th>
          {action && <th scope="col">{actionLabel}</th>}
        </tr></thead>
        <tbody>{courses.map(course => (
          <tr key={course.id}>
            <th scope="row">
              {onEdit ? (
                <input className={styles.courseInput} aria-label={`Nombre en el plan de ${course.codigo}`}
                  required maxLength={120} value={course.nombre}
                  onChange={event => onEdit({ ...course, nombre: event.target.value })} />
              ) : <strong>{course.nombre}</strong>}
              <small>{course.codigo}</small>
            </th>
            <td>{onEdit ? (
              <input className={styles.hoursInput} aria-label={`Horas semanales de ${course.codigo}`}
                type="number" inputMode="numeric" min={1} max={40} step={1} required value={course.horasSemanales}
                onChange={event => onEdit({ ...course, horasSemanales: event.target.value === "" ? "" : event.target.valueAsNumber })} />
            ) : course.horasSemanales}</td>
            {action && <td>{action(course)}</td>}
          </tr>
        ))}</tbody>
      </table>
    </div>
  );
}

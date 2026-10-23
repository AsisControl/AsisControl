"use client";
import { Feedback, Pager } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import type { Student } from "@/modules/students";
import { initialsOf } from "../users/user-format";
import { SearchInput, StepCard, useDebouncedSearch } from "./step-parts";
import styles from "./new-user.module.css";
/** Paso 4 (solo apoderados): estudiantes que podrá seguir. Id → nombre. */
export function StudentsStep({
  selected,
  onSelectedChange,
}: {
  selected: Map<number, string>;
  onSelectedChange: (selected: Map<number, string>) => void;
}) {
  const search = useDebouncedSearch();
  const students = usePaged<Student>("/api/alumnos", search.query);
  function toggle(student: Student, checked: boolean) {
    const next = new Map(selected);
    if (checked) next.set(student.id, student.persona.nombreCompleto);
    else next.delete(student.id);
    onSelectedChange(next);
  }
  function remove(id: number) {
    const next = new Map(selected);
    next.delete(id);
    onSelectedChange(next);
  }
  return (
    <StepCard
      number={4}
      title="Estudiantes asociados"
      description="Puede asociar varios estudiantes. Sin asociaciones activas, el apoderado verá una experiencia vacía."
      done={selected.size > 0}
    >
      {selected.size > 0 && (
        <div className={styles.chips} aria-label="Estudiantes seleccionados">
          {[...selected].map(([id, name]) => (
            <span key={id} className={styles.chip}>
              {name}
              <button
                type="button"
                aria-label={`Quitar a ${name}`}
                onClick={() => remove(id)}
              >
                ×
              </button>
            </span>
          ))}
        </div>
      )}
      <div className={styles.subsection}>
        <SearchInput
          label="Buscar estudiante"
          placeholder="Buscar estudiante por nombre o código…"
          value={search.text}
          onChange={(value) => {
            search.setText(value);
            students.setPage(0);
          }}
        />
      </div>
      <Feedback error={students.error} />
      {students.loading ? (
        <p role="status">Cargando estudiantes…</p>
      ) : students.data?.content.length ? (
        <div className={styles.choiceList}>
          {students.data.content.map((student) => (
            <label key={student.id} className={styles.choice}>
              <input
                type="checkbox"
                checked={selected.has(student.id)}
                onChange={(e) => toggle(student, e.target.checked)}
              />
              <span className={styles.initials} aria-hidden="true">
                {initialsOf(student.persona.nombreCompleto)}
              </span>
              <span className={styles.choiceText}>
                <strong>{student.persona.nombreCompleto}</strong>
                <small>Código: {student.codigoAlumno}</small>
              </span>
            </label>
          ))}
        </div>
      ) : (
        !students.error && (
          <p className="empty">No hay estudiantes para esta búsqueda.</p>
        )
      )}
      <Pager
        data={students.data}
        loading={students.loading}
        setPage={students.setPage}
      />
    </StepCard>
  );
}

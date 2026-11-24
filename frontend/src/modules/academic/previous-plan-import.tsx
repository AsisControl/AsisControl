"use client";

import { useEffect, useState } from "react";
import { api, message } from "@/core/api/client";
import { countLabel } from "@/shared/lib/formatters";
import { Feedback, Loading } from "@/shared/ui";
import { PlanCourseList, type PlanCourse } from "./plan-course-list";
import styles from "./study-plans.module.css";

type PreviousPlan = { anioOrigen: number; cursos: PlanCourse[] };

export function PreviousPlanImport({ id, path, grade, selected, onAdd }: {
  id: string;
  path: string;
  grade: number;
  selected: Set<number>;
  onAdd: (courses: PlanCourse[]) => void;
}) {
  const [preview, setPreview] = useState<PreviousPlan | null>(null);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    api<PreviousPlan>(`${path}/anterior`)
      .then(data => { if (active) setPreview(data); })
      .catch(cause => { if (active) setError(message(cause)); });
    return () => { active = false; };
  }, [path, retry]);

  const missing = preview?.cursos.filter(course => !selected.has(course.id)) ?? [];
  const hasArchived = missing.some(course => course.competencias?.some(item => !item.activa));
  return (
    <section className={styles.catalog} id={id} aria-label="Vista previa del plan anterior">
      <h5>{preview ? `Plan ${preview.anioOrigen} · ${grade}.° grado` : "Plan del año anterior"}</h5>
      <Feedback error={error} />
      {error && <button type="button" className="secondary-action" onClick={() => {
        setError("");
        setRetry(value => value + 1);
      }}>Volver a consultar</button>}
      {!preview && !error && <Loading skeleton="table" columns={3} rows={3}>Cargando el plan anterior…</Loading>}
      {preview && (preview.cursos.length === 0 ? (
        <p className={styles.empty}>En {preview.anioOrigen} este grado no tiene un plan guardado con cursos.</p>
      ) : (
        <>
          <p className={styles.note}>Los cursos que ya tienes conservan sus datos. Revisa la selección antes de guardar el plan.</p>
          {hasArchived && <p className={styles.note}>Los cursos se agregarán con sus competencias activas.</p>}
          <PlanCourseList courses={preview.cursos} label="Cursos del plan anterior" actionLabel="Estado"
            action={course => <span className={styles.hint}>
              {selected.has(course.id) ? "En la selección" : "Por agregar"}
              {course.competencias && <small>{countLabel(course.competencias.length, "competencia", "competencias")}</small>}
            </span>} />
          {missing.length > 0 ? <div className={styles.saveActions}>
            <button type="button" className="primary-action" onClick={() => onAdd(missing.map(course => ({
              ...course, competencias: course.competencias?.filter(item => item.activa),
            })))}>
              Agregar {countLabel(missing.length, "curso", "cursos")}
            </button>
          </div> : <p className={styles.hint} role="status">Todos los cursos del plan anterior ya están en la selección.</p>}
        </>
      ))}
    </section>
  );
}

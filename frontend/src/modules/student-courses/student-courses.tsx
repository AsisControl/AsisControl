// inscripcion cursos
"use client";
import Link from "next/link";
import { useEffect, useState, type ReactNode } from "react";
import { message } from "@/core/api/client";
import { countLabel } from "@/shared/lib/formatters";
import { Feedback, Loading, PageHeader, Skeleton, StatusPill } from "@/shared/ui";
import { listarMisCursos } from "./services/mis-cursos-service";
import styles from "./student-courses.module.css";
import type { CursoInscrito, MisCursos } from "./types";

const icono = (ruta: ReactNode) => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {ruta}
  </svg>
);
const LIBRO = (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 7v14M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3 3 3 0 0 0-3-3Z" />
  </svg>
);
const RELOJ = icono(<><circle cx="12" cy="12" r="10" /><path d="M12 6v6l4 2" /></>);
const AULA = icono(<><path d="M3 21h18M5 21V7l7-4 7 4v14" /><path d="M9 21v-6h6v6" /></>);

/** Misma tarjeta que la del docente: icono, curso, docente en azul, horario y aula, y accesos. */
function CursoCard({ curso }: { curso: CursoInscrito }) {
  const bloques = curso.horario?.split(" · ") ?? [];
  return (
    <article className={styles.card}>
      <div className={styles.cardTop}>
        <span className={styles.icon}>{LIBRO}</span>
        {!curso.docente && <StatusPill state="PENDIENTE" label="Por asignar" tone="warning" />}
      </div>
      <h3>{curso.curso}</h3>
      <p className={curso.docente ? styles.docente : `${styles.docente} ${styles.pendiente}`}>
        {curso.docente ?? "Docente por asignar"}
      </p>
      <ul className={styles.detalles}>
        {bloques.length ? (
          bloques.map((bloque) => (
            <li key={bloque}>
              {RELOJ}
              {bloque}
            </li>
          ))
        ) : (
          <li className={styles.pendiente}>
            {RELOJ}
            Horario por definir
          </li>
        )}
        {curso.aula && (
          <li>
            {AULA}
            {curso.aula}
          </li>
        )}
      </ul>
      <div className={styles.acciones}>
        <Link className={`primary-action ${styles.enlace}`} href="/tareas">
          Ver tareas
        </Link>
        <Link className={styles.secundario} href="/asistencia">
          Asistencia
        </Link>
      </div>
    </article>
  );
}

/** Tarjetas de espera con las mismas clases que CursoCard, para que no salte el diseño al cargar. */
function CursosSkeleton() {
  return (
    <div className={styles.grilla}>
      {[0, 1, 2].map((tarjeta) => (
        <article key={tarjeta} className={styles.card} aria-hidden="true">
          <div className={styles.cardTop}>
            <Skeleton width={40} height={40} radius={8} />
          </div>
          <Skeleton width="58%" height={18} />
          <Skeleton width="40%" height={14} className={styles.esqueletoDocente} />
          <div className={styles.detalles}>
            <Skeleton width="62%" height={12} />
            <Skeleton width="38%" height={12} />
          </div>
          <div className={styles.acciones}>
            <Skeleton height={38} radius={8} />
            <Skeleton height={38} radius={8} />
          </div>
        </article>
      ))}
    </div>
  );
}

/** Cursos en los que el estudiante queda inscrito al tener su matrícula activa. */
export function StudentCourses() {
  const [datos, setDatos] = useState<MisCursos | null>(null);
  const [error, setError] = useState("");
  const [intento, setIntento] = useState(0);
  useEffect(() => {
    let activo = true;
    listarMisCursos()
      .then((respuesta) => {
        if (activo) setDatos(respuesta);
      })
      .catch((e) => {
        if (activo) setError(message(e));
      });
    return () => {
      activo = false;
    };
  }, [intento]);
  const reintentar = () => {
    setError("");
    setDatos(null);
    setIntento((valor) => valor + 1);
  };
  return (
    <section>
      <div className={styles.encabezado}>
        <PageHeader title="Mis cursos" description="Cursos de tu matrícula de este año" />
        {datos?.seccion && (
          <div className={styles.resumen}>
            <strong>{datos.seccion}</strong>
            <span>
              Año {datos.anioAcademico} · {countLabel(datos.cursos.length, "curso", "cursos")}
            </span>
          </div>
        )}
      </div>
      {error && (
        <div className={styles.aviso}>
          <Feedback error={error} />
          <button type="button" onClick={reintentar}>
            Reintentar
          </button>
        </div>
      )}
      {!error && !datos && <Loading skeleton={<CursosSkeleton />}>Cargando tus cursos…</Loading>}
      {datos && !datos.seccion && (
        <p className={styles.vacio}>
          Aún no tienes una matrícula activa. Cuando se finalice, aquí aparecerán tus cursos.
        </p>
      )}
      {datos?.seccion && datos.cursos.length === 0 && (
        <p className={styles.vacio}>Tu grado todavía no tiene cursos en su plan de estudios.</p>
      )}
      {datos && datos.cursos.length > 0 && (
        <div className={styles.grilla}>
          {datos.cursos.map((curso) => (
            <CursoCard key={curso.cursoId} curso={curso} />
          ))}
        </div>
      )}
    </section>
  );
}

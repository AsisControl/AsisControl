"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { countLabel } from "@/shared/lib/formatters";
import { Feedback, PageHeader, StatusPill, TabNav, type TabItem } from "@/shared/ui";
import { listarMisCursos } from "./services/curso-service";
import styles from "./teacher-courses.module.css";
import type { CursoAsignado, PeriodoCurso } from "./types";

const FILTROS: TabItem[] = [
  { id: "VIGENTES", label: "Vigentes" },
  { id: "PASADOS", label: "Pasados" },
  { id: "TODOS", label: "Todos" },
];
const SIN_CURSOS: Record<PeriodoCurso, string> = {
  VIGENTES: "No tienes cursos vigentes.",
  PASADOS: "No tienes cursos de años anteriores.",
  TODOS: "No tienes cursos asignados.",
};

function CursoCard({ curso }: { curso: CursoAsignado }) {
  const detalles = [
    countLabel(curso.estudiantes, "estudiante", "estudiantes"),
    curso.horario,
    curso.aula,
    curso.periodo ?? `Año ${curso.anioAcademico}`,
  ].filter(Boolean);
  return (
    <article className={styles.card}>
      <div className={styles.cardTop}>
        <span className={styles.icon} aria-hidden="true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 7v14M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3 3 3 0 0 0-3-3Z" />
          </svg>
        </span>
        <StatusPill
          state={curso.vigente ? "ACTIVO" : "CERRADO"}
          label={curso.vigente ? "Activo" : "Cerrado"}
          tone={curso.vigente ? "success" : "neutral"}
        />
      </div>
      <h3>{curso.curso}</h3>
      <p className={styles.seccion}>{curso.seccion}</p>
      <ul className={styles.detalles}>
        {detalles.map((detalle) => (
          <li key={detalle}>{detalle}</li>
        ))}
      </ul>
      <div className={styles.acciones}>
        <Link className={`primary-action ${styles.enlace}`} href="/evaluaciones">
          Ver evaluaciones
        </Link>
        <Link className={styles.secundario} href={`/mis-cursos/${curso.id}/asistencia`}>
          Asistencia
        </Link>
      </div>
    </article>
  );
}

/** Cursos del docente con su filtro; cada tarjeta lleva a sus evaluaciones y a su asistencia. */
export function TeacherCourses() {
  const [periodo, setPeriodo] = useState<PeriodoCurso>("VIGENTES");
  const [resultado, setResultado] = useState<{ periodo: PeriodoCurso; cursos: CursoAsignado[] } | null>(null);
  const [error, setError] = useState("");
  const [intento, setIntento] = useState(0);
  useEffect(() => {
    let activo = true;
    listarMisCursos(periodo)
      .then((cursos) => {
        if (activo) setResultado({ periodo, cursos });
      })
      .catch((e) => {
        if (activo) setError(message(e));
      });
    return () => {
      activo = false;
    };
  }, [periodo, intento]);
  const cursos = resultado?.periodo === periodo ? resultado.cursos : null;
  const seleccionar = (id: string) => {
    setError("");
    setPeriodo(id as PeriodoCurso);
  };
  const reintentar = () => {
    setError("");
    setIntento((valor) => valor + 1);
  };
  return (
    <section>
      <div className={styles.encabezado}>
        <PageHeader title="Mis cursos" description="Cursos y secciones asignados a ti" />
        <div className={styles.filtro}>
          <TabNav label="Filtrar cursos" tabs={FILTROS} current={periodo} onSelect={seleccionar} />
        </div>
      </div>
      {error && (
        <div className={styles.aviso}>
          <Feedback error={error} />
          <button type="button" onClick={reintentar}>
            Reintentar
          </button>
        </div>
      )}
      {!error && !cursos && <p role="status">Cargando cursos…</p>}
      {!error && cursos?.length === 0 && <p className={styles.vacio}>{SIN_CURSOS[periodo]}</p>}
      {!error && !!cursos?.length && (
        <div className={styles.grilla}>
          {cursos.map((curso) => (
            <CursoCard key={curso.id} curso={curso} />
          ))}
        </div>
      )}
    </section>
  );
}

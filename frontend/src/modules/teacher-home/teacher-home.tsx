"use client";
import Link from "next/link";
import { PageHeader, StatusPill } from "@/shared/ui";
import styles from "./teacher-home.module.css";

const INDICADORES = [
  { valor: 3, etiqueta: "Clases del día", color: "#1e3a8a" },
  { valor: 1, etiqueta: "Asistencias pendientes", color: "#d97706" },
  { valor: 27, etiqueta: "Evaluaciones y tareas por revisar", color: "#d97706" },
  { valor: 8, etiqueta: "Bloques en mi horario", color: "#0284c7" },
];
const CLASES = [
  { curso: "Matemática", detalle: "3° Grado A · Aula 201", hora: "07:30 – 09:30", estado: "En curso", actual: true },
  { curso: "Matemática", detalle: "4° Grado B · Aula 201", hora: "09:30 – 11:30", estado: "Próxima", actual: false },
  { curso: "Matemática", detalle: "5° Grado C · Aula 201", hora: "13:30 – 15:30", estado: "Próxima", actual: false },
];
const PENDIENTES = [
  { titulo: "Evaluar Período III — Matemática 3°A", tipo: "Evaluación", prioridad: "Alta" },
  { titulo: "Evaluar Período III — Matemática 4°B", tipo: "Evaluación", prioridad: "Alta" },
  { titulo: "Revisar 12 entregas — Ejercicios Cap. 4", tipo: "Tarea", prioridad: "Media" },
  { titulo: "Publicar progreso — 3°A", tipo: "Progreso", prioridad: "Media" },
];
const ACCIONES = [
  { etiqueta: "Tomar asistencia", href: "/mis-cursos" },
  { etiqueta: "Registrar evaluación", href: "/mis-cursos" },
  { etiqueta: "Crear tarea", href: "/tareas" },
  { etiqueta: "Ver horario", href: "/horario" },
];

/** Inicio del docente con datos de ejemplo: solo el nombre y la fecha son reales. */
export function TeacherHome({ fullName }: { fullName: string }) {
  const hoy = new Date().toLocaleDateString("es-PE", { timeZone: "America/Lima", dateStyle: "full" });
  return (
    <section>
      <PageHeader
        title={`Bienvenido/a, ${fullName}`}
        description={hoy}
        actions={
          <Link className={`primary-action ${styles.enlace}`} href="/tareas">
            Nueva tarea
          </Link>
        }
      />
      <p className={styles.ejemplo} role="note">
        Datos de ejemplo: esta pantalla todavía no muestra información real.
      </p>
      <div className={styles.indicadores}>
        {INDICADORES.map(({ valor, etiqueta, color }) => (
          <article className={styles.indicador} key={etiqueta}>
            <strong style={{ color }}>{valor}</strong>
            <span>{etiqueta}</span>
          </article>
        ))}
      </div>
      <div className={styles.columnas}>
        <article className={styles.tarjeta}>
          <h3>Clases de hoy</h3>
          {CLASES.map((clase) => (
            <div className={clase.actual ? styles.claseActual : styles.clase} key={clase.detalle}>
              <div className={styles.claseFila}>
                <div>
                  <strong>{clase.curso}</strong>
                  <p>{clase.detalle}</p>
                </div>
                <div className={styles.claseHora}>
                  <time>{clase.hora}</time>
                  <StatusPill state={clase.estado} label={clase.estado} tone={clase.actual ? "info" : "neutral"} />
                </div>
              </div>
              {clase.actual && (
                <div className={styles.claseAcciones}>
                  <Link className={`primary-action ${styles.enlace}`} href="/mis-cursos">
                    Tomar asistencia
                  </Link>
                  <Link className={styles.secundario} href="/mis-cursos">
                    Evaluar
                  </Link>
                </div>
              )}
            </div>
          ))}
        </article>
        <article className={styles.tarjeta}>
          <h3>Pendientes académicos</h3>
          <p className={styles.resumen}>{PENDIENTES.length} tareas</p>
          <ul className={styles.pendientes}>
            {PENDIENTES.map(({ titulo, tipo, prioridad }) => (
              <li key={titulo}>
                <div>
                  <span>{titulo}</span>
                  <small>{tipo}</small>
                </div>
                <StatusPill state={prioridad} label={prioridad} tone={prioridad === "Alta" ? "danger" : "warning"} />
              </li>
            ))}
          </ul>
        </article>
        <article className={styles.tarjeta}>
          <h3>Acciones rápidas</h3>
          <nav className={styles.accesos} aria-label="Acciones rápidas">
            {ACCIONES.map(({ etiqueta, href }) => (
              <Link href={href} key={etiqueta}>
                {etiqueta} <span aria-hidden="true">›</span>
              </Link>
            ))}
          </nav>
        </article>
      </div>
    </section>
  );
}

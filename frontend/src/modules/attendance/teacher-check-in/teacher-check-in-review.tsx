"use client";
import { Fragment, useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Loading } from "@/shared/ui";
import { ESTADO_LABEL, SESION_LABEL, VERIFICACION_LABEL, dateOf, hourOf, timeOf } from "./format";
import { clasesDelDia, listarMarcacionesDocentes } from "./services/asistencia-docente-service";
import type { AsistenciaDocente, ClaseDia } from "./types";
import styles from "./teacher-check-in.module.css";

type Props = { day: string; onDayChange: (day: string) => void; refreshKey: number };

/**
 * Marcaciones de docentes de un día, cruzadas con la asistencia de alumnos. Una marcación observada
 * indica que el docente escaneó el QR pero aún no registró la lista de su clase; al abrir la fila
 * se ven sus clases del día y el estado de cada una.
 */
export function TeacherCheckInReview({ day, onDayChange, refreshKey }: Props) {
  const [onlyObserved, setOnlyObserved] = useState(false);
  const [rows, setRows] = useState<AsistenciaDocente[] | null>(null);
  const [error, setError] = useState("");
  const [openId, setOpenId] = useState<number | null>(null);
  const [classes, setClasses] = useState<ClaseDia[] | null>(null);
  const [classesError, setClassesError] = useState("");

  useEffect(() => {
    let alive = true;
    listarMarcacionesDocentes({ fecha: day, verificacion: onlyObserved ? "OBSERVADA" : undefined })
      .then((list) => {
        if (!alive) return;
        setRows(list);
        setError("");
      })
      .catch((problem) => alive && setError(message(problem)));
    return () => {
      alive = false;
    };
  }, [day, onlyObserved, refreshKey]);

  function toggle(item: AsistenciaDocente) {
    if (openId === item.id) {
      setOpenId(null);
      return;
    }
    setOpenId(item.id);
    setClasses(null);
    setClassesError("");
    clasesDelDia(item.docenteId, item.fechaJornada)
      .then(setClasses)
      .catch((problem) => setClassesError(message(problem)));
  }

  return (
    <section className={`panel ${styles.card}`} aria-labelledby="revision-title">
      <div className={styles.reviewHeader}>
        <h2 id="revision-title" className={styles.cardTitle}>
          Marcaciones de docentes
        </h2>
        <label className={styles.inlineField}>
          <span>Día</span>
          <input
            type="date"
            value={day}
            onChange={(event) => {
              if (!event.target.value) return;
              setRows(null);
              setOpenId(null);
              onDayChange(event.target.value);
            }}
          />
        </label>
      </div>
      <div className={styles.filters} role="group" aria-label="Filtrar marcaciones">
        <button
          type="button"
          className={styles.filter}
          aria-pressed={!onlyObserved}
          onClick={() => {
            setRows(null);
            setOnlyObserved(false);
          }}
        >
          Todas
        </button>
        <button
          type="button"
          className={styles.filter}
          aria-pressed={onlyObserved}
          onClick={() => {
            setRows(null);
            setOnlyObserved(true);
          }}
        >
          Pendientes de confirmación
        </button>
      </div>
      {error && <p className={styles.error}>{error}</p>}
      {!error && rows === null && <Loading variant="block">Cargando marcaciones…</Loading>}
      {rows?.length === 0 && (
        <p className={styles.muted}>
          {onlyObserved ? "No hay marcaciones pendientes de confirmación." : "No hay marcaciones en este día."}
        </p>
      )}
      {rows && rows.length > 0 && (
        <table className={styles.table}>
          <thead>
            <tr>
              <th scope="col">Docente</th>
              <th scope="col">Hora</th>
              <th scope="col">Estado</th>
              <th scope="col">Clase</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((item) => (
              <Fragment key={item.id}>
                <tr>
                  <td>
                    <button
                      type="button"
                      className={styles.rowButton}
                      aria-expanded={openId === item.id}
                      onClick={() => toggle(item)}
                    >
                      {item.docenteNombre}
                    </button>
                  </td>
                  <td>{timeOf(item.fechaHoraMarcada)}</td>
                  <td>{ESTADO_LABEL[item.estado] ?? item.estado}</td>
                  <td>
                    <span className={item.verificacion === "OBSERVADA" ? styles.pending : styles.confirmed}>
                      {VERIFICACION_LABEL[item.verificacion] ?? "—"}
                    </span>
                  </td>
                </tr>
                {openId === item.id && (
                  <tr>
                    <td colSpan={4} className={styles.detail}>
                      {classesError && <p className={styles.error}>{classesError}</p>}
                      {!classesError && classes === null && <p className={styles.muted}>Cargando clases…</p>}
                      {classes?.length === 0 && <p className={styles.muted}>No tiene clases programadas este día.</p>}
                      {classes && classes.length > 0 && (
                        <ul className={styles.classList}>
                          {classes.map((clase) => (
                            <li key={clase.sesionId}>
                              <span>
                                {clase.curso} · {clase.seccion}
                              </span>
                              <span className={styles.muted}>
                                {hourOf(clase.horaInicio)} a {hourOf(clase.horaFin)}
                              </span>
                              <span className={clase.estado === "TOMADA" ? styles.confirmed : styles.pending}>
                                Lista de alumnos: {SESION_LABEL[clase.estado].toLowerCase()}
                              </span>
                            </li>
                          ))}
                        </ul>
                      )}
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

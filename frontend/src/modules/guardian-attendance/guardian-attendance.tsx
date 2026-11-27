// asistencia alumnos
"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback, Loading, PageHeader, initialsOf } from "@/shared/ui";
import { AttendanceRecord } from "./attendance-record";
import { JustifyDialog } from "./justify-dialog";
import { listarAsistencias, listarEstudiantes } from "./services/asistencia-apoderado-service";
import styles from "./guardian-attendance.module.css";
import type { AsistenciaEstudiante, EstudianteFamilia } from "./types";

/** Asistencia de cada estudiante a cargo; el apoderado es quien solicita las justificaciones. */
export function GuardianAttendance() {
  const [estudiantes, setEstudiantes] = useState<EstudianteFamilia[] | null>(null);
  const [elegido, setElegido] = useState<number | null>(null);
  const [asistencias, setAsistencias] = useState<AsistenciaEstudiante[] | null>(null);
  const [recarga, setRecarga] = useState(0);
  const [justificando, setJustificando] = useState<AsistenciaEstudiante | null>(null);
  const [error, setError] = useState("");
  const [exito, setExito] = useState("");
  // Con un solo estudiante no hay nada que elegir; con varios no se carga nada hasta que elija.
  const alumnoId = elegido ?? (estudiantes?.length === 1 ? estudiantes[0].id : null);
  const estudiante = estudiantes?.find((item) => item.id === alumnoId);
  useEffect(() => {
    let activo = true;
    listarEstudiantes()
      .then((lista) => {
        if (activo) setEstudiantes(lista);
      })
      .catch((e) => {
        if (activo) setError(message(e));
      });
    return () => {
      activo = false;
    };
  }, []);
  useEffect(() => {
    if (alumnoId === null) return;
    let activo = true;
    listarAsistencias(alumnoId)
      .then((lista) => {
        if (activo) setAsistencias(lista);
      })
      .catch((e) => {
        if (activo) setError(message(e));
      });
    return () => {
      activo = false;
    };
  }, [alumnoId, recarga]);
  const cambiarEstudiante = (id: number) => {
    setElegido(id);
    setAsistencias(null);
    setExito("");
  };
  return (
    <section className={styles.pagina}>
      <PageHeader
        title="Asistencia"
        description={`${estudiante ? `${estudiante.nombreCompleto} · ` : ""}Consulta y justificación de inasistencias`}
        actions={
          estudiantes && estudiantes.length > 0 ? (
          <label className={styles.selector}>
            <span>Estudiante:</span>
            <select
              className={styles.control}
              value={alumnoId ?? ""}
              onChange={(event) => cambiarEstudiante(Number(event.target.value))}
            >
              {alumnoId === null && <option value="" disabled>Selecciona un estudiante</option>}
              {estudiantes.map((item) => (
                <option key={item.id} value={item.id}>{item.nombreCompleto}</option>
              ))}
            </select>
          </label>
          ) : undefined
        }
      />
      <p className={styles.aviso}>
        Puedes consultar la asistencia del estudiante seleccionado y solicitar la justificación de sus inasistencias.
      </p>
      <Feedback error={error} success={exito} />
      {!error && !estudiantes && <Loading>Cargando estudiantes…</Loading>}
      {estudiantes?.length === 0 && <p className={styles.vacio}>No tienes estudiantes asociados.</p>}
      {estudiantes && estudiantes.length > 1 && alumnoId === null && (
        <section className={styles.elegir} aria-labelledby="elegir-estudiante">
          <h3 id="elegir-estudiante">¿De quién quieres ver la asistencia?</h3>
          <p>Elige a uno de tus estudiantes para ver su asistencia.</p>
          <ul className={styles.estudiantes}>
            {estudiantes.map((item) => (
              <li key={item.id}>
                <button type="button" className={styles.estudiante} onClick={() => cambiarEstudiante(item.id)}>
                  <span className={styles.avatar} aria-hidden="true">{initialsOf(item.nombreCompleto)}</span>
                  <span className={styles.estudianteDatos}>
                    <strong>{item.nombreCompleto}</strong>
                    <small>{item.codigoAlumno}</small>
                  </span>
                  <span className={styles.estudianteAccion}>Ver asistencia →</span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
      {!error && alumnoId !== null && !asistencias && <Loading>Cargando asistencia…</Loading>}
      {asistencias && (
        <AttendanceRecord
          key={alumnoId}
          asistencias={asistencias}
          onJustify={(fila) => {
            setExito("");
            setJustificando(fila);
          }}
        />
      )}
      {justificando && estudiante && (
        <JustifyDialog
          asistencia={justificando}
          estudiante={estudiante.nombreCompleto}
          onClose={() => setJustificando(null)}
          onSent={() => {
            setJustificando(null);
            setExito("La justificación fue enviada y está pendiente de revisión.");
            setRecarga((valor) => valor + 1);
          }}
        />
      )}
    </section>
  );
}

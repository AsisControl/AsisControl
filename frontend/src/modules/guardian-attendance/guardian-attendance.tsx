// asistencia alumnos
"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback, Loading } from "@/shared/ui";
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
      <div className={styles.encabezado}>
        <div>
          <h2>Asistencia</h2>
          <p>{estudiante ? `${estudiante.nombreCompleto} · ` : ""}Consulta y justificación de inasistencias</p>
        </div>
        {estudiantes && estudiantes.length > 0 && (
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
        )}
      </div>
      <p className={styles.aviso}>
        Puedes consultar la asistencia del estudiante seleccionado y solicitar la justificación de sus inasistencias.
      </p>
      <Feedback error={error} success={exito} />
      {!error && !estudiantes && <Loading>Cargando estudiantes…</Loading>}
      {estudiantes?.length === 0 && <p className={styles.vacio}>No tienes estudiantes asociados.</p>}
      {estudiantes && estudiantes.length > 1 && alumnoId === null && (
        <p className={styles.vacio}>Elige a uno de tus estudiantes para ver su asistencia.</p>
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

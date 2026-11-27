// asistencia alumnos
"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback, Loading, PageHeader, StatusPill } from "@/shared/ui";
import { AttendanceRecord } from "./attendance-record";
import { clasesDelDia, fechaConDia, fechaLocal, presentarEstado, sinJustificar } from "./lib";
import { listarAsistencias, listarHorarioPropio } from "./services/asistencia-apoderado-service";
import styles from "./guardian-attendance.module.css";
import type { AsistenciaEstudiante, BloqueHorario } from "./types";

/** El alumno solo consulta: el backend le devuelve únicamente su asistencia y justifica el apoderado. */
export function StudentAttendance({ fullName }: { fullName: string }) {
  const [asistencias, setAsistencias] = useState<AsistenciaEstudiante[] | null>(null);
  const [bloques, setBloques] = useState<BloqueHorario[] | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    let activo = true;
    // Sin horario la sección Hoy solo muestra lo ya registrado; no bloquea el resto de la pantalla.
    listarHorarioPropio(new Date().getFullYear())
      .then((lista) => {
        if (activo) setBloques(lista);
      })
      .catch(() => {
        if (activo) setBloques([]);
      });
    return () => {
      activo = false;
    };
  }, []);
  useEffect(() => {
    let activo = true;
    listarAsistencias()
      .then((lista) => {
        if (activo) setAsistencias(lista);
      })
      .catch((e) => {
        if (activo) setError(message(e));
      });
    return () => {
      activo = false;
    };
  }, []);
  const pendientes = asistencias ? sinJustificar(asistencias) : 0;
  const hoy = asistencias && bloques ? clasesDelDia(bloques, asistencias) : null;
  return (
    <section className={styles.pagina}>
      <PageHeader title="Mi asistencia" description={`${fullName} · Consulta de tu asistencia`} />
      <Feedback error={error} />
      {!error && !asistencias && <Loading>Cargando asistencia…</Loading>}
      {hoy && (
        <section className={styles.hoy} aria-labelledby="asistencia-hoy">
          <h3 id="asistencia-hoy">Hoy · {fechaConDia(fechaLocal())}</h3>
          {hoy.length === 0 ? (
            <p className={styles.sinClases}>Hoy no tienes clases.</p>
          ) : (
            <ul>
              {hoy.map(({ clave, curso, hora, horaFin, docente, aula, sesion, registro }) => {
                const estado = registro && presentarEstado(registro);
                const detalle = [sesion, docente, aula].filter(Boolean).join(" · ");
                return (
                  <li key={clave}>
                    <span className={styles.hoyHora}>
                      <strong>{hora}</strong>
                      {horaFin && <small>{horaFin}</small>}
                    </span>
                    <span className={styles.hoyClase}>
                      <span className={styles.hoyCurso}>{curso}</span>
                      {detalle && <small className={styles.hoyDetalle}>{detalle}</small>}
                    </span>
                    {estado ? (
                      <StatusPill state={estado.valor} label={estado.etiqueta} tone={estado.tono} />
                    ) : (
                      <span className={styles.porRegistrar}>Por registrar</span>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </section>
      )}
      {asistencias && <AttendanceRecord asistencias={asistencias} />}
      {pendientes > 0 && (
        <p className={styles.avisoPie}>
          {pendientes === 1
            ? "Tienes 1 inasistencia sin justificar. Pídele a tu apoderado que la justifique."
            : `Tienes ${pendientes} inasistencias sin justificar. Pídele a tu apoderado que las justifique.`}
        </p>
      )}
    </section>
  );
}

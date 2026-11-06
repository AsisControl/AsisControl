"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { descargarArchivo } from "@/modules/tasks";
import { Dialog, Feedback } from "@/shared/ui";
import { fechaHora, fechaSesion, horaSesion, indicadorJustificacion } from "./lib";
import { obtenerJustificacion } from "./services/asistencia-service";
import styles from "./attendance.module.css";
import type { AlumnoSesion, Justificacion, SesionAsistencia } from "./types";

/** Consulta sin salir de la sesión; los datos opcionales solo se muestran si existen. */
export function JustificationDialog({
  alumno,
  sesion,
  onClose,
}: {
  alumno: AlumnoSesion;
  sesion: SesionAsistencia;
  onClose: () => void;
}) {
  const [justificacion, setJustificacion] = useState<Justificacion | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    if (alumno.justificacionId === null) return;
    let activo = true;
    obtenerJustificacion(alumno.justificacionId)
      .then((datos) => {
        if (activo) setJustificacion(datos);
      })
      .catch((e) => {
        if (activo) setError(message(e));
      });
    return () => {
      activo = false;
    };
  }, [alumno.justificacionId]);
  const descargar = (archivo: Justificacion["archivos"][number]) => {
    setError("");
    descargarArchivo(archivo).catch((e) => setError(message(e)));
  };
  const estado = justificacion ? indicadorJustificacion(justificacion.estado).etiqueta : "";
  return (
    <Dialog title="Justificación" onClose={onClose}>
      <dl className={styles.datosJustificacion}>
        <dt>Estudiante</dt>
        <dd>{alumno.nombreCompleto}</dd>
        <dt>Sesión</dt>
        <dd>{fechaSesion(sesion.fecha)} · {horaSesion(sesion.horaInicio)}</dd>
        {justificacion && (
          <>
            <dt>Motivo</dt>
            <dd>{justificacion.motivo}</dd>
            {justificacion.observacionRevision && (
              <>
                <dt>Observación</dt>
                <dd>{justificacion.observacionRevision}</dd>
              </>
            )}
            <dt>Resolución</dt>
            <dd>
              {estado}
              {justificacion.fechaHoraRevision ? ` el ${fechaHora(justificacion.fechaHoraRevision)}` : ""}
            </dd>
            {justificacion.archivos.length > 0 && (
              <>
                <dt>Archivo adjunto</dt>
                <dd>
                  {justificacion.archivos.map((archivo) => (
                    <button key={archivo.id} type="button" className={styles.archivo} onClick={() => descargar(archivo)}>
                      {archivo.nombreOriginal}
                    </button>
                  ))}
                </dd>
              </>
            )}
          </>
        )}
      </dl>
      {!justificacion && !error && <p role="status">Cargando justificación…</p>}
      <Feedback error={error} />
      <div className="form-actions">
        <button type="button" onClick={onClose}>Cerrar</button>
      </div>
    </Dialog>
  );
}

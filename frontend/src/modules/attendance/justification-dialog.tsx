"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { descargarArchivo } from "@/modules/tasks";
import { Dialog, Feedback } from "@/shared/ui";
import { fechaHora, fechaSesion, horaSesion, indicadorJustificacion } from "./lib";
import {
  aprobarJustificacion,
  obtenerJustificacion,
  rechazarJustificacion,
} from "./services/asistencia-service";
import styles from "./attendance.module.css";
import type { Justificacion } from "./types";

const MAX_OBSERVACION = 2000;

/** Consulta sin salir de la pantalla; con `onResolved` el docente también puede resolverla. */
export function JustificationDialog({
  justificacionId,
  onClose,
  onResolved,
}: {
  justificacionId: number;
  onClose: () => void;
  onResolved?: (mensaje: string) => void;
}) {
  const [justificacion, setJustificacion] = useState<Justificacion | null>(null);
  const [error, setError] = useState("");
  const [decision, setDecision] = useState<"aprobar" | "rechazar" | null>(null);
  const [observacion, setObservacion] = useState("");
  const [guardando, setGuardando] = useState(false);
  useEffect(() => {
    let activo = true;
    obtenerJustificacion(justificacionId)
      .then((datos) => {
        if (activo) setJustificacion(datos);
      })
      .catch((e) => {
        if (activo) setError(message(e));
      });
    return () => {
      activo = false;
    };
  }, [justificacionId]);
  const descargar = (archivo: Justificacion["archivos"][number]) => {
    setError("");
    descargarArchivo(archivo).catch((e) => setError(message(e)));
  };
  const resolver = async () => {
    if (!justificacion) return;
    setGuardando(true);
    setError("");
    try {
      if (decision === "aprobar") await aprobarJustificacion(justificacion.id, justificacion.version);
      else await rechazarJustificacion(justificacion.id, justificacion.version, observacion.trim());
      onResolved?.(decision === "aprobar" ? "Justificación aprobada." : "Justificación rechazada.");
    } catch (e) {
      setError(message(e));
      setGuardando(false);
    }
  };
  const estado = justificacion ? indicadorJustificacion(justificacion.estado).etiqueta : "";
  const pendiente = justificacion?.estado === "ENVIADA" || justificacion?.estado === "EN_REVISION";
  return (
    <Dialog title="Justificación" busy={guardando} onClose={onClose}>
      {justificacion && (
        <dl className={styles.datosJustificacion}>
          <dt>Estudiante</dt>
          <dd>{justificacion.estudiante}</dd>
          <dt>Curso</dt>
          <dd>{justificacion.curso} - {justificacion.seccion}</dd>
          <dt>Sesión</dt>
          <dd>
            {fechaSesion(justificacion.fechaSesion)}
            {justificacion.horaSesion ? ` · ${horaSesion(justificacion.horaSesion)}` : ""}
          </dd>
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
        </dl>
      )}
      {!justificacion && !error && <p role="status">Cargando justificación…</p>}
      {decision === "aprobar" && (
        <p className={styles.avisoInfo}>
          La inasistencia quedará como justificada. Esta decisión no se puede deshacer.
        </p>
      )}
      {decision === "rechazar" && (
        <label className={styles.motivo}>
          <span>Motivo del rechazo <em>*</em></span>
          <textarea
            value={observacion}
            rows={4}
            autoFocus
            maxLength={MAX_OBSERVACION}
            disabled={guardando}
            placeholder="Ej.: el documento adjunto no corresponde a la fecha."
            onChange={(event) => setObservacion(event.target.value)}
          />
        </label>
      )}
      <Feedback error={error} />
      <div className="form-actions">
        {decision ? (
          <>
            <button type="button" disabled={guardando} onClick={() => setDecision(null)}>Volver</button>
            <button
              type="button"
              className={decision === "aprobar" ? "primary-action" : "primary-action danger"}
              disabled={guardando || (decision === "rechazar" && !observacion.trim())}
              onClick={resolver}
            >
              {guardando ? "Guardando…" : decision === "aprobar" ? "Confirmar aprobación" : "Confirmar rechazo"}
            </button>
          </>
        ) : (
          <>
            <button type="button" onClick={onClose}>Cerrar</button>
            {onResolved && pendiente && (
              <>
                <button type="button" onClick={() => setDecision("rechazar")}>Rechazar</button>
                <button type="button" className="primary-action" onClick={() => setDecision("aprobar")}>Aprobar</button>
              </>
            )}
          </>
        )}
      </div>
    </Dialog>
  );
}

// asistencia alumnos
"use client";
import { useRef, useState, type ChangeEvent } from "react";
import { message } from "@/core/api/client";
import { FILE_ACCEPT, fileError, subirArchivo } from "@/modules/tasks";
import { Dialog, Feedback } from "@/shared/ui";
import { componerMotivo, fechaCorta, MAX_OBSERVACIONES, MOTIVOS } from "./lib";
import { enviarJustificacion } from "./services/asistencia-apoderado-service";
import styles from "./guardian-attendance.module.css";
import type { AsistenciaEstudiante } from "./types";

/** El sustento es opcional; ya subido se conserva para no repetir la carga al reintentar. */
export function JustifyDialog({
  asistencia,
  estudiante,
  onClose,
  onSent,
}: {
  asistencia: AsistenciaEstudiante;
  estudiante: string;
  onClose: () => void;
  onSent: () => void;
}) {
  const selector = useRef<HTMLInputElement>(null);
  const subido = useRef<number | null>(null);
  const [motivo, setMotivo] = useState("");
  const [observaciones, setObservaciones] = useState("");
  const [archivo, setArchivo] = useState<File | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState("");
  const elegir = (event: ChangeEvent<HTMLInputElement>) => {
    const elegido = event.target.files?.[0] ?? null;
    event.target.value = "";
    const problema = elegido ? fileError(elegido) : "";
    setError(problema);
    subido.current = null;
    setArchivo(problema ? null : elegido);
  };
  const enviar = async () => {
    setEnviando(true);
    setError("");
    try {
      if (archivo && subido.current === null) subido.current = (await subirArchivo(archivo)).id;
      await enviarJustificacion(
        asistencia.id,
        componerMotivo(motivo, observaciones),
        subido.current === null ? [] : [subido.current],
      );
      onSent();
    } catch (e) {
      setError(message(e));
      setEnviando(false);
    }
  };
  const faltaDetalle = motivo === "Otro" && !observaciones.trim();
  return (
    <Dialog title="Justificar inasistencia" busy={enviando} onClose={onClose}>
      <p className={styles.ayuda}>La solicitud será enviada para revisión del colegio.</p>
      <dl className={styles.datos}>
        <div><dt>Estudiante</dt><dd>{estudiante}</dd></div>
        <div><dt>Fecha</dt><dd>{fechaCorta(asistencia.fechaRegistro)}</dd></div>
        <div><dt>Curso</dt><dd>{asistencia.curso}</dd></div>
      </dl>
      <label className={styles.campo}>
        <span>Motivo <em>*</em></span>
        <select value={motivo} disabled={enviando} onChange={(event) => setMotivo(event.target.value)}>
          <option value="">Selecciona un motivo</option>
          {MOTIVOS.map((opcion) => <option key={opcion} value={opcion}>{opcion}</option>)}
        </select>
      </label>
      <label className={styles.campo}>
        <span>Observaciones {motivo === "Otro" && <em>*</em>}</span>
        <textarea
          rows={3}
          value={observaciones}
          maxLength={MAX_OBSERVACIONES}
          disabled={enviando}
          placeholder="Describe brevemente el motivo de la inasistencia…"
          onChange={(event) => setObservaciones(event.target.value)}
        />
      </label>
      <div className={styles.campo}>
        <span>Sustento (opcional)</span>
        <div className={styles.sustento}>
          <p>Adjunta un certificado, constancia o imagen como sustento.</p>
          <input
            ref={selector}
            hidden
            type="file"
            accept={FILE_ACCEPT}
            aria-label="Archivo de sustento"
            onChange={elegir}
          />
          {archivo ? (
            <p className={styles.archivo}>
              {archivo.name}
              <button
                type="button"
                disabled={enviando}
                onClick={() => {
                  subido.current = null;
                  setArchivo(null);
                }}
              >
                Quitar
              </button>
            </p>
          ) : (
            <button
              type="button"
              className={styles.accion}
              disabled={enviando}
              onClick={() => selector.current?.click()}
            >
              Seleccionar archivo
            </button>
          )}
        </div>
      </div>
      <Feedback error={error} />
      <div className="form-actions">
        <button type="button" disabled={enviando} onClick={onClose}>Cancelar</button>
        <button
          type="button"
          className="primary-action"
          disabled={enviando || !motivo || faltaDetalle}
          onClick={enviar}
        >
          {enviando ? "Enviando…" : "Enviar justificación"}
        </button>
      </div>
    </Dialog>
  );
}

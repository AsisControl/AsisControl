"use client";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { message } from "@/core/api/client";
import { countLabel } from "@/shared/lib/formatters";
import { Dialog, Feedback, StatusPill } from "@/shared/ui";
import { coincideBusqueda, etiquetaMarca } from "./lib";
import { JustificationDialog } from "./justification-dialog";
import { corregirAsistencia } from "./services/asistencia-service";
import { MarkButtons } from "./session-marks";
import {
  Buscador,
  ExitDialog,
  JustificationIndicator,
  SessionHeader,
  SessionSummary,
  StudentsTable,
} from "./session-ui";
import styles from "./attendance.module.css";
import type { AlumnoSesion, EstadoMarca, SesionAsistencia } from "./types";

const MAX_MOTIVO = 500;

/** Corrección de una asistencia finalizada: el motivo se pide al confirmar y la sesión sigue TOMADA. */
export function SessionCorrection({
  sesion,
  onSaved,
  onCancel,
}: {
  sesion: SesionAsistencia;
  onSaved: (sesion: SesionAsistencia) => void;
  onCancel: () => void;
}) {
  const router = useRouter();
  const campoMotivo = useRef<HTMLTextAreaElement>(null);
  const [nuevos, setNuevos] = useState<Record<number, EstadoMarca>>({});
  const [motivo, setMotivo] = useState("");
  const [busqueda, setBusqueda] = useState("");
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState("");
  const [confirmando, setConfirmando] = useState(false);
  const [salida, setSalida] = useState<string | "cancelar" | null>(null);
  const [consulta, setConsulta] = useState<AlumnoSesion | null>(null);
  const cambios = sesion.alumnos.flatMap((alumno) => {
    const estado = nuevos[alumno.alumnoId];
    return estado && estado !== alumno.estado ? [{ alumno, estado }] : [];
  });
  const cambiado = (alumno: AlumnoSesion) => cambios.some((cambio) => cambio.alumno === alumno);
  const sinGuardar = cambios.length > 0 || motivo.trim() !== "";
  const totales = sesion.alumnos.map((alumno) => ({ estado: nuevos[alumno.alumnoId] ?? alumno.estado }));
  const salir = (destino: string | "cancelar") => {
    if (sinGuardar) setSalida(destino);
    else if (destino === "cancelar") onCancel();
    else router.push(destino);
  };
  const guardar = async () => {
    setGuardando(true);
    setError("");
    try {
      const marcas = cambios.map(({ alumno, estado }) => ({ alumnoId: alumno.alumnoId, estado }));
      onSaved(await corregirAsistencia(sesion.id, marcas, motivo.trim(), sesion.version));
    } catch (e) {
      setError(message(e));
      setGuardando(false);
    }
  };
  return (
    <section className={`${styles.pagina} ${styles.conBarra}`}>
      <SessionHeader sesion={sesion} onNavigate={salir} />
      <p className={styles.avisoInfo}>
        Estás modificando una asistencia finalizada. Los cambios quedarán registrados con el motivo de corrección.
      </p>
      <SessionSummary
        sesion={sesion}
        estado={<StatusPill state="CORRECCION" label="Modo corrección" tone="info" />}
        totales={totales}
        nota={cambios.length > 0 && <span className={styles.textoSuave}>Con cambios sin guardar</span>}
        registro
      />
      <div className={styles.lista}>
        <div className={styles.herramientas}>
          <Buscador valor={busqueda} onChange={setBusqueda} />
        </div>
        <StudentsTable
          alumnos={sesion.alumnos.filter((alumno) => coincideBusqueda(alumno.nombreCompleto, busqueda))}
          claseFila={(alumno) => (cambiado(alumno) ? styles.filaCambiada : undefined)}
          renderEstado={(alumno) => (
            <MarkButtons
              nombre={alumno.nombreCompleto}
              valor={nuevos[alumno.alumnoId] ?? alumno.estado}
              disabled={guardando}
              onChange={(estado) => setNuevos((actual) => ({ ...actual, [alumno.alumnoId]: estado }))}
            />
          )}
          renderIndicador={(alumno) => (
            <>
              <JustificationIndicator alumno={alumno} onOpen={setConsulta} />
              {cambiado(alumno) && <small className={styles.antes}>antes: {etiquetaMarca(alumno.estado ?? "")}</small>}
            </>
          )}
        />
      </div>
      <div className={styles.barra}>
        <div className={styles.barraInterior}>
          <span className={cambios.length ? styles.mensajeInfo : styles.textoSuave}>
            {cambios.length ? countLabel(cambios.length, "cambio pendiente", "cambios pendientes") : "Sin cambios por guardar"}
          </span>
          <div>
            <button type="button" className={styles.enlaceTexto} disabled={guardando} onClick={() => salir("cancelar")}>
              Cancelar
            </button>
            <button
              type="button"
              className={`primary-action ${styles.guardarCambios}`}
              disabled={guardando || cambios.length === 0}
              onClick={() => setConfirmando(true)}
            >
              Guardar cambios
            </button>
          </div>
        </div>
      </div>
      {confirmando && (
        <Dialog
          title="Confirmar corrección"
          busy={guardando}
          initialFocusRef={campoMotivo}
          onClose={() => setConfirmando(false)}
        >
          <p className={styles.textoSuave}>{countLabel(cambios.length, "cambio pendiente", "cambios pendientes")}</p>
          <div className={styles.listaCambios}>
            {cambios.map(({ alumno, estado }) => (
              <div className={styles.cambio} key={alumno.alumnoId}>
                <span>{alumno.nombreCompleto}</span>
                <strong>{etiquetaMarca(alumno.estado ?? "")} → {etiquetaMarca(estado)}</strong>
              </div>
            ))}
          </div>
          <label className={styles.motivo}>
            <span>Motivo de la corrección <em>*</em></span>
            <textarea
              ref={campoMotivo}
              value={motivo}
              rows={5}
              maxLength={MAX_MOTIVO}
              disabled={guardando}
              placeholder="Ej.: la estudiante llegó a tiempo, se registró por error."
              onChange={(event) => setMotivo(event.target.value)}
            />
          </label>
          <div className={styles.ayudaMotivo}>
            <span>
              {motivo.trim()
                ? "Quedará registrado junto con el docente responsable y la fecha."
                : "Escribe el motivo para confirmar."}
            </span>
            <span>{motivo.length} / {MAX_MOTIVO}</span>
          </div>
          <Feedback error={error} />
          <div className="form-actions">
            <button type="button" disabled={guardando} onClick={() => setConfirmando(false)}>
              Seguir editando
            </button>
            <button
              type="button"
              className="primary-action"
              disabled={guardando || motivo.trim() === ""}
              onClick={guardar}
            >
              {guardando ? "Guardando…" : "Confirmar y guardar"}
            </button>
          </div>
        </Dialog>
      )}
      {salida && (
        <ExitDialog
          onStay={() => setSalida(null)}
          onLeave={() => (salida === "cancelar" ? onCancel() : router.push(salida))}
        />
      )}
      {consulta && <JustificationDialog alumno={consulta} sesion={sesion} onClose={() => setConsulta(null)} />}
    </section>
  );
}

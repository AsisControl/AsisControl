"use client";
import { useState } from "react";
import { StatusPill } from "@/shared/ui";
import { coincideBusqueda } from "./lib";
import { JustificationDialog } from "./justification-dialog";
import { MarkStatus } from "./session-marks";
import {
  AlertIcon,
  Buscador,
  JustificationIndicator,
  SessionHeader,
  SessionSummary,
  StudentsTable,
} from "./session-ui";
import styles from "./attendance.module.css";
import type { AlumnoSesion, SesionAsistencia } from "./types";

/** Consulta de una sesión: TOMADA con sus estados, o pasada sin asistencia registrada. */
export function SessionReadonly({
  sesion,
  aviso,
  onEdit,
  onJustificationResolved,
}: {
  sesion: SesionAsistencia;
  aviso?: string;
  onEdit: () => void;
  onJustificationResolved: (mensaje: string) => void;
}) {
  const [busqueda, setBusqueda] = useState("");
  const [consulta, setConsulta] = useState<AlumnoSesion | null>(null);
  const tomada = sesion.estado === "TOMADA";
  return (
    <section className={styles.pagina}>
      <SessionHeader
        sesion={sesion}
        accion={
          sesion.puedeEditar && (
            <button type="button" className={styles.secundario} onClick={onEdit}>
              Editar asistencia
            </button>
          )
        }
      />
      {aviso && <p className="action-feedback" role="status">{aviso}</p>}
      {!tomada && (
        <p className={styles.avisoAlerta}>
          Esta sesión no tiene asistencia registrada y ya no se puede registrar retroactivamente.
        </p>
      )}
      {tomada ? (
        <SessionSummary
          sesion={sesion}
          estado={<StatusPill state="TOMADA" label="Tomada" tone="success" />}
          totales={sesion.alumnos}
          registro
        />
      ) : (
        <SessionSummary
          sesion={sesion}
          estado={<span className={styles.sinRegistrar}><AlertIcon /> Sin registrar</span>}
        />
      )}
      <div className={styles.lista}>
        <div className={styles.herramientas}>
          <Buscador valor={busqueda} onChange={setBusqueda} />
        </div>
        <StudentsTable
          alumnos={sesion.alumnos.filter((alumno) => coincideBusqueda(alumno.nombreCompleto, busqueda))}
          renderEstado={(alumno) =>
            alumno.estado ? <MarkStatus estado={alumno.estado} /> : <span aria-label="Sin registro">—</span>
          }
          renderIndicador={(alumno) => <JustificationIndicator alumno={alumno} onOpen={setConsulta} />}
        />
      </div>
      {consulta?.justificacionId != null && (
        <JustificationDialog
          justificacionId={consulta.justificacionId}
          onClose={() => setConsulta(null)}
          onResolved={
            sesion.puedeEditar
              ? (mensaje) => {
                  setConsulta(null);
                  onJustificationResolved(mensaje);
                }
              : undefined
          }
        />
      )}
    </section>
  );
}

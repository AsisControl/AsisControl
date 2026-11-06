// asistencia alumnos
"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import type { Page } from "@/shared/types/api";
import { Feedback, Pager, StatusPill } from "@/shared/ui";
import { JustificationDialog } from "./justification-dialog";
import { fechaHora, fechaSesion, indicadorJustificacion } from "./lib";
import { listarJustificaciones } from "./services/asistencia-service";
import styles from "./attendance.module.css";
import type { EstadoJustificacion, Justificacion } from "./types";

type Bandeja = "pendientes" | "resueltas";

const BANDEJAS: Record<Bandeja, { etiqueta: string; estados: EstadoJustificacion[]; orden: string; vacio: string }> = {
  pendientes: {
    etiqueta: "Por revisar",
    estados: ["ENVIADA", "EN_REVISION"],
    orden: "fechaHoraEnvio,asc",
    vacio: "No tienes justificaciones por revisar.",
  },
  resueltas: {
    etiqueta: "Resueltas",
    estados: ["APROBADA", "RECHAZADA"],
    orden: "fechaHoraRevision,desc",
    vacio: "Todavía no has resuelto justificaciones.",
  },
};

/** Bandeja del docente: lo más antiguo primero, para que nada quede sin atender. */
export function JustificationInbox() {
  const [bandeja, setBandeja] = useState<Bandeja>("pendientes");
  const [pagina, setPagina] = useState(0);
  const [recarga, setRecarga] = useState(0);
  const [datos, setDatos] = useState<Page<Justificacion> | null>(null);
  const [error, setError] = useState("");
  const [exito, setExito] = useState("");
  const [abierta, setAbierta] = useState<number | null>(null);
  useEffect(() => {
    let activo = true;
    const { estados, orden } = BANDEJAS[bandeja];
    listarJustificaciones(estados, orden, pagina)
      .then((respuesta) => {
        if (activo) {
          setDatos(respuesta);
          setError("");
        }
      })
      .catch((e) => {
        if (activo) setError(message(e));
      });
    return () => {
      activo = false;
    };
  }, [bandeja, pagina, recarga]);
  const cambiar = (destino: Bandeja) => {
    setBandeja(destino);
    setPagina(0);
    setDatos(null);
    setExito("");
  };
  const resuelta = bandeja === "resueltas";
  return (
    <section className={styles.pagina}>
      <div className={styles.titulo}>
        <h2>Justificaciones</h2>
      </div>
      <p className={styles.subtitulo}>Revisa las solicitudes de los apoderados de tus cursos</p>
      <div className={styles.pestanas} role="group" aria-label="Bandeja de justificaciones">
        {(Object.keys(BANDEJAS) as Bandeja[]).map((clave) => (
          <button key={clave} type="button" aria-pressed={bandeja === clave} onClick={() => cambiar(clave)}>
            {BANDEJAS[clave].etiqueta}
          </button>
        ))}
      </div>
      <Feedback error={error} success={exito} />
      {!error && !datos && <p role="status">Cargando justificaciones…</p>}
      {datos && datos.content.length === 0 && <p className={styles.vacio}>{BANDEJAS[bandeja].vacio}</p>}
      {datos && datos.content.length > 0 && (
        <div className={styles.historial}>
          <table className={styles.tabla}>
            <thead>
              <tr>
                <th scope="col">Estudiante</th>
                <th scope="col">Curso</th>
                <th scope="col">Sesión</th>
                <th scope="col">{resuelta ? "Resolución" : "Enviada"}</th>
                <th scope="col" className={styles.columnaAccion}>Acción</th>
              </tr>
            </thead>
            <tbody>
              {datos.content.map((justificacion) => (
                <tr key={justificacion.id}>
                  <td>{justificacion.estudiante}</td>
                  <td>{justificacion.curso} - {justificacion.seccion}</td>
                  <td>{fechaSesion(justificacion.fechaSesion)}</td>
                  <td>
                    {resuelta ? (
                      <StatusPill
                        state={justificacion.estado}
                        label={indicadorJustificacion(justificacion.estado).etiqueta}
                        tone={justificacion.estado === "APROBADA" ? "success" : "danger"}
                      />
                    ) : (
                      fechaHora(justificacion.fechaHoraEnvio)
                    )}
                  </td>
                  <td className={styles.columnaAccion}>
                    <button
                      type="button"
                      className={styles.secundario}
                      aria-label={`${resuelta ? "Ver" : "Revisar"} justificación de ${justificacion.estudiante}`}
                      onClick={() => setAbierta(justificacion.id)}
                    >
                      {resuelta ? "Ver" : "Revisar"}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {datos.totalPages > 1 && <Pager data={datos} loading={false} setPage={setPagina} />}
        </div>
      )}
      {abierta !== null && (
        <JustificationDialog
          justificacionId={abierta}
          onClose={() => setAbierta(null)}
          onResolved={(mensaje) => {
            setAbierta(null);
            setExito(mensaje);
            setRecarga((valor) => valor + 1);
          }}
        />
      )}
    </section>
  );
}

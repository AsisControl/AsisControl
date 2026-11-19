// asistencia alumnos
"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import type { Page } from "@/shared/types/api";
import { Feedback, Pager, StatusPill, Loading } from "@/shared/ui";
import { JustificationDialog } from "./justification-dialog";
import { fechaHora, fechaSesion, indicadorJustificacion } from "./lib";
import { listarJustificaciones } from "./services/asistencia-service";
import styles from "./teacher-check-in/teacher-attendance.module.css";
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

const iniciales = (nombre: string) =>
  nombre.split(/\s+/).filter(Boolean).slice(0, 2).map((parte) => parte[0]?.toUpperCase()).join("");

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
    <section>
      <div className={styles.header}>
        <h2>Justificaciones</h2>
        <p>Revisa las solicitudes de los apoderados de tus cursos</p>
      </div>
      <div className={styles.views} role="group" aria-label="Bandeja de justificaciones">
        {(Object.keys(BANDEJAS) as Bandeja[]).map((clave) => (
          <button key={clave} type="button" aria-pressed={bandeja === clave} onClick={() => cambiar(clave)}>
            {BANDEJAS[clave].etiqueta}
            {bandeja === clave && datos && datos.totalElements > 0 && (
              <span className={styles.count} aria-label={`${datos.totalElements} en total`}>{datos.totalElements}</span>
            )}
          </button>
        ))}
      </div>
      <Feedback error={error} success={exito} />
      {!error && !datos && <Loading skeleton="list">Cargando justificaciones…</Loading>}
      {datos && datos.content.length === 0 && (
        <div className={styles.empty}>
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M22 12h-6l-2 3h-4l-2-3H2" />
            <path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z" />
          </svg>
          <p>{BANDEJAS[bandeja].vacio}</p>
        </div>
      )}
      {datos && datos.content.length > 0 && (
        <div className={styles.card}>
          <table className={styles.tabla}>
            <thead>
              <tr>
                <th scope="col">Estudiante</th>
                <th scope="col">Curso</th>
                <th scope="col">Sesión</th>
                <th scope="col">{resuelta ? "Resolución" : "Enviada"}</th>
                <th scope="col" className={styles.alignEnd}>Acción</th>
              </tr>
            </thead>
            <tbody>
              {datos.content.map((justificacion) => (
                <tr key={justificacion.id}>
                  <td>
                    <div className={styles.person}>
                      <span className={styles.avatar} aria-hidden="true">{iniciales(justificacion.estudiante)}</span>
                      <strong>{justificacion.estudiante}</strong>
                    </div>
                  </td>
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
                  <td className={styles.alignEnd}>
                    <button
                      type="button"
                      className={resuelta ? styles.outline : styles.primary}
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
          {datos.totalPages > 1 && (
            <div className={styles.pager}><Pager data={datos} loading={false} setPage={setPagina} /></div>
          )}
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

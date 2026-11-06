"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback } from "@/shared/ui";
import { obtenerSesion } from "./services/asistencia-service";
import { SessionCorrection } from "./session-correction";
import { SessionReadonly } from "./session-readonly";
import { SessionTaking } from "./session-taking";
import styles from "./attendance.module.css";
import type { SesionAsistencia } from "./types";

/** Carga la sesión y elige su vista según los permisos que informa el backend. */
export function SessionDetail({ asignacionId, sesionId }: { asignacionId: number; sesionId: number }) {
  const [sesion, setSesion] = useState<SesionAsistencia | null>(null);
  const [error, setError] = useState("");
  const [corrigiendo, setCorrigiendo] = useState(false);
  const [aviso, setAviso] = useState("");
  useEffect(() => {
    let activo = true;
    obtenerSesion(sesionId)
      .then((datos) => {
        if (activo) setSesion(datos);
      })
      .catch((e) => {
        if (activo) setError(message(e));
      });
    return () => {
      activo = false;
    };
  }, [sesionId]);
  const historial = `/mis-cursos/${asignacionId}/asistencia`;
  if (error)
    return (
      <section>
        <Feedback error={error} />
        <Link href={historial}>Volver al historial</Link>
      </section>
    );
  if (!sesion) return <p role="status">Cargando asistencia…</p>;
  if (!sesion.puedeVer)
    return (
      <section>
        <p className={styles.vacio}>Esta sesión todavía no está disponible.</p>
        <Link href={historial}>Volver al historial</Link>
      </section>
    );
  if (corrigiendo && sesion.puedeEditar)
    return (
      <SessionCorrection
        sesion={sesion}
        onCancel={() => setCorrigiendo(false)}
        onSaved={(actualizada) => {
          setSesion(actualizada);
          setAviso("Cambios guardados correctamente.");
          setCorrigiendo(false);
        }}
      />
    );
  if (sesion.puedeTomar)
    return (
      <SessionTaking
        sesion={sesion}
        onSaved={(actualizada, mensaje) => {
          setSesion(actualizada);
          setAviso(mensaje);
        }}
      />
    );
  return (
    <SessionReadonly
      sesion={sesion}
      aviso={aviso}
      onEdit={() => {
        setAviso("");
        setCorrigiendo(true);
      }}
    />
  );
}

// asistencia alumnos
"use client";
import { useEffect, useMemo, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback, Loading } from "@/shared/ui";
import { AttendanceRecord } from "./attendance-record";
import type { Page } from "@/shared/types/api";
import { Feedback, Pager, StatusPill, Loading } from "@/shared/ui";
import { DateField } from "./date-field";
import { JustifyDialog } from "./justify-dialog";
import { detalleFila, etiquetaMes, fechaCorta, presentarEstado, puedeJustificar, resumir } from "./lib";
import { listarAsistencias, listarEstudiantes } from "./services/asistencia-apoderado-service";
import styles from "./guardian-attendance.module.css";
import type { AsistenciaEstudiante, EstudianteFamilia } from "./types";

const POR_PAGINA = 20;
const SIN_FILTROS = { mes: "", curso: "", desde: "", hasta: "" };

/** Asistencia de cada estudiante a cargo; el apoderado es quien solicita las justificaciones. */
export function GuardianAttendance() {
  const [estudiantes, setEstudiantes] = useState<EstudianteFamilia[] | null>(null);
  const [elegido, setElegido] = useState<number | null>(null);
  const [asistencias, setAsistencias] = useState<AsistenciaEstudiante[] | null>(null);
  const [filtros, setFiltros] = useState(SIN_FILTROS);
  const [pagina, setPagina] = useState(0);
  const [recarga, setRecarga] = useState(0);
  const [justificando, setJustificando] = useState<AsistenciaEstudiante | null>(null);
  const [error, setError] = useState("");
  const [exito, setExito] = useState("");
  const alumnoId = elegido ?? estudiantes?.[0]?.id ?? null;
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
  const lista = useMemo(() => asistencias ?? [], [asistencias]);
  const meses = [...new Set(lista.map((fila) => fila.fechaRegistro.slice(0, 7)))];
  const cursos = [...new Set(lista.map((fila) => fila.curso))].sort((a, b) => a.localeCompare(b, "es"));
  const filas = useMemo(
    () =>
      lista.filter(
        ({ fechaRegistro, curso }) =>
          (!filtros.mes || fechaRegistro.startsWith(filtros.mes)) &&
          (!filtros.curso || curso === filtros.curso) &&
          (!filtros.desde || fechaRegistro >= filtros.desde) &&
          (!filtros.hasta || fechaRegistro <= filtros.hasta),
      ),
    [lista, filtros],
  );
  const filtrar = (cambio: Partial<typeof SIN_FILTROS>) => {
    setFiltros((actual) => ({ ...actual, ...cambio }));
    setPagina(0);
  };
  const cambiarEstudiante = (id: number) => {
    setElegido(id);
    setAsistencias(null);
    setFiltros(SIN_FILTROS);
    setPagina(0);
    setExito("");
  };
  const { presencias, tardanzas, inasistencias, porcentaje } = resumir(filas);
  const totalPaginas = Math.max(1, Math.ceil(filas.length / POR_PAGINA));
  const actual = Math.min(pagina, totalPaginas - 1);
  const inicio = actual * POR_PAGINA;
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
            <select value={alumnoId ?? ""} onChange={(event) => cambiarEstudiante(Number(event.target.value))}>
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

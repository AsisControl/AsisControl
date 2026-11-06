// asistencia alumnos
"use client";
import { useEffect, useMemo, useState } from "react";
import { message } from "@/core/api/client";
import type { Page } from "@/shared/types/api";
import { Feedback, Pager, StatusPill } from "@/shared/ui";
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
      {!error && !estudiantes && <p role="status">Cargando estudiantes…</p>}
      {estudiantes?.length === 0 && <p className={styles.vacio}>No tienes estudiantes asociados.</p>}
      {!error && alumnoId !== null && !asistencias && <p role="status">Cargando asistencia…</p>}
      {asistencias && (
        <>
          <div className={styles.filtros}>
            <label>
              <span>Mes</span>
              <select value={filtros.mes} onChange={(event) => filtrar({ mes: event.target.value })}>
                <option value="">Todos</option>
                {meses.map((mes) => <option key={mes} value={mes}>{etiquetaMes(mes)}</option>)}
              </select>
            </label>
            <label>
              <span>Curso</span>
              <select value={filtros.curso} onChange={(event) => filtrar({ curso: event.target.value })}>
                <option value="">Todos los cursos</option>
                {cursos.map((curso) => <option key={curso} value={curso}>{curso}</option>)}
              </select>
            </label>
            <label>
              <span>Desde</span>
              <input type="date" value={filtros.desde} onChange={(event) => filtrar({ desde: event.target.value })} />
            </label>
            <label>
              <span>Hasta</span>
              <input type="date" value={filtros.hasta} onChange={(event) => filtrar({ hasta: event.target.value })} />
            </label>
          </div>
          <div className={styles.totales}>
            <div><strong className={styles.presencias}>{presencias}</strong><span>Presencias</span></div>
            <div><strong className={styles.tardanzas}>{tardanzas}</strong><span>Tardanzas</span></div>
            <div><strong className={styles.inasistencias}>{inasistencias}</strong><span>Inasistencias</span></div>
            <div><strong className={styles.porcentaje}>{porcentaje}%</strong><span>% Asistencia</span></div>
          </div>
          <div className={styles.lista}>
            {filas.length === 0 ? (
              <p className={styles.vacio}>No hay asistencias registradas con estos filtros.</p>
            ) : (
              <table className={styles.tabla}>
                <thead>
                  <tr>
                    <th scope="col">Fecha</th>
                    <th scope="col">Curso</th>
                    <th scope="col">Estado</th>
                    <th scope="col">Detalle</th>
                    <th scope="col">Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {filas.slice(inicio, inicio + POR_PAGINA).map((fila) => {
                    const { etiqueta, tono } = presentarEstado(fila);
                    return (
                      <tr key={fila.id}>
                        <td className={styles.fecha}>{fechaCorta(fila.fechaRegistro)}</td>
                        <td className={styles.curso}>{fila.curso}</td>
                        <td><StatusPill state={fila.estado} label={etiqueta} tone={tono} /></td>
                        <td className={styles.detalle}>{detalleFila(fila)}</td>
                        <td>
                          {puedeJustificar(fila) && (
                            <button
                              type="button"
                              className={styles.accion}
                              aria-label={`Justificar ${fila.curso} del ${fechaCorta(fila.fechaRegistro)}`}
                              onClick={() => {
                                setExito("");
                                setJustificando(fila);
                              }}
                            >
                              Justificar
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
            {totalPaginas > 1 && (
              <Pager
                data={{
                  content: [],
                  totalElements: filas.length,
                  totalPages: totalPaginas,
                  page: actual,
                  first: actual === 0,
                  last: actual === totalPaginas - 1,
                } as Page<unknown>}
                loading={false}
                setPage={setPagina}
              />
            )}
          </div>
        </>
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

// asistencia alumnos
"use client";
import { useState } from "react";
import { countLabel } from "@/shared/lib/formatters";
import { Dialog, StatusPill } from "@/shared/ui";
import { DateField } from "./date-field";
import {
  anioDe,
  enPeriodo,
  ESTADOS,
  estadoBase,
  NOTA_SIN_MARCAR,
  etiquetaPeriodo,
  fechaConDia,
  horaDe,
  presentarEstado,
  presentarJustificacion,
  puedeJustificar,
  resumir,
  type EstadoBase,
  type FiltroPeriodo,
} from "./lib";
import styles from "./guardian-attendance.module.css";
import type { AsistenciaEstudiante } from "./types";

const POR_TANDA = 20;
type Filtros = {
  anio: number | null;
  periodo: FiltroPeriodo;
  desde: string;
  hasta: string;
  curso: string;
  estado: EstadoBase | "";
};
const SIN_FILTROS: Filtros = { anio: null, periodo: "TODO", desde: "", hasta: "", curso: "", estado: "" };

/** Detalle de una clase: fecha, curso, hora, período, estado y, si la hay, la justificación. */
function RecordDetail({
  fila,
  onClose,
  onJustify,
}: {
  fila: AsistenciaEstudiante;
  onClose: () => void;
  onJustify?: () => void;
}) {
  const { etiqueta, tono } = presentarEstado(fila);
  const justificacion = presentarJustificacion(fila);
  return (
    <Dialog title="Detalle de asistencia" onClose={onClose}>
      <dl className={styles.detalleRegistro}>
        <dt>Fecha</dt>
        <dd>{fechaConDia(fila.fechaRegistro)}</dd>
        <dt>Curso</dt>
        <dd>{fila.curso}</dd>
        <dt>Hora de la clase</dt>
        <dd>{horaDe(fila)}</dd>
        {fila.periodo && (
          <>
            <dt>Período</dt>
            <dd>{fila.periodo}</dd>
          </>
        )}
        <dt>Estado</dt>
        <dd>
          <StatusPill state={fila.estado} label={etiqueta} tone={tono} />
          {estadoBase(fila) === "SIN_MARCAR" && <p className={styles.notaDetalle}>{NOTA_SIN_MARCAR}.</p>}
        </dd>
        {fila.comentario && (
          <>
            <dt>Comentario del docente</dt>
            <dd>{fila.comentario}</dd>
          </>
        )}
        {justificacion && (
          <>
            <dt>Justificación</dt>
            <dd><StatusPill state={justificacion.etiqueta} label={justificacion.etiqueta} tone={justificacion.tono} /></dd>
          </>
        )}
        {justificacion && fila.motivoJustificacion && fila.estadoJustificacion !== "CANCELADA" && (
          <>
            <dt>Motivo</dt>
            <dd>{fila.motivoJustificacion}</dd>
          </>
        )}
        {fila.estadoJustificacion === "RECHAZADA" && fila.observacionRevision && (
          <>
            <dt>Motivo del rechazo</dt>
            <dd>{fila.observacionRevision}</dd>
          </>
        )}
      </dl>
      <div className="form-actions">
        <button type="button" onClick={onClose}>Cerrar</button>
        {onJustify && (
          <button type="button" className="primary-action" onClick={onJustify}>Justificar</button>
        )}
      </div>
    </Dialog>
  );
}

/**
 * Historial de un estudiante con filtros, totales y tabla, común al apoderado y al alumno.
 * Sin `onJustify` la tabla es solo de consulta. Los filtros se reinician al cambiar el `key`.
 */
export function AttendanceRecord({
  asistencias,
  onJustify,
}: {
  asistencias: AsistenciaEstudiante[];
  onJustify?: (fila: AsistenciaEstudiante) => void;
}) {
  const [filtros, setFiltros] = useState(SIN_FILTROS);
  const [visibles, setVisibles] = useState(POR_TANDA);
  const [abiertos, setAbiertos] = useState(false);
  const [detalle, setDetalle] = useState<AsistenciaEstudiante | null>(null);
  const anios = [...new Set(asistencias.map(anioDe))].sort((a, b) => b - a);
  const anio = filtros.anio ?? anios[0];
  const delAnio = asistencias.filter((fila) => anioDe(fila) === anio);
  const periodos = [...new Set(
    [...delAnio].sort((a, b) => a.fechaRegistro.localeCompare(b.fechaRegistro)).map((fila) => fila.periodo),
  )].filter((periodo): periodo is string => !!periodo);
  const cursos = [...new Set(delAnio.map((fila) => fila.curso))].sort((a, b) => a.localeCompare(b, "es"));
  const base = delAnio.filter(
    (fila) =>
      enPeriodo(fila, filtros.periodo, filtros.desde, filtros.hasta) &&
      (!filtros.curso || fila.curso === filtros.curso),
  );
  const filas = base
    .filter((fila) => !filtros.estado || estadoBase(fila) === filtros.estado)
    .sort((a, b) => `${b.fechaRegistro}${b.horaSesion ?? ""}`.localeCompare(`${a.fechaRegistro}${a.horaSesion ?? ""}`));
  const extras = Number(!!filtros.curso) + Number(!!filtros.estado);
  const activos = extras + Number(filtros.periodo !== "TODO") + Number(filtros.anio !== null);
  const cambiar = (cambio: Partial<Filtros>) => {
    setFiltros((actual) => ({ ...actual, ...cambio }));
    setVisibles(POR_TANDA);
  };
  const limpiar = () => {
    setFiltros(SIN_FILTROS);
    setVisibles(POR_TANDA);
  };
  const { totales, porcentaje } = resumir(base);
  // "Sin marcar" solo aparece como tarjeta y filtro cuando existe en el año elegido.
  const estados = ESTADOS.filter(
    ({ valor }) => valor !== "SIN_MARCAR" || delAnio.some((fila) => estadoBase(fila) === "SIN_MARCAR"),
  );
  const contexto = [
    anios.length > 1 ? String(anio) : null,
    etiquetaPeriodo(filtros.periodo, filtros.desde, filtros.hasta),
    filtros.curso || "Todos los cursos",
    countLabel(base.length, "registro", "registros"),
  ].filter(Boolean).join(" · ");
  if (!asistencias.length) return <p className={styles.vacio}>Aún no hay asistencias registradas.</p>;
  return (
    <>
      <div className={styles.filtros}>
        {anios.length > 1 && (
          <label>
            <span>Año</span>
            <select
              className={styles.control}
              value={anio}
              onChange={(event) => cambiar({ anio: Number(event.target.value), periodo: "TODO", desde: "", hasta: "", curso: "" })}
            >
              {anios.map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
          </label>
        )}
        <label className={styles.filtroPeriodo}>
          <span>Periodo</span>
          <select
            className={styles.control}
            value={filtros.periodo}
            onChange={(event) => cambiar({ periodo: event.target.value as FiltroPeriodo })}
          >
            <option value="TODO">Todo el año</option>
            {anio === anios[0] && <option value="MES">Este mes</option>}
            {anio === anios[0] && <option value="MES_ANTERIOR">Mes anterior</option>}
            {periodos.map((periodo) => <option key={periodo} value={`P:${periodo}`}>{periodo}</option>)}
            <option value="PERSONALIZADO">Personalizado</option>
          </select>
        </label>
        {filtros.periodo === "PERSONALIZADO" && (
          <>
            <DateField label="Desde" onChange={(desde) => cambiar({ desde })} />
            <DateField label="Hasta" onChange={(hasta) => cambiar({ hasta })} />
          </>
        )}
        <button
          type="button"
          className={styles.botonFiltros}
          aria-expanded={abiertos}
          onClick={() => setAbiertos((valor) => !valor)}
        >
          Filtros{extras ? ` · ${extras}` : ""}
        </button>
        <div className={styles.filtrosExtra} data-abierto={abiertos}>
          <label>
            <span>Curso</span>
            <select className={styles.control} value={filtros.curso} onChange={(event) => cambiar({ curso: event.target.value })}>
              <option value="">Todos</option>
              {cursos.map((curso) => <option key={curso} value={curso}>{curso}</option>)}
            </select>
          </label>
          <label>
            <span>Estado</span>
            <select
              className={styles.control}
              value={filtros.estado}
              onChange={(event) => cambiar({ estado: event.target.value as EstadoBase | "" })}
            >
              <option value="">Todos</option>
              {estados.map(({ valor, etiqueta }) => <option key={valor} value={valor}>{etiqueta}</option>)}
            </select>
          </label>
        </div>
        {activos > 0 && (
          <button type="button" className={styles.limpiar} onClick={limpiar}>Limpiar filtros</button>
        )}
      </div>
      <p className={styles.contexto}>{contexto}</p>
      <div className={estados.length > 3 ? `${styles.totales} ${styles.conSinMarcar}` : styles.totales}>
        <button
          type="button"
          className={`${styles.tarjeta} ${styles.tarjetaPrincipal}`}
          aria-pressed={!filtros.estado}
          title="Presentes y tardanzas cuentan como asistencia; las clases sin marcar no se cuentan"
          onClick={() => cambiar({ estado: "" })}
        >
          <strong>{porcentaje}%</strong>
          <span>Asistencia</span>
        </button>
        {estados.map(({ valor, plural }) => (
          <button
            key={valor}
            type="button"
            className={styles.tarjeta}
            aria-pressed={filtros.estado === valor}
            onClick={() => cambiar({ estado: filtros.estado === valor ? "" : valor })}
          >
            <strong className={totales[valor] ? styles[valor] : styles.cero}>{totales[valor]}</strong>
            <span>{plural}</span>
          </button>
        ))}
      </div>
      <div className={styles.lista}>
        {filas.length === 0 ? (
          <div className={styles.vacioFiltros}>
            <h3>Aún no hay asistencias en este periodo</h3>
            <p>Prueba con otro mes o quita los filtros.</p>
            {activos > 0 && (
              <button type="button" className={styles.accion} onClick={limpiar}>Limpiar filtros</button>
            )}
          </div>
        ) : (
          <table className={styles.tabla}>
            <thead>
              <tr>
                <th scope="col">Fecha</th>
                <th scope="col">Curso</th>
                <th scope="col">Hora</th>
                <th scope="col">Estado</th>
                <th scope="col">Justificación</th>
                {onJustify && <th scope="col">Acciones</th>}
              </tr>
            </thead>
            <tbody>
              {filas.slice(0, visibles).map((fila) => {
                const { etiqueta, tono } = presentarEstado(fila);
                const justificacion = presentarJustificacion(fila);
                const hora = horaDe(fila);
                return (
                  <tr
                    key={fila.id}
                    className={styles.filaClicable}
                    onClick={(event) => {
                      if (!(event.target as HTMLElement).closest("button")) setDetalle(fila);
                    }}
                  >
                    <td className={styles.celdaFecha}>
                      <button
                        type="button"
                        className={styles.verDetalle}
                        aria-label={`Ver detalle de ${fila.curso} del ${fechaConDia(fila.fechaRegistro)}`}
                        onClick={() => setDetalle(fila)}
                      >
                        {fechaConDia(fila.fechaRegistro)}
                      </button>
                    </td>
                    <td className={styles.celdaCurso}>
                      {fila.curso}
                      <span className={styles.horaMovil}> · {hora}</span>
                    </td>
                    <td className={styles.celdaHora}>{hora}</td>
                    <td className={styles.celdaEstado}>
                      <span title={estadoBase(fila) === "SIN_MARCAR" ? NOTA_SIN_MARCAR : (fila.comentario ?? undefined)}>
                        <StatusPill state={fila.estado} label={etiqueta} tone={tono} />
                      </span>
                    </td>
                    <td className={styles.celdaJustificacion} data-vacia={!justificacion}>
                      {justificacion ? (
                        <span title={justificacion.detalle ?? undefined}>
                          <StatusPill state={etiqueta} label={justificacion.etiqueta} tone={justificacion.tono} />
                        </span>
                      ) : (
                        "—"
                      )}
                    </td>
                    {onJustify && (
                      <td className={styles.celdaAcciones} data-vacia={!puedeJustificar(fila)}>
                        {puedeJustificar(fila) && (
                          <button
                            type="button"
                            className={styles.accion}
                            aria-label={`Justificar ${fila.curso} del ${fechaConDia(fila.fechaRegistro)}`}
                            onClick={() => onJustify(fila)}
                          >
                            Justificar
                          </button>
                        )}
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
        {detalle && (
          <RecordDetail
            fila={detalle}
            onClose={() => setDetalle(null)}
            onJustify={
              onJustify && puedeJustificar(detalle)
                ? () => {
                    setDetalle(null);
                    onJustify(detalle);
                  }
                : undefined
            }
          />
        )}
        {filas.length > visibles && (
          <button type="button" className={styles.verMas} onClick={() => setVisibles((valor) => valor + POR_TANDA)}>
            Ver más
          </button>
        )}
      </div>
    </>
  );
}

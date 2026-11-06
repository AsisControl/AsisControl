"use client";
import Link from "next/link";
import { useEffect, useState, type MouseEvent, type ReactNode } from "react";
import { message } from "@/core/api/client";
import { Dialog, Feedback } from "@/shared/ui";
import { fechaHora, fechaSesion, horaSesion, indicadorJustificacion } from "./lib";
import { listarCorrecciones } from "./services/asistencia-service";
import styles from "./attendance.module.css";
import type { AlumnoSesion, CorreccionAsistencia, SesionAsistencia } from "./types";

const icono = (ruta: ReactNode, size = 16) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {ruta}
  </svg>
);
export const ChevronIcon = () => icono(<path d="m9 18 6-6-6-6" />);
export const CheckIcon = () => icono(<path d="M20 6 9 17l-5-5" />);
export const ClockIcon = () => icono(<><circle cx="12" cy="12" r="10" /><path d="M12 6v6l4 2" /></>);
export const CrossIcon = () => icono(<path d="M18 6 6 18M6 6l12 12" />);
export const SearchIcon = () => icono(<><circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" /></>);
export const CalendarIcon = () => icono(<><rect x="3" y="4" width="18" height="18" rx="2" /><path d="M16 2v4M8 2v4M3 10h18" /></>);
export const AlertIcon = () => icono(<><path d="m21.7 18-8-14a2 2 0 0 0-3.4 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.7-3Z" /><path d="M12 9v4M12 17h.01" /></>, 13);

export function Breadcrumb({
  items,
  onNavigate,
}: {
  items: { etiqueta: string; href?: string }[];
  /** Permite confirmar la salida cuando hay cambios sin guardar. */
  onNavigate?: (href: string) => void;
}) {
  const navegar = (event: MouseEvent, href: string) => {
    if (!onNavigate) return;
    event.preventDefault();
    onNavigate(href);
  };
  return (
    <nav className={styles.breadcrumb} aria-label="Ruta de navegación">
      {items.map(({ etiqueta, href }, indice) => (
        <span key={etiqueta}>
          {indice > 0 && <ChevronIcon />}
          {href ? (
            <Link href={href} onClick={(event) => navegar(event, href)}>
              {etiqueta}
            </Link>
          ) : (
            <strong aria-current="page">{etiqueta}</strong>
          )}
        </span>
      ))}
    </nav>
  );
}

const fechaYHora = (sesion: SesionAsistencia) =>
  `${fechaSesion(sesion.fecha)} · ${horaSesion(sesion.horaInicio)}`;

/** Ruta, título y acción principal; sin `badge`, el estado y la fecha van en la tarjeta de resumen. */
export function SessionHeader({
  sesion,
  badge,
  accion,
  onNavigate,
}: {
  sesion: SesionAsistencia;
  badge?: ReactNode;
  accion?: ReactNode;
  onNavigate?: (href: string) => void;
}) {
  const historial = `/mis-cursos/${sesion.asignacionCursoId}/asistencia`;
  const curso = `${sesion.curso} - ${sesion.seccion}`;
  return (
    <>
      <Breadcrumb
        onNavigate={onNavigate}
        items={[
          { etiqueta: "Mis cursos", href: "/mis-cursos" },
          { etiqueta: curso, href: historial },
          { etiqueta: `Sesión ${fechaSesion(sesion.fecha).slice(5)}` },
        ]}
      />
      <div className={styles.encabezado}>
        <div>
          <div className={styles.titulo}>
            <h2>Asistencia: {curso}</h2>
            {badge}
          </div>
          {badge && <p className={styles.subtitulo}>{fechaYHora(sesion)}</p>}
        </div>
        {accion}
      </div>
    </>
  );
}

export function Buscador({ valor, onChange }: { valor: string; onChange: (valor: string) => void }) {
  return (
    <label className={styles.buscador}>
      <SearchIcon />
      <input
        type="search"
        value={valor}
        aria-label="Buscar estudiante"
        placeholder="Buscar por nombre o apellido"
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

/** Lista común: el encabezado queda fijo al desplazarse y cada vista aporta el control de estado. */
export function StudentsTable({
  alumnos,
  renderEstado,
  renderIndicador,
  claseFila,
}: {
  alumnos: AlumnoSesion[];
  renderEstado: (alumno: AlumnoSesion) => ReactNode;
  renderIndicador?: (alumno: AlumnoSesion) => ReactNode;
  claseFila?: (alumno: AlumnoSesion) => string | undefined;
}) {
  if (!alumnos.length) return <p className={styles.vacio}>No se encontraron estudiantes.</p>;
  return (
    <table className={styles.tablaAlumnos}>
      <thead>
        <tr>
          <th scope="col">Código</th>
          <th scope="col">Nombre completo</th>
          <th scope="col" className={styles.columnaEstado}>Asistencia</th>
        </tr>
      </thead>
      <tbody>
        {alumnos.map((alumno) => (
          <tr key={alumno.alumnoId} className={claseFila?.(alumno)}>
            <td className={styles.codigo}>{alumno.codigoAlumno}</td>
            <td>
              <span className={styles.nombre}>
                {alumno.nombreCompleto}
                {renderIndicador?.(alumno)}
              </span>
            </td>
            <td className={styles.columnaEstado}>{renderEstado(alumno)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function TotalesEstados({ alumnos }: { alumnos: { estado: string | null }[] }) {
  const contar = (estado: string) => alumnos.filter((alumno) => alumno.estado === estado).length;
  const total = (estado: string, etiqueta: string, clase: string) => {
    const cantidad = contar(estado);
    return (
      <span>
        <strong className={cantidad > 0 ? clase : undefined}>{cantidad}</strong> {etiqueta}
      </span>
    );
  };
  return (
    <div className={styles.totales}>
      {total("PRESENTE", "Presentes", styles.tomadas)}
      {total("TARDANZA", "Tardanzas", styles.borradores)}
      {total("INASISTENCIA", "Inasistencias", styles.inasistencias)}
    </div>
  );
}

/** Justificación separada del estado; solo abre el detalle si existe una justificación. */
export function JustificationIndicator({
  alumno,
  onOpen,
}: {
  alumno: AlumnoSesion;
  onOpen: (alumno: AlumnoSesion) => void;
}) {
  const sinSolicitud = alumno.justificacionId === null || alumno.estadoJustificacion === "CANCELADA";
  if (alumno.estado !== "INASISTENCIA" && (alumno.estado !== "TARDANZA" || sinSolicitud)) return null;
  const { etiqueta, tono } = indicadorJustificacion(alumno.estadoJustificacion);
  const clase = `${styles.indicador} ${styles[`indicador-${tono}`]}`;
  if (sinSolicitud) return <span className={clase}>{etiqueta}</span>;
  return (
    <button type="button" className={clase} onClick={() => onOpen(alumno)}>
      {etiqueta} <ChevronIcon />
    </button>
  );
}

/** Tarjeta única con el estado de la sesión, sus totales y quién la registró. */
export function SessionSummary({
  sesion,
  estado,
  totales,
  nota,
  registro,
}: {
  sesion: SesionAsistencia;
  estado: ReactNode;
  totales?: { estado: string | null }[];
  nota?: ReactNode;
  registro?: boolean;
}) {
  return (
    <div className={styles.resumenSesion}>
      <div className={styles.zonaSesion}>
        <span className={styles.etiquetaZona}>Sesión</span>
        {estado}
        <span className={styles.textoSuave}>{fechaYHora(sesion)}</span>
      </div>
      {totales && (
        <div className={styles.zonaTotales}>
          <TotalesEstados alumnos={totales} />
          {nota}
        </div>
      )}
      {registro && <AuditNote sesion={sesion} />}
    </div>
  );
}

/** Quién registró la asistencia y su última corrección; el motivo completo se ve al pasar el mouse. */
function AuditNote({ sesion }: { sesion: SesionAsistencia }) {
  const [correcciones, setCorrecciones] = useState<CorreccionAsistencia[]>([]);
  const [error, setError] = useState("");
  useEffect(() => {
    let activo = true;
    listarCorrecciones(sesion.id)
      .then((lista) => {
        if (activo) setCorrecciones(lista);
      })
      .catch((e) => {
        if (activo) setError(message(e));
      });
    return () => {
      activo = false;
    };
  }, [sesion.id, sesion.version]);
  const ultima = correcciones[0];
  return (
    <div className={styles.auditoria}>
      <span className={styles.etiquetaZona}>Registro</span>
      {sesion.registradoPor && sesion.registradoEn && (
        <p>Registrada por {sesion.registradoPor} · {fechaHora(sesion.registradoEn)}</p>
      )}
      {ultima && (
        <>
          <p>Corregida por {ultima.docente} · {fechaHora(ultima.fechaHora)}</p>
          <p className={styles.motivoAuditoria} title={ultima.motivo}>Motivo: {ultima.motivo}</p>
        </>
      )}
      <Feedback error={error} />
    </div>
  );
}

export function ExitDialog({
  onStay,
  onLeave,
}: {
  onStay: () => void;
  onLeave: () => void;
}) {
  return (
    <Dialog title="¿Salir sin guardar?" role="alertdialog" onClose={onStay}>
      <p>Los cambios nuevos se perderán. Tu último guardado se conserva.</p>
      <div className="form-actions">
        <button type="button" onClick={onStay}>Seguir editando</button>
        <button type="button" className="primary-action danger" onClick={onLeave}>
          Salir sin guardar
        </button>
      </div>
    </Dialog>
  );
}

"use client";

import Link from "next/link";
import { useEffect, useState, type ReactNode } from "react";
import { message } from "@/core/api/client";
import { obtenerPanelIndicadores, type PanelIndicadores } from "@/core/api/dashboard";
import type { AuthenticatedUser } from "@/core/auth/types";
import { canAccess } from "@/core/navigation/access-policy";
import { NavIcon } from "@/core/navigation/nav-icons";
import { navPath } from "@/core/navigation/navigation";
import {
  Feedback,
  HomeCard,
  HomeCardsSkeleton,
  HomeColumns,
  HomeIndicator,
  HomeIndicators,
  HomeIndicatorsSkeleton,
  Loading,
  PageHeader,
  Skeleton,
  type HomeTone,
} from "@/shared/ui";
import { DashboardIndicators } from "@/shared/ui/dashboard-indicators";
import { timeLabel } from "@/shared/lib/formatters";
import { fechaCorta, resumir, estadoBase, type AsistenciaEstudiante } from "@/modules/guardian-attendance";
import type { AvailableReport } from "@/modules/evaluations";
import { loadGuardianHome, loadHomeStudents, loadStudentHome, type GuardianHomeData, type HomeStudent, type StudentHomeData } from "./data";
import styles from "./role-home.module.css";

const weekdays = ["DOMINGO", "LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO"];
const homeDate = () => new Intl.DateTimeFormat("es-PE", { timeZone: "America/Lima", dateStyle: "full" }).format(new Date());
const today = () => new Intl.DateTimeFormat("en-CA", { timeZone: "America/Lima", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
const nowHour = () => new Intl.DateTimeFormat("en-GB", { timeZone: "America/Lima", hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date());
const month = () => today().slice(0, 7);
const periodKey = (report: AvailableReport) => `${report.anio}-${report.idPeriodoAcademico}`;
const latestReports = (reports: AvailableReport[]) => [...reports]
  .sort((a, b) => b.anio - a.anio || b.idPeriodoAcademico - a.idPeriodoAcademico)
  .filter((report, index, sorted) => sorted.findIndex((candidate) => candidate.curso === report.curso) === index)
  .slice(0, 4);

const TONES: Record<string, HomeTone> = { amber: "warning", blue: "primary", green: "success", purple: "info" };
const FAMILY_DESTINATIONS: Record<string, { icono: string; opcion?: string }> = {
  ESTUDIANTES_VINCULADOS: { icono: "Matrícula", opcion: "Matrícula" },
  TAREAS_PENDIENTES: { icono: "Tareas" },
  PAGOS_PENDIENTES: { icono: "Pagos", opcion: "Pagos" },
  ALERTAS_ACTIVAS: { icono: "Progreso" },
  NOTIFICACIONES_NO_LEIDAS: { icono: "Inicio" },
};

function Metric({ icon, value, label, tone, href }: { icon: string; value: string | number; label: string; tone: string; href: string }) {
  return <HomeIndicator icon={<NavIcon label={icon} />} value={value} label={label} tone={TONES[tone] ?? "primary"} href={href} />;
}

function Card({ title, action, children }: { title: string; action?: { label: string; href: string }; children: ReactNode }) {
  return <HomeCard as="section" title={title} action={action}>{children}</HomeCard>;
}

function Empty({ children }: { children: React.ReactNode }) {
  return <p className={styles.empty}>{children}</p>;
}

/** Mantiene la distribución de indicadores y tarjetas mientras llegan los datos. */
function HomeSkeleton({ guardian }: { guardian?: boolean }) {
  return <div aria-hidden="true">
    <HomeIndicatorsSkeleton />
    <HomeCardsSkeleton layout={guardian ? "equal" : "wide"} />
  </div>;
}

function ReportList({ reports }: { reports: AvailableReport[] }) {
  const latest = latestReports(reports);
  if (!latest.length) return <Empty>Aún no hay informes de progreso publicados.</Empty>;
  return <div className={styles.reportList}>{latest.map((report) =>
    <div className={styles.reportRow} key={`${report.idAsignacionCurso}-${periodKey(report)}`}>
      <div><strong>{report.curso}</strong><span>{report.periodo} · {report.anio}</span></div>
      <span className={styles.published}>Publicado</span>
    </div>)}</div>;
}

function AttendanceSummary({ rows }: { rows: AsistenciaEstudiante[] }) {
  const current = rows.filter((row) => row.fechaRegistro.startsWith(month()));
  const { totales, porcentaje } = resumir(current);
  return { current, totales, porcentaje };
}

export function StudentHome({ fullName }: { fullName: string }) {
  const [data, setData] = useState<StudentHomeData | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [updated, setUpdated] = useState("");
  function refresh() { setData(null); setError(""); setRevision((value) => value + 1); }
  useEffect(() => {
    let active = true;
    loadStudentHome().then((value) => { if (active) { setData(value); setError(""); setUpdated(timeLabel(new Date())); } })
      .catch((cause) => { if (active) setError(message(cause)); });
    return () => { active = false; };
  }, [revision]);
  const attendance = data ? AttendanceSummary({ rows: data.attendance }) : null;
  const weekday = weekdays[new Date(`${today()}T12:00:00`).getDay()];
  const classes = (data?.schedule ?? []).filter((block) => block.diaSemana === weekday)
    .sort((a, b) => a.horaInicio.localeCompare(b.horaInicio));
  const tasks = [...(data?.tasks.content ?? [])].sort((a, b) => a.tarea.fechaLimite.localeCompare(b.tarea.fechaLimite)).slice(0, 3);
  return <section>
    <PageHeader
      title={`Bienvenido, ${fullName}`}
      description={<>{homeDate()} · Tu actividad académica de un vistazo.{updated && <><br /><small className={styles.updated}>Última actualización: {updated}</small></>}</>}
      actions={<button type="button" onClick={refresh} disabled={!data && !error}>Actualizar</button>}
    />
    <Feedback error={error && `No se pudo cargar el panel. ${error}`} />
    {!data && !error && <Loading skeleton={<HomeSkeleton />}>Cargando tu panel…</Loading>}
    {data && <>
      <HomeIndicators>
        <Metric icon="Tareas" value={data.tasks.totalElements} label="Tareas pendientes" tone="amber" href="/tareas" />
        <Metric icon="Mis cursos" value={classes.length} label="Clases de hoy" tone="blue" href="/horario" />
        <Metric icon="Asistencia" value={attendance?.current.length ? `${attendance.porcentaje}%` : "—"} label="Asistencia mensual" tone="green" href="/asistencia" />
        <Metric icon="Progreso" value={data.reports.length} label="Informes publicados" tone="purple" href="/progreso" />
      </HomeIndicators>
      <HomeColumns layout="wide">
        <Card title="Clases de hoy" action={{ label: "Ver horario", href: "/horario" }}>
          {!classes.length ? <Empty>No hay clases programadas para hoy.</Empty> : classes.map((block) => {
            const active = nowHour() >= block.horaInicio.slice(0, 5) && nowHour() < block.horaFin.slice(0, 5);
            return <div className={`${styles.classRow} ${active ? styles.classActive : ""}`} key={block.id}>
              <div><strong>{block.curso}</strong><span>{block.docente} · {block.aula}</span></div>
              <div className={styles.classTime}><time>{block.horaInicio.slice(0, 5)}–{block.horaFin.slice(0, 5)}</time>{active && <em>En curso</em>}</div>
            </div>;
          })}
        </Card>
        <Card title="Tareas pendientes" action={{ label: "Ver todas", href: "/tareas" }}>
          {!tasks.length ? <Empty>Estás al día con tus tareas.</Empty> : tasks.map((item) => <div className={styles.task} key={item.tarea.id}>
            <span className={styles.subject}>{item.curso}</span><strong>{item.tarea.nombre}</strong>
            <span className={styles.due}>Entrega: {fechaCorta(item.tarea.fechaLimite.slice(0, 10))}</span>
            <Link href="/tareas">Ver detalle y entregar <span aria-hidden="true">→</span></Link>
          </div>)}
        </Card>
        <div className={styles.stack}>
          <Card title="Último progreso publicado" action={{ label: "Ver progreso", href: "/progreso" }}><ReportList reports={data.reports} /></Card>
          <Card title="Asistencia — mes actual" action={{ label: "Ver asistencia", href: "/asistencia" }}>
            {attendance?.current.length ? <div className={styles.attendanceStats}>
              <span><strong>{attendance.totales.PRESENTE}</strong>Presentes</span><span><strong>{attendance.totales.TARDANZA}</strong>Tardanzas</span><span><strong>{attendance.totales.INASISTENCIA}</strong>Inasistencias</span>
            </div> : <Empty>Aún no hay asistencias registradas este mes.</Empty>}
          </Card>
        </div>
      </HomeColumns>
    </>}
  </section>;
}

export function GuardianHome({ user, quickActions }: { user: AuthenticatedUser; quickActions?: ReactNode }) {
  const [students, setStudents] = useState<HomeStudent[] | null>(null);
  const [selected, setSelected] = useState("");
  const [data, setData] = useState<GuardianHomeData | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [updated, setUpdated] = useState("");
  const [summary, setSummary] = useState<PanelIndicadores | null>(null);
  const [summaryError, setSummaryError] = useState("");
  useEffect(() => {
    let active = true;
    obtenerPanelIndicadores().then((panel) => {
      if (!active) return;
      setSummary(panel);
      setSummaryError("");
    })
      .catch(() => { if (active) setSummaryError("No se pudo cargar el resumen familiar."); });
    return () => { active = false; };
  }, [revision]);
  function refresh() { setData(null); setError(""); setRevision((value) => value + 1); }
  useEffect(() => {
    let active = true;
    loadHomeStudents().then((items) => {
      if (!active) return;
      setStudents(items);
      setSelected((previous) => previous && items.some((item) => String(item.id) === previous) ? previous : String(items[0]?.id ?? ""));
      setError("");
    }).catch((cause) => { if (active) setError(message(cause)); });
    return () => { active = false; };
  }, [revision]);
  useEffect(() => {
    if (!selected) return;
    let active = true;
    loadGuardianHome(Number(selected)).then((value) => { if (active) { setData(value); setError(""); setUpdated(timeLabel(new Date())); } })
      .catch((cause) => { if (active) setError(message(cause)); });
    return () => { active = false; };
  }, [selected, revision]);
  const student = students?.find((item) => String(item.id) === selected);
  const unpaid = (data?.obligations ?? []).filter((item) => item.estadoAdministrativo === "ACTIVO" && item.saldo > 0)
    .sort((a, b) => a.fechaVencimiento.localeCompare(b.fechaVencimiento));
  const attendance = data ? AttendanceSummary({ rows: data.attendance }) : null;
  const missed = (data?.attendance ?? []).filter((row) => estadoBase(row) === "INASISTENCIA")
    .sort((a, b) => b.fechaRegistro.localeCompare(a.fechaRegistro)).slice(0, 3);
  const request = [...(data?.requests ?? [])].sort((a, b) => b.anioAcademico - a.anioAcademico || b.id - a.id)[0];
  return <section>
    <PageHeader
      title="Panel del Apoderado"
      description={updated
        ? <>Información de tus estudiantes asociados<br /><small className={styles.updated}>Última actualización: {updated}</small></>
        : "Información de tus estudiantes asociados"}
      actions={<>
        {!students && !error && <div aria-hidden="true"><Skeleton width={210} height={38} radius={8} /></div>}
        {students && students.length > 0 && <label className={styles.studentSelect}>Estudiante
          <select value={selected} onChange={(event) => { setData(null); setError(""); setSelected(event.target.value); }}>
            {students.map((item) => <option value={item.id} key={item.id}>{item.nombreCompleto}</option>)}
          </select>
        </label>}
        <button type="button" onClick={refresh} disabled={!data && !error && !summaryError}>Actualizar</button>
      </>}
    />
    <Feedback error={summaryError} />
    {!!summary?.indicadores.length && <section className={styles.familySummary} aria-label="Resumen familiar">
      <h2>Resumen familiar</h2>
      <DashboardIndicators indicadores={summary.indicadores} destino={(codigo) => {
        const { icono, opcion } = FAMILY_DESTINATIONS[codigo] ?? { icono: "Inicio" };
        return { icono, enlace: opcion && canAccess(user, opcion) ? navPath(opcion) : undefined };
      }} />
    </section>}
    <Feedback error={error && `No se pudo cargar el panel. ${error}`} />
    {students?.length === 0 && <Empty>No tienes estudiantes asociados. Consulta con la institución para revisar tu vínculo familiar.</Empty>}
    {!data && !error && students?.length !== 0 && <Loading skeleton={<HomeSkeleton guardian />}>
      {selected ? `Cargando información de ${student?.nombreCompleto ?? "tu estudiante"}…` : "Cargando estudiantes asociados…"}
    </Loading>}
    {data && <>
      <HomeIndicators>
        <Metric icon="Matrícula" value={request ? request.estado.replaceAll("_", " ").toLowerCase() : "Sin solicitud"} label="Estado de matrícula" tone="amber" href="/matricula" />
        <Metric icon="Pagos" value={unpaid[0] ? new Intl.NumberFormat("es-PE", { style: "currency", currency: "PEN", maximumFractionDigits: 0 }).format(unpaid[0].saldo) : "Al día"} label="Próximo vencimiento" tone="amber" href="/pagos" />
        <Metric icon="Asistencia" value={attendance?.current.length ? `${attendance.porcentaje}%` : "—"} label="Asistencia mensual" tone="blue" href="/asistencia" />
        <Metric icon="Progreso" value={data.reports.length ? "Disponible" : "Pendiente"} label="Progreso académico" tone="blue" href="/progreso" />
      </HomeIndicators>
      <HomeColumns layout="equal">
        <Card title="Próximos compromisos" action={{ label: "Ver pagos", href: "/pagos" }}>
          <p className={styles.studentName}>{student?.nombreCompleto}</p>
          {!unpaid.length ? <Empty>No hay compromisos pendientes.</Empty> : unpaid.slice(0, 3).map((item) => <div className={styles.obligation} key={item.id}>
            <strong>{item.concepto}</strong><span>Vence: {fechaCorta(item.fechaVencimiento)}</span>
            <div><b>{new Intl.NumberFormat("es-PE", { style: "currency", currency: "PEN" }).format(item.saldo)}</b><em>{item.estadoFinanciero.toLowerCase()}</em></div>
          </div>)}
        </Card>
        <Card title="Últimas inasistencias" action={{ label: "Ver asistencia", href: "/asistencia" }}>
          <p className={styles.studentName}>{student?.nombreCompleto}</p>
          {!missed.length ? <Empty>No hay inasistencias registradas.</Empty> : missed.map((row) => <div className={styles.absence} key={row.id}>
            <div><strong>{row.curso}</strong><span>{fechaCorta(row.fechaRegistro)}</span></div>
            <em>{row.estadoJustificacion === "APROBADA" ? "Justificada" : "Inasistencia"}</em>
          </div>)}
        </Card>
        <Card title="Último informe de progreso" action={{ label: "Ver informe", href: "/progreso" }}>
          <p className={styles.studentName}>{student?.nombreCompleto}</p><ReportList reports={data.reports} />
          {data.reports.length > 0 && <Link className={styles.primaryLink} href="/progreso">Ver informe completo</Link>}
        </Card>
      </HomeColumns>
    </>}
    {quickActions}
  </section>;
}

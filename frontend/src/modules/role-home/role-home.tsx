"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { NavIcon } from "@/core/navigation/nav-icons";
import { Loading, Skeleton } from "@/shared/ui";
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

function Metric({ icon, value, label, tone, href }: { icon: string; value: string | number; label: string; tone: string; href: string }) {
  return <Link href={href} className={`${styles.metric} ${styles[tone]}`}>
    <span className={styles.metricIcon}><NavIcon label={icon} /></span>
    <span className={styles.metricText}><strong>{value}</strong><span>{label}</span></span>
  </Link>;
}

function Card({ title, action, children }: { title: string; action?: { label: string; href: string }; children: React.ReactNode }) {
  return <section className={styles.card}>
    <div className={styles.cardHead}><h2>{title}</h2>{action && <Link href={action.href}>{action.label} <span aria-hidden="true">→</span></Link>}</div>
    {children}
  </section>;
}

function Empty({ children }: { children: React.ReactNode }) {
  return <p className={styles.empty}>{children}</p>;
}

/** Mantiene la distribución de indicadores y tarjetas mientras llegan los datos. */
function HomeSkeleton({ guardian }: { guardian?: boolean }) {
  const columns = guardian ? styles.guardianColumns : styles.studentColumns;
  return <div aria-hidden="true">
    <div className={styles.metrics}>
      {[0, 1, 2, 3].map((item) => <div className={styles.metric} key={item}>
        <Skeleton width={42} height={42} radius={9} />
        <div className={styles.skeletonText}><Skeleton width={72} height={25} /><Skeleton width={120} height={12} /></div>
      </div>)}
    </div>
    <div className={columns}>
      {[0, 1, 2].map((card) => <div className={styles.card} key={card}>
        <Skeleton width="62%" height={18} />
        <div className={styles.skeletonRows}>
          {[0, 1, 2].map((row) => <div className={styles.skeletonRow} key={row}>
            <Skeleton width={row === 1 ? "54%" : "72%"} height={14} />
            <Skeleton width="42%" height={11} />
          </div>)}
        </div>
      </div>)}
    </div>
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
  function refresh() { setData(null); setError(""); setRevision((value) => value + 1); }
  useEffect(() => {
    let active = true;
    loadStudentHome().then((value) => { if (active) { setData(value); setError(""); } })
      .catch((cause) => { if (active) setError(message(cause)); });
    return () => { active = false; };
  }, [revision]);
  const attendance = data ? AttendanceSummary({ rows: data.attendance }) : null;
  const weekday = weekdays[new Date(`${today()}T12:00:00`).getDay()];
  const classes = (data?.schedule ?? []).filter((block) => block.diaSemana === weekday)
    .sort((a, b) => a.horaInicio.localeCompare(b.horaInicio));
  const tasks = [...(data?.tasks.content ?? [])].sort((a, b) => a.tarea.fechaLimite.localeCompare(b.tarea.fechaLimite)).slice(0, 3);
  return <section className={styles.home}>
    <header className={styles.header}>
      <div><h1>Bienvenido, {fullName}</h1><p>{homeDate()}</p><span>Tu actividad académica de un vistazo.</span></div>
      <button type="button" onClick={refresh} disabled={!data && !error}>Actualizar</button>
    </header>
    {error && <div className={styles.error} role="alert">No se pudo cargar el panel. {error} <button onClick={refresh}>Reintentar</button></div>}
    {!data && !error && <Loading skeleton={<HomeSkeleton />}>Cargando tu panel…</Loading>}
    {data && <>
      <div className={styles.metrics}>
        <Metric icon="Tareas" value={data.tasks.totalElements} label="Tareas pendientes" tone="amber" href="/tareas" />
        <Metric icon="Mis cursos" value={classes.length} label="Clases de hoy" tone="blue" href="/horario" />
        <Metric icon="Asistencia" value={attendance?.current.length ? `${attendance.porcentaje}%` : "—"} label="Asistencia mensual" tone="green" href="/asistencia" />
        <Metric icon="Progreso" value={data.reports.length} label="Informes publicados" tone="purple" href="/progreso" />
      </div>
      <div className={styles.studentColumns}>
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
      </div>
    </>}
  </section>;
}

export function GuardianHome() {
  const [students, setStudents] = useState<HomeStudent[] | null>(null);
  const [selected, setSelected] = useState("");
  const [data, setData] = useState<GuardianHomeData | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
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
    loadGuardianHome(Number(selected)).then((value) => { if (active) { setData(value); setError(""); } })
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
  return <section className={styles.home}>
    <header className={styles.header}>
      <div><h1>Panel del Apoderado</h1><p>Información de tus estudiantes asociados</p></div>
      {!students && !error && <div aria-hidden="true"><Skeleton width={210} height={38} radius={8} /></div>}
      {students && students.length > 0 && <label className={styles.studentSelect}>Estudiante
        <select value={selected} onChange={(event) => { setData(null); setError(""); setSelected(event.target.value); }}>
          {students.map((item) => <option value={item.id} key={item.id}>{item.nombreCompleto}</option>)}
        </select>
      </label>}
    </header>
    {error && <div className={styles.error} role="alert">No se pudo cargar el panel. {error} <button onClick={refresh}>Reintentar</button></div>}
    {students?.length === 0 && <Empty>No tienes estudiantes asociados. Consulta con la institución para revisar tu vínculo familiar.</Empty>}
    {!data && !error && students?.length !== 0 && <Loading skeleton={<HomeSkeleton guardian />}>
      {selected ? `Cargando información de ${student?.nombreCompleto ?? "tu estudiante"}…` : "Cargando estudiantes asociados…"}
    </Loading>}
    {data && <>
      <div className={styles.metrics}>
        <Metric icon="Matrícula" value={request ? request.estado.replaceAll("_", " ").toLowerCase() : "Sin solicitud"} label="Estado de matrícula" tone="amber" href="/matricula" />
        <Metric icon="Pagos" value={unpaid[0] ? new Intl.NumberFormat("es-PE", { style: "currency", currency: "PEN", maximumFractionDigits: 0 }).format(unpaid[0].saldo) : "Al día"} label="Próximo vencimiento" tone="amber" href="/pagos" />
        <Metric icon="Asistencia" value={attendance?.current.length ? `${attendance.porcentaje}%` : "—"} label="Asistencia mensual" tone="blue" href="/asistencia" />
        <Metric icon="Progreso" value={data.reports.length ? "Disponible" : "Pendiente"} label="Progreso académico" tone="blue" href="/progreso" />
      </div>
      <div className={styles.guardianColumns}>
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
      </div>
    </>}
  </section>;
}

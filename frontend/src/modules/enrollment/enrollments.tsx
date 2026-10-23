"use client";
import { useEffect, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { api, message } from "@/core/api/client";
import {
  Dialog,
  Feedback,
  Field,
  PageHeader,
  Pager,
  PanelHeading,
  PersonCell,
  StatusPill,
  TabNav,
  type TabItem,
} from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import {
  countLabel,
  dateLabel,
  shortDateLabel,
  stateLabel,
  timeLabel,
} from "@/shared/lib/formatters";
import type { Persona } from "@/shared/types/person";
import { Students, type Student } from "@/modules/students";
import type { AcademicYear } from "@/modules/academic-config";
import type { History } from "./types";
type EnrollmentRecord = {
  id: number;
  alumnoId: number;
  codigoAlumno: string;
  alumno: string;
  seccionId: number | null;
  seccion: string;
  grado: number;
  anioAcademico: number;
  estado: string;
  version: number;
  codigoMatricula?: string;
  fechaMatricula?: string;
  fechaEnvio?: string | null;
  fechaRevision?: string | null;
  observaciones?: string | null;
  motivoRechazo?: string | null;
};
type Guardian = { id: number; persona: Persona; activo: boolean };
export const requestTransitions: Record<string, string[]> = {
  BORRADOR: ["ENVIADA", "CANCELADA"],
  ENVIADA: ["EN_REVISION", "CANCELADA"],
  EN_REVISION: ["OBSERVADA", "RECHAZADA"],
  OBSERVADA: ["ENVIADA", "CANCELADA"],
};
const actionLabels: Record<string, string> = {
  ENVIADA: "Enviar solicitud",
  EN_REVISION: "Iniciar revisión",
  OBSERVADA: "Observar",
  RECHAZADA: "Rechazar",
  CANCELADA: "Cancelar solicitud",
  MATRICULAR: "Finalizar matrícula",
  EDITAR: "Editar solicitud / sección",
  RETIRADA: "Retirar",
  FINALIZADA: "Finalizar año",
  ANULADA: "Anular matrícula",
};
const ENROLLMENT_TABS: TabItem[] = [
  { id: "solicitudes", label: "Solicitudes" },
  { id: "matriculas", label: "Matrículas" },
  { id: "fichas", label: "Fichas de estudiantes" },
];
export const ENROLLMENT_PATH = "/matricula";
const DEFAULT_TAB = "solicitudes";
/** Gestión de Matrícula del administrador; la pestaña sale de la URL: /matricula/{id}. */
export function Enrollments({ tab = DEFAULT_TAB }: { tab?: string }) {
  const router = useRouter();
  const current = ENROLLMENT_TABS.find((item) => item.id === tab);
  useEffect(() => {
    if (!current) router.replace(`${ENROLLMENT_PATH}/${DEFAULT_TAB}`);
  }, [current, router]);
  if (!current) return null;
  return (
    <section>
      <PageHeader
        title="Gestión de Matrícula"
        description="Solicitudes, asignación de vacantes y matrículas. Las fichas conservan los datos del estudiante."
      />
      <TabNav
        label="Secciones de matrícula"
        tabs={ENROLLMENT_TABS}
        current={current.id}
        onSelect={(id) => router.push(`${ENROLLMENT_PATH}/${id}`)}
      />
      {current.id === "fichas" ? (
        <Students />
      ) : (
        <EnrollmentList
          key={current.id}
          isRequest={current.id === DEFAULT_TAB}
        />
      )}
    </section>
  );
}
function EnrollmentList({ isRequest }: { isRequest: boolean }) {
  const path = isRequest ? "/api/solicitudes-matricula" : "/api/matriculas";
  const list = usePaged<EnrollmentRecord>(path);
  const [creating, setCreating] = useState(false);
  const [selected, setSelected] = useState<EnrollmentRecord | null>(null);
  const [success, setSuccess] = useState("");
  const total = list.data?.totalElements ?? 0;
  return (
    <section className="panel">
      <PanelHeading
        title={isRequest ? "Solicitudes de matrícula" : "Matrículas registradas"}
        summary={
          <>
            {list.data
              ? isRequest
                ? countLabel(total, "solicitud", "solicitudes")
                : countLabel(total, "matrícula", "matrículas")
              : "Registros del año académico"}
            {list.updatedAt &&
              ` · Última actualización: ${timeLabel(list.updatedAt)}`}
          </>
        }
        actions={
          <>
            <button disabled={list.loading} onClick={list.refresh}>
              Actualizar
            </button>
            {isRequest && (
              <button
                className="primary-action"
                onClick={() => {
                  setSuccess("");
                  setCreating(true);
                }}
              >
                + Nueva solicitud
              </button>
            )}
          </>
        }
      />
      <Feedback error={list.error} success={success} />
      {list.loading ? (
        <p role="status">Cargando registros…</p>
      ) : list.data?.content.length ? (
        <div className="user-table-wrap">
          <table className="user-table">
            <thead>
              <tr>
                <th>Código</th>
                <th>Estudiante</th>
                <th>Grado / Sección</th>
                <th>{isRequest ? "Fecha de envío" : "Fecha de matrícula"}</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {list.data.content.map((record) => (
                <tr key={record.id}>
                  <td data-label="Código" className="muted-cell">
                    {record.codigoMatricula ?? record.codigoAlumno}
                  </td>
                  <td data-label="Estudiante">
                    <PersonCell
                      name={record.alumno}
                      detail={record.codigoMatricula ? record.codigoAlumno : undefined}
                    />
                  </td>
                  <td data-label="Grado / Sección">
                    <strong className="grade-cell">
                      {record.grado}° {record.seccion}
                    </strong>
                    <small>Año {record.anioAcademico}</small>
                  </td>
                  <td data-label="Fecha">
                    {shortDateLabel(record.fechaMatricula || record.fechaEnvio)}
                  </td>
                  <td data-label="Estado">
                    <StatusPill state={record.estado} />
                  </td>
                  <td data-label="Acciones">
                    <button onClick={() => setSelected(record)}>
                      Ver detalle
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        !list.error && (
          <p className="empty">
            No hay {isRequest ? "solicitudes" : "matrículas"} registradas.
          </p>
        )
      )}
      <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
      {creating && (
        <NewRequest
          onClose={() => setCreating(false)}
          onCreated={(record) => {
            setCreating(false);
            setSelected(record);
            list.refresh();
            setSuccess("Solicitud guardada en borrador.");
          }}
        />
      )}
      {selected && (
        <EnrollmentDetail
          initial={selected}
          path={path}
          isRequest={isRequest}
          onClose={() => {
            setSelected(null);
            list.refresh();
          }}
          onChanged={list.refresh}
        />
      )}
    </section>
  );
}
function StudentPicker({
  onSelect,
  selected,
}: {
  onSelect: (student: Student) => void;
  selected: Student | null;
}) {
  const [search, setSearch] = useState("");
  const students = usePaged<Student>("/api/alumnos", search);
  return (
    <div className="picker-card">
      <h4>Estudiante</h4>
      <form
        className="toolbar"
        onSubmit={(event) => {
          event.preventDefault();
          setSearch(
            String(new FormData(event.currentTarget).get("search") || ""),
          );
          students.setPage(0);
        }}
      >
        <Field label="Buscar por nombre, documento o código" name="search" />
        <button>Buscar</button>
      </form>
      <Feedback error={students.error} />
      {selected && (
        <p className="selected-person">
          Seleccionado: <strong>{selected.persona.nombreCompleto}</strong> ·{" "}
          {selected.codigoAlumno}
        </p>
      )}
      {students.loading ? (
        <p role="status">Cargando estudiantes…</p>
      ) : (
        <ul className="choice-list">
          {students.data?.content.map((student) => (
            <li key={student.id}>
              <span>
                {student.persona.nombreCompleto}
                <small>
                  {student.codigoAlumno} · {student.persona.numeroDocumento}
                </small>
              </span>
              <button
                disabled={!student.activo || selected?.id === student.id}
                onClick={() => onSelect(student)}
              >
                {selected?.id === student.id ? "Seleccionado" : "Elegir"}
              </button>
            </li>
          ))}
        </ul>
      )}
      <Pager
        data={students.data}
        loading={students.loading}
        setPage={students.setPage}
      />
    </div>
  );
}
function NewRequest({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (record: EnrollmentRecord) => void;
}) {
  const [student, setStudent] = useState<Student | null>(null);
  const years = usePaged<AcademicYear>("/api/configuracion-academica/anios");
  const [yearId, setYearId] = useState(0);
  const [grade, setGrade] = useState(0);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const year =
    years.data?.content.find((value) => value.id === yearId) ||
    years.data?.content.find((value) => value.estado === "ACTIVO");
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!student || !year) return;
    const form = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    try {
      const record = await api<EnrollmentRecord>("/api/solicitudes-matricula", {
        method: "POST",
        body: JSON.stringify({
          alumnoId: student.id,
          seccionId: Number(form.get("seccionId")),
          observaciones: String(form.get("observaciones") || "").trim() || null,
        }),
      });
      onCreated(record);
    } catch (error) {
      setError(message(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Dialog
      title="Nueva solicitud de matrícula"
      description="Elige al estudiante y la sección solicitada del año activo."
      size="lg"
      onClose={onClose}
      busy={busy}
    >
      <Feedback error={error || years.error} />
      <StudentPicker selected={student} onSelect={setStudent} />
      <form onSubmit={save}>
        <div className="form-grid">
          <label className="field">
            Año académico activo *
            <select
              value={year?.id || ""}
              onChange={(event) => {
                setYearId(Number(event.target.value));
                setGrade(0);
              }}
              required
            >
              <option value="" disabled>
                Seleccionar año
              </option>
              {years.data?.content
                .filter((value) => value.estado === "ACTIVO")
                .map((value) => (
                  <option key={value.id} value={value.id}>
                    {value.anio}
                  </option>
                ))}
            </select>
          </label>
          <label className="field">
            Grado *
            <select
              required
              value={grade || ""}
              onChange={(event) => setGrade(Number(event.target.value))}
            >
              <option value="" disabled>
                Seleccionar grado
              </option>
              {year?.grados
                .filter((value) => value.activo)
                .map((value) => (
                  <option key={value.id} value={value.numero}>
                    {value.numero}° de secundaria
                  </option>
                ))}
            </select>
          </label>
          <label className="field">
            Sección solicitada *
            <select
              key={`${year?.id}-${grade}`}
              name="seccionId"
              required
              defaultValue=""
            >
              <option value="" disabled>
                Seleccionar sección
              </option>
              {year?.secciones
                .filter((value) => value.activo && value.grado === grade)
                .map((value) => (
                  <option key={value.id} value={value.id}>
                    {value.nombre} · {value.vacantes} vacantes /{" "}
                    {value.capacidadMaxima}
                  </option>
                ))}
            </select>
          </label>
          <label className="field full-field">
            Observaciones
            <textarea name="observaciones" maxLength={1000} rows={3} />
          </label>
        </div>
        <p className="hint">
          La solicitud se guarda en borrador. Las vacantes se verifican
          nuevamente al finalizar la matrícula.
        </p>
        {!year && !years.loading && (
          <p className="form-error">
            Configure y active un año académico en Administración.
          </p>
        )}
        <Pager
          data={years.data}
          loading={years.loading}
          setPage={years.setPage}
        />
        <div className="form-actions">
          <button type="button" disabled={busy} onClick={onClose}>
            Cancelar
          </button>
          <button
            className="primary-action"
            disabled={busy || !student || !year}
          >
            {busy ? "Guardando…" : "Guardar solicitud"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
function EnrollmentDetail({
  initial,
  path,
  isRequest,
  onClose,
  onChanged,
}: {
  initial: EnrollmentRecord;
  path: string;
  isRequest: boolean;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [record, setRecord] = useState(initial);
  const [history, setHistory] = useState<History[] | null>(null);
  const [year, setYear] = useState<AcademicYear | null>(null);
  const [action, setAction] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  useEffect(() => {
    let active = true;
    Promise.all([
      api<History[]>(`${path}/${record.id}/historial`),
      api<AcademicYear>(
        `/api/configuracion-academica/anios/${record.anioAcademico}`,
      ),
    ])
      .then(([events, config]) => {
        if (active) {
          setHistory(events);
          setYear(config);
        }
      })
      .catch((e) => {
        if (active) setError(message(e));
      });
    return () => {
      active = false;
    };
  }, [record, path]);
  async function reload() {
    setError("");
    try {
      setRecord(await api<EnrollmentRecord>(`${path}/${record.id}`));
      onChanged();
    } catch (error) {
      setError(message(error));
    }
  }
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const reason = String(form.get("motivo") || "").trim();
    setBusy(true);
    setError("");
    setSuccess("");
    try {
      if (action === "MATRICULAR") {
        const result = await api<EnrollmentRecord>("/api/matriculas", {
          method: "POST",
          body: JSON.stringify({
            solicitudMatriculaId: record.id,
            versionSolicitud: record.version,
          }),
        });
        setSuccess(
          `Matrícula ${result.codigoMatricula} creada. Consulta su gestión en la pestaña Matrículas.`,
        );
        setRecord(await api<EnrollmentRecord>(`${path}/${record.id}`));
      } else {
        const body = isRequest
          ? {
              estado: action === "EDITAR" ? record.estado : action,
              seccionId:
                action === "EDITAR" ? Number(form.get("seccionId")) : null,
              observaciones:
                action === "EDITAR"
                  ? String(form.get("observaciones") || "").trim()
                  : ["OBSERVADA", "CANCELADA"].includes(action)
                    ? reason
                    : record.observaciones,
              motivoRechazo: action === "RECHAZADA" ? reason : null,
              version: record.version,
            }
          : { estado: action, motivo: reason, version: record.version };
        setRecord(
          await api<EnrollmentRecord>(`${path}/${record.id}`, {
            method: "PUT",
            body: JSON.stringify(body),
          }),
        );
        setSuccess("Cambio registrado con historial.");
      }
      setAction("");
      onChanged();
    } catch (error) {
      setError(message(error));
    } finally {
      setBusy(false);
    }
  }
  const editable =
    isRequest &&
    ["BORRADOR", "OBSERVADA", "EN_REVISION"].includes(record.estado);
  const actions = isRequest
    ? requestTransitions[record.estado] || []
    : record.estado === "ACTIVA"
      ? ["RETIRADA", "FINALIZADA", "ANULADA"]
      : [];
  return (
    <Dialog
      title={isRequest ? "Gestionar solicitud" : "Gestionar matrícula"}
      description={`${record.alumno} · ${record.codigoAlumno}`}
      size="lg"
      onClose={onClose}
      busy={busy}
    >
      <Feedback error={error} success={success} />
      <dl className="detail-grid">
        <div>
          <dt>Estudiante</dt>
          <dd>
            {record.alumno}
            <small>{record.codigoAlumno}</small>
          </dd>
        </div>
        <div>
          <dt>Asignación</dt>
          <dd>
            {record.anioAcademico} · {record.grado}° {record.seccion}
          </dd>
        </div>
        <div>
          <dt>Estado</dt>
          <dd>
            <StatusPill state={record.estado} />
          </dd>
        </div>
        <div>
          <dt>{isRequest ? "Última revisión" : "Matrícula"}</dt>
          <dd>
            {isRequest
              ? dateLabel(record.fechaRevision || null)
              : record.codigoMatricula}
          </dd>
        </div>
      </dl>
      {record.observaciones && (
        <p className="dialog-note">
          <strong>Observaciones:</strong> {record.observaciones}
        </p>
      )}
      {record.motivoRechazo && (
        <p className="dialog-note dialog-note-danger">
          <strong>Motivo de rechazo:</strong> {record.motivoRechazo}
        </p>
      )}
      <div className="row-actions">
        <button disabled={busy} onClick={reload}>
          Actualizar datos
        </button>
        {editable && (
          <button
            disabled={busy}
            onClick={() => {
              setAction("EDITAR");
              setError("");
            }}
          >
            Editar sección / observaciones
          </button>
        )}
        {actions.map((value) => (
          <button
            key={value}
            disabled={busy}
            onClick={() => {
              setAction(value);
              setError("");
            }}
          >
            {actionLabels[value]}
          </button>
        ))}
        {isRequest && record.estado === "EN_REVISION" && (
          <button
            className="primary-action"
            disabled={busy}
            onClick={() => {
              setAction("MATRICULAR");
              setError("");
            }}
          >
            Finalizar matrícula
          </button>
        )}
      </div>
      {action && (
        <form key={action} className="action-card" onSubmit={save}>
          <h4>{actionLabels[action]}</h4>
          {action === "EDITAR" ? (
            <>
              <label className="field">
                Sección del mismo grado y año *
                <select
                  name="seccionId"
                  required
                  defaultValue={record.seccionId || ""}
                >
                  <option value="" disabled>
                    Seleccionar sección
                  </option>
                  {year?.secciones
                    .filter(
                      (value) => value.activo && value.grado === record.grado,
                    )
                    .map((value) => (
                      <option key={value.id} value={value.id}>
                        {value.nombre} · {value.vacantes} vacantes
                      </option>
                    ))}
                </select>
              </label>
              <label className="field">
                Observaciones
                <textarea
                  name="observaciones"
                  maxLength={1000}
                  rows={3}
                  defaultValue={record.observaciones || ""}
                />
              </label>
            </>
          ) : [
              "OBSERVADA",
              "RECHAZADA",
              "CANCELADA",
              "RETIRADA",
              "FINALIZADA",
              "ANULADA",
            ].includes(action) ? (
            <label className="field">
              {action === "OBSERVADA" ? "Detalle a subsanar" : "Motivo"} *
              <textarea
                name="motivo"
                required
                maxLength={action === "OBSERVADA" ? 1000 : 700}
                rows={3}
              />
            </label>
          ) : (
            <p>
              {action === "MATRICULAR"
                ? "Se creará la matrícula y finalizará la solicitud en una sola operación. El servidor verifica la última vacante disponible."
                : "Confirma el cambio de estado de la solicitud."}
            </p>
          )}
          <div className="form-actions">
            <button type="button" disabled={busy} onClick={() => setAction("")}>
              Volver
            </button>
            <button
              className="primary-action"
              disabled={busy || (action === "EDITAR" && !year)}
            >
              {busy ? "Guardando…" : "Confirmar"}
            </button>
          </div>
        </form>
      )}
      <GuardianLinks studentId={record.alumnoId} />
      <h4>Historial de cambios</h4>
      {history ? (
        history.length ? (
          <ol className="history-list">
            {history.map((item) => (
              <li key={item.id}>
                <strong>{stateLabel(item.accion)}</strong>
                <small>
                  {dateLabel(item.fecha)} · {item.actor} · {item.rol}
                </small>
                {item.anterior && <p>Anterior: {item.anterior}</p>}
                {item.nuevo && <p>Nuevo: {item.nuevo}</p>}
                {item.motivo && <p>Motivo: {item.motivo}</p>}
              </li>
            ))}
          </ol>
        ) : (
          <p className="empty">Sin cambios registrados.</p>
        )
      ) : (
        <p role="status">Cargando historial…</p>
      )}
    </Dialog>
  );
}
function GuardianLinks({ studentId }: { studentId: number }) {
  const [student, setStudent] = useState<Student | null>(null);
  const [search, setSearch] = useState("");
  const guardians = usePaged<Guardian>("/api/apoderados", search);
  const [choice, setChoice] = useState<Guardian | null>(null);
  const [adding, setAdding] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    api<Student>(`/api/alumnos/${studentId}`)
      .then((value) => {
        if (active) setStudent(value);
      })
      .catch((e) => {
        if (active) setError(message(e));
      });
    return () => {
      active = false;
    };
  }, [studentId]);
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!choice) return;
    const form = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    try {
      await api(`/api/alumnos/${studentId}/apoderados`, {
        method: "POST",
        body: JSON.stringify({
          apoderadoId: choice.id,
          parentesco: form.get("parentesco"),
          principal: form.get("principal") === "on",
        }),
      });
      setStudent(await api<Student>(`/api/alumnos/${studentId}`));
      setAdding(false);
      setChoice(null);
    } catch (error) {
      setError(message(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="guardian-section">
      <div className="section-heading">
        <h4>Apoderados asociados</h4>
        <button
          type="button"
          disabled={busy}
          onClick={() => setAdding((value) => !value)}
        >
          {adding ? "Cerrar asociación" : "Asociar apoderado"}
        </button>
      </div>
      <Feedback error={error} />
      {!student ? (
        <p role="status">Cargando vínculos…</p>
      ) : student.apoderados.length ? (
        <ul className="choice-list">
          {student.apoderados.map((guardian) => (
            <li key={guardian.id}>
              <span>
                {guardian.nombreCompleto}
                <small>
                  {stateLabel(guardian.parentesco)}
                  {guardian.principal ? " · Principal" : ""}
                </small>
              </span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="empty">Sin apoderados asociados.</p>
      )}
      {adding && (
        <div className="action-card">
          <p className="hint">
            Selecciona un apoderado existente. Para registrar uno nuevo, utiliza
            Administración → Usuarios → Crear acceso.
          </p>
          <form
            className="toolbar"
            onSubmit={(event) => {
              event.preventDefault();
              setSearch(
                String(new FormData(event.currentTarget).get("search") || ""),
              );
              guardians.setPage(0);
            }}
          >
            <Field label="Buscar apoderado" name="search" />
            <button>Buscar</button>
          </form>
          <Feedback error={guardians.error} />
          {guardians.loading ? (
            <p role="status">Cargando apoderados…</p>
          ) : (
            <ul className="choice-list">
              {guardians.data?.content.map((guardian) => {
                const linked = student?.apoderados.some(
                  (value) => value.apoderadoId === guardian.id,
                );
                return (
                  <li key={guardian.id}>
                    <span>
                      {guardian.persona.nombreCompleto}
                      <small>{guardian.persona.numeroDocumento}</small>
                    </span>
                    <button
                      disabled={
                        linked || choice?.id === guardian.id || !guardian.activo
                      }
                      onClick={() => setChoice(guardian)}
                    >
                      {linked
                        ? "Ya asociado"
                        : choice?.id === guardian.id
                          ? "Seleccionado"
                          : "Elegir"}
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
          <Pager
            data={guardians.data}
            loading={guardians.loading}
            setPage={guardians.setPage}
          />
          {choice && (
            <form onSubmit={save}>
              <p>
                Apoderado seleccionado:{" "}
                <strong>{choice.persona.nombreCompleto}</strong>
              </p>
              <label className="field">
                Parentesco *
                <select name="parentesco" defaultValue="NO_DECLARADO">
                  {[
                    "PADRE",
                    "MADRE",
                    "TUTOR_LEGAL",
                    "OTRO_FAMILIAR",
                    "NO_DECLARADO",
                  ].map((value) => (
                    <option key={value} value={value}>
                      {stateLabel(value)}
                    </option>
                  ))}
                </select>
              </label>
              <label className="check-field">
                <input type="checkbox" name="principal" /> Apoderado principal
              </label>
              <div className="form-actions">
                <button className="primary-action" disabled={busy}>
                  {busy ? "Asociando…" : "Guardar vínculo"}
                </button>
              </div>
            </form>
          )}
        </div>
      )}
    </div>
  );
}

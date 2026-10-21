"use client";
import { useEffect, useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { Dialog, Feedback, Pager } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { dateLabel, stateLabel } from "@/shared/lib/formatters";
type Child = { id: number; codigoAlumno: string; nombreCompleto: string };
type Offer = { anio: number; grados: number[] };
type Request = {
  id: number;
  alumnoId: number;
  alumno: string;
  codigoAlumno: string;
  anioAcademico: number;
  grado: number;
  seccion: string;
  estado: string;
  version: number;
  fechaEnvio: string | null;
  fechaRevision: string | null;
  observaciones: string | null;
  motivoRechazo: string | null;
};
export function GuardianEnrollment() {
  const list = usePaged<Request>("/api/solicitudes-matricula");
  const [students, setStudents] = useState<Child[] | null>(null);
  const [offers, setOffers] = useState<Offer[]>([]);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [creating, setCreating] = useState(false);
  const [year, setYear] = useState("");
  const [busy, setBusy] = useState(false);
  const [selected, setSelected] = useState<Request | null>(null);
  const [action, setAction] = useState("");
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    Promise.all([
      api<Child[]>("/api/familia/estudiantes"),
      api<Offer[]>("/api/familia/oferta"),
    ])
      .then(([children, choices]) => {
        if (active) {
          setStudents(children);
          setOffers(choices);
          setError("");
        }
      })
      .catch((e) => {
        if (active) setError(message(e));
      });
    return () => {
      active = false;
    };
  }, [reload]);
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const form = new FormData(event.currentTarget);
    try {
      const value = await api<Request>("/api/solicitudes-matricula", {
        method: "POST",
        body: JSON.stringify({
          alumnoId: Number(form.get("student")),
          anioAcademico: Number(year),
          grado: Number(form.get("grade")),
          observaciones: form.get("observaciones") || null,
        }),
      });
      setCreating(false);
      setSelected(value);
      setAction("");
      setSuccess(
        "Borrador guardado. Revisa los datos y envía la solicitud para que la administración la evalúe.",
      );
      list.refresh();
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  async function update(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) return;
    const form = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    setSuccess("");
    try {
      const value = await api<Request>(
        `/api/solicitudes-matricula/${selected.id}`,
        {
          method: "PUT",
          body: JSON.stringify({
            estado: action === "EDITAR" ? selected.estado : action,
            observaciones:
              action === "EDITAR" || action === "CANCELADA"
                ? String(form.get("observaciones") || "").trim()
                : selected.observaciones,
            version: selected.version,
          }),
        },
      );
      setSelected(value);
      setAction("");
      setSuccess(
        action === "ENVIADA"
          ? "Solicitud enviada. La administración revisará tus datos y asignará la sección según las vacantes."
          : "Cambio guardado.",
      );
      list.refresh();
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  async function refreshSelected() {
    if (!selected) return;
    setBusy(true);
    try {
      setSelected(
        await api<Request>(`/api/solicitudes-matricula/${selected.id}`),
      );
      list.refresh();
      setError("");
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  const editable =
    selected && ["BORRADOR", "OBSERVADA"].includes(selected.estado);
  const cancellable =
    selected && ["BORRADOR", "ENVIADA", "OBSERVADA"].includes(selected.estado);
  return (
    <section>
      <div className="management-heading">
        <div>
          <p className="eyebrow">MATRÍCULA · APODERADO</p>
          <h2>Solicitudes de mis estudiantes</h2>
          <p>
            Solicita año y grado. La administración revisa la solicitud y asigna
            la sección. Enviar una solicitud no reserva una vacante.
          </p>
        </div>
        <div className="row-actions">
          <button
            disabled={busy}
            onClick={() => {
              list.refresh();
              setReload((v) => v + 1);
            }}
          >
            Actualizar
          </button>
          <button
            className="primary-action"
            disabled={busy || !students?.length || !offers.length}
            onClick={() => {
              setCreating(true);
              setYear("");
              setError("");
              setSuccess("");
            }}
          >
            Nueva solicitud
          </button>
        </div>
      </div>
      <Feedback error={error || list.error} success={success} />
      {!students && !error && (
        <p role="status">Cargando estudiantes asociados…</p>
      )}
      {students?.length === 0 && (
        <p className="empty">
          No tienes estudiantes asociados y habilitados. Solicita a la
          administración revisar el vínculo.
        </p>
      )}
      {students && !offers.length && (
        <p className="notice">
          La institución no tiene una convocatoria abierta. Puedes consultar tus
          solicitudes existentes.
        </p>
      )}
      {list.loading ? (
        <p role="status">Cargando solicitudes…</p>
      ) : list.data?.content.length ? (
        <div className="user-table-wrap">
          <table className="user-table">
            <thead>
              <tr>
                <th>Estudiante</th>
                <th>Año y grado</th>
                <th>Estado</th>
                <th>Último envío</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {list.data.content.map((item) => (
                <tr key={item.id}>
                  <td data-label="Estudiante">
                    <strong>{item.alumno}</strong>
                    <small>{item.codigoAlumno}</small>
                  </td>
                  <td data-label="Año y grado">
                    {item.anioAcademico} · {item.grado}°
                  </td>
                  <td data-label="Estado">
                    <span className="status">{stateLabel(item.estado)}</span>
                  </td>
                  <td data-label="Último envío">
                    {dateLabel(item.fechaEnvio)}
                  </td>
                  <td data-label="Acciones">
                    <button
                      onClick={() => {
                        setSelected(item);
                        setAction("");
                        setError("");
                        setSuccess("");
                      }}
                    >
                      Ver solicitud
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        !list.error && (
          <p className="empty">Todavía no tienes solicitudes registradas.</p>
        )
      )}
      <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
      {creating && (
        <Dialog
          title="Nueva solicitud de matrícula"
          busy={busy}
          onClose={() => setCreating(false)}
        >
          <Feedback error={error} />
          <form onSubmit={create}>
            <fieldset disabled={busy}>
              <label className="field">
                Estudiante asociado *
                <select name="student" required defaultValue="">
                  <option value="">Seleccionar estudiante</option>
                  {students?.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.nombreCompleto} · {s.codigoAlumno}
                    </option>
                  ))}
                </select>
              </label>
              <div className="form-grid">
                <label className="field">
                  Año con admisión abierta *
                  <select
                    required
                    value={year}
                    onChange={(e) => setYear(e.target.value)}
                  >
                    <option value="">Seleccionar año</option>
                    {offers.map((o) => (
                      <option key={o.anio} value={o.anio}>
                        {o.anio}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  Grado solicitado *
                  <select
                    key={year}
                    name="grade"
                    required
                    disabled={!year}
                    defaultValue=""
                  >
                    <option value="">Seleccionar grado</option>
                    {offers
                      .find((o) => String(o.anio) === year)
                      ?.grados.map((g) => (
                        <option key={g} value={g}>
                          {g}° de secundaria
                        </option>
                      ))}
                  </select>
                </label>
              </div>
              <label className="field">
                Observaciones para la administración
                <textarea name="observaciones" rows={3} maxLength={1000} />
              </label>
              <p className="hint">
                El borrador se puede revisar antes de enviarlo. No puedes tener
                otra solicitud no cancelada para el mismo estudiante y año.
              </p>
              <div className="form-actions">
                <button type="button" onClick={() => setCreating(false)}>
                  Cancelar
                </button>
                <button className="primary-action" disabled={busy}>
                  {busy ? "Guardando…" : "Guardar borrador"}
                </button>
              </div>
            </fieldset>
          </form>
        </Dialog>
      )}
      {selected && (
        <Dialog
          title="Detalle de mi solicitud"
          busy={busy}
          onClose={() => {
            setSelected(null);
            setAction("");
          }}
        >
          <Feedback error={error} success={success} />
          <dl className="detail-grid">
            <div>
              <dt>Estudiante</dt>
              <dd>{selected.alumno}</dd>
            </div>
            <div>
              <dt>Año / grado</dt>
              <dd>
                {selected.anioAcademico} · {selected.grado}°
              </dd>
            </div>
            <div>
              <dt>Estado</dt>
              <dd>{stateLabel(selected.estado)}</dd>
            </div>
            <div>
              <dt>Sección</dt>
              <dd>{selected.seccion}</dd>
            </div>
          </dl>
          <ol className="history-list">
            <li>
              <strong>Guardada</strong>
              <p>{stateLabel(selected.estado)}</p>
            </li>
            {selected.fechaEnvio && (
              <li>
                <strong>Enviada a administración</strong>
                <small>{dateLabel(selected.fechaEnvio)}</small>
              </li>
            )}
            {selected.fechaRevision && (
              <li>
                <strong>Última revisión</strong>
                <small>{dateLabel(selected.fechaRevision)}</small>
              </li>
            )}
          </ol>
          {selected.observaciones && (
            <p>
              <strong>Observaciones:</strong> {selected.observaciones}
            </p>
          )}
          {selected.motivoRechazo && (
            <p>
              <strong>Motivo de rechazo:</strong> {selected.motivoRechazo}
            </p>
          )}
          <div className="row-actions">
            <button disabled={busy} onClick={refreshSelected}>
              Actualizar estado
            </button>
            {editable && (
              <>
                <button disabled={busy} onClick={() => setAction("EDITAR")}>
                  Editar observaciones
                </button>
                <button
                  className="primary-action"
                  disabled={busy}
                  onClick={() => setAction("ENVIADA")}
                >
                  {selected.estado === "OBSERVADA"
                    ? "Reenviar subsanación"
                    : "Enviar solicitud"}
                </button>
              </>
            )}
            {cancellable && (
              <button disabled={busy} onClick={() => setAction("CANCELADA")}>
                Cancelar solicitud
              </button>
            )}
          </div>
          {action && (
            <form className="action-card" key={action} onSubmit={update}>
              <h3>
                {action === "EDITAR"
                  ? "Editar observaciones"
                  : action === "CANCELADA"
                    ? "Cancelar solicitud"
                    : "Confirmar envío"}
              </h3>
              {["EDITAR", "CANCELADA"].includes(action) ? (
                <label className="field">
                  {action === "CANCELADA"
                    ? "Motivo de cancelación *"
                    : "Observaciones"}
                  <textarea
                    name="observaciones"
                    required={action === "CANCELADA"}
                    maxLength={1000}
                    rows={3}
                    defaultValue={
                      action === "EDITAR" ? selected.observaciones || "" : ""
                    }
                  />
                </label>
              ) : (
                <p>
                  Confirma que los datos son correctos. Una vez enviada, podrás
                  consultar su estado; la administración realizará la revisión.
                </p>
              )}
              <div className="form-actions">
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => setAction("")}
                >
                  Volver
                </button>
                <button className="primary-action" disabled={busy}>
                  {busy ? "Procesando…" : "Confirmar"}
                </button>
              </div>
            </form>
          )}
        </Dialog>
      )}
    </section>
  );
}

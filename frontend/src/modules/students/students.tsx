"use client";
import { useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import {
  Feedback,
  Field,
  Pager,
  PanelHeading,
  PersonCell,
  StatusPill,
} from "@/shared/ui";
import { PersonFields, personData } from "@/shared/forms/person-fields";
import { usePaged } from "@/shared/hooks/use-paged";
import { countLabel, timeLabel } from "@/shared/lib/formatters";
import type { Student } from "./types";
export function Students() {
  const [query, setQuery] = useState("");
  const list = usePaged<Student>("/api/alumnos", query);
  const [detail, setDetail] = useState<Student | null>(null);
  const [formMode, setFormMode] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [busy, setBusy] = useState(false);
  async function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setSuccess("");
    const form = new FormData(e.currentTarget);
    try {
      const saved = await api<Student>(
        detail ? `/api/alumnos/${detail.id}` : "/api/alumnos",
        {
          method: detail ? "PUT" : "POST",
          body: JSON.stringify({
            codigoAlumno: String(form.get("codigoAlumno")).trim(),
            persona: personData(form, detail?.persona.nacionalidadId),
          }),
        },
      );
      setDetail(saved);
      setFormMode(false);
      list.refresh();
      setSuccess(
        "Ficha guardada. El acceso se gestiona desde Administración → Usuarios → Crear acceso.",
      );
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  async function open(id: number) {
    setError("");
    setSuccess("");
    try {
      setDetail(await api<Student>(`/api/alumnos/${id}`));
    } catch (e) {
      setError(message(e));
    }
  }
  const total = list.data?.totalElements ?? 0;
  return (
    <section className="panel">
      {formMode ? (
        <form onSubmit={save}>
          <PanelHeading
            title={detail ? "Editar ficha" : "Registrar ficha"}
            summary={
              detail
                ? `${detail.persona.nombreCompleto} · ${detail.codigoAlumno}`
                : "Datos institucionales del estudiante. El acceso se crea aparte."
            }
          />
          <Feedback error={error} />
          <div className="form-grid">
            <Field
              label="Código de estudiante"
              name="codigoAlumno"
              required
              maxLength={30}
              defaultValue={detail?.codigoAlumno}
            />
          </div>
          <PersonFields person={detail?.persona} />
          <div className="form-actions">
            <button
              type="button"
              disabled={busy}
              onClick={() => setFormMode(false)}
            >
              Cancelar
            </button>
            <button className="primary-action" disabled={busy}>
              {busy ? "Guardando…" : "Guardar ficha"}
            </button>
          </div>
        </form>
      ) : detail ? (
        <article>
          <PanelHeading
            title={detail.persona.nombreCompleto}
            summary={`Ficha del estudiante · ${detail.codigoAlumno}`}
            actions={
              <>
                <button
                  onClick={() => {
                    setDetail(null);
                    setSuccess("");
                  }}
                >
                  Volver a fichas
                </button>
                <button
                  className="primary-action"
                  onClick={() => setFormMode(true)}
                >
                  Editar ficha
                </button>
              </>
            }
          />
          <Feedback error={error} success={success} />
          <dl className="detail-grid detail-card">
            <div>
              <dt>Código</dt>
              <dd>{detail.codigoAlumno}</dd>
            </div>
            <div>
              <dt>Documento</dt>
              <dd>
                {detail.persona.tipoDocumento}: {detail.persona.numeroDocumento}
              </dd>
            </div>
            <div>
              <dt>Correo</dt>
              <dd>{detail.persona.correo}</dd>
            </div>
            <div>
              <dt>Nacimiento</dt>
              <dd>{detail.persona.fechaNacimiento}</dd>
            </div>
            <div>
              <dt>Teléfono</dt>
              <dd>{detail.persona.telefono || "Sin registrar"}</dd>
            </div>
            <div>
              <dt>Dirección</dt>
              <dd>{detail.persona.direccion || "Sin registrar"}</dd>
            </div>
          </dl>
          <h4>Apoderados asociados</h4>
          {detail.apoderados.length ? (
            <ul className="choice-list">
              {detail.apoderados.map((a) => (
                <li key={a.id}>
                  <PersonCell
                    name={a.nombreCompleto}
                    detail={a.principal ? "Principal" : undefined}
                  />
                </li>
              ))}
            </ul>
          ) : (
            <p className="empty">No tiene apoderados asociados.</p>
          )}
        </article>
      ) : (
        <>
          <PanelHeading
            title="Fichas de estudiantes"
            summary={
              <>
                {list.data
                  ? countLabel(total, "estudiante", "estudiantes")
                  : "Datos institucionales y apoderados asociados"}
                {list.updatedAt &&
                  ` · Última actualización: ${timeLabel(list.updatedAt)}`}
              </>
            }
            actions={
              <>
                <button disabled={list.loading} onClick={list.refresh}>
                  Actualizar
                </button>
                <button
                  className="primary-action"
                  onClick={() => {
                    setDetail(null);
                    setFormMode(true);
                    setSuccess("");
                  }}
                >
                  + Registrar estudiante
                </button>
              </>
            }
          />
          <Feedback error={error || list.error} success={success} />
          <form
            className="toolbar"
            onSubmit={(e) => {
              e.preventDefault();
              setQuery(
                String(new FormData(e.currentTarget).get("search") || ""),
              );
              list.setPage(0);
            }}
          >
            <Field
              label="Buscar por nombre, código o documento"
              name="search"
            />
            <button disabled={list.loading}>Buscar</button>
          </form>
          {list.loading ? (
            <p role="status">Cargando fichas…</p>
          ) : list.data?.content.length ? (
            <div className="user-table-wrap">
              <table className="user-table">
                <thead>
                  <tr>
                    <th>Código</th>
                    <th>Estudiante</th>
                    <th>Documento</th>
                    <th>Estado</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {list.data.content.map((s) => (
                    <tr key={s.id}>
                      <td data-label="Código" className="muted-cell">
                        {s.codigoAlumno}
                      </td>
                      <td data-label="Estudiante">
                        <PersonCell
                          name={s.persona.nombreCompleto}
                          detail={s.persona.correo}
                        />
                      </td>
                      <td data-label="Documento">
                        {s.persona.numeroDocumento}
                      </td>
                      <td data-label="Estado">
                        <StatusPill
                          state={s.activo ? "ACTIVO" : "INACTIVO"}
                          label={s.activo ? "Activo" : "Inactivo"}
                          tone={s.activo ? "success" : "neutral"}
                        />
                      </td>
                      <td data-label="Acciones">
                        <button onClick={() => open(s.id)}>Ver ficha</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            !list.error && (
              <p className="empty">No se encontraron estudiantes.</p>
            )
          )}
          <Pager
            data={list.data}
            loading={list.loading}
            setPage={list.setPage}
          />
        </>
      )}
    </section>
  );
}

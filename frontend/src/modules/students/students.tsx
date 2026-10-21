"use client";
import { useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { Feedback, Field, Pager } from "@/shared/ui";
import { PersonFields, personData } from "@/shared/forms/person-fields";
import { usePaged } from "@/shared/hooks/use-paged";
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
  return (
    <section>
      <div className="management-heading">
        <div>
          <p className="eyebrow">MATRÍCULA</p>
          <h2>Fichas de estudiantes</h2>
          <p>Datos institucionales y apoderados asociados.</p>
        </div>
        {!detail && !formMode && (
          <button
            className="primary-action"
            onClick={() => {
              setDetail(null);
              setFormMode(true);
              setSuccess("");
            }}
          >
            Registrar estudiante
          </button>
        )}
      </div>
      <Feedback error={error || list.error} success={success} />
      {formMode ? (
        <form className="panel" onSubmit={save}>
          <h3>{detail ? "Editar ficha" : "Registrar ficha"}</h3>
          <Field
            label="Código de estudiante"
            name="codigoAlumno"
            required
            maxLength={30}
            defaultValue={detail?.codigoAlumno}
          />
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
        <article className="panel">
          <div className="toolbar">
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
          </div>
          <h3>{detail.persona.nombreCompleto}</h3>
          <dl className="detail-grid">
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
            <ul>
              {detail.apoderados.map((a) => (
                <li key={a.id}>
                  {a.nombreCompleto}
                  {a.principal ? " · Principal" : ""}
                </li>
              ))}
            </ul>
          ) : (
            <p>No tiene apoderados asociados.</p>
          )}
        </article>
      ) : (
        <>
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
                    <th>Acción</th>
                  </tr>
                </thead>
                <tbody>
                  {list.data.content.map((s) => (
                    <tr key={s.id}>
                      <td data-label="Código">{s.codigoAlumno}</td>
                      <td data-label="Estudiante">
                        <strong>{s.persona.nombreCompleto}</strong>
                        <small>{s.persona.correo}</small>
                      </td>
                      <td data-label="Documento">
                        {s.persona.numeroDocumento}
                      </td>
                      <td data-label="Acción">
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

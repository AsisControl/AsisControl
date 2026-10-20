"use client";
import { useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { Feedback, Field, FormDialog, Pager, ReasonField } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { stateLabel } from "@/shared/lib/formatters";
import { PeriodsPanel } from "./periods/periods-panel";
import { NonTeachingDaysPanel } from "./periods/non-teaching-days-panel";
import { GradesPanel } from "./sections/grades-panel";
import { SectionsPanel } from "./sections/sections-panel";
import type { AcademicYear, YearPanelProps } from "./types";
type YearAction = { kind: "year" | "admission" | "year-state"; next?: string };
const titles: Record<YearAction["kind"], string> = {
  year: "Crear año académico",
  admission: "Recepción de solicitudes del apoderado",
  "year-state": "Cambiar estado del año",
};
/**
 * Contenedor de US-05/US-06: lista de años y año seleccionado.
 * Cada bloque del año (períodos, días no lectivos, grados, secciones) vive en
 * su propio panel para que pueda desarrollarse en paralelo.
 */
export function AcademicConfiguration() {
  const years = usePaged<AcademicYear>("/api/configuracion-academica/anios");
  const [year, setYear] = useState<AcademicYear | null>(null);
  const [action, setAction] = useState<YearAction | null>(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [busy, setBusy] = useState(false);
  const open = (value: YearAction) => {
    setError("");
    setSuccess("");
    setAction(value);
  };
  async function reload(number: number) {
    const updated = await api<AcademicYear>(
      `/api/configuracion-academica/anios/${number}`,
    );
    setYear(updated);
    years.refresh();
  }
  function applyYear(updated: AcademicYear) {
    setYear(updated);
    years.refresh();
  }
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!action) return;
    const form = new FormData(event.currentTarget);
    const str = (key: string) => String(form.get(key) || "").trim();
    let path = "/api/configuracion-academica/anios",
      method = "POST",
      body: object;
    if (action.kind === "year")
      body = {
        anio: Number(form.get("anio")),
        fechaInicio: str("fechaInicio"),
        fechaFin: str("fechaFin"),
      };
    else if (!year) return;
    else if (action.kind === "admission") {
      path += `/${year.anio}/admision`;
      method = "PUT";
      body = {
        abierta: !year.admisionAbierta,
        version: year.version,
        motivo: str("motivo"),
      };
    } else {
      path += `/${year.anio}/estado`;
      method = "PUT";
      body = {
        estado: action.next,
        version: year.version,
        motivo: str("motivo"),
      };
    }
    setBusy(true);
    setError("");
    setSuccess("");
    try {
      applyYear(
        await api<AcademicYear>(path, { method, body: JSON.stringify(body) }),
      );
      setAction(null);
      setSuccess("Configuración guardada.");
    } catch (error) {
      setError(message(error));
    } finally {
      setBusy(false);
    }
  }
  const editable = year?.estado !== "CERRADO";
  const panelProps: YearPanelProps | null = year && {
    year,
    editable,
    onYearChange: applyYear,
    onReload: () => reload(year.anio),
    onSuccess: (value) => {
      setError("");
      setSuccess(value);
    },
  };
  return (
    <>
      <Feedback error={!action ? error : ""} success={success} />
      <div className="section-heading">
        <h3>Años académicos</h3>
        <button
          className="primary-action"
          onClick={() => open({ kind: "year" })}
        >
          Crear año
        </button>
      </div>
      <Feedback error={years.error} />
      {years.loading ? (
        <p role="status">Cargando configuración…</p>
      ) : (
        <div className="year-list">
          {years.data?.content.map((value) => (
            <button
              key={value.id}
              className={
                year?.id === value.id ? "year-card selected" : "year-card"
              }
              onClick={() => {
                setError("");
                reload(value.anio).catch((e) => setError(message(e)));
              }}
            >
              <strong>{value.anio}</strong>
              <span>{stateLabel(value.estado)}</span>
              <small>
                {value.fechaInicio} a {value.fechaFin}
              </small>
            </button>
          ))}
        </div>
      )}
      {!years.loading && !years.error && !years.data?.content.length && (
        <p className="empty">
          Crea el año y configura los períodos, grados y secciones.
        </p>
      )}
      <Pager
        data={years.data}
        loading={years.loading}
        setPage={years.setPage}
      />
      {year && panelProps && (
        <div className="configuration-detail">
          <div className="section-heading">
            <div>
              <h3>Año {year.anio}</h3>
              <p>
                {stateLabel(year.estado)} · {year.fechaInicio} a {year.fechaFin}
                <br />
                Admisión del apoderado:{" "}
                {year.admisionAbierta ? "Abierta" : "Cerrada"}
              </p>
            </div>
            <div className="row-actions">
              <button
                disabled={busy}
                onClick={() =>
                  reload(year.anio).catch((e) => setError(message(e)))
                }
              >
                Actualizar
              </button>
              {year.estado === "ACTIVO" && (
                <button onClick={() => open({ kind: "admission" })}>
                  {year.admisionAbierta ? "Cerrar admisión" : "Abrir admisión"}
                </button>
              )}
              {editable && (
                <button
                  onClick={() =>
                    open({
                      kind: "year-state",
                      next: year.estado === "BORRADOR" ? "ACTIVO" : "CERRADO",
                    })
                  }
                >
                  {year.estado === "BORRADOR" ? "Activar año" : "Cerrar año"}
                </button>
              )}
            </div>
          </div>
          {year.estado === "CERRADO" && (
            <p className="hint">Año cerrado: consulta histórica disponible.</p>
          )}
          <PeriodsPanel {...panelProps} />
          <NonTeachingDaysPanel {...panelProps} />
          <GradesPanel {...panelProps} />
          <SectionsPanel {...panelProps} />
        </div>
      )}
      {action && (
        <FormDialog
          title={titles[action.kind]}
          busy={busy}
          error={error}
          onClose={() => setAction(null)}
          onSubmit={save}
          notes={
            <>
              {action.kind === "admission" && (
                <p>
                  {year?.admisionAbierta
                    ? "Dejarán de aceptarse solicitudes nuevas y reenvíos del apoderado. La revisión administrativa continúa."
                    : "Los apoderados podrán solicitar los grados habilitados del año activo."}
                </p>
              )}
              {action.next && (
                <p>
                  Nuevo estado: <strong>{stateLabel(action.next)}</strong>. Los
                  estados cerrados no se reabren mediante este flujo.
                </p>
              )}
            </>
          }
        >
          {action.kind === "year" ? (
            <>
              <Field
                label="Año"
                name="anio"
                type="number"
                min={2000}
                max={2100}
                required
              />
              <Field
                label="Fecha inicial"
                name="fechaInicio"
                type="date"
                required
              />
              <Field label="Fecha final" name="fechaFin" type="date" required />
            </>
          ) : (
            <ReasonField />
          )}
        </FormDialog>
      )}
    </>
  );
}

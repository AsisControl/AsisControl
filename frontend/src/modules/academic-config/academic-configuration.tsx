"use client";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { api, fieldErrorsOf, message } from "@/core/api/client";
import {
  Feedback,
  Field,
  FormDialog,
  Pager,
  ReasonField,
  StatusPill,
} from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { stateLabel } from "@/shared/lib/formatters";
import { PeriodsPanel } from "./periods/periods-panel";
import { NonTeachingDaysPanel } from "./periods/non-teaching-days-panel";
import { GradesPanel } from "./sections/grades-panel";
import { SectionsPanel } from "./sections/sections-panel";
import {
  hasErrors,
  validateYear,
  YEAR_MAX,
  YEAR_MIN,
  type YearErrors,
} from "./lib/validation";
import { formatDate } from "./lib/format-date";
import styles from "./academic-config.module.css";
import type { AcademicYear, YearPanelProps } from "./types";
type YearAction = {
  kind: "year" | "admission" | "year-state" | "delete";
  next?: string;
};
const titles: Record<YearAction["kind"], string> = {
  year: "Crear año académico",
  admission: "Recepción de solicitudes del apoderado",
  "year-state": "Cambiar estado del año",
  delete: "Eliminar año académico",
};
const yearTones = {
  BORRADOR: "neutral",
  ACTIVO: "success",
  CERRADO: "info",
} as const;
/**
 * Contenedor de US-05/US-06: lista de años y año seleccionado.
 * Cada bloque del año (períodos, días no lectivos, grados, secciones) vive en
 * su propio panel para que pueda desarrollarse en paralelo.
 */
export function AcademicConfiguration() {
  const years = usePaged<AcademicYear>("/api/configuracion-academica/anios");
  const [loaded, setYear] = useState<AcademicYear | null>(null);
  const [chosen, setChosen] = useState<number | null>(null);
  const [action, setAction] = useState<YearAction | null>(null);
  const [error, setError] = useState("");
  const [yearErrors, setYearErrors] = useState<YearErrors>({});
  const [success, setSuccess] = useState("");
  const [busy, setBusy] = useState(false);
  // Más reciente primero; por defecto el año ACTIVO o, si no hay, el más reciente.
  const sorted = useMemo(
    () => [...(years.data?.content ?? [])].sort((a, b) => b.anio - a.anio),
    [years.data],
  );
  const selected =
    chosen ?? (sorted.find((y) => y.estado === "ACTIVO") ?? sorted[0])?.anio;
  const year = loaded && loaded.anio === selected ? loaded : null;
  useEffect(() => {
    if (selected == null) return;
    let active = true;
    api<AcademicYear>(`/api/configuracion-academica/anios/${selected}`)
      .then((value) => active && setYear(value))
      .catch((e) => active && setError(message(e)));
    return () => {
      active = false;
    };
  }, [selected]);
  const open = (value: YearAction) => {
    setError("");
    setSuccess("");
    setYearErrors({});
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
    if (action.kind === "delete") return remove();
    const form = new FormData(event.currentTarget);
    const str = (key: string) => String(form.get(key) || "").trim();
    let path = "/api/configuracion-academica/anios",
      method = "POST",
      body: object;
    if (action.kind === "year") {
      const input = {
        anio: str("anio"),
        fechaInicio: str("fechaInicio"),
        fechaFin: str("fechaFin"),
      };
      const found = validateYear(input, sorted);
      setYearErrors(found);
      if (hasErrors(found)) return;
      body = { ...input, anio: Number(input.anio) };
    } else if (!year) return;
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
      const saved = await api<AcademicYear>(path, {
        method,
        body: JSON.stringify(body),
      });
      if (action.kind === "year") setChosen(saved.anio);
      applyYear(saved);
      setAction(null);
      setSuccess("Configuración guardada.");
    } catch (error) {
      const fields = fieldErrorsOf(error);
      // Un error que ya se muestra junto a su campo no se repite arriba.
      if (action.kind === "year" && hasErrors(fields)) setYearErrors(fields);
      else setError(message(error));
    } finally {
      setBusy(false);
    }
  }
  /** Solo un año en borrador (nunca activado) sin uso; el backend lo verifica. */
  async function remove() {
    if (!year) return;
    setBusy(true);
    setError("");
    try {
      await api<null>(
        `/api/configuracion-academica/anios/${year.anio}?version=${year.version}`,
        { method: "DELETE" },
      );
      setAction(null);
      setChosen(null);
      setYear(null);
      years.refresh();
      setSuccess(`Año ${year.anio} eliminado.`);
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
      <div className={styles.header}>
        <div>
          <h3>Configuración académica</h3>
          <p>Años académicos, períodos, grados y secciones</p>
        </div>
        <div className={styles.controls}>
          {sorted.length > 0 && (
            <label className={styles.yearPicker}>
              Año académico
              <select
                className={styles.yearSelect}
                value={selected}
                onChange={(e) => {
                  setError("");
                  setSuccess("");
                  setChosen(Number(e.target.value));
                }}
              >
                {sorted.map((value) => (
                  <option key={value.id} value={value.anio}>
                    {value.anio} · {stateLabel(value.estado)}
                  </option>
                ))}
              </select>
            </label>
          )}
          {year?.estado === "ACTIVO" && (
            <span className={styles.activeTag}>Año activo</span>
          )}
          <button
            className="primary-action"
            onClick={() => open({ kind: "year" })}
          >
            Crear año
          </button>
        </div>
      </div>
      <div className={styles.feedback}>
        <Feedback error={!action ? error : ""} success={success} />
        <Feedback error={years.error} />
      </div>
      {years.loading && !years.data && (
        <p role="status">Cargando configuración…</p>
      )}
      {!years.loading && !years.error && !sorted.length && (
        <div className={styles.infoNotice} role="note">
          <p>
            <strong>Aún no hay años académicos.</strong> Usa «Crear año» para
            empezar; después podrás agregar sus períodos, grados y secciones.
          </p>
        </div>
      )}
      {(years.data?.totalPages ?? 0) > 1 && (
        <Pager
          data={years.data}
          loading={years.loading}
          setPage={years.setPage}
        />
      )}
      {year && panelProps && (
        <div className="configuration-detail">
          <div className={styles.yearHeading}>
            <div>
              <div className={styles.yearTitle}>
                <h3>Año {year.anio}</h3>
                <StatusPill
                  state={year.estado}
                  label={stateLabel(year.estado)}
                  tone={
                    yearTones[year.estado as keyof typeof yearTones] ??
                    "neutral"
                  }
                />
              </div>
              <p>
                Del {formatDate(year.fechaInicio)} al{" "}
                {formatDate(year.fechaFin)}
                {" · "}Admisión del apoderado:{" "}
                {year.admisionAbierta ? "abierta" : "cerrada"}
              </p>
            </div>
            <div className={styles.yearActions}>
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
                  className={styles.primaryButton}
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
              {year.estado === "BORRADOR" && (
                <button
                  className={styles.dangerButton}
                  onClick={() => open({ kind: "delete" })}
                >
                  Eliminar año
                </button>
              )}
            </div>
          </div>
          {year.estado === "CERRADO" && (
            <p className="hint">Año cerrado: consulta histórica disponible.</p>
          )}
          <PeriodsPanel {...panelProps} />
          <NonTeachingDaysPanel key={year.anio} {...panelProps} />
          <GradesPanel key={`grades-${year.anio}`} {...panelProps} />
          <SectionsPanel {...panelProps} />
        </div>
      )}
      {action && (
        <FormDialog
          title={titles[action.kind]}
          description={
            action.kind === "delete" ? `Año ${year?.anio}` : undefined
          }
          submitLabel={action.kind === "delete" ? "Eliminar año" : undefined}
          danger={action.kind === "delete"}
          noValidate={action.kind === "year"}
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
          {action.kind === "delete" ? (
            <p className="full-field hint">
              Se eliminarán el año, sus períodos, grados y días no lectivos.
              Solo es posible porque nunca se activó; si ya tiene secciones,
              solicitudes, matrículas o cursos, no se eliminará.
            </p>
          ) : action.kind === "year" ? (
            <>
              <Field
                label="Año"
                name="anio"
                type="number"
                min={Math.max(YEAR_MIN, new Date().getFullYear())}
                max={YEAR_MAX}
                required
                error={yearErrors.anio}
                onChange={() =>
                  setYearErrors((e) => ({ ...e, anio: undefined }))
                }
              />
              <Field
                label="Fecha inicial"
                name="fechaInicio"
                type="date"
                required
                error={yearErrors.fechaInicio}
                onChange={() =>
                  setYearErrors((e) => ({ ...e, fechaInicio: undefined }))
                }
              />
              <Field
                label="Fecha final"
                name="fechaFin"
                type="date"
                required
                error={yearErrors.fechaFin}
                onChange={() =>
                  setYearErrors((e) => ({ ...e, fechaFin: undefined }))
                }
              />
            </>
          ) : (
            <ReasonField />
          )}
        </FormDialog>
      )}
    </>
  );
}

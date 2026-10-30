"use client";
import { useEffect, useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Feedback, FormDialog, PanelHeading } from "@/shared/ui";
import { countLabel, timeLabel } from "@/shared/lib/formatters";
import { scheduleSlotLabel } from "../lib/labels";
import { serverConflict } from "../lib/timetable";
import type {
  Classroom,
  CourseAssignment,
  ScheduleBlock,
  ScheduleSection,
} from "../types";
import styles from "../schedules.module.css";
import { BlockFormDialog } from "./block-form-dialog";
import { TimetableGrid } from "./timetable-grid";
import {
  eliminarHorario,
  listarAsignaciones,
  listarAulasHabilitadas,
  listarHorarios,
  listarSeccionesActivas,
} from "./services/horarioService";
type BlockDialog =
  | { kind: "create" }
  | { kind: "edit" | "delete"; block: ScheduleBlock };
type Catalogs = { assignments: CourseAssignment[]; classrooms: Classroom[] };
const sectionLabel = (section: ScheduleSection) => `${section.grado}° ${section.nombre}`;
const bySection = (a: ScheduleSection, b: ScheduleSection) =>
  a.grado - b.grado || a.nombre.localeCompare(b.nombre);
/** Pestaña Horario: matriz semanal por sección y asignación manual de bloques. */
export function TimetablePanel({ canWrite }: { canWrite: boolean }) {
  const [sections, setSections] = useState<ScheduleSection[] | null>(null);
  const [catalogs, setCatalogs] = useState<Catalogs | null>(null);
  const [setupError, setSetupError] = useState("");
  const [year, setYear] = useState<number | null>(null);
  const [sectionId, setSectionId] = useState<number | null>(null);
  const [blocks, setBlocks] = useState<ScheduleBlock[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const [tick, setTick] = useState(0);
  const [dialog, setDialog] = useState<BlockDialog | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  useEffect(() => {
    let active = true;
    // Los catálogos del formulario solo hacen falta con permiso de escritura.
    Promise.all([
      listarSeccionesActivas(),
      canWrite
        ? Promise.all([listarAsignaciones(), listarAulasHabilitadas()])
        : Promise.resolve(null),
    ])
      .then(([loaded, forms]) => {
        if (!active) return;
        setSections(loaded);
        if (forms) setCatalogs({ assignments: forms[0], classrooms: forms[1] });
        const years = [...new Set(loaded.map((s) => s.anioAcademico))];
        const current = new Date().getFullYear();
        setYear(years.includes(current) ? current : (Math.max(...years, 0) || null));
      })
      .catch((e) => {
        if (active) setSetupError(message(e));
      });
    return () => {
      active = false;
    };
  }, [canWrite]);
  const years = [...new Set((sections ?? []).map((s) => s.anioAcademico))].sort(
    (a, b) => b - a,
  );
  const yearSections = (sections ?? [])
    .filter((s) => s.anioAcademico === year)
    .sort(bySection);
  const section =
    yearSections.find((s) => s.id === sectionId) ?? yearSections[0] ?? null;
  const selectedId = section?.id ?? null;
  useEffect(() => {
    if (selectedId === null || year === null) return;
    let active = true;
    const timer = setTimeout(() => {
      setLoading(true);
      setLoadError("");
      listarHorarios({ anioAcademico: year, seccionId: selectedId })
        .then((loaded) => {
          if (!active) return;
          setBlocks(loaded);
          setUpdatedAt(new Date());
        })
        .catch((e) => {
          if (active) setLoadError(message(e));
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    }, 0);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [year, selectedId, tick]);
  const refresh = () => setTick((value) => value + 1);
  function open(next: BlockDialog) {
    setError("");
    setSuccess("");
    setDialog(next);
  }
  function close() {
    setDialog(null);
    setError("");
  }
  function finish(feedback: string) {
    setDialog(null);
    setSuccess(feedback);
    refresh();
  }
  async function remove(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (dialog?.kind !== "delete") return;
    setBusy(true);
    setError("");
    try {
      await eliminarHorario(dialog.block.id);
      finish(`Bloque de ${dialog.block.curso} eliminado.`);
    } catch (e) {
      setError(serverConflict(e)?.detail ?? message(e));
      refresh();
    } finally {
      setBusy(false);
    }
  }
  const assignments = (catalogs?.assignments ?? []).filter(
    (a) => a.seccionId === selectedId,
  );
  const rows = blocks ?? [];
  const writable = canWrite && catalogs !== null;
  if (setupError)
    return (
      <section className="panel">
        <PanelHeading title="Horario por sección" />
        <Feedback error={setupError} />
      </section>
    );
  return (
    <section className="panel">
      <PanelHeading
        title="Horario por sección"
        summary={
          <>
            {blocks
              ? countLabel(rows.length, "bloque asignado", "bloques asignados")
              : "Bloques de clase de la semana"}
            {updatedAt && ` · Última actualización: ${timeLabel(updatedAt)}`}
          </>
        }
        actions={
          <>
            <button
              type="button"
              disabled={loading || !section}
              onClick={refresh}
            >
              Actualizar
            </button>
            {writable && (
              <button
                type="button"
                className="primary-action"
                disabled={!section || loading}
                onClick={() => open({ kind: "create" })}
              >
                + Nuevo bloque
              </button>
            )}
          </>
        }
      />
      {sections === null ? (
        <p role="status">Cargando secciones…</p>
      ) : !years.length ? (
        <p className="empty">
          No hay secciones habilitadas. Créalas en «Configuración académica»
          para armar su horario.
        </p>
      ) : (
        <>
          <div className={styles.filters}>
            <label className="field">
              Año académico
              <select
                value={year ?? ""}
                onChange={(e) => {
                  setYear(Number(e.target.value));
                  setSectionId(null);
                  setSuccess("");
                }}
              >
                {years.map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              Sección
              <select
                value={selectedId ?? ""}
                onChange={(e) => {
                  setSectionId(Number(e.target.value));
                  setSuccess("");
                }}
              >
                {yearSections.map((item) => (
                  <option key={item.id} value={item.id}>
                    {sectionLabel(item)} · aforo {item.capacidadMaxima}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <Feedback error={loadError} success={success} />
          {loading ? (
            <p role="status">Cargando horario…</p>
          ) : rows.length ? (
            <TimetableGrid
              blocks={rows}
              onEdit={writable ? (block) => open({ kind: "edit", block }) : undefined}
              onDelete={writable ? (block) => open({ kind: "delete", block }) : undefined}
            />
          ) : (
            !loadError && (
              <p className="empty">
                La sección {section && sectionLabel(section)} aún no tiene bloques
                de horario.
                {writable && " Usa «Nuevo bloque» para asignar el primero."}
              </p>
            )
          )}
        </>
      )}
      {section && catalogs && (dialog?.kind === "create" || dialog?.kind === "edit") && (
        <BlockFormDialog
          section={section}
          block={dialog.kind === "edit" ? dialog.block : undefined}
          assignments={assignments}
          classrooms={catalogs.classrooms}
          sectionBlocks={rows}
          onClose={close}
          onStale={refresh}
          onSaved={(saved) =>
            finish(
              `Bloque de ${saved.curso} ${dialog.kind === "edit" ? "actualizado" : "registrado"}: ${scheduleSlotLabel(saved)}.`,
            )
          }
        />
      )}
      {dialog?.kind === "delete" && (
        <FormDialog
          title="Eliminar bloque"
          description={`${dialog.block.curso} · ${scheduleSlotLabel(dialog.block)}`}
          submitLabel="Eliminar bloque"
          danger
          busy={busy}
          error={error}
          onClose={close}
          onSubmit={remove}
        >
          <p className="full-field hint">
            El bloque de {dialog.block.curso} con {dialog.block.docente} en{" "}
            {dialog.block.aula} dejará de aparecer en el horario de la sección.
          </p>
        </FormDialog>
      )}
    </section>
  );
}

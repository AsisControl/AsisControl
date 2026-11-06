"use client";
import { useEffect, useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Feedback, FormDialog, PanelHeading } from "@/shared/ui";
import { countLabel, timeLabel } from "@/shared/lib/formatters";
import { scheduleSlotLabel } from "../lib/labels";
import {
  PERSPECTIVES,
  perspectiveOptions,
  serverConflict,
  type Perspective,
} from "../lib/timetable";
import type {
  Classroom,
  CourseAssignment,
  ScheduleBlock,
  ScheduleSection,
} from "../types";
import styles from "../schedules.module.css";
import { BlockDetailDialog } from "./block-detail-dialog";
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
/** Parámetro de GET /api/horarios de cada perspectiva. */
const FILTERS = {
  seccion: "seccionId",
  docente: "docenteId",
  aula: "aulaId",
  curso: "cursoId",
} as const;
const EMPTY_OPTIONS: Record<Exclude<Perspective, "seccion">, string> = {
  docente: "No hay docentes con asignaciones activas en",
  curso: "No hay cursos con asignaciones activas en",
  aula: "No hay aulas habilitadas para",
};
const sectionLabel = (section: ScheduleSection) => `${section.grado}° ${section.nombre}`;
const bySection = (a: ScheduleSection, b: ScheduleSection) =>
  a.grado - b.grado || a.nombre.localeCompare(b.nombre);
/** Pestaña Horario: matriz por sección, docente, aula o curso y asignación manual de bloques. */
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
  const [assignmentsStatus, setAssignmentsStatus] = useState<"loading" | "ready" | "failed">("ready");
  const [perspective, setPerspective] = useState<Perspective>("seccion");
  const [targetId, setTargetId] = useState<number | null>(null);
  const [viewCatalogs, setViewCatalogs] = useState<Catalogs | null>(null);
  const [detail, setDetail] = useState<ScheduleBlock | null>(null);
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
  const options =
    perspective === "seccion" || !viewCatalogs
      ? []
      : perspectiveOptions(perspective, year, viewCatalogs.assignments, viewCatalogs.classrooms);
  const target = options.find((item) => item.id === targetId) ?? options[0] ?? null;
  const filterName = FILTERS[perspective];
  const filterValue = perspective === "seccion" ? selectedId : (target?.id ?? null);
  useEffect(() => {
    if (perspective === "seccion") return;
    let active = true;
    // Se consultan al cambiar de vista: quien solo lee no carga los catálogos del formulario.
    Promise.all([listarAsignaciones(), listarAulasHabilitadas()])
      .then(([loadedAssignments, classrooms]) => {
        if (active) setViewCatalogs({ assignments: loadedAssignments, classrooms });
      })
      .catch((e) => {
        if (active) setLoadError(message(e));
      });
    return () => {
      active = false;
    };
  }, [perspective, tick]);
  useEffect(() => {
    if (filterValue === null || year === null) return;
    let active = true;
    const timer = setTimeout(() => {
      setLoading(true);
      setLoadError("");
      listarHorarios({ anioAcademico: year, [filterName]: filterValue })
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
  }, [year, filterName, filterValue, tick]);
  const refresh = () => setTick((value) => value + 1);
  function open(next: BlockDialog) {
    setError("");
    setSuccess("");
    setDialog(next);
    if (next.kind === "delete") return;
    // El catálogo de la pestaña puede ser anterior a una asignación recién registrada.
    setAssignmentsStatus("loading");
    listarAsignaciones()
      .then((loaded) => {
        setCatalogs((current) => current && { ...current, assignments: loaded });
        setAssignmentsStatus("ready");
      })
      .catch(() => setAssignmentsStatus("failed"));
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
  const rows = filterValue === null ? [] : (blocks ?? []);
  const writable = canWrite && catalogs !== null;
  // Los bloques se editan por sección: el formulario valida los cruces de una sola.
  const editable = writable && perspective === "seccion";
  const title = `Horario por ${PERSPECTIVES[perspective].toLowerCase()}`;
  const blockSection = (block: ScheduleBlock) => {
    const found = sections?.find((item) => item.id === block.seccionId);
    return found ? sectionLabel(found) : block.seccion;
  };
  const changePerspective = (next: Perspective) => {
    setPerspective(next);
    setTargetId(null);
    setBlocks(null);
    setLoadError("");
    setSuccess("");
  };
  if (setupError)
    return (
      <section className="panel">
        <PanelHeading title={title} />
        <Feedback error={setupError} />
      </section>
    );
  return (
    <section className="panel">
      <PanelHeading
        title={title}
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
              disabled={loading || filterValue === null}
              onClick={refresh}
            >
              Actualizar
            </button>
            {editable && (
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
              Ver horario por
              <select
                value={perspective}
                onChange={(e) => changePerspective(e.target.value as Perspective)}
              >
                {Object.entries(PERSPECTIVES).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
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
            {perspective === "seccion" ? (
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
            ) : (
              <label className="field">
                {PERSPECTIVES[perspective]}
                <select
                  value={target?.id ?? ""}
                  disabled={!options.length}
                  onChange={(e) => {
                    setTargetId(Number(e.target.value));
                    setSuccess("");
                  }}
                >
                  {!options.length && <option value="">Sin opciones</option>}
                  {options.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.label}
                    </option>
                  ))}
                </select>
              </label>
            )}
          </div>
          <Feedback error={loadError} success={success} />
          {loading || (filterValue !== null && !blocks && !loadError) ? (
            <p role="status">Cargando horario…</p>
          ) : perspective !== "seccion" && !viewCatalogs ? (
            !loadError && <p role="status">Cargando opciones…</p>
          ) : (
            !loadError && (
              <TimetableGrid
                blocks={rows}
                label={
                  perspective === "seccion"
                    ? "Horario semanal de la sección"
                    : `Horario semanal por ${PERSPECTIVES[perspective].toLowerCase()}`
                }
                emptyMessage={
                  perspective === "seccion" ? (
                    <>
                      La sección {section && sectionLabel(section)} aún no tiene bloques de horario.
                      {writable && " Usa «Nuevo bloque» para asignar el primero."}
                    </>
                  ) : target ? (
                    `${target.label} no tiene bloques de horario en ${year}.`
                  ) : (
                    `${EMPTY_OPTIONS[perspective]} ${year}.`
                  )
                }
                sectionOf={perspective === "seccion" ? undefined : blockSection}
                onOpen={setDetail}
                onEdit={editable ? (block) => open({ kind: "edit", block }) : undefined}
                onDelete={editable ? (block) => open({ kind: "delete", block }) : undefined}
              />
            )
          )}
        </>
      )}
      {detail && (
        <BlockDetailDialog
          block={detail}
          section={`${blockSection(detail)} · ${year}`}
          onClose={() => setDetail(null)}
        />
      )}
      {section && catalogs && (dialog?.kind === "create" || dialog?.kind === "edit") && (
        <BlockFormDialog
          section={section}
          block={dialog.kind === "edit" ? dialog.block : undefined}
          assignments={assignments}
          assignmentsStatus={assignmentsStatus}
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

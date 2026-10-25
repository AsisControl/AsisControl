"use client";
import { useEffect, useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Feedback, FormDialog, PanelHeading, Pager, RowMenu } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { countLabel, timeLabel } from "@/shared/lib/formatters";
import { classroomTypeLabels } from "../lib/labels";
import { PendingBadge } from "../pending/pending-badge";
import type { Classroom } from "../types";
import styles from "../schedules.module.css";
import { ClassroomDetailDialog } from "./classroom-detail-dialog";
import { ClassroomFormDialog } from "./classroom-form-dialog";
import { ClassroomStateDialog } from "./classroom-state-dialog";
import { ClassroomStatus } from "./classroom-status";
import {
  AULAS_LIST_PATH,
  eliminarAula,
  resumenAulas,
} from "./services/aulaService";
type ClassroomDialog =
  | { kind: "create" }
  | { kind: "detail" | "edit" | "state" | "delete"; classroom: Classroom };
/** Pestaña Aulas de Horario Académico: listado, detalle, alta, edición, estado y eliminación. */
export function ClassroomsPanel({ canWrite }: { canWrite: boolean }) {
  const classrooms = usePaged<Classroom>(AULAS_LIST_PATH);
  const [available, setAvailable] = useState<number | null>(null);
  const [reload, setReload] = useState(0);
  const [dialog, setDialog] = useState<ClassroomDialog | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  useEffect(() => {
    let active = true;
    // El resumen es complementario: si falla, solo se omite el dato.
    resumenAulas()
      .then((summary) => {
        if (active) setAvailable(summary.habilitadas);
      })
      .catch(() => {
        if (active) setAvailable(null);
      });
    return () => {
      active = false;
    };
  }, [reload]);
  function refresh() {
    classrooms.refresh();
    setReload((value) => value + 1);
  }
  function open(next: ClassroomDialog) {
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
      await eliminarAula(dialog.classroom.id);
      finish(`Aula ${dialog.classroom.nombre} eliminada.`);
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  const rows = classrooms.data?.content ?? [];
  const total = classrooms.data?.totalElements ?? 0;
  return (
    <section className="panel">
      <PanelHeading
        title="Gestión de aulas"
        summary={
          <>
            {classrooms.data
              ? countLabel(total, "aula registrada", "aulas registradas")
              : "Ambientes disponibles para los bloques de horario"}
            {classrooms.data &&
              available !== null &&
              ` · ${countLabel(available, "disponible", "disponibles")}`}
            {classrooms.updatedAt &&
              ` · Última actualización: ${timeLabel(classrooms.updatedAt)}`}
          </>
        }
        actions={
          <>
            <button
              type="button"
              disabled={classrooms.loading}
              onClick={refresh}
            >
              Actualizar
            </button>
            {canWrite && (
              <button
                type="button"
                className="primary-action"
                onClick={() => open({ kind: "create" })}
              >
                + Nueva aula
              </button>
            )}
          </>
        }
      />
      <p className={styles.availabilityNotice}>
        Las aulas inhabilitadas no pueden asignarse a nuevos bloques de
        horario.
      </p>
      <Feedback error={classrooms.error} success={success} />
      {classrooms.loading ? (
        <p role="status">Cargando aulas…</p>
      ) : rows.length ? (
        <div className={styles.tableWrap}>
          <table className={`user-table ${styles.table}`}>
            <thead>
              <tr>
                <th>Código</th>
                <th>Aula</th>
                <th>Capacidad</th>
                <th>
                  Equipamiento <PendingBadge feature="classroomEquipment" />
                </th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((classroom) => (
                <tr key={classroom.id}>
                  <td data-label="Código">
                    <span className={styles.code}>{classroom.codigo}</span>
                  </td>
                  <td data-label="Aula">
                    <strong>{classroom.nombre}</strong>
                    <small>
                      {classroom.ubicacion ||
                        classroomTypeLabels[classroom.tipoAula]}
                    </small>
                  </td>
                  <td data-label="Capacidad">
                    {countLabel(classroom.capacidad, "estudiante", "estudiantes")}
                  </td>
                  <td data-label="Equipamiento" className={styles.muted}>
                    —
                  </td>
                  <td data-label="Estado">
                    <ClassroomStatus active={classroom.activo} />
                  </td>
                  <td data-label="Acciones">
                    <div className={styles.actions}>
                      <button
                        type="button"
                        className={styles.detailButton}
                        onClick={() => open({ kind: "detail", classroom })}
                      >
                        Ver detalle
                      </button>
                      {canWrite && (
                        <RowMenu
                          itemName={classroom.nombre}
                          options={[
                            {
                              label: "Editar",
                              onSelect: () => open({ kind: "edit", classroom }),
                            },
                            {
                              label: classroom.activo
                                ? "Inhabilitar"
                                : "Habilitar",
                              danger: classroom.activo,
                              onSelect: () =>
                                open({ kind: "state", classroom }),
                            },
                            {
                              label: "Eliminar",
                              danger: true,
                              onSelect: () =>
                                open({ kind: "delete", classroom }),
                            },
                          ]}
                        />
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        !classrooms.error && (
          <p className="empty">
            Aún no hay aulas registradas.
            {canWrite && " Usa «Nueva aula» para registrar la primera."}
          </p>
        )
      )}
      <Pager
        data={classrooms.data}
        loading={classrooms.loading}
        setPage={classrooms.setPage}
      />
      {dialog?.kind === "detail" && (
        <ClassroomDetailDialog
          classroom={dialog.classroom}
          onClose={close}
          onEdit={
            canWrite
              ? () => setDialog({ kind: "edit", classroom: dialog.classroom })
              : undefined
          }
        />
      )}
      {(dialog?.kind === "create" || dialog?.kind === "edit") && (
        <ClassroomFormDialog
          classroom={dialog.kind === "edit" ? dialog.classroom : undefined}
          classrooms={rows}
          onClose={close}
          onSaved={(saved) =>
            finish(
              dialog.kind === "edit"
                ? `Aula ${saved.nombre} actualizada.`
                : `Aula ${saved.nombre} registrada.`,
            )
          }
        />
      )}
      {dialog?.kind === "state" && (
        <ClassroomStateDialog
          classroom={dialog.classroom}
          onClose={close}
          onSaved={(saved) =>
            finish(
              saved.activo
                ? `Aula ${saved.nombre} habilitada.`
                : `Aula ${saved.nombre} inhabilitada.`,
            )
          }
        />
      )}
      {dialog?.kind === "delete" && (
        <FormDialog
          title="Eliminar aula"
          description={`${dialog.classroom.nombre} · ${dialog.classroom.codigo}`}
          submitLabel="Eliminar aula"
          danger
          busy={busy}
          error={error}
          onClose={close}
          onSubmit={remove}
        >
          <p className="full-field hint">
            {dialog.classroom.nombre} se eliminará del catálogo. Solo es
            posible si nunca se usó en un horario; si ya se usó, inhabilítala
            en su lugar.
          </p>
        </FormDialog>
      )}
    </section>
  );
}

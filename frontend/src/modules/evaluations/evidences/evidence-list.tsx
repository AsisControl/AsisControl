"use client";
import { useState, type FormEvent } from "react";
import { descargarArchivo } from "@/modules/tasks";
import { usePaged } from "@/shared/hooks/use-paged";
import { countLabel } from "@/shared/lib/formatters";
import { Feedback, FormDialog, Loading, Pager, PanelHeading, RowMenu } from "@/shared/ui";
import type { OwnAssignment } from "../types";
import { EvidenceFormDialog } from "./evidence-form-dialog";
import { competencyLabel, evidenceDate } from "./lib/format";
import { evidenceError } from "./lib/messages";
import { EVIDENCIAS_PATH, evidenceFilters, inactivarEvidencia } from "./services/evidence-service";
import type { Evidence, EvidenceCompetency, EvidencePeriod, EvidenceStudent } from "./types";
import styles from "./evidences.module.css";

/** Evidencias de un estudiante en una asignación, filtradas por competencia y período. */
export function EvidenceList({
  assignment,
  student,
  competencies,
  periods,
  competenciaId,
  periodoId,
  canWrite,
  notice,
}: {
  assignment: OwnAssignment;
  student: EvidenceStudent;
  competencies: EvidenceCompetency[];
  periods: EvidencePeriod[];
  competenciaId: string;
  periodoId: string;
  canWrite: boolean;
  /** Mensaje de éxito de una acción hecha fuera de la lista (registro). */
  notice: string;
}) {
  const list = usePaged<Evidence>(
    EVIDENCIAS_PATH,
    "",
    evidenceFilters({
      asignacionId: String(assignment.id),
      alumnoId: String(student.id),
      competenciaId,
      periodoId,
    }),
  );
  const [editing, setEditing] = useState<Evidence | null>(null);
  const [removing, setRemoving] = useState<Evidence | null>(null);
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const competencyName = (evidence: Evidence) => {
    if (evidence.competenciaNombre) return competencyLabel({
      competenciaNombre: evidence.competenciaNombre, competenciaCodigo: evidence.competenciaCodigo,
    });
    const found = competencies.find(item => item.competenciaId === evidence.competenciaId);
    return found ? competencyLabel(found) : `Competencia ${evidence.competenciaId}`;
  };
  const periodName = (id: number) => periods.find((item) => item.id === id)?.nombre ?? `Período ${id}`;
  async function download(file: NonNullable<Evidence["archivo"]>) {
    setError("");
    try {
      await descargarArchivo(file);
    } catch (e) {
      setError(evidenceError(e));
    }
  }
  async function confirmRemove(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!removing) return;
    setBusy(true);
    setDialogError("");
    try {
      await inactivarEvidencia(removing.id);
      setRemoving(null);
      setSuccess("Evidencia inactivada.");
      list.refresh();
    } catch (e) {
      setDialogError(evidenceError(e));
    } finally {
      setBusy(false);
    }
  }
  const total = list.data?.totalElements ?? 0;
  return (
    <>
      <PanelHeading
        title="Evidencias registradas"
        summary={list.data ? countLabel(total, "evidencia", "evidencias") : student.nombre}
      />
      <Feedback error={list.error || error} success={success || notice} />
      {list.loading ? (
        <Loading>Cargando evidencias…</Loading>
      ) : list.data?.content.length ? (
        <div className="user-table-wrap">
          <table className="user-table">
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Competencia</th>
                <th>Período</th>
                <th>Descripción</th>
                <th>Archivo</th>
                {canWrite && <th>Acciones</th>}
              </tr>
            </thead>
            <tbody>
              {list.data.content.map((item) => (
                <tr key={item.id}>
                  <td data-label="Fecha">{evidenceDate(item.fechaCreacion)}</td>
                  <td data-label="Competencia">{competencyName(item)}</td>
                  <td data-label="Período">{periodName(item.periodoId)}</td>
                  <td data-label="Descripción">
                    <span className={styles.description}>{item.descripcion || "Sin descripción"}</span>
                  </td>
                  <td data-label="Archivo">
                    {item.archivo ? (
                      <button
                        type="button"
                        className={styles.linkButton}
                        onClick={() => download(item.archivo!)}
                      >
                        {item.archivo.nombreOriginal}
                      </button>
                    ) : (
                      "—"
                    )}
                  </td>
                  {canWrite && (
                    <td data-label="Acciones">
                      <RowMenu
                        itemName={`evidencia ${item.id}`}
                        options={[
                          {
                            label: "Editar",
                            onSelect: () => {
                              setSuccess("");
                              setEditing(item);
                            },
                          },
                          {
                            label: "Inactivar",
                            danger: true,
                            onSelect: () => {
                              setSuccess("");
                              setDialogError("");
                              setRemoving(item);
                            },
                          },
                        ]}
                      />
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        !list.error && <p className="empty">Este estudiante no tiene evidencias con estos filtros.</p>
      )}
      <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
      {editing && (
        <EvidenceFormDialog
          assignment={assignment}
          student={student}
          competencies={competencies}
          periods={periods}
          evidence={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            setSuccess("Evidencia actualizada.");
            list.refresh();
          }}
        />
      )}
      {removing && (
        <FormDialog
          title="Inactivar evidencia"
          description={student.nombre}
          submitLabel="Inactivar evidencia"
          danger
          busy={busy}
          error={dialogError}
          onClose={() => setRemoving(null)}
          onSubmit={confirmRemove}
        >
          <p className="full-field hint">
            La evidencia dejará de mostrarse. Si ya se usa en una evaluación, no se podrá inactivar.
          </p>
        </FormDialog>
      )}
    </>
  );
}

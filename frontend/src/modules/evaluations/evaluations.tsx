"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { message } from "@/core/api/client";
import { Feedback, PageHeader, TabNav, type TabItem, Loading } from "@/shared/ui";
import { EvaluationList } from "./evaluation-list";
import { Evidences } from "./evidences/evidences";
import { GradeRegistry } from "./grade-registry";
import { PublishPeriod } from "./progress-reports";
import { listarAsignacionesPropias } from "./services/evaluation-service";
import type { Evaluation, OwnAssignment } from "./types";

const PATH = "/evaluaciones";
const LIST_TAB = "mis-evaluaciones";
const REGISTRY_TAB = "registro-de-calificaciones";
const PUBLISH_TAB = "publicar-notas";
const EVIDENCE_TAB = "evidencias";
const tabs: TabItem[] = [
  { id: LIST_TAB, label: "Mis evaluaciones" },
  { id: REGISTRY_TAB, label: "Registro de calificaciones" },
  { id: PUBLISH_TAB, label: "Libretas" },
  { id: EVIDENCE_TAB, label: "Evidencias" },
];

/** Evaluaciones del docente (US-18 y US-50). La URL es /evaluaciones/{pestaña}/{evaluación}. */
export function Evaluations({
  tab,
  evaluationId,
  canWrite,
}: {
  tab?: string;
  evaluationId?: string;
  /** Permiso CALIFICACIONES_ESCRIBIR: sin él la pantalla es de solo consulta. */
  canWrite: boolean;
}) {
  const router = useRouter();
  const current = [PUBLISH_TAB, REGISTRY_TAB, EVIDENCE_TAB].includes(tab ?? "") ? (tab as string) : LIST_TAB;
  const [assignments, setAssignments] = useState<OwnAssignment[] | null>(null);
  const [error, setError] = useState("");
  const [creating, setCreating] = useState(false);
  useEffect(() => {
    let active = true;
    listarAsignacionesPropias()
      .then((items) => {
        if (active) setAssignments(items);
      })
      .catch((e) => {
        if (active) setError(message(e));
      });
    return () => {
      active = false;
    };
  }, []);
  const parsedId = Number(evaluationId);
  const openGrades = (evaluation: Evaluation) =>
    router.push(`${PATH}/${REGISTRY_TAB}/${evaluation.id}`);
  return (
    <section>
      <PageHeader
        title="Evaluaciones"
        description="Gestiona evaluaciones y calificaciones de tus cursos asignados."
        actions={
          canWrite &&
          current === LIST_TAB && (
            <button
              type="button"
              className="primary-action"
              disabled={!assignments?.length}
              onClick={() => setCreating(true)}
            >
              + Nueva evaluación
            </button>
          )
        }
      />
      <TabNav
        label="Secciones de evaluaciones"
        tabs={tabs}
        current={current}
        onSelect={(id) => router.push(`${PATH}/${id}`)}
      />
      <Feedback error={error} />
      {!assignments ? (
        !error && <Loading>Cargando tus cursos…</Loading>
      ) : current === PUBLISH_TAB ? (
        <PublishPeriod assignments={assignments} canWrite={canWrite} />
      ) : current === EVIDENCE_TAB ? (
        <Evidences assignments={assignments} canWrite={canWrite} />
      ) : current === LIST_TAB ? (
        <EvaluationList
          canWrite={canWrite}
          assignments={assignments}
          creating={creating}
          onCloseCreate={() => setCreating(false)}
          onGrade={openGrades}
        />
      ) : (
        <GradeRegistry
          key={evaluationId ?? "registro"}
          canWrite={canWrite}
          assignments={assignments}
          evaluationId={Number.isSafeInteger(parsedId) && parsedId > 0 ? parsedId : null}
        />
      )}
    </section>
  );
}

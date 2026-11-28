"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { listarCompetenciasDeAsignacion } from "../services/evaluation-service";
import type { OwnAssignment } from "../types";
import { listarEstudiantes, listarPeriodos } from "./services/evidence-service";
import type { EvidenceCompetency, EvidencePeriod, EvidenceStudent } from "./types";

type Loaded = {
  assignmentId: number;
  periods: EvidencePeriod[];
  competencies: EvidenceCompetency[];
  students: EvidenceStudent[];
};

/** Opciones válidas de una asignación; cada cambio de asignación descarta las anteriores. */
export function useEvidenceContext(assignment: OwnAssignment | undefined) {
  const [result, setResult] = useState<{ data: Loaded } | { error: string } | null>(null);
  const [attempt, setAttempt] = useState(0);
  const id = assignment?.id;
  const [previousId, setPreviousId] = useState(id);
  if (previousId !== id) {
    setPreviousId(id);
    setResult(null);
  }
  useEffect(() => {
    if (id === undefined) return;
    let active = true;
    Promise.all([listarPeriodos(id), listarCompetenciasDeAsignacion(id), listarEstudiantes(id)])
      .then(([periods, competencies, students]) => {
        if (active) setResult({ data: { assignmentId: id, periods, competencies, students } });
      })
      .catch((e) => {
        if (active) setResult({ error: message(e) });
      });
    return () => {
      active = false;
    };
  }, [id, attempt]);
  return {
    context: result && "data" in result ? result.data : null,
    error: result && "error" in result ? result.error : "",
    loading: id !== undefined && result === null,
    retry: () => {
      setResult(null);
      setAttempt((value) => value + 1);
    },
  };
}

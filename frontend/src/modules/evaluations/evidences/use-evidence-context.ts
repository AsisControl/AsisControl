"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { listarCompetenciasDelCurso } from "../services/evaluation-service";
import type { OwnAssignment } from "../types";
import { listarEstudiantes, listarPeriodos } from "./services/evidence-service";
import type { EvidenceCompetency, EvidencePeriod, EvidenceStudent } from "./types";

type Loaded = {
  assignmentId: number;
  periods: EvidencePeriod[];
  competencies: EvidenceCompetency[];
  /** `null`: la asignación no tiene evaluaciones y el backend no ofrece otra fuente. */
  students: EvidenceStudent[] | null;
};

/** Opciones válidas de una asignación; cada cambio de asignación descarta las anteriores. */
export function useEvidenceContext(assignment: OwnAssignment | undefined) {
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [failed, setFailed] = useState<{ assignmentId: number; error: string } | null>(null);
  const id = assignment?.id;
  const courseId = assignment?.cursoId;
  useEffect(() => {
    if (id === undefined || courseId === undefined) return;
    let active = true;
    Promise.all([listarPeriodos(id), listarCompetenciasDelCurso(courseId), listarEstudiantes(id)])
      .then(([periods, competencies, students]) => {
        if (active) setLoaded({ assignmentId: id, periods, competencies, students });
      })
      .catch((e) => {
        if (active) setFailed({ assignmentId: id, error: message(e) });
      });
    return () => {
      active = false;
    };
  }, [id, courseId]);
  const ready = loaded?.assignmentId === id ? loaded : null;
  const error = failed && failed.assignmentId === id ? failed.error : "";
  return { context: ready, error, loading: id !== undefined && !ready && !error };
}

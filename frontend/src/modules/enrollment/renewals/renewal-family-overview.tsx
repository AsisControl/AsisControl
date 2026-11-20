"use client";

import { useEffect, useState } from "react";
import { api, message } from "@/core/api/client";
import { Feedback, Loading, PersonCell, StatusPill } from "@/shared/ui";
import {
  getFamilyRenewalHistory,
  getRenewalOptions,
  type RenewalOption,
  type RenewalRecord,
} from "../services/renewal-service";
import { renewalLabels } from "./renewal-labels";
import styles from "./renewal-family-overview.module.css";

type Child = { id: number; codigoAlumno: string; nombreCompleto: string };
type Overview = { children: Child[]; options: RenewalOption[]; records: RenewalRecord[] };

export function RenewalFamilyOverview({
  canWrite,
  refreshKey,
  onRequest,
  onView,
}: {
  canWrite: boolean;
  refreshKey: number;
  onRequest: (previousEnrollmentId: number) => void;
  onView: (record: RenewalRecord) => void;
}) {
  const [overview, setOverview] = useState<Overview | null>(null);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    api<Child[]>("/api/familia/estudiantes").then(async (children) => {
      if (!active) return;
      if (!children.length) {
        setOverview({ children, options: [], records: [] });
        setError("");
        return;
      }
      const [options, records] = await Promise.all([getRenewalOptions(), getFamilyRenewalHistory()]);
      if (!active) return;
      setOverview({ children, options, records });
      setError("");
    }).catch((cause) => {
      if (active) setError(message(cause));
    });
    return () => { active = false; };
  }, [refreshKey, attempt]);

  const latest = new Map<number, RenewalRecord>();
  for (const record of overview?.records ?? []) {
    if (!latest.has(record.idAlumno) || latest.get(record.idAlumno)!.id < record.id) {
      latest.set(record.idAlumno, record);
    }
  }

  return (
    <section className={styles.section} aria-labelledby="family-renewals-title">
      <div className={styles.heading}>
        <h3 id="family-renewals-title">Renovación por estudiante</h3>
        <p>Consulta quién puede solicitar la matrícula del próximo año y sigue cada trámite.</p>
      </div>
      {error && <div className={styles.error}><Feedback error={error} /><button type="button" className="secondary-action" onClick={() => { setError(""); setOverview(null); setAttempt((value) => value + 1); }}>Reintentar</button></div>}
      {!overview && !error && <Loading skeleton="list" rows={2}>Cargando estudiantes y opciones de renovación…</Loading>}
      {overview?.children.length === 0 && <p className="empty">No tienes estudiantes asociados. Solicita a la institución que revise el vínculo familiar.</p>}
      {overview && overview.children.length > 0 && (
        <div className={styles.grid}>
          {overview.children.map((child) => {
            const option = overview.options.find((item) => item.idAlumno === child.id);
            const record = latest.get(child.id);
            const current = record && (!option || record.anioDestino === option.anioDestino) ? record : null;
            return (
              <article className={styles.card} key={child.id} aria-label={`Renovación de ${child.nombreCompleto}`}>
                <div className={styles.cardHeading}>
                  <PersonCell name={child.nombreCompleto} detail={child.codigoAlumno} />
                  {current && <StatusPill state={current.estado} label={renewalLabels[current.estado]} tone={current.estado === "APROBADA" ? "success" : undefined} />}
                </div>
                {option ? (
                  <p className={styles.detail}>Próximo año: {option.anioDestino} · {option.gradoDestino}.° grado</p>
                ) : current ? (
                  <p className={styles.detail}>Último trámite: {current.anioDestino} · {current.gradoDestino}.° grado</p>
                ) : (
                  <p className={styles.detail}>No hay una renovación disponible por ahora. Puede depender de la convocatoria o de la matrícula anterior.</p>
                )}
                {current?.estado === "RECHAZADA" && current.motivo && <p className={styles.reason}>Motivo: {current.motivo}</p>}
                <div className={styles.actions}>
                  {option && canWrite && (
                    <button type="button" className="primary-action" onClick={() => onRequest(option.idMatriculaAnterior)}>
                      Renovar matrícula
                    </button>
                  )}
                  {current && (
                    <button type="button" className="secondary-action" onClick={() => onView(current)}>
                      Ver estado
                    </button>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}
      <p className={styles.note}>La promoción, los pagos vencidos y las vacantes se verifican cuando la institución revisa la solicitud.</p>
    </section>
  );
}

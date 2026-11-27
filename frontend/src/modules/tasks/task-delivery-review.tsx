"use client";
import { useEffect, useState, type FormEvent } from "react";
import { api } from "@/core/api/client";
import { Dialog, Feedback, Field, Loading } from "@/shared/ui";
import { AchievementButtons, type AchievementChoice } from "@/shared/ui/achievement-buttons";
import { obtenerEntrega } from "./services/entrega-service";
import { TaskDeliveryFiles } from "./task-delivery-files";
import { message } from "./task-error-copy";
import type { EntregaTareaResponse, SeguimientoAlumno, TareaResponse } from "./types";
import styles from "./tasks.module.css";

type Nota = { idCalificacion: number; idCompetencia: number; notaNumerica: number | null; nivelLogro: string; observacion: string | null; version: number };
type Planilla = {
  evaluacion: { estado: string; modeloCualitativo?: boolean; competencias: { id: number; nombre: string }[] };
  alumnos: { idAlumno: number; notas: Nota[] }[];
};
type Revision = { nota: number | null; nivelLogro: AchievementChoice | null; observacion: string | null; devolucion: string | null };

export function TaskDeliveryReview({ task, student, canGrade, onClose, onSaved }: {
  task: TareaResponse; student: SeguimientoAlumno; canGrade: boolean; onClose: () => void; onSaved: () => void;
}) {
  const idEvaluacion = task.evaluacionAcademica?.id;
  const withoutDelivery = student.entregaId == null;
  const reviewPath = `/api/tareas/${task.id}/calificaciones-sin-entrega/${student.alumnoId}`;
  const [snapshot, setSnapshot] = useState<{ delivery: EntregaTareaResponse | null; planilla: Planilla | null; revision: Revision | null }>();
  const [edited, setEdited] = useState<Record<number, { nota: string; nivel?: AchievementChoice; observacion: string }>>({});
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [devolucion, setDevolucion] = useState<string | null>(null);
  const [notice, setNotice] = useState("");
  const [discard, setDiscard] = useState(false);
  const [correcting, setCorrecting] = useState(false);
  const [motivo, setMotivo] = useState("");
  const [confirmCorrection, setConfirmCorrection] = useState(false);
  const [removing, setRemoving] = useState<Nota | null>(null);
  const requestClose = () => devolucion !== null || Object.keys(edited).length ? setDiscard(true) : onClose();
  useEffect(() => {
    let mounted = true;
    Promise.all([
      student.entregaId == null ? null : obtenerEntrega(student.entregaId),
      idEvaluacion == null ? null : api<Planilla>(`/api/evaluaciones/${idEvaluacion}/planilla?idAlumno=${student.alumnoId}`),
      withoutDelivery && (task.estado === "CERRADA" || task.estado === "ARCHIVADA") ? api<Revision>(reviewPath) : null,
    ]).then(([delivery, planilla, revision]) => {
      if (delivery && (delivery.tareaId !== task.id || delivery.alumnoId !== student.alumnoId)) {
        throw new Error("La entrega no corresponde a este estudiante.");
      }
      if (mounted) setSnapshot({ delivery, planilla, revision });
    }).catch(reason => { if (mounted) setError(message(reason)); });
    return () => { mounted = false; };
  }, [idEvaluacion, student.alumnoId, student.entregaId, task.id, task.estado, withoutDelivery, reviewPath]);

  const row = snapshot?.planilla?.alumnos.find(item => item.idAlumno === student.alumnoId);
  const canReviewMissing = canGrade && withoutDelivery && task.estado === "CERRADA" && Boolean(snapshot?.revision);
  const canDraft = Boolean(canGrade && (task.estado === "PUBLICADA" || task.estado === "CERRADA") && student.estado !== "ANULADO"
    && (!withoutDelivery || canReviewMissing) && row && snapshot?.planilla?.evaluacion.estado === "BORRADOR");
  const canGradeGeneral = canReviewMissing && !idEvaluacion;
  const notas = row?.notas ?? [];
  const canCorrect = canGrade && (task.estado === "PUBLICADA" || task.estado === "CERRADA")
    && snapshot?.planilla?.evaluacion.estado === "PUBLICADA" && notas.length > 0;
  const canEdit = canDraft || canGradeGeneral || (canCorrect && correcting);
  const cualitativa = idEvaluacion ? snapshot?.planilla?.evaluacion.modeloCualitativo === true : task.modeloCualitativo === true;
  const delivery = snapshot?.delivery;
  const revision = snapshot?.revision;
  const feedback = delivery?.comentario ?? revision?.devolucion;
  const canReturn = canReviewMissing || Boolean(canGrade && delivery && delivery.estado !== "ANULADO");
  async function saveComment() {
    if (!canReturn || !snapshot || saving || devolucion === null) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const comentario = devolucion.trim() || null;
      if (delivery) {
        const updated = await api<EntregaTareaResponse>(`/api/entregas/${delivery.id}/devolucion`, {
          method: "PUT", body: JSON.stringify({ comentario, version: delivery.version }),
        });
        setSnapshot({ ...snapshot, delivery: updated });
      } else if (revision) {
        await api(`${reviewPath}/devolucion`, { method: "PUT", body: JSON.stringify({ comentario }) });
        setSnapshot({ ...snapshot, revision: { ...revision, devolucion: comentario } });
      }
      setDevolucion(null);
      setNotice("Devolución guardada.");
    } catch (reason) { setError(message(reason)); }
    finally { setSaving(false); }
  }
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canEdit || saving || devolucion !== null || !Object.keys(edited).length) return;
    if (correcting) {
      if (motivo.trim()) setConfirmCorrection(true);
      return;
    }
    const calificaciones = Object.entries(edited).map(([id, grade]) => ({
      idEvaluacion, idAlumno: student.alumnoId, idCompetencia: Number(id),
      nivelLogro: cualitativa ? grade.nivel : undefined,
      notaNumerica: cualitativa ? undefined : grade.nota,
      observacion: grade.observacion.trim() || null, versionEsperada: notas.find(note => note.idCompetencia === Number(id))?.version ?? -1,
    }));
    const max = canGradeGeneral ? task.puntajeMaximo : 20;
    if (calificaciones.some(grade => cualitativa ? !grade.nivelLogro
      : !/^\d{1,4}(?:\.\d{1,10})?$/.test(grade.notaNumerica ?? "") || Number(grade.notaNumerica) > max)) {
      setError(cualitativa ? "Selecciona un nivel de logro." : `Escribe notas válidas entre 0 y ${max}.`); return;
    }
    setSaving(true);
    setError("");
    try {
      if (canGradeGeneral) {
        const grade = edited[0];
        await api(reviewPath, { method: "PUT", body: JSON.stringify({
          nivelLogro: cualitativa ? grade.nivel : undefined,
          nota: cualitativa ? undefined : grade.nota,
          observacion: grade.observacion.trim() || null,
        }) });
      } else {
        await api(`/api/tareas/${task.id}/calificaciones`, { method: "PUT", body: JSON.stringify({ calificaciones }) });
      }
      onSaved();
    } catch (reason) { setError(message(reason)); }
    finally { setSaving(false); }
  }
  async function saveCorrection() {
    const correcciones = notas.filter(note => edited[note.idCompetencia]).map(note => {
      const grade = edited[note.idCompetencia];
      return {
        idCalificacion: note.idCalificacion,
        version: note.version,
        ...(cualitativa || note.notaNumerica == null ? { nivelLogro: grade.nivel } : { notaNumerica: grade.nota }),
      };
    });
    setSaving(true);
    setError("");
    try {
      await api(`/api/tareas/${task.id}/correcciones`, {
        method: "POST", body: JSON.stringify({ motivo: motivo.trim(), correcciones }),
      });
      onSaved();
    } catch (reason) {
      setConfirmCorrection(false);
      setError(message(reason));
    } finally { setSaving(false); }
  }
  async function removeGrade() {
    if (!removing || saving) return;
    setSaving(true);
    setError("");
    try {
      await api(`/api/tareas/${task.id}/calificaciones/${removing.idCalificacion}?version=${removing.version}`, { method: "DELETE" });
      onSaved();
    } catch (reason) {
      setRemoving(null);
      setError(message(reason));
    } finally { setSaving(false); }
  }

  return <><Dialog title="Revisar entrega" size="lg" busy={saving} active={!discard && !confirmCorrection && !removing} onClose={requestClose}
    description={`${student.nombreCompleto}${student.codigoAlumno ? ` · ${student.codigoAlumno}` : ""} · ${task.nombre}`}>
    <Feedback error={error} success={notice} />
    {!snapshot && !error && <Loading variant="inline">Cargando entrega…</Loading>}
    {snapshot && <>
      {snapshot.delivery ? <section className={styles.reviewContent} aria-label="Entrega del estudiante">
        <h4>Entrega del estudiante</h4>
        <p>{snapshot.delivery.fechaHoraEntrega.slice(0, 10).split("-").reverse().join("/")} · {snapshot.delivery.fechaHoraEntrega.slice(11, 16)} · {student.estado === "ENTREGADO_TARDE" ? "Entregado tarde" : "Entrega registrada"}</p>
        {snapshot.delivery.respuesta && <p className={styles.deliveryComment}>{snapshot.delivery.respuesta}</p>}
        <TaskDeliveryFiles files={snapshot.delivery.archivos} />
      </section> : <p>Sin entrega. El estudiante no presentó una entrega.</p>}
      <form onSubmit={save}>
        {snapshot.planilla?.evaluacion.estado === "PUBLICADA" && <p className="hint">Calificaciones publicadas</p>}
        {snapshot.planilla?.evaluacion.competencias.map(item => {
          const note = notas.find(grade => grade.idCompetencia === item.id);
          const value = edited[item.id] ?? { nota: note?.notaNumerica == null ? "" : String(note.notaNumerica),
            nivel: note?.nivelLogro as AchievementChoice | undefined, observacion: note?.observacion ?? "" };
          return <fieldset className={styles.reviewCompetency} key={item.id}>
            <legend>{item.nombre}</legend>
            {(canDraft || (canCorrect && correcting && note)) ? <div className={styles.reviewGradeFields}>
              {cualitativa || (correcting && note?.notaNumerica == null) ? <AchievementButtons label={item.nombre} selected={value.nivel} disabled={saving}
                correction={!!note && value.nivel !== note.nivelLogro}
                onChange={nivel => setEdited({ ...edited, [item.id]: { ...value, nivel } })} />
                : <Field label="Nota" type="number" min={0} max={20} step="any" value={value.nota}
                  onChange={event => setEdited({ ...edited, [item.id]: { ...value, nota: event.target.value } })} />}
              {canDraft && cualitativa && value.nivel && <button type="button"
                aria-label={`Quitar nivel de ${item.nombre}`} title="Quitar nivel" disabled={saving}
                className={styles.removeLevel} onClick={() => {
                  if (edited[item.id]) {
                    const remaining = { ...edited };
                    delete remaining[item.id];
                    setEdited(remaining);
                  } else if (note && Object.keys(edited).length === 0 && devolucion === null) {
                    setRemoving(note);
                  }
                }}><span aria-hidden="true">×</span></button>}
              {canDraft && <label className="field">Observación opcional<textarea rows={1} maxLength={1000} value={value.observacion}
                onChange={event => setEdited({ ...edited, [item.id]: { ...value, observacion: event.target.value } })} /></label>}
            </div> : <p>{note?.notaNumerica ?? note?.nivelLogro ?? "Sin calificar"}{note?.observacion && ` · ${note.observacion}`}</p>}
          </fieldset>;
        })}
        {canGradeGeneral ? (() => {
          const general = edited[0] ?? { nivel: revision?.nivelLogro ?? undefined, nota: String(revision?.nota ?? ""), observacion: revision?.observacion ?? "" };
          return <fieldset className={styles.reviewCompetency}>
            <legend>Calificación general</legend>
            {cualitativa ? <AchievementButtons label="Nivel general" selected={general.nivel} disabled={saving}
              onChange={nivel => setEdited({ 0: { ...general, nivel } })} />
              : <Field label="Nota" type="number" min={0} max={task.puntajeMaximo} step="any" value={general.nota}
                onChange={event => setEdited({ 0: { ...general, nota: event.target.value } })} />}
            <label className="field">Observación opcional
              <textarea rows={2} maxLength={1000} value={general.observacion}
                onChange={event => setEdited({ 0: { ...general, observacion: event.target.value } })} />
            </label>
          </fieldset>;
        })() : !idEvaluacion && <p>{student.nivelLogro ?? (student.nota == null ? "Sin calificar" : `Nota: ${student.nota} / ${task.puntajeMaximo}`)}
          {revision?.observacion && ` · ${revision.observacion}`}</p>}
        {canCorrect && !correcting && <button type="button" onClick={() => setCorrecting(true)}>Corregir calificación</button>}
        {correcting && <label className="field">Motivo de la corrección
          <textarea required maxLength={500} value={motivo} onChange={event => setMotivo(event.target.value)} />
        </label>}
        {canEdit && <div className={styles.reviewActions}>
          {correcting && <button type="button" disabled={saving} onClick={() => {
            setCorrecting(false); setEdited({}); setMotivo("");
          }}>Cancelar corrección</button>}
          {canEdit && <button type="submit" className="primary-action" disabled={saving || devolucion !== null || !Object.keys(edited).length || (correcting && !motivo.trim())}>
            {correcting ? "Revisar corrección" : "Guardar calificación"}
          </button>}
        </div>}
      </form>
      {(canReturn || feedback) && <section className={styles.reviewFeedback} aria-label="Devolución docente">
        {canReturn ? <>
          <label className="field">Devolución docente (opcional)
            <textarea rows={1} value={devolucion ?? feedback ?? ""} maxLength={2000} disabled={saving}
              onChange={event => setDevolucion(event.target.value)} />
          </label>
          <button type="button" className="secondary-action" disabled={saving || devolucion === null}
            onClick={() => void saveComment()}>Guardar devolución</button>
        </> : <p>Devolución docente: {feedback}</p>}
      </section>}
    </>}
  </Dialog>
  {removing && <Dialog title="¿Quitar el nivel registrado?" role="alertdialog" busy={saving} onClose={() => setRemoving(null)}>
    <div className="form-actions">
      <button type="button" disabled={saving} onClick={() => setRemoving(null)}>Conservar</button>
      <button className="primary-action" type="button" disabled={saving} onClick={() => void removeGrade()}>Quitar nivel</button>
    </div>
  </Dialog>}
  {confirmCorrection && <Dialog title="Confirmar corrección de calificaciones" role="alertdialog" busy={saving} onClose={() => setConfirmCorrection(false)}>
    <p>Se registrará una nueva versión de las calificaciones con el motivo indicado.</p>
    <div className="form-actions">
      <button type="button" disabled={saving} onClick={() => setConfirmCorrection(false)}>Volver</button>
      <button type="button" className="primary-action" disabled={saving} onClick={() => void saveCorrection()}>Confirmar corrección</button>
    </div>
  </Dialog>}
  {discard && <Dialog title="¿Descartar cambios de la entrega?" role="alertdialog" onClose={() => setDiscard(false)}>
    <div className="form-actions">
      <button type="button" onClick={() => setDiscard(false)}>Seguir editando</button>
      <button type="button" className="primary-action" onClick={onClose}>Descartar cambios</button>
    </div>
  </Dialog>}</>;
}

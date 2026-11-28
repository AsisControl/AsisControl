"use client";

import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { message } from "@/core/api/client";
import { Dialog, Feedback, FormDialog, Loading, StatusPill } from "@/shared/ui";
import type { Student } from "@/modules/students";
import { kinshipLabel, readRelationshipFields, RelationshipFields } from "./relationship-fields";
import {
  changeRelationshipState,
  editRelationship,
  listRelationships,
  relationshipHistory,
  type Relationship,
} from "./services/relationship-service";
import styles from "./academic-records.module.css";

type Action = "editar" | "finalizar" | "reactivar" | "historial";
const actions = {
  editar: { title: "Editar vínculo", submit: "Guardar cambios", success: "Vínculo actualizado." },
  finalizar: { title: "Finalizar vínculo", submit: "Finalizar vínculo", success: "Vínculo finalizado." },
  reactivar: { title: "Reactivar vínculo", submit: "Reactivar vínculo", success: "Vínculo reactivado." },
};
const auditLabels: Record<string, string> = {
  ASOCIAR_APODERADO: "Vínculo registrado",
  EDITAR_VINCULO_APODERADO: "Vínculo editado",
  DESVINCULAR_APODERADO: "Vínculo finalizado",
  REACTIVAR_VINCULO_APODERADO: "Vínculo reactivado",
};
const dateTime = new Intl.DateTimeFormat("es-PE", { dateStyle: "medium", timeStyle: "short" });

function RelationshipRead<T>({ load, children }: { load: () => Promise<T>; children: (data: T) => ReactNode }) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ data: T } | { error: string } | null>(null);
  useEffect(() => {
    let current = true;
    load().then(
      data => { if (current) setResult({ data }); },
      failure => { if (current) setResult({ error: message(failure) }); },
    );
    return () => { current = false; };
  }, [load, attempt]);

  if (!result) return <Loading>Cargando…</Loading>;
  if ("error" in result) return <>
    <Feedback error={result.error} />
    <button type="button" className="secondary-action" onClick={() => {
      setResult(null);
      setAttempt(value => value + 1);
    }}>Reintentar carga</button>
  </>;
  return children(result.data);
}

export function RelationshipManagementDialog({ student, onClose, onChanged }: {
  student: Student;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [load] = useState(() => () => listRelationships(student.id));
  const [selection, setSelection] = useState<{ action: Action; link: Relationship } | null>(null);
  const [notice, setNotice] = useState("");
  const [version, setVersion] = useState(0);
  const canChange = student.activo && student.persona.activo;

  if (selection?.action === "historial") {
    return <RelationshipHistoryDialog studentId={student.id} link={selection.link} onClose={() => setSelection(null)} />;
  }
  if (selection) {
    return <ChangeRelationshipDialog studentId={student.id} link={selection.link} action={selection.action}
      onClose={() => setSelection(null)} onSaved={success => {
        setNotice(success);
        setSelection(null);
        setVersion(value => value + 1);
        onChanged();
      }} />;
  }

  return <Dialog title="Gestionar vínculos" description={student.persona.nombreCompleto} onClose={onClose} size="lg">
    <div className={styles.relationshipStack}>
      <Feedback success={notice} />
      <RelationshipRead key={version} load={load}>{(links) => links.length ?
        <ul className={styles.relationshipList}>{links.map(link => <li key={link.id}>
          <div className={styles.relationshipIdentity}>
            <strong>{link.nombreCompleto}</strong>
            <StatusPill state={link.activo ? "ACTIVO" : "INACTIVO"} label={link.activo ? "Activo" : "Finalizado"}
              tone={link.activo ? "success" : "neutral"} />
          </div>
          <p>{kinshipLabel(link.parentesco)}{link.activo && link.principal ? " · Apoderado principal" : ""}</p>
          {!link.apoderadoActivo && <p className="hint">Ficha del apoderado inactiva</p>}
          <div className={styles.relationshipActions}>
            {canChange && link.activo && <>
              {link.apoderadoActivo && <button type="button" onClick={() => setSelection({ link, action: "editar" })}>Editar</button>}
              <button type="button" onClick={() => setSelection({ link, action: "finalizar" })}>Finalizar</button>
            </>}
            {canChange && !link.activo && link.apoderadoActivo &&
              <button type="button" onClick={() => setSelection({ link, action: "reactivar" })}>Reactivar</button>}
            <button type="button" onClick={() => setSelection({ link, action: "historial" })}>Ver historial</button>
          </div>
        </li>)}</ul> : <p className="empty">El estudiante todavía no tiene vínculos registrados.</p>}
      </RelationshipRead>
    </div>
  </Dialog>;
}

function ChangeRelationshipDialog({ studentId, link, action, onClose, onSaved }: {
  studentId: number;
  link: Relationship;
  action: Exclude<Action, "historial">;
  onClose: () => void;
  onSaved: (notice: string) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const copy = actions[action];
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const data = new FormData(event.currentTarget);
    const motivo = String(data.get("motivo") ?? "").trim();
    if (!motivo) {
      setError("Ingresa el motivo del cambio.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      if (action === "editar") await editRelationship(studentId, link.apoderadoId, { ...readRelationshipFields(data), motivo });
      else await changeRelationshipState(studentId, link.apoderadoId, action === "reactivar", motivo);
      onSaved(copy.success);
    } catch (failure) {
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }
  return <FormDialog title={copy.title} description={link.nombreCompleto} busy={busy}
    error={error} onClose={onClose} onSubmit={save} submitLabel={copy.submit} danger={action === "finalizar"}>
    {action === "editar" && <RelationshipFields kinship={link.parentesco} principal={link.principal} />}
    {action === "finalizar" && <p className={styles.relationshipSummary}>
      El apoderado dejará de tener acceso a la información de este estudiante.
    </p>}
    <label className={`field ${styles.relationshipSummary}`}>
      Motivo del cambio
      <textarea name="motivo" required maxLength={500} rows={3} />
    </label>
  </FormDialog>;
}

function RelationshipHistoryDialog({ studentId, link, onClose }: {
  studentId: number; link: Relationship; onClose: () => void;
}) {
  const [load] = useState(() => () => relationshipHistory(studentId, link.apoderadoId));
  return <Dialog title="Historial del vínculo" description={link.nombreCompleto} onClose={onClose}>
    <RelationshipRead load={load}>{(events) => events.length ? <ol className={styles.relationshipList}>
      {events.toReversed().map(event => <li key={event.id}>
        <strong>{auditLabels[event.accion] ?? "Vínculo actualizado"}</strong>
        <p>{event.actorNombre ?? (event.actor === "SISTEMA" ? "Sistema" : event.actor)}
          {" · "}<time dateTime={event.fecha}>{dateTime.format(new Date(event.fecha))}</time>
        </p>
        {event.motivo && <p>{event.motivo}</p>}
      </li>)}</ol> : <p className="empty">No hay movimientos registrados para este vínculo.</p>}
    </RelationshipRead>
  </Dialog>;
}

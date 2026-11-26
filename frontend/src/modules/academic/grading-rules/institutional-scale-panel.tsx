"use client";

import { useEffect, useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { Dialog, Feedback, Loading } from "@/shared/ui";
import styles from "./grading-rules-panel.module.css";

const PATH = "/api/escala-institucional";
const LEVELS = { C: "En inicio", B: "En proceso", A: "Logro esperado", AD: "Logro destacado" };
type Level = keyof typeof LEVELS;
type Range = { nivel: Level; minimo: number; maximo: number };
type Scale = {
  id: number; numeroVersion: number; version: number;
  estado: "BORRADOR" | "VIGENTE" | "HISTORICA";
  rangos: Range[];
  fechaAplicacion: string | null; actorAplicacion: string | null; motivoAplicacion: string | null;
};
const ORDER = Object.keys(LEVELS) as Level[];
const STATUS = { BORRADOR: "Borrador", VIGENTE: "Vigente", HISTORICA: "Histórica" };
const dateFormat = new Intl.DateTimeFormat("es-PE", { dateStyle: "short", timeStyle: "short", timeZone: "America/Lima" });

export function InstitutionalScalePanel({ canWrite }: { canWrite: boolean }) {
  const [history, setHistory] = useState<Scale[] | null>(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [started, setStarted] = useState(false);
  const [base, setBase] = useState<Scale>();
  const [replace, setReplace] = useState<Scale>();
  function copyHistoricalScale(scale: Scale) {
    setBase(scale);
    setReplace(undefined);
    setStarted(true);
  }
  useEffect(() => {
    let active = true;
    api<Scale[]>(PATH).then(
      scales => { if (active) setHistory(scales); },
      reason => { if (active) setError(message(reason)); },
    );
    return () => { active = false; };
  }, []);

  const current = history?.find(scale => scale.estado === "VIGENTE");
  const draft = history?.find(scale => scale.estado === "BORRADOR");

  function update(scale: Scale) {
    setStarted(scale.estado !== "VIGENTE");
    setHistory(previous => [scale, ...(previous ?? []).filter(item => item.id !== scale.id).map(item =>
      scale.estado === "VIGENTE" && item.estado === "VIGENTE" ? { ...item, estado: "HISTORICA" as const } : item)]);
    setSuccess(scale.estado === "VIGENTE" ? `Escala institucional v${scale.numeroVersion} activada.`
      : `Borrador de escala v${scale.numeroVersion} guardado.`);
  }

  return <section className={styles.card} aria-label="Escala institucional">
    <header className={styles.scaleHeader}>
      <div><h4>Escala institucional</h4><p>Equivalencias de notas y niveles de logro para todo el colegio.</p></div>
      {history && canWrite && !started && <button type="button" className="secondary-action"
        onClick={() => { setBase(undefined); setStarted(true); }}>{draft ? "Continuar borrador" : current ? "Nueva versión" : "Configurar escala"}</button>}
    </header>
    <Feedback error={error} success={success} />
    {!history && !error && <Loading variant="inline">Cargando escala institucional…</Loading>}
    {history && <div className={styles.workspace}>
      {current ? <ScaleDetails scale={current} expanded /> : <p className={styles.scaleEmpty}>Todavía no hay una escala vigente.</p>}
      {canWrite && started && <ScaleEditor key={base?.id ?? "borrador"} draft={draft} base={base} onUpdate={update} onClose={() => setStarted(false)} />}
      {history.some(scale => scale.estado === "HISTORICA") && <section aria-label="Historial de escalas" className={styles.history}>
        <h4>Historial de escalas</h4>
        {history.filter(scale => scale.estado === "HISTORICA").map(scale => <ScaleDetails key={scale.id} scale={scale} action={canWrite && !started ? () => draft ? setReplace(scale) : copyHistoricalScale(scale) : undefined} />)}
      </section>}
    </div>}
    {replace && <Dialog title="¿Reemplazar los rangos del borrador?" role="alertdialog" onClose={() => setReplace(undefined)}>
      <p>El borrador guardado se actualizará cuando guardes la copia.</p>
      <div className={styles.confirmActions}>
        <button type="button" onClick={() => setReplace(undefined)}>Cancelar</button>
        <button type="button" className="primary-action" onClick={() => copyHistoricalScale(replace)}>Usar como base</button>
      </div>
    </Dialog>}
  </section>;
}

function ScaleDetails({ scale, expanded, action }: { scale: Scale; expanded?: boolean; action?: () => void }) {
  const title = `Escala v${scale.numeroVersion} · ${STATUS[scale.estado]}`;
  const content = <div className={styles.scaleOverview}>
    <table className={styles.scaleTable}>
      <thead><tr><th scope="col">Nivel</th><th scope="col">Rango</th></tr></thead>
      <tbody>{scale.rangos.map(({ nivel, minimo, maximo }) => <tr key={nivel}>
        <th scope="row">{nivel} · {LEVELS[nivel]}</th><td>{minimo} a {maximo}</td>
      </tr>)}</tbody>
    </table>
    <dl className={styles.scaleMetadata}>
      {scale.fechaAplicacion && <div><dt>Fecha de aplicación</dt><dd>{dateFormat.format(new Date(scale.fechaAplicacion))}</dd></div>}
      {scale.actorAplicacion && <div><dt>Activada por</dt><dd>{scale.actorAplicacion}</dd></div>}
      {scale.motivoAplicacion && <div><dt>Motivo de activación</dt><dd>{scale.motivoAplicacion}</dd></div>}
    </dl>
  </div>;
  return expanded ? <section className={styles.currentScale}>
    <h5>{title}</h5>{content}
  </section> : <details><summary>{title}</summary>{content}{action && <button type="button" className="secondary-action" onClick={action}>Usar como base</button>}</details>;
}

type Bounds = { minimo: string; maximo: string };
function toFields(ranges: Range[] = []) {
  return Object.fromEntries(ORDER.map(level => {
    const range = ranges.find(item => item.nivel === level);
    return [level, { minimo: range ? String(range.minimo) : "", maximo: range ? String(range.maximo) : "" }];
  })) as Record<Level, Bounds>;
}

function ScaleEditor({ draft, base, onUpdate, onClose }: { draft?: Scale; base?: Scale; onUpdate: (scale: Scale) => void; onClose: () => void }) {
  const [fields, setFields] = useState(() => toFields(base?.rangos ?? draft?.rangos));
  const [dirty, setDirty] = useState(Boolean(base));
  const [discard, setDiscard] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const ranges = ORDER.filter(level => fields[level].minimo !== "" && fields[level].maximo !== "")
    .map(nivel => ({ nivel, minimo: Number(fields[nivel].minimo), maximo: Number(fields[nivel].maximo) }));
  const partial = ORDER.some(level => (fields[level].minimo === "") !== (fields[level].maximo === ""));
  const invalid = ranges.find(({ minimo, maximo }) =>
    !Number.isInteger(minimo) || !Number.isInteger(maximo) || minimo < 0 || maximo > 20 || minimo > maximo);
  const overlaps = ranges.some((range, index) => ranges.slice(0, index)
    .some(previous => range.minimo <= previous.maximo && previous.minimo <= range.maximo));
  const outOfOrder = ranges.some((range, index) => index > 0 && ranges[index - 1].maximo >= range.minimo);
  const problem = partial ? "Completa Desde y Hasta en cada rango que hayas empezado."
    : invalid ? `Revisa el nivel ${invalid.nivel}. Usa enteros de 0 a 20 y un valor Desde menor o igual a Hasta.`
      : overlaps ? "Hay rangos superpuestos. Cada nota debe corresponder a un solo nivel."
        : outOfOrder ? "Ordena los rangos de menor a mayor: C, B, A y AD." : "";
  const valid = !problem;
  const complete = valid && ranges.length === 4 && ranges[0].minimo === 0 && ranges[3].maximo === 20
    && ranges.slice(1).every(({ minimo }, index) => minimo === ranges[index].maximo + 1);
  const canActivate = Boolean(draft) && complete && !dirty && !busy;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || (confirm ? !canActivate || !reason.trim() : !valid)) return;
    setBusy(true);
    setError("");
    try {
      const scale = confirm && draft
        ? await api<Scale>(`${PATH}/${draft.id}/activar`, {
            method: "POST", body: JSON.stringify({ version: draft.version, motivo: reason.trim() }),
          })
        : await api<Scale>(`${PATH}/borrador`, {
            method: "PUT", body: JSON.stringify({ rangos: ranges, version: draft?.version }),
          });
      setConfirm(false);
      setFields(toFields(scale.rangos));
      setDirty(false);
      onUpdate(scale);
    } catch (failure) { setError(message(failure)); }
    finally { setBusy(false); }
  }

  return <>
    <form onSubmit={submit} className={`${styles.editor} ${styles.scaleEditor}`} aria-label="Borrador de escala">
      <h4>{draft ? `Borrador de escala v${draft.numeroVersion}` : "Nueva escala institucional"}</h4>
      <table className={styles.scaleTable}>
        <thead><tr><th scope="col">Nivel de logro</th><th scope="col">Desde</th><th scope="col">Hasta</th></tr></thead>
        <tbody>{ORDER.map(level => <tr key={level}>
          <th scope="row">{level}<small>{LEVELS[level]}</small></th>
          {(["minimo", "maximo"] as const).map(bound => <td key={bound}>
            <input type="number" min="0" max="20" step="1" disabled={busy}
              aria-label={`${bound === "minimo" ? "Mínimo" : "Máximo"} de ${level}`}
              value={fields[level][bound]} onChange={event => {
                setFields({ ...fields, [level]: { ...fields[level], [bound]: event.target.value } });
                setDirty(true);
              }} />
          </td>)}
        </tr>)}</tbody>
      </table>
      {!complete && <p className={problem ? "field-error" : "hint"} aria-live="polite">
        {problem || "Completa los cuatro rangos sin huecos del 0 al 20 para activar."}
      </p>}
      {!confirm && <Feedback error={error} />}
      <div className={styles.actions}>
        <button type="button" className="secondary-action" disabled={busy} onClick={() => dirty ? setDiscard(true) : onClose()}>Cerrar editor</button>
        <button className="primary-action" disabled={!valid || busy}>Guardar escala en borrador</button>
        {draft && <button type="button" className="secondary-action" disabled={!canActivate} onClick={() => {
          setError("");
          setConfirm(true);
        }}>Activar escala</button>}
      </div>
    </form>
    {discard && <Dialog title="¿Descartar cambios de la escala?" role="alertdialog" onClose={() => setDiscard(false)}
      description="Se conservará la última versión guardada.">
      <div className={styles.confirmActions}>
        <button type="button" onClick={() => setDiscard(false)}>Seguir editando</button>
        <button type="button" className="primary-action" onClick={onClose}>Descartar cambios</button>
      </div>
    </Dialog>}
    {confirm && <Dialog title="Activar escala institucional" role="alertdialog" busy={busy}
      description="La versión del borrador se aplicará a nuevas publicaciones. Los resultados publicados conservarán su escala."
      onClose={() => { setConfirm(false); setError(""); }}>
      <form onSubmit={submit}>
        <label className="field">Motivo de activación
          <textarea required maxLength={300} value={reason} disabled={busy} onChange={event => setReason(event.target.value)} />
        </label>
        <Feedback error={error} />
        <div className={styles.confirmActions}>
          <button type="button" disabled={busy} onClick={() => { setConfirm(false); setError(""); }}>Cancelar</button>
          <button className="primary-action" disabled={!canActivate || !reason.trim()}>Confirmar activación de escala</button>
        </div>
      </form>
    </Dialog>}
  </>;
}

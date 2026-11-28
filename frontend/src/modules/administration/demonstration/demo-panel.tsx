"use client";

import { useEffect, useRef, useState } from "react";
import { ApiError, message } from "@/core/api/client";
import { AccessCredentials, ExcelFilePicker, validateImportFile, type AccessCredential } from "@/modules/students";
import { Dialog, Feedback, PanelHeading } from "@/shared/ui";
import { FILES, STEPS, finishedSteps, loadDemonstration, readPreparation, type Preparation, type FileSpec } from "./demo-service";
import styles from "./demo.module.css";

type Progress = { state: "running" | "done" | "error"; detail?: string };

function validateFile(spec: FileSpec, file: File | null): string {
  if (!file) return `Selecciona ${spec.name}.`;
  const problem = spec.format === "sql"
    ? (!file.name.endsWith(".sql") ? "El archivo debe tener formato .sql." : !file.size || file.size > 1024 * 1024 ? "Selecciona un SQL no vacío de hasta 1 MB." : "")
    : validateImportFile(file);
  return problem || (file.name !== spec.name ? `Selecciona ${spec.name}.` : "");
}

export function DemoPanel() {
  const [preparation, setPreparation] = useState<Preparation | null>(null);
  const [checking, setChecking] = useState(true);
  const [connectionError, setConnectionError] = useState("");
  const [open, setOpen] = useState(false);
  const [files, setFiles] = useState<Partial<Record<number, File>>>({});
  const [fileErrors, setFileErrors] = useState<Record<number, string>>({});
  const [selectionError, setSelectionError] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [complete, setComplete] = useState(false);
  const [progress, setProgress] = useState<Record<string, Progress>>({});
  const [credentials, setCredentials] = useState<AccessCredential[]>([]);
  const running = useRef(false);
  const controller = useRef<AbortController | null>(null);
  const batchInput = useRef<HTMLInputElement>(null);

  async function check() {
    try {
      const state = await readPreparation();
      setConnectionError("");
      setPreparation(state);
      setComplete(finishedSteps(state).size === STEPS.length);
    } catch (cause) {
      setConnectionError(cause instanceof ApiError && cause.status === 404
        ? "La carga de demostración no está habilitada en este servidor. Activa DEMO_DATA_ENABLED=true en el backend y reinícialo."
        : message(cause));
    } finally { setChecking(false); }
  }

  useEffect(() => {
    let active = true;
    void readPreparation().then(state => {
      if (!active) return;
      setPreparation(state);
      setComplete(finishedSteps(state).size === STEPS.length);
    }).catch(cause => {
      if (!active) return;
      setConnectionError(cause instanceof ApiError && cause.status === 404
        ? "La carga de demostración no está habilitada en este servidor. Activa DEMO_DATA_ENABLED=true en el backend y reinícialo."
        : message(cause));
    }).finally(() => { if (active) setChecking(false); });
    return () => { active = false; controller.current?.abort(); };
  }, []);
  useEffect(() => {
    if (!busy) return;
    const prevent = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", prevent);
    return () => window.removeEventListener("beforeunload", prevent);
  }, [busy]);

  const done = new Set([...(preparation ? finishedSteps(preparation) : []),
    ...Object.entries(progress).filter(([, p]) => p.state === "done").map(([id]) => id)]);
  const required = FILES.filter(f => STEPS.some(s => s.files.includes(f.number) && !done.has(s.id)));

  function select(number: number, file: File | null) {
    const spec = FILES.find(f => f.number === number)!;
    const problem = file ? validateFile(spec, file) : "";
    setFiles(previous => ({ ...previous, [number]: file ?? undefined }));
    setFileErrors(previous => ({ ...previous, [number]: problem }));
    setSelectionError(""); setError("");
  }

  function selectBatch(selected: FileList | null) {
    if (!selected) return;
    const unknown: string[] = [];
    const seen = new Set<string>();
    for (const file of Array.from(selected)) {
      if (file.name === "00_LEER_ANTES_DE_IMPORTAR.xlsx") continue;
      const spec = FILES.find(f => f.name === file.name);
      if (!spec || seen.has(file.name)) { unknown.push(file.name); continue; }
      seen.add(file.name); select(spec.number, file);
    }
    setSelectionError(unknown.length ? `Archivos no reconocidos o repetidos: ${unknown.join(", ")}. Usa los nombres del ZIP de demostración.` : "");
    if (batchInput.current) batchInput.current.value = "";
  }

  async function run() {
    if (running.current || !preparation) return;
    const problems = Object.fromEntries(required.flatMap(spec => {
      const file = files[spec.number];
      const problem = validateFile(spec, file ?? null);
      return problem ? [[spec.number, problem]] : [];
    }));
    setFileErrors(problems);
    if (Object.keys(problems).length || selectionError) return;
    running.current = true; setBusy(true); setError("");
    const abort = new AbortController(); controller.current = abort;
    try {
      // Una solicitud anterior puede haberse confirmado aunque su respuesta no llegara.
      const fresh = await readPreparation();
      setPreparation(fresh);
      await loadDemonstration(files, fresh, done, (id, state, detail) => {
        setProgress(previous => ({ ...previous, [id]: { state, detail } }));
      }, items => setCredentials(previous => {
        const all = new Map(previous.map(c => [`${c.role}:${c.username}`, c]));
        items.forEach(c => {
          const key = `${c.role}:${c.username}`;
          if (c.temporaryPassword || !all.get(key)?.temporaryPassword) all.set(key, c);
        });
        return [...all.values()];
      }), abort.signal);
      if (!abort.signal.aborted) {
        const verified = await readPreparation();
        setPreparation(verified);
        const missing = [13, 14].filter(number => !verified.notesReady.includes(number));
        if (missing.length) {
          setProgress(previous => {
            const next = { ...previous };
            missing.forEach(number => { next[String(number)] = { state: "error", detail: "La planilla no tiene los niveles esperados. Revisa el Excel de notas y vuelve a cargarlo." }; });
            return next;
          });
          throw new Error("Faltan niveles en las planillas de Matemática.");
        }
        setComplete(true);
      }
    } catch (cause) { setError(message(cause)); }
    finally { running.current = false; setBusy(false); }
  }

  return <section className="panel">
    <PanelHeading title="Preparar demostración" summary="Carga los Excel y los archivos SQL de la exposición desde una instalación limpia." />
    <p className={styles.description}>Paquete 2026: dos secciones, diez estudiantes matriculados y dos candidatos. Carmen tendrá asociados a Mía y Diego.</p>
    <Feedback error={connectionError} />
    {checking ? <p role="status">Comprobando disponibilidad…</p> : connectionError ?
      <button className="secondary-action" type="button" onClick={() => { setChecking(true); void check(); }}>Volver a comprobar</button> :
      <button className="primary-action" type="button" onClick={() => setOpen(true)}>{complete ? "Ver carga preparada" : "Cargar datos de demostración"}</button>}
    <p className={styles.hint}>Empieza con la base vacía y el administrador inicial. Repetir una etapa completada no restablece los datos de la exposición.</p>
    {open && <Dialog title="Cargar datos de demostración" description="Selecciona los 14 Excel y los 3 SQL descomprimidos. El sistema realizará cada carga en el orden necesario." size="lg" busy={busy} onClose={() => setOpen(false)}>
      {!complete && <>
        <div className={styles.toolbar}>
          <input ref={batchInput} type="file" multiple accept=".xlsx,.sql" tabIndex={-1} aria-label="Seleccionar los Excel y SQL de demostración" className={styles.hidden} disabled={busy} onChange={e => selectBatch(e.target.files)} />
          <button className="secondary-action" type="button" disabled={busy} onClick={() => batchInput.current?.click()}>Seleccionar Excel y SQL</button>
          <span>{required.filter(f => files[f.number] && !fileErrors[f.number]).length} de {required.length} archivos pendientes seleccionados</span>
        </div>
        <Feedback error={selectionError} />
        {!busy && required.length > 0 && <details className={styles.files} open={required.some(f => !files[f.number] || fileErrors[f.number])}>
          <summary>Revisar archivos seleccionados</summary>
          <div className={styles.grid}>{required.map(spec => <div className={styles.file} key={spec.number}>
            <strong>{spec.format === "sql" ? "SQL" : String(spec.number).padStart(2, "0")}. {spec.label}</strong>
            {spec.format === "sql" ? <div className={styles.sqlPicker}>
              <label htmlFor={`demo-sql-${spec.number}`}>SQL de {spec.label}</label>
              <input id={`demo-sql-${spec.number}`} type="file" accept=".sql" disabled={busy}
                aria-invalid={!!fileErrors[spec.number]} aria-describedby={`demo-sql-help-${spec.number}`}
                onChange={e => select(spec.number, e.target.files?.[0] ?? null)} />
              <small id={`demo-sql-help-${spec.number}`}>{fileErrors[spec.number] || files[spec.number]?.name || spec.name}</small>
            </div> : <ExcelFilePicker label={`Excel de ${spec.label}`} file={files[spec.number] ?? null} error={fileErrors[spec.number] ?? ""} disabled={busy} onChange={file => select(spec.number, file)} />}
          </div>)}</div>
        </details>}
      </>}
      <ol className={styles.steps} aria-label="Avance de la preparación" aria-live="polite" aria-busy={busy}>
        {STEPS.map(step => {
          const state = progress[step.id];
          const prepared = done.has(step.id);
          return <li key={step.id} data-state={state?.state ?? (prepared ? "done" : "pending")}>
            <span>{step.label}</span>
            <small>{state?.state === "running" ? "Cargando…" : state?.state === "error" ? "Revisar" : prepared ? "Preparado" : "Pendiente"}</small>
            {state?.state === "error" && <p role="alert" className={styles.stepError}>{state.detail}</p>}
          </li>;
        })}
      </ol>
      {error && !Object.values(progress).some(p => p.state === "error") && <Feedback error={error} />}
      {complete && <Feedback success="Carga completada. Ya puedes recorrer los flujos de demostración." />}
      {credentials.length > 0 && <AccessCredentials credentials={credentials} saved />}
      {complete && <div className={styles.next}>
        <h4>Durante la exposición</h4>
        <ul>{preparation?.etapas.find(s => s.etapa === "ESCENARIOS")?.indicaciones
          .filter(text => text.startsWith("Reporte de Mía:") || text.startsWith("Riesgo:"))
          .map(text => <li key={text}>{text.replace(/\b(\d{4})-(\d{2})-(\d{2})\b/g, "$3/$2/$1")}</li>)}
          <li>Completa el nivel pendiente de Mía y publica las evaluaciones de Matemática; luego genera las libretas.</li>
          <li>Muestra la última vacante después de las notas y libretas. Matricular antes a Sofía añade otra estudiante a las planillas.</li>
          <li>La marcación QR docente está disponible de 07:00 a 10:00, de lunes a viernes, con los horarios del paquete.</li>
          <li>El año 2027 queda en borrador. La renovación requiere cerrar 2026 y activar el siguiente año al terminar sus flujos.</li></ul>
      </div>}
      <div className="form-actions">
        <button className="secondary-action" type="button" disabled={busy} onClick={() => setOpen(false)}>Cerrar</button>
        {!complete && <button className="primary-action" type="button" disabled={busy || !!selectionError} onClick={() => void run()}>
          {busy ? "Preparando demostración…" : Object.values(progress).some(p => p.state === "error") ? "Continuar carga" : "Iniciar carga"}
        </button>}
      </div>
    </Dialog>}
  </section>;
}

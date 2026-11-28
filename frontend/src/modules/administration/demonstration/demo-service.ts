import { api } from "@/core/api/client";
import type { AccessCredential, ImportResult } from "@/modules/students";
import type { BulkImportResult } from "@/modules/bulk-import";

const PATH = "/api/administracion/demostracion";
const TIMEOUT = 180000;
export type Stage = "INICIAL" | "HABILITAR_MATRICULAS" | "ESCENARIOS";
type StageResult = { etapa: Stage; recursos: Record<string, number>; fechaReferencia: string; indicaciones: string[] };
type Status = { paquete: string; etapas: StageResult[] };
export type Preparation = Status & { notesReady: number[] };
export type FileSpec = { number: number; name: string; label: string; path?: string; format?: "sql" };
export const FILES: FileSpec[] = [
  { number: 1, name: "01_estudiantes.xlsx", label: "Estudiantes" },
  { number: 2, name: "02_apoderados.xlsx", label: "Apoderados" },
  { number: 3, name: "03_parentescos.xlsx", label: "Parentescos" },
  { number: 4, name: "04_docentes.xlsx", label: "Docentes", path: "/api/docentes/importaciones" },
  { number: 5, name: "05_competencias.xlsx", label: "Competencias", path: "/api/competencias/importaciones" },
  { number: 6, name: "06_plan_estudios.xlsx", label: "Plan de estudios", path: "/api/planes-estudios/importaciones" },
  { number: 7, name: "07_secciones.xlsx", label: "Secciones", path: "/api/secciones/importaciones" },
  { number: 8, name: "08_aulas.xlsx", label: "Aulas", path: "/api/aulas/importaciones" },
  { number: 9, name: "09_tarifas.xlsx", label: "Tarifas", path: "/api/configuracion-pagos/tarifas/importaciones" },
  { number: 10, name: "10_matriculas.xlsx", label: "Matrículas", path: "/api/matriculas/importaciones" },
  { number: 11, name: "11_asignaciones_docentes.xlsx", label: "Asignaciones docentes", path: "/api/asignaciones-cursos/importaciones" },
  { number: 12, name: "12_horarios.xlsx", label: "Horarios", path: "/api/horarios/importaciones" },
  { number: 13, name: "13_notas_matematica_2A.xlsx", label: "Notas de Matemática · 2.º A" },
  { number: 14, name: "14_notas_matematica_3A.xlsx", label: "Notas de Matemática · 3.º A" },
  { number: 100, name: "00_configuracion_inicial.sql", label: "Configuración inicial", format: "sql" },
  { number: 109, name: "09_habilitar_matriculas.sql", label: "Habilitar matrículas", format: "sql" },
  { number: 112, name: "12_escenarios.sql", label: "Escenarios de la exposición", format: "sql" },
];
export type Step = { id: string; label: string; files: number[]; stage?: Stage };
export const STEPS: Step[] = [
  { id: "INICIAL", label: "SQL: preparar años, períodos y grados", files: [100], stage: "INICIAL" },
  { id: "familias", label: "Cargar estudiantes, apoderados y parentescos", files: [1, 2, 3] },
  ...FILES.filter(f => f.number >= 4 && f.number <= 9).map(f => ({ id: String(f.number), label: `Cargar ${f.label.toLowerCase()}`, files: [f.number] })),
  { id: "HABILITAR_MATRICULAS", label: "SQL: activar el año y habilitar matrículas", files: [109], stage: "HABILITAR_MATRICULAS" },
  ...FILES.filter(f => f.number >= 10 && f.number <= 12).map(f => ({ id: String(f.number), label: `Cargar ${f.label.toLowerCase()}`, files: [f.number] })),
  { id: "ESCENARIOS", label: "SQL: preparar asistencia, evaluaciones, tareas, riesgo y pagos", files: [112], stage: "ESCENARIOS" },
  ...FILES.filter(f => f.number >= 13 && f.number <= 14).map(f => ({ id: String(f.number), label: `Cargar ${f.label.toLowerCase()}`, files: [f.number] })),
];

/** Los puntos de control del servidor permiten continuar sin repetir la base ya preparada. */
export function finishedSteps(preparation: Preparation): Set<string> {
  const stages = new Set(preparation.etapas.map(s => s.etapa));
  return new Set(STEPS.filter(step => step.stage ? stages.has(step.stage)
    : step.files[0] >= 13 ? preparation.notesReady.includes(step.files[0])
      : stages.has(step.files[0] <= 9 ? "HABILITAR_MATRICULAS" : "ESCENARIOS")).map(s => s.id));
}

export async function readPreparation(): Promise<Preparation> {
  const status = await api<Status>(PATH);
  const scenarios = status.etapas.find(s => s.etapa === "ESCENARIOS");
  const notesReady: number[] = [];
  if (scenarios) for (const [number, key, minimum] of [[13, "evaluacionMat2", 9], [14, "evaluacionMat3", 10]] as const) {
    const id = scenarios.recursos[key];
    if (!id) throw new Error("Falta la evaluación de Matemática. Revisa la preparación del servidor.");
    const sheet = await api<{ evaluacion: { estado: string }; alumnos: { notas: { idCalificacion: number | null }[] }[] }>(`/api/evaluaciones/${id}/planilla`);
    if (sheet.evaluacion.estado === "ANULADA")
      throw new Error("Una evaluación de demostración fue anulada. Revisa la evaluación antes de continuar; la carga no la restablecerá.");
    // Nunca reimportar una evaluación publicada ni sobrescribir notas ya preparadas o corregidas.
    if (sheet.evaluacion.estado !== "BORRADOR" || sheet.alumnos.flatMap(a => a.notas).filter(n => n.idCalificacion != null).length >= minimum)
      notesReady.push(number);
  }
  return { ...status, notesReady };
}

function issuesOf(result: BulkImportResult | ImportResult): string[] {
  const issues = "issues" in result ? result.issues : result.errores;
  return issues.map(issue => `Fila ${issue.row}: ${issue.message}`);
}

export async function loadDemonstration(
  files: Partial<Record<number, File>>,
  preparation: Preparation,
  completed: Set<string>,
  update: (id: string, state: "running" | "done" | "error", detail?: string) => void,
  credentials: (items: AccessCredential[]) => void,
  signal: AbortSignal,
): Promise<void> {
  const resources = Object.assign({}, ...preparation.etapas.map(s => s.recursos)) as Record<string, number>;
  const done = new Set([...finishedSteps(preparation), ...completed]);
  for (const step of STEPS) {
    if (signal.aborted) return;
    if (done.has(step.id)) { update(step.id, "done", "Ya preparado"); continue; }
    update(step.id, "running");
    try {
      if (step.stage) {
        const body = new FormData(); body.append("archivo", files[step.files[0]]!);
        const result = await api<StageResult>(`${PATH}?etapa=${step.stage}`, { method: "POST", body, timeoutMs: TIMEOUT });
        Object.assign(resources, result.recursos);
      } else if (step.id === "familias") {
        const body = new FormData();
        ["estudiantes", "apoderados", "parentescos"].forEach((key, index) => body.append(key, files[index + 1]!));
        const result = await api<{ saved: boolean; files: Record<string, { error: string | null; result: ImportResult | null }> }>(
          "/api/alumnos/importaciones/familias?confirmar=true", { method: "POST", body, timeoutMs: TIMEOUT });
        if (result.saved) credentials(Object.values(result.files).flatMap(r => r.result?.credentials ?? []));
        const errors = Object.values(result.files).flatMap(r => r.error ? [r.error] : r.result ? issuesOf(r.result) : ["No se pudo leer un archivo de familia."]);
        if (!result.saved || errors.length) throw new Error(errors.join("\n") || "La carga de familias no se guardó.");
      } else {
        const spec = FILES.find(f => f.number === step.files[0])!;
        const body = new FormData(); body.append("archivo", files[spec.number]!);
        let path = spec.path;
        if (spec.number >= 13) {
          const id = resources[spec.number === 13 ? "evaluacionMat2" : "evaluacionMat3"];
          if (!id) throw new Error("Primero se deben crear las evaluaciones de Matemática.");
          path = `/api/evaluaciones/${id}/calificaciones/importaciones`;
        }
        const result = await api<BulkImportResult | ImportResult>(`${path}?confirmar=true`, { method: "POST", body, timeoutMs: TIMEOUT });
        if ("credentials" in result) credentials(result.credentials ?? []);
        const errors = issuesOf(result);
        if (errors.length) throw new Error(errors.join("\n"));
      }
      done.add(step.id); update(step.id, "done", "Preparado");
    } catch (cause) {
      update(step.id, "error", cause instanceof Error ? cause.message : "No se pudo completar esta etapa.");
      throw cause;
    }
  }
}

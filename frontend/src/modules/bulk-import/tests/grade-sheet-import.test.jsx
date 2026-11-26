import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { Evaluations } from "@/modules/evaluations";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

// OBS-05 · ASISCTRL-234: carga masiva de notas desde la planilla del docente.
const IMPORT_PATH = "/api/evaluaciones/10/calificaciones/importaciones";
const competencies = [{ id: 1, codigo: "C1", nombre: "Resuelve problemas" }];
const evaluation = (estado = "BORRADOR") => ({
  id: 10, idAsignacionCurso: 5, titulo: "Examen parcial", descripcion: null,
  tipoEvaluacion: "EXAMEN", periodoEvaluacion: "BIMESTRAL", estado,
  fechaEvaluacion: "2026-09-22", ponderacionPorcentaje: 100, competencias: competencies,
  idCurso: 100, curso: "Matemática", idSeccion: 8, seccion: "3° A", anioAcademico: 2026,
});
const assignment = { id: 5, cursoId: 100, curso: "Matemática", seccionId: 8, seccion: "A", grado: 3, anioAcademico: 2026 };
const sheet = (estado) => ({
  evaluacion: evaluation(estado),
  alumnos: [{ idAlumno: 1, codigoAlumno: "E-1", nombreCompleto: "Alvarado Medina, Diego", notas: [] }],
});
const excel = () =>
  new File(["x".repeat(10)], "notas.xlsx", {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });

function backend(estado = "BORRADOR", routes = {}) {
  return fakeBackend({
    "GET /api/asignaciones-cursos/mias": page([assignment]),
    "GET /api/evaluaciones/anio-vigente": { anioActivo: 2026 },
    "GET /api/evaluaciones": page([evaluation(estado)]),
    "GET /api/evaluaciones/:id": evaluation(estado),
    "GET /api/evaluaciones/:id/planilla": sheet(estado),
    "GET /api/evaluaciones/competencias": [],
    "GET /api/calificaciones/consolidados": [],
    ...routes,
  });
}
const openSheet = () =>
  render(<Evaluations tab="registro-de-calificaciones" evaluationId="10" canWrite />);
const sheetLoads = (calls) => calls.filter((call) => call.path === "/api/evaluaciones/10/planilla").length;

beforeEach(() => {
  signIn({ role: "DOCENTE", permissions: ["CALIFICACIONES_LEER", "CALIFICACIONES_ESCRIBIR"] });
  setUrl("/evaluaciones");
});

test("En borrador, Carga masiva usa la planilla de la evaluación y recarga las notas al importar", async () => {
  const result = { total: 1, validas: 1, creadas: 0, omitidas: [], errores: [] };
  const calls = backend("BORRADOR", {
    [`POST ${IMPORT_PATH}`]: (call) => (call.query.confirmar === "true" ? { ...result, creadas: 1 } : result),
  });
  const user = userEvent.setup();
  openSheet();
  await user.click(await screen.findByRole("button", { name: "Carga masiva" }));
  const dialog = screen.getByRole("dialog", { name: "Carga masiva de notas" });
  await user.upload(within(dialog).getByLabelText("Archivo Excel de notas", { exact: false }), excel());
  await user.click(within(dialog).getByRole("button", { name: "Revisar archivo" }));
  expect(await within(dialog).findByText(/1 lista para importar/)).toBeTruthy();
  const before = sheetLoads(calls);

  await user.click(within(dialog).getByRole("button", { name: "Importar filas válidas" }));
  expect(await within(dialog).findByText(/1 creada/)).toBeTruthy();
  expect(calls.filter((call) => call.path === IMPORT_PATH).map((call) => call.query.confirmar))
    .toEqual(["false", "true"]);
  await waitFor(() => expect(sheetLoads(calls)).toBeGreaterThan(before));
});

test("Una evaluación publicada no ofrece Carga masiva: se corrige con motivo", async () => {
  backend("PUBLICADA");
  openSheet();
  expect(await screen.findByRole("button", { name: "Guardar cambios" })).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Carga masiva" })).toBeNull();
});

test("Con cambios sin guardar, Carga masiva espera a que se guarden", async () => {
  backend("BORRADOR");
  const user = userEvent.setup();
  openSheet();
  const importButton = await screen.findByRole("button", { name: "Carga masiva" });
  expect(importButton.disabled).toBe(false);
  await user.click(screen.getByRole("button", { name: "AD: Alvarado Medina, Diego, Resuelve problemas" }));
  expect(screen.getByRole("button", { name: "Carga masiva" }).disabled).toBe(true);
});

import { beforeEach, expect, test, vi } from "vitest";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { ProgressPreview } from "../progress-preview";
import { availableReport, progressReport, reportAssignment } from "./report-fixture";

const periods = [{ id: 40, nombre: "Primer bimestre" }, { id: 41, nombre: "Segundo bimestre" }];
const otherStudent = { ...availableReport, idAlumno: 12, alumno: "Luis Díaz" };

function backend(routes = {}) {
  return fakeBackend({
    "GET /api/asignaciones-cursos/mias": page([reportAssignment]),
    "GET /api/evaluaciones/periodos-informe": periods,
    "GET /api/calificaciones/informes-disponibles": [availableReport, otherStudent],
    "GET /api/calificaciones/avance": ({ query }) => query.idAlumno === "11" ? progressReport : { ...progressReport, alumno: "Luis Díaz", filas: [] },
    "GET /api/calificaciones/reporte-avance": {},
    ...routes,
  });
}

beforeEach(() => {
  signIn({ role: "DOCENTE", permissions: ["CALIFICACIONES_LEER", "ACADEMICO_LEER"] });
  URL.createObjectURL = vi.fn(() => "blob:report");
  URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
});

async function openPeriod({ assignment = true, period = true, student = true } = {}) {
  const interaction = userEvent.setup();
  await interaction.click(screen.getByRole("button", { name: "Informe por período" }));
  if (assignment) await interaction.selectOptions(await screen.findByLabelText("Curso y sección"), "5");
  if (period) await interaction.selectOptions(await screen.findByLabelText("Período académico"), "40");
  if (student) await interaction.selectOptions(await screen.findByLabelText("Estudiante"), "11");
  return interaction;
}

test("Informe por período usa IDs reales, muestra cabecera completa, filas y descarga PDF sin escrituras", async () => {
  const calls = backend(); render(<ProgressPreview />);
  const interaction = await openPeriod();
  const article = await screen.findByRole("article", { name: "Informe de progreso por período" });
  for (const value of ["Ana Ramos", "Código: E11", "Matemática · 3° A · 2026", "Docente: María Pérez", "Primer bimestre · 01/03/2026 - 30/04/2026", "Sin registrar", "70 %", "Sin observaciones"])
    expect(within(article).getByText(value)).toBeTruthy();
  expect(within(article).getByText(/Generado:.*Lima/)).toBeTruthy();
  expect(within(article).getByText(/Explica su procedimiento/)).toBeTruthy();
  expect(within(article).getAllByRole("columnheader").map((cell) => cell.textContent)).toEqual(["Competencia", "Evaluación", "Fecha", "Resultado", "Ponderación", "Observación"]);
  expect(screen.getByText(/Consulta la última versión publicada/)).toBeTruthy();
  expect(calls.find((call) => call.path.endsWith("periodos-informe")).query).toEqual({ idAsignacionCurso: "5" });
  expect(calls.find((call) => call.path.endsWith("avance")).query).toEqual({ idAlumno: "11", idAsignacionCurso: "5", idPeriodoAcademico: "40" });
  await interaction.click(screen.getByRole("button", { name: "Descargar PDF" }));
  await waitFor(() => expect(URL.createObjectURL).toHaveBeenCalledTimes(1));
  expect(HTMLAnchorElement.prototype.click.mock.instances.at(-1).download).toBe("avance-11-5-40.pdf");
  expect(calls.find((call) => call.path.endsWith("reporte-avance")).query).toEqual({ idAlumno: "11", idAsignacionCurso: "5", idPeriodoAcademico: "40" });
  expect(calls.every((call) => call.method === "GET")).toBe(true);
  expect(screen.queryByRole("button", { name: /publicar|generar/i })).toBeNull();
});

test("Solo ofrece estudiantes autorizados de la asignación y período seleccionados", async () => {
  const calls = backend({ "GET /api/calificaciones/informes-disponibles": [availableReport,
    { ...availableReport, idAlumno: 90, alumno: "Otro curso", idAsignacionCurso: 9 },
    { ...availableReport, idAlumno: 91, alumno: "Otro período", idPeriodoAcademico: 41 }] });
  render(<ProgressPreview />); await openPeriod({ student: false });
  const select = await screen.findByLabelText("Estudiante");
  expect([...select.options].map((option) => option.textContent)).toEqual(["Selecciona un estudiante", "Ana Ramos"]);
  expect(calls.some((call) => call.path.endsWith("/avance"))).toBe(false);
});

test.each(["student", "period", "assignment", "mode"])("Cambiar %s limpia el detalle y descarta consultas tardías", async (change) => {
  let resolveReport;
  backend({ "GET /api/calificaciones/avance": () => new Promise((resolve) => { resolveReport = resolve; }) });
  render(<ProgressPreview />); const interaction = await openPeriod();
  await waitFor(() => expect(resolveReport).toBeTypeOf("function"));
  expect(screen.getByRole("status").textContent).toContain("Cargando");
  if (change === "student") await interaction.selectOptions(screen.getByLabelText("Estudiante"), "");
  if (change === "period") await interaction.selectOptions(screen.getByLabelText("Período académico"), "41");
  if (change === "assignment") await interaction.selectOptions(screen.getByLabelText("Curso y sección"), "");
  if (change === "mode") await interaction.click(screen.getByRole("button", { name: "Consulta por evaluación" }));
  await act(async () => resolveReport(progressReport));
  expect(screen.queryByRole("article")).toBeNull();
  expect(screen.queryByRole("button", { name: "Descargar PDF" })).toBeNull();
  if (change === "assignment") {
    await interaction.selectOptions(screen.getByLabelText("Curso y sección"), "5");
    expect((await screen.findByLabelText("Período académico")).value).toBe("");
  }
});

test("Cambiar a otro estudiante oculta el anterior y carga su propio informe", async () => {
  backend(); render(<ProgressPreview />); const interaction = await openPeriod();
  await screen.findByRole("article");
  await interaction.selectOptions(screen.getByLabelText("Estudiante"), "12");
  expect(await screen.findByText("No hay resultados disponibles en este informe.")).toBeTruthy();
  expect(within(screen.getByRole("article")).getByRole("heading", { name: "Luis Díaz" })).toBeTruthy();
  expect(screen.queryByText(/Explica su procedimiento/)).toBeNull();
});

test.each([
  ["GET /api/evaluaciones/periodos-informe", [], "No hay períodos académicos disponibles", false],
  ["GET /api/calificaciones/informes-disponibles", [], "No hay informes disponibles", true],
])("Maneja vacío de %s", async (route, data, text, selectPeriod) => {
  backend({ [route]: data }); render(<ProgressPreview />);
  await openPeriod({ period: selectPeriod, student: false });
  expect(await screen.findByText(new RegExp(text))).toBeTruthy();
  expect(screen.queryByLabelText("Estudiante")).toBeNull();
});

test.each(["GET /api/evaluaciones/periodos-informe", "GET /api/calificaciones/informes-disponibles", "GET /api/calificaciones/avance"])("Error de lectura en %s permite reintentar sin datos anteriores", async (route) => {
  let failed = true;
  const data = route.endsWith("periodos-informe") ? periods : route.endsWith("informes-disponibles") ? [availableReport] : progressReport;
  backend({ [route]: () => failed ? reply(403, { message: "No tienes acceso a este contexto" }) : data });
  render(<ProgressPreview />);
  await openPeriod({ period: !route.endsWith("periodos-informe"), student: route.endsWith("/avance") });
  expect((await screen.findByRole("alert")).textContent).toContain("No tienes acceso");
  expect(screen.queryByRole("article")).toBeNull();
  failed = false;
  await userEvent.click(screen.getByRole("button", { name: "Reintentar" }));
  await waitFor(() => expect(screen.queryByRole("alert")).toBeNull());
  if (route.endsWith("/avance")) expect(await screen.findByRole("article")).toBeTruthy();
});

test("Una descarga fallida conserva el informe y permite reintentar", async () => {
  let failed = true;
  backend({ "GET /api/calificaciones/reporte-avance": () => failed ? reply(500, { message: "No se pudo generar el PDF" }) : {} });
  render(<ProgressPreview />); const interaction = await openPeriod();
  await interaction.click(await screen.findByRole("button", { name: "Descargar PDF" }));
  expect((await screen.findByRole("alert")).textContent).toContain("No se pudo generar");
  expect(screen.getByRole("article")).toBeTruthy();
  expect(URL.createObjectURL).not.toHaveBeenCalled();
  failed = false;
  await interaction.click(screen.getByRole("button", { name: "Reintentar descarga PDF" }));
  await waitFor(() => expect(URL.createObjectURL).toHaveBeenCalledTimes(1));
  expect(screen.queryByRole("alert")).toBeNull();
});

test("Cambiar período durante una descarga evita guardar un PDF del contexto anterior", async () => {
  let resolvePdf;
  backend({ "GET /api/calificaciones/reporte-avance": () => new Promise((resolve) => { resolvePdf = resolve; }) });
  render(<ProgressPreview />); const interaction = await openPeriod();
  await interaction.click(await screen.findByRole("button", { name: "Descargar PDF" }));
  await waitFor(() => expect(resolvePdf).toBeTypeOf("function"));
  expect(screen.getByRole("button", { name: "Preparando PDF…" }).disabled).toBe(true);
  await interaction.selectOptions(screen.getByLabelText("Período académico"), "41");
  await act(async () => resolvePdf({}));
  expect(URL.createObjectURL).not.toHaveBeenCalled();
  expect(screen.queryByRole("article")).toBeNull();
});

test("Tras un error cambiar período limpia el mensaje anterior", async () => {
  backend({ "GET /api/calificaciones/avance": reply(422, { message: "Ya no hay evaluaciones publicadas" }) });
  render(<ProgressPreview />); const interaction = await openPeriod();
  await screen.findByRole("alert");
  await interaction.selectOptions(screen.getByLabelText("Período académico"), "41");
  expect(screen.queryByRole("alert")).toBeNull();
  expect(await screen.findByText(/No hay informes disponibles/)).toBeTruthy();
});

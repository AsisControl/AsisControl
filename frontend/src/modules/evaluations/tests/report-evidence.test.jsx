import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { ReportContent } from "../report-content";
import { ProgressReports } from "../progress-reports";
import { fakeBackend, reply, signIn } from "../../../../tests/support/backend.js";
import { availableReport, progressReport } from "./report-fixture";

const row = {
  ...progressReport.filas[0],
  idInforme: 41, idDetalleInforme: 203, idEvaluacion: 18, idCompetencia: 7, idCalificacion: null, tieneSustento: true,
};
const draft = { ...progressReport, periodo: "Primer bimestre | Version 1 | BORRADOR", filas: [row] };
const published = { ...draft, periodo: "Primer bimestre | Version 1 | PUBLICADA" };
const path = "/api/calificaciones/informes/41/filas/203/evidencias";
const filePath = `${path}/51/archivo`;
async function openEvidence() {
  await screen.findByRole("region", { name: "Consolidado por competencia" });
  const detail = document.querySelector("article details");
  if (detail && !detail.open) await userEvent.click(detail.querySelector("summary"));
  await userEvent.click(screen.getByRole("button", { name: /^Ver sustento de/ }));
}

test("el parcial consulta y descarga sustento con el contexto real aunque la fila tenga id cero", async () => {
  signIn({ role: "DOCENTE", permissions: ["CALIFICACIONES_LEER"] });
  const base = "/api/calificaciones/informes/parcial/evidencias";
  const calls = fakeBackend({
    [`GET ${base}`]: { evidencias: [{ idEvidencia: 51, descripcion: "Evidencia parcial", archivo: {
      idArchivo: 67, nombreOriginal: "Trabajo.pdf", tipoMime: "application/pdf", tamanioBytes: 123,
    } }] },
    [`GET ${base}/51/archivo`]: {},
  });
  URL.createObjectURL = vi.fn(() => "blob:parcial");
  URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  const noEvidence = { ...row, idEvaluacion: 19, evaluacion: "Proyecto", idInforme: 0, idDetalleInforme: 0, tieneSustento: false };
  render(<ReportContent partial sustento="docente" partialContext={availableReport}
    report={{ ...progressReport, filas: [{ ...row, idInforme: 0, idDetalleInforme: 0 }, noEvidence] }} />);
  expect(screen.getByText("Sin sustento registrado")).toBeTruthy();
  await userEvent.click(screen.getByRole("button", { name: /^Ver sustento de/ }));
  await screen.findByText("Evidencia parcial");
  await userEvent.click(screen.getByRole("button", { name: "Descargar Trabajo.pdf" }));
  await waitFor(() => expect(HTMLAnchorElement.prototype.click).toHaveBeenCalledOnce());
  expect(calls.map(({ path, query }) => ({ path, query }))).toEqual([base, `${base}/51/archivo`].map(path => ({
    path, query: { idAlumno: "11", idAsignacionCurso: "5", idPeriodoAcademico: "40", idEvaluacion: "18", idCompetencia: "7" },
  })));
});

test("un docente consulta el sustento del borrador y descarga el adjunto con su sesión", async () => {
  signIn({ role: "DOCENTE", permissions: ["CALIFICACIONES_LEER"] });
  const calls = fakeBackend({
    [`GET ${path}`]: {
      evidencias: [
        { idEvidencia: 51, descripcion: "Trabajo de clase", archivo: {
          idArchivo: 67, nombreOriginal: "Trabajo.pdf", tipoMime: "application/pdf", tamanioBytes: 123, urlDescarga: filePath,
        } },
        { idEvidencia: 52, descripcion: "Referencia de observación", archivo: null },
      ],
    },
    [`GET ${filePath}`]: {},
  });
  URL.createObjectURL = vi.fn(() => "blob:trabajo");
  URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  render(<ReportContent report={draft} draft sustento="docente" />);
  expect(calls).toHaveLength(0);
  await openEvidence();
  await screen.findByText("Trabajo de clase");
  expect(screen.getByText("Referencia de observación")).toBeTruthy();
  const dialog = within(screen.getByRole("dialog", { name: "Sustento de la competencia" }));
  expect(dialog.getByText(row.competencia)).toBeTruthy();
  expect(dialog.getByText(`${row.evaluacion} · ${row.fecha}`)).toBeTruthy();
  const [first, second] = dialog.getAllByRole("listitem");
  expect(within(first).getByRole("heading", { name: "Descripción de la evidencia" })).toBeTruthy();
  expect(within(first).getByText("Trabajo.pdf")).toBeTruthy();
  expect(within(first).getByText("PDF · 123 B")).toBeTruthy();
  expect(within(second).getByText("Esta evidencia no tiene archivo adjunto.")).toBeTruthy();
  expect(screen.getAllByRole("button", { name: /^Descargar/ })).toHaveLength(1);
  await userEvent.click(screen.getByRole("button", { name: "Descargar Trabajo.pdf" }));
  await waitFor(() => expect(HTMLAnchorElement.prototype.click).toHaveBeenCalledTimes(1));
  expect(HTMLAnchorElement.prototype.click.mock.instances[0].download).toBe("Trabajo.pdf");
  expect(calls.map(call => call.path)).toEqual([path, filePath]);
  expect(new Headers(fetch.mock.calls[1][1].headers).get("Authorization")).toBe("Bearer test-token");
});

test("Distingue pesos efectivos por competencia y muestra la versión una sola vez", async () => {
  const user = userEvent.setup();
  render(<ReportContent sustento="docente" report={{ ...published, reglaCalificacion: { numeroVersion: 1 },
    consolidado: [
      { idCompetencia: 7, nombreCompetencia: row.competencia, nivelConsolidado: "A", promedioPonderado: 16.25, numeroVersionEscala: 2, componentesFaltantes: [] },
      { idCompetencia: 8, nombreCompetencia: "Comunica", nivelConsolidado: null, numeroVersionEscala: 2, componentesFaltantes: ["Proyectos"] },
      { idCompetencia: 9, nombreCompetencia: "Modela", nivelConsolidado: null, estadoAvance: "NO_EVALUADA", componentesFaltantes: [] },
    ],
    filas: [{ ...row, tipoEvaluacion: "EXAMEN", ponderacion: "15 %", notaNumerica: 0 },
      { ...row, idDetalleInforme: 205, idEvaluacion: 20, tipoEvaluacion: "EXAMEN", ponderacion: "15 %" },
      { ...row, idDetalleInforme: 204, idCompetencia: 8, competencia: "Comunica", tipoEvaluacion: "EXAMEN", ponderacion: "30 %" }],
  }} />);
  const summary = within(screen.getByRole("region", { name: "Consolidado por competencia" }));
  expect(screen.getAllByText("Escala institucional v2")).toHaveLength(1);
  expect(summary.getByText("Pendiente")).toBeTruthy();
  const unassessed = summary.getByRole("region", { name: "Modela" });
  expect(within(unassessed).getByText("Aún no evaluada")).toBeTruthy();
  expect(unassessed.querySelector("details")).toBeTruthy();
  expect(summary.getByText("Proyectos")).toBeTruthy();
  expect([...document.querySelectorAll("article details")].every(detail => !detail.open)).toBe(true);
  for (const detail of summary.getAllByText(/^Ver detalle del resultado/, { selector: "summary" })) {
    await user.click(detail);
  }
  expect(summary.getByText(/Promedio ponderado: 16[.,]25/)).toBeTruthy();
  expect(summary.getAllByText("Promedio ponderado: —")).toHaveLength(2);
  expect(summary.getByText(/· 0[.,]00/)).toBeTruthy();
  expect(within(summary.getByRole("region", { name: row.competencia })).getAllByRole("cell", { name: "15 %" })).toHaveLength(2);
  expect(within(summary.getByRole("region", { name: "Comunica" })).getByRole("cell", { name: "30 %" })).toBeTruthy();
});

test("un parcial usa claves de competencias aunque sus detalles no estén persistidos", () => {
  const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
  render(<ReportContent partial sustento="docente" report={{ ...draft, filas: [
    { ...row, idDetalleInforme: 0, tieneSustento: false },
    { ...row, idDetalleInforme: 0, idEvaluacion: 19, evaluacion: "Examen final", tieneSustento: false },
    { ...row, idDetalleInforme: 0, idCompetencia: 8, competencia: "Comunica", tieneSustento: false },
  ] }} />);
  expect(screen.getByText("Comunica")).toBeTruthy();
  expect(screen.queryByRole("region", { name: "Consolidado por competencia" })).toBeNull();
  expect(consoleError.mock.calls.flat().join(" ")).not.toMatch(/same key/);
});

test("Agrupa por identificador, expande solo la evaluación elegida y abre el sustento de su competencia", async () => {
  const another = { ...row, idEvaluacion: 19, idDetalleInforme: 204, competencia: "Argumenta" };
  const same = { ...row, idDetalleInforme: 205, competencia: "Modela" };
  const calls = fakeBackend({ "GET /api/calificaciones/informes/41/filas/204/evidencias": { evidencias: [] } });
  const user = userEvent.setup();
  const { container } = render(<ReportContent report={{ ...draft, filas: [row, another, same] }} sustento="docente" />);
  await user.click(screen.getByRole("button", { name: "Ver por evaluación" }));
  const [first, second] = container.querySelectorAll("details");
  expect(first.open).toBe(true);
  expect(second.open).toBe(false);
  expect(within(first).getAllByRole("rowheader").map((cell) => cell.textContent)).toEqual([row.competencia, "Modela"]);
  expect(screen.queryByRole("navigation", { name: "Paginación de evaluaciones" })).toBeNull();
  await user.click(first.querySelector("summary"));
  await user.click(second.querySelector("summary"));
  expect(first.open).toBe(false);
  expect(second.open).toBe(true);
  await user.click(within(second).getByRole("button", { name: /Ver sustento de Argumenta/ }));
  await screen.findByText("No hay sustento disponible para esta competencia.");
  expect(calls.map((call) => call.path)).toEqual(["/api/calificaciones/informes/41/filas/204/evidencias"]);
});

test("Numera el listado en orden cronológico, pagina de cinco en cinco y actualiza el contador al filtrar", async () => {
  const rows = Array.from({ length: 8 }, (_, index) => ({
    ...row, idEvaluacion: index + 1, idDetalleInforme: index + 1, evaluacion: `Evaluación ${index + 1}`,
    fecha: `0${index + 1}/04/2027`,
    tipoEvaluacion: index < 5 ? "EXAMEN" : "PROYECTO", estadoEvaluacion: "BORRADOR",
  })).reverse();
  const user = userEvent.setup();
  const { container } = render(<ReportContent report={{ ...draft, filas: rows }} sustento="docente" />);
  await user.click(screen.getByRole("button", { name: "Ver por evaluación" }));
  const groups = () => [...container.querySelectorAll("details")];
  const numbers = () => groups().map((group) => group.querySelector("summary").firstElementChild.textContent);
  expect(screen.getByText("8 evaluaciones")).toBeTruthy();
  expect(numbers()).toEqual(["1.", "2.", "3.", "4.", "5."]);
  expect(groups()[0].querySelector("summary").textContent).toContain("Evaluación 1");
  expect(groups()[0].open).toBe(true);
  await user.click(groups()[1].querySelector("summary"));
  expect(groups()[1].open).toBe(true);
  await user.click(groups()[1].querySelector("summary"));
  expect(groups()[1].open).toBe(false);
  await user.click(groups()[1].querySelector("summary"));
  await user.click(screen.getByRole("button", { name: "Siguiente" }));
  expect(numbers()).toEqual(["6.", "7.", "8."]);
  expect(groups()[0].querySelector("summary").textContent).toContain("Evaluación 6");
  expect(groups()[0].open).toBe(true);
  await user.click(screen.getByRole("button", { name: "Anterior" }));
  expect(groups()[1].open).toBe(false);
  await user.selectOptions(screen.getByLabelText("Tipo de evaluación"), "PROYECTO");
  expect(screen.getByText("3 de 8 evaluaciones")).toBeTruthy();
  expect(numbers()).toEqual(["1.", "2.", "3."]);
  expect(groups()[0].querySelector("summary").textContent).toContain("Evaluación 6");
  expect(groups()[0].querySelector("summary").textContent).toContain("Borrador");
  expect(screen.queryByRole("navigation", { name: "Paginación de evaluaciones" })).toBeNull();
});

test("una respuesta vacía se distingue de un error", async () => {
  fakeBackend({ [`GET ${path}`]: { evidencias: [] } });
  render(<ReportContent report={published} sustento="docente" />);
  await openEvidence();
  expect(await screen.findByText("No hay sustento disponible para esta competencia.")).toBeTruthy();
  expect(screen.queryByRole("alert")).toBeNull();
});

test.each([
  [403, "No tienes permiso para consultar este sustento."],
  [404, "La fila de la libreta ya no está disponible."],
])("muestra el error HTTP %s sin evidencias", async (status, message) => {
  fakeBackend({ [`GET ${path}`]: reply(status) });
  render(<ReportContent report={published} sustento="docente" />);
  await openEvidence();
  expect((await screen.findByRole("alert")).textContent).toBe(message);
  expect(screen.queryByRole("button", { name: /^Descargar/ })).toBeNull();
});

test("al cambiar de alumno y fila descarta la respuesta anterior", async () => {
  let finish;
  const nextRow = { ...row, idInforme: 42, idDetalleInforme: 204, competencia: "Argumenta" };
  const next = { ...published, alumno: "Luis", filas: [nextRow] };
  fakeBackend({
    [`GET ${path}`]: () => new Promise(resolve => { finish = resolve; }),
    "GET /api/calificaciones/informes/42/filas/204/evidencias": {
      evidencias: [{ idEvidencia: 2, descripcion: "Sustento de Luis", archivo: null }],
    },
  });
  const view = render(<ReportContent report={published} sustento="docente" />);
  await openEvidence();
  expect(screen.getByRole("status").textContent).toContain("Consultando sustento");
  view.rerender(<ReportContent report={next} sustento="docente" />);
  expect(screen.queryByRole("dialog")).toBeNull();
  await openEvidence();
  await screen.findByText("Sustento de Luis");
  await act(async () => finish({ evidencias: [{ idEvidencia: 1, descripcion: "Sustento anterior", archivo: null }] }));
  expect(screen.queryByText("Sustento anterior")).toBeNull();
  expect(screen.getByText("Sustento de Luis")).toBeTruthy();
});

test.each([
  ["APODERADO", true, published],
  ["ESTUDIANTE", false, published],
  ["APODERADO", true, draft],
  ["ESTUDIANTE", false, draft],
])("Progreso de %s solo permite abrir sustento de publicaciones", async (role, guardian, report) => {
  signIn({ role, permissions: ["CALIFICACIONES_LEER"] });
  fakeBackend({
    "GET /api/familia/estudiantes": [{ id: availableReport.idAlumno, nombreCompleto: availableReport.alumno }],
    "GET /api/calificaciones/informes-disponibles": [availableReport],
    "GET /api/calificaciones/avance": report,
    [`GET ${path}`]: { evidencias: [] },
  });
  render(<ProgressReports guardian={guardian} />);
  const user = userEvent.setup();
  await screen.findByRole("option", { name: "Primer bimestre · 2026" });
  await user.selectOptions(screen.getByLabelText("Período académico"), "2026-40");
  await user.selectOptions(screen.getByLabelText("Curso"), "5");
  await user.click(screen.getByRole("button", { name: "Ver notas" }));
  if (report === draft) {
    await screen.findByText("No hay una libreta publicada para este período y curso.");
    expect(screen.queryByText("BORRADOR")).toBeNull();
    expect(screen.queryByRole("button", { name: /^Ver sustento de/ })).toBeNull();
  } else {
    await openEvidence();
    await screen.findByText("No hay sustento disponible para esta competencia.");
  }
});

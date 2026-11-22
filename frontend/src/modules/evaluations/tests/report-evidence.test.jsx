import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { ReportContent } from "../report-content";
import { ProgressReports } from "../progress-reports";
import { fakeBackend, reply, signIn } from "../../../../tests/support/backend.js";
import { availableReport, progressReport } from "./report-fixture";

const row = {
  ...progressReport.filas[0],
  idInforme: 41, idDetalleInforme: 203, idEvaluacion: 18, idCompetencia: 7, idCalificacion: null,
};
const draft = { ...progressReport, periodo: "Primer bimestre | Version 1 | BORRADOR", filas: [row] };
const published = { ...draft, periodo: "Primer bimestre | Version 1 | PUBLICADA" };
const path = "/api/calificaciones/informes/41/filas/203/evidencias";
const filePath = `${path}/51/archivo`;
const openEvidence = () => userEvent.click(screen.getByRole("button", { name: /^Ver sustento de/ }));

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

test("Agrupa por identificador, expande solo la evaluación elegida y abre el sustento de su competencia", async () => {
  const another = { ...row, idEvaluacion: 19, idDetalleInforme: 204, competencia: "Argumenta" };
  const same = { ...row, idDetalleInforme: 205, competencia: "Modela" };
  const calls = fakeBackend({ "GET /api/calificaciones/informes/41/filas/204/evidencias": { evidencias: [] } });
  const user = userEvent.setup();
  const { container } = render(<ReportContent report={{ ...draft, filas: [row, another, same] }} sustento="docente" />);
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
    await user.click(await screen.findByRole("button", { name: /^Ver sustento de/ }));
    await screen.findByText("No hay sustento disponible para esta competencia.");
  }
});

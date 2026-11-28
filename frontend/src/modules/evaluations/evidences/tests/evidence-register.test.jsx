import { expect, test, vi } from "vitest";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { page, reply } from "../../../../../tests/support/backend.js";
import { backend, chooseStudent, evaluation, evidence, open, pdf, withFile } from "./fixtures.jsx";

vi.mock("next/navigation", () => import("../../../../../tests/support/navigation.js"));

async function openDialog(user) {
  await user.click(screen.getByRole("button", { name: "+ Nueva evidencia" }));
  return screen.findByRole("dialog", { name: /Nueva evidencia/ });
}
const attach = (file) => fireEvent.change(screen.getByLabelText("Archivo de evidencia"), { target: { files: [file] } });

test("Sube el archivo, crea la evidencia y vincula la evaluación compatible", async () => {
  const calls = backend({
    "GET /api/evaluaciones": page([
      evaluation,
      { ...evaluation, id: 11, titulo: "Otra asignación", idAsignacionCurso: 9 },
      { ...evaluation, id: 12, titulo: "Otra competencia", competencias: [{ id: 2 }] },
      { ...evaluation, id: 13, titulo: "Fuera del período", fechaEvaluacion: "2026-09-23" },
      { ...evaluation, id: 14, titulo: "Anulada", estado: "ANULADA" },
    ]),
    "POST /api/archivos": withFile,
    "POST /api/evidencias-pedagogicas": reply(201, evidence({ archivo: withFile })),
    "PUT /api/evidencias-pedagogicas/70/evaluaciones/10": reply(204),
  });
  open();
  const user = await chooseStudent();
  const dialog = await openDialog(user);
  expect(within(dialog).queryByRole("option", { name: "Otro curso" })).toBeNull();
  await user.selectOptions(within(dialog).getByLabelText(/Competencia/), "Resuelve problemas (MAT-C1)");
  await user.selectOptions(within(dialog).getByLabelText(/Período/), "I Bimestre");
  await within(dialog).findByRole("option", { name: "Examen · 2026-09-22" });
  const selector = within(dialog).getByLabelText("Evaluación");
  expect(within(selector).getAllByRole("option").map((option) => option.value)).toEqual(["", "10"]);
  await user.selectOptions(selector, "10");
  await user.type(within(dialog).getByLabelText(/Descripción/), "  Maqueta del sistema solar ");
  attach(pdf());
  await user.click(within(dialog).getByRole("button", { name: "Guardar evidencia" }));
  expect(await screen.findByText("Evidencia registrada.")).toBeTruthy();
  const upload = calls.find((call) => call.path === "/api/archivos");
  expect(upload.body.get("archivo").name).toBe("maqueta.pdf");
  const create = calls.find((call) => call.method === "POST" && call.path === "/api/evidencias-pedagogicas");
  expect(create.body).toEqual({
    asignacionId: 5, alumnoId: 1, competenciaId: 1, periodoId: 3,
    descripcion: "Maqueta del sistema solar", archivoId: 31,
  });
  expect(calls.indexOf(upload)).toBeLessThan(calls.indexOf(create));
  expect(calls.findIndex((call) => call.method === "PUT" && call.path.endsWith("/70/evaluaciones/10")))
    .toBeGreaterThan(calls.indexOf(create));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Si falla el vínculo después del alta, avisa y reintenta con la evidencia ya creada", async () => {
  let attempts = 0;
  const calls = backend({
    "POST /api/evidencias-pedagogicas": reply(201, evidence()),
    "PUT /api/evidencias-pedagogicas/70/evaluaciones/10": () => ++attempts === 1
      ? reply(422, { message: "Evaluación incompatible" }) : reply(204),
  });
  open();
  const user = await chooseStudent();
  const dialog = await openDialog(user);
  await user.selectOptions(within(dialog).getByLabelText(/Competencia/), "1");
  await user.selectOptions(within(dialog).getByLabelText(/Período/), "3");
  await within(dialog).findByRole("option", { name: "Examen · 2026-09-22" });
  await user.selectOptions(within(dialog).getByLabelText("Evaluación"), "10");
  await user.type(within(dialog).getByLabelText(/Descripción/), "Evidencia de prueba");
  await user.click(within(dialog).getByRole("button", { name: "Guardar evidencia" }));
  expect(await within(dialog).findByText(/está guardada y quedó sin vincular.*Evaluación incompatible/)).toBeTruthy();
  await user.click(within(dialog).getByRole("button", { name: "Reintentar vinculación" }));
  expect(await screen.findByText("Evidencia registrada.")).toBeTruthy();
  expect(calls.filter((call) => call.method === "POST")).toHaveLength(1);
  expect(calls.filter((call) => call.method === "PUT")).toHaveLength(2);
});

test("Al reintentar tras un error no vuelve a subir el archivo", async () => {
  let attempts = 0;
  const calls = backend({
    "POST /api/archivos": withFile,
    "POST /api/evidencias-pedagogicas": () =>
      ++attempts === 1 ? reply(500, { message: "Error temporal" }) : reply(201, evidence()),
  });
  open();
  const user = await chooseStudent();
  const dialog = await openDialog(user);
  await user.selectOptions(within(dialog).getByLabelText(/Competencia/), "Resuelve problemas (MAT-C1)");
  await user.selectOptions(within(dialog).getByLabelText(/Período/), "I Bimestre");
  attach(pdf());
  await user.click(within(dialog).getByRole("button", { name: "Guardar evidencia" }));
  expect(await within(dialog).findByText("Error temporal")).toBeTruthy();
  await user.click(within(dialog).getByRole("button", { name: "Guardar evidencia" }));
  expect(await screen.findByText("Evidencia registrada.")).toBeTruthy();
  expect(calls.filter((call) => call.path === "/api/archivos")).toHaveLength(1);
  expect(calls.filter((call) => call.method === "POST" && call.path === "/api/evidencias-pedagogicas")).toHaveLength(2);
});

test("Sin descripción ni archivo no envía nada", async () => {
  const calls = backend();
  open();
  const user = await chooseStudent();
  const dialog = await openDialog(user);
  await user.selectOptions(within(dialog).getByLabelText(/Competencia/), "Resuelve problemas (MAT-C1)");
  await user.selectOptions(within(dialog).getByLabelText(/Período/), "I Bimestre");
  await user.click(within(dialog).getByRole("button", { name: "Guardar evidencia" }));
  expect(await within(dialog).findByText("Escribe una descripción o adjunta un archivo.")).toBeTruthy();
  expect(calls.some((call) => call.method === "POST")).toBe(false);
});

test("Un archivo no permitido se rechaza antes de subirlo; quitar el archivo es posible", async () => {
  const calls = backend();
  open();
  const user = await chooseStudent();
  const dialog = await openDialog(user);
  attach(new File(["x"], "virus.exe"));
  expect(await within(dialog).findByText("Selecciona PDF, JPEG, PNG, Word, Excel o PowerPoint.")).toBeTruthy();
  expect(within(dialog).queryByText("virus.exe")).toBeNull();
  attach(pdf());
  expect(within(dialog).getByText("maqueta.pdf")).toBeTruthy();
  expect(within(dialog).getByText("PDF · 4 B")).toBeTruthy();
  await user.click(within(dialog).getByRole("button", { name: "Quitar" }));
  expect(within(dialog).queryByText("maqueta.pdf")).toBeNull();
  expect(within(dialog).getByText("Adjuntar archivo")).toBeTruthy();
  expect(within(dialog).getByRole("button", { name: "Seleccionar archivo" })).toBeTruthy();
  expect(calls.some((call) => call.method === "POST")).toBe(false);
});

test("Con matrícula y sin evaluaciones se puede seleccionar al estudiante y registrar evidencia", async () => {
  const calls = backend({ "GET /api/evaluaciones": page([]) });
  open();
  const user = await chooseStudent();
  await openDialog(user);
  expect(calls.some(call => call.path.endsWith("/planilla"))).toBe(false);
});

test("Una sección sin matrícula muestra un estado vacío", async () => {
  backend({ "GET /api/evidencias-pedagogicas/asignaciones/5/estudiantes": [] });
  open();
  const user = userEvent.setup();
  await user.selectOptions(await screen.findByLabelText("Curso y sección"), "5");
  expect(await screen.findByText("Esta sección no tiene estudiantes con matrícula activa.")).toBeTruthy();
  expect(screen.getByLabelText(/Estudiante/).disabled).toBe(true);
  expect(screen.getByRole("button", { name: "+ Nueva evidencia" }).disabled).toBe(true);
});

test("Un error al cargar el curso se muestra y el rol sin escritura no ve el botón", async () => {
  backend({ "GET /api/evaluaciones/periodos-informe": reply(500, { message: "Sin períodos" }) });
  open({ canWrite: false });
  const user = userEvent.setup();
  await user.selectOptions(await screen.findByLabelText("Curso y sección"), "5");
  expect(await screen.findByText(/No se pudieron cargar los datos del curso/)).toBeTruthy();
  expect(screen.getByRole("button", { name: "Reintentar carga" })).toBeTruthy();
  expect(screen.queryByRole("button", { name: "+ Nueva evidencia" })).toBeNull();
});

test("Cambiar de curso descarta el estudiante elegido", async () => {
  backend();
  open();
  const user = await chooseStudent();
  await user.selectOptions(screen.getByLabelText("Curso y sección"), "");
  expect(screen.getByLabelText(/Estudiante/).value).toBe("");
  expect(screen.getByRole("button", { name: "+ Nueva evidencia" }).disabled).toBe(true);
  await waitFor(() => expect(screen.getByText("Selecciona un curso y sección para empezar.")).toBeTruthy());
});

test("El tamaño excedido del servidor (413) se explica y los filtros precargan el formulario", async () => {
  backend({
    "POST /api/archivos": reply(413, null),
  });
  open();
  const user = await chooseStudent();
  await user.selectOptions(screen.getByLabelText("Competencia"), "Razona en geometría (MAT-C2)");
  await user.selectOptions(screen.getByLabelText("Período"), "II Bimestre");
  const dialog = await openDialog(user);
  expect(within(dialog).getByLabelText(/Competencia/).value).toBe("2");
  expect(within(dialog).getByLabelText(/Período/).value).toBe("4");
  attach(pdf());
  await user.click(within(dialog).getByRole("button", { name: "Guardar evidencia" }));
  expect(await within(dialog).findByText("El archivo supera el tamaño permitido (10 MiB).")).toBeTruthy();
  await user.click(within(dialog).getByRole("button", { name: "Cancelar" }));
  expect(screen.queryByRole("dialog")).toBeNull();
});

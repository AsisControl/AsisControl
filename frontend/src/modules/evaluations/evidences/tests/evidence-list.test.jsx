import { expect, test, vi } from "vitest";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { page, reply } from "../../../../../tests/support/backend.js";
import { backend, chooseStudent, evidence, open, pdf, withFile } from "./fixtures.jsx";

vi.mock("next/navigation", () => import("../../../../../tests/support/navigation.js"));

const listCalls = (calls) => calls.filter((call) => call.method === "GET" && call.path === "/api/evidencias-pedagogicas");
const rowMenu = async (user, name) => {
  await user.click(await screen.findByRole("button", { name }));
};

test("No consulta evidencias hasta elegir estudiante y filtra por curso, estudiante, competencia y período", async () => {
  const calls = backend({
    "GET /api/evidencias-pedagogicas": page([evidence({ archivo: withFile }), evidence({ id: 71, competenciaId: 2, periodoId: 4, descripcion: null })]),
  });
  open();
  const user = await chooseStudent();
  expect(await screen.findByText("Maqueta del sistema solar")).toBeTruthy();
  expect(screen.getByText("2 evidencias")).toBeTruthy();
  expect(screen.getByText("Sin descripción")).toBeTruthy();
  expect(screen.getByRole("cell", { name: "II Bimestre" })).toBeTruthy();
  expect(listCalls(calls)[0].query).toMatchObject({ asignacionId: "5", alumnoId: "1", page: "0", size: "20" });
  expect(listCalls(calls)[0].query.competenciaId).toBeUndefined();
  await user.selectOptions(screen.getByLabelText("Competencia"), "Razona en geometría");
  await user.selectOptions(screen.getByLabelText("Período"), "II Bimestre");
  await waitFor(() => expect(listCalls(calls).at(-1).query).toMatchObject({ competenciaId: "2", periodoId: "4", alumnoId: "1" }));
  expect(listCalls(calls).every((call) => call.query.alumnoId === "1")).toBe(true);
});

test("Antes de elegir estudiante no se llama al listado", async () => {
  const calls = backend();
  open();
  await screen.findByLabelText("Curso y sección");
  expect(listCalls(calls)).toHaveLength(0);
});

test("Sin resultados muestra el estado vacío y un error del servidor se informa", async () => {
  let fail = false;
  backend({ "GET /api/evidencias-pedagogicas": () => (fail ? reply(403, { message: "No eres docente titular" }) : page([])) });
  open();
  const user = await chooseStudent();
  expect(await screen.findByText("Este estudiante no tiene evidencias con estos filtros.")).toBeTruthy();
  fail = true;
  await user.selectOptions(screen.getByLabelText("Período"), "I Bimestre");
  expect(await screen.findByText("No eres docente titular")).toBeTruthy();
});

test("Descarga el adjunto y avisa si la descarga falla", async () => {
  const calls = backend({
    "GET /api/evidencias-pedagogicas": page([evidence({ archivo: withFile })]),
    "GET /api/archivos/:id/descarga": reply(404, { message: "Archivo no disponible" }),
  });
  URL.createObjectURL = vi.fn(() => "blob:x");
  URL.revokeObjectURL = vi.fn();
  open();
  const user = await chooseStudent();
  await user.click(await screen.findByRole("button", { name: "maqueta.pdf" }));
  expect(await screen.findByText("Archivo no disponible")).toBeTruthy();
  expect(calls.some((call) => call.path === "/api/archivos/31/descarga")).toBe(true);
});

test("Edita la descripción conservando el archivo, sin volver a subirlo", async () => {
  const calls = backend({
    "GET /api/evidencias-pedagogicas": page([evidence({ archivo: withFile })]),
    "PUT /api/evidencias-pedagogicas/:id": evidence({ archivo: withFile }),
  });
  open();
  const user = await chooseStudent();
  await rowMenu(user, "Más acciones para evidencia 70");
  await user.click(await screen.findByRole("menuitem", { name: "Editar" }));
  const dialog = await screen.findByRole("dialog", { name: /Editar evidencia/ });
  expect(within(dialog).getByLabelText(/Competencia/).disabled).toBe(true);
  const text = within(dialog).getByLabelText(/Descripción/);
  await user.clear(text);
  await user.type(text, "Nueva descripción");
  await user.click(within(dialog).getByRole("button", { name: "Guardar cambios" }));
  expect(await screen.findByText("Evidencia actualizada.")).toBeTruthy();
  const put = calls.find((call) => call.method === "PUT");
  expect(put.path).toBe("/api/evidencias-pedagogicas/70");
  expect(put.body).toEqual({ descripcion: "Nueva descripción", archivoId: 31 });
  expect(calls.some((call) => call.path === "/api/archivos")).toBe(false);
});

test("Editar permite quitar o reemplazar el archivo", async () => {
  const calls = backend({
    "GET /api/evidencias-pedagogicas": page([evidence({ archivo: withFile })]),
    "POST /api/archivos": { ...withFile, id: 32 },
    "PUT /api/evidencias-pedagogicas/:id": evidence(),
  });
  open();
  const user = await chooseStudent();
  await rowMenu(user, "Más acciones para evidencia 70");
  await user.click(await screen.findByRole("menuitem", { name: "Editar" }));
  const dialog = await screen.findByRole("dialog", { name: /Editar evidencia/ });
  await user.click(within(dialog).getByRole("button", { name: "Quitar" }));
  await user.click(within(dialog).getByRole("button", { name: "Guardar cambios" }));
  await screen.findByText("Evidencia actualizada.");
  expect(calls.find((call) => call.method === "PUT").body.archivoId).toBeNull();
  await rowMenu(user, "Más acciones para evidencia 70");
  await user.click(await screen.findByRole("menuitem", { name: "Editar" }));
  const again = await screen.findByRole("dialog", { name: /Editar evidencia/ });
  expect(within(again).getByRole("button", { name: "Reemplazar archivo" })).toBeTruthy();
  fireEvent.change(screen.getByLabelText("Archivo de evidencia"), { target: { files: [pdf("nuevo.pdf")] } });
  await user.click(within(again).getByRole("button", { name: "Guardar cambios" }));
  await waitFor(() => expect(calls.filter((call) => call.method === "PUT")).toHaveLength(2));
  expect(calls.filter((call) => call.method === "PUT")[1].body.archivoId).toBe(32);
});

test("Inactiva una evidencia tras confirmar", async () => {
  const calls = backend({
    "GET /api/evidencias-pedagogicas": page([evidence()]),
    "DELETE /api/evidencias-pedagogicas/:id": reply(204, null),
  });
  open();
  const user = await chooseStudent();
  await rowMenu(user, "Más acciones para evidencia 70");
  await user.click(await screen.findByRole("menuitem", { name: "Inactivar" }));
  const dialog = await screen.findByRole("dialog", { name: "Inactivar evidencia" });
  await user.click(within(dialog).getByRole("button", { name: "Inactivar evidencia" }));
  expect(await screen.findByText("Evidencia inactivada.")).toBeTruthy();
  expect(calls.find((call) => call.method === "DELETE").path).toBe("/api/evidencias-pedagogicas/70");
});

test("Una evidencia usada en una evaluación (409) muestra el mensaje del backend y se conserva", async () => {
  backend({
    "GET /api/evidencias-pedagogicas": page([evidence()]),
    "DELETE /api/evidencias-pedagogicas/:id": reply(409, { message: "La evidencia está asociada a una evaluación", code: "EVIDENCIA_USADA_EN_EVALUACION" }),
  });
  open();
  const user = await chooseStudent();
  await rowMenu(user, "Más acciones para evidencia 70");
  await user.click(await screen.findByRole("menuitem", { name: "Inactivar" }));
  const dialog = await screen.findByRole("dialog", { name: "Inactivar evidencia" });
  await user.click(within(dialog).getByRole("button", { name: "Inactivar evidencia" }));
  expect(await within(dialog).findByText("La evidencia está asociada a una evaluación")).toBeTruthy();
  await user.click(within(dialog).getByRole("button", { name: "Cancelar" }));
  expect(screen.getByText("Maqueta del sistema solar")).toBeTruthy();
});

test("El registro nuevo aparece en el listado y sin permiso de escritura no hay acciones", async () => {
  let items = [];
  backend({
    "GET /api/evidencias-pedagogicas": () => page(items),
    "POST /api/evidencias-pedagogicas": () => { items = [evidence()]; return reply(201, evidence()); },
  });
  open();
  const user = await chooseStudent();
  await user.click(screen.getByRole("button", { name: "+ Nueva evidencia" }));
  const dialog = await screen.findByRole("dialog", { name: /Nueva evidencia/ });
  await user.selectOptions(within(dialog).getByLabelText(/Competencia/), "Resuelve problemas");
  await user.selectOptions(within(dialog).getByLabelText(/Período/), "I Bimestre");
  await user.type(within(dialog).getByLabelText(/Descripción/), "Texto");
  await user.click(within(dialog).getByRole("button", { name: "Guardar evidencia" }));
  expect(await screen.findByText("Maqueta del sistema solar")).toBeTruthy();
  expect(screen.getByText("Evidencia registrada.")).toBeTruthy();
});

test("El rol sin CALIFICACIONES_ESCRIBIR solo consulta", async () => {
  backend({ "GET /api/evidencias-pedagogicas": page([evidence()]) });
  open({ canWrite: false });
  await chooseStudent();
  expect(await screen.findByText("Maqueta del sistema solar")).toBeTruthy();
  expect(screen.queryByRole("columnheader", { name: "Acciones" })).toBeNull();
  expect(screen.queryByRole("button", { name: /Más acciones para evidencia/ })).toBeNull();
  expect(screen.queryByRole("button", { name: "+ Nueva evidencia" })).toBeNull();
});

test("Muestra la fecha de un instante UTC con microsegundos en hora de Lima y tolera valores vacíos o inválidos", async () => {
  backend({
    "GET /api/evidencias-pedagogicas": page([
      evidence({ id: 80, fechaCreacion: "2026-11-20T04:01:54.728339Z" }),
      evidence({ id: 81, fechaCreacion: "" }),
      evidence({ id: 82, fechaCreacion: "no-es-fecha" }),
    ]),
  });
  open();
  await chooseStudent();
  expect(await screen.findByText("19/11/2026")).toBeTruthy();
  expect(screen.queryByText(/Invalid Date/)).toBeNull();
  expect(screen.getAllByRole("cell", { name: "—" }).length).toBeGreaterThanOrEqual(2);
});

import { expect, test, vi } from "vitest";
import { fireEvent, screen, within } from "@testing-library/react";
import { reply } from "../../../../../tests/support/backend.js";
import { backend, chooseStudent, evidence, open, pdf, withFile } from "./fixtures.jsx";

vi.mock("next/navigation", () => import("../../../../../tests/support/navigation.js"));

const post = (calls, path) => calls.filter((call) => call.method === "POST" && call.path === path);
async function fillDialog(user, { description = "", file } = {}) {
  await user.click(screen.getByRole("button", { name: "+ Nueva evidencia" }));
  const dialog = await screen.findByRole("dialog", { name: /Nueva evidencia/ });
  await user.selectOptions(within(dialog).getByLabelText(/Competencia/), "Resuelve problemas (MAT-C1)");
  await user.selectOptions(within(dialog).getByLabelText(/Período/), "I Bimestre");
  if (description) await user.type(within(dialog).getByLabelText(/Descripción/), description);
  if (file) fireEvent.change(screen.getByLabelText("Archivo de evidencia"), { target: { files: [file] } });
  return dialog;
}
const save = (user, dialog) => user.click(within(dialog).getByRole("button", { name: "Guardar evidencia" }));

test("Registro exitoso con solo descripción: no sube ningún archivo", async () => {
  const calls = backend({ "POST /api/evidencias-pedagogicas": reply(201, evidence()) });
  open();
  const user = await chooseStudent();
  await save(user, await fillDialog(user, { description: "Exposición oral" }));
  expect(await screen.findByText("Evidencia registrada.")).toBeTruthy();
  expect(post(calls, "/api/archivos")).toHaveLength(0);
  expect(post(calls, "/api/evidencias-pedagogicas")[0].body).toEqual({
    asignacionId: 5, alumnoId: 1, competenciaId: 1, periodoId: 3, descripcion: "Exposición oral", archivoId: null,
  });
});

test("Registro exitoso con solo archivo: descripción nula y archivoId del paso uno", async () => {
  const calls = backend({
    "POST /api/archivos": withFile,
    "POST /api/evidencias-pedagogicas": reply(201, evidence({ descripcion: null, archivo: withFile })),
  });
  open();
  const user = await chooseStudent();
  await save(user, await fillDialog(user, { file: pdf() }));
  expect(await screen.findByText("Evidencia registrada.")).toBeTruthy();
  expect(post(calls, "/api/evidencias-pedagogicas")[0].body).toMatchObject({ descripcion: null, archivoId: 31 });
});

test("Un archivo mayor de 10 MiB se rechaza en el cliente sin llamar al servidor", async () => {
  const calls = backend();
  open();
  const user = await chooseStudent();
  const big = new File(["x"], "grande.pdf", { type: "application/pdf" });
  Object.defineProperty(big, "size", { value: 10485761 });
  const dialog = await fillDialog(user, { description: "Con adjunto", file: big });
  expect(await within(dialog).findByText("Cada archivo admite hasta 10 MiB.")).toBeTruthy();
  expect(within(dialog).queryByText("grande.pdf")).toBeNull();
  await save(user, dialog);
  expect(post(calls, "/api/archivos")).toHaveLength(0);
  expect(post(calls, "/api/evidencias-pedagogicas")[0].body.archivoId).toBeNull();
});

test("Un archivo vacío se rechaza en el cliente", async () => {
  backend();
  open();
  const user = await chooseStudent();
  const dialog = await fillDialog(user, { file: new File([], "vacio.pdf") });
  expect(await within(dialog).findByText("El archivo está vacío.")).toBeTruthy();
});

test.each([
  ["422 por tipo", 422, { message: "Tipo de archivo no permitido", code: "ARCHIVO_TIPO_NO_PERMITIDO" }, "Tipo de archivo no permitido"],
  ["422 por tamaño", 422, { message: "El archivo supera 10 MiB", code: "ARCHIVO_DEMASIADO_GRANDE" }, "El archivo supera 10 MiB"],
  ["413 por límite multipart", 413, null, "El archivo supera el tamaño permitido (10 MiB)."],
])("El servidor rechaza el archivo (%s): muestra el motivo, conserva el formulario y no crea la evidencia", async (_, status, body, text) => {
  const calls = backend({ "POST /api/archivos": reply(status, body) });
  open();
  const user = await chooseStudent();
  const dialog = await fillDialog(user, { description: "Mi texto", file: pdf() });
  await save(user, dialog);
  expect(await within(dialog).findByText(text)).toBeTruthy();
  expect(within(dialog).getByLabelText(/Descripción/).value).toBe("Mi texto");
  expect(within(dialog).getByText("maqueta.pdf")).toBeTruthy();
  expect(post(calls, "/api/evidencias-pedagogicas")).toHaveLength(0);
});

test("Si el archivo no subió, el reintento lo vuelve a subir; si ya subió, no", async () => {
  let uploads = 0;
  const calls = backend({
    "POST /api/archivos": () => (++uploads === 1 ? reply(500, { message: "Fallo al subir" }) : withFile),
    "POST /api/evidencias-pedagogicas": reply(201, evidence()),
  });
  open();
  const user = await chooseStudent();
  const dialog = await fillDialog(user, { file: pdf() });
  await save(user, dialog);
  expect(await within(dialog).findByText("Fallo al subir")).toBeTruthy();
  await save(user, dialog);
  await screen.findByText("Evidencia registrada.");
  expect(post(calls, "/api/archivos")).toHaveLength(2);
  expect(post(calls, "/api/evidencias-pedagogicas")).toHaveLength(1);
});

test("Contexto ajeno: el 403 del backend se muestra y no se registra nada", async () => {
  const calls = backend({
    "POST /api/archivos": withFile,
    "POST /api/evidencias-pedagogicas": reply(403, { message: "No tienes acceso a esta asignación" }),
  });
  open();
  const user = await chooseStudent();
  const dialog = await fillDialog(user, { description: "Texto", file: pdf() });
  await save(user, dialog);
  expect(await within(dialog).findByText("No tienes acceso a esta asignación")).toBeTruthy();
  expect(screen.queryByText("Evidencia registrada.")).toBeNull();
  // El reintento reutiliza el archivo ya subido.
  await save(user, dialog);
  await within(dialog).findByText("No tienes acceso a esta asignación");
  expect(post(calls, "/api/archivos")).toHaveLength(1);
  expect(post(calls, "/api/evidencias-pedagogicas")).toHaveLength(2);
});

test("Contexto ajeno: 403 al listar las evidencias de otra asignación", async () => {
  backend({ "GET /api/evidencias-pedagogicas": reply(403, { message: "Asignación de otro docente" }) });
  open();
  await chooseStudent();
  expect(await screen.findByText("Asignación de otro docente")).toBeTruthy();
  expect(screen.queryByText("Este estudiante no tiene evidencias con estos filtros.")).toBeNull();
});

test.each([
  ["EVIDENCIA_ALUMNO_FUERA_DE_SECCION", "El estudiante no pertenece a la sección"],
  ["EVIDENCIA_COMPETENCIA_AJENA", "La competencia no pertenece al curso"],
  ["EVIDENCIA_PERIODO_INVALIDO", "El período no es válido"],
])("Contexto inválido (422 %s) muestra el mensaje y conserva el formulario", async (code, text) => {
  backend({ "POST /api/evidencias-pedagogicas": reply(422, { message: text, code }) });
  open();
  const user = await chooseStudent();
  const dialog = await fillDialog(user, { description: "Texto" });
  await save(user, dialog);
  expect(await within(dialog).findByText(text)).toBeTruthy();
  expect(within(dialog).getByLabelText(/Descripción/).value).toBe("Texto");
});

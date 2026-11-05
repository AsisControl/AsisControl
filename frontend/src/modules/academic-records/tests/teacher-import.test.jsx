import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { AcademicRecords } from "@/modules/academic-records";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const IMPORT_PATH = "/api/docentes/importaciones";
const excel = (name = "docentes.xlsx", size = 10) =>
  new File(["x".repeat(size)], name, {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
const clean = { totalRows: 2, importedRows: 2, rejectedRows: 0, issues: [] };
const mixed = {
  totalRows: 3,
  importedRows: 2,
  rejectedRows: 1,
  issues: [{ row: 3, field: "fila", message: "El codigo del docente ya existe" }],
};
const noneValid = { totalRows: 1, importedRows: 0, rejectedRows: 1, issues: mixed.issues };

function backend(routes = {}) {
  return fakeBackend({ "GET /api/docentes": page([]), ...routes });
}
async function openDialog(user) {
  render(<AcademicRecords basePath="/matricula/fichas" view="docentes" />);
  await user.click(await screen.findByRole("button", { name: "Carga masiva" }));
  return screen.getByRole("dialog", { name: "Carga masiva de docentes" });
}
const choose = (user, dialog, file) =>
  user.upload(within(dialog).getByLabelText("Archivo Excel de docentes", { exact: false }), file);
const importCalls = (calls) => calls.filter((call) => call.path === IMPORT_PATH);

beforeEach(() => {
  signIn({ permissions: ["USUARIOS_LEER"] });
  setUrl("/matricula/fichas/docentes");
});

test("Analiza el archivo sin guardar y al confirmar registra los docentes", async () => {
  const calls = backend({
    "POST /api/docentes/importaciones": clean,
  });
  const user = userEvent.setup();
  const dialog = await openDialog(user);
  expect(within(dialog).getByRole("button", { name: "Importar" }).disabled).toBe(true);
  await choose(user, dialog, excel());
  await user.click(within(dialog).getByRole("button", { name: "Validar archivo" }));
  expect(await within(dialog).findByText(/no tiene errores/)).toBeTruthy();
  expect(importCalls(calls).map((call) => call.query.confirmar)).toEqual(["false"]);
  const listBefore = calls.filter((call) => call.path === "/api/docentes").length;

  await user.click(within(dialog).getByRole("button", { name: "Importar" }));
  expect(await within(dialog).findByText("Se registraron 2 docentes.")).toBeTruthy();
  expect(importCalls(calls).map((call) => call.query.confirmar)).toEqual(["false", "true"]);
  await waitFor(() =>
    expect(calls.filter((call) => call.path === "/api/docentes").length).toBeGreaterThan(listBefore),
  );
  await user.click(within(dialog).getByRole("button", { name: "Listo" }));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Muestra las filas con error y su motivo, y permite importar solo las válidas", async () => {
  backend({ "POST /api/docentes/importaciones": mixed });
  const user = userEvent.setup();
  const dialog = await openDialog(user);
  await choose(user, dialog, excel());
  await user.click(within(dialog).getByRole("button", { name: "Validar archivo" }));
  expect(await within(dialog).findByText(/solo se registrarán las válidas/)).toBeTruthy();
  expect(within(dialog).getByText("El codigo del docente ya existe")).toBeTruthy();
  await user.click(within(dialog).getByRole("button", { name: "Importar" }));
  expect(await within(dialog).findByText("Registradas")).toBeTruthy();
  expect(within(dialog).getByText("El codigo del docente ya existe")).toBeTruthy();
});

test("Si ninguna fila es válida no permite importar", async () => {
  backend({ "POST /api/docentes/importaciones": noneValid });
  const user = userEvent.setup();
  const dialog = await openDialog(user);
  await choose(user, dialog, excel());
  await user.click(within(dialog).getByRole("button", { name: "Validar archivo" }));
  expect(await within(dialog).findByText(/Ninguna fila es válida/)).toBeTruthy();
  expect(within(dialog).getByRole("button", { name: "Importar" }).disabled).toBe(true);
});

test("Cambiar de archivo obliga a validar de nuevo", async () => {
  backend({ "POST /api/docentes/importaciones": clean });
  const user = userEvent.setup();
  const dialog = await openDialog(user);
  await choose(user, dialog, excel());
  await user.click(within(dialog).getByRole("button", { name: "Validar archivo" }));
  await within(dialog).findByText(/no tiene errores/);
  await choose(user, dialog, excel("otro.xlsx"));
  expect(within(dialog).getByRole("button", { name: "Importar" }).disabled).toBe(true);
});

test("Exige un archivo .xlsx antes de llamar al servidor", async () => {
  const calls = backend();
  const user = userEvent.setup();
  const dialog = await openDialog(user);
  await user.click(within(dialog).getByRole("button", { name: "Validar archivo" }));
  expect(await within(dialog).findByText("Selecciona al menos un archivo Excel para importar.")).toBeTruthy();
  expect(importCalls(calls)).toEqual([]);
});

test("Muestra el error del servidor al validar y al importar", async () => {
  let attempts = 0;
  backend({
    "POST /api/docentes/importaciones": () => {
      attempts += 1;
      return attempts === 2 ? reply(500, { message: "No se pudo importar" }) : clean;
    },
  });
  const user = userEvent.setup();
  const dialog = await openDialog(user);
  await choose(user, dialog, excel());
  await user.click(within(dialog).getByRole("button", { name: "Validar archivo" }));
  await within(dialog).findByText(/no tiene errores/);
  await user.click(within(dialog).getByRole("button", { name: "Importar" }));
  expect(await within(dialog).findByRole("alert")).toBeTruthy();
  expect(within(dialog).queryByText(/Se registraron/)).toBeNull();
});

test("Si el archivo no se puede leer lo indica", async () => {
  backend({
    "POST /api/docentes/importaciones": reply(422, { message: "El archivo no es un Excel válido" }),
  });
  const user = userEvent.setup();
  const dialog = await openDialog(user);
  await choose(user, dialog, excel());
  await user.click(within(dialog).getByRole("button", { name: "Validar archivo" }));
  expect((await within(dialog).findByRole("alert")).textContent).toContain("Excel válido");
});

test("Descarga la plantilla de docentes", async () => {
  const calls = backend({
    "GET /api/docentes/importaciones/plantilla": {},
  });
    vi.stubGlobal("URL", class extends URL {
    static createObjectURL = vi.fn(() => "blob:plantilla");
    static revokeObjectURL = vi.fn();
  });
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  const user = userEvent.setup();
  const dialog = await openDialog(user);
  await user.click(within(dialog).getByRole("button", { name: "Descargar plantilla de docentes" }));
  await waitFor(() => expect(click).toHaveBeenCalled());
  expect(calls.some((call) => call.path === `${IMPORT_PATH}/plantilla`)).toBe(true);
});

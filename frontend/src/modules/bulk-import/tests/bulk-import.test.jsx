import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { Schedules } from "@/modules/schedules";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

// OBS-05: carga masiva común, probada desde la pantalla de aulas.
const IMPORT_PATH = "/api/aulas/importaciones";
const excel = () =>
  new File(["x".repeat(10)], "aulas.xlsx", {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
const review = {
  total: 4,
  validas: 2,
  creadas: 0,
  omitidas: [{ row: 5, field: "codigo", message: "Ya está registrado; no se duplica" }],
  errores: [{ row: 3, field: "capacidad", message: "La columna capacidad debe ser un número entero" }],
};
const imported = { ...review, creadas: 2 };

function backend(routes = {}) {
  return fakeBackend({
    "GET /api/aulas": page([]),
    "GET /api/aulas/resumen": { habilitadas: 0 },
    ...routes,
  });
}
async function openDialog(user) {
  render(<Schedules canWrite tab="aulas" />);
  await user.click(await screen.findByRole("button", { name: "Carga masiva" }));
  return screen.getByRole("dialog", { name: "Carga masiva de aulas" });
}
const importCalls = (calls) => calls.filter((call) => call.path === IMPORT_PATH);

beforeEach(() => {
  signIn({ permissions: ["ACADEMICO_LEER", "ACADEMICO_ESCRIBIR"] });
  setUrl("/horarios/aulas");
});

test("Revisa sin guardar, muestra fila, campo y motivo, y luego importa las válidas", async () => {
  const calls = backend({
    [`POST ${IMPORT_PATH}`]: (call) => (call.query.confirmar === "true" ? imported : review),
  });
  const user = userEvent.setup();
  const dialog = await openDialog(user);
  await user.upload(within(dialog).getByLabelText("Archivo Excel de aulas", { exact: false }), excel());
  await user.click(within(dialog).getByRole("button", { name: "Revisar archivo" }));

  expect(await within(dialog).findByText(/2 listas para importar/)).toBeTruthy();
  expect(
    within(dialog).getByText("Fila 3 · capacidad · La columna capacidad debe ser un número entero"),
  ).toBeTruthy();
  expect(within(dialog).getByText("Fila 5 · codigo · Ya está registrado; no se duplica")).toBeTruthy();
  expect(importCalls(calls).map((call) => call.query.confirmar)).toEqual(["false"]);

  const listBefore = calls.filter((call) => call.path === "/api/aulas").length;
  await user.click(within(dialog).getByRole("button", { name: "Importar filas válidas" }));
  expect(await within(dialog).findByText(/2 creadas/)).toBeTruthy();
  expect(importCalls(calls).map((call) => call.query.confirmar)).toEqual(["false", "true"]);
  expect(within(dialog).queryByRole("button", { name: "Importar filas válidas" })).toBeNull();
  await waitFor(() =>
    expect(calls.filter((call) => call.path === "/api/aulas").length).toBeGreaterThan(listBefore),
  );
});

test("Si ninguna fila es válida no permite importar", async () => {
  backend({ [`POST ${IMPORT_PATH}`]: { ...review, validas: 0 } });
  const user = userEvent.setup();
  const dialog = await openDialog(user);
  await user.upload(within(dialog).getByLabelText("Archivo Excel de aulas", { exact: false }), excel());
  await user.click(within(dialog).getByRole("button", { name: "Revisar archivo" }));
  const importButton = await within(dialog).findByRole("button", { name: "Importar filas válidas" });
  expect(importButton.disabled).toBe(true);
});

test("Sin permiso de escritura no aparece el botón Carga masiva", async () => {
  backend();
  render(<Schedules canWrite={false} tab="aulas" />);
  expect(await screen.findByText("Gestión de aulas")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Carga masiva" })).toBeNull();
});

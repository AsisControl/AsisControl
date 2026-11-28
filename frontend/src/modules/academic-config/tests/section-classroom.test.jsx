import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { page, reply, signIn } from "../../../../tests/support/backend.js";
import { AcademicConfiguration } from "@/modules/academic-config";
import { academicBackend, section, year } from "./fixtures.js";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const classroom = (id, nombre, capacidad) => ({
  id,
  codigo: `A-${id}`,
  nombre,
  tipoAula: "AULA_REGULAR",
  capacidad,
  ubicacion: "",
  equipamiento: null,
  activo: true,
  version: 0,
});
const big = classroom(1, "Aula 101", 40);
const small = classroom(2, "Aula 102", 1);
const assigned = { id: 1, nombre: "Aula 101", capacidad: 40 };
const withClassroom = () =>
  year({ secciones: [section({ aula: assigned }), section({ id: 32, nombre: "B" })] });

function backend(data = year(), routes = {}) {
  return academicBackend([data], {
    "GET /api/aulas": page([big, small]),
    "GET /api/secciones/:id": { docenteTutorId: null },
    "PUT /api/secciones/:id": reply(200, {}),
    "POST /api/secciones": reply(201, { id: 40 }),
    ...routes,
  });
}

async function open(user, name) {
  render(<AcademicConfiguration />);
  await screen.findByRole("heading", { name: "Año 2026" });
  await user.click(screen.getByText(name, { selector: "button" }).closest("button"));
}
async function openEdit(user) {
  render(<AcademicConfiguration />);
  await screen.findByRole("heading", { name: "Año 2026" });
  await user.click(screen.getByLabelText("Editar 1° Grado, Sección A"));
  return screen.getByRole("dialog", { name: "Editar sección" });
}
const select = (dialog) => within(dialog).getByLabelText(/^Aula asignada/);
const loaded = (dialog) => within(dialog).findByText(/Varias secciones pueden compartirla/);
async function save(user, dialog) {
  await user.type(within(dialog).getByLabelText(/^Motivo/), "Cambio de aula");
  await user.click(within(dialog).getByText("Guardar sección"));
}
const body = (calls, method) => calls.find((c) => c.method === method).body;

beforeEach(() => {
  signIn();
});

test("La lista muestra el aula de cada sección o indica que no tiene", async () => {
  backend(withClassroom());
  render(<AcademicConfiguration />);
  await screen.findByRole("heading", { name: "Año 2026" });
  expect(screen.getByRole("columnheader", { name: "Aula" })).toBeTruthy();
  expect(within(screen.getByText("Sección A").closest("tr")).getByText("Aula 101")).toBeTruthy();
  expect(within(screen.getByText("Sección B").closest("tr")).getByText("Sin aula")).toBeTruthy();
});

test("Crea una sección con el aula elegida entre las habilitadas", async () => {
  const { calls } = backend();
  const user = userEvent.setup();
  await open(user, "+ Nueva sección");
  const dialog = screen.getByRole("dialog", { name: "Nueva sección" });
  await loaded(dialog);
  expect(select(dialog).disabled).toBe(false);
  expect(select(dialog).value).toBe("");
  // No se filtra por capacidad en el cliente: la regla la aplica el servidor.
  expect([...select(dialog).options].map((o) => o.textContent)).toEqual([
    "Sin aula",
    "Aula 101 · 40 estudiantes",
    "Aula 102 · 1 estudiante",
  ]);
  expect(within(dialog).queryByText(/Próximamente/)).toBeNull();
  await user.type(within(dialog).getByLabelText(/^Sección/), "C");
  await user.selectOptions(select(dialog), "1");
  await save(user, dialog);
  expect(await screen.findByText("Sección creada correctamente.")).toBeTruthy();
  expect(body(calls, "POST").aulaId).toBe(1);
  expect(body(calls, "POST")).not.toHaveProperty("quitarAula");
  expect(calls.find((c) => c.path === "/api/aulas").query).toMatchObject({ sort: "codigo" });
});

test("Al editar otros datos reenvía el aula actual", async () => {
  const { calls } = backend(withClassroom());
  const user = userEvent.setup();
  const dialog = await openEdit(user);
  expect(select(dialog).value).toBe("1");
  await loaded(dialog);
  expect([...select(dialog).options].filter((o) => o.value === "1")).toHaveLength(1);
  await save(user, dialog);
  expect(await screen.findByText("Sección actualizada.")).toBeTruthy();
  expect(body(calls, "PUT")).toMatchObject({ aulaId: 1, quitarAula: false, version: 2 });
});

test("Elegir «Sin aula» pide quitarla", async () => {
  const { calls } = backend(withClassroom());
  const user = userEvent.setup();
  const dialog = await openEdit(user);
  await user.selectOptions(select(dialog), "");
  await save(user, dialog);
  expect(await screen.findByText("Sección actualizada.")).toBeTruthy();
  expect(body(calls, "PUT")).toMatchObject({ aulaId: null, quitarAula: true });
});

test("Una sección sin aula se guarda sin pedir quitar nada", async () => {
  const { calls } = backend();
  const user = userEvent.setup();
  const dialog = await openEdit(user);
  await save(user, dialog);
  expect(await screen.findByText("Sección actualizada.")).toBeTruthy();
  expect(body(calls, "PUT")).toMatchObject({ aulaId: null, quitarAula: false });
});

test("Muestra el error de capacidad del servidor, marca el selector y permite corregir", async () => {
  const rejection = "El aula Aula 102 admite 1 estudiantes y la sección necesita capacidad para 35.";
  const { calls } = backend(year(), {
    "PUT /api/secciones/:id": ({ body: sent }) =>
      sent.aulaId === 2
        ? reply(422, { message: rejection, fieldErrors: [{ field: "aulaId", message: rejection }] })
        : reply(200, {}),
  });
  const user = userEvent.setup();
  const dialog = await openEdit(user);
  await loaded(dialog);
  await user.selectOptions(select(dialog), "2");
  await save(user, dialog);
  expect((await within(dialog).findByRole("alert")).textContent).toBe(rejection);
  expect(select(dialog).getAttribute("aria-invalid")).toBe("true");
  expect(select(dialog).value).toBe("2");
  await user.selectOptions(select(dialog), "1");
  expect(select(dialog).getAttribute("aria-invalid")).toBe("false");
  await user.click(within(dialog).getByText("Guardar sección"));
  expect(await screen.findByText("Sección actualizada.")).toBeTruthy();
  expect(calls.filter((c) => c.method === "PUT").map((c) => c.body.aulaId)).toEqual([2, 1]);
});

test("Si la lista de aulas falla, lo avisa y conserva el aula actual", async () => {
  const { calls } = backend(withClassroom(), {
    "GET /api/aulas": reply(500, { message: "No disponible" }),
  });
  const user = userEvent.setup();
  const dialog = await openEdit(user);
  expect(
    await within(dialog).findByText("No se pudo cargar la lista de aulas: No disponible"),
  ).toBeTruthy();
  expect(select(dialog).value).toBe("1");
  await save(user, dialog);
  await waitFor(() => expect(calls.some((c) => c.method === "PUT")).toBe(true));
  expect(body(calls, "PUT")).toMatchObject({ aulaId: 1, quitarAula: false });
});

test.each([
  ["responde", () => page([big])],
  ["falla", () => reply(500, { message: "No disponible" })],
])("Cerrar el formulario antes de que la lista de aulas %s no deja avisos", async (_, answer) => {
  let release;
  const waiting = new Promise((resolve) => (release = resolve));
  backend(year(), { "GET /api/aulas": () => waiting.then(answer) });
  const user = userEvent.setup();
  await open(user, "+ Nueva sección");
  const dialog = screen.getByRole("dialog", { name: "Nueva sección" });
  expect(within(dialog).getByText("Cargando aulas…")).toBeTruthy();
  await user.click(within(dialog).getByText("Cancelar"));
  release();
  await waiting;
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(screen.queryByText(/lista de aulas/)).toBeNull();
});

test("Los demás diálogos de la lista se cierran sin guardar", async () => {
  const { calls } = backend(year(), { "GET /api/matriculas": page([]) });
  const user = userEvent.setup();
  await open(user, "Generar propuestas");
  const closeWith = async (label) => {
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: label }));
    expect(screen.queryByRole("dialog")).toBeNull();
  };
  await closeWith("Cancelar");
  await user.click(screen.getByRole("button", { name: "Ver alumnos de 1° Grado, Sección A" }));
  await closeWith("Salir");
  const first = screen.getByText("Sección B").closest("tr");
  await user.click(within(first).getByRole("button", { name: "Inhabilitar" }));
  await closeWith("Cancelar");
  await user.click(screen.getByRole("button", { name: "Carga masiva" }));
  await closeWith("Cerrar");
  expect(calls.every((c) => c.method === "GET")).toBe(true);
});

test.each([
  ["recarga el año y lo avisa", false],
  ["no avisa si la recarga falla", true],
])("Tras una carga masiva de secciones %s", async (_, reloadFails) => {
  let reloads = 0;
  const result = { total: 1, validas: 1, creadas: 0, omitidas: [], errores: [] };
  backend(year(), {
    "POST /api/secciones/importaciones": ({ query }) =>
      query.confirmar === "true" ? { ...result, creadas: 1 } : result,
    "GET /api/configuracion-academica/anios/:anio": () =>
      ++reloads > 1 && reloadFails ? reply(500, null) : year(),
  });
  const user = userEvent.setup();
  await open(user, "Carga masiva");
  const dialog = screen.getByRole("dialog");
  await user.upload(
    within(dialog).getByLabelText("Archivo Excel de secciones", { exact: false }),
    new File(["x"], "secciones.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    }),
  );
  await user.click(within(dialog).getByRole("button", { name: "Revisar archivo" }));
  await user.click(await within(dialog).findByRole("button", { name: "Importar filas válidas" }));
  await waitFor(() => expect(reloads).toBeGreaterThan(1));
  if (reloadFails) expect(screen.queryByText("Carga masiva: 1 secciones creadas.")).toBeNull();
  else expect(await screen.findByText("Carga masiva: 1 secciones creadas.")).toBeTruthy();
});

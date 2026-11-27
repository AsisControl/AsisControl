import { expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply } from "../../../../tests/support/backend.js";
import { StudyPlansPanel } from "../study-plans-panel";

const year = { id: 1, anio: 2027, grados: [{ id: 10, numero: 1, activo: true }] };
const course = { id: 3, nombre: "Matemática", codigo: "MAT", horasSemanales: 4 };
const art = { id: 4, nombre: "Arte y Cultura", codigo: "ART", horasSemanales: 2 };
const problema = { id: 11, nombre: "Resuelve problemas", codigo: "MAT-1", activa: true };
const estadistica = { id: 12, nombre: "Interpreta datos", codigo: "MAT-2", activa: true };
const dibujo = { id: 13, nombre: "Explora lenguajes artísticos", codigo: "ART-1", activa: true };
const path = "/api/planes-estudios/2027/1";
const namePath = "/api/planes-estudios/2027/nombre";
const name = { nombre: "Plan de estudios 2027", version: 0 };
const annual = ({ id, nombre, horasSemanales }) => ({ id, nombre, horasSemanales });

function plan(overrides = {}) {
  return { anio: 2027, grado: 1, estadoAnio: "ACTIVO", editable: true, definido: false, version: 0, cursos: [], ...overrides };
}

async function selectPlan(user) {
  await screen.findByRole("option", { name: "2027" });
  await user.selectOptions(screen.getByLabelText("Año académico"), "2027");
  await user.selectOptions(screen.getByLabelText("Grado"), "1");
}

test("busca por código y por grado en el mismo catálogo y guarda el plan inicial", async () => {
  const calls = fakeBackend({
    "GET /api/configuracion-academica/anios": page([year]),
    [`GET ${namePath}`]: name,
    [`GET ${path}`]: plan(),
    "GET /api/cursos": ({ query }) => page(query.busqueda ? [course] : [art, course]),
    [`PUT ${path}`]: plan({ definido: true, version: 1, cursos: [course] }),
  });
  render(<StudyPlansPanel canWrite />);
  const user = userEvent.setup();
  await selectPlan(user);
  await user.click(await screen.findByRole("button", { name: "Agregar curso", exact: true }));
  expect(screen.getByRole("button", { name: "Guardar plan" }).disabled).toBe(true);
  const catalog = within(screen.getByRole("region", { name: "Catálogo de cursos" }));
  await catalog.findByText(art.nombre);
  await user.type(catalog.getByRole("searchbox"), "MAT");
  await user.selectOptions(catalog.getByLabelText("Mostrar cursos"), "grade");
  await waitFor(() => expect(calls.some(call => call.path === "/api/cursos" && call.query.busqueda === "MAT"
    && call.query.anio === "2027" && call.query.grado === "1")).toBe(true));
  await user.click(await catalog.findByRole("button", { name: "Agregar Matemática" }));
  expect(catalog.getByText("En el plan")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Guardar plan" }));
  await screen.findByText("Se guardó el plan de estudios.");
  expect(calls.find(call => call.method === "PUT").body).toEqual({ version: 0, cursos: [annual(course)] });
  const table = within(screen.getByRole("table", { name: "Cursos seleccionados" }));
  expect(table.getByText(course.nombre)).toBeTruthy();
  expect(table.getByText(course.codigo)).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Guardar plan" })).toBeNull();
  expect(screen.getByRole("button", { name: "Editar plan" })).toBeTruthy();
});

test("edita con motivo, conserva la versión y permite cancelar el borrador", async () => {
  const saved = plan({ definido: true, version: 2, cursos: [course, art] });
  const calls = fakeBackend({
    "GET /api/configuracion-academica/anios": page([year]),
    [`GET ${namePath}`]: name,
    [`GET ${path}`]: saved,
    [`PUT ${path}`]: { ...saved, version: 3, cursos: [course] },
  });
  render(<StudyPlansPanel canWrite />);
  const user = userEvent.setup();
  await selectPlan(user);
  await user.click(await screen.findByRole("button", { name: "Editar plan" }));
  await user.click(screen.getByRole("button", { name: "Quitar Arte y Cultura" }));
  const save = screen.getByRole("button", { name: "Guardar cambios" });
  expect(save.disabled).toBe(true);
  await user.click(screen.getByRole("button", { name: "Cancelar" }));
  expect(screen.getByText(art.nombre)).toBeTruthy();
  expect(calls.some(call => call.method === "PUT")).toBe(false);
  await user.click(screen.getByRole("button", { name: "Editar plan" }));
  await user.click(screen.getByRole("button", { name: "Quitar Arte y Cultura" }));
  await user.type(screen.getByLabelText("Motivo de la modificación"), "  Se corrigió la lista  ");
  await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
  await screen.findByText("Se guardaron los cambios del plan.");
  expect(calls.find(call => call.method === "PUT").body).toEqual({ version: 2, cursos: [annual(course)], motivo: "Se corrigió la lista" });
});

test("previsualiza el plan anterior y agrega faltantes conservando los cursos de destino", async () => {
  const current = { ...course, competencias: [estadistica] };
  const historical = { ...course, nombre: "Matemática 2026", horasSemanales: 5, competencias: [problema] };
  const updatedArt = { ...art, nombre: "Arte aplicado", horasSemanales: 3 };
  const saved = plan({ definido: true, version: 2, cursos: [current] });
  const calls = fakeBackend({
    "GET /api/configuracion-academica/anios": page([year]),
    [`GET ${namePath}`]: name,
    [`GET ${path}`]: saved,
    [`GET ${path}/anterior`]: { anioOrigen: 2026, cursos: [historical, { ...art, competencias: [dibujo] }] },
    [`PUT ${path}`]: { ...saved, version: 3, cursos: [current, { ...updatedArt, competencias: [dibujo] }] },
  });
  render(<StudyPlansPanel canWrite />);
  const user = userEvent.setup();
  await selectPlan(user);
  await user.click(await screen.findByRole("button", { name: "Editar plan" }));
  await user.click(screen.getByRole("button", { name: "Importar plan del año anterior" }));
  const preview = within(screen.getByRole("region", { name: "Vista previa del plan anterior" }));
  expect(await preview.findByText("Matemática 2026")).toBeTruthy();
  expect(preview.getByText("En la selección")).toBeTruthy();
  await user.click(preview.getByRole("button", { name: "Agregar 1 curso" }));
  expect(screen.getByRole("textbox", { name: "Nombre en el plan de MAT" }).value).toBe(course.nombre);
  expect(calls.some(call => call.method === "PUT")).toBe(false);

  const artName = screen.getByRole("textbox", { name: "Nombre en el plan de ART" });
  const artHours = screen.getByRole("spinbutton", { name: "Horas semanales de ART" });
  await user.clear(artName);
  await user.type(artName, updatedArt.nombre);
  await user.clear(artHours);
  await user.type(artHours, String(updatedArt.horasSemanales));
  expect(screen.getByRole("button", { name: "Guardar cambios" }).disabled).toBe(true);
  await user.type(screen.getByLabelText("Motivo de la modificación"), "Ajuste curricular 2027");
  await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
  expect(await screen.findByText(updatedArt.nombre)).toBeTruthy();
  expect(calls.find(call => call.method === "PUT").body).toEqual({
    version: 2, cursos: [
      { ...annual(course), competenciasIds: [estadistica.id] },
      { ...annual(updatedArt), competenciasIds: [dibujo.id] },
    ], motivo: "Ajuste curricular 2027",
  });
});

test("permite guardar un cambio exclusivo de competencias con un motivo", async () => {
  const saved = plan({ definido: true, version: 2, cursos: [{ ...course, competencias: [problema] }] });
  const calls = fakeBackend({
    "GET /api/configuracion-academica/anios": page([year]),
    [`GET ${namePath}`]: name,
    [`GET ${path}`]: saved,
    "GET /api/competencias": page([problema, estadistica]),
    [`PUT ${path}`]: { ...saved, version: 3, cursos: [{ ...course, competencias: [estadistica] }] },
  });
  render(<StudyPlansPanel canWrite />);
  const user = userEvent.setup();
  await selectPlan(user);
  await user.click(await screen.findByRole("button", { name: "Editar plan" }));
  const opener = screen.getByRole("button", { name: "Competencias de Matemática" });
  await user.click(opener);
  const editor = within(screen.getByRole("region", { name: "Competencias de Matemática" }));
  await user.click(await editor.findByRole("button", { name: "Agregar competencia Interpreta datos" }));
  await user.click(editor.getByRole("button", { name: "Quitar competencia Resuelve problemas" }));
  await user.click(editor.getByRole("button", { name: "Cerrar competencias" }));
  expect(document.activeElement).toBe(opener);
  const save = screen.getByRole("button", { name: "Guardar cambios" });
  expect(save.disabled).toBe(true);
  await user.type(screen.getByLabelText("Motivo de la modificación"), "Ajuste de la competencia anual");
  await user.click(save);
  await screen.findByText("Se guardaron los cambios del plan.");
  expect(calls.find(call => call.method === "PUT").body).toEqual({ version: 2,
    cursos: [{ ...annual(course), competenciasIds: [estadistica.id] }], motivo: "Ajuste de la competencia anual" });
});

test("el contenido de un año cerrado es de solo lectura", async () => {
  fakeBackend({
    "GET /api/configuracion-academica/anios": page([year]),
    [`GET ${namePath}`]: name,
    [`GET ${path}`]: plan({ cursos: [course], definido: true, estadoAnio: "CERRADO", editable: false }),
  });
  render(<StudyPlansPanel canWrite />);
  await selectPlan(userEvent.setup());
  await screen.findByText(course.nombre);
  expect(screen.queryByRole("button", { name: "Editar plan" })).toBeNull();
  expect(screen.queryByRole("button", { name: "Agregar curso" })).toBeNull();
  expect(screen.queryByRole("button", { name: "Importar plan del año anterior" })).toBeNull();
});

test("descarga el plan del año entero sin depender del grado seleccionado", async () => {
  const calls = fakeBackend({
    "GET /api/configuracion-academica/anios": page([year]),
    [`GET ${namePath}`]: name,
    "GET /api/planes-estudios/2027/pdf": {},
  });
  const originalCreate = URL.createObjectURL;
  const originalRevoke = URL.revokeObjectURL;
  URL.createObjectURL = vi.fn(() => "blob:plan");
  URL.revokeObjectURL = vi.fn();
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  try {
    render(<StudyPlansPanel canWrite={false} />);
    const user = userEvent.setup();
    const button = screen.getByRole("button", { name: /Descargar plan de estudios/ });
    expect(button.disabled).toBe(true);
    await screen.findByRole("option", { name: "2027" });
    await user.selectOptions(screen.getByLabelText("Año académico"), "2027");
    await user.click(button);
    await waitFor(() => expect(click.mock.instances[0]?.download).toBe("Plan_de_Estudios_AsisControl_2027.pdf"));
    expect(calls.some(call => call.path === "/api/planes-estudios/2027/pdf")).toBe(true);
  } finally {
    click.mockRestore();
    URL.createObjectURL = originalCreate;
    URL.revokeObjectURL = originalRevoke;
  }
});

test("avisa el error de descarga y conserva el año para reintentar", async () => {
  fakeBackend({
    "GET /api/configuracion-academica/anios": page([year]),
    [`GET ${namePath}`]: name,
    "GET /api/planes-estudios/2027/pdf": reply(500, { message: "No se pudo generar el PDF." }),
  });
  render(<StudyPlansPanel canWrite />);
  const user = userEvent.setup();
  await screen.findByRole("option", { name: "2027" });
  await user.selectOptions(screen.getByLabelText("Año académico"), "2027");
  const button = screen.getByRole("button", { name: /Descargar plan de estudios/ });
  await user.click(button);
  expect((await screen.findByRole("alert")).textContent).toContain("No se pudo generar el PDF.");
  expect(button.disabled).toBe(false);
});

test("renombra el plan anual sin seleccionar grado ni guardar cursos", async () => {
  const calls = fakeBackend({
    "GET /api/configuracion-academica/anios": page([year]),
    [`GET ${namePath}`]: name,
    [`PUT ${namePath}`]: { nombre: "Plan histórico de secundaria", version: 1 },
  });
  render(<StudyPlansPanel canWrite />);
  const user = userEvent.setup();
  await screen.findByRole("option", { name: "2027" });
  await user.selectOptions(screen.getByLabelText("Año académico"), "2027");
  const field = await screen.findByLabelText("Nombre del plan anual");
  await user.clear(field);
  await user.type(field, "Plan histórico de secundaria");
  await user.click(screen.getByRole("button", { name: "Guardar nombre" }));
  await screen.findByText("Se guardó el nombre del plan.");
  expect(calls.filter(call => call.method === "PUT").map(call => [call.path, call.body]))
    .toEqual([[namePath, { nombre: "Plan histórico de secundaria", version: 0 }]]);
});

test("al guardar un plan con menos horas avisa las secciones que quedan excedidas sin bloquear", async () => {
  const saved = plan({ definido: true, version: 2, cursos: [course, art] });
  const excedida = { seccionId: 8, seccion: "1° A", cursoId: 3, curso: "Matemática", horasPlan: 3, minutosProgramados: 240 };
  fakeBackend({
    "GET /api/configuracion-academica/anios": page([year]),
    [`GET ${namePath}`]: name,
    [`GET ${path}`]: { ...saved, seccionesExcedidas: [] },
    [`PUT ${path}`]: { ...saved, version: 3, cursos: [course], seccionesExcedidas: [excedida] },
  });
  render(<StudyPlansPanel canWrite />);
  const user = userEvent.setup();
  await selectPlan(user);
  await user.click(await screen.findByRole("button", { name: "Editar plan" }));
  expect(screen.queryByRole("status", { name: "Secciones que exceden el plan" })).toBeNull();
  await user.click(screen.getByRole("button", { name: "Quitar Arte y Cultura" }));
  await user.type(screen.getByLabelText("Motivo de la modificación"), "Ajuste de horas");
  await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
  await screen.findByText("Se guardaron los cambios del plan.");
  const notice = screen.getByRole("status", { name: "Secciones que exceden el plan" });
  expect(notice.textContent).toContain("1° A · Matemática · 4 h programadas, el plan permite 3 h (sobra 1 h)");
  expect(within(notice).getByRole("link", { name: "Horarios › Horario" }).getAttribute("href")).toBe("/horarios/horario");
});
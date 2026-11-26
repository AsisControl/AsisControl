import { beforeEach, expect, test } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { GradingRulesPanel } from "../grading-rules/grading-rules-panel";

const exam = { tipoEvaluacion: "EXAMEN", nombre: "Exámenes", peso: 60 };
const project = { tipoEvaluacion: "PROYECTO", nombre: "Proyecto", peso: 40 };
const active = { id: 5, estado: "VIGENTE", numeroVersion: 1, version: 1, componentes: [exam, project] };
const draft = { id: 6, estado: "BORRADOR", numeroVersion: 2, version: 3, componentes: [exam, project] };

beforeEach(() => signIn());

async function openEditor(routes = {}, canWrite = true) {
  const calls = fakeBackend({
    "GET /api/configuracion-academica/anios": page([{ id: 1, anio: 2026, grados: [{ id: 8, numero: 1, activo: true }] }]),
    "GET /api/planes-estudios/2026/1": { cursos: [{ id: 4, nombre: "Matemática", codigo: "MAT" }] },
    "GET /api/reglas-calificacion": [],
    "GET /api/escala-institucional": [],
    ...routes,
  });
  const user = userEvent.setup();
  render(<GradingRulesPanel canWrite={canWrite} />);
  await screen.findByRole("option", { name: "2026" });
  await user.selectOptions(screen.getByLabelText("Año académico"), "2026");
  await user.selectOptions(screen.getByLabelText("Grado"), "8");
  await screen.findByRole("option", { name: "Matemática · MAT" });
  await user.selectOptions(screen.getByLabelText("Curso"), "4");
  const workspace = await screen.findByRole("region", { name: "Fórmula del curso" });
  const start = within(workspace).queryByRole("button", { name: "Crear regla" });
  if (start) {
    expect(within(workspace).queryByRole("region", { name: "Editor de borrador" })).toBeNull();
    await user.click(start);
  }
  return { calls, user };
}

test("Empieza con un componente, agrega uno por clic e impide repetir tipos", async () => {
  const { user } = await openEditor();
  const editor = within(await screen.findByRole("region", { name: "Editor de borrador" }));
  expect(editor.getAllByRole("group", { name: /Componente/ })).toHaveLength(1);
  expect(editor.queryByLabelText(/Nivel/)).toBeNull();
  for (let count = 2; count <= 5; count++) {
    await user.click(editor.getByRole("button", { name: "Agregar componente" }));
    expect(editor.getAllByRole("group", { name: /Componente/ })).toHaveLength(count);
  }
  expect(editor.getByRole("button", { name: "Agregar componente" }).disabled).toBe(true);
  const last = within(editor.getByRole("group", { name: "Componente 5" }));
  expect(last.getAllByRole("option")).toHaveLength(1);
  await user.click(last.getByRole("button", { name: /^Quitar componente/ }));
  expect(editor.getAllByRole("group", { name: /Componente/ })).toHaveLength(4);
  expect(editor.getByRole("button", { name: "Agregar componente" }).disabled).toBe(false);
  expect(screen.queryByText("1 registro · Página 1 de 1")).toBeNull();
});

test.each([
  ["Empezar desde cero", [100]],
  ["Copiar regla vigente", [60, 40]],
])("Mantiene la vigente separada y permite %s", async (action, expected) => {
  const { user } = await openEditor({ "GET /api/reglas-calificacion": [active] });
  const current = within(await screen.findByRole("region", { name: "Regla vigente · versión 1" }));
  expect(current.getByRole("cell", { name: "60 %" })).toBeTruthy();
  expect(current.queryByRole("spinbutton")).toBeNull();
  expect(screen.queryByRole("region", { name: "Editor de borrador" })).toBeNull();
  await user.click(screen.getByRole("button", { name: action }));
  const editor = within(screen.getByRole("region", { name: "Editor de borrador" }));
  expect(editor.getAllByRole("spinbutton").map(input => Number(input.value))).toEqual(expected);
  expect(current.getByRole("cell", { name: "40 %" })).toBeTruthy();
});

test("Guarda una regla nueva incompleta sin enviar una versión previa", async () => {
  const { user, calls } = await openEditor({
    "PUT /api/reglas-calificacion/borrador": ({ body }) =>
      ({ ...draft, numeroVersion: 1, version: 0, componentes: body.componentes }),
  });
  const editor = within(await screen.findByRole("region", { name: "Editor de borrador" }));
  const weight = editor.getByRole("spinbutton");
  await user.clear(weight);
  expect(editor.getByRole("button", { name: "Guardar borrador" }).disabled).toBe(true);
  await user.type(weight, "60");
  await user.click(editor.getByRole("button", { name: "Guardar borrador" }));
  await screen.findByText("Borrador de la versión 1 guardado.");
  expect(calls.find(call => call.method === "PUT").body).toEqual({
    idCurso: 4, idGrado: 8, formula: "PROMEDIO_PONDERADO", componentes: [exam],
  });
  expect(editor.getByRole("button", { name: "Activar regla" }).disabled).toBe(true);
});

test("Recupera el borrador y distingue total incompleto, excedido y correcto", async () => {
  const { user, calls } = await openEditor({
    "GET /api/reglas-calificacion": [draft, active],
    "PUT /api/reglas-calificacion/borrador": ({ body }) => ({ ...draft, version: 4, componentes: body.componentes }),
  });
  const editor = within(await screen.findByRole("region", { name: "Editor de borrador" }));
  expect(editor.getByRole("heading", { name: "Versión 2 en edición" })).toBeTruthy();
  expect(editor.getAllByRole("textbox").map(input => input.value)).toEqual(["Exámenes", "Proyecto"]);
  expect(editor.queryByText("La suma es correcta.")).toBeNull();
  expect(editor.getByRole("progressbar").value).toBe(100);
  const weight = within(editor.getByRole("group", { name: "Componente 2" })).getByRole("spinbutton");
  await user.clear(weight);
  await user.type(weight, "45");
  expect(editor.getByText("Excede en 5 %.")).toBeTruthy();
  expect(editor.getByRole("progressbar").value).toBe(100);
  expect(editor.getByRole("button", { name: "Activar regla" }).disabled).toBe(true);
  await user.clear(weight);
  await user.type(weight, "30");
  expect(editor.getByText("Falta 10 %.")).toBeTruthy();
  expect(editor.getByRole("progressbar").value).toBe(90);
  await user.click(editor.getByRole("button", { name: "Guardar borrador" }));
  await screen.findByText("Borrador de la versión 2 guardado.");
  expect(calls.find(call => call.method === "PUT").body).toMatchObject({
    idCurso: 4, idGrado: 8, version: 3, componentes: [exam, { ...project, peso: 30 }],
  });
  expect(editor.getByRole("button", { name: "Activar regla" }).disabled).toBe(true);
  expect(editor.getByRole("button", { name: "Vista previa" }).disabled).toBe(true);
});

test("Simula con el endpoint existente y permite reintentar en el modal", async () => {
  let attempts = 0;
  const { user, calls } = await openEditor({
    "GET /api/reglas-calificacion": [draft, active],
    "POST /api/reglas-calificacion/vista-previa-numerica": () => ++attempts === 1
      ? reply(422, { message: "No se pudo simular la regla." })
      : { nivelConsolidado: "A", promedioPonderado: 16.35, notaRedondeada: 16,
          numeroVersionEscala: 2, componentesFaltantes: [] },
  });
  await user.click(await screen.findByRole("button", { name: "Vista previa" }));
  const modal = within(screen.getByRole("dialog", { name: "Vista previa de la regla" }));
  expect(modal.getByText(/no modifica las calificaciones reales/)).toBeTruthy();
  expect(modal.getByText("60 %")).toBeTruthy();
  await user.type(modal.getByLabelText("Nota hipotética de Exámenes"), "16.25");
  await user.type(modal.getByLabelText("Nota hipotética de Proyecto"), "16.50");
  await user.click(modal.getByRole("button", { name: "Simular resultado" }));
  await modal.findByRole("alert");
  await user.click(modal.getByRole("button", { name: "Simular resultado" }));
  const result = within(await modal.findByRole("region", { name: "Resultado simulado" }));
  expect(result.getByText("A")).toBeTruthy();
  expect(result.getByText("Promedio ponderado")).toBeTruthy();
  expect(result.getByText(/16[.,]35/)).toBeTruthy();
  expect(result.getByText("Nota redondeada")).toBeTruthy();
  expect(result.getByText("Escala institucional v2")).toBeTruthy();
  expect(calls.filter(call => call.method === "POST").at(-1).body).toEqual({
    formula: "PROMEDIO_PONDERADO", componentes: [exam, project],
    muestra: [{ tipoEvaluacion: "EXAMEN", nota: "16.25" }, { tipoEvaluacion: "PROYECTO", nota: "16.5" }],
  });
  await user.click(modal.getByRole("button", { name: "Cerrar", exact: true }));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Exige guardar y confirmar antes de activar y conserva la versión histórica", async () => {
  let attempts = 0;
  const { user, calls } = await openEditor({
    "GET /api/reglas-calificacion": [draft, active],
    "PUT /api/reglas-calificacion/borrador": ({ body }) => ({ ...draft, version: 4, componentes: body.componentes }),
    "POST /api/reglas-calificacion/6/activar": () => ++attempts === 1
      ? reply(409, { message: "No se pudo activar la versión." })
      : { ...draft, estado: "VIGENTE", version: 5 },
  });
  const button = await screen.findByRole("button", { name: "Activar regla" });
  const editor = within(screen.getByRole("region", { name: "Editor de borrador" }));
  await user.type(editor.getAllByRole("textbox")[0], " finales");
  expect(button.disabled).toBe(true);
  await user.click(editor.getByRole("button", { name: "Guardar borrador" }));
  await screen.findByText("Borrador de la versión 2 guardado.");
  await user.click(button);
  let modal = within(screen.getByRole("alertdialog", { name: "Activar regla de calificación" }));
  expect(calls.some(call => call.path.endsWith("/activar"))).toBe(false);
  await user.click(modal.getByRole("button", { name: "Cancelar" }));
  await user.click(button);
  modal = within(screen.getByRole("alertdialog", { name: "Activar regla de calificación" }));
  expect(modal.getByRole("button", { name: "Confirmar activación" }).disabled).toBe(true);
  await user.type(modal.getByLabelText("Motivo de activación"), "Acuerdo académico");
  await user.click(modal.getByRole("button", { name: "Confirmar activación" }));
  await modal.findByRole("alert");
  expect(screen.getByRole("region", { name: "Regla vigente · versión 1" })).toBeTruthy();
  await user.click(modal.getByRole("button", { name: "Confirmar activación" }));
  await screen.findByRole("region", { name: "Regla vigente · versión 2" });
  expect(calls.find(call => call.path.endsWith("/activar")).body).toEqual({ version: 4, motivo: "Acuerdo académico" });
  expect(within(screen.getByRole("region", { name: "Historial de reglas" })).getByText("Histórica")).toBeTruthy();
});

test("Pagina el historial y muestra el peso al abrir una versión en modo de consulta", async () => {
  const history = Array.from({ length: 6 }, (_, index) => ({
    ...active, id: 10 - index, numeroVersion: 6 - index, estado: index === 0 ? "VIGENTE" : "HISTORICA",
  }));
  const { user } = await openEditor({ "GET /api/reglas-calificacion": history }, false);
  const list = within(await screen.findByRole("region", { name: "Historial de reglas" }));
  expect(list.queryByText("Versión 1")).toBeNull();
  expect(screen.queryByRole("button", { name: "Agregar componente" })).toBeNull();
  await user.click(list.getByRole("button", { name: "Siguiente" }));
  await user.click(list.getByText("Versión 1"));
  const table = within(list.getByRole("table"));
  expect(table.getByRole("rowheader", { name: "Proyecto", exact: true })).toBeTruthy();
  expect(table.getByRole("cell", { name: "40 %" })).toBeTruthy();
  await user.click(list.getByRole("button", { name: "Anterior" }));
  expect(list.getByText("Versión 6")).toBeTruthy();
});

test("Recupera el borrador de escala, mantiene la vigente y muestra el error al activar", async () => {
  const ranges = [
    { nivel: "C", minimo: 0, maximo: 8 }, { nivel: "B", minimo: 9, maximo: 12 },
    { nivel: "A", minimo: 13, maximo: 16 }, { nivel: "AD", minimo: 17, maximo: 20 },
  ];
  const current = { id: 9, numeroVersion: 1, version: 1, estado: "VIGENTE", rangos: ranges,
    fechaAplicacion: "2026-06-01T12:00:00Z", actorAplicacion: "admin", motivoAplicacion: "Acuerdo anterior" };
  const draftScale = { ...current, id: 10, numeroVersion: 2, estado: "BORRADOR" };
  const calls = fakeBackend({
    "GET /api/configuracion-academica/anios": page([]),
    "GET /api/escala-institucional": [draftScale, current],
    "POST /api/escala-institucional/10/activar": reply(409, { message: "Otra sesión actualizó la escala." }),
  });
  const user = userEvent.setup();
  render(<GradingRulesPanel canWrite />);
  const panel = within(screen.getByRole("region", { name: "Escala institucional" }));
  await panel.findByText("Escala v1 · Vigente");
  expect(panel.getByText("admin")).toBeTruthy();
  expect(panel.getByText("Acuerdo anterior")).toBeTruthy();
  await user.click(panel.getByRole("button", { name: "Continuar borrador" }));
  const input = panel.getByLabelText("Mínimo de B");
  const save = panel.getByRole("button", { name: "Guardar escala en borrador" });
  const activate = panel.getByRole("button", { name: "Activar escala", exact: true });
  expect(input.value).toBe("9");
  await user.click(activate);
  const dialog = within(screen.getByRole("alertdialog", { name: "Activar escala institucional" }));
  await user.type(dialog.getByLabelText("Motivo de activación"), "Acuerdo nuevo");
  await user.click(dialog.getByRole("button", { name: "Confirmar activación de escala" }));
  await dialog.findByText("Otra sesión actualizó la escala.");
  await user.click(dialog.getByRole("button", { name: "Cancelar" }));
  fireEvent.change(input, { target: { value: "8" } });
  expect(panel.getByText(/Hay rangos superpuestos/)).toBeTruthy();
  expect(save.disabled).toBe(true);
  expect(activate.disabled).toBe(true);
  expect(calls.filter(call => call.method === "POST")).toHaveLength(1);
});

test("copia una escala histórica y confirma antes de reemplazar el borrador", async () => {
  const historica = { id: 1, numeroVersion: 1, version: 1, estado: "HISTORICA", rangos: [{ nivel: "C", minimo: 0, maximo: 7 }] };
  const vigente = { ...historica, id: 2, numeroVersion: 2, estado: "VIGENTE" };
  const guardada = { ...historica, id: 3, numeroVersion: 3, version: 5, estado: "BORRADOR", rangos: [{ nivel: "C", minimo: 0, maximo: 8 }] };
  const calls = fakeBackend({ "GET /api/configuracion-academica/anios": page([]), "GET /api/escala-institucional": [guardada, vigente, historica],
    "PUT /api/escala-institucional/borrador": ({ body }) => ({ ...guardada, version: 6, rangos: body.rangos }) });
  const user = userEvent.setup();
  render(<GradingRulesPanel canWrite />);
  await user.click(await screen.findByText("Escala v1 · Histórica", { selector: "summary" }));
  await user.click(screen.getByRole("button", { name: "Usar como base" }));
  const confirm = within(screen.getByRole("alertdialog", { name: "¿Reemplazar los rangos del borrador?" }));
  expect(calls.every(call => call.method === "GET")).toBe(true);
  await user.click(confirm.getByRole("button", { name: "Usar como base" }));
  expect(screen.getByLabelText("Máximo de C").value).toBe("7");
  await user.click(screen.getByRole("button", { name: "Guardar escala en borrador" }));
  await screen.findByText("Borrador de escala v3 guardado.");
  expect(calls.find(call => call.method === "PUT").body).toEqual({ version: 5, rangos: historica.rangos });
  expect(screen.getByText("Escala v2 · Vigente")).toBeTruthy();
});

test("Cerrar el editor de escala protege los cambios sin guardar", async () => {
  const calls = fakeBackend({ "GET /api/configuracion-academica/anios": page([]), "GET /api/escala-institucional": [] });
  const user = userEvent.setup();
  render(<GradingRulesPanel canWrite />);
  await user.click(await screen.findByRole("button", { name: "Configurar escala" }));
  const initial = screen.getByLabelText("Mínimo de C");
  await user.type(initial, "0");
  const close = screen.getByRole("button", { name: "Cerrar editor" });
  await user.click(close);
  const modal = within(screen.getByRole("alertdialog", { name: "¿Descartar cambios de la escala?" }));
  await user.click(modal.getByRole("button", { name: "Seguir editando" }));
  expect(initial.value).toBe("0");
  await user.click(close);
  await user.click(screen.getByRole("button", { name: "Descartar cambios" }));
  expect(screen.queryByLabelText("Mínimo de C")).toBeNull();
  expect(calls.every(call => call.method === "GET")).toBe(true);
});

test("Configura una escala para todo el colegio, exige motivo y muestra su versión", async () => {
  const ranges = [
    { nivel: "C", minimo: 0, maximo: 8 }, { nivel: "B", minimo: 9, maximo: 12 },
    { nivel: "A", minimo: 13, maximo: 16 }, { nivel: "AD", minimo: 17, maximo: 20 },
  ];
  const scale = { id: 10, numeroVersion: 1, version: 0, estado: "BORRADOR", rangos: ranges };
  const calls = fakeBackend({
    "GET /api/configuracion-academica/anios": page([]),
    "GET /api/escala-institucional": [],
    "PUT /api/escala-institucional/borrador": scale,
    "POST /api/escala-institucional/10/activar": { ...scale, estado: "VIGENTE", motivoAplicacion: "Acuerdo escolar" },
  });
  const user = userEvent.setup();
  render(<GradingRulesPanel canWrite />);
  const panel = within(screen.getByRole("region", { name: "Escala institucional" }));
  await panel.findByText("Todavía no hay una escala vigente.");
  await user.click(panel.getByRole("button", { name: "Configurar escala" }));
  for (const { nivel, minimo, maximo } of ranges) {
    fireEvent.change(panel.getByLabelText(`Mínimo de ${nivel}`), { target: { value: String(minimo) } });
    fireEvent.change(panel.getByLabelText(`Máximo de ${nivel}`), { target: { value: String(maximo) } });
  }
  await user.click(panel.getByRole("button", { name: "Guardar escala en borrador" }));
  expect(await panel.findByText("Borrador de escala v1 guardado.")).toBeTruthy();
  await user.click(panel.getByRole("button", { name: "Activar escala", exact: true }));
  const modal = within(screen.getByRole("alertdialog", { name: "Activar escala institucional" }));
  const activate = modal.getByRole("button", { name: "Confirmar activación de escala" });
  expect(activate.disabled).toBe(true);
  await user.type(modal.getByLabelText("Motivo de activación"), "Acuerdo escolar");
  await user.click(activate);
  expect(await panel.findByText("Escala v1 · Vigente")).toBeTruthy();
  expect(panel.queryByRole("form", { name: "Borrador de escala" })).toBeNull();
  await user.click(panel.getByRole("button", { name: "Nueva versión" }));
  expect(panel.getByRole("form", { name: "Borrador de escala" })).toBeTruthy();
  expect(calls.find(call => call.method === "PUT").body).toEqual({ rangos: ranges });
  expect(calls.find(call => call.method === "POST").body).toEqual({ version: 0, motivo: "Acuerdo escolar" });
});

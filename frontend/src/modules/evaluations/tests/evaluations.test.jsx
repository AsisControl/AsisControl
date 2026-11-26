import { beforeEach, expect, test, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { currentUrl, setUrl } from "../../../../tests/support/navigation.js";
import { Evaluations } from "@/modules/evaluations";
import { EvaluationFormDialog } from "../evaluation-form-dialog";
import { ModuleOutlet } from "@/core/navigation/module-outlet";
import { SessionUserProvider } from "@/core/layout/session-user";
import { canAccess } from "@/core/navigation/access-policy";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const competencies = [
  { id: 1, codigo: "C1", nombre: "Resuelve problemas" },
  { id: 2, codigo: "C2", nombre: "Razona en geometría" },
];
const evaluation = (overrides = {}) => ({
  id: 10, idAsignacionCurso: 5, titulo: "Examen parcial", descripcion: null,
  tipoEvaluacion: "EXAMEN", periodoEvaluacion: "BIMESTRAL", estado: "BORRADOR",
  fechaEvaluacion: "2026-09-22", ponderacionPorcentaje: 100, competencias: competencies,
  idCurso: 100, curso: "Matemática", idSeccion: 8, seccion: "3° A", anioAcademico: 2026,
  ...overrides,
});
const assignment = { id: 5, cursoId: 100, curso: "Matemática", seccionId: 8, seccion: "A", grado: 3, anioAcademico: 2026 };
const note = (idCalificacion, idCompetencia, nivelLogro, overrides = {}) => ({
  idCalificacion, idCompetencia, nivelLogro, observacion: null, version: 2, corregida: false, ...overrides,
});
const student = (idAlumno, nombreCompleto, notas = []) => ({ idAlumno, codigoAlumno: `E-${idAlumno}`, nombreCompleto, notas });
const sheet = (overrides = {}, alumnos) => ({
  evaluacion: evaluation(overrides),
  alumnos: alumnos ?? [student(1, "Alvarado Medina, Diego"), student(2, "Bravo Castillo, Ana")],
});
function backend(routes = {}) {
  return fakeBackend({
    "GET /api/asignaciones-cursos/mias": page([assignment]),
    "GET /api/evaluaciones/anio-vigente": { anioActivo: assignment.anioAcademico },
    "GET /api/evaluaciones": page([evaluation()]),
    "GET /api/evaluaciones/competencias": competencies,
    "GET /api/reglas-calificacion/vigente": reply(204),
    "GET /api/evaluaciones/periodos-informe": [{ id: 3, nombre: "Bimestre", fechaInicio: "2026-09-01", fechaFin: "2026-10-31" }],
    "GET /api/evaluaciones/:id": evaluation(),
    "GET /api/evaluaciones/:id/planilla": sheet(),
    "GET /api/calificaciones/consolidados": [],
    ...routes,
  });
}
beforeEach(() => {
  signIn({ role: "DOCENTE", permissions: ["CALIFICACIONES_LEER", "CALIFICACIONES_ESCRIBIR"] });
  setUrl("/evaluaciones");
});
const gradingRule = { componentes: [
  { tipoEvaluacion: "EXAMEN", nombre: "Exámenes", peso: 62.5 },
  { tipoEvaluacion: "PROYECTO", nombre: "Proyectos", peso: 37.5 },
] };

test("El alta consulta la regla y envía el componente elegido sin copiar su peso", async () => {
  const calls = backend({
    "GET /api/reglas-calificacion/vigente": gradingRule,
    "POST /api/evaluaciones": ({ body }) => evaluation(body),
  });
  const user = userEvent.setup();
  const onSaved = vi.fn();
  render(<EvaluationFormDialog assignments={[assignment]} onClose={vi.fn()} onSaved={onSaved} />);
  await user.type(screen.getByLabelText(/Nombre de la evaluación/), "Proyecto del curso");
  const { fireEvent } = await import("@testing-library/react");
  fireEvent.change(screen.getByLabelText(/Fecha de evaluación/), { target: { value: "2026-10-01" } });
  await user.selectOptions(screen.getByLabelText(/^Curso/), "100");
  await user.selectOptions(screen.getByLabelText(/^Sección/), "8");
  const component = await screen.findByRole("combobox", { name: /Componente/ });
  expect(calls.find(call => call.path === "/api/reglas-calificacion/vigente").query)
    .toEqual({ idAsignacionCurso: "5" });
  expect([...component.options].map(option => option.value)).toEqual(["", "EXAMEN", "PROYECTO"]);
  await user.click(await screen.findByLabelText("Resuelve problemas"));
  await user.click(screen.getByRole("button", { name: "Guardar borrador" }));
  expect(screen.getByText("Selecciona un componente.")).toBeTruthy();
  expect(calls.some(call => call.method === "POST")).toBe(false);
  const weight = screen.getByRole("textbox", { name: /Peso del componente/ });
  expect(weight.readOnly).toBe(true);
  await user.selectOptions(component, "EXAMEN");
  expect(weight.value).toMatch(/62[.,]5\s*%/);
  await user.selectOptions(component, "PROYECTO");
  expect(weight.value).toMatch(/37[.,]5\s*%/);
  await user.click(screen.getByRole("button", { name: "Guardar borrador" }));
  await waitFor(() => expect(onSaved).toHaveBeenCalledWith(expect.objectContaining({ tipoEvaluacion: "PROYECTO" }), false));
  expect(calls.find(call => call.method === "POST").body).not.toHaveProperty("ponderacionPorcentaje");
});

test.each([
  ["EXAMEN", gradingRule, "PROYECTO", "PROYECTO"],
  ["PRACTICA_CALIFICADA", gradingRule, null, "PRACTICA_CALIFICADA"],
  ["EXAMEN", reply(204), null, "EXAMEN"],
])("Editar %s conserva la ponderación histórica y respeta la regla aplicable", async (tipo, rule, nextType, expected) => {
  const calls = backend({
    "GET /api/reglas-calificacion/vigente": rule,
    "PUT /api/evaluaciones/:id": ({ body }) => evaluation(body),
  });
  const user = userEvent.setup();
  const onSaved = vi.fn();
  render(<EvaluationFormDialog evaluation={evaluation({ tipoEvaluacion: tipo, ponderacionPorcentaje: 75 })}
    assignments={[assignment]} onClose={vi.fn()} onSaved={onSaved} />);
  if (rule === gradingRule) {
    const component = await screen.findByRole("combobox", { name: /Componente/ });
    expect(component.value).toBe(tipo);
    if (nextType) await user.selectOptions(component, nextType);
    else expect(screen.getByRole("option", { name: /Fuera de la regla vigente/ })).toBeTruthy();
  } else {
    await screen.findByText("Esta asignación no tiene una regla de calificación vigente.");
  }
  await screen.findByLabelText("Resuelve problemas");
  await user.click(screen.getByRole("button", { name: "Guardar y calificar" }));
  await waitFor(() => expect(onSaved).toHaveBeenCalledWith(expect.objectContaining({ tipoEvaluacion: expected }), true));
  expect(calls.find(call => call.method === "PUT").body).toMatchObject({
    tipoEvaluacion: expected,
  });
  expect(calls.find(call => call.method === "PUT").body).not.toHaveProperty("ponderacionPorcentaje");
});

test("Un error al cargar la regla bloquea el guardado y permite reintentar", async () => {
  const calls = backend({
    "GET /api/reglas-calificacion/vigente": () =>
      calls.filter(call => call.path === "/api/reglas-calificacion/vigente").length === 1
        ? reply(500, { message: "Error temporal" }) : gradingRule,
  });
  const user = userEvent.setup();
  render(<EvaluationFormDialog evaluation={evaluation()} assignments={[assignment]} onClose={vi.fn()} onSaved={vi.fn()} />);
  await screen.findByText(/No se pudo consultar la regla/);
  await screen.findByLabelText("Resuelve problemas");
  await user.click(screen.getByRole("button", { name: "Guardar y calificar" }));
  expect(calls.some(call => call.method === "PUT")).toBe(false);
  await user.click(screen.getByRole("button", { name: "Reintentar consulta" }));
  expect(await screen.findByRole("option", { name: "Exámenes" })).toBeTruthy();
});

const openSheet = (props = {}) =>
  render(<Evaluations tab="registro-de-calificaciones" evaluationId="10" canWrite {...props} />);
const level = (value, name, competency) => `${value}: ${name}, ${competency}`;

test("El listado identifica el peso del componente y la planilla explica el aporte por competencia", async () => {
  const weighted = { estado: "PUBLICADA", reglaVigente: true, componente: "Exámenes", pesoComponente: 60 };
  backend({
    "GET /api/evaluaciones": page([evaluation(weighted)]),
    "GET /api/evaluaciones/:id": evaluation(weighted),
    "GET /api/evaluaciones/:id/planilla": sheet(weighted, [
      student(1, "Alvarado Medina, Diego", [note(50, 1, "A", { pesoEfectivo: 30 })]),
    ]),
  });
  const list = render(<Evaluations canWrite />);
  expect(await screen.findByText("Peso del componente: 60 %")).toBeTruthy();
  expect(screen.queryByText(/Peso efectivo:/)).toBeNull();
  list.unmount();
  openSheet();
  expect(await screen.findByText("Peso efectivo: 30 %")).toBeTruthy();
  expect(screen.queryByLabelText("Peso efectivo pendiente")).toBeNull();
});

test("Lista las evaluaciones con su curso, sección, fecha y competencias", async () => {
  backend();
  render(<Evaluations canWrite />);
  const row = (await screen.findByText("Examen parcial")).closest("tr");
  expect(within(row).getByText("Matemática")).toBeTruthy();
  expect(within(row).getByText("3° A")).toBeTruthy();
  expect(within(row).getByText("Bimestral")).toBeTruthy();
  expect(within(row).getByText("22/09/2026")).toBeTruthy();
  expect(within(row).getByText("competencias")).toBeTruthy();
  expect(within(row).getByText("Borrador")).toBeTruthy();
  expect(screen.getByText("1 evaluación")).toBeTruthy();
});

test.each([
  ["BORRADOR", "Publicar"],
  ["PUBLICADA", "Calificar"],
  ["CERRADA", "Ver"],
])("En %s la acción principal es %s", async (estado, label) => {
  backend({ "GET /api/evaluaciones": page([evaluation({ estado })]) });
  render(<Evaluations canWrite />);
  const row = (await screen.findByText("Examen parcial")).closest("tr");
  expect(within(row).getByRole("button", { name: label })).toBeTruthy();
});

test("Sin permiso de escritura solo se puede consultar", async () => {
  backend({ "GET /api/evaluaciones": page([evaluation({ estado: "PUBLICADA" })]) });
  render(<Evaluations canWrite={false} />);
  const row = (await screen.findByText("Examen parcial")).closest("tr");
  expect(within(row).getByRole("button", { name: "Ver" })).toBeTruthy();
  expect(screen.queryByText(/Nueva evaluación/)).toBeNull();
  expect(within(row).queryByLabelText(/acciones/i)).toBeNull();
});

test("Los filtros se envían al servidor", async () => {
  const calls = backend();
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  await screen.findByText("Examen parcial");
  await user.selectOptions(screen.getByLabelText("Período"), "ANUAL");
  await user.selectOptions(screen.getByLabelText("Curso"), "100");
  await user.selectOptions(screen.getByLabelText("Sección"), "8");
  await user.selectOptions(screen.getByLabelText("Estado"), "PUBLICADA");
  await user.type(screen.getByLabelText("Buscar"), "par");
  await waitFor(() =>
    expect(calls.filter((c) => c.path === "/api/evaluaciones").at(-1).query).toMatchObject({
      periodo: "ANUAL", idCurso: "100", idSeccion: "8", estado: "PUBLICADA", busqueda: "par",
    }),
  );
});

test("Publicar confirma y explica si la planilla está incompleta", async () => {
  const calls = backend({
    "PUT /api/evaluaciones/:id/estado": reply(422, { code: "EVALUACION_INCOMPLETA", message: "Faltan 4 niveles por registrar" }),
  });
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  await user.click(await screen.findByRole("button", { name: "Publicar" }));
  const dialog = screen.getByRole("dialog", { name: "Publicar evaluación" });
  await user.click(within(dialog).getByRole("button", { name: "Publicar evaluación" }));
  expect(await within(dialog).findByText(/Faltan 4 notas por registrar\. Completa la planilla/)).toBeTruthy();
  expect(calls.find((c) => c.method === "PUT").body).toEqual({ estado: "PUBLICADA" });
});

test("Nueva evaluación pide los datos, ofrece solo competencias del curso y va a calificar", async () => {
  const calls = backend({ "POST /api/evaluaciones": ({ body }) => evaluation({ ...body, id: 77 }) });
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  await user.click(await screen.findByRole("button", { name: "+ Nueva evaluación" }));
  const dialog = screen.getByRole("dialog", { name: "Nueva evaluación" });
  expect(within(dialog).getByText("Selecciona un curso y una sección para ver las competencias del plan.")).toBeTruthy();
  await user.click(within(dialog).getByRole("button", { name: "Crear evaluación" }));
  expect(within(dialog).getByText("Escribe el nombre de la evaluación.")).toBeTruthy();
  expect(within(dialog).getByText("Selecciona al menos una competencia.")).toBeTruthy();
  expect(calls.some((c) => c.method === "POST")).toBe(false);

  await user.type(within(dialog).getByLabelText(/Nombre de la evaluación/), "Práctica 3");
  fireEvent.change(within(dialog).getByLabelText(/Fecha de evaluación/), { target: { value: "2026-10-01" } });
  await user.selectOptions(within(dialog).getByLabelText(/^Curso/), "100");
  await user.selectOptions(within(dialog).getByLabelText(/^Sección/), "8");
  await user.click(await within(dialog).findByLabelText("Resuelve problemas"));
  expect(calls.find(c => c.path === "/api/evaluaciones/competencias").query).toEqual({ idAsignacionCurso: "5" });
  expect(within(dialog).queryByLabelText("Otro curso")).toBeNull();
  expect(within(dialog).queryByLabelText("Retirada")).toBeNull();
  await user.click(within(dialog).getByRole("button", { name: "Crear evaluación" }));
  await waitFor(() => expect(calls.some((c) => c.method === "POST")).toBe(true));
  expect(calls.find((c) => c.method === "POST").body).toMatchObject({
    idAsignacionCurso: 5, titulo: "Práctica 3", periodoEvaluacion: "BIMESTRAL",
    fechaEvaluacion: "2026-10-01", idsCompetencia: [1],
    tipoEvaluacion: "PRACTICA_CALIFICADA",
  });
  await waitFor(() => expect(currentUrl()).toBe("/evaluaciones/registro-de-calificaciones/77"));
});

test("Bloquea fechas inexistentes, años excesivos y fechas fuera del período por ambos modos de guardado", async () => {
  const calls = backend();
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  await user.click(await screen.findByRole("button", { name: "+ Nueva evaluación" }));
  const dialog = within(screen.getByRole("dialog", { name: "Nueva evaluación" }));
  const date = dialog.getByLabelText(/Fecha de evaluación/);
  await user.type(dialog.getByLabelText(/Nombre de la evaluación/), "Práctica");
  await user.selectOptions(dialog.getByLabelText(/^Curso/), "100");
  await user.selectOptions(dialog.getByLabelText(/^Sección/), "8");
  await user.click(await dialog.findByLabelText("Resuelve problemas"));
  for (const [value, expected, command] of [
    ["2026-02-30", "Indica una fecha de evaluación válida.", "Crear evaluación"],
    ["202020-04-01", "Ingresa un año entre 1900 y 2100.", "Guardar borrador"],
    ["2026-11-01", "La fecha debe estar dentro de un período académico activo de la asignación.", "Crear evaluación"],
  ]) {
    fireEvent.change(date, { target: { value } });
    await user.click(dialog.getByRole("button", { name: command }));
    expect(await dialog.findByText(expected)).toBeTruthy();
  }
  expect(calls.some((call) => call.method === "POST")).toBe(false);
});

test("Busca por nombre, conserva la selección múltiple y permite seleccionar todas y limpiar", async () => {
  const names = ["Resuelve problemas", "Razona en geometría", ...Array.from({ length: 13 }, (_, index) => `Competencia ${index + 3}`)];
  backend({ "GET /api/evaluaciones/competencias": names.map((nombre, index) => ({
    id: index + 1, codigo: `C${index + 1}`, nombre,
  })) });
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  await user.click(await screen.findByRole("button", { name: "+ Nueva evaluación" }));
  const dialog = within(screen.getByRole("dialog", { name: "Nueva evaluación" }));
  await user.selectOptions(dialog.getByLabelText(/^Curso/), "100");
  await user.selectOptions(dialog.getByLabelText(/^Sección/), "8");
  const search = await dialog.findByRole("searchbox", { name: "Buscar competencias" });
  const list = within(dialog.getByRole("group", { name: "Lista de competencias" }));
  expect(list.getAllByRole("checkbox")).toHaveLength(15);
  await user.click(dialog.getByRole("checkbox", { name: "Resuelve problemas" }));
  await user.type(search, "GEOMETRIA");
  expect(dialog.queryByRole("checkbox", { name: "Resuelve problemas" })).toBeNull();
  await user.click(dialog.getByRole("checkbox", { name: "Razona en geometría" }));
  expect(dialog.getByText("2 de 15 competencias seleccionadas")).toBeTruthy();
  await user.clear(search);
  expect(list.getByRole("checkbox", { name: "Resuelve problemas" }).checked).toBe(true);
  expect(list.getByRole("checkbox", { name: "Razona en geometría" }).checked).toBe(true);
  await user.type(search, "GEOMETRIA");
  await user.click(dialog.getByRole("button", { name: "Limpiar selección" }));
  expect(dialog.getByText("0 de 15 competencias seleccionadas")).toBeTruthy();
  await user.click(dialog.getByRole("button", { name: "Seleccionar todas" }));
  expect(dialog.getByText("15 de 15 competencias seleccionadas")).toBeTruthy();
  await user.clear(search);
  expect(dialog.getAllByRole("checkbox").every((checkbox) => checkbox.checked)).toBe(true);
  await user.click(dialog.getByRole("button", { name: "Limpiar selección" }));
  expect(dialog.getAllByRole("checkbox").every((checkbox) => !checkbox.checked)).toBe(true);
});

test("Guardar borrador no abre la planilla y refresca el listado", async () => {
  const calls = backend({ "POST /api/evaluaciones": ({ body }) => evaluation({ ...body, id: 77 }) });
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  await user.click(await screen.findByRole("button", { name: "+ Nueva evaluación" }));
  const dialog = screen.getByRole("dialog", { name: "Nueva evaluación" });
  await user.type(within(dialog).getByLabelText(/Nombre de la evaluación/), "Práctica 3");
  fireEvent.change(within(dialog).getByLabelText(/Fecha de evaluación/), { target: { value: "2026-10-01" } });
  await user.selectOptions(within(dialog).getByLabelText(/^Curso/), "100");
  await user.selectOptions(within(dialog).getByLabelText(/^Sección/), "8");
  await user.click(await within(dialog).findByLabelText("Razona en geometría"));
  await user.click(within(dialog).getByRole("button", { name: "Guardar borrador" }));
  expect(await screen.findByText("Borrador guardado.")).toBeTruthy();
  expect(calls.filter((c) => c.path === "/api/evaluaciones" && c.method === "GET").length).toBeGreaterThan(1);
});

test("Sin curso elegido el registro orienta al docente", async () => {
  backend();
  render(<Evaluations tab="registro-de-calificaciones" canWrite />);
  expect(await screen.findByText("Selecciona período, curso y sección para ver las evaluaciones")).toBeTruthy();
  expect(screen.getByLabelText("Sección").disabled).toBe(true);
  expect(screen.getByLabelText("Competencia").disabled).toBe(true);
});

test("preselecciona el año activo y consulta cada año por su asignación", async () => {
  const user = userEvent.setup();
  const historical = evaluation({ id: 20, idAsignacionCurso: 6, titulo: "Examen histórico", anioAcademico: 2025 });
  const calls = backend({
    "GET /api/asignaciones-cursos/mias": page([assignment, { ...assignment, id: 6, anioAcademico: 2025 }]),
    "GET /api/evaluaciones": ({ query }) =>
      page(query.idAsignacionCurso === "6" ? [historical] : [evaluation()]),
    "GET /api/evaluaciones/:id/planilla": ({ path }) =>
      path.includes("/20/") ? { evaluacion: historical, alumnos: [student(3, "Estudiante anterior")] } : sheet(),
  });
  render(<Evaluations tab="registro-de-calificaciones" canWrite />);
  await screen.findByRole("option", { name: "2026 · Vigente" });
  expect(screen.getByLabelText("Año académico").value).toBe("2026");
  await user.selectOptions(screen.getByLabelText("Curso"), "100");
  await user.selectOptions(screen.getByLabelText("Sección"), "8");
  await screen.findByRole("table", { name: "Planilla de Examen parcial" });
  await user.selectOptions(screen.getByLabelText("Año académico"), "2025");
  expect(screen.queryByRole("table", { name: "Planilla de Examen parcial" })).toBeNull();
  expect(screen.getByLabelText("Curso").value).toBe("");
  await user.selectOptions(screen.getByLabelText("Curso"), "100");
  await user.selectOptions(screen.getByLabelText("Sección"), "8");
  await screen.findByText("Estudiante anterior");
  expect(calls.filter(({ path }) => path === "/api/evaluaciones").map(({ query }) => query.idAsignacionCurso))
    .toEqual(["5", "6"]);
});

test("el enlace a una evaluación anterior conserva su año aunque haya un año vigente distinto", async () => {
  backend({ "GET /api/evaluaciones/anio-vigente": { anioActivo: 2027 } });
  openSheet();
  await screen.findByRole("table", { name: "Planilla de Examen parcial" });
  expect(screen.getByLabelText("Año académico").value).toBe("2026");
});

test("US-18: en borrador registra niveles y avisa cuántos faltan para publicar", async () => {
  const calls = backend({ "PUT /api/calificaciones/lote": [] });
  const user = userEvent.setup();
  openSheet();
  await screen.findByRole("table", { name: "Planilla de Examen parcial" });
  expect(screen.getByText("Faltan 4 niveles para publicar.")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Guardar evaluación" }).disabled).toBe(true);
  expect(screen.queryByLabelText(/Motivo de la corrección/)).toBeNull();

  const name = "Alvarado Medina, Diego";
  const button = screen.getByRole("button", { name: level("AD", name, "Resuelve problemas") });
  await user.click(button);
  expect(button.getAttribute("aria-pressed")).toBe("true");
  expect(screen.getByText("Faltan 3 niveles para publicar.")).toBeTruthy();
  await user.click(button);
  expect(button.getAttribute("aria-pressed")).toBe("false");
  await user.click(button);
  await user.click(screen.getByRole("button", { name: level("B", "Bravo Castillo, Ana", "Razona en geometría") }));
  await user.click(screen.getByRole("button", { name: "Guardar evaluación" }));
  await screen.findByText(/Calificaciones guardadas/);
  expect(calls.find((c) => c.method === "PUT").body).toEqual({
    calificaciones: [
      { idEvaluacion: 10, idAlumno: 1, idCompetencia: 1, nivelLogro: "AD", observacion: null },
      { idEvaluacion: 10, idAlumno: 2, idCompetencia: 2, nivelLogro: "B", observacion: null },
    ],
  });
});

test("Carga, edita, conserva al cambiar el nivel y borra explícitamente la observación de una nota", async () => {
  const name = "Alvarado Medina, Diego";
  const competency = "Resuelve problemas";
  const field = `Observación: ${name}, ${competency}`;
  let saved = note(50, 1, "A", { observacion: "Explica su procedimiento." });
  const calls = backend({
    "GET /api/evaluaciones/:id/planilla": () => sheet({}, [student(1, name, [saved])]),
    "PUT /api/calificaciones/lote": ({ body }) => {
      const { nivelLogro, observacion } = body.calificaciones[0];
      saved = { ...saved, nivelLogro, observacion };
      return [];
    },
  });
  const user = userEvent.setup();
  openSheet();
  expect((await screen.findByRole("textbox", { name: field })).value).toBe("Explica su procedimiento.");

  await user.clear(screen.getByRole("textbox", { name: field }));
  await user.type(screen.getByRole("textbox", { name: field }), "Justifica sus respuestas.");
  await user.click(screen.getByRole("button", { name: "Guardar evaluación" }));
  expect(await screen.findByText(/Calificaciones guardadas/)).toBeTruthy();
  expect(screen.getByRole("textbox", { name: field }).value).toBe("Justifica sus respuestas.");

  await user.click(screen.getByRole("button", { name: level("AD", name, competency) }));
  await user.click(screen.getByRole("button", { name: "Guardar evaluación" }));
  expect(await screen.findByText(/Calificaciones guardadas/)).toBeTruthy();
  expect(screen.getByRole("textbox", { name: field }).value).toBe("Justifica sus respuestas.");

  await user.clear(screen.getByRole("textbox", { name: field }));
  expect(calls.filter((call) => call.method === "PUT")).toHaveLength(2);
  await user.click(screen.getByRole("button", { name: "Guardar evaluación" }));
  expect(await screen.findByText(/Calificaciones guardadas/)).toBeTruthy();
  expect(screen.getByRole("textbox", { name: field }).value).toBe("");

  expect(calls.filter((call) => call.method === "PUT").map((call) => call.body.calificaciones)).toEqual([
    [{ idEvaluacion: 10, idAlumno: 1, idCompetencia: 1, nivelLogro: "A", observacion: "Justifica sus respuestas." }],
    [{ idEvaluacion: 10, idAlumno: 1, idCompetencia: 1, nivelLogro: "AD", observacion: "Justifica sus respuestas." }],
    [{ idEvaluacion: 10, idAlumno: 1, idCompetencia: 1, nivelLogro: "AD", observacion: null }],
  ]);
});

test("Las columnas conservan su orden aunque el servidor entregue las competencias en otro", async () => {
  backend({ "GET /api/evaluaciones/:id/planilla": sheet({ competencias: [...competencies].reverse() }) });
  openSheet();
  const table = await screen.findByRole("table", { name: "Planilla de Examen parcial" });
  expect(within(table).getAllByRole("columnheader").map((header) => header.textContent)).toEqual([
    "Estudiante",
    "Resuelve problemas",
    "Razona en geometría",
  ]);
});

test("El filtro de competencia reduce las columnas", async () => {
  backend();
  const user = userEvent.setup();
  openSheet();
  const table = await screen.findByRole("table", { name: "Planilla de Examen parcial" });
  await user.selectOptions(await screen.findByLabelText("Competencia"), "2");
  expect(within(table).queryByRole("columnheader", { name: "Resuelve problemas" })).toBeNull();
  expect(within(table).getByRole("columnheader", { name: "Razona en geometría" })).toBeTruthy();
});

const published = () =>
  sheet({ estado: "PUBLICADA" }, [
    student(1, "Alvarado Medina, Diego", [note(50, 1, "C"), note(51, 2, "A")]),
    student(2, "Bravo Castillo, Ana", [note(52, 1, "B"), note(53, 2, "B")]),
  ]);

test("Registra cero y notas decimales por competencia, con observaciones", async () => {
  const calls = backend({
    "GET /api/evaluaciones/:id/planilla": { ...sheet(), numeroVersionEscala: 1 },
    "PUT /api/calificaciones/lote": [],
  });
  const user = userEvent.setup();
  openSheet();
  const name = "Alvarado Medina, Diego";
  const primera = await screen.findByRole("spinbutton", { name: `Nota: ${name}, Resuelve problemas` });
  const segunda = screen.getByRole("spinbutton", { name: `Nota: ${name}, Razona en geometría` });
  const save = screen.getByRole("button", { name: "Guardar evaluación" });
  await user.type(primera, "21");
  expect(save.disabled).toBe(true);
  await user.clear(primera);
  await user.type(primera, "0");
  await user.type(segunda, "17.5");
  await user.type(screen.getByLabelText(`Observación: ${name}, Resuelve problemas`), "Actividad sin resolver");
  await user.click(save);
  await waitFor(() => expect(calls.find(call => call.path === "/api/calificaciones/lote")?.body.calificaciones)
    .toEqual(expect.arrayContaining([
      expect.objectContaining({ idCompetencia: 1, notaNumerica: "0", observacion: "Actividad sin resolver" }),
      expect.objectContaining({ idCompetencia: 2, notaNumerica: "17.5" }),
    ])));
});

test("US-50: publicada entra en modo corrección y exige motivo", async () => {
  const calls = backend({
    "GET /api/evaluaciones/:id": evaluation({ estado: "PUBLICADA" }),
    "GET /api/evaluaciones/:id/planilla": published(),
    "POST /api/calificaciones/correcciones": [],
  });
  const user = userEvent.setup();
  openSheet();
  expect(await screen.findByText(/Estás modificando una evaluación publicada/)).toBeTruthy();
  const save = screen.getByRole("button", { name: "Guardar cambios" });
  expect(save.disabled).toBe(true);
  expect(screen.getByText("0 cambios pendientes")).toBeTruthy();

  const name = "Alvarado Medina, Diego";
  await user.click(screen.getByRole("button", { name: level("A", name, "Resuelve problemas") }));
  const changes = screen.getByRole("region", { name: "Cambios pendientes" });
  expect(within(changes).getByText("1 cambio pendiente")).toBeTruthy();
  expect(within(changes).getByText("Resuelve problemas: C → A")).toBeTruthy();

  await user.click(save);
  expect(screen.getByText("Indica el motivo de la corrección.")).toBeTruthy();
  expect(calls.some((c) => c.method === "POST")).toBe(false);

  await user.type(screen.getByLabelText(/Motivo de la corrección/), " Error de digitación ");
  await user.click(save);
  expect(await screen.findByText("1 corrección guardada.")).toBeTruthy();
  expect(calls.find((c) => c.method === "POST").body).toEqual({
    motivo: "Error de digitación",
    correcciones: [{ idCalificacion: 50, nivelLogro: "A", version: 2 }],
  });
  expect(calls.filter((c) => c.path === "/api/evaluaciones/10/planilla")).toHaveLength(2);
});

test("US-50: una corrección numérica envía la nota original editada y su versión", async () => {
  const calls = backend({
    "GET /api/evaluaciones/:id/planilla": {
      ...sheet({ estado: "PUBLICADA" }, [student(1, "Diego", [note(50, 1, "A", { notaNumerica: 15.25 })])]),
      numeroVersionEscala: 2,
    },
    "POST /api/calificaciones/correcciones": [],
  });
  const user = userEvent.setup();
  openSheet();
  const input = await screen.findByRole("spinbutton", { name: "Nota: Diego, Resuelve problemas" });
  expect(input.value).toBe("15.25");
  await user.clear(input);
  await user.type(input, "15.5");
  expect(screen.getByText("Resuelve problemas: 15.25 → 15.5")).toBeTruthy();
  await user.type(screen.getByLabelText(/Motivo de la corrección/), "Revisión de la nota");
  await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
  await screen.findByText("1 corrección guardada.");
  expect(calls.find(call => call.method === "POST").body).toEqual({
    motivo: "Revisión de la nota", correcciones: [{ idCalificacion: 50, notaNumerica: "15.5", version: 2 }],
  });
});

test("US-50: volver al nivel original quita el cambio pendiente", async () => {
  backend({
    "GET /api/evaluaciones/:id": evaluation({ estado: "PUBLICADA" }),
    "GET /api/evaluaciones/:id/planilla": published(),
  });
  const user = userEvent.setup();
  openSheet();
  const name = "Alvarado Medina, Diego";
  await user.click(await screen.findByRole("button", { name: level("B", name, "Resuelve problemas") }));
  await user.click(screen.getByRole("button", { name: level("C", name, "Resuelve problemas") }));
  expect(screen.getByText("0 cambios pendientes")).toBeTruthy();
});

test("US-50: un conflicto de versión avisa, descarta los cambios y recarga", async () => {
  const calls = backend({
    "GET /api/evaluaciones/:id": evaluation({ estado: "PUBLICADA" }),
    "GET /api/evaluaciones/:id/planilla": published(),
    "POST /api/calificaciones/correcciones": reply(409, { code: "VERSION_DESACTUALIZADA", message: "La calificacion fue modificada" }),
  });
  const user = userEvent.setup();
  openSheet();
  await user.click(await screen.findByRole("button", { name: level("A", "Alvarado Medina, Diego", "Resuelve problemas") }));
  await user.type(screen.getByLabelText(/Motivo de la corrección/), "Error");
  await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
  expect(await screen.findByText("La calificacion fue modificada")).toBeTruthy();
  expect(screen.getByText("0 cambios pendientes")).toBeTruthy();
  await waitFor(() => expect(calls.filter((c) => c.path === "/api/evaluaciones/10/planilla")).toHaveLength(2));
});

test("US-50: un estudiante sin nivel publicado no se puede corregir", async () => {
  backend({
    "GET /api/evaluaciones/:id": evaluation({ estado: "PUBLICADA" }),
    "GET /api/evaluaciones/:id/planilla": sheet({ estado: "PUBLICADA" }, [
      student(1, "Alvarado Medina, Diego", [note(50, 1, "C"), note(51, 2, "A")]),
      student(9, "Nuevo Ingreso, Luz"),
    ]),
  });
  openSheet();
  expect((await screen.findByRole("button", { name: level("A", "Nuevo Ingreso, Luz", "Resuelve problemas") })).disabled).toBe(true);
});

test.each([["CERRADA"], ["PUBLICADA"]])("Sin poder escribir, una evaluación %s es de consulta", async (estado) => {
  backend({
    "GET /api/evaluaciones/:id": evaluation({ estado }),
    "GET /api/evaluaciones/:id/planilla": sheet({ estado }, [student(1, "Alvarado Medina, Diego", [note(50, 1, "C")])]),
  });
  openSheet({ canWrite: estado === "CERRADA" });
  const button = await screen.findByRole("button", { name: level("C", "Alvarado Medina, Diego", "Resuelve problemas") });
  expect(button.disabled).toBe(true);
  expect(screen.queryByRole("button", { name: /Guardar/ })).toBeNull();
  expect(screen.queryByText(/Estás modificando/)).toBeNull();
});

test("US-50: el detalle del estudiante muestra su consolidado y la trazabilidad", async () => {
  const calls = backend({
    "GET /api/evaluaciones/:id": evaluation({ estado: "PUBLICADA" }),
    "GET /api/evaluaciones/:id/planilla": sheet({ estado: "PUBLICADA" }, [
      student(1, "Alvarado Medina, Diego", [note(50, 1, "A", { corregida: true }), note(51, 2, "B")]),
    ]),
    "GET /api/calificaciones/consolidados": [
      { idCompetencia: 1, nombreCompetencia: "Resuelve problemas", nivelConsolidado: "A", evaluacionesConsideradas: 1 },
    ],
    "GET /api/calificaciones/:id/correcciones": [
      { numeroVersion: 1, nivelAnterior: "C", nivelNuevo: "A", motivo: "Error de digitación", autor: "Patricia Morales", fecha: "2026-10-01T14:00:00Z" },
    ],
  });
  const user = userEvent.setup();
  openSheet();
  expect(await screen.findByText("Selecciona un estudiante para ver su detalle de progreso")).toBeTruthy();
  expect(screen.getByText("Corregida")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Alvarado Medina, Diego" }));
  const detail = await screen.findByRole("region", { name: "Detalle del estudiante" });
  expect(await within(detail).findByText("Resuelve problemas: C → A")).toBeTruthy();
  expect(within(detail).getByText("Motivo: Error de digitación")).toBeTruthy();
  expect(within(detail).getByText(/Patricia Morales/)).toBeTruthy();
  expect(calls.find((c) => c.path === "/api/calificaciones/consolidados").query).toEqual({ idAlumno: "1", idAsignacionCurso: "5" });
  expect(calls.some((c) => c.path === "/api/calificaciones/51/correcciones")).toBe(false);
});

test("Con varias evaluaciones permite elegir cuál calificar", async () => {
  backend({
    "GET /api/evaluaciones": page([evaluation(), evaluation({ id: 11, titulo: "Práctica 3" })]),
  });
  openSheet();
  const select = await screen.findByLabelText("Evaluación");
  expect([...select.options].map((o) => o.textContent)).toEqual(["Examen parcial", "Práctica 3"]);
});

test("El menú Evaluaciones del docente abre la pantalla y exige CALIFICACIONES_LEER", async () => {
  backend();
  const user = { id: "1", username: "docente", fullName: "Ana Pérez", role: "DOCENTE", permissions: ["CALIFICACIONES_LEER"] };
  expect(canAccess(user, "Evaluaciones")).toBe(true);
  expect(canAccess({ ...user, permissions: [] }, "Evaluaciones")).toBe(false);
  render(<SessionUserProvider value={user}><ModuleOutlet /></SessionUserProvider>);
  expect(await screen.findByText("Examen parcial")).toBeTruthy();
});

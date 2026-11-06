import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { Evaluations } from "@/modules/evaluations";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

// Ciclo de vida de una evaluación (US-18): niveles en borrador, publicación, evaluación
// publicada y evaluación cerrada. Complementa evaluations.test.jsx sin repetir sus casos.

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
const note = (idCalificacion, idCompetencia, nivelLogro) => ({
  idCalificacion, idCompetencia, nivelLogro, version: 2, corregida: false,
});
const student = (idAlumno, nombreCompleto, notas = []) => ({ idAlumno, codigoAlumno: `E-${idAlumno}`, nombreCompleto, notas });
const DIEGO = "Alvarado Medina, Diego";
const ANA = "Bravo Castillo, Ana";
const sheet = (overrides = {}, alumnos) => ({
  evaluacion: evaluation(overrides),
  alumnos: alumnos ?? [student(1, DIEGO), student(2, ANA)],
});
const withNotes = (estado) =>
  sheet({ estado }, [
    student(1, DIEGO, [note(50, 1, "C"), note(51, 2, "A")]),
    student(2, ANA, [note(52, 1, "B"), note(53, 2, "B")]),
  ]);
function backend(routes = {}) {
  return fakeBackend({
    "GET /api/asignaciones-cursos/mias": page([assignment]),
    "GET /api/evaluaciones": page([evaluation()]),
    "GET /api/evaluaciones/:id": evaluation(),
    "GET /api/evaluaciones/:id/planilla": sheet(),
    "GET /api/cursos-competencias": page([]),
    "GET /api/calificaciones/consolidados": [],
    ...routes,
  });
}
/** Backend con estado: el PUT de estado cambia lo que devuelve el listado siguiente. */
function statefulBackend(initial) {
  let estado = initial;
  const calls = backend({
    "GET /api/evaluaciones": () => page([evaluation({ estado })]),
    "PUT /api/evaluaciones/:id/estado": ({ body }) => {
      estado = body.estado;
      return evaluation({ estado });
    },
    "DELETE /api/evaluaciones/:id": null,
  });
  return calls;
}
beforeEach(() => {
  signIn({ role: "DOCENTE", permissions: ["CALIFICACIONES_LEER", "CALIFICACIONES_ESCRIBIR"] });
  setUrl("/evaluaciones");
});
const openSheet = (props = {}) =>
  render(<Evaluations tab="registro-de-calificaciones" evaluationId="10" canWrite {...props} />);
const level = (value, name, competency) => `${value}: ${name}, ${competency}`;
const cell = (name, competency) => screen.getByRole("group", { name: `${name}, ${competency}` });
/** Primera fila de la tabla: con un diálogo abierto el título también aparece en su descripción. */
const rowNow = () => document.querySelector("tbody tr");
const rowOf = () => waitFor(() => { expect(rowNow()).toBeTruthy(); return rowNow(); });
async function openMenu(user) {
  const row = await rowOf();
  await user.click(within(row).getByRole("button", { name: "Más acciones para Examen parcial" }));
  return screen.getByRole("menu");
}
const menuLabels = (menu) => within(menu).getAllByRole("menuitem").map((item) => item.textContent);
const puts = (calls, path) => calls.filter((c) => c.method === "PUT" && c.path === path);

// --- Niveles en borrador (valor válido AD/A/B/C, se guarda en borrador) ---

test("1. Cada celda ofrece solo AD, A, B y C, sin entrada libre", async () => {
  backend();
  openSheet();
  const table = await screen.findByRole("table", { name: "Planilla de Examen parcial" });
  for (const name of [DIEGO, ANA])
    for (const competency of ["Resuelve problemas", "Razona en geometría"]) {
      const buttons = within(cell(name, competency)).getAllByRole("button");
      expect(buttons.map((button) => button.textContent)).toEqual(["AD", "A", "B", "C"]);
    }
  expect(within(table).queryByRole("textbox")).toBeNull();
  expect(within(table).queryByRole("combobox")).toBeNull();
});

test("2. Si falla el guardado del lote se ve el error y se conservan los niveles marcados", async () => {
  backend({
    "PUT /api/calificaciones/lote": reply(422, { code: "ALUMNO_NO_MATRICULADO_EN_SECCION", message: "El alumno no está matriculado en la sección" }),
  });
  const user = userEvent.setup();
  openSheet();
  const button = await screen.findByRole("button", { name: level("A", DIEGO, "Resuelve problemas") });
  await user.click(button);
  await user.click(screen.getByRole("button", { name: "Guardar evaluación" }));
  expect(await screen.findByText("El alumno no está matriculado en la sección")).toBeTruthy();
  expect(screen.queryByText(/Niveles guardados/)).toBeNull();
  expect(button.getAttribute("aria-pressed")).toBe("true");
  expect(screen.getByRole("button", { name: "Guardar evaluación" }).disabled).toBe(false);
});

test("3. Tras guardar en borrador recarga la planilla y avisa que se publica desde Mis evaluaciones", async () => {
  const calls = backend({ "PUT /api/calificaciones/lote": [] });
  const user = userEvent.setup();
  openSheet();
  await user.click(await screen.findByRole("button", { name: level("AD", DIEGO, "Resuelve problemas") }));
  expect(calls.filter((c) => c.path === "/api/evaluaciones/10/planilla")).toHaveLength(1);
  await user.click(screen.getByRole("button", { name: "Guardar evaluación" }));
  expect(
    await screen.findByText("Niveles guardados. Publica la evaluación desde Mis evaluaciones cuando esté completa."),
  ).toBeTruthy();
  await waitFor(() => expect(calls.filter((c) => c.path === "/api/evaluaciones/10/planilla")).toHaveLength(2));
});

test("4. En borrador el guardado usa el lote y nunca la corrección", async () => {
  const calls = backend({ "PUT /api/calificaciones/lote": [], "POST /api/calificaciones/correcciones": [] });
  const user = userEvent.setup();
  openSheet();
  await user.click(await screen.findByRole("button", { name: level("B", ANA, "Razona en geometría") }));
  await user.click(screen.getByRole("button", { name: "Guardar evaluación" }));
  await screen.findByText(/Niveles guardados/);
  expect(puts(calls, "/api/calificaciones/lote")).toHaveLength(1);
  expect(calls.some((c) => c.path === "/api/calificaciones/correcciones")).toBe(false);
});

test("5. La grilla solo muestra a los estudiantes de la planilla y avisa si no hay ninguno", async () => {
  backend({ "GET /api/evaluaciones/:id/planilla": sheet({}, [student(1, DIEGO)]) });
  const view = openSheet();
  const table = await screen.findByRole("table", { name: "Planilla de Examen parcial" });
  expect(within(table).getByRole("button", { name: DIEGO })).toBeTruthy();
  expect(within(table).queryByRole("button", { name: ANA })).toBeNull();
  view.unmount();

  backend({ "GET /api/evaluaciones/:id/planilla": sheet({}, []) });
  openSheet();
  expect(await screen.findByText("La sección no tiene estudiantes matriculados.")).toBeTruthy();
  expect(screen.queryByRole("table")).toBeNull();
});

// --- Publicación ---

test("6. Publicar una evaluación completa: confirma, envía PUBLICADA y refresca el listado", async () => {
  const calls = statefulBackend("BORRADOR");
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  const row = await rowOf();
  await user.click(within(row).getByRole("button", { name: "Publicar" }));
  const dialog = screen.getByRole("dialog", { name: "Publicar evaluación" });
  expect(puts(calls, "/api/evaluaciones/10/estado")).toHaveLength(0);
  await user.click(within(dialog).getByRole("button", { name: "Publicar evaluación" }));
  expect(await screen.findByText("Evaluación publicada.")).toBeTruthy();
  expect(puts(calls, "/api/evaluaciones/10/estado")[0].body).toEqual({ estado: "PUBLICADA" });
  expect(screen.queryByRole("dialog")).toBeNull();
  await waitFor(() => expect(within(rowNow()).getByText("Publicada")).toBeTruthy());
  expect(within(rowNow()).getByRole("button", { name: "Calificar" })).toBeTruthy();
});

test("7. Con la planilla incompleta la publicación se bloquea: el diálogo sigue y la evaluación sigue en borrador", async () => {
  const calls = backend({
    "PUT /api/evaluaciones/:id/estado": reply(422, { code: "EVALUACION_INCOMPLETA", message: "Faltan 3 niveles por registrar antes de publicar la evaluacion" }),
  });
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  const row = await rowOf();
  await user.click(within(row).getByRole("button", { name: "Publicar" }));
  const dialog = screen.getByRole("dialog", { name: "Publicar evaluación" });
  await user.click(within(dialog).getByRole("button", { name: "Publicar evaluación" }));
  expect(await within(dialog).findByText(/Faltan 3 niveles por registrar antes de publicar la evaluacion\. Completa la planilla con Calificar\./)).toBeTruthy();
  expect(screen.queryByText("Evaluación publicada.")).toBeNull();
  expect(screen.getByRole("dialog", { name: "Publicar evaluación" })).toBeTruthy();
  expect(calls.filter((c) => c.path === "/api/evaluaciones" && c.method === "GET")).toHaveLength(1);
  expect(within(rowNow()).getByText("Borrador")).toBeTruthy();
});

test("8. Sin estudiantes en la sección, el mensaje del servidor se muestra tal cual", async () => {
  backend({
    "PUT /api/evaluaciones/:id/estado": reply(422, { code: "EVALUACION_SIN_ESTUDIANTES", message: "La seccion no tiene estudiantes matriculados para evaluar" }),
  });
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  await user.click(within(await rowOf()).getByRole("button", { name: "Publicar" }));
  const dialog = screen.getByRole("dialog", { name: "Publicar evaluación" });
  await user.click(within(dialog).getByRole("button", { name: "Publicar evaluación" }));
  expect(await within(dialog).findByText("La seccion no tiene estudiantes matriculados para evaluar")).toBeTruthy();
  expect(within(dialog).queryByText(/Completa la planilla/)).toBeNull();
});

test("9. Cancelar el diálogo de publicar no cambia el estado", async () => {
  const calls = backend();
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  await user.click(within(await rowOf()).getByRole("button", { name: "Publicar" }));
  const dialog = screen.getByRole("dialog", { name: "Publicar evaluación" });
  await user.click(within(dialog).getByRole("button", { name: "Cancelar" }));
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(calls.some((c) => c.method === "PUT")).toBe(false);
});

// --- Evaluación publicada: no se edita directamente ---

test("10. En publicada el menú no ofrece Editar, Calificar ni Eliminar", async () => {
  backend({ "GET /api/evaluaciones": page([evaluation({ estado: "PUBLICADA" })]) });
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  const labels = menuLabels(await openMenu(user));
  expect(labels).toEqual(["Cerrar", "Anular"]);
});

test("11. En publicada los cambios van como corrección con motivo y nunca como lote", async () => {
  const calls = backend({
    "GET /api/evaluaciones/:id": evaluation({ estado: "PUBLICADA" }),
    "GET /api/evaluaciones/:id/planilla": withNotes("PUBLICADA"),
    "POST /api/calificaciones/correcciones": [],
    "PUT /api/calificaciones/lote": [],
  });
  const user = userEvent.setup();
  openSheet();
  await user.click(await screen.findByRole("button", { name: level("A", DIEGO, "Resuelve problemas") }));
  await user.type(screen.getByLabelText(/Motivo de la corrección/), "Error de digitación");
  await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
  await screen.findByText("1 corrección guardada.");
  expect(calls.filter((c) => c.method === "POST" && c.path === "/api/calificaciones/correcciones")).toHaveLength(1);
  expect(calls.some((c) => c.path === "/api/calificaciones/lote")).toBe(false);
});

test("12. Si el servidor rechaza el cambio directo en una publicada, el error se muestra", async () => {
  backend({
    "GET /api/evaluaciones/:id": evaluation({ estado: "PUBLICADA" }),
    "GET /api/evaluaciones/:id/planilla": withNotes("PUBLICADA"),
    "POST /api/calificaciones/correcciones": reply(409, { code: "CALIFICACION_PUBLICADA", message: "La evaluacion esta publicada: use la correccion con motivo" }),
  });
  const user = userEvent.setup();
  openSheet();
  await user.click(await screen.findByRole("button", { name: level("A", DIEGO, "Resuelve problemas") }));
  await user.type(screen.getByLabelText(/Motivo de la corrección/), "Error");
  await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
  expect(await screen.findByText("La evaluacion esta publicada: use la correccion con motivo")).toBeTruthy();
  expect(screen.queryByText(/corrección guardada/)).toBeNull();
});

// --- Evaluación cerrada ---

test("13. Cerrar desde el menú: confirma, envía CERRADA y la fila pasa a Cerrada", async () => {
  const calls = statefulBackend("PUBLICADA");
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  const menu = await openMenu(user);
  await user.click(within(menu).getByRole("menuitem", { name: "Cerrar" }));
  const dialog = screen.getByRole("dialog", { name: "Cerrar evaluación" });
  expect(puts(calls, "/api/evaluaciones/10/estado")).toHaveLength(0);
  await user.click(within(dialog).getByRole("button", { name: "Cerrar evaluación" }));
  expect(await screen.findByText("Evaluación cerrada.")).toBeTruthy();
  expect(puts(calls, "/api/evaluaciones/10/estado")[0].body).toEqual({ estado: "CERRADA" });
  await waitFor(() => expect(within(rowNow()).getByText("Cerrada")).toBeTruthy());
  expect(within(rowNow()).getByRole("button", { name: "Ver" })).toBeTruthy();
});

test("14. La grilla de una evaluación cerrada es de solo lectura", async () => {
  backend({
    "GET /api/evaluaciones/:id": evaluation({ estado: "CERRADA" }),
    "GET /api/evaluaciones/:id/planilla": withNotes("CERRADA"),
  });
  openSheet();
  expect(await screen.findByText("Evaluación cerrada: consulta de solo lectura.")).toBeTruthy();
  for (const name of [DIEGO, ANA])
    for (const competency of ["Resuelve problemas", "Razona en geometría"])
      for (const button of within(cell(name, competency)).getAllByRole("button"))
        expect(button.disabled).toBe(true);
  expect(screen.queryByRole("button", { name: /Guardar/ })).toBeNull();
  expect(screen.queryByLabelText(/Motivo de la corrección/)).toBeNull();
});

test("15. En cerrada la fila no ofrece menú de acciones", async () => {
  backend({ "GET /api/evaluaciones": page([evaluation({ estado: "CERRADA" })]) });
  render(<Evaluations canWrite />);
  const row = await rowOf();
  expect(within(row).queryByRole("button", { name: /Más acciones/ })).toBeNull();
  expect(within(row).getByRole("button", { name: "Ver" })).toBeTruthy();
});

// --- Extras: no salen de un criterio de la US-18 ---

test("16. (extra) Anular: confirma, envía ANULADA y avisa", async () => {
  const calls = statefulBackend("BORRADOR");
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  const menu = await openMenu(user);
  await user.click(within(menu).getByRole("menuitem", { name: "Anular" }));
  const dialog = screen.getByRole("dialog", { name: "Anular evaluación" });
  await user.click(within(dialog).getByRole("button", { name: "Anular evaluación" }));
  expect(await screen.findByText("Evaluación anulada.")).toBeTruthy();
  expect(puts(calls, "/api/evaluaciones/10/estado")[0].body).toEqual({ estado: "ANULADA" });
  await waitFor(() => expect(within(rowNow()).getByText("Anulada")).toBeTruthy());
  expect(within(rowNow()).queryByRole("button", { name: "Ver" })).toBeNull();
});

test("17. (extra) Eliminar solo se ofrece en borrador y anulada, y envía DELETE", async () => {
  const calls = statefulBackend("BORRADOR");
  const user = userEvent.setup();
  const view = render(<Evaluations canWrite />);
  const menu = await openMenu(user);
  expect(menuLabels(menu)).toContain("Eliminar");
  await user.click(within(menu).getByRole("menuitem", { name: "Eliminar" }));
  const dialog = screen.getByRole("dialog", { name: "Eliminar evaluación" });
  await user.click(within(dialog).getByRole("button", { name: "Eliminar evaluación" }));
  expect(await screen.findByText("Evaluación eliminada.")).toBeTruthy();
  expect(calls.some((c) => c.method === "DELETE" && c.path === "/api/evaluaciones/10")).toBe(true);
  view.unmount();

  backend({ "GET /api/evaluaciones": page([evaluation({ estado: "ANULADA" })]) });
  const anulada = render(<Evaluations canWrite />);
  expect(menuLabels(await openMenu(user))).toEqual(["Eliminar"]);
  anulada.unmount();

  backend({ "GET /api/evaluaciones": page([evaluation({ estado: "PUBLICADA" })]) });
  render(<Evaluations canWrite />);
  expect(menuLabels(await openMenu(user))).not.toContain("Eliminar");
});

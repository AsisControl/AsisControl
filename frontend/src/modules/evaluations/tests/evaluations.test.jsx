import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { currentUrl, setUrl } from "../../../../tests/support/navigation.js";
import { Evaluations } from "@/modules/evaluations";
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
  idCalificacion, idCompetencia, nivelLogro, version: 2, corregida: false, ...overrides,
});
const student = (idAlumno, nombreCompleto, notas = []) => ({ idAlumno, codigoAlumno: `E-${idAlumno}`, nombreCompleto, notas });
const sheet = (overrides = {}, alumnos) => ({
  evaluacion: evaluation(overrides),
  alumnos: alumnos ?? [student(1, "Alvarado Medina, Diego"), student(2, "Bravo Castillo, Ana")],
});
function backend(routes = {}) {
  return fakeBackend({
    "GET /api/asignaciones-cursos/mias": page([assignment]),
    "GET /api/evaluaciones": page([evaluation()]),
    "GET /api/evaluaciones/:id": evaluation(),
    "GET /api/evaluaciones/:id/planilla": sheet(),
    "GET /api/cursos-competencias": page([
      { cursoId: 100, competenciaId: 1, competenciaNombre: "Resuelve problemas", orden: 1, activo: true },
      { cursoId: 100, competenciaId: 2, competenciaNombre: "Razona en geometría", orden: 2, activo: true },
      { cursoId: 200, competenciaId: 3, competenciaNombre: "Otro curso", orden: 1, activo: true },
      { cursoId: 100, competenciaId: 4, competenciaNombre: "Retirada", orden: 3, activo: false },
    ]),
    "GET /api/calificaciones/consolidados": [],
    ...routes,
  });
}
beforeEach(() => {
  signIn({ role: "DOCENTE", permissions: ["CALIFICACIONES_LEER", "CALIFICACIONES_ESCRIBIR"] });
  setUrl("/evaluaciones");
});
const openSheet = (props = {}) =>
  render(<Evaluations tab="registro-de-calificaciones" evaluationId="10" canWrite {...props} />);
const level = (value, name, competency) => `${value}: ${name}, ${competency}`;

test("Lista las evaluaciones con su curso, sección, fecha y competencias", async () => {
  backend();
  render(<Evaluations canWrite />);
  const row = (await screen.findByText("Examen parcial")).closest("tr");
  expect(within(row).getByText("Matemática")).toBeTruthy();
  expect(within(row).getByText("3° A")).toBeTruthy();
  expect(within(row).getByText("Bimestral")).toBeTruthy();
  expect(within(row).getByText(/22 .*2026/)).toBeTruthy();
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
  expect(await within(dialog).findByText(/Faltan 4 niveles por registrar\. Completa la planilla/)).toBeTruthy();
  expect(calls.find((c) => c.method === "PUT").body).toEqual({ estado: "PUBLICADA" });
});

test("Nueva evaluación pide los datos, ofrece solo competencias del curso y va a calificar", async () => {
  const calls = backend({ "POST /api/evaluaciones": ({ body }) => evaluation({ ...body, id: 77 }) });
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  await user.click(await screen.findByRole("button", { name: "+ Nueva evaluación" }));
  const dialog = screen.getByRole("dialog", { name: "Nueva evaluación" });
  expect(within(dialog).getByText("Selecciona un curso para ver las competencias disponibles.")).toBeTruthy();
  await user.click(within(dialog).getByRole("button", { name: "Crear evaluación" }));
  expect(within(dialog).getByText("Escribe el nombre de la evaluación.")).toBeTruthy();
  expect(within(dialog).getByText("Selecciona al menos una competencia.")).toBeTruthy();
  expect(calls.some((c) => c.method === "POST")).toBe(false);

  await user.type(within(dialog).getByLabelText(/Nombre de la evaluación/), "Práctica 3");
  const { fireEvent } = await import("@testing-library/react");
  fireEvent.change(within(dialog).getByLabelText(/Fecha de evaluación/), { target: { value: "2026-10-01" } });
  await user.selectOptions(within(dialog).getByLabelText(/^Curso/), "100");
  await user.selectOptions(within(dialog).getByLabelText(/^Sección/), "8");
  await user.click(await within(dialog).findByLabelText("Resuelve problemas"));
  expect(within(dialog).queryByLabelText("Otro curso")).toBeNull();
  expect(within(dialog).queryByLabelText("Retirada")).toBeNull();
  await user.click(within(dialog).getByRole("button", { name: "Crear evaluación" }));
  await waitFor(() => expect(calls.some((c) => c.method === "POST")).toBe(true));
  expect(calls.find((c) => c.method === "POST").body).toMatchObject({
    idAsignacionCurso: 5, titulo: "Práctica 3", periodoEvaluacion: "BIMESTRAL",
    fechaEvaluacion: "2026-10-01", idsCompetencia: [1],
  });
  await waitFor(() => expect(currentUrl()).toBe("/evaluaciones/registro-de-calificaciones/77"));
});

test("Guardar borrador no abre la planilla y refresca el listado", async () => {
  const calls = backend({ "POST /api/evaluaciones": ({ body }) => evaluation({ ...body, id: 77 }) });
  const user = userEvent.setup();
  const { fireEvent } = await import("@testing-library/react");
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

test("US-18: en borrador registra niveles y avisa cuántos faltan para publicar", async () => {
  const calls = backend({ "PUT /api/calificaciones/lote": [] });
  const user = userEvent.setup();
  openSheet();
  await screen.findByRole("table", { name: "Planilla de Examen parcial" });
  expect(screen.getByText("Faltan 4 niveles para poder publicar.")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Guardar evaluación" }).disabled).toBe(true);
  expect(screen.queryByLabelText(/Motivo de la corrección/)).toBeNull();

  const name = "Alvarado Medina, Diego";
  const button = screen.getByRole("button", { name: level("AD", name, "Resuelve problemas") });
  await user.click(button);
  expect(button.getAttribute("aria-pressed")).toBe("true");
  expect(screen.getByText("Faltan 3 niveles para poder publicar.")).toBeTruthy();
  await user.click(button);
  expect(button.getAttribute("aria-pressed")).toBe("false");
  await user.click(button);
  await user.click(screen.getByRole("button", { name: level("B", "Bravo Castillo, Ana", "Razona en geometría") }));
  await user.click(screen.getByRole("button", { name: "Guardar evaluación" }));
  await screen.findByText(/Niveles guardados/);
  expect(calls.find((c) => c.method === "PUT").body).toEqual({
    calificaciones: [
      { idEvaluacion: 10, idAlumno: 1, idCompetencia: 1, nivelLogro: "AD", observacion: null },
      { idEvaluacion: 10, idAlumno: 2, idCompetencia: 2, nivelLogro: "B", observacion: null },
    ],
  });
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

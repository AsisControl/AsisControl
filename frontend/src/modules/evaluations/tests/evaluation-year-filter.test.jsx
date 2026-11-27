import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { Evaluations } from "@/modules/evaluations";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

// ASISCTRL-179: el año académico es el filtro principal de Mis evaluaciones.

const evaluation = (overrides = {}) => ({
  id: 10, idAsignacionCurso: 5, titulo: "Examen vigente", descripcion: null,
  tipoEvaluacion: "EXAMEN", periodoEvaluacion: "BIMESTRAL", estado: "BORRADOR",
  fechaEvaluacion: "2026-09-22", ponderacionPorcentaje: 100, competencias: [],
  idCurso: 100, curso: "Matemática", idSeccion: 8, seccion: "3° A", anioAcademico: 2026,
  ...overrides,
});
const previous = evaluation({
  id: 20, idAsignacionCurso: 6, titulo: "Examen anterior", estado: "CERRADA",
  fechaEvaluacion: "2025-09-22", idCurso: 90, curso: "Comunicación", idSeccion: 7, seccion: "2° B",
  anioAcademico: 2025,
});
const assignments = [
  { id: 5, cursoId: 100, curso: "Matemática", seccionId: 8, seccion: "A", grado: 3, anioAcademico: 2026 },
  { id: 6, cursoId: 90, curso: "Comunicación", seccionId: 7, seccion: "B", grado: 2, anioAcademico: 2025 },
];

function backend(routes = {}) {
  return fakeBackend({
    "GET /api/asignaciones-cursos/mias": page(assignments),
    "GET /api/evaluaciones/anio-vigente": { anioActivo: 2026 },
    "GET /api/evaluaciones": page([evaluation(), previous]),
    ...routes,
  });
}
beforeEach(() => {
  signIn({ role: "DOCENTE", permissions: ["CALIFICACIONES_LEER", "CALIFICACIONES_ESCRIBIR"] });
  setUrl("/evaluaciones");
});
const yearSelect = () => screen.getByLabelText("Año académico");
const listCalls = (calls) => calls.filter((call) => call.path === "/api/evaluaciones");
const options = (label) =>
  within(screen.getByLabelText(label)).getAllByRole("option").map((option) => option.textContent);

test("El año vigente viene seleccionado y es el primer filtro", async () => {
  const calls = backend();
  render(<Evaluations canWrite />);
  await screen.findByText("Examen vigente");
  expect(yearSelect().value).toBe("2026");
  expect(options("Año académico")).toEqual(["Selecciona un año", "2026 · Vigente", "2025"]);
  expect(within(yearSelect()).getByRole("option", { name: "Selecciona un año" }).disabled).toBe(true);
  expect(screen.getAllByRole("combobox")[0]).toBe(yearSelect());
  expect(screen.queryByText("Examen anterior")).toBeNull();
  expect(screen.getByText("1 evaluación")).toBeTruthy();
  expect(listCalls(calls)).toHaveLength(1);
});

test("Al cambiar de año solo se ven sus evaluaciones y sus cursos, sin volver a consultar", async () => {
  const calls = backend();
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  await screen.findByText("Examen vigente");
  expect(options("Curso")).toEqual(["Todos los cursos", "Matemática · 3° grado"]);
  await user.selectOptions(yearSelect(), "2025");
  expect(screen.getByText("Examen anterior")).toBeTruthy();
  expect(screen.queryByText("Examen vigente")).toBeNull();
  expect(options("Curso")).toEqual(["Todos los cursos", "Comunicación · 2° grado"]);
  expect(listCalls(calls)).toHaveLength(1);
});

test("Cambiar de año descarta el curso y la sección del año anterior", async () => {
  const calls = backend();
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  await screen.findByText("Examen vigente");
  await user.selectOptions(screen.getByLabelText("Curso"), "100");
  await user.selectOptions(screen.getByLabelText("Sección"), "8");
  await user.selectOptions(yearSelect(), "2025");
  expect(await screen.findByText("Examen anterior")).toBeTruthy();
  expect(screen.getByLabelText("Curso").value).toBe("");
  expect(screen.getByLabelText("Sección").value).toBe("");
  await waitFor(() => expect(listCalls(calls).at(-1).query).toEqual({ size: "100", page: "0" }));
});

test("El año se combina con el período y el estado, que siguen aparte", async () => {
  const all = [
    evaluation(),
    evaluation({ id: 11, titulo: "Práctica mensual", periodoEvaluacion: "MENSUAL", estado: "PUBLICADA" }),
    previous,
    evaluation({ ...previous, id: 21, titulo: "Mensual anterior", periodoEvaluacion: "MENSUAL", estado: "PUBLICADA" }),
  ];
  const calls = backend({
    "GET /api/evaluaciones": ({ query }) =>
      page(all.filter((item) =>
        (!query.periodo || item.periodoEvaluacion === query.periodo) &&
        (!query.estado || item.estado === query.estado))),
  });
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  await screen.findByText("2 evaluaciones");
  await user.selectOptions(screen.getByLabelText("Período"), "MENSUAL");
  await user.selectOptions(screen.getByLabelText("Estado"), "PUBLICADA");
  expect(await screen.findByText("Práctica mensual")).toBeTruthy();
  expect(screen.queryByText("Examen vigente")).toBeNull();
  expect(screen.queryByText("Mensual anterior")).toBeNull();
  await user.selectOptions(yearSelect(), "2025");
  expect(screen.getByText("Mensual anterior")).toBeTruthy();
  expect(screen.getByText("1 evaluación")).toBeTruthy();
  expect(screen.getByLabelText("Período").value).toBe("MENSUAL");
  expect(screen.getByLabelText("Estado").value).toBe("PUBLICADA");
  expect(listCalls(calls).at(-1).query).toMatchObject({ periodo: "MENSUAL", estado: "PUBLICADA" });
  expect(listCalls(calls).at(-1).query.anioAcademico).toBeUndefined();
});

test("Un año sin evaluaciones muestra el listado vacío", async () => {
  backend({ "GET /api/evaluaciones": page([previous]) });
  render(<Evaluations canWrite />);
  expect(await screen.findByText("No hay evaluaciones con estos filtros.")).toBeTruthy();
  expect(yearSelect().value).toBe("2026");
  expect(screen.getByText("0 evaluaciones")).toBeTruthy();
});

test("Sin año vigente se elige el más reciente disponible", async () => {
  backend({
    "GET /api/evaluaciones/anio-vigente": { anioActivo: null },
    "GET /api/asignaciones-cursos/mias": page([assignments[1]]),
    "GET /api/evaluaciones": page([previous, evaluation({ ...previous, id: 30, titulo: "Más antiguo", anioAcademico: 2024 })]),
  });
  render(<Evaluations canWrite />);
  await screen.findByText("Examen anterior");
  expect(yearSelect().value).toBe("2025");
  expect(options("Año académico")).toEqual(["Selecciona un año", "2025", "2024"]);
  expect(screen.queryByText("Más antiguo")).toBeNull();
});

test("Sin año vigente ni datos no se ofrece ningún año", async () => {
  backend({
    "GET /api/evaluaciones/anio-vigente": { anioActivo: null },
    "GET /api/asignaciones-cursos/mias": page([]),
    "GET /api/evaluaciones": page([]),
  });
  render(<Evaluations canWrite />);
  expect(await screen.findByText("No hay evaluaciones con estos filtros.")).toBeTruthy();
  expect(yearSelect().value).toBe("");
  expect(options("Año académico")).toEqual(["Selecciona un año"]);
});

test("Si falla el año vigente se avisa y se usa el más reciente", async () => {
  backend({ "GET /api/evaluaciones/anio-vigente": reply(500, { message: "Sin año vigente disponible" }) });
  render(<Evaluations canWrite />);
  expect(await screen.findByText("Sin año vigente disponible")).toBeTruthy();
  expect(await screen.findByText("Examen vigente")).toBeTruthy();
  expect(yearSelect().value).toBe("2026");
});

test("Si falla el listado se muestra el error y no el mensaje de vacío", async () => {
  backend({ "GET /api/evaluaciones": reply(500, { message: "No se pudo listar" }) });
  render(<Evaluations canWrite />);
  expect(await screen.findByText("No se pudo listar")).toBeTruthy();
  expect(screen.queryByText("No hay evaluaciones con estos filtros.")).toBeNull();
  expect(screen.queryByRole("table")).toBeNull();
});

test("Carga todas las páginas del servidor y pagina solo el año elegido", async () => {
  const many = Array.from({ length: 25 }, (_, index) =>
    evaluation({ id: 100 + index, titulo: `Práctica ${index + 1}` }));
  const pages = [[previous, ...many.slice(0, 12)], many.slice(12)];
  const calls = backend({
    "GET /api/evaluaciones": ({ query }) => ({
      content: pages[Number(query.page)], number: Number(query.page), totalElements: 26, totalPages: 2,
    }),
  });
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  expect(await screen.findByText("25 evaluaciones")).toBeTruthy();
  expect(listCalls(calls).map((call) => call.query.page)).toEqual(["0", "1"]);
  expect(screen.getByText("25 registros · Página 1 de 2")).toBeTruthy();
  expect(screen.getByText("Práctica 20")).toBeTruthy();
  expect(screen.queryByText("Práctica 21")).toBeNull();
  await user.click(screen.getByRole("button", { name: "Siguiente" }));
  expect(screen.getByText("Práctica 21")).toBeTruthy();
  expect(screen.queryByText("Práctica 20")).toBeNull();
  await user.selectOptions(yearSelect(), "2025");
  expect(screen.getByText("1 registro · Página 1 de 1")).toBeTruthy();
  await user.selectOptions(yearSelect(), "2026");
  expect(screen.getByText("25 registros · Página 1 de 2")).toBeTruthy();
  expect(listCalls(calls)).toHaveLength(2);
});

test("Una respuesta atrasada no reemplaza el resultado de los filtros actuales", async () => {
  const late = [];
  const wait = (value) => new Promise((resolve) => late.push(() => resolve(value)));
  const calls = backend({
    "GET /api/evaluaciones": ({ query }) =>
      query.estado ? page([evaluation({ titulo: "Resultado actual" })]) : wait(page([evaluation()])),
  });
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  await waitFor(() => expect(listCalls(calls)).toHaveLength(1));
  await user.selectOptions(screen.getByLabelText("Estado"), "BORRADOR");
  await screen.findByText("Resultado actual");
  late.forEach((resolve) => resolve());
  await new Promise((resolve) => setTimeout(resolve, 0));
  expect(screen.getByText("Resultado actual")).toBeTruthy();
  expect(screen.queryByText("Examen vigente")).toBeNull();
});

test("Salir de la pantalla antes de recibir las respuestas no las aplica", async () => {
  const late = [];
  const fail = () => new Promise((resolve) => late.push(() => resolve(reply(500, { message: "Tarde" }))));
  const calls = backend({ "GET /api/evaluaciones": fail, "GET /api/evaluaciones/anio-vigente": fail });
  const view = render(<Evaluations canWrite />);
  await waitFor(() => expect(late).toHaveLength(2));
  view.unmount();
  late.forEach((resolve) => resolve());
  await new Promise((resolve) => setTimeout(resolve, 0));
  expect(screen.queryByText("Tarde")).toBeNull();
  expect(listCalls(calls)).toHaveLength(1);
});

test("El año de una asignación retirada sigue disponible aunque cambien los filtros", async () => {
  backend({
    "GET /api/asignaciones-cursos/mias": page([assignments[0]]),
    "GET /api/evaluaciones": ({ query }) => page(query.estado ? [] : [evaluation(), previous]),
  });
  const user = userEvent.setup();
  render(<Evaluations canWrite />);
  await screen.findByText("Examen vigente");
  await user.selectOptions(screen.getByLabelText("Estado"), "ANULADA");
  await screen.findByText("No hay evaluaciones con estos filtros.");
  expect(options("Año académico")).toContain("2025");
});

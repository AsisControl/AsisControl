import { beforeEach, expect, test, vi } from "vitest";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { ProgressPreview } from "../progress-preview";
import { ModuleOutlet } from "@/core/navigation/module-outlet";
import { SessionUserProvider } from "@/core/layout/session-user";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const assignment = { id: 5, cursoId: 10, curso: "Matemática", seccionId: 8, seccion: "A", grado: 3, anioAcademico: 2026 };
const evaluation = {
  id: 20, idAsignacionCurso: 5, titulo: "Práctica", periodoEvaluacion: "BIMESTRAL",
  fechaEvaluacion: "2026-10-01", estado: "BORRADOR", curso: "Matemática", seccion: "3° A", anioAcademico: 2026,
  competencias: [{ id: 1, codigo: "C1", nombre: "Resuelve problemas" }, { id: 2, codigo: "C2", nombre: "Argumenta" }],
};
const students = [{ idAlumno: 11, codigoAlumno: "E11", nombreCompleto: "Ana Ramos", notas: [] }, { idAlumno: 12, nombreCompleto: "Luis Díaz", notas: [] }];
const grade = { id: 1, idEvaluacion: 20, idAlumno: 11, idCompetencia: 1, nivelLogro: "A", observacion: "Explica su procedimiento.\nDebe revisar las unidades." };
const user = { id: "1", username: "docente", fullName: "Docente", role: "DOCENTE", permissions: ["CALIFICACIONES_LEER", "ACADEMICO_LEER"] };

function backend(routes = {}) {
  return fakeBackend({
    "GET /api/asignaciones-cursos/mias": page([assignment]),
    "GET /api/evaluaciones": page([evaluation]),
    "GET /api/evaluaciones/:id/planilla": { evaluacion: evaluation, alumnos: students },
    "GET /api/calificaciones": ({ query }) => page(query.idAlumno === "11" ? [grade] : []),
    ...routes,
  });
}
beforeEach(() => { signIn(user); setUrl("/progreso"); });
async function selectStudent(name = "Ana Ramos") {
  const interaction = userEvent.setup();
  await interaction.selectOptions(await screen.findByLabelText("Curso y sección"), "5");
  await interaction.selectOptions(await screen.findByLabelText("Evaluación"), "20");
  await interaction.selectOptions(await screen.findByLabelText("Estudiante"), name);
  return interaction;
}

test("Consulta solo resultados del estudiante y evaluación elegidos, con observaciones y competencias sin nota", async () => {
  const calls = backend();
  render(<ProgressPreview />);
  await selectStudent();
  const preview = await screen.findByRole("article", { name: "Vista preliminar del estudiante" });
  expect(within(preview).getByText("C1 · Resuelve problemas")).toBeTruthy();
  expect(within(preview).getByText("A")).toBeTruthy();
  expect(within(preview).getByText(/Explica su procedimiento/)).toBeTruthy();
  expect(within(preview).getByText("Sin resultado")).toBeTruthy();
  expect(within(preview).getByText("Sin observación registrada")).toBeTruthy();
  expect(within(preview).getByText("Estado de la evaluación: Borrador")).toBeTruthy();
  expect(screen.getByText(/no constituye una libreta publicada/)).toBeTruthy();
  expect(screen.queryByRole("button", { name: /publicar|generar/i })).toBeNull();
  expect(calls.find((call) => call.path === "/api/evaluaciones").query.idAsignacionCurso).toBe("5");
  expect(calls.find((call) => call.path === "/api/calificaciones").query).toMatchObject({ idAlumno: "11", idEvaluacion: "20" });
  expect(calls.every((call) => call.method === "GET")).toBe(true);
  expect(calls.some((call) => call.path.includes("consolidados"))).toBe(false);
});

test("Cambiar estudiante oculta inmediatamente sus datos e ignora respuestas tardías", async () => {
  let resolveOld;
  backend({ "GET /api/calificaciones": ({ query }) => query.idAlumno === "11"
    ? new Promise((resolve) => { resolveOld = resolve; }) : page([]) });
  render(<ProgressPreview />);
  const interaction = await selectStudent();
  await waitFor(() => expect(resolveOld).toBeTypeOf("function"));
  expect(screen.getByRole("status").textContent).toContain("Cargando");
  await interaction.selectOptions(screen.getByLabelText("Estudiante"), "12");
  expect(await screen.findByText(/aún no tiene resultados/)).toBeTruthy();
  await act(async () => resolveOld(page([grade])));
  const preview = screen.getByRole("article");
  expect(within(preview).getByRole("heading", { name: "Luis Díaz" })).toBeTruthy();
  expect(within(preview).queryByText(/Explica su procedimiento/)).toBeNull();
});

test("Descarta resultados de otro estudiante o evaluación aunque aparezcan en la respuesta", async () => {
  backend({ "GET /api/calificaciones": page([{ ...grade, idAlumno: 999 }, { ...grade, idEvaluacion: 999 }]) });
  render(<ProgressPreview />);
  await selectStudent();
  expect(await screen.findByText(/aún no tiene resultados/)).toBeTruthy();
  expect(screen.queryByText(/Explica su procedimiento/)).toBeNull();
});

test("Cambiar curso y sección reinicia evaluación, estudiante y detalle", async () => {
  backend(); render(<ProgressPreview />);
  const interaction = await selectStudent();
  await screen.findByRole("article");
  await interaction.selectOptions(screen.getByLabelText("Curso y sección"), "");
  expect(screen.queryByRole("article")).toBeNull();
  expect(screen.queryByLabelText("Estudiante")).toBeNull();
  await interaction.selectOptions(screen.getByLabelText("Curso y sección"), "5");
  expect((await screen.findByLabelText("Evaluación")).value).toBe("");
});

test.each([
  ["GET /api/asignaciones-cursos/mias", page([]), "No tienes cursos y secciones asignados."],
  ["GET /api/evaluaciones", page([]), "No hay evaluaciones disponibles para este curso y sección."],
  ["GET /api/evaluaciones/:id/planilla", { evaluacion: evaluation, alumnos: [] }, "No hay estudiantes con matrícula activa en esta evaluación."],
])("Muestra vacío de %s", async (path, response, text) => {
  backend({ [path]: response }); render(<ProgressPreview />);
  const interaction = userEvent.setup();
  if (!path.includes("asignaciones")) await interaction.selectOptions(await screen.findByLabelText("Curso y sección"), "5");
  if (path.includes("planilla")) await interaction.selectOptions(await screen.findByLabelText("Evaluación"), "20");
  expect(await screen.findByText(text)).toBeTruthy();
});

test("Muestra denegación del backend sin datos y permite reintentar", async () => {
  let denied = true;
  backend({ "GET /api/calificaciones": () => denied ? reply(403, { message: "Acceso denegado" }) : page([grade]) });
  render(<ProgressPreview />); const interaction = await selectStudent();
  expect((await screen.findByRole("alert")).textContent).toBe("Acceso denegado");
  expect(screen.queryByRole("article")).toBeNull();
  denied = false;
  await interaction.click(screen.getByRole("button", { name: "Reintentar" }));
  expect(await screen.findByText(/Explica su procedimiento/)).toBeTruthy();
  expect(screen.queryByRole("alert")).toBeNull();
});

test("Recorre páginas de asignaciones, evaluaciones y calificaciones sin perder observaciones", async () => {
  const calls = backend({
    "GET /api/asignaciones-cursos/mias": ({ query }) => query.page === "0" ? page([], 2) : page([assignment]),
    "GET /api/evaluaciones": ({ query }) => query.page === "0" ? page([{ ...evaluation, id: 30, estado: "ANULADA" }], 2) : page([evaluation]),
    "GET /api/calificaciones": ({ query }) => query.page === "0" ? page([], 2) : page([grade]),
  });
  render(<ProgressPreview />); await selectStudent();
  expect(await screen.findByText(/Explica su procedimiento/)).toBeTruthy();
  for (const path of ["/api/asignaciones-cursos/mias", "/api/evaluaciones", "/api/calificaciones"])
    expect(calls.filter((call) => call.path === path).map((call) => call.query.page)).toEqual(["0", "1"]);
});

test.each([[], ["CALIFICACIONES_LEER"], ["ACADEMICO_LEER"]])("Sin ambos permisos rechaza la ruta sin consultar datos (%j)", async (permissions) => {
  const calls = backend();
  render(<SessionUserProvider value={{ ...user, permissions }}><ModuleOutlet /></SessionUserProvider>);
  expect(screen.getByText("ACCESO DENEGADO")).toBeTruthy();
  expect(calls).toHaveLength(0);
});

test("La ruta docente Progreso abre la consulta con permisos de lectura", async () => {
  backend();
  render(<SessionUserProvider value={user}><ModuleOutlet /></SessionUserProvider>);
  expect(await screen.findByLabelText("Curso y sección")).toBeTruthy();
  expect(screen.getByRole("heading", { name: "Progreso por competencias" })).toBeTruthy();
});

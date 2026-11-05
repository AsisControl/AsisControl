import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { TeacherSchedule } from "@/modules/schedules";
import { ModuleOutlet } from "@/core/navigation/module-outlet";
import { SessionUserProvider } from "@/core/layout/session-user";
import { canAccess } from "@/core/navigation/access-policy";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const year = new Date().getFullYear();
const assignment = (overrides = {}) => ({
  id: 10, cursoId: 100, curso: "Matemática", seccionId: 8, seccion: "A", anioAcademico: year,
  grado: 3, docenteId: 7, docente: "Patricia Morales", activo: true, version: 0, horariosActivos: 2,
  ...overrides,
});
const block = (overrides = {}) => ({
  id: 1, asignacionCursoId: 10, cursoId: 100, curso: "Matemática", seccionId: 8, seccion: "A",
  docenteId: 7, docente: "Patricia Morales", aulaId: 1, aula: "Aula 201", diaSemana: "LUNES",
  horaInicio: "08:00:00", horaFin: "09:30:00", activo: true, version: 3,
  ...overrides,
});
function backend(routes = {}) {
  return fakeBackend({
    "GET /api/asignaciones-cursos/mias": page([assignment()]),
    "GET /api/horarios": page([block(), block({ id: 2, diaSemana: "MIERCOLES" })]),
    ...routes,
  });
}
beforeEach(() => {
  signIn({ role: "DOCENTE", permissions: ["ACADEMICO_LEER"] });
  setUrl("/horario");
});

test("Consulta solo el horario del docente autenticado y muestra curso, sección y aula", async () => {
  const calls = backend();
  render(<TeacherSchedule />);
  const table = await screen.findByRole("table", { name: "Mi horario semanal" });
  const query = calls.find((call) => call.path === "/api/horarios").query;
  expect(query.docenteId).toBe("7");
  expect(query.anioAcademico).toBe(String(year));
  expect(within(table).getAllByText("Matemática")).toHaveLength(2);
  expect(within(table).getAllByText("3° A")).toHaveLength(2);
  expect(within(table).getAllByText("Aula 201")).toHaveLength(2);
  expect(within(table).queryByText("Patricia Morales")).toBeNull();
  expect(screen.getByText("2 bloques de clase · 1 curso")).toBeTruthy();
});

test("Es de solo lectura: sin crear, editar ni eliminar, y sin escrituras", async () => {
  const calls = backend();
  render(<TeacherSchedule />);
  await screen.findByRole("table", { name: "Mi horario semanal" });
  expect(screen.queryByText(/Nuevo bloque/)).toBeNull();
  expect(screen.queryByLabelText(/Más acciones/)).toBeNull();
  expect(screen.queryByRole("button", { name: /Editar|Eliminar/ })).toBeNull();
  await userEvent.setup().click(screen.getByRole("button", { name: "Ver detalle de Matemática, Lunes 08:00" }));
  const dialog = screen.getByRole("dialog", { name: "Detalle del bloque" });
  expect(within(dialog).queryByRole("button", { name: /Editar|Eliminar/ })).toBeNull();
  expect(calls.every((call) => call.method === "GET")).toBe(true);
});

test("Clic en un bloque muestra su detalle completo", async () => {
  backend();
  render(<TeacherSchedule />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "Ver detalle de Matemática, Miércoles 08:00" }));
  const dialog = screen.getByRole("dialog", { name: "Detalle del bloque" });
  expect(within(dialog).getByText(`3° A · ${year}`)).toBeTruthy();
  expect(within(dialog).getByText("Patricia Morales")).toBeTruthy();
  expect(within(dialog).getByText("Aula 201")).toBeTruthy();
  expect(within(dialog).getByText("Miércoles")).toBeTruthy();
  expect(within(dialog).getByText("09:30")).toBeTruthy();
  await user.click(within(dialog).getByRole("button", { name: "Cerrar" }));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Sin asignaciones lo indica y no consulta horarios", async () => {
  const calls = backend({ "GET /api/asignaciones-cursos/mias": page([]) });
  render(<TeacherSchedule />);
  expect(await screen.findByText(/Aún no tienes cursos asignados/)).toBeTruthy();
  expect(calls.some((call) => call.path === "/api/horarios")).toBe(false);
});

test("Con asignaciones pero sin bloques muestra la matriz vacía con un mensaje", async () => {
  backend({ "GET /api/horarios": page([]) });
  render(<TeacherSchedule />);
  const table = await screen.findByRole("table", { name: "Mi horario semanal" });
  expect(within(table).getByText(`Aún no tienes bloques de horario asignados en ${year}.`)).toBeTruthy();
});

test("Con varios años permite cambiar y vuelve a consultar", async () => {
  const calls = backend({
    "GET /api/asignaciones-cursos/mias": page([
      assignment(),
      assignment({ id: 11, seccionId: 9, anioAcademico: year + 1 }),
    ]),
  });
  render(<TeacherSchedule />);
  const user = userEvent.setup();
  await screen.findByRole("table", { name: "Mi horario semanal" });
  await user.selectOptions(screen.getByLabelText("Año académico"), String(year + 1));
  await waitFor(() =>
    expect(calls.some((call) => call.path === "/api/horarios" && call.query.anioAcademico === String(year + 1))).toBe(true),
  );
});

test("Si falla la consulta lo informa y Actualizar permite recuperarla", async () => {
  let fail = true;
  backend({ "GET /api/horarios": () => (fail ? reply(500, { message: "Error del servidor" }) : page([block()])) });
  render(<TeacherSchedule />);
  expect(await screen.findByText("Error del servidor")).toBeTruthy();
  fail = false;
  await userEvent.setup().click(screen.getByText("Actualizar"));
  expect(await screen.findByRole("table", { name: "Mi horario semanal" })).toBeTruthy();
  expect(screen.queryByText("Error del servidor")).toBeNull();
});

test("El menú Horario del docente abre su pantalla y exige ACADEMICO_LEER", async () => {
  backend();
  const user = { id: "1", username: "docente", fullName: "Patricia Morales", role: "DOCENTE", permissions: ["ACADEMICO_LEER"] };
  expect(canAccess(user, "Horario")).toBe(true);
  expect(canAccess({ ...user, permissions: [] }, "Horario")).toBe(false);
  render(<SessionUserProvider value={user}><ModuleOutlet /></SessionUserProvider>);
  expect(await screen.findByRole("heading", { name: "Mi horario" })).toBeTruthy();
});

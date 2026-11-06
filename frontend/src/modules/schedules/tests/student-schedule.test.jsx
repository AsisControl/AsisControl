import { beforeEach, expect, test, vi } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { ModuleOutlet } from "@/core/navigation/module-outlet";
import { SessionUserProvider } from "@/core/layout/session-user";
import { StudentSchedule } from "@/modules/schedules";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const year = new Date().getFullYear();
const enrollment = { id: 10, seccionId: 8, seccion: "A", grado: 3, anioAcademico: year, estado: "ACTIVA" };
const block = {
  id: 1, asignacionCursoId: 20, cursoId: 30, curso: "Matemática", seccionId: 8,
  seccion: "A", docenteId: 40, docente: "Patricia Morales", aulaId: 50,
  aula: "Aula 201", diaSemana: "LUNES", horaInicio: "08:00:00", horaFin: "09:30:00",
  activo: true, version: 1,
};
const student = { id: "1", username: "alumno", fullName: "Luis García", role: "ESTUDIANTE",
  permissions: ["ACADEMICO_LEER", "MATRICULA_LEER"] };

beforeEach(() => {
  signIn(student);
  setUrl("/horario");
});

test("El estudiante consulta solo su horario y abre el detalle desde cualquier parte del bloque", async () => {
  const calls = fakeBackend({
    "GET /api/matriculas": page([enrollment]),
    "GET /api/horarios/mio": [block],
  });
  render(<SessionUserProvider value={student}><ModuleOutlet /></SessionUserProvider>);
  const table = await screen.findByRole("table", { name: "Mi horario semanal" });
  expect(within(table).getByText("Matemática")).toBeTruthy();
  expect(within(table).getByText("3° A")).toBeTruthy();
  expect(screen.queryByText(/Nuevo bloque/)).toBeNull();
  expect(screen.queryByRole("button", { name: /Editar|Eliminar/ })).toBeNull();
  const article = within(table).getByRole("button", { name: /Ver detalle de Matemática/ }).closest("article");
  fireEvent.click(article);
  const dialog = screen.getByRole("dialog", { name: "Detalle del bloque" });
  expect(within(dialog).getByText("Patricia Morales")).toBeTruthy();
  expect(within(dialog).getByText("Aula 201")).toBeTruthy();
  expect(calls.some((call) => call.path === "/api/horarios")).toBe(false);
  expect(calls.find((call) => call.path === "/api/horarios/mio").query.anioAcademico).toBe(String(year));
  expect(calls.every((call) => call.method === "GET")).toBe(true);
});

test("Sin matrícula activa muestra el estado vacío y no consulta bloques", async () => {
  const calls = fakeBackend({ "GET /api/matriculas": page([{ ...enrollment, estado: "RETIRADA" }]) });
  render(<StudentSchedule />);
  expect(await screen.findByText("No tienes una matrícula activa con horario disponible.")).toBeTruthy();
  expect(calls.some((call) => call.path === "/api/horarios/mio")).toBe(false);
});

test("Actualizar vuelve a consultar los bloques del estudiante", async () => {
  const calls = fakeBackend({
    "GET /api/matriculas": page([enrollment]),
    "GET /api/horarios/mio": [block],
  });
  render(<StudentSchedule />);
  await screen.findByRole("table", { name: "Mi horario semanal" });
  await userEvent.setup().click(screen.getByRole("button", { name: "Actualizar" }));
  expect(calls.filter((call) => call.path === "/api/horarios/mio").length).toBe(2);
});

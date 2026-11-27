// inscripcion cursos
import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, reply, signIn } from "../../../../tests/support/backend.js";
import { StudentCourses } from "@/modules/student-courses";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const RUTA = "GET /api/alumnos/me/cursos";
const curso = (datos) => ({
  cursoId: 1, curso: "Comunicación", docente: null, horario: null, aula: null,
  anioAcademico: 2026, seccion: "1° Grado A", vigente: true, ...datos,
});
const CURSOS = {
  cursos: [
    curso({}),
    curso({ cursoId: 2, curso: "Matemática", docente: "Angela García", horario: "Lun 07:30–09:00 · Mié 10:00–11:30", aula: "Aula 101" }),
  ],
};

beforeEach(() => {
  signIn({ role: "ESTUDIANTE", id: "7", permissions: ["ACADEMICO_LEER", "ASISTENCIA_LEER"] });
});

test("Muestra los cursos vigentes con docente, cada bloque de horario y aula", async () => {
  const calls = fakeBackend({ [RUTA]: CURSOS });
  render(<StudentCourses />);
  expect(await screen.findByText("Matemática")).toBeTruthy();
  expect(calls[0].query).toEqual({ periodo: "VIGENTES" });
  const [comunicacion, matematica] = screen.getAllByRole("article");
  expect(within(comunicacion).getByText("1° Grado A · Año 2026")).toBeTruthy();
  expect(within(comunicacion).getByText("Docente por asignar")).toBeTruthy();
  expect(within(comunicacion).getByText("Por asignar")).toBeTruthy();
  expect(within(comunicacion).getByText("Horario por definir")).toBeTruthy();
  expect(within(matematica).getByText("Angela García")).toBeTruthy();
  expect(within(matematica).getByText("Activo")).toBeTruthy();
  expect(within(matematica).getByText("Lun 07:30–09:00")).toBeTruthy();
  expect(within(matematica).getByText("Mié 10:00–11:30")).toBeTruthy();
  expect(within(matematica).getByText("Aula 101")).toBeTruthy();
  expect(within(matematica).getByRole("link", { name: "Ver tareas" }).getAttribute("href")).toBe("/tareas");
  expect(within(matematica).getByRole("link", { name: "Asistencia" }).getAttribute("href")).toBe("/asistencia");
});

test("El filtro Pasados pide los cursos de años cerrados y los marca como cerrados", async () => {
  const calls = fakeBackend({
    [RUTA]: (request) => request.query.periodo === "PASADOS"
      ? { cursos: [curso({ curso: "Historia", anioAcademico: 2025, vigente: false, docente: "Luis Vega" })] }
      : CURSOS,
  });
  render(<StudentCourses />);
  await screen.findByText("Matemática");
  await userEvent.click(screen.getByRole("button", { name: "Pasados" }));
  const tarjeta = (await screen.findByText("Historia")).closest("article");
  expect(calls.at(-1).query).toEqual({ periodo: "PASADOS" });
  expect(within(tarjeta).getByText("Cerrado")).toBeTruthy();
  expect(within(tarjeta).getByText("1° Grado A · Año 2025")).toBeTruthy();
});

test("Sin matrícula activa avisa que los cursos aparecerán al finalizarla", async () => {
  fakeBackend({ [RUTA]: { cursos: [] } });
  render(<StudentCourses />);
  expect(await screen.findByText(/Aún no tienes una matrícula activa/)).toBeTruthy();
});

test("Ante un error permite reintentar", async () => {
  let fallar = true;
  fakeBackend({ [RUTA]: () => (fallar ? reply(500, { message: "No se pudieron cargar tus cursos" }) : CURSOS) });
  render(<StudentCourses />);
  expect(await screen.findByText("No se pudieron cargar tus cursos")).toBeTruthy();
  fallar = false;
  await userEvent.click(screen.getByRole("button", { name: "Reintentar" }));
  expect(await screen.findByText("Matemática")).toBeTruthy();
});

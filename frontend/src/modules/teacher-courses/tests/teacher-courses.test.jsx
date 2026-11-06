import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, reply, signIn } from "../../../../tests/support/backend.js";
import { currentUrl, setUrl } from "../../../../tests/support/navigation.js";
import { ModuleOutlet } from "@/core/navigation/module-outlet";
import { SessionUserProvider } from "@/core/layout/session-user";
import { canAccess } from "@/core/navigation/access-policy";
import { TeacherCoursesModule } from "@/modules/teacher-courses";
import { TeacherHome } from "@/modules/teacher-home";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const curso = (overrides = {}) => ({
  id: 42, curso: "Matemática", seccion: "3° Grado A", anioAcademico: 2026, vigente: true,
  estudiantes: 36, horario: "Lun, Mié, Vie 07:30", aula: "Aula 201", periodo: "Período III",
  ...overrides,
});
const RUTA = "GET /api/asistencias/asignaciones";

beforeEach(() => {
  signIn({ role: "DOCENTE", id: "999", permissions: ["ASISTENCIA_LEER"] });
  setUrl("/mis-cursos");
});

test("Muestra los cursos vigentes por defecto con sus datos y accesos", async () => {
  const calls = fakeBackend({ [RUTA]: [curso()] });
  render(<TeacherCoursesModule subpath={[]} />);
  expect(screen.getByRole("status").textContent).toBe("Cargando cursos…");
  const card = (await screen.findByText("Matemática")).closest("article");
  for (const texto of ["3° Grado A", "36 estudiantes", "Lun, Mié, Vie 07:30", "Aula 201", "Período III", "Activo"])
    expect(within(card).getByText(texto)).toBeTruthy();
  expect(within(card).getByRole("link", { name: "Asistencia" }).getAttribute("href")).toBe("/mis-cursos/42/asistencia");
  expect(within(card).getByRole("link", { name: "Ver evaluaciones" }).getAttribute("href")).toBe("/evaluaciones");
  expect(calls[0].query).toEqual({ periodo: "VIGENTES" });
});

test("Filtra por pasados y omite los datos que no existen", async () => {
  const calls = fakeBackend({ [RUTA]: [curso({ vigente: false, horario: null, aula: null, periodo: null, anioAcademico: 2025 })] });
  render(<TeacherCoursesModule subpath={[]} />);
  await screen.findByText("Matemática");
  await userEvent.click(screen.getByRole("button", { name: "Pasados" }));
  await screen.findByText("Cerrado");
  expect(screen.getByText("Año 2025")).toBeTruthy();
  expect(calls.at(-1).query).toEqual({ periodo: "PASADOS" });
});

test("Informa cuando no hay cursos y permite reintentar tras un error", async () => {
  fakeBackend({ [RUTA]: reply(500, { message: "No se pudo cargar" }) });
  const { unmount } = render(<TeacherCoursesModule subpath={[]} />);
  expect(await screen.findByRole("alert")).toBeTruthy();
  fakeBackend({ [RUTA]: [] });
  await userEvent.click(screen.getByRole("button", { name: "Reintentar" }));
  expect(await screen.findByText("No tienes cursos vigentes.")).toBeTruthy();
  unmount();
});

test("Redirige enlaces anteriores de evaluaciones y valida direcciones incorrectas", async () => {
  render(<TeacherCoursesModule subpath={["42", "evaluaciones"]} />);
  await waitFor(() => expect(currentUrl()).toBe("/evaluaciones"));
  render(<TeacherCoursesModule subpath={["abc", "asistencia"]} />);
  expect(screen.getByText("La dirección no es válida.")).toBeTruthy();
});

test("Mis cursos exige permiso de asistencia", () => {
  expect(canAccess({ role: "DOCENTE", permissions: ["ASISTENCIA_LEER"] }, "Mis cursos")).toBe(true);
  expect(canAccess({ role: "DOCENTE", permissions: [] }, "Mis cursos")).toBe(false);
});

test("Inicio del docente aclara que usa datos de ejemplo", () => {
  render(<TeacherHome fullName="Ana Pérez" />);
  expect(screen.getByRole("heading", { name: "Bienvenido/a, Ana Pérez" })).toBeTruthy();
  expect(screen.getByRole("note").textContent).toContain("Datos de ejemplo");
  expect(screen.getByRole("link", { name: "Nueva tarea" }).getAttribute("href")).toBe("/tareas");
});

test("El menú del docente abre sus pantallas", async () => {
  fakeBackend({ [RUTA]: [curso()] });
  const user = { id: "999", role: "DOCENTE", fullName: "Ana Pérez", permissions: ["ASISTENCIA_LEER"] };
  render(<SessionUserProvider value={user}><ModuleOutlet /></SessionUserProvider>);
  expect(await screen.findByRole("heading", { name: "Mis cursos" })).toBeTruthy();
});

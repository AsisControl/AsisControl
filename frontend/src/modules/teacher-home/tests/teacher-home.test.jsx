import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, reply, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { TeacherHome } from "@/modules/teacher-home";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const RUTA = "GET /api/dashboard/docente";
const clase = (overrides = {}) => ({
  sesionId: 7, asignacionCursoId: 42, curso: "Matemática", seccion: "3° Grado A", aula: "Aula 201",
  horaInicio: "07:30:00", horaFin: "09:30:00", estado: "PENDIENTE", momento: "EN_CURSO", puedeTomar: true,
  ...overrides,
});
const inicio = (overrides = {}) => ({
  fecha: "2026-11-17", anioAcademico: 2026, periodo: "Período III",
  clasesHoy: [
    clase(),
    clase({ sesionId: 8, seccion: "4° Grado B", horaInicio: "09:30:00", horaFin: "11:30:00", momento: "PROXIMA" }),
    clase({ sesionId: 6, seccion: "5° Grado C", horaInicio: "06:00:00", horaFin: "07:00:00",
      estado: "TOMADA", momento: "FINALIZADA", puedeTomar: false }),
  ],
  asistenciasPendientes: 1, porRevisar: 13, bloquesSemanales: 8,
  pendientes: [
    { tipo: "EVALUACION", id: 31, asignacionCursoId: 42, titulo: "Práctica 1", detalle: "Matemática · 3° Grado A",
      cantidad: 3, prioridad: "ALTA" },
    { tipo: "TAREA", id: 12, asignacionCursoId: 42, titulo: "Ejercicios Cap. 4", detalle: "Matemática · 3° Grado A",
      cantidad: 12, prioridad: "MEDIA" },
    { tipo: "EVALUACION", id: 32, asignacionCursoId: 42, titulo: "Examen", detalle: "Matemática · 4° Grado B",
      cantidad: 0, prioridad: "MEDIA" },
    { tipo: "JUSTIFICACION", id: null, asignacionCursoId: null, titulo: "Justificaciones de inasistencia",
      detalle: "Solicitudes enviadas por apoderados", cantidad: 1, prioridad: "MEDIA" },
  ],
  ...overrides,
});
const acciones = <section aria-label="Acciones rápidas"><p>Atajos del docente</p></section>;

beforeEach(() => {
  signIn({ role: "DOCENTE", id: "999", permissions: ["ASISTENCIA_LEER"] });
  setUrl("/");
});

test("Muestra la fecha del colegio, el año y los indicadores reales del docente", async () => {
  fakeBackend({ [RUTA]: inicio() });
  render(<TeacherHome fullName="Ana Pérez" />);
  expect(screen.getByRole("heading", { name: "Bienvenido/a, Ana Pérez" })).toBeTruthy();
  expect(screen.getByRole("status").textContent).toBe("Cargando tu inicio…");
  expect((await screen.findByText("Clases del día")).previousElementSibling.textContent).toBe("3");
  expect(screen.getByText("Asistencias pendientes").previousElementSibling.textContent).toBe("1");
  expect(screen.getByText("Evaluaciones y tareas por revisar").previousElementSibling.textContent).toBe("13");
  expect(screen.getByText("Bloques en mi horario").previousElementSibling.textContent).toBe("8");
  expect(screen.getByText(/Martes, 17 de noviembre de 2026 · Año académico 2026 — Período III/)).toBeTruthy();
  expect(screen.getByText(/Última actualización: \d{2}:\d{2}/)).toBeTruthy();
  expect(screen.queryByText(/Datos de ejemplo/)).toBeNull();
  expect(screen.getByRole("link", { name: "Nueva tarea" }).getAttribute("href")).toBe("/tareas");
});

test("Clases de hoy: tomar en curso, ver la ya tomada y sin acciones para la próxima", async () => {
  fakeBackend({ [RUTA]: inicio() });
  render(<TeacherHome fullName="Ana Pérez" />);
  const tarjeta = (await screen.findByRole("heading", { name: "Clases de hoy" })).closest("article");
  const enCurso = within(tarjeta).getByText("3° Grado A · Aula 201").closest("div").parentElement.parentElement;
  expect(within(enCurso).getByText("En curso")).toBeTruthy();
  expect(within(enCurso).getByText("07:30 – 09:30")).toBeTruthy();
  expect(within(enCurso).getByRole("link", { name: "Tomar asistencia" }).getAttribute("href"))
    .toBe("/mis-cursos/42/asistencia/7");
  expect(within(enCurso).getByRole("link", { name: "Evaluar" }).getAttribute("href")).toBe("/mis-cursos/42/evaluaciones");

  const proxima = within(tarjeta).getByText("4° Grado B · Aula 201").closest("div").parentElement.parentElement;
  expect(within(proxima).getByText("Próxima")).toBeTruthy();
  expect(within(proxima).queryByRole("link")).toBeNull();

  const tomada = within(tarjeta).getByText("5° Grado C · Aula 201").closest("div").parentElement.parentElement;
  expect(within(tomada).getByText("Asistencia tomada")).toBeTruthy();
  expect(within(tomada).queryByRole("link", { name: "Tomar asistencia" })).toBeNull();
  expect(within(tomada).getByRole("link", { name: "Ver asistencia" }).getAttribute("href")).toBe("/mis-cursos/42/asistencia/6");
});

test("Pendientes académicos llevan a la pantalla donde se resuelven, con su prioridad", async () => {
  fakeBackend({ [RUTA]: inicio() });
  render(<TeacherHome fullName="Ana Pérez" />);
  const tarjeta = (await screen.findByRole("heading", { name: "Pendientes académicos" })).closest("article");
  expect(within(tarjeta).getByText("4 pendientes")).toBeTruthy();
  const enlaces = within(tarjeta).getAllByRole("link");
  expect(enlaces.map((enlace) => [enlace.querySelector("span").textContent, enlace.getAttribute("href")])).toEqual([
    ["Registrar 3 niveles — Práctica 1", "/evaluaciones/registro-de-calificaciones/31"],
    ["Revisar 12 entregas — Ejercicios Cap. 4", "/tareas/12/entregas"],
    ["Publicar notas — Examen", "/evaluaciones/registro-de-calificaciones/32"],
    ["Revisar 1 justificación de inasistencia", "/asistencia"],
  ]);
  expect(within(enlaces[0].closest("li")).getByText("Alta")).toBeTruthy();
  expect(within(enlaces[1].closest("li")).getByText("Media")).toBeTruthy();
  expect(within(tarjeta).getByText("Tarea · Matemática · 3° Grado A")).toBeTruthy();
});

test("Sin clases ni pendientes lo dice claramente, sin inventar datos", async () => {
  fakeBackend({ [RUTA]: inicio({ clasesHoy: [], pendientes: [], asistenciasPendientes: 0, porRevisar: 0,
    anioAcademico: null, periodo: null }) });
  render(<TeacherHome fullName="Ana Pérez" />);
  expect(await screen.findByText("No tienes clases programadas para hoy.")).toBeTruthy();
  expect(screen.getByText("No tienes pendientes académicos.")).toBeTruthy();
  expect(screen.getByText("0 pendientes")).toBeTruthy();
  expect(screen.queryByText(/Año académico/)).toBeNull();
});

test("Si falla, avisa y conserva las acciones rápidas; Actualizar vuelve a consultar", async () => {
  let fallar = true;
  const calls = fakeBackend({ [RUTA]: () => (fallar ? reply(500, { message: "Error de prueba" }) : inicio()) });
  render(<TeacherHome fullName="Ana Pérez" quickActions={acciones} />);
  expect((await screen.findByRole("alert")).textContent).toContain("Error de prueba");
  expect(screen.getByRole("region", { name: "Acciones rápidas" })).toBeTruthy();
  expect(screen.queryByText("Clases de hoy")).toBeNull();
  fallar = false;
  await userEvent.setup().click(screen.getByRole("button", { name: "Actualizar" }));
  expect(await screen.findByText("Clases de hoy")).toBeTruthy();
  expect(screen.queryByRole("alert")).toBeNull();
  expect(calls.filter((call) => call.path === "/api/dashboard/docente")).toHaveLength(2);
});

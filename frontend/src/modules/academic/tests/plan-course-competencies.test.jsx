// cobertura: competencias de un curso dentro del plan de estudios
import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { PlanCourseCompetencies } from "../plan-course-competencies";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const course = { id: 3, codigo: "MAT1", nombre: "Matemática", horasSemanales: 4 };
const resuelve = { id: 1, codigo: "C1", nombre: "Resuelve problemas", activa: true };
const comunica = { id: 2, codigo: "C2", nombre: "Comunica ideas", activa: true };

beforeEach(() => signIn({ permissions: ["ACADEMICO_LEER", "ACADEMICO_ESCRIBIR"] }));

test("sin competencias guardadas carga las del catálogo del curso y permite quitarlas", async () => {
  fakeBackend({
    "GET /api/cursos-competencias/curso/3": [{ competenciaId: 1, competenciaCodigo: "C1", competenciaNombre: "Resuelve problemas" }],
    "GET /api/competencias": page([resuelve, comunica]),
  });
  const onChange = vi.fn();
  render(<PlanCourseCompetencies course={course} onChange={onChange} onClose={vi.fn()} />);
  const seleccion = await screen.findByRole("list", { name: "Competencias seleccionadas" });
  expect(within(seleccion).getByText("Resuelve problemas")).toBeTruthy();
  await userEvent.click(screen.getByRole("button", { name: "Quitar competencia Resuelve problemas" }));
  expect(onChange).toHaveBeenCalledWith([]);
});

test("agrega una competencia del catálogo y marca las que ya están en el plan", async () => {
  fakeBackend({ "GET /api/competencias": page([resuelve, comunica]) });
  const onChange = vi.fn();
  render(<PlanCourseCompetencies course={{ ...course, competencias: [resuelve] }} onChange={onChange} onClose={vi.fn()} />);
  const resultados = await screen.findByRole("list", { name: "Resultados de competencias" });
  expect(within(resultados).getByText("En el plan")).toBeTruthy();
  await userEvent.click(within(resultados).getByRole("button", { name: "Agregar competencia Comunica ideas" }));
  expect(onChange).toHaveBeenCalledWith([resuelve, { ...comunica, activa: true }]);
});

test("busca en el catálogo e informa cuando no hay resultados", async () => {
  const calls = fakeBackend({
    "GET /api/competencias": ({ query }) => (query.busqueda ? page([]) : page([comunica])),
  });
  render(<PlanCourseCompetencies course={{ ...course, competencias: [] }} onChange={vi.fn()} onClose={vi.fn()} />);
  expect(await screen.findByText("Este curso no tiene competencias seleccionadas para el plan.")).toBeTruthy();
  await screen.findByRole("list", { name: "Resultados de competencias" });
  const buscar = screen.getByLabelText("Buscar competencias en el catálogo");
  await userEvent.type(buscar, "xyz{Enter}");
  expect(await screen.findByText("No se encontraron competencias con esa búsqueda.")).toBeTruthy();
  expect(calls.at(-1).query.busqueda).toBe("xyz");
});

test("si falla el catálogo permite volver a buscar", async () => {
  let fallar = true;
  fakeBackend({
    "GET /api/competencias": () => (fallar ? reply(500, { message: "Catálogo no disponible" }) : page([comunica])),
  });
  render(<PlanCourseCompetencies course={{ ...course, competencias: [] }} onChange={vi.fn()} onClose={vi.fn()} />);
  expect(await screen.findByText("Catálogo no disponible")).toBeTruthy();
  fallar = false;
  await userEvent.click(screen.getByRole("button", { name: "Volver a buscar" }));
  expect(await screen.findByText("Comunica ideas")).toBeTruthy();
});

test("si fallan las competencias del curso permite volver a consultar", async () => {
  let fallar = true;
  fakeBackend({
    "GET /api/cursos-competencias/curso/3": () => (fallar ? reply(500, { message: "Sin conexión" }) : []),
    "GET /api/competencias": page([]),
  });
  render(<PlanCourseCompetencies course={course} onChange={vi.fn()} onClose={vi.fn()} />);
  expect(await screen.findByText("Sin conexión")).toBeTruthy();
  fallar = false;
  await userEvent.click(screen.getByRole("button", { name: "Volver a consultar" }));
  expect(await screen.findByText("Este curso no tiene competencias seleccionadas para el plan.")).toBeTruthy();
});

test("en solo lectura muestra las competencias inactivas y cierra con Escape", async () => {
  fakeBackend({});
  const onClose = vi.fn();
  render(<PlanCourseCompetencies course={{ ...course, competencias: [{ ...comunica, activa: false }] }} onClose={onClose} />);
  expect(screen.getByText("C2 · Inactiva")).toBeTruthy();
  expect(screen.queryByRole("button", { name: /Quitar competencia/ })).toBeNull();
  expect(screen.queryByLabelText("Buscar competencias en el catálogo")).toBeNull();
  await userEvent.keyboard("{Escape}");
  expect(onClose).toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "Cerrar competencias" }));
  expect(onClose).toHaveBeenCalledTimes(2);
});

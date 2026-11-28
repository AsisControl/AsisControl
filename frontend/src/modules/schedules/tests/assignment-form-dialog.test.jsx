// cobertura: errores de carga y validaciones del formulario de asignación docente
import { beforeEach, expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { AssignmentFormDialog } from "../assignments/assignment-form-dialog";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

beforeEach(() => signIn({ permissions: ["ACADEMICO_LEER", "ACADEMICO_ESCRIBIR"] }));

test("informa si fallan los docentes, las secciones o los cursos", async () => {
  fakeBackend({
    "GET /api/docentes": reply(500, { message: "Docentes no disponibles" }),
    "GET /api/secciones": reply(500, { message: "Secciones no disponibles" }),
  });
  const { unmount } = render(<AssignmentFormDialog onClose={vi.fn()} onSaved={vi.fn()} />);
  expect(await screen.findByText(/no disponibles/)).toBeTruthy();
  unmount();
  const anio = new Date().getFullYear();
  fakeBackend({
    "GET /api/docentes": page([]),
    "GET /api/secciones": page([{ id: 8, nombre: "A", grado: 1, anioAcademico: anio }]),
    [`GET /api/planes-estudios/${anio}/1`]: reply(500, { message: "Cursos no disponibles" }),
  });
  render(<AssignmentFormDialog onClose={vi.fn()} onSaved={vi.fn()} />);
  await userEvent.selectOptions(await screen.findByLabelText(/Sección/), "8");
  expect(await screen.findByText("Cursos no disponibles")).toBeTruthy();
});

test("valida el año y los campos obligatorios antes de guardar", async () => {
  const calls = fakeBackend({ "GET /api/docentes": page([]), "GET /api/secciones": page([]) });
  render(<AssignmentFormDialog onClose={vi.fn()} onSaved={vi.fn()} />);
  const anio = screen.getByLabelText(/Año/);
  await userEvent.clear(anio);
  await userEvent.type(anio, "1990");
  await userEvent.click(screen.getByRole("button", { name: /Guardar|Asignar|Crear/ }));
  expect(await screen.findByText("Ingrese un año entre 2000 y 2100.")).toBeTruthy();
  expect(screen.getByText("Seleccione la sección.")).toBeTruthy();
  expect(screen.getByText("Seleccione el curso.")).toBeTruthy();
  expect(screen.getByText("Seleccione el docente.")).toBeTruthy();
  expect(calls.some(c => c.method === "POST")).toBe(false);
});

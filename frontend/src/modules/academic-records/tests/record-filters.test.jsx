import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { Students } from "@/modules/students";
import { GuardiansPanel } from "../family-panels";
import { TeachersPanel } from "../teachers-panel";
import { fakeBackend, page, signIn } from "../../../../tests/support/backend.js";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

beforeEach(() => signIn());

const lastQuery = (calls) => calls.at(-1).query;

it("combina los filtros de estudiantes, los envía y permite limpiarlos", async () => {
  const calls = fakeBackend({ "GET /api/alumnos": page([]) });
  const user = userEvent.setup();
  render(<Students />);
  await screen.findByText("Todavía no hay estudiantes registrados.");
  const clear = screen.getByRole("button", { name: "Limpiar filtros" });
  expect(clear.disabled).toBe(true);
  await user.selectOptions(screen.getByLabelText("Grado matriculado"), "2");
  await user.selectOptions(screen.getByLabelText("Apoderado"), "false");
  await waitFor(() => expect(lastQuery(calls)).toMatchObject({ grado: "2", conApoderado: "false" }));
  expect(await screen.findByText("No se encontraron estudiantes para esta búsqueda.")).toBeTruthy();
  await user.selectOptions(screen.getByLabelText("Matrícula"), "false");
  expect(screen.getByLabelText("Grado matriculado").value).toBe("");
  await waitFor(() => expect(lastQuery(calls).conMatricula).toBe("false"));
  expect(lastQuery(calls).grado).toBeUndefined();
  await user.selectOptions(screen.getByLabelText("Grado matriculado"), "1");
  expect(screen.getByLabelText("Matrícula").value).toBe("");
  await user.click(clear);
  await waitFor(() => expect(lastQuery(calls).grado).toBeUndefined());
  expect(lastQuery(calls).conApoderado).toBeUndefined();
});

it("filtra apoderados según tengan estudiantes", async () => {
  const calls = fakeBackend({ "GET /api/apoderados": page([]) });
  const user = userEvent.setup();
  render(<GuardiansPanel />);
  await user.selectOptions(await screen.findByLabelText("Estudiantes a cargo"), "false");
  await waitFor(() => expect(lastQuery(calls).conEstudiantes).toBe("false"));
});

it.each([
  ["estudiantes", "/api/alumnos", () => <Students />],
  ["apoderados", "/api/apoderados", () => <GuardiansPanel />],
  ["docentes", "/api/docentes", () => <TeachersPanel />],
])("ordena %s por fecha de registro y vuelve al apellido", async (_, path, screenOf) => {
  const calls = fakeBackend({ [`GET ${path}`]: page([]) });
  const user = userEvent.setup();
  render(screenOf());
  const order = await screen.findByLabelText("Ordenar por");
  await user.selectOptions(order, "Registro más reciente");
  await waitFor(() => expect(lastQuery(calls)).toMatchObject({ sortBy: "fechaCreacion", direction: "DESC" }));
  await user.selectOptions(order, "Registro más antiguo");
  await waitFor(() => expect(lastQuery(calls).direction).toBe("ASC"));
  await user.selectOptions(order, "Apellido (A-Z)");
  await waitFor(() => expect(lastQuery(calls).sortBy).toBeUndefined());
});

it("filtra docentes por especialidad", async () => {
  const calls = fakeBackend({ "GET /api/docentes": page([]) });
  const user = userEvent.setup();
  render(<TeachersPanel />);
  await user.selectOptions(await screen.findByLabelText("Especialidad"), "MATEMATICA");
  await waitFor(() => expect(lastQuery(calls).especialidad).toBe("MATEMATICA"));
});

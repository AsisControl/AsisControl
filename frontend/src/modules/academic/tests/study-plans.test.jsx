import { expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page } from "../../../../tests/support/backend.js";
import { StudyPlansPanel } from "../study-plans-panel";

const year = { id: 1, anio: 2027, grados: [{ id: 10, numero: 1, activo: true }] };
const course = { id: 3, nombre: "Matemática", codigo: "MAT" };
const path = "/api/planes-estudios/2027/1";

test("guarda un curso del catálogo y respeta el bloqueo posterior del plan", async () => {
  const calls = fakeBackend({
    "GET /api/configuracion-academica/anios": page([year]),
    [`GET ${path}`]: { editable: true, inicializacion: true, version: 0, cursos: [] },
    "GET /api/cursos": page([course]),
    [`PUT ${path}`]: { editable: false, inicializacion: false, version: 1, cursos: [course] },
  });
  render(<StudyPlansPanel canWrite />);
  const user = userEvent.setup();
  await screen.findByRole("option", { name: "2027" });
  await user.selectOptions(screen.getByLabelText("Año académico"), "2027");
  await user.selectOptions(screen.getByLabelText("Grado"), "1");
  await screen.findByRole("option", { name: "Matemática (MAT)" });
  expect(screen.getByRole("button", { name: "Guardar plan" }).disabled).toBe(true);
  await user.selectOptions(screen.getByLabelText("Agregar curso"), "3");
  await user.click(screen.getByRole("button", { name: "Guardar plan" }));
  await screen.findByText("Se guardó el plan de estudios.");
  expect(calls.find(call => call.method === "PUT").body).toEqual({ version: 0, cursosIds: [3] });
  expect(screen.getByText("Matemática (MAT)")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Guardar plan" })).toBeNull();
});

import { expect, test, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply } from "../../../../tests/support/backend.js";
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

test("descarga el plan del año entero sin depender del grado seleccionado", async () => {
  const calls = fakeBackend({
    "GET /api/configuracion-academica/anios": page([year]),
    "GET /api/planes-estudios/2027/pdf": {},
  });
  const originalCreate = URL.createObjectURL;
  const originalRevoke = URL.revokeObjectURL;
  URL.createObjectURL = vi.fn(() => "blob:plan");
  URL.revokeObjectURL = vi.fn();
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  try {
    render(<StudyPlansPanel canWrite={false} />);
    const user = userEvent.setup();
    const button = screen.getByRole("button", { name: /Descargar plan de estudios/ });
    expect(button.disabled).toBe(true);
    await screen.findByRole("option", { name: "2027" });
    await user.selectOptions(screen.getByLabelText("Año académico"), "2027");
    await user.click(button);
    await waitFor(() => expect(click.mock.instances[0]?.download).toBe("Plan_de_Estudios_AsisControl_2027.pdf"));
    expect(calls.at(-1).path).toBe("/api/planes-estudios/2027/pdf");
  } finally {
    click.mockRestore();
    URL.createObjectURL = originalCreate;
    URL.revokeObjectURL = originalRevoke;
  }
});

test("avisa el error de descarga y conserva el año para reintentar", async () => {
  fakeBackend({
    "GET /api/configuracion-academica/anios": page([year]),
    "GET /api/planes-estudios/2027/pdf": reply(500, { message: "No se pudo generar el PDF." }),
  });
  render(<StudyPlansPanel canWrite />);
  const user = userEvent.setup();
  await screen.findByRole("option", { name: "2027" });
  await user.selectOptions(screen.getByLabelText("Año académico"), "2027");
  const button = screen.getByRole("button", { name: /Descargar plan de estudios/ });
  await user.click(button);
  expect((await screen.findByRole("alert")).textContent).toContain("No se pudo generar el PDF.");
  expect(button.disabled).toBe(false);
});

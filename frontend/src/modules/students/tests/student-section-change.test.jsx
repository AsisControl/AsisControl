import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { StudentDetail } from "../student-detail";
import { fakeBackend, page, signIn } from "../../../../tests/support/backend.js";

const student = {
  id: 42,
  codigoAlumno: "EST-042",
  activo: true,
  apoderados: [],
  accionesPermitidas: {},
  persona: { id: 17, nombres: "Lucía", apellidoPaterno: "Flores", nombreCompleto: "Lucía Flores" },
};

const enrollment = (overrides = {}) => ({
  id: 200, alumno: "Lucía Flores", codigoAlumno: "EST-042", seccionId: 31, seccion: "A",
  grado: 1, anioAcademico: 2026, estado: "ACTIVA", version: 3, ...overrides,
});

const year = {
  id: 7, anio: 2026,
  secciones: [
    { id: 31, nombre: "A", grado: 1, activo: true, vacantes: 5 },
    { id: 32, nombre: "B", grado: 1, activo: true, vacantes: 2 },
  ],
};

const renderDetail = () =>
  render(<StudentDetail student={student} onClose={vi.fn()} onEdit={vi.fn()} onChanged={vi.fn()} />);

beforeEach(() => signIn());

it("cambia de sección desde la ficha y muestra la nueva asignación", async () => {
  let current = enrollment();
  const calls = fakeBackend({
    "GET /api/matriculas": () => page([current, enrollment({ id: 100, estado: "FINALIZADA", anioAcademico: 2025 })]),
    "GET /api/configuracion-academica/anios/:anio": year,
    "PUT /api/matriculas/:id/seccion": () => (current = enrollment({ seccionId: 32, seccion: "B", version: 4 })),
  });
  const user = userEvent.setup();
  renderDetail();
  expect(await screen.findByText("1° A")).toBeTruthy();
  expect(screen.getAllByRole("button", { name: "Cambiar sección" })).toHaveLength(1);
  await user.click(screen.getByRole("button", { name: "Cambiar sección" }));
  const dialog = screen.getByRole("dialog", { name: "Cambiar sección" });
  const section = within(dialog).getByLabelText(/Nueva sección/);
  await waitFor(() => expect(section.options.length).toBe(2));
  await user.selectOptions(section, "32");
  await user.type(within(dialog).getByLabelText(/Motivo del cambio/), "Redistribución");
  await user.click(within(dialog).getByRole("button", { name: "Confirmar cambio" }));
  expect(await screen.findByText("1° B")).toBeTruthy();
  expect(calls.find((call) => call.method === "PUT").body).toEqual({
    seccionId: 32, motivo: "Redistribución", version: 3,
  });
  expect(calls.find((call) => call.path === "/api/matriculas").query.alumnoId).toBe("42");
});

it("avisa cuando el estudiante no tiene matrícula activa", async () => {
  fakeBackend({ "GET /api/matriculas": page([]) });
  renderDetail();
  expect(await screen.findByText("No tiene una matrícula activa.")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Cambiar sección" })).toBeNull();
});

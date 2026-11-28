import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { Students } from "../students";
import { fakeBackend, page, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const student = {
  id: 42, codigoAlumno: "EST-042", activo: true, apoderados: [], accionesPermitidas: {},
  persona: { id: 17, nombres: "Lucía", apellidoPaterno: "Flores", nombreCompleto: "Lucía Flores" },
};
const destination = { id: 32, nombre: "C", grado: 1, anioAcademico: 2026 };
const enrollment = (overrides = {}) => ({
  id: 200, alumno: "Lucía Flores", codigoAlumno: "EST-042", seccionId: 31, seccion: "A",
  grado: 1, anioAcademico: 2026, estado: "ACTIVA", version: 3, ...overrides,
});

function backend(enrollments) {
  return fakeBackend({
    "GET /api/alumnos": page([student]),
    "GET /api/alumnos/:id": student,
    "GET /api/secciones/:id": destination,
    "GET /api/matriculas": page(enrollments),
    "GET /api/configuracion-academica/anios/:anio": {
      secciones: [
        { id: 31, nombre: "A", grado: 1, activo: true, vacantes: 5 },
        { id: 32, nombre: "C", grado: 1, activo: true, vacantes: 4 },
      ],
    },
  });
}

async function openRecord(user) {
  render(<Students />);
  expect((await screen.findByText(/Agregando alumnos a/)).textContent).toContain("1° C · 2026");
  await user.click(await screen.findByRole("button", { name: /Ver ficha de/ }));
  return screen.findByRole("dialog", { name: "Ficha del estudiante" });
}

beforeEach(() => {
  signIn();
  setUrl("/matricula/fichas/estudiantes?seccion=32");
});

it("ofrece cambiar a la sección de destino y la deja preseleccionada", async () => {
  backend([enrollment()]);
  const user = userEvent.setup();
  const record = await openRecord(user);
  await user.click(await within(record).findByRole("button", { name: "Cambiar a la sección C" }));
  const section = await screen.findByLabelText(/Nueva sección/);
  await waitFor(() => expect(section.value).toBe("32"));
});

it.each([
  [enrollment({ seccionId: 32, seccion: "C" }), "Ya está en la sección C"],
  [enrollment({ grado: 2 }), "La sección C es de 1°: no se puede cambiar de grado"],
])("explica por qué no ofrece el cambio (%#)", async (current, text) => {
  backend([current]);
  const record = await openRecord(userEvent.setup());
  expect(await within(record).findByText(text)).toBeTruthy();
  expect(within(record).queryByRole("button", { name: /Cambiar/ })).toBeNull();
});

it("sin matrícula en el año ofrece matricular en la sección de destino", async () => {
  backend([]);
  const record = await openRecord(userEvent.setup());
  expect(await within(record).findByRole("button", { name: "Matricular en la sección C" })).toBeTruthy();
});

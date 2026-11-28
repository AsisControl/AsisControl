import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { StudentDetail } from "../student-detail";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { paymentPlan } from "../../enrollment/tests/payment-plan-fixture";

const student = {
  id: 42, codigoAlumno: "EST-042", activo: true, apoderados: [], accionesPermitidas: {},
  persona: { id: 17, nombres: "Mía", apellidoPaterno: "Cárdenas", nombreCompleto: "Mía Cárdenas" },
};

const year = {
  id: 7, anio: 2026, estado: "ACTIVO",
  grados: [{ id: 1, numero: 1, activo: true }],
  secciones: [
    { id: 31, nombre: "A", grado: 1, activo: true, vacantes: 5 },
    { id: 32, nombre: "B", grado: 1, activo: true, vacantes: 0 },
  ],
};

function backend(routes = {}) {
  let enrolled = false;
  return fakeBackend({
    "GET /api/matriculas": () => page(enrolled ? [{
      id: 200, seccionId: 31, seccion: "A", grado: 1, anioAcademico: 2026, estado: "ACTIVA", version: 0,
    }] : []),
    "GET /api/configuracion-academica/anios": page([year, { ...year, id: 6, anio: 2025, estado: "CERRADO" }]),
    "GET /api/configuracion-pagos/planes/alumnos/:id/preview": paymentPlan(),
    "POST /api/matriculas/directas": () => { enrolled = true; return reply(201, { codigoMatricula: "MAT-2026-0009" }); },
    ...routes,
  });
}

const renderDetail = (props = {}) =>
  render(<StudentDetail student={student} onClose={vi.fn()} {...props} />);

beforeEach(() => signIn());

it("matricula desde la ficha eligiendo año, grado y sección, y muestra la matrícula", async () => {
  const calls = backend();
  const user = userEvent.setup();
  renderDetail();
  await user.click(await screen.findByRole("button", { name: "Matricular" }));
  const dialog = screen.getByRole("dialog", { name: "Matricular estudiante" });
  const confirm = () => within(dialog).getByRole("button", { name: "Confirmar matrícula" });
  const grade = await within(dialog).findByLabelText(/^Grado/);
  expect([...within(dialog).getByLabelText(/Año académico activo/).options]).toHaveLength(1);
  await user.selectOptions(grade, "1");
  const section = within(dialog).getByLabelText(/^Sección/);
  expect([...section.options].map((option) => option.disabled)).toEqual([true, false, true]);
  await within(dialog).findByRole("table", { name: "Pensiones que se asignarán" });
  expect(confirm().disabled).toBe(true);
  await user.selectOptions(section, "31");
  await user.click(confirm());
  expect(await within(dialog).findByText(/Matrícula MAT-2026-0009 creada/)).toBeTruthy();
  expect(calls.find((call) => call.method === "POST").body).toEqual({
    alumnoId: 42, seccionId: 31, firmaPlan: "plan-v2",
  });
  await user.click(within(dialog).getByRole("button", { name: "Volver a la ficha" }));
  expect(await screen.findByText("1° A")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Matricular" })).toBeNull();
});

it("desde una sección de destino llega con todo elegido y muestra el rechazo del servidor", async () => {
  backend({ "POST /api/matriculas/directas": reply(422, { code: "RENOVACION_REQUERIDA", message: "Usa Renovación anual." }) });
  const user = userEvent.setup();
  renderDetail({ destination: { id: 31, nombre: "A", grado: 1, anioAcademico: 2026 } });
  await user.click(await screen.findByRole("button", { name: "Matricular en la sección A" }));
  const dialog = screen.getByRole("dialog");
  const confirm = await within(dialog).findByRole("button", { name: "Confirmar matrícula" });
  await waitFor(() => expect(confirm.disabled).toBe(false));
  expect(within(dialog).getByLabelText(/^Sección/).value).toBe("31");
  await user.click(confirm);
  expect((await within(dialog).findByRole("alert")).textContent).toBe("Usa Renovación anual.");
  await user.click(within(dialog).getByRole("button", { name: "Cancelar" }));
  expect(screen.getByRole("dialog", { name: "Ficha del estudiante" })).toBeTruthy();
});

it("sin tarifa no permite confirmar y deja volver a consultar el plan", async () => {
  let configured = false;
  backend({
    "GET /api/configuracion-pagos/planes/alumnos/:id/preview": () => configured
      ? paymentPlan()
      : paymentPlan({ puedeGenerar: false, items: [], mensaje: "Configura la tarifa." }),
  });
  const user = userEvent.setup();
  renderDetail();
  await user.click(await screen.findByRole("button", { name: "Matricular" }));
  const dialog = screen.getByRole("dialog");
  await user.selectOptions(await within(dialog).findByLabelText(/^Grado/), "1");
  await user.selectOptions(within(dialog).getByLabelText(/^Sección/), "31");
  expect(await within(dialog).findByText("Configura la tarifa.")).toBeTruthy();
  expect(within(dialog).getByRole("button", { name: "Confirmar matrícula" }).disabled).toBe(true);
  configured = true;
  await user.click(within(dialog).getByRole("button", { name: "Actualizar plan de pensiones" }));
  await within(dialog).findByRole("table", { name: "Pensiones que se asignarán" });
  expect(within(dialog).getByRole("button", { name: "Confirmar matrícula" }).disabled).toBe(false);
});

it("no ofrece matricular a un estudiante inactivo", async () => {
  backend();
  render(<StudentDetail student={{ ...student, activo: false }} onClose={vi.fn()} />);
  expect(await screen.findByText("No tiene una matrícula activa.")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Matricular" })).toBeNull();
});

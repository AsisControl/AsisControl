// cobertura: matrícula directa desde la ficha del estudiante
import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { DirectEnrollmentDialog } from "../direct-enrollment-dialog";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const anio = {
  id: 1, anio: 2026, estado: "ACTIVO",
  grados: [{ id: 10, numero: 1, activo: true }, { id: 11, numero: 2, activo: true }],
  secciones: [
    { id: 8, nombre: "A", grado: 1, activo: true, vacantes: 2 },
    { id: 9, nombre: "B", grado: 2, activo: true, vacantes: 0 },
  ],
};
const plan = { idAlumno: 7, anioAcademico: 2026, grado: 1, puedeGenerar: true, firmaPlan: "firma-1", montoMensual: 300, cuotas: [], mensaje: null };
const alumno = { id: 7, nombre: "Lucía Prado", codigo: "E7" };
const PLAN = "GET /api/configuracion-pagos/planes/alumnos/7/preview";

beforeEach(() => signIn({ permissions: ["MATRICULA_ESCRIBIR", "PAGOS_LEER"] }));

test("si el plan cambió lo vuelve a consultar y luego matricula", async () => {
  let intentos = 0;
  const calls = fakeBackend({
    "GET /api/configuracion-academica/anios": page([anio, { ...anio, id: 2, anio: 2025, estado: "CERRADO" }]),
    [PLAN]: plan,
    "POST /api/matriculas/directas": () => (++intentos === 1
      ? reply(409, { code: "PLAN_ACTUALIZADO", message: "El plan de pensiones cambió" }) : { codigoMatricula: "MAT-1" }),
  });
  const onEnrolled = vi.fn();
  const user = userEvent.setup();
  render(<DirectEnrollmentDialog student={alumno} destination={{ id: 8, grado: 1, anioAcademico: 2026 }}
    onClose={vi.fn()} onEnrolled={onEnrolled} />);
  expect(screen.queryByRole("option", { name: "2025" })).toBeNull();
  await screen.findByRole("option", { name: "2026" });
  const confirmar = await screen.findByRole("button", { name: "Confirmar matrícula" });
  await waitFor(() => expect(confirmar.disabled).toBe(false));
  await user.click(confirmar);
  expect(await screen.findByText("El plan de pensiones cambió")).toBeTruthy();
  await waitFor(() => expect(screen.getByRole("button", { name: "Confirmar matrícula" }).disabled).toBe(false));
  await user.click(screen.getByRole("button", { name: "Confirmar matrícula" }));
  expect(await screen.findByText("Matrícula MAT-1 creada con su plan de pensiones.")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Volver a la ficha" }));
  expect(onEnrolled).toHaveBeenCalled();
  expect(calls.filter(c => c.path.endsWith("/preview")).length).toBeGreaterThan(1);
});

test("avisa si no hay vacantes, si no se puede generar el plan o si fallan los datos", async () => {
  let generar = false;
  fakeBackend({
    "GET /api/configuracion-academica/anios": page([anio]),
    [PLAN]: () => (generar ? plan : { ...plan, puedeGenerar: false, firmaPlan: null, mensaje: "Falta la tarifa del grado" }),
  });
  const user = userEvent.setup();
  const { unmount } = render(<DirectEnrollmentDialog student={alumno} onClose={vi.fn()} onEnrolled={vi.fn()} />);
  await screen.findByRole("option", { name: "2026" });
  await user.selectOptions(screen.getByLabelText(/Grado/), "2");
  expect(await screen.findByText("No hay secciones con vacantes en 2°.")).toBeTruthy();
  expect(await screen.findByText("Falta la tarifa del grado")).toBeTruthy();
  generar = true;
  await user.click(screen.getByRole("button", { name: "Actualizar plan de pensiones" }));
  await waitFor(() => expect(screen.queryByRole("button", { name: "Actualizar plan de pensiones" })).toBeNull());
  unmount();
  fakeBackend({ "GET /api/configuracion-academica/anios": reply(500, { message: "Años no disponibles" }) });
  render(<DirectEnrollmentDialog student={alumno} onClose={vi.fn()} onEnrolled={vi.fn()} />);
  expect(await screen.findByText("Años no disponibles")).toBeTruthy();
});

test("sin año académico activo lo indica", async () => {
  fakeBackend({ "GET /api/configuracion-academica/anios": page([{ ...anio, estado: "CERRADO" }]) });
  render(<DirectEnrollmentDialog student={alumno} onClose={vi.fn()} onEnrolled={vi.fn()} />);
  expect(await screen.findByText("No hay un año académico activo. Actívalo en Administración.")).toBeTruthy();
});

import { beforeEach, expect, test, vi } from "vitest";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import { fakeBackend, reply, signIn } from "../../../../tests/support/backend.js";
import { EnrollmentPaymentPreview } from "../enrollment-payment-preview";
import { paymentPlan } from "./payment-plan-fixture";

beforeEach(() => signIn());
const PATH = "GET /api/configuracion-pagos/planes/solicitudes/100/preview";

test("Muestra diez mensualidades, becas y total sin presentar una matrícula como un cobro", async () => {
  const plan = paymentPlan(); const notify = vi.fn();
  fakeBackend({ [PATH]: plan });
  render(<EnrollmentPaymentPreview requestId={100} revision={0} onPlan={notify} />);
  const table = await screen.findByRole("table", { name: "Pensiones que se asignarán" });
  expect(within(table).getAllByRole("row")).toHaveLength(11);
  expect(within(table).getAllByText("Beca de estudios")).toHaveLength(10);
  expect(screen.getByText(/Total de las diez pensiones: S\/\s3,000\.00/)).toBeTruthy();
  expect(screen.getByText(/no realiza un cobro automático/)).toBeTruthy();
  expect(notify).toHaveBeenLastCalledWith(plan);
});

test("Presenta exoneraciones y un plan que requiere revisión sin autorizar su emisión", async () => {
  const notify = vi.fn(), base = paymentPlan();
  const plan = paymentPlan({ puedeGenerar: false, mensaje: "Revisa las pensiones anteriores antes de generar otras.",
    items: base.items.map((item, index) => index === 0 ? { ...item, montoTotal: 0, montoDescuento: 400 } : { ...item, beneficioNombre: null, montoDescuento: 0, montoTotal: 400 }) });
  fakeBackend({ [PATH]: plan });
  render(<EnrollmentPaymentPreview requestId={100} revision={0} onPlan={notify} />);
  expect(await screen.findByText("Revisa las pensiones anteriores antes de generar otras.")).toBeTruthy();
  expect(screen.getByText("Exonerado")).toBeTruthy();
  expect(screen.getAllByText("Sin beneficio")).toHaveLength(9);
  expect(notify).toHaveBeenLastCalledWith(plan);
});

test("Descarta respuestas de una consulta cerrada y conserva el bloqueo mientras se consulta", async () => {
  let finish; const notify = vi.fn();
  fakeBackend({ [PATH]: () => new Promise((resolve) => { finish = resolve; }) });
  const view = render(<EnrollmentPaymentPreview requestId={100} revision={0} onPlan={notify} />);
  await screen.findByRole("status");
  await waitFor(() => expect(finish).toBeTypeOf("function"));
  expect(notify).toHaveBeenLastCalledWith(null);
  view.unmount();
  await act(async () => { finish(paymentPlan()); });
  expect(notify).toHaveBeenCalledTimes(1);
});

test("Informa un fallo de conexión sin devolver un plan utilizable", async () => {
  const notify = vi.fn(); fakeBackend({ [PATH]: reply(503, { message: "No pudimos consultar las pensiones." }) });
  render(<EnrollmentPaymentPreview requestId={100} revision={0} onPlan={notify} />);
  expect(await screen.findByText("No pudimos consultar las pensiones.")).toBeTruthy();
  expect(notify).toHaveBeenLastCalledWith(null);
});

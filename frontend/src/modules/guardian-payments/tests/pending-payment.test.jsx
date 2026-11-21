import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { GuardianPayments } from "../guardian-payments";
import { PaymentCheckout } from "../payment-checkout";
import { obligation, order, student } from "./fixtures";
import { payWithCard } from "./card-form-support";

beforeEach(() => signIn({ role: "APODERADO", permissions: ["PAGOS_LEER"] }));
afterEach(() => vi.useRealTimers());
const backend = (routes = {}) => fakeBackend({
  "GET /api/apoderado/obligaciones/estudiantes": [student],
  "GET /api/apoderado/obligaciones": page([obligation()]),
  ...routes,
});

test("una cuota con pago en curso lleva al seguimiento y continúa el QR original sin crear otra solicitud", async () => {
  const conflictMessage = "Una o más cuotas tienen un pago en curso. Revisa Seguimiento de pagos o espera a que termine su vigencia antes de intentarlo nuevamente.";
  const existing = order({ metodo: "QR", qrUrl: "http://frontend.test/pagar-qr/capacidad" });
  const calls = backend({
    "POST /api/pagos/iniciar": reply(409, { code: "PAGO_EN_CURSO", message: conflictMessage }),
    "GET /api/pagos/ordenes": page([existing]),
  });
  const user = userEvent.setup(); render(<GuardianPayments />);
  await user.click(await screen.findByRole("checkbox", { name: "Seleccionar CP-000001 de Lucía Flores" }));
  await user.click(screen.getByRole("button", { name: "Pagar seleccionadas" }));
  await user.click(screen.getByRole("button", { name: "Continuar con tarjeta" }));
  const warning = await screen.findByRole("alertdialog", { name: "Pago en curso" });
  expect(within(warning).getByText(/Ya hay un pago en curso para uno o más cargos seleccionados/)).toBeTruthy();
  expect(screen.queryByRole("tab", { name: "Tarjeta" })).toBeNull();
  await user.click(within(warning).getByRole("button", { name: "Ir a Seguimiento de pagos" }));
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(screen.queryByRole("alertdialog")).toBeNull();
  expect(screen.getByRole("tab", { name: "Seguimiento de pagos" }).getAttribute("aria-selected")).toBe("true");
  await user.click(await screen.findByRole("button", { name: "Continuar pago" }));
  expect(await screen.findByRole("link", { name: "Abrir billetera en esta pantalla" })).toBeTruthy();
  expect(screen.getByText(/Retomaste el pago OP-000010/)).toBeTruthy();
  expect(screen.getByText(/Conserva su medio de pago y el tiempo disponible/)).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Continuar con tarjeta" })).toBeNull();
  expect(calls.filter((call) => call.method === "POST")).toHaveLength(1);
  await user.click(screen.getByRole("button", { name: "Cerrar", exact: true }));
  await user.click(screen.getByRole("tab", { name: "Obligaciones", exact: true }));
  expect(screen.getByRole("button", { name: "Pagar seleccionadas" }).disabled).toBe(true);
});

test("volver al pago conserva el medio seleccionado y devuelve el foco al botón para iniciar", async () => {
  const calls = backend({
    "POST /api/pagos/iniciar": reply(409, { code: "PAGO_EN_CURSO", message: "Ya hay una operación vigente." }),
  });
  const user = userEvent.setup();
  const follow = vi.fn();
  render(<PaymentCheckout obligations={[obligation()]} onClose={vi.fn()} onSettled={vi.fn()} onFollowPayments={follow} />);
  await user.click(screen.getByRole("tab", { name: "Código QR" }));
  const start = screen.getByRole("button", { name: "Generar código QR" });
  await user.click(start);
  const warning = await screen.findByRole("alertdialog", { name: "Pago en curso" });
  await user.click(within(warning).getByRole("button", { name: "Volver al pago" }));
  expect(screen.queryByRole("alertdialog")).toBeNull();
  expect(screen.getByRole("tab", { name: "Código QR", selected: true })).toBeTruthy();
  await waitFor(() => expect(document.activeElement).toBe(start));
  expect(calls.filter((call) => call.method === "POST")).toHaveLength(1);
  expect(follow).not.toHaveBeenCalled();
});

test("un QR que vence abierto en seguimiento vuelve a Obligaciones y permite iniciar una orden nueva", async () => {
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval", "Date"] });
  const pending = order({
    metodo: "QR",
    expiraEn: new Date(Date.now() + 1500).toISOString(),
    qrUrl: "http://frontend.test/pagar-qr/capacidad",
  });
  const expired = { ...pending, estado: "EXPIRADO", qrUrl: null, mensaje: "El tiempo para este pago terminó." };
  let expiredOnServer = false;
  const calls = backend({
    "GET /api/pagos/ordenes": () => page(expiredOnServer ? [] : [pending]),
    "GET /api/apoderado/pagos/historial": () => page(expiredOnServer ? [{
      id: "orden-10-cuota-1", codigo: pending.codigo, codigoOrden: pending.codigo, alumno: "Lucía Flores",
      codigoCompromiso: "CP-000001", concepto: "Pensión", numeroCuota: 1, medioPago: "QR",
      montoTotal: 350, moneda: "PEN", estado: "VENCIDO", fechaEmision: pending.creadaEn, mensaje: expired.mensaje,
    }] : []),
    "GET /api/pagos/ordenes/10": () => { expiredOnServer = true; return expired; },
    "POST /api/pagos/iniciar": order({ id: 11, codigo: "OP-000011" }),
  });
  const user = userEvent.setup();
  render(<GuardianPayments />);
  await user.click(screen.getByRole("tab", { name: "Seguimiento de pagos" }));
  await user.click(await screen.findByRole("button", { name: "Continuar pago" }));
  expect(screen.getByRole("link", { name: "Abrir billetera en esta pantalla" })).toBeTruthy();
  await act(async () => vi.advanceTimersByTimeAsync(2000));
  const warning = await screen.findByRole("alertdialog", { name: "Se acabó el tiempo para pagar" });
  await user.click(within(warning).getByRole("button", { name: "Volver a Obligaciones" }));
  expect(screen.getByRole("tab", { name: "Obligaciones", selected: true })).toBeTruthy();

  await user.click(screen.getByRole("tab", { name: "Seguimiento de pagos" }));
  expect(await screen.findByText("No hay órdenes en curso para la selección.")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Continuar pago" })).toBeNull();
  expect(screen.queryByText(expired.mensaje)).toBeNull();

  await user.click(screen.getByRole("tab", { name: "Historial de pagos" }));
  const history = await screen.findByRole("table", { name: "Historial de mis pagos" });
  expect(within(history).getByText("Vencido")).toBeTruthy();

  await user.click(screen.getByRole("tab", { name: "Obligaciones" }));
  await user.click(await screen.findByRole("checkbox", { name: "Seleccionar CP-000001 de Lucía Flores" }));
  await user.click(screen.getByRole("button", { name: "Pagar seleccionadas" }));
  await user.click(screen.getByRole("button", { name: "Continuar con tarjeta" }));
  expect(await screen.findByLabelText("Número de tarjeta")).toBeTruthy();
  expect(calls.filter((call) => call.method === "POST").map((call) => call.path)).toEqual(["/api/pagos/iniciar"]);
});

test("un doble clic mientras se prepara el pago envía una sola solicitud", async () => {
  let finish;
  const calls = backend({ "POST /api/pagos/iniciar": () => new Promise((resolve) => { finish = resolve; }) });
  const user = userEvent.setup();
  render(<PaymentCheckout obligations={[obligation()]} onClose={vi.fn()} onSettled={vi.fn()} />);
  await user.dblClick(screen.getByRole("button", { name: "Continuar con tarjeta" }));
  await waitFor(() => expect(calls.filter((call) => call.method === "POST")).toHaveLength(1));
  expect(screen.getByRole("button", { name: "Preparando…" }).disabled).toBe(true);
  await act(async () => { finish(order()); });
  expect((await payWithCard()).disabled).toBe(true);
  expect(calls.filter((call) => call.method === "POST")).toHaveLength(1);
});

test("un error temporal no se presenta como un conflicto de pago pendiente", async () => {
  backend({ "POST /api/pagos/iniciar": reply(503, { code: "SERVICIO_NO_DISPONIBLE", message: "No pudimos preparar el pago. Inténtalo nuevamente." }) });
  const user = userEvent.setup(), follow = vi.fn();
  render(<PaymentCheckout obligations={[obligation()]} onClose={vi.fn()} onSettled={vi.fn()} onFollowPayments={follow} />);
  await user.click(screen.getByRole("button", { name: "Continuar con tarjeta" }));
  expect(await screen.findByText("No pudimos preparar el pago. Inténtalo nuevamente.")).toBeTruthy();
  expect(screen.queryByRole("alertdialog")).toBeNull();
  expect(screen.queryByRole("button", { name: "Ir a Seguimiento de pagos" })).toBeNull();
  expect(follow).not.toHaveBeenCalled();
});

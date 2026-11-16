import { beforeEach, expect, test, vi } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { GuardianPayments } from "../guardian-payments";
import { PaymentCheckout } from "../payment-checkout";
import { obligation, order, student } from "./fixtures";

beforeEach(() => signIn({ role: "APODERADO", permissions: ["PAGOS_LEER"] }));
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
  expect(await screen.findByText(conflictMessage)).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Ir a Seguimiento de pagos" }));
  expect(screen.queryByRole("dialog")).toBeNull();
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

test("un doble clic mientras se prepara el pago envía una sola solicitud", async () => {
  let finish;
  const calls = backend({ "POST /api/pagos/iniciar": () => new Promise((resolve) => { finish = resolve; }) });
  const user = userEvent.setup();
  render(<PaymentCheckout obligations={[obligation()]} onClose={vi.fn()} onSettled={vi.fn()} />);
  await user.dblClick(screen.getByRole("button", { name: "Continuar con tarjeta" }));
  await waitFor(() => expect(calls.filter((call) => call.method === "POST")).toHaveLength(1));
  expect(screen.getByRole("button", { name: "Preparando…" }).disabled).toBe(true);
  await act(async () => { finish(order()); });
  expect(await screen.findByLabelText("Resultado de autorización")).toBeTruthy();
  expect(calls.filter((call) => call.method === "POST")).toHaveLength(1);
});

test("un error temporal no se presenta como un conflicto de pago pendiente", async () => {
  backend({ "POST /api/pagos/iniciar": reply(503, { code: "SERVICIO_NO_DISPONIBLE", message: "No pudimos preparar el pago. Inténtalo nuevamente." }) });
  const user = userEvent.setup(), follow = vi.fn();
  render(<PaymentCheckout obligations={[obligation()]} onClose={vi.fn()} onSettled={vi.fn()} onFollowPayments={follow} />);
  await user.click(screen.getByRole("button", { name: "Continuar con tarjeta" }));
  expect(await screen.findByText("No pudimos preparar el pago. Inténtalo nuevamente.")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Ir a Seguimiento de pagos" })).toBeNull();
  expect(follow).not.toHaveBeenCalled();
});

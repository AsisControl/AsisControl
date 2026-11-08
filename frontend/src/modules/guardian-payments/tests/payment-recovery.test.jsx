import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { GuardianPayments } from "../guardian-payments";
import { PaymentCheckout } from "../payment-checkout";
import { ReceiptPanel } from "../receipt-panel";
import { paymentRequestKey } from "../payment-request-key";
import { complete, obligation, order, receipt, student } from "./fixtures";

const OBLIGATIONS = "GET /api/apoderado/obligaciones";
beforeEach(() => signIn({ role: "APODERADO", permissions: ["PAGOS_LEER"] }));
afterEach(() => vi.useRealTimers());
const backend = (routes = {}) => fakeBackend({ "GET /api/apoderado/obligaciones/estudiantes": [student], [OBLIGATIONS]: page([obligation()]), ...routes });

test("las pestañas permiten navegación por teclado y mantienen el panel asociado", async () => {
  backend({ "GET /api/apoderado/comprobantes": page([]), "GET /api/pagos/ordenes": page([]) });
  const user = userEvent.setup(); render(<GuardianPayments />);
  const obligationsTab = screen.getByRole("tab", { name: "Obligaciones" });
  obligationsTab.focus(); await user.keyboard("{ArrowRight}");
  expect(await screen.findByText("Todavía no tienes pagos registrados.")).toBeTruthy();
  const historyTab = screen.getByRole("tab", { name: "Historial de pagos" });
  expect(document.activeElement).toBe(historyTab);
  expect(screen.getByRole("tabpanel").getAttribute("aria-labelledby")).toBe(historyTab.id);
  await user.keyboard("{ArrowLeft}");
  expect(document.activeElement).toBe(obligationsTab);
  await user.keyboard("{End}"); expect(document.activeElement).toBe(screen.getByRole("tab", { name: "Seguimiento de pagos" }));
  await user.keyboard("{Home}"); expect(document.activeElement).toBe(obligationsTab);
  await user.keyboard("a");
  expect(screen.getByRole("tab", { name: "Obligaciones" }).getAttribute("aria-selected")).toBe("true");
});

test("distingue cuotas vencidas, anuladas y parcialmente pagadas y permite quitar la selección", async () => {
  backend({ [OBLIGATIONS]: page([
    obligation(1, { saldo: 300, montoPagado: 50, estadoFinanciero: "PARCIAL" }),
    obligation(2, { saldo: 350, estadoFinanciero: "VENCIDO" }),
    obligation(3, { saldo: 0, estadoAdministrativo: "ANULADO", estadoFinanciero: "ANULADO" }),
  ]) });
  const user = userEvent.setup(); render(<GuardianPayments />);
  await screen.findByRole("button", { name: "Ver obligación CP-000001" });
  expect(screen.getByRole("checkbox", { name: "Seleccionar CP-000003 de Lucía Flores" }).disabled).toBe(true);
  await user.click(screen.getByRole("checkbox", { name: "Seleccionar obligaciones de esta página" }));
  expect(screen.getByText("2 obligaciones seleccionadas")).toBeTruthy();
  await user.click(screen.getByRole("checkbox", { name: "Seleccionar obligaciones de esta página" }));
  expect(screen.getByRole("button", { name: "Pagar seleccionadas" }).disabled).toBe(true);
  const partial = screen.getByRole("checkbox", { name: "Seleccionar CP-000001 de Lucía Flores" });
  await user.click(partial); expect(screen.getByText("1 obligación seleccionada")).toBeTruthy();
  await user.click(partial); expect(screen.getByRole("button", { name: "Pagar seleccionadas" }).disabled).toBe(true);
  await user.selectOptions(screen.getByLabelText("Estudiante", { exact: true }), "3");
  await user.click(screen.getByRole("button", { name: "Buscar", exact: true }));
  await user.selectOptions(screen.getByLabelText("Estudiante", { exact: true }), "");
  await user.click(screen.getByRole("button", { name: "Buscar", exact: true }));
  expect(screen.getByRole("checkbox", { name: "Seleccionar CP-000003 de Lucía Flores" }).disabled).toBe(true);
});

test("el historial puede recuperarse de un fallo de consulta y mostrar una lista vacía", async () => {
  let failed = true;
  backend({ "GET /api/apoderado/comprobantes": () => failed ? reply(503, { message: "Historial temporalmente no disponible" }) : page([]) });
  const user = userEvent.setup(); render(<GuardianPayments />);
  await user.click(screen.getByRole("tab", { name: "Historial de pagos" }));
  await screen.findByText("Historial temporalmente no disponible"); failed = false;
  await user.click(screen.getByRole("button", { name: "Actualizar historial" }));
  expect(await screen.findByText("Todavía no tienes pagos registrados.")).toBeTruthy();
});

test("retoma una orden desde el historial y actualiza obligaciones e historial al aprobar", async () => {
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
  let paid = false;
  const calls = backend({ "GET /api/pagos/ordenes": () => page([paid ? complete() : order()]),
    "GET /api/pagos/ordenes/10": () => { paid = true; return complete(); },
    "GET /api/pagos/ordenes/10/comprobante": receipt() });
  const user = userEvent.setup(); render(<GuardianPayments />);
  await user.click(screen.getByRole("tab", { name: "Seguimiento de pagos" }));
  await user.click(await screen.findByRole("button", { name: "Continuar pago" }));
  await act(async () => vi.advanceTimersByTimeAsync(2000));
  expect(await screen.findByText("Referencias: REC-000022")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Cerrar", exact: true }));
  expect(await screen.findByRole("button", { name: "Ver comprobante" })).toBeTruthy();
  expect(calls.filter((call) => call.path === "/api/apoderado/obligaciones").length).toBeGreaterThan(1);
});

test("elige QR antes de crear la orden y no abre enlaces con protocolos inesperados", async () => {
  const calls = backend({ "POST /api/pagos/iniciar": order({ metodo: "QR", qrUrl: "javascript:alert(1)" }) });
  const user = userEvent.setup(); render(<PaymentCheckout obligations={[obligation()]} onClose={vi.fn()} onSettled={vi.fn()} />);
  await user.click(screen.getByRole("tab", { name: "Código QR" }));
  await user.click(screen.getByRole("button", { name: "Generar código QR" }));
  expect(await screen.findByText("No pudimos preparar el enlace del QR. Cierra la ventana e inténtalo nuevamente.")).toBeTruthy();
  expect(screen.queryByRole("link", { name: "Abrir billetera en esta pantalla" })).toBeNull();
  expect(calls.find((call) => call.method === "POST").body).toEqual({ obligacionesIds: [1], metodo: "QR" });
});

test("cancelar la autenticación adicional conserva el rechazo sin generar comprobante", async () => {
  const calls = backend({ "POST /api/pagos/ordenes/10/autenticar": order({ estado: "RECHAZADO", mensaje: "Autenticación cancelada" }) });
  const user = userEvent.setup(); render(<PaymentCheckout obligations={[obligation()]} initialOrder={order({ estado: "REQUIERE_AUTENTICACION" })} onClose={vi.fn()} onSettled={vi.fn()} />);
  await user.click(screen.getByRole("button", { name: "Cancelar autenticación" }));
  expect(await screen.findByText("Autenticación cancelada")).toBeTruthy();
  expect(calls.find((call) => call.method === "POST").body).toEqual({ aprobar: false });
  expect(screen.queryByText("Constancia de pago")).toBeNull();
});

test("un sondeo atrasado no revierte una aprobación ni abre consultas superpuestas", async () => {
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
  let finishPoll;
  const calls = backend({ "GET /api/pagos/ordenes/10": () => new Promise((resolve) => { finishPoll = resolve; }),
    "POST /api/pagos/ordenes/10/confirmar-tarjeta": complete(), "GET /api/pagos/ordenes/10/comprobante": receipt() });
  const user = userEvent.setup(); render(<PaymentCheckout obligations={[obligation()]} initialOrder={order()} onClose={vi.fn()} onSettled={vi.fn()} />);
  await act(async () => vi.advanceTimersByTimeAsync(4000));
  expect(calls.filter((call) => call.path === "/api/pagos/ordenes/10")).toHaveLength(1);
  await user.click(screen.getByRole("button", { name: /Confirmar pago/ }));
  expect(await screen.findByText("Referencias: REC-000022")).toBeTruthy();
  await act(async () => { finishPoll(order()); await Promise.resolve(); });
  expect(screen.queryByRole("button", { name: /Confirmar pago/ })).toBeNull();
  expect(screen.getByText("Referencias: REC-000022")).toBeTruthy();
});

test("un fallo temporal de sondeo se comunica y el siguiente resultado recupera el pago", async () => {
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
  let failed = true;
  backend({ "GET /api/pagos/ordenes/10": () => failed ? reply(503, { message: "Error temporal de estado" }) : complete({ metodo: "QR" }),
    "GET /api/pagos/ordenes/10/comprobante": receipt(complete({ metodo: "QR" })) });
  render(<PaymentCheckout obligations={[obligation()]} initialOrder={order({ metodo: "QR", qrUrl: "http://frontend.test/pagar-qr/capacidad" })} onClose={vi.fn()} onSettled={vi.fn()} />);
  await act(async () => vi.advanceTimersByTimeAsync(2000));
  expect(screen.getByText(/No pudimos actualizar el estado/)).toBeTruthy();
  failed = false; await act(async () => vi.advanceTimersByTimeAsync(2000));
  expect(await screen.findByText("Referencias: REC-000022")).toBeTruthy();
  expect(screen.queryByText(/No pudimos actualizar el estado/)).toBeNull();
});

test("un error de descarga conserva el comprobante y permite descargarlo después", async () => {
  let failed = true;
  backend({ "GET /api/pagos/ordenes/10/comprobante": receipt(), "GET /api/pagos/ordenes/10/comprobante/descargar": () => failed ? reply(503, { message: "Descarga temporalmente no disponible" }) : "archivo" });
  URL.createObjectURL = vi.fn(() => "blob:recibo"); URL.revokeObjectURL = vi.fn();
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  const user = userEvent.setup(); render(<ReceiptPanel orderId={10} />);
  await user.click(await screen.findByRole("button", { name: "Descargar comprobante" }));
  expect(await screen.findByText("Descarga temporalmente no disponible")).toBeTruthy();
  expect(screen.getByText("Referencias: REC-000022")).toBeTruthy();
  failed = false; await user.click(screen.getByRole("button", { name: "Descargar comprobante" }));
  await waitFor(() => expect(click).toHaveBeenCalledOnce());
  expect(screen.queryByText("Descarga temporalmente no disponible")).toBeNull();
});

test("el UUID alternativo usa criptografía de red local y conserva el formato v4", () => {
  const secureRandom = crypto.getRandomValues.bind(crypto);
  vi.stubGlobal("crypto", { getRandomValues: secureRandom });
  const values = Array.from({ length: 20 }, paymentRequestKey);
  for (const value of values) expect(value).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  expect(new Set(values).size).toBe(20);
});

test("si falta criptografía segura informa el problema y no inicia una orden", async () => {
  vi.stubGlobal("crypto", undefined);
  const calls = backend();
  const user = userEvent.setup(); render(<PaymentCheckout obligations={[obligation()]} onClose={vi.fn()} onSettled={vi.fn()} />);
  await user.click(screen.getByRole("button", { name: "Continuar con tarjeta" }));
  expect(await screen.findByText(/No pudimos preparar un pago seguro en este navegador/)).toBeTruthy();
  expect(calls.some((call) => call.method === "POST")).toBe(false);
});

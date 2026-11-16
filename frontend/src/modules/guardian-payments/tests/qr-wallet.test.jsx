import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, reply, signIn } from "../../../../tests/support/backend.js";
import { QrWallet } from "../qr-wallet";
import { getQrOrder } from "../services/payment-service";
import { countdownLabel, walletUrl } from "../payment-format";
import QrPaymentPage, { metadata } from "@/app/pagar-qr/[token]/page";
import { complete, order } from "./fixtures";

beforeEach(() => signIn({ role: "APODERADO", permissions: ["PAGOS_LEER"] }));
afterEach(() => vi.useRealTimers());
const pendingQr = (overrides = {}) => order({ metodo: "QR", paymentIntentId: null, clientSecret: null, qrUrl: null, ...overrides });

test("la billetera pública confirma usando sólo su capacidad, sin Bearer ni cookies", async () => {
  const calls = fakeBackend({ "GET /api/pagos/qr/capacidad": pendingQr(), "POST /api/pagos/qr/capacidad/confirmar": complete({ metodo: "QR" }) });
  const requests = [], fetcher = globalThis.fetch;
  vi.stubGlobal("fetch", (url, options) => { requests.push(options); return fetcher(url, options); });
  const user = userEvent.setup(); render(<QrWallet token="capacidad" />);
  await user.click(await screen.findByRole("button", { name: /Confirmar pago/ }));
  expect(await screen.findByText("Tu pago fue confirmado.")).toBeTruthy();
  expect(calls.find((call) => call.method === "POST").body).toEqual({ accion: "APROBAR" });
  expect(requests.every((options) => !options.headers.has("Authorization") && options.credentials === "omit" && options.referrerPolicy === "no-referrer")).toBe(true);
  expect(screen.queryByRole("button", { name: /Confirmar pago/ })).toBeNull();
  expect(screen.queryByText("Iniciar sesión")).toBeNull();
});

test("la billetera permite rechazo y no lo muestra como pago realizado", async () => {
  const calls = fakeBackend({ "GET /api/pagos/qr/capacidad": pendingQr(), "POST /api/pagos/qr/capacidad/confirmar": pendingQr({ estado: "RECHAZADO" }) });
  const user = userEvent.setup(); render(<QrWallet token="capacidad" />);
  await user.click(await screen.findByRole("button", { name: "Rechazar operación" }));
  expect(await screen.findByText("El pago fue rechazado.")).toBeTruthy();
  expect(calls.find((call) => call.method === "POST").body).toEqual({ accion: "RECHAZAR" });
  expect(screen.queryByText("Tu pago fue confirmado.")).toBeNull();
});

test("un pago QR con respuesta perdida se confirma por sondeo y elimina el mensaje de error", async () => {
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
  let requested = 0;
  fakeBackend({ "GET /api/pagos/qr/capacidad": () => ++requested === 1 ? pendingQr() : complete({ metodo: "QR" }),
    "POST /api/pagos/qr/capacidad/confirmar": reply(503, { message: "No llegó la respuesta de la billetera" }) });
  const user = userEvent.setup(); render(<QrWallet token="capacidad" />);
  await user.click(await screen.findByRole("button", { name: /Confirmar pago/ }));
  expect(await screen.findByText("No llegó la respuesta de la billetera")).toBeTruthy();
  await act(async () => vi.advanceTimersByTimeAsync(2000));
  expect(await screen.findByText("Tu pago fue confirmado.")).toBeTruthy();
  expect(screen.queryByText("No llegó la respuesta de la billetera")).toBeNull();
});

test("un QR inválido informa el error y permite volver a consultar", async () => {
  let failed = true;
  fakeBackend({ "GET /api/pagos/qr/capacidad": () => failed ? reply(404, { message: "Este código QR no está disponible" }) : pendingQr() });
  const user = userEvent.setup(); render(<QrWallet token="capacidad" />);
  await screen.findByText("Este código QR no está disponible"); failed = false;
  await user.click(screen.getByRole("button", { name: "Volver a consultar QR" }));
  expect(await screen.findByRole("button", { name: /Confirmar pago/ })).toBeTruthy();
});

test("un QR vencido bloquea las acciones y el sondeo detecta su expiración", async () => {
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
  let requested = 0;
  fakeBackend({ "GET /api/pagos/qr/capacidad": () => ++requested === 1 ? pendingQr({ expiraEn: new Date(Date.now() - 1000).toISOString() }) : pendingQr({ estado: "EXPIRADO" }) });
  render(<QrWallet token="capacidad" />);
  expect((await screen.findByRole("button", { name: /Confirmar pago/ })).disabled).toBe(true);
  await act(async () => vi.advanceTimersByTimeAsync(2000));
  expect(await screen.findByText("Este código QR ya venció.")).toBeTruthy();
  const calls = requested;
  await act(async () => vi.advanceTimersByTimeAsync(6000)); expect(requested).toBe(calls);
});

test("la capacidad QR no acepta enlaces de otras rutas o protocolos ejecutables", () => {
  expect(walletUrl("javascript:alert(1)")).toBeNull();
  expect(walletUrl("http://frontend.test/login")).toBeNull();
  expect(walletUrl("no-url")).toBeNull();
  expect(walletUrl(null)).toBeNull();
  expect(walletUrl("https://colegio.test/pagar-qr/capacidad")).toBe("https://colegio.test/pagar-qr/capacidad");
  expect(countdownLabel(300)).toBe("5:00"); expect(countdownLabel(9)).toBe("0:09");
});

test("una capacidad vencida con datos retirados no expone personas ni presenta un total cero", async () => {
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
  const calls = fakeBackend({ "GET /api/pagos/qr/capacidad": complete({ metodo: "QR", apoderado: "", items: [], montoBase: 0, mora: 0, montoTotal: 0, expiraEn: new Date(Date.now() - 1000).toISOString() }) });
  render(<QrWallet token="capacidad" />);
  expect(await screen.findByText("Este enlace venció. Consulta el resultado y el comprobante desde tu cuenta.")).toBeTruthy();
  expect(screen.getByText("Pago aprobado")).toBeTruthy();
  expect(screen.queryByText(/Pago solicitado por/)).toBeNull();
  expect(screen.queryByText("Total a pagar")).toBeNull();
  expect(screen.queryByText("Lucía Flores")).toBeNull();
  expect(screen.queryByText(/S\/\s*0[.,]00/)).toBeNull();
  expect(screen.queryByRole("button", { name: /Confirmar pago/ })).toBeNull();
  expect(screen.queryByLabelText(/tarjeta|CVV|documento/i)).toBeNull();
  const count = calls.length;
  await act(async () => vi.advanceTimersByTimeAsync(6000)); expect(calls.length).toBe(count);
});

test("la página QR queda fuera de login y desactiva referencia e indexación", async () => {
  fakeBackend({ "GET /api/pagos/qr/capacidad": pendingQr({ estado: "EXPIRADO" }) });
  render(await QrPaymentPage({ params: Promise.resolve({ token: "capacidad" }) }));
  expect(await screen.findByText("Este código QR ya venció.")).toBeTruthy();
  expect(metadata.referrer).toBe("no-referrer"); expect(metadata.robots).toEqual({ index: false, follow: false });
});

test("la consulta QR codifica una capacidad y nunca incorpora una identidad del cliente", async () => {
  const requests = [];
  vi.stubGlobal("fetch", vi.fn(async (url, options) => {
    requests.push({ url, options });
    return { ok: true, status: 200, json: async () => pendingQr() };
  }));
  await getQrOrder("token/ajeno?x=1");
  expect(requests[0].url).toContain("/api/pagos/qr/token%2Fajeno%3Fx%3D1");
  expect(requests[0].options.headers.has("Authorization")).toBe(false);
});

import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { GuardianPayments } from "../guardian-payments";
import { PaymentCheckout } from "../payment-checkout";
import { ObligationDetailDialog } from "../obligation-detail";
import { ReceiptPanel } from "../receipt-panel";
import { complete, obligation, order, receipt, sibling, student } from "./fixtures";

const OBLIGATIONS = "GET /api/apoderado/obligaciones";
const STUDENTS = "GET /api/apoderado/obligaciones/estudiantes";
beforeEach(() => signIn({ role: "APODERADO", permissions: ["PAGOS_LEER"] }));
afterEach(() => vi.useRealTimers());
const backend = (routes = {}) => fakeBackend({ [STUDENTS]: [student, sibling], [OBLIGATIONS]: page([obligation()]), ...routes });
const selectFirst = async (user) => user.click(await screen.findByRole("checkbox", { name: "Seleccionar CP-000001 de Lucía Flores" }));
const checkout = (props = {}) => render(<PaymentCheckout obligations={[obligation()]} onClose={vi.fn()} onSettled={vi.fn()} {...props} />);

test("consulta sólo las obligaciones recibidas, filtra por estudiante y estado y pide páginas de diez", async () => {
  const calls = backend({ [OBLIGATIONS]: ({ query }) => ({ ...page([obligation(), obligation(2, { estadoFinanciero: "PAGADO", saldo: 0 })], 2), page: Number(query.page), totalElements: 12 }) });
  const user = userEvent.setup(); render(<GuardianPayments />);
  await screen.findByRole("button", { name: "Ver obligación CP-000001" });
  expect(screen.getByRole("checkbox", { name: "Seleccionar CP-000002 de Lucía Flores" }).disabled).toBe(true);
  await user.selectOptions(screen.getByLabelText("Estudiante", { exact: true }), "3");
  await user.selectOptions(screen.getByLabelText("Situación de pago"), "VENCIDO");
  await user.click(screen.getByRole("button", { name: "Buscar", exact: true }));
  await waitFor(() => expect(calls.at(-1).query).toMatchObject({ size: "10", page: "0", idAlumno: "3", estado: "VENCIDO" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "Siguiente" }).disabled).toBe(false));
  await user.click(screen.getByRole("button", { name: "Siguiente" }));
  await waitFor(() => expect(calls.at(-1).query.page).toBe("1"));
  expect(calls.every((call) => !("apoderadoId" in call.query))).toBe(true);
});

test("selecciona varias cuotas entre páginas y envía sólo sus identificadores al iniciar", async () => {
  const calls = backend({ [OBLIGATIONS]: ({ query }) => ({ ...page([obligation(Number(query.page) + 1)], 2), page: Number(query.page), totalElements: 2 }), "POST /api/pagos/iniciar": order({ montoBase: 700, montoTotal: 700 }) });
  const user = userEvent.setup(); render(<GuardianPayments />); await selectFirst(user);
  await user.click(screen.getByRole("button", { name: "Siguiente" }));
  await user.click(await screen.findByRole("checkbox", { name: "Seleccionar CP-000002 de Lucía Flores" }));
  expect(screen.getByText("2 obligaciones seleccionadas")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Pagar seleccionadas" }));
  await user.click(screen.getByRole("button", { name: "Continuar con tarjeta" }));
  await screen.findByLabelText("Resultado de autorización");
  expect(calls.find((call) => call.method === "POST").body).toEqual({ obligacionesIds: [1, 2], metodo: "TARJETA" });
});

test("limita la selección a veinte obligaciones y permite limpiar o quitar una página", async () => {
  backend({ [OBLIGATIONS]: ({ query }) => ({ ...page(Array.from({ length: Number(query.page) === 2 ? 1 : 10 }, (_, i) => obligation(Number(query.page) * 10 + i + 1)), 3), page: Number(query.page), totalElements: 21 }) });
  const user = userEvent.setup(); render(<GuardianPayments />);
  await screen.findByRole("button", { name: "Ver obligación CP-000001" });
  await user.click(screen.getByRole("checkbox", { name: "Seleccionar obligaciones de esta página" }));
  await user.click(screen.getByRole("button", { name: "Siguiente" }));
  await screen.findByRole("button", { name: "Ver obligación CP-000011" });
  await user.click(screen.getByRole("checkbox", { name: "Seleccionar obligaciones de esta página" }));
  await user.click(screen.getByRole("button", { name: "Siguiente" }));
  await user.click(await screen.findByRole("checkbox", { name: "Seleccionar CP-000021 de Lucía Flores" }));
  expect(screen.getByText("Puedes seleccionar hasta 20 obligaciones en un pago.")).toBeTruthy();
  await user.click(screen.getByRole("checkbox", { name: "Seleccionar obligaciones de esta página" }));
  expect(screen.getByText("20 obligaciones seleccionadas")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Limpiar selección" }));
  expect(screen.getByRole("button", { name: "Pagar seleccionadas" }).disabled).toBe(true);
});

test("informa errores de estudiantes y listado, y permite recuperar una consulta vacía", async () => {
  let failed = true;
  backend({ [STUDENTS]: () => failed ? reply(503, { message: "No se pudieron cargar tus estudiantes" }) : [], [OBLIGATIONS]: () => failed ? reply(503, { message: "Error temporal" }) : page([]) });
  const user = userEvent.setup(); render(<GuardianPayments />);
  await screen.findByText("No se pudieron cargar tus estudiantes"); failed = false;
  await user.click(screen.getByRole("button", { name: "Reintentar estudiantes" }));
  await user.click(screen.getByRole("button", { name: "Actualizar obligaciones" }));
  expect(await screen.findByText("No hay obligaciones que coincidan con la consulta.")).toBeTruthy();
  expect(screen.getByText(/No tienes estudiantes asociados/)).toBeTruthy();
});

test("el detalle distingue intentos fallidos de pagos aprobados y muestra comprobantes internos", async () => {
  backend({ "GET /api/apoderado/obligaciones/1": { compromiso: obligation(), transacciones: ["APROBADA", "RECHAZADA", "PENDIENTE", "EXPIRADA"].map((estado, index) => ({ id: index + 1, monto: 350, estado, medioPago: index === 0 ? "QR" : "TARJETA", fechaTransaccion: "2026-03-01T12:00:00Z", numeroOperacion: `SIM-${index}` })), comprobantes: receipt().comprobantes } });
  const user = userEvent.setup(); render(<GuardianPayments />);
  await user.click(await screen.findByRole("button", { name: "Ver obligación CP-000001" }));
  expect(await screen.findByText("Pago aprobado")).toBeTruthy();
  expect(screen.getByText("Intento rechazado")).toBeTruthy();
  expect(screen.getByText("Pendiente de confirmación")).toBeTruthy();
  expect(screen.getByText("Intento vencido")).toBeTruthy();
  expect(screen.getByText("REC-000022")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Cerrar", exact: true }));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("recupera un detalle fallido y explica la ausencia de movimientos", async () => {
  let failed = true;
  backend({ "GET /api/apoderado/obligaciones/1": () => failed ? reply(503, { message: "No se pudo consultar" }) : { compromiso: obligation(), transacciones: [], comprobantes: [] } });
  const user = userEvent.setup(); render(<ObligationDetailDialog id={1} onClose={vi.fn()} />);
  await screen.findByText("No se pudo consultar"); failed = false;
  await user.click(screen.getByRole("button", { name: "Reintentar consulta" }));
  expect(await screen.findByText("Todavía no hay intentos de pago para esta obligación.")).toBeTruthy();
});

test("una aprobación con tarjeta actualiza saldos y muestra el comprobante que devuelve el servidor", async () => {
  let paid = false;
  const calls = backend({ [OBLIGATIONS]: () => page([obligation(1, paid ? { saldo: 0, estadoFinanciero: "PAGADO" } : {})]), "POST /api/pagos/iniciar": order(), "POST /api/pagos/ordenes/10/confirmar-tarjeta": () => { paid = true; return complete(); }, "GET /api/pagos/ordenes/10/comprobante": receipt() });
  const user = userEvent.setup(); render(<GuardianPayments />); await selectFirst(user);
  await user.click(screen.getByRole("button", { name: "Pagar seleccionadas" }));
  await user.click(screen.getByRole("button", { name: "Continuar con tarjeta" }));
  await user.click(await screen.findByRole("button", { name: /Confirmar pago/ }));
  expect(await screen.findByText("Referencias: REC-000022")).toBeTruthy();
  expect(calls.find((call) => call.path.endsWith("confirmar-tarjeta")).body).toEqual({ escenario: "APROBADO" });
  expect(screen.queryByLabelText(/CVV|Número de tarjeta/)).toBeNull();
  await user.click(screen.getByRole("button", { name: "Cerrar", exact: true }));
  await waitFor(() => expect(screen.getByRole("checkbox", { name: "Seleccionar CP-000001 de Lucía Flores" }).disabled).toBe(true));
});

test("el desafío de autenticación requiere una segunda confirmación explícita", async () => {
  const calls = backend({ "POST /api/pagos/ordenes/10/confirmar-tarjeta": order({ estado: "REQUIERE_AUTENTICACION" }), "POST /api/pagos/ordenes/10/autenticar": complete(), "GET /api/pagos/ordenes/10/comprobante": receipt() });
  const user = userEvent.setup(); checkout({ initialOrder: order() });
  await user.selectOptions(screen.getByLabelText("Resultado de autorización"), "REQUIERE_AUTENTICACION");
  await user.click(screen.getByRole("button", { name: /Confirmar pago/ }));
  await user.click(await screen.findByRole("button", { name: "Completar autenticación" }));
  expect(await screen.findByText("Referencias: REC-000022")).toBeTruthy();
  expect(calls.find((call) => call.path.endsWith("/autenticar")).body).toEqual({ aprobar: true });
});

test("un rechazo no muestra comprobante y permite iniciar otro intento", async () => {
  const calls = backend({ "POST /api/pagos/ordenes/10/confirmar-tarjeta": order({ estado: "RECHAZADO", mensaje: "Saldo insuficiente en la tarjeta de prueba." }), "POST /api/pagos/iniciar": order({ id: 11, codigo: "OP-11" }) });
  const user = userEvent.setup(); checkout({ initialOrder: order() });
  await user.selectOptions(screen.getByLabelText("Resultado de autorización"), "FONDOS_INSUFICIENTES");
  await user.click(screen.getByRole("button", { name: /Confirmar pago/ }));
  expect(await screen.findByText("Saldo insuficiente en la tarjeta de prueba.")).toBeTruthy();
  expect(screen.queryByText("Constancia de pago")).toBeNull();
  await user.click(screen.getByRole("button", { name: "Intentar de nuevo" }));
  await user.click(screen.getByRole("button", { name: "Continuar con tarjeta" }));
  expect(await screen.findByText("OP-11")).toBeTruthy();
  expect(calls.filter((call) => call.path.includes("/comprobante"))).toHaveLength(0);
});

test("reutiliza la clave idempotente después de una respuesta de inicio perdida", async () => {
  const secureRandom = crypto.getRandomValues.bind(crypto);
  vi.stubGlobal("crypto", { getRandomValues: secureRandom });
  let failed = true; const keys = [];
  backend({ "POST /api/pagos/iniciar": () => failed ? reply(503, { message: "Respuesta temporalmente no disponible" }) : order() });
  const mockedFetch = globalThis.fetch;
  vi.stubGlobal("fetch", (url, options) => { if (url.endsWith("/api/pagos/iniciar")) keys.push(options.headers.get("Idempotency-Key")); return mockedFetch(url, options); });
  const user = userEvent.setup(); checkout();
  await user.click(screen.getByRole("button", { name: "Continuar con tarjeta" }));
  await screen.findByText("Respuesta temporalmente no disponible"); failed = false;
  await user.click(screen.getByRole("button", { name: "Continuar con tarjeta" }));
  await screen.findByLabelText("Resultado de autorización");
  expect(keys[0]).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/); expect(keys[1]).toBe(keys[0]);
});

test("el QR real lleva a la billetera y el sondeo reemplaza la espera por el comprobante", async () => {
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
  const calls = backend({ "GET /api/pagos/ordenes/10": complete({ metodo: "QR" }), "GET /api/pagos/ordenes/10/comprobante": receipt(complete({ metodo: "QR" })) });
  const settled = vi.fn(); const view = checkout({ initialOrder: order({ metodo: "QR", qrUrl: "http://frontend.test/pagar-qr/capacidad" }), onSettled: settled });
  expect(screen.getByRole("link", { name: "Abrir billetera en esta pantalla" }).getAttribute("href")).toBe("http://frontend.test/pagar-qr/capacidad");
  expect(screen.getByTitle("Escanea para abrir la billetera")).toBeTruthy();
  await act(async () => vi.advanceTimersByTimeAsync(2000));
  expect(await screen.findByText("Referencias: REC-000022")).toBeTruthy();
  expect(screen.queryByText("Esperando confirmación en la billetera…")).toBeNull();
  expect(settled).toHaveBeenCalledOnce();
  const count = calls.length; view.unmount();
  await act(async () => vi.advanceTimersByTimeAsync(6000)); expect(calls.length).toBe(count);
});

test("consulta una orden vencida y permite cerrar sin intentar un pago", async () => {
  backend({ "GET /api/pagos/ordenes/10": order({ estado: "EXPIRADO" }) });
  const close = vi.fn(), user = userEvent.setup(); checkout({ initialOrder: order({ expiraEn: new Date(Date.now() - 1000).toISOString() }), onClose: close });
  expect(screen.getByRole("button", { name: /Confirmar pago/ }).disabled).toBe(true);
  await user.click(screen.getByRole("button", { name: "Consultar estado" }));
  expect(await screen.findByText("Orden vencida")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Cerrar", exact: true })); expect(close).toHaveBeenCalledOnce();
});

test("una confirmación de tarjeta con respuesta perdida se recupera por sondeo sin error contradictorio", async () => {
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
  backend({ "POST /api/pagos/ordenes/10/confirmar-tarjeta": reply(503, { message: "Se perdió la respuesta de confirmación" }),
    "GET /api/pagos/ordenes/10": complete(), "GET /api/pagos/ordenes/10/comprobante": receipt() });
  const user = userEvent.setup(); checkout({ initialOrder: order() });
  await user.click(screen.getByRole("button", { name: /Confirmar pago/ }));
  expect(await screen.findByText("Se perdió la respuesta de confirmación")).toBeTruthy();
  await act(async () => vi.advanceTimersByTimeAsync(2000));
  expect(await screen.findByText("Referencias: REC-000022")).toBeTruthy();
  expect(screen.queryByText("Se perdió la respuesta de confirmación")).toBeNull();
});

test("el historial muestra órdenes, continúa pendientes y sólo ofrece comprobante para aprobadas", async () => {
  backend({ "GET /api/pagos/ordenes": page([complete(), order({ id: 11, codigo: "OP-11" }), order({ id: 12, codigo: "OP-12", estado: "RECHAZADO" })]), "GET /api/pagos/ordenes/10/comprobante": receipt() });
  const user = userEvent.setup(); render(<GuardianPayments />);
  await user.click(screen.getByRole("tab", { name: "Seguimiento de pagos" }));
  await user.click(await screen.findByRole("button", { name: "Ver comprobante" }));
  expect(await screen.findByText("Referencias: REC-000022")).toBeTruthy();
  await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Cerrar", exact: true }));
  await user.click(screen.getByRole("button", { name: "Continuar pago" }));
  expect(await screen.findByLabelText("Resultado de autorización")).toBeTruthy();
});

test("recupera el comprobante y descarga el archivo entregado por la API", async () => {
  let failed = true;
  const calls = backend({ "GET /api/pagos/ordenes/10/comprobante": () => failed ? reply(503, { message: "Comprobante temporalmente no disponible" }) : receipt(), "GET /api/pagos/ordenes/10/comprobante/descargar": "archivo" });
  URL.createObjectURL = vi.fn(() => "blob:comprobante"); URL.revokeObjectURL = vi.fn();
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  const user = userEvent.setup(); render(<ReceiptPanel orderId={10} />);
  await screen.findByText("Comprobante temporalmente no disponible"); failed = false;
  await user.click(screen.getByRole("button", { name: "Reintentar comprobante" }));
  await user.click(await screen.findByRole("button", { name: "Descargar comprobante" }));
  expect(click).toHaveBeenCalledOnce(); expect(calls.some((call) => call.path.endsWith("/descargar"))).toBe(true);
});

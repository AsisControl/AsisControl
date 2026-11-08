import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { GuardianPayments } from "../guardian-payments";
import { ReceiptDetail } from "../receipt-detail";
import { ObligationDetailDialog } from "../obligation-detail";
import { paymentMethodLabel } from "../payment-format";
import { obligation, student } from "./fixtures";

const PATH = "/api/apoderado/comprobantes";
const historicalReceipt = (id = 1, overrides = {}) => ({
  id, codigo: `REC-${String(id).padStart(6, "0")}`, fechaEmision: "2026-03-01T12:00:00Z", montoTotal: 350, moneda: "PEN",
  alumno: "Lucía Flores", concepto: "Pensión de marzo", numeroCuota: 1, codigoCompromiso: "CP-000001", medioPago: "EFECTIVO",
  idOrden: null, codigoOrden: null, institucion: "Colegio PUCP", apoderado: "Ana Flores", ...overrides,
});
beforeEach(() => signIn({ role: "APODERADO", permissions: ["PAGOS_LEER"] }));
const backend = (routes = {}) => fakeBackend({ "GET /api/apoderado/obligaciones/estudiantes": [student], "GET /api/apoderado/obligaciones": page([obligation()]), ...routes });

test("el historial incluye comprobantes previos sin orden y varias cuotas de la misma operación", async () => {
  const calls = backend({ [`GET ${PATH}`]: page([historicalReceipt(), historicalReceipt(2, { idOrden: 10, codigoOrden: "OP-000010", medioPago: "TARJETA_SIMULADA" }), historicalReceipt(3, { numeroCuota: 2, codigoCompromiso: "CP-000002", idOrden: 10, codigoOrden: "OP-000010", medioPago: "QR_SIMULADO" })]) });
  const user = userEvent.setup(); render(<GuardianPayments />);
  await user.click(screen.getByRole("tab", { name: "Historial de pagos" }));
  const table = await screen.findByRole("table", { name: "Comprobantes de mis estudiantes" });
  expect(within(table).getAllByRole("button", { name: /Ver comprobante REC-/ })).toHaveLength(3);
  expect(within(table).getByText("Efectivo")).toBeTruthy();
  expect(within(table).getByText("Tarjeta")).toBeTruthy();
  expect(within(table).getByText("Código QR")).toBeTruthy();
  expect(within(table).getAllByText("OP-000010")).toHaveLength(2);
  expect(calls.some((call) => call.path === "/api/pagos/ordenes")).toBe(false);
  expect(screen.queryByText(/Entorno de prueba|Simulación académica/)).toBeNull();
});

test("el historial pagina comprobantes y abre el detalle de un pago institucional sin orden", async () => {
  const calls = backend({ [`GET ${PATH}`]: ({ query }) => ({ ...page([historicalReceipt(Number(query.page) + 1)], 2), page: Number(query.page), totalElements: 11 }), [`GET ${PATH}/2`]: historicalReceipt(2) });
  const user = userEvent.setup(); render(<GuardianPayments />);
  await user.click(screen.getByRole("tab", { name: "Historial de pagos" }));
  await screen.findByRole("button", { name: "Ver comprobante REC-000001" });
  await user.click(screen.getByRole("button", { name: "Siguiente" }));
  await user.click(await screen.findByRole("button", { name: "Ver comprobante REC-000002" }));
  const dialog = screen.getByRole("dialog");
  expect(await within(dialog).findByText("Constancia de pago")).toBeTruthy();
  expect(within(dialog).getByText("Colegio PUCP")).toBeTruthy();
  expect(within(dialog).queryByText("Operación")).toBeNull();
  expect(calls.find((call) => call.path === PATH && call.query.page === "1").query.size).toBe("10");
  await user.click(within(dialog).getByRole("button", { name: "Cerrar", exact: true }));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("el detalle recupera un error, conserva el comprobante si falla la descarga y vuelve a descargar", async () => {
  let failedDetail = true, failedDownload = true;
  const calls = backend({ [`GET ${PATH}/2`]: () => failedDetail ? reply(503, { message: "Consulta no disponible" }) : historicalReceipt(2, { codigoOrden: "OP-000001" }), [`GET ${PATH}/2/descargar`]: () => failedDownload ? reply(503, { message: "Descarga no disponible" }) : "archivo" });
  URL.createObjectURL = vi.fn(() => "blob:constancia"); URL.revokeObjectURL = vi.fn();
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  const user = userEvent.setup(); render(<ReceiptDetail id={2} />);
  await screen.findByText("Consulta no disponible"); failedDetail = false;
  await user.click(screen.getByRole("button", { name: "Reintentar comprobante" }));
  await user.click(await screen.findByRole("button", { name: "Descargar comprobante" }));
  expect(await screen.findByText("Descarga no disponible")).toBeTruthy();
  expect(screen.getByText("OP-000001")).toBeTruthy();
  failedDownload = false; await user.click(screen.getByRole("button", { name: "Descargar comprobante" }));
  await waitFor(() => expect(click).toHaveBeenCalledOnce());
  expect(screen.queryByText("Descarga no disponible")).toBeNull();
  expect(calls.filter((call) => call.path.endsWith("/descargar"))).toHaveLength(2);
});

test("las obligaciones pagadas mantienen su monto original y sólo los pagos parciales muestran lo restante", async () => {
  backend({ "GET /api/apoderado/obligaciones": page([obligation(1, { estadoFinanciero: "PAGADO", saldo: 0, montoPagado: 350 }), obligation(2, { estadoFinanciero: "PARCIAL", saldo: 250, montoPagado: 100 })]) });
  render(<GuardianPayments />);
  const table = await screen.findByRole("table", { name: "Obligaciones de mis estudiantes" });
  expect(within(table).getByRole("columnheader", { name: "Monto", exact: true })).toBeTruthy();
  expect(within(table).queryByRole("columnheader", { name: "Saldo" })).toBeNull();
  expect(within(table).getAllByText(/S\/\s*350[.,]00/)).toHaveLength(2);
  expect(within(table).getByText(/Por pagar:.*250[.,]00/)).toBeTruthy();
  expect(within(table).queryByText(/S\/\s*0[.,]00/)).toBeNull();
});

test("el detalle parcial muestra el total pagado y el monto restante sin reducir el monto original", async () => {
  backend({ "GET /api/apoderado/obligaciones/1": { compromiso: obligation(1, { estadoFinanciero: "PARCIAL", saldo: 250, montoPagado: 100 }), transacciones: [], comprobantes: [] } });
  render(<ObligationDetailDialog id={1} onClose={vi.fn()} />);
  expect(await screen.findByText("Pagado a la fecha")).toBeTruthy();
  expect(screen.getByText("Por pagar")).toBeTruthy();
  expect(screen.queryByText("Saldo")).toBeNull();
});

test("los medios existentes y desconocidos tienen nombres de presentación", () => {
  for (const method of ["TARJETA", "QR", "EFECTIVO", "YAPE", "PLIN", "TRANSFERENCIA", "TRANSFERENCIA_BANCARIA", "DEPOSITO", "OTRO"])
    expect(paymentMethodLabel(method)).not.toContain("_");
  expect(paymentMethodLabel("TARJETA_CREDITO")).toBe("Tarjeta de crédito");
  expect(paymentMethodLabel("TARJETA_DEBITO")).toBe("Tarjeta de débito");
  expect(paymentMethodLabel("POS")).toBe("Tarjeta (POS)");
});

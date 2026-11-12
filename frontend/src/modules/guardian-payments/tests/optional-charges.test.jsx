import { beforeEach, expect, test, vi } from "vitest";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { OptionalCharges } from "../optional-charges";
import { GuardianPayments } from "../guardian-payments";
import { obligation, student } from "./fixtures";

const offer = (overrides = {}) => ({ id: 21, codigo: "OC-000021", idMatricula: 7, idAlumno: 3,
  alumno: "Lucía Flores", anioAcademico: 2027, idConcepto: 2, concepto: "Actividad opcional",
  referencia: "ACT-2027-01", titulo: "Actividad de integración", descripcion: "Incluye transporte y materiales.",
  montoBase: 80, montoDescuento: 20, monto: 60, beneficioNombre: "Convenio del estudiante",
  fechaVencimiento: "2027-06-30", fechaLimiteAceptacion: "2027-06-15", estado: "PROPUESTA",
  idCompromiso: null, codigoCompromiso: null, fechaCreacion: "2027-03-01T12:00:00Z", respondidaEn: null,
  version: 3, firmaCotizacion: "cotizacion-v3", ...overrides });
const PATH = "GET /api/apoderado/ofertas";
const DETAIL = "GET /api/apoderado/ofertas/21";
const ACCEPT = "POST /api/apoderado/ofertas/21/aceptar";

beforeEach(() => signIn({ role: "APODERADO", permissions: ["PAGOS_LEER"] }));
const renderOptions = (props = {}) => render(<OptionalCharges onChanged={vi.fn()} onObligation={vi.fn()} {...props} />);

test("Aceptar requiere revisar las condiciones y registra la cotización, sin iniciar un pago", async () => {
  let finish; const changed = vi.fn(), open = vi.fn();
  const calls = fakeBackend({ [PATH]: page([offer()]), [DETAIL]: offer(), [ACCEPT]: () => new Promise((resolve) => { finish = resolve; }) });
  const user = userEvent.setup(); renderOptions({ onChanged: changed, onObligation: open });
  await user.click(await screen.findByRole("button", { name: "Revisar Actividad de integración de Lucía Flores" }));
  const dialog = await screen.findByRole("dialog");
  await within(dialog).findByText("Incluye transporte y materiales.");
  expect(within(dialog).getByText("Convenio del estudiante")).toBeTruthy();
  expect(within(dialog).getByRole("button", { name: "Confirmar aceptación" }).disabled).toBe(true);
  await user.click(within(dialog).getByRole("checkbox"));
  await user.dblClick(within(dialog).getByRole("button", { name: "Confirmar aceptación" }));
  await waitFor(() => expect(finish).toBeTypeOf("function"));
  expect(calls.filter((call) => call.method === "POST")).toHaveLength(1);
  expect(calls.find((call) => call.method === "POST").body).toEqual({ version: 3, firmaCotizacion: "cotizacion-v3" });
  await act(async () => finish(offer({ estado: "ACEPTADA", idCompromiso: 41, codigoCompromiso: "CP-000041" })));
  expect(await within(dialog).findByText("La opción quedó aceptada. Puedes realizar su pago desde Obligaciones.")).toBeTruthy();
  expect(changed).toHaveBeenCalledOnce();
  expect(calls.some((call) => call.path === "/api/pagos/iniciar")).toBe(false);
  await user.click(within(dialog).getByRole("button", { name: "Ver obligación" }));
  expect(open).toHaveBeenCalledWith(41);
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Una cotización cambiada exige volver a revisarla y aceptarla, sin reenviar automáticamente", async () => {
  let revised = false;
  const calls = fakeBackend({ [PATH]: page([offer()]), [DETAIL]: () => offer(revised ? { monto: 80, montoDescuento: 0, beneficioNombre: null, firmaCotizacion: "cotizacion-v4" } : {}),
    [ACCEPT]: () => { if (!revised) { revised = true; return reply(409, { code: "OFERTA_ACTUALIZADA", message: "El importe cambió. Revisa el detalle actualizado." }); } return offer({ estado: "ACEPTADA", idCompromiso: 41 }); } });
  const user = userEvent.setup(); renderOptions();
  await user.click(await screen.findByRole("button", { name: /Revisar Actividad/ }));
  const dialog = screen.getByRole("dialog");
  await within(dialog).findByRole("checkbox"); await user.click(within(dialog).getByRole("checkbox"));
  await user.click(within(dialog).getByRole("button", { name: "Confirmar aceptación" }));
  await within(dialog).findByText("El importe cambió. Revisa el detalle actualizado.");
  await waitFor(() => expect(calls.filter((call) => call.path === "/api/apoderado/ofertas/21" && call.method === "GET")).toHaveLength(2));
  await within(dialog).findByRole("checkbox");
  expect(within(dialog).getByRole("checkbox").checked).toBe(false);
  expect(within(dialog).getByRole("button", { name: "Confirmar aceptación" }).disabled).toBe(true);
  expect(calls.filter((call) => call.method === "POST")).toHaveLength(1);
  await user.click(within(dialog).getByRole("checkbox"));
  await user.click(within(dialog).getByRole("button", { name: "Confirmar aceptación" }));
  await within(dialog).findByText(/La opción quedó aceptada/);
  expect(calls.filter((call) => call.method === "POST").at(-1).body.firmaCotizacion).toBe("cotizacion-v4");
});

test("No aceptar una actividad no genera una obligación ni un intento de pago", async () => {
  const calls = fakeBackend({ [PATH]: page([offer()]), [DETAIL]: offer(), "POST /api/apoderado/ofertas/21/rechazar": offer({ estado: "RECHAZADA" }) });
  const user = userEvent.setup(); renderOptions();
  await user.click(await screen.findByRole("button", { name: /Revisar Actividad/ }));
  await user.click(await screen.findByRole("button", { name: "No aceptar" }));
  expect(await screen.findByText("Registramos que no deseas aceptar esta opción.")).toBeTruthy();
  expect(screen.queryByRole("checkbox")).toBeNull();
  expect(calls.filter((call) => call.method === "POST").map((call) => call.path)).toEqual(["/api/apoderado/ofertas/21/rechazar"]);
});

test("El catálogo opcional presenta vacíos y estados finales y recupera errores de consulta", async () => {
  let failed = true;
  fakeBackend({ [PATH]: () => failed ? reply(503, { message: "No pudimos consultar las opciones." }) : page([]) });
  const user = userEvent.setup(); const view = renderOptions();
  await screen.findByText("No pudimos consultar las opciones."); failed = false;
  await user.click(screen.getByRole("button", { name: "Actualizar opciones" }));
  expect(await screen.findByText("No tienes actividades ni servicios opcionales registrados.")).toBeTruthy();
  view.unmount();
  fakeBackend({ [PATH]: page([offer({ estado: "CANCELADA" }), offer({ id: 22, estado: "VENCIDA", monto: 0 })]), [DETAIL]: offer({ estado: "CANCELADA" }) });
  renderOptions(); await screen.findByText("Plazo finalizado");
  expect(screen.getByText("Cancelado")).toBeTruthy(); expect(screen.getByText("Exonerado")).toBeTruthy();
  await user.click(screen.getAllByRole("button", { name: /Revisar Actividad/ })[0]);
  await within(screen.getByRole("dialog")).findByText("Cancelado");
  expect(screen.queryByRole("button", { name: "Confirmar aceptación" })).toBeNull();
  await user.click(screen.getByRole("button", { name: "Cerrar", exact: true }));
});

test("Recupera el detalle de una opción sin mostrar importes mientras su consulta falla", async () => {
  let failed = true;
  fakeBackend({ [PATH]: page([offer()]), [DETAIL]: () => failed ? reply(503, { message: "Detalle no disponible" }) : offer({ montoBase: 0, montoDescuento: 0, monto: 0 }),
    [ACCEPT]: offer({ estado: "ACEPTADA", monto: 0, idCompromiso: 41 }) });
  const user = userEvent.setup(); renderOptions();
  await user.click(await screen.findByRole("button", { name: /Revisar Actividad/ }));
  await screen.findByText("Detalle no disponible");
  expect(within(screen.getByRole("dialog")).queryByRole("checkbox")).toBeNull();
  failed = false; await user.click(screen.getByRole("button", { name: "Reintentar consulta" }));
  await user.click(await screen.findByRole("checkbox"));
  await user.click(screen.getByRole("button", { name: "Confirmar aceptación" }));
  expect(await screen.findByText("La opción quedó aceptada y exonerada. No necesitas realizar un pago.")).toBeTruthy();
});

test("El apoderado llega de una opción aceptada a una obligación única sin cuota inventada", async () => {
  const single = obligation(41, { numeroCuota: null, concepto: "Actividad opcional", idConcepto: 2 });
  fakeBackend({ "GET /api/apoderado/obligaciones/estudiantes": [student], "GET /api/apoderado/obligaciones": page([single]),
    [PATH]: page([offer({ estado: "ACEPTADA", idCompromiso: 41 })]), [DETAIL]: offer({ estado: "ACEPTADA", idCompromiso: 41 }),
    "GET /api/apoderado/obligaciones/41": { compromiso: single, transacciones: [], comprobantes: [] } });
  const user = userEvent.setup(); render(<GuardianPayments />);
  await user.click(screen.getByRole("tab", { name: "Actividades y servicios" }));
  await user.click(await screen.findByRole("button", { name: /Revisar Actividad/ }));
  await user.click(await screen.findByRole("button", { name: "Ver obligación", exact: true }));
  await screen.findByRole("dialog", { name: "Detalle de la obligación" });
  expect(screen.queryByText("Sin cuota registrada")).toBeNull();
});

test("Una beca completa impide seleccionar o pagar la pensión y conserva el detalle del beneficio", async () => {
  const exempt = obligation(1, { montoTotal: 0, saldo: 0, montoBase: 400, montoDescuento: 400, beneficioNombre: "Beca completa", estadoFinanciero: "EXONERADO" });
  fakeBackend({ "GET /api/apoderado/obligaciones/estudiantes": [student], "GET /api/apoderado/obligaciones": page([exempt]),
    "GET /api/apoderado/obligaciones/1": { compromiso: exempt, transacciones: [], comprobantes: [] } });
  const user = userEvent.setup(); render(<GuardianPayments />);
  const checkbox = await screen.findByRole("checkbox", { name: "Seleccionar CP-000001 de Lucía Flores" });
  expect(checkbox.disabled).toBe(true);
  expect(screen.getByRole("button", { name: "Pagar seleccionadas" }).disabled).toBe(true);
  await user.click(screen.getByRole("button", { name: "Ver obligación CP-000001" }));
  expect(await within(screen.getByRole("dialog")).findByText("Beca completa")).toBeTruthy();
  expect(screen.getByText(/No requiere pago y no genera un comprobante/)).toBeTruthy();
});

import { beforeEach, expect, test } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { Finance } from "../finance";

/** US-44: estados Pendiente, Parcial, Pagado y Vencido, transacciones e indicadores de morosidad. */
const base = {
  idMatricula: 7, idAlumno: 3, alumno: "Lucía Flores", codigoAlumno: "EST-003",
  codigoMatricula: "MAT-007", anioAcademico: 2026, concepto: "Pensión", descripcion: null,
  moneda: "PEN", estadoAdministrativo: "ACTIVO", fechaCreacion: "2026-03-01T12:00:00Z",
  fechaActualizacion: "2026-03-01T12:00:00Z", motivoAnulacion: null, fechaAnulacion: null,
  version: 1, grado: 3, seccion: "B", idSeccion: 31,
};
const commitments = [
  { ...base, id: 1, codigoCompromiso: "CP-001", numeroCuota: 1, montoTotal: 300, montoPagado: 0, saldo: 300, fechaVencimiento: "2026-12-10", estado: "PENDIENTE", estadoFinanciero: "PENDIENTE" },
  { ...base, id: 2, codigoCompromiso: "CP-002", numeroCuota: 2, montoTotal: 300, montoPagado: 120.5, saldo: 179.5, fechaVencimiento: "2026-12-10", estado: "PARCIAL", estadoFinanciero: "PARCIAL" },
  { ...base, id: 3, codigoCompromiso: "CP-003", numeroCuota: 3, montoTotal: 300, montoPagado: 300, saldo: 0, fechaVencimiento: "2026-03-10", estado: "PAGADO", estadoFinanciero: "PAGADO" },
  { ...base, id: 4, codigoCompromiso: "CP-004", numeroCuota: 4, montoTotal: 250, montoPagado: 50, saldo: 200, fechaVencimiento: "2026-04-10", estado: "VENCIDO", estadoFinanciero: "VENCIDO" },
];
const summary = {
  fechaCalculo: "2026-11-17",
  cantidad: 4, montoTotal: 1150, montoPagado: 470.5, saldo: 679.5,
  porEstado: [
    { estado: "PENDIENTE", cantidad: 1, montoTotal: 300, montoPagado: 0, saldo: 300 },
    { estado: "PARCIAL", cantidad: 1, montoTotal: 300, montoPagado: 120.5, saldo: 179.5 },
    { estado: "PAGADO", cantidad: 1, montoTotal: 300, montoPagado: 300, saldo: 0 },
    { estado: "VENCIDO", cantidad: 1, montoTotal: 250, montoPagado: 50, saldo: 200 },
    { estado: "ANULADO", cantidad: 0, montoTotal: 0, montoPagado: 0, saldo: 0 },
    { estado: "EXONERADO", cantidad: 0, montoTotal: 0, montoPagado: 0, saldo: 0 },
  ],
  morosidad: { montoVencido: 200, compromisosVencidos: 1, estudiantesMorosos: 1 },
};
const transaction = (id, estado, monto, extra = {}) => ({
  id, idCompromisoPago: 2, idApoderado: 5, idAdministrador: null, numeroOperacion: `OP-${id}`,
  monto, medioPago: "EFECTIVO", estado, fechaTransaccion: "2026-11-10T15:30:00Z", observacion: null,
  fechaCreacion: "2026-11-10T15:30:00Z", idAlumno: 3, alumno: "Lucía Flores", codigoAlumno: "EST-003",
  codigoCompromiso: "CP-002", concepto: "Pensión", ...extra,
});
const sections = page([{ id: 31, nombre: "B", grado: 3 }, { id: 30, nombre: "A", grado: 3 }, { id: 40, nombre: "A", grado: 4 }]);

const routes = (overrides = {}) => ({
  "GET /api/compromisos-pago": page(commitments),
  "GET /api/compromisos-pago/resumen": summary,
  "GET /api/secciones": sections,
  ...overrides,
});

beforeEach(() => signIn({ permissions: ["PAGOS_LEER", "PAGOS_ESCRIBIR"] }));

test("muestra los cuatro estados con monto, pagado y saldo calculados por el sistema", async () => {
  fakeBackend(routes());
  render(<Finance canWrite={false} />);
  await screen.findByText("Cuota 1 · CP-001");
  const table = screen.getAllByRole("table").find((element) => within(element).queryByText("CP-001", { exact: false }));
  const rows = within(table).getAllByRole("row").slice(1);
  const byCode = (code) => rows.find((row) => within(row).queryByText(code, { exact: false }));
  expect(within(byCode("CP-001")).getByText("Pendiente", { selector: ".status" })).toBeTruthy();
  expect(within(byCode("CP-002")).getByText("Pago parcial", { selector: ".status" })).toBeTruthy();
  expect(within(byCode("CP-002")).getByText(/^S\/\s179\.50$/)).toBeTruthy();
  expect(within(byCode("CP-003")).getByText("Pagado", { selector: ".status" })).toBeTruthy();
  expect(within(byCode("CP-003")).getByText(/^S\/\s0\.00$/)).toBeTruthy();
  expect(within(byCode("CP-004")).getByText("Vencido", { selector: ".status" })).toBeTruthy();
  expect(within(byCode("CP-004")).getByText(/^S\/\s200\.00$/)).toBeTruthy();
  expect(within(byCode("CP-001")).getByText(/3\.° grado · Sección B/)).toBeTruthy();
});

test("muestra los indicadores de morosidad y el resumen por estado", async () => {
  fakeBackend(routes());
  render(<Finance canWrite={false} />);
  const panel = await screen.findByRole("region", { name: "Indicadores de morosidad" });
  await within(panel).findByText("Monto vencido");
  const kpi = (label) => within(panel).getByText(label).closest("div");
  expect(within(kpi("Monto vencido")).getByText(/^S\/\s200\.00$/)).toBeTruthy();
  expect(within(kpi("Compromisos vencidos")).getByText("1")).toBeTruthy();
  expect(within(kpi("Estudiantes morosos")).getByText("1")).toBeTruthy();
  const resumen = within(panel).getByRole("table", { name: "Resumen por estado" });
  expect(within(resumen).getByText("Pago parcial")).toBeTruthy();
  expect(within(resumen).queryByText("Anulado")).toBeNull();
  const total = within(resumen).getByRole("rowheader", { name: "Total" }).closest("tr");
  expect(within(total).getByText("4")).toBeTruthy();
  expect(within(total).getByText(/^S\/\s679\.50$/)).toBeTruthy();
});

test("filtra por año, grado, sección, estado y vencimiento y recalcula el resumen con los mismos filtros", async () => {
  const calls = fakeBackend(routes());
  const user = userEvent.setup();
  render(<Finance canWrite={false} />);
  await screen.findByText("Monto vencido");
  await user.type(screen.getByLabelText("Año académico"), "2026");
  await user.selectOptions(await screen.findByRole("combobox", { name: "Grado" }), "3");
  await user.selectOptions(screen.getByRole("combobox", { name: "Sección" }), "31");
  await user.selectOptions(screen.getByLabelText("Estado del compromiso"), "VENCIDO");
  fireEvent.change(screen.getByLabelText("Vence desde"), { target: { value: "2026-01-01" } });
  fireEvent.change(screen.getByLabelText("Vence hasta"), { target: { value: "2026-06-30" } });
  await user.click(screen.getByRole("button", { name: "Buscar", exact: true }));
  const expected = { anioAcademico: "2026", grado: "3", idSeccion: "31", estado: "VENCIDO", vencimientoDesde: "2026-01-01", vencimientoHasta: "2026-06-30" };
  await waitFor(() => expect(calls.filter((call) => call.path === "/api/compromisos-pago").at(-1).query).toMatchObject({ ...expected, page: "0" }));
  await waitFor(() => expect(calls.filter((call) => call.path === "/api/compromisos-pago/resumen").at(-1).query).toMatchObject(expected));
  expect(screen.getByRole("combobox", { name: "Sección" }).querySelectorAll("option")).toHaveLength(3);
});

test("no envía un rango de vencimiento invertido", async () => {
  const calls = fakeBackend(routes());
  const user = userEvent.setup();
  render(<Finance canWrite={false} />);
  await screen.findByText("Monto vencido");
  const before = calls.length;
  fireEvent.change(screen.getByLabelText("Vence desde"), { target: { value: "2026-06-30" } });
  fireEvent.change(screen.getByLabelText("Vence hasta"), { target: { value: "2026-01-01" } });
  await user.click(screen.getByRole("button", { name: "Buscar", exact: true }));
  expect(await screen.findByText("La fecha inicial del vencimiento no puede ser posterior a la final.")).toBeTruthy();
  expect(calls.slice(before).filter((call) => call.path.startsWith("/api/compromisos-pago"))).toHaveLength(0);
});

test("si los indicadores fallan, el listado sigue disponible y se pueden reintentar", async () => {
  let failing = true;
  fakeBackend(routes({ "GET /api/compromisos-pago/resumen": () => failing ? reply(503, { message: "Servicio no disponible" }) : summary }));
  const user = userEvent.setup();
  render(<Finance canWrite={false} />);
  expect(await screen.findByText(/No pudimos calcular los indicadores/)).toBeTruthy();
  expect(await screen.findByText("CP-001", { exact: false })).toBeTruthy();
  failing = false;
  await user.click(screen.getByRole("button", { name: "Reintentar indicadores" }));
  expect(await screen.findByText("Estudiantes morosos")).toBeTruthy();
});

test("el detalle muestra la secuencia de transacciones, su efecto en el saldo y el comprobante interno", async () => {
  fakeBackend(routes({
    "GET /api/compromisos-pago/2": commitments[1],
    "GET /api/compromisos-pago/2/detalle": {
      compromiso: commitments[1],
      movimientos: [
        { secuencia: 1, transaccion: transaction(10, "RECHAZADA", 300), aplicaAlSaldo: false, pagadoAcumulado: 0, saldoResultante: 300, comprobante: null },
        { secuencia: 2, transaccion: transaction(11, "APROBADA", 120.5), aplicaAlSaldo: true, pagadoAcumulado: 120.5, saldoResultante: 179.5,
          comprobante: { id: 90, idTransaccionPago: 11, idArchivoAdjunto: null, tipoComprobante: "BOLETA", serie: "B001", numero: "000123", fechaEmision: "2026-11-10T15:31:00Z", montoTotal: 120.5 } },
      ],
    },
  }));
  const user = userEvent.setup();
  render(<Finance canWrite={false} />);
  await user.click(await screen.findByRole("button", { name: "Ver compromiso CP-002" }));
  const dialog = await screen.findByRole("dialog");
  const first = await within(dialog).findByRole("listitem", { name: "Transacción 1" });
  expect(within(first).getByText("Rechazada")).toBeTruthy();
  expect(within(first).getByText(/No se aplica al saldo/)).toBeTruthy();
  expect(within(first).getByText(/Sin cambio · S\/\s300\.00/)).toBeTruthy();
  const second = within(dialog).getByRole("listitem", { name: "Transacción 2" });
  expect(within(second).getByText("Aprobada")).toBeTruthy();
  expect(within(second).getByText(/^S\/\s179\.50$/)).toBeTruthy();
  expect(within(second).getByText("B001-000123")).toBeTruthy();
  expect(within(dialog).getByText("2 transacciones")).toBeTruthy();
});

test("la pestaña Transacciones filtra por estudiante, estado y fecha e indica cuáles no se aplican al saldo", async () => {
  const calls = fakeBackend(routes({
    "GET /api/transacciones-pago": page([transaction(11, "APROBADA", 120.5), transaction(12, "EXPIRADA", 300, { medioPago: "QR_SIMULADO" })]),
  }));
  const user = userEvent.setup();
  render(<Finance canWrite={false} />);
  await user.click(await screen.findByRole("button", { name: "Transacciones" }));
  expect(await screen.findByText("OP-11")).toBeTruthy();
  const expired = screen.getByText("OP-12").closest("tr");
  expect(within(expired).getByText("Expirada")).toBeTruthy();
  expect(within(expired).getByText("No se aplica al saldo")).toBeTruthy();
  expect(within(expired).getByText("QR (pago en línea)")).toBeTruthy();
  expect(within(screen.getByText("OP-11").closest("tr")).queryByText("No se aplica al saldo")).toBeNull();

  await user.type(screen.getByLabelText("Estudiante u operación"), "EST-003");
  await user.selectOptions(screen.getByLabelText("Estado de la transacción"), "APROBADA");
  fireEvent.change(screen.getByLabelText("Desde"), { target: { value: "2026-11-01" } });
  fireEvent.change(screen.getByLabelText("Hasta"), { target: { value: "2026-11-30" } });
  await user.click(screen.getByRole("button", { name: "Filtrar" }));
  await waitFor(() => expect(calls.filter((call) => call.path === "/api/transacciones-pago").at(-1).query).toMatchObject({
    busqueda: "EST-003", estado: "APROBADA", fechaDesde: "2026-11-01", fechaHasta: "2026-11-30", page: "0",
  }));
});

test("al volver a Compromisos el formulario muestra los filtros que siguen aplicados", async () => {
  fakeBackend(routes({ "GET /api/transacciones-pago": page([]) }));
  const user = userEvent.setup();
  render(<Finance canWrite={false} />);
  await screen.findByText("Monto vencido");
  await user.type(screen.getByLabelText("Buscar estudiante o compromiso"), "Lucía");
  await user.selectOptions(screen.getByLabelText("Estado del compromiso"), "VENCIDO");
  await user.click(screen.getByRole("button", { name: "Buscar", exact: true }));
  await user.click(screen.getByRole("button", { name: "Transacciones" }));
  await screen.findByText("No hay transacciones que coincidan con la consulta.");
  await user.click(screen.getByRole("button", { name: "Compromisos de pago" }));
  expect((await screen.findByLabelText("Buscar estudiante o compromiso")).value).toBe("Lucía");
  expect(screen.getByLabelText("Estado del compromiso").value).toBe("VENCIDO");
});

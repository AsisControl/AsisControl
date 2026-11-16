import { beforeEach, expect, test } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { Finance } from "../finance";

const BASE = "/api/configuracion-pagos";
const enrollment = { id: 7, codigoMatricula: "MAT-007", alumno: "Lucía Flores", codigoAlumno: "EST-003", alumnoId: 3, anioAcademico: 2027, grado: 1, seccion: "A", estado: "ACTIVA" };
const otherEnrollment = { ...enrollment, id: 8, alumnoId: 4, alumno: "Mateo Flores", codigoMatricula: "MAT-008", codigoAlumno: "EST-004" };
const pension = { id: 1, codigo: "PENSION", nombre: "Pensión", periodicidad: "MENSUAL", esPension: true, requiereAceptacion: false, activo: true, version: 0 };
const materials = { ...pension, id: 2, codigo: "MATERIAL", nombre: "Materiales", periodicidad: "UNICO", esPension: false, montoSugerido: 40 };
const optional = { ...materials, id: 3, codigo: "TALLER", nombre: "Taller de arte", requiereAceptacion: true, montoSugerido: 100 };
const legacy = { id: 12, idMatricula: 7, idAlumno: 3, alumno: "Lucía Flores", codigoAlumno: "EST-003", codigoMatricula: "MAT-007", anioAcademico: 2027, codigoCompromiso: "CP-000012", concepto: "Pensión anterior", numeroCuota: 1, montoTotal: 400, montoPagado: 400, saldo: 0, moneda: "PEN", fechaVencimiento: "2027-03-31", estadoAdministrativo: "ACTIVO", estadoFinanciero: "PAGADO", version: 4 };
const plan = { idMatricula: 7, idAlumno: 3, alumno: "Lucía Flores", anioAcademico: 2027, grado: 1, idTarifa: 6, montoMensual: 400, puedeGenerar: false, mensaje: "Revisa los compromisos anteriores.", firmaPlan: "plan-original", items: [{ mes: 3, numeroCuota: 1, concepto: "Pensión de marzo", fechaVencimiento: "2027-03-31", montoBase: 400, montoDescuento: 100, montoTotal: 300, beneficioNombre: "Beca parcial", estado: "PENDIENTE" }, { mes: 4, numeroCuota: 2, concepto: "Pensión de abril", fechaVencimiento: "2027-04-30", montoBase: 400, montoDescuento: 400, montoTotal: 0, beneficioNombre: "Beca total", idCompromiso: 20, estado: "EXONERADO" }], legados: [legacy] };
const offer = { id: 15, codigo: "OC-000015", idMatricula: 7, idAlumno: 3, alumno: "Lucía Flores", anioAcademico: 2027, idConcepto: 3, concepto: "Taller de arte", referencia: "TALLER-2027", titulo: "Taller de arte", descripcion: "Materiales incluidos", montoBase: 100, montoDescuento: 25, monto: 75, beneficioNombre: "Descuento", fechaVencimiento: "2027-06-30", fechaLimiteAceptacion: "2027-06-20", estado: "PROPUESTA", idCompromiso: null, codigoCompromiso: null, version: 2 };

beforeEach(() => signIn({ permissions: ["PAGOS_LEER", "PAGOS_ESCRIBIR"] }));
function backend(routes = {}) { return fakeBackend({ "GET /api/compromisos-pago": page([]), "GET /api/compromisos-pago/matriculas": page([enrollment, otherEnrollment]), [`GET ${BASE}/conceptos`]: [pension, materials, optional], [`GET ${BASE}/planes/matriculas/7/preview`]: plan, [`GET ${BASE}/ofertas`]: page([offer]), ...routes }); }
async function openPlan(user) { await user.click(screen.getByRole("button", { name: "Plan anual de pensiones", exact: true })); await user.click(await screen.findByRole("button", { name: "Elegir matrícula de Lucía Flores" })); await user.click(screen.getByRole("button", { name: "Revisar plan de pensiones" })); }

test("regulariza una pensión anterior sin modificar pagos y genera solo las mensualidades faltantes", async () => {
  const linked = { ...plan, puedeGenerar: true, mensaje: "Puedes completar el plan.", legados: [], firmaPlan: "plan-vinculado" };
  const calls = backend({ [`POST ${BASE}/planes/matriculas/7/vincular`]: linked, [`POST ${BASE}/planes/matriculas/7/generar`]: { ...linked, mensaje: "Se generaron las mensualidades faltantes." } });
  const user = userEvent.setup(); render(<Finance canWrite />); await openPlan(user);
  expect(await screen.findByText("Exonerado")).toBeTruthy(); expect(screen.getByRole("button", { name: "Generar mensualidades faltantes" }).disabled).toBe(true);
  await user.click(screen.getByRole("button", { name: "Relacionar mes de CP-000012" }));
  await user.selectOptions(screen.getByLabelText(/Mes de la pensión/), "3"); await user.type(screen.getByLabelText(/Motivo de la vinculación/), "Corresponde a marzo"); await user.click(screen.getByRole("button", { name: "Guardar relación" }));
  await screen.findByText(/Compromiso relacionado/);
  expect(calls.find((call) => call.path.endsWith("/vincular")).body).toEqual({ idCompromiso: 12, mes: 3, motivo: "Corresponde a marzo", version: 4 });
  await user.click(screen.getByRole("button", { name: "Generar mensualidades faltantes" })); await screen.findByText("Se generaron las mensualidades faltantes.");
  expect(calls.find((call) => call.path.endsWith("/generar")).body).toEqual({ firmaPlan: "plan-vinculado" });
});

test("clasifica un cobro anterior como otro concepto sin inventar una mensualidad", async () => {
  const calls = backend({ [`POST ${BASE}/planes/matriculas/7/vincular`]: { ...plan, legados: [], puedeGenerar: true } });
  const user = userEvent.setup(); render(<Finance canWrite />); await openPlan(user);
  await user.click(await screen.findByRole("button", { name: "Relacionar mes de CP-000012" })); await user.selectOptions(screen.getByLabelText("Corresponde a"), "OTRO");
  await user.selectOptions(await screen.findByLabelText(/Concepto del catálogo/), "2"); await user.type(screen.getByLabelText(/Motivo de la vinculación/), "Se registró como mensualidad por error"); await user.click(screen.getByRole("button", { name: "Guardar relación" }));
  await screen.findByText(/Compromiso relacionado/); expect(calls.find((call) => call.method === "POST").body).toMatchObject({ idCompromiso: 12, mes: null, idConcepto: 2, version: 4 });
});

test("recupera una revisión fallida y exige volver a confirmar cuando cambia el plan", async () => {
  let failed = true, revision = 0;
  const calls = backend({ [`GET ${BASE}/planes/matriculas/7/preview`]: () => failed ? reply(503, { message: "No pudimos consultar el plan" }) : { ...plan, legados: [], puedeGenerar: true, firmaPlan: revision ? "plan-nuevo" : "plan-original" }, [`POST ${BASE}/planes/matriculas/7/generar`]: () => { revision++; return revision === 1 ? reply(409, { code: "PLAN_ACTUALIZADO", message: "El plan cambió." }) : { ...plan, legados: [], puedeGenerar: true, mensaje: "Plan completado." }; } });
  const user = userEvent.setup(); render(<Finance canWrite />); await openPlan(user); await screen.findByText("No pudimos consultar el plan"); failed = false;
  await user.click(screen.getByRole("button", { name: "Revisar plan de pensiones" })); await waitFor(() => expect(screen.getByRole("button", { name: "Generar mensualidades faltantes" }).disabled).toBe(false));
  await user.click(screen.getByRole("button", { name: "Generar mensualidades faltantes" })); await screen.findByText(/Revisa los importes actuales y confirma nuevamente/);
  expect(calls.filter((call) => call.method === "POST")).toHaveLength(1);
  await user.click(screen.getByRole("button", { name: "Generar mensualidades faltantes" })); await screen.findByText("Plan completado.");
  expect(calls.filter((call) => call.method === "POST").at(-1).body).toEqual({ firmaPlan: "plan-nuevo" });
});

test("la consulta sin escritura no permite generar planes ni publicar o cancelar propuestas", async () => {
  backend({ [`GET ${BASE}/planes/matriculas/7/preview`]: { ...plan, puedeGenerar: true } }); const user = userEvent.setup(); render(<Finance canWrite={false} />); await openPlan(user); await screen.findByText("Exonerado");
  expect(screen.queryByRole("button", { name: "Generar mensualidades faltantes" })).toBeNull(); expect(screen.queryByRole("button", { name: /Relacionar mes/ })).toBeNull();
  await user.click(screen.getByRole("button", { name: "Cobros opcionales", exact: true })); await screen.findByText("OC-000015 · TALLER-2027");
  expect(screen.queryByRole("button", { name: "Publicar cobro opcional" })).toBeNull(); expect(screen.queryByRole("button", { name: /Cancelar propuesta/ })).toBeNull();
});

test("publica propuestas por matrícula usando el catálogo y conserva los datos si falla la validación", async () => {
  let rejected = true;
  const calls = backend({ [`POST ${BASE}/ofertas`]: () => rejected ? reply(422, { message: "Revisa la fecha límite de aceptación." }) : [offer, { ...offer, id: 16 }] });
  const user = userEvent.setup(); render(<Finance canWrite />); await user.click(screen.getByRole("button", { name: "Cobros opcionales", exact: true })); await user.click(screen.getByRole("button", { name: "Publicar cobro opcional" }));
  await screen.findByRole("option", { name: "Taller de arte" }); await user.selectOptions(screen.getByLabelText(/Concepto \*/), "3"); expect(screen.getByLabelText(/Importe regular por estudiante/).value).toBe("100");
  await user.type(screen.getByLabelText(/Referencia de la actividad/), "TALLER-2027"); await user.type(screen.getByLabelText(/Título para el apoderado/), "Taller de arte de junio"); await user.type(screen.getByLabelText(/Condiciones o descripción/), "Materiales incluidos");
  fireEvent.change(screen.getByLabelText(/Fecha de vencimiento del pago/), { target: { value: "2027-06-30" } }); fireEvent.change(screen.getByLabelText(/Fecha límite de aceptación/), { target: { value: "2027-06-20" } });
  await user.click(await screen.findByRole("button", { name: "Elegir matrícula de Lucía Flores" })); await user.click(screen.getByRole("button", { name: "Elegir matrícula de Lucía Flores" }));
  expect(screen.getByText("Estudiantes seleccionados (1)")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Elegir matrícula de Mateo Flores" })); await user.click(screen.getByRole("button", { name: "Quitar a Lucía Flores" })); await user.click(screen.getByRole("button", { name: "Elegir matrícula de Lucía Flores" }));
  await user.click(screen.getByRole("button", { name: "Publicar propuestas" })); await screen.findByText("Revisa la fecha límite de aceptación."); rejected = false;
  expect(screen.getByLabelText(/Título para el apoderado/).value).toBe("Taller de arte de junio"); await user.click(screen.getByRole("button", { name: "Publicar propuestas" }));
  await screen.findByText(/2 propuestas publicadas/); expect(calls.filter((call) => call.method === "POST").at(-1).body).toMatchObject({ idConcepto: 3, idMatriculas: [8, 7], monto: 100, referencia: "TALLER-2027", fechaVencimiento: "2027-06-30", fechaLimiteAceptacion: "2027-06-20" });
});

test("solo cancela propuestas no aceptadas con motivo y versión", async () => {
  const calls = backend({ [`GET ${BASE}/ofertas`]: page([offer, { ...offer, id: 16, codigo: "OC-000016", estado: "ACEPTADA", codigoCompromiso: "CP-000022" }]), [`POST ${BASE}/ofertas/15/cancelar`]: { ...offer, estado: "CANCELADA" } });
  const user = userEvent.setup(); render(<Finance canWrite />); await user.click(screen.getByRole("button", { name: "Cobros opcionales", exact: true }));
  await screen.findByText("CP-000022"); expect(screen.queryByRole("button", { name: "Cancelar propuesta OC-000016" })).toBeNull();
  await user.click(screen.getByRole("button", { name: "Cancelar propuesta OC-000015" })); await user.type(screen.getByLabelText(/Motivo/), "Actividad suspendida"); await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Cancelar propuesta", exact: true }));
  await screen.findByText("Propuesta cancelada."); expect(calls.find((call) => call.method === "POST").body).toEqual({ version: 2, motivo: "Actividad suspendida" });
});

test("recupera conceptos y propuestas no disponibles sin ofrecer publicaciones incompletas", async () => {
  let fail = true;
  backend({ [`GET ${BASE}/ofertas`]: () => fail ? reply(503, { message: "Propuestas no disponibles" }) : page([]), [`GET ${BASE}/conceptos`]: () => fail ? reply(503, { message: "Conceptos no disponibles" }) : [optional] });
  const user = userEvent.setup(); render(<Finance canWrite />); await user.click(screen.getByRole("button", { name: "Cobros opcionales", exact: true })); await screen.findByText("Propuestas no disponibles");
  await user.click(screen.getByRole("button", { name: "Publicar cobro opcional" })); await screen.findByText("Conceptos no disponibles"); expect(screen.getByRole("button", { name: "Publicar propuestas" }).disabled).toBe(true);
  fail = false; await user.click(screen.getByRole("button", { name: "Reintentar conceptos" })); await screen.findByRole("option", { name: "Taller de arte" });
  await user.click(screen.getByRole("button", { name: "Cancelar", exact: true })); await user.click(screen.getByRole("button", { name: "Reintentar propuestas" })); await screen.findByText("Todavía no hay cobros opcionales publicados.");
});

test("un plan completo conserva el estado de cada pensión, sus importes emitidos y bloquea otra generación", async () => {
  const states = ["PAGADO", "ANULADO", "PENDIENTE", "VENCIDO", "PARCIAL", "EXONERADO", "PENDIENTE", "PENDIENTE", "PENDIENTE", "PENDIENTE"];
  const items = states.map((estado, index) => ({ ...plan.items[0], mes: index + 3, numeroCuota: index + 1, idCompromiso: 30 + index, concepto: `Pensión ${index + 1}`, estado, montoBase: 400, montoDescuento: estado === "EXONERADO" ? 400 : 0, montoTotal: estado === "EXONERADO" ? 0 : 400, beneficioNombre: estado === "EXONERADO" ? "Beca integral" : undefined }));
  const calls = backend({ [`GET ${BASE}/planes/matriculas/7/preview`]: { ...plan, items, legados: [], montoMensual: 450, puedeGenerar: true, mensaje: "Las diez pensiones ya están registradas." } });
  const user = userEvent.setup(); render(<Finance canWrite />); await openPlan(user);
  await screen.findByText("Las diez pensiones ya están registradas."); expect(screen.getByText("Tarifa configurada actualmente")).toBeTruthy();
  const rows = within(screen.getByRole("table", { name: "Mensualidades del plan anual" })).getAllByRole("row").slice(1);
  expect(rows).toHaveLength(10);
  const expected = [["Pagado", "success"], ["Anulado", "neutral"], ["Pendiente", "info"], ["Vencido", "danger"], ["Pago parcial", "warning"], ["Exonerado", "info"]];
  expected.forEach(([label, tone], index) => expect(within(rows[index]).getByText(label).className).toContain(`status-${tone}`));
  expect(within(rows[0]).getAllByText(/^S\/\s400\.00$/)).toHaveLength(2); expect(screen.queryByText("Emitida", { exact: true })).toBeNull(); expect(screen.queryByText("Por generar", { exact: true })).toBeNull();
  const complete = screen.getByRole("button", { name: "Plan completo" }); expect(complete.disabled).toBe(true); await user.click(complete);
  expect(calls.some((call) => call.method === "POST")).toBe(false);
  await user.click(screen.getByRole("button", { name: "Cambiar matrícula" })); expect(screen.queryByRole("table", { name: "Mensualidades del plan anual" })).toBeNull();
  expect(screen.getByRole("button", { name: "Revisar plan de pensiones" }).disabled).toBe(true);
});

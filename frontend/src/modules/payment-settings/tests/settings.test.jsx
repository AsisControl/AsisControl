import { beforeEach, expect, test } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { PaymentSettings } from "../payment-settings";

const BASE = "/api/configuracion-pagos";
const pension = { id: 1, codigo: "PENSION", nombre: "Pensión", descripcion: null, periodicidad: "MENSUAL", esPension: true, montoSugerido: null, requiereAceptacion: false, activo: true, version: 2 };
const materials = { ...pension, id: 2, codigo: "MATERIAL", nombre: "Materiales", descripcion: "Materiales autorizados", periodicidad: "UNICO", esPension: false, montoSugerido: 40, version: 3 };
const rate = { id: 4, idConcepto: 1, anioAcademico: 2027, grado: 1, montoMensual: 400, activo: true, version: 5 };
const student = { id: 9, codigoAlumno: "EST-009", nombreCompleto: "Ana Flores" };
const benefit = { id: 7, idAlumno: 9, alumno: "Ana Flores", anioAcademico: 2027, idConcepto: 1, concepto: "Pensión", nombre: "Beca parcial", tipo: "PORCENTAJE", valor: 25, mesInicio: 3, mesFin: 12, motivo: "Beca aprobada", activo: true, version: 6 };
const commitment = { id: 11, codigoCompromiso: "CP-000011", concepto: "Pensión de marzo", fechaVencimiento: "2027-03-31", montoTotal: 300, montoBase: 400, montoDescuento: 100, montoPagado: 0, estadoFinanciero: "PENDIENTE" };
const preview = { idBeneficio: 7, mensaje: "Una obligación disponible para ajustar.", ajustables: [commitment], omitidos: [{ ...commitment, id: 12, codigoCompromiso: "CP-000012", montoPagado: 400 }], firmaAjuste: "firma-vigente" };

beforeEach(() => signIn({ permissions: ["PAGOS_LEER", "PAGOS_ESCRIBIR"] }));
function backend(routes = {}) {
  return fakeBackend({ [`GET ${BASE}/conceptos`]: [pension, materials], [`GET ${BASE}/anios`]: [2027, 2028], [`GET ${BASE}/tarifas`]: [rate], [`GET ${BASE}/beneficios`]: [benefit], [`GET ${BASE}/estudiantes`]: page([student]), ...routes });
}
async function tab(user, label) { await user.click(screen.getByRole("button", { name: label, exact: true })); }

test("crea un concepto genérico con aceptación opcional y conserva la configuración enviada", async () => {
  const calls = backend({ [`POST ${BASE}/conceptos`]: materials }); const user = userEvent.setup();
  render(<PaymentSettings canWrite />); await user.click(screen.getByRole("button", { name: "Nuevo concepto" }));
  await user.type(screen.getByLabelText(/Código del concepto/), "visita_cultural");
  await user.type(screen.getByLabelText(/Nombre del concepto/), "Visita cultural");
  await user.type(screen.getByLabelText(/Importe sugerido/), "55.50");
  await user.type(screen.getByLabelText(/Descripción/), "Servicio opcional de visita");
  await user.click(screen.getByRole("checkbox"));
  await user.click(screen.getByRole("button", { name: "Guardar concepto" }));
  expect(await screen.findByText("Concepto guardado.")).toBeTruthy();
  expect(calls.find((call) => call.method === "POST").body).toMatchObject({ codigo: "VISITA_CULTURAL", nombre: "Visita cultural", montoSugerido: 55.5, periodicidad: "UNICO", requiereAceptacion: true, esPension: false });
});

test("un concepto mensual conserva su frecuencia sin precio sugerido ni aceptación opcional", async () => {
  let reject = true;
  const calls = backend({ [`POST ${BASE}/conceptos`]: () => reject ? reply(409, { message: "El código ya existe." }) : materials });
  const user = userEvent.setup(); render(<PaymentSettings canWrite />);
  await user.click(screen.getByRole("button", { name: "Nuevo concepto" }));
  await user.type(screen.getByLabelText(/Código del concepto/), "SERVICIO"); await user.type(screen.getByLabelText(/Nombre del concepto/), "Servicio adicional");
  await user.selectOptions(screen.getByLabelText("Frecuencia"), "MENSUAL");
  expect(screen.getByRole("checkbox").disabled).toBe(true);
  await user.click(screen.getByRole("button", { name: "Guardar concepto" }));
  expect(await screen.findByText("El código ya existe.")).toBeTruthy();
  expect(screen.getByLabelText(/Nombre del concepto/).value).toBe("Servicio adicional"); reject = false;
  await user.click(screen.getByRole("button", { name: "Guardar concepto" })); await screen.findByText("Concepto guardado.");
  expect(calls.filter((call) => call.method === "POST").at(-1).body).toMatchObject({ periodicidad: "MENSUAL", montoSugerido: null, requiereAceptacion: false });
});

test("editar la pensión conserva su identidad y frecuencia y envía la versión", async () => {
  const calls = backend({ [`PUT ${BASE}/conceptos/1`]: pension }); const user = userEvent.setup(); render(<PaymentSettings canWrite />);
  await user.click(await screen.findByRole("button", { name: "Editar concepto Pensión" }));
  expect(screen.getByLabelText(/Código del concepto/).disabled).toBe(true); expect(screen.getByLabelText("Frecuencia").disabled).toBe(true);
  expect(screen.queryByLabelText(/Importe sugerido/)).toBeNull(); expect(screen.queryByRole("checkbox")).toBeNull();
  await user.clear(screen.getByLabelText(/Nombre del concepto/)); await user.type(screen.getByLabelText(/Nombre del concepto/), "Pensión escolar");
  await user.click(screen.getByRole("button", { name: "Guardar concepto" })); await screen.findByText("Concepto guardado.");
  expect(calls.find((call) => call.method === "PUT").body).toMatchObject({ codigo: "PENSION", nombre: "Pensión escolar", periodicidad: "MENSUAL", esPension: true, montoSugerido: null, version: 2 });
});

test("desactiva un concepto con motivo sin borrar los anteriores y permite cancelar la acción", async () => {
  const calls = backend({ [`DELETE ${BASE}/conceptos/2`]: null }); const user = userEvent.setup(); render(<PaymentSettings canWrite />);
  await user.click(await screen.findByRole("button", { name: "Desactivar concepto Materiales" }));
  await user.click(screen.getByRole("button", { name: "Cancelar", exact: true })); expect(screen.queryByRole("dialog")).toBeNull();
  await user.click(screen.getByRole("button", { name: "Desactivar concepto Materiales" }));
  await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Desactivar concepto" }));
  expect(screen.getByText("Explica el motivo para registrar el cambio.")).toBeTruthy();
  await user.type(screen.getByLabelText(/Motivo/), "Concepto fuera de uso");
  await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Desactivar concepto" }));
  expect(await screen.findByText(/Concepto desactivado/)).toBeTruthy();
  expect(calls.find((call) => call.method === "DELETE").query).toMatchObject({ version: "3", motivo: "Concepto fuera de uso" });
});

test("la consulta sin escritura oculta acciones y puede recuperar un listado vacío", async () => {
  let fail = true; backend({ [`GET ${BASE}/conceptos`]: () => fail ? reply(503, { message: "Conceptos no disponibles" }) : [] });
  const user = userEvent.setup(); render(<PaymentSettings canWrite={false} />);
  expect(await screen.findByText("Conceptos no disponibles")).toBeTruthy(); expect(screen.queryByRole("button", { name: "Nuevo concepto" })).toBeNull();
  fail = false; await user.click(screen.getByRole("button", { name: "Reintentar conceptos" })); expect(await screen.findByText("Todavía no hay conceptos registrados.")).toBeTruthy();
});

test("configura una tarifa por año y grado y bloquea el envío repetido mientras guarda", async () => {
  const calls = backend({ [`POST ${BASE}/tarifas`]: rate }); const user = userEvent.setup(); render(<PaymentSettings canWrite />);
  await tab(user, "Tarifas por año y grado");
  await waitFor(() => expect(screen.getByRole("button", { name: "Nueva tarifa" }).disabled).toBe(false));
  await user.click(screen.getByRole("button", { name: "Nueva tarifa" }));
  await user.selectOptions(screen.getByLabelText(/Año académico \*/i), "2028"); await user.selectOptions(screen.getByLabelText(/Grado \*/), "2");
  await user.type(screen.getByLabelText(/Pensión mensual en soles/), "430"); await user.click(screen.getByRole("button", { name: "Guardar tarifa" }));
  expect(await screen.findByText(/Tarifa guardada/)).toBeTruthy(); expect(calls.find((call) => call.method === "POST").body).toMatchObject({ idConcepto: 1, anioAcademico: 2028, grado: 2, montoMensual: 430 });
  await user.selectOptions(screen.getByLabelText("Filtrar año académico"), "2027");
  expect(screen.getByText("1° de secundaria")).toBeTruthy(); await user.click(screen.getByRole("button", { name: "Actualizar tarifas" }));
});

test("editar o desactivar una tarifa conserva año y grado y muestra conflictos del servidor", async () => {
  let fail = true;
  const calls = backend({ [`PUT ${BASE}/tarifas/4`]: () => fail ? reply(409, { message: "La tarifa cambió; actualiza la lista." }) : rate, [`DELETE ${BASE}/tarifas/4`]: null });
  const user = userEvent.setup(); render(<PaymentSettings canWrite />); await tab(user, "Tarifas por año y grado");
  await user.click(await screen.findByRole("button", { name: "Editar tarifa 2027 grado 1" }));
  expect(screen.getByLabelText(/Año académico \*/i).disabled).toBe(true); expect(screen.getByLabelText(/Grado \*/).disabled).toBe(true);
  await user.clear(screen.getByLabelText(/Pensión mensual en soles/)); await user.type(screen.getByLabelText(/Pensión mensual en soles/), "450");
  await user.click(screen.getByRole("button", { name: "Guardar tarifa" })); await screen.findByText("La tarifa cambió; actualiza la lista."); fail = false;
  await user.click(screen.getByRole("button", { name: "Guardar tarifa" })); await screen.findByText(/Tarifa guardada/);
  expect(calls.filter((call) => call.method === "PUT").at(-1).body).toMatchObject({ anioAcademico: 2027, grado: 1, montoMensual: 450, version: 5 });
  await user.click(await screen.findByRole("button", { name: "Desactivar tarifa 2027 grado 1" })); await user.type(screen.getByLabelText(/Motivo/), "Revisión de tarifas"); await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Desactivar tarifa" }));
  await screen.findByText("Tarifa desactivada.");
});

test("sin año académico no permite crear tarifas y recupera la configuración", async () => {
  let fail = true; backend({ [`GET ${BASE}/anios`]: () => fail ? reply(503, { message: "Años no disponibles" }) : [], [`GET ${BASE}/tarifas`]: [] });
  const user = userEvent.setup(); render(<PaymentSettings canWrite />); await tab(user, "Tarifas por año y grado");
  await screen.findByText("Años no disponibles"); fail = false; await user.click(screen.getByRole("button", { name: "Reintentar configuración" }));
  expect(await screen.findByText("Primero registra el año académico para configurar sus tarifas.")).toBeTruthy(); expect(screen.getByRole("button", { name: "Nueva tarifa" }).disabled).toBe(true);
  expect(screen.getByText("No hay tarifas para esta consulta.")).toBeTruthy();
});

test("asigna un beneficio a un estudiante antes de matricularlo y valida la vigencia", async () => {
  const calls = backend({ [`POST ${BASE}/beneficios`]: benefit }); const user = userEvent.setup(); render(<PaymentSettings canWrite />);
  await tab(user, "Becas y descuentos"); await waitFor(() => expect(screen.getByRole("button", { name: "Asignar beneficio" }).disabled).toBe(false));
  await user.click(screen.getByRole("button", { name: "Asignar beneficio" }));
  await user.type(screen.getByLabelText("Buscar estudiante", { exact: true }), "Ana"); await user.click(screen.getByRole("button", { name: "Buscar estudiante", exact: true }));
  await user.click(await screen.findByRole("button", { name: "Elegir a Ana Flores" }));
  await user.type(screen.getByLabelText(/Nombre del beneficio/), "Beca aprobada"); await user.selectOptions(screen.getByLabelText(/Año académico \*/i), "2027");
  await user.type(screen.getByLabelText(/Porcentaje de descuento/), "25"); await user.selectOptions(screen.getByLabelText(/Mes de inicio/), "6"); await user.selectOptions(screen.getByLabelText(/Mes de fin/), "4");
  await user.type(screen.getByLabelText(/Motivo de la asignación/), "Resolución de beca"); await user.click(screen.getByRole("button", { name: "Guardar beneficio" }));
  expect(screen.getByText(/El mes de inicio debe ser anterior/)).toBeTruthy(); expect(calls.some((call) => call.method === "POST")).toBe(false);
  await user.selectOptions(screen.getByLabelText(/Mes de fin/), "12"); await user.click(screen.getByRole("button", { name: "Guardar beneficio" }));
  await screen.findByText(/Beneficio guardado/); expect(calls.find((call) => call.method === "POST").body).toMatchObject({ idAlumno: 9, idConcepto: 1, tipo: "PORCENTAJE", valor: 25, mesInicio: 6, mesFin: 12, motivo: "Resolución de beca" });
});

test("editar un beneficio permite importe fijo y conserva beneficiario y concepto", async () => {
  const calls = backend({ [`PUT ${BASE}/beneficios/7`]: benefit }); const user = userEvent.setup(); render(<PaymentSettings canWrite />); await tab(user, "Becas y descuentos");
  await user.click(await screen.findByRole("button", { name: "Editar beneficio Beca parcial de Ana Flores" }));
  expect(screen.queryByRole("button", { name: "Cambiar estudiante" })).toBeNull();
  await user.selectOptions(screen.getByLabelText(/Tipo de beneficio/), "IMPORTE_FIJO"); await user.clear(screen.getByLabelText(/Descuento por cobro/)); await user.type(screen.getByLabelText(/Descuento por cobro/), "80");
  await user.click(screen.getByRole("button", { name: "Guardar beneficio" })); await screen.findByText(/Beneficio guardado/);
  expect(calls.find((call) => call.method === "PUT").body).toMatchObject({ idAlumno: 9, idConcepto: 1, tipo: "IMPORTE_FIJO", valor: 80, version: 6 });
});

test("aplica una beca tras revisar el importe y preserva cuotas pagadas", async () => {
  const calls = backend({ [`GET ${BASE}/beneficios/7/preview`]: preview, [`POST ${BASE}/beneficios/7/aplicar`]: { ...preview, mensaje: "Beca aplicada a la cuota pendiente." } });
  const user = userEvent.setup(); render(<PaymentSettings canWrite />); await tab(user, "Becas y descuentos");
  await user.click(await screen.findByRole("button", { name: "Revisar aplicación de Beca parcial a Ana Flores" }));
  await screen.findByText("Obligaciones que se actualizarán"); expect(await screen.findByText("Con pagos registrados")).toBeTruthy();
  await user.type(screen.getByLabelText(/Motivo del ajuste/), "Aplicar beca aprobada"); await user.click(screen.getByRole("button", { name: "Aplicar a las obligaciones indicadas" }));
  await screen.findByText("Beca aplicada a la cuota pendiente."); expect(calls.find((call) => call.method === "POST").body).toEqual({ motivo: "Aplicar beca aprobada", version: 6, firmaAjuste: "firma-vigente" });
});

test("si cambia la cotización del beneficio recarga la revisión y exige confirmar otra vez", async () => {
  let revision = 0;
  const calls = backend({ [`GET ${BASE}/beneficios/7/preview`]: () => ({ ...preview, firmaAjuste: revision ? "firma-nueva" : "firma-vigente", ajustables: [{ ...commitment, montoTotal: revision ? 0 : 300 }] }), [`POST ${BASE}/beneficios/7/aplicar`]: () => { revision++; return reply(409, { code: "AJUSTE_ACTUALIZADO", message: "Los importes se actualizaron." }); } });
  const user = userEvent.setup(); render(<PaymentSettings canWrite />); await tab(user, "Becas y descuentos"); await user.click(await screen.findByRole("button", { name: "Revisar aplicación de Beca parcial a Ana Flores" }));
  await user.type(await screen.findByLabelText(/Motivo del ajuste/), "Revisar ajuste"); await user.click(screen.getByRole("button", { name: "Aplicar a las obligaciones indicadas" }));
  expect(await screen.findByText("Exonerado")).toBeTruthy(); expect(screen.getByText(/Los importes cambiaron/)).toBeTruthy(); expect(calls.filter((call) => call.method === "POST")).toHaveLength(1);
});

test("recupera el preview sin obligaciones ajustables y permite desactivar el beneficio", async () => {
  let fail = true; const calls = backend({ [`GET ${BASE}/beneficios/7/preview`]: () => fail ? reply(503, { message: "Revisión no disponible" }) : { ...preview, ajustables: [], omitidos: [{ ...commitment, montoPagado: 0 }] }, [`DELETE ${BASE}/beneficios/7`]: null });
  const user = userEvent.setup(); render(<PaymentSettings canWrite />); await tab(user, "Becas y descuentos"); await user.click(await screen.findByRole("button", { name: "Revisar aplicación de Beca parcial a Ana Flores" }));
  await screen.findByText("Revisión no disponible"); fail = false; await user.click(screen.getByRole("button", { name: "Reintentar revisión" })); await screen.findByText("No hay obligaciones disponibles para ajustar."); expect(screen.queryByRole("button", { name: "Aplicar a las obligaciones indicadas" })).toBeNull();
  await user.click(screen.getByRole("button", { name: "Cerrar", exact: true })); await user.click(screen.getByRole("button", { name: "Desactivar beneficio Beca parcial de Ana Flores" })); await user.type(screen.getByLabelText(/Motivo/), "Fin del beneficio");
  await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Desactivar beneficio" })); await screen.findByText(/Beneficio desactivado/); expect(calls.find((call) => call.method === "DELETE").query.version).toBe("6");
});

test("recupera la búsqueda y exige elegir de nuevo al estudiante cuando se cambia el beneficiario", async () => {
  let unavailable = true;
  const calls = backend({
    [`GET ${BASE}/estudiantes`]: ({ query }) => unavailable ? reply(503, { message: "Búsqueda de estudiantes no disponible" }) : page(query.busqueda === "Ana" ? [student] : []),
    [`POST ${BASE}/beneficios`]: reply(409, { message: "Ya existe un beneficio para ese periodo." }),
  });
  const user = userEvent.setup(); render(<PaymentSettings canWrite />); await tab(user, "Becas y descuentos");
  await waitFor(() => expect(screen.getByRole("button", { name: "Asignar beneficio" }).disabled).toBe(false));
  await user.click(screen.getByRole("button", { name: "Asignar beneficio" }));
  await screen.findByText("Búsqueda de estudiantes no disponible"); unavailable = false;
  await user.click(screen.getByRole("button", { name: "Reintentar estudiantes" }));
  await screen.findByText("No hay estudiantes que coincidan con la búsqueda.");
  await user.type(screen.getByLabelText("Buscar estudiante", { exact: true }), "Ana{Enter}");
  await user.click(await screen.findByRole("button", { name: "Elegir a Ana Flores" }));
  await user.click(screen.getByRole("button", { name: "Cambiar estudiante" }));
  await user.type(screen.getByLabelText(/Nombre del beneficio/), "Beca adicional");
  await user.selectOptions(screen.getByLabelText(/Año académico \*/i), "2027");
  await user.type(screen.getByLabelText(/Porcentaje de descuento/), "15");
  await user.type(screen.getByLabelText(/Motivo de la asignación/), "Solicitud aprobada");
  await user.click(screen.getByRole("button", { name: "Guardar beneficio" }));
  expect(screen.getByText("Selecciona el estudiante que recibirá el beneficio.")).toBeTruthy();
  expect(calls.some((call) => call.method === "POST")).toBe(false);
  await user.click(screen.getByRole("button", { name: "Elegir a Ana Flores" }));
  await user.click(screen.getByRole("button", { name: "Guardar beneficio" }));
  await screen.findByText("Ya existe un beneficio para ese periodo.");
  expect(screen.getByLabelText(/Nombre del beneficio/).value).toBe("Beca adicional");
  expect(calls.filter((call) => call.method === "POST")).toHaveLength(1);
  expect(calls.find((call) => call.path === `${BASE}/estudiantes` && call.query.busqueda === "Ana").query.size).toBe("5");
  await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Cancelar", exact: true }));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("un fallo al aplicar la beca conserva la revisión y el motivo para reintentar sin alterar lo cotizado", async () => {
  let fail = true;
  const calls = backend({
    [`GET ${BASE}/beneficios/7/preview`]: preview,
    [`POST ${BASE}/beneficios/7/aplicar`]: () => fail ? reply(503, { message: "No se pudo registrar el ajuste." }) : { ...preview, mensaje: "Ajuste registrado." },
  });
  const user = userEvent.setup(); render(<PaymentSettings canWrite />); await tab(user, "Becas y descuentos");
  await user.click(await screen.findByRole("button", { name: "Revisar aplicación de Beca parcial a Ana Flores" }));
  await user.type(await screen.findByLabelText(/Motivo del ajuste/), "Resolución revisada");
  await user.click(screen.getByRole("button", { name: "Aplicar a las obligaciones indicadas" }));
  await screen.findByText("No se pudo registrar el ajuste.");
  expect(screen.getByLabelText(/Motivo del ajuste/).value).toBe("Resolución revisada");
  expect(screen.getByText("Con pagos registrados")).toBeTruthy(); fail = false;
  await user.click(screen.getByRole("button", { name: "Aplicar a las obligaciones indicadas" })); await screen.findByText("Ajuste registrado.");
  const attempts = calls.filter((call) => call.method === "POST");
  expect(attempts).toHaveLength(2); expect(attempts[1].body).toEqual(attempts[0].body);
  expect(calls.filter((call) => call.path.endsWith("/preview"))).toHaveLength(1);
});

test("un rechazo al desactivar conserva el motivo y permite corregir la operación sin perder el diálogo", async () => {
  let fail = true;
  const calls = backend({ [`DELETE ${BASE}/conceptos/2`]: () => fail ? reply(409, { message: "El concepto está siendo actualizado." }) : null });
  const user = userEvent.setup(); render(<PaymentSettings canWrite />);
  await user.click(await screen.findByRole("button", { name: "Desactivar concepto Materiales" }));
  await user.type(screen.getByLabelText(/Motivo/), "  Reemplazo de concepto  ");
  await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Desactivar concepto" }));
  await screen.findByText("El concepto está siendo actualizado."); expect(screen.getByLabelText(/Motivo/).value).toBe("  Reemplazo de concepto  "); fail = false;
  await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Desactivar concepto" })); await screen.findByText(/Concepto desactivado/);
  expect(calls.filter((call) => call.method === "DELETE").at(-1).query.motivo).toBe("Reemplazo de concepto");
});

test("la consulta de tarifas y beneficios inactivos no permite administrar ni aplicar descuentos", async () => {
  const calls = backend({ [`GET ${BASE}/tarifas`]: [{ ...rate, activo: false }], [`GET ${BASE}/beneficios`]: [{ ...benefit, tipo: "IMPORTE_FIJO", valor: 80, activo: false }] });
  const user = userEvent.setup(); render(<PaymentSettings canWrite={false} />); await tab(user, "Tarifas por año y grado");
  await screen.findByText("1° de secundaria"); expect(screen.getByText("Inactivo")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Nueva tarifa" })).toBeNull(); expect(screen.queryByRole("columnheader", { name: "Acciones" })).toBeNull();
  await tab(user, "Becas y descuentos"); await screen.findByText(/por cobro$/);
  expect(screen.getByText("Inactivo")).toBeTruthy(); expect(screen.queryByRole("button", { name: "Asignar beneficio" })).toBeNull();
  expect(screen.queryByRole("button", { name: /Revisar aplicación/ })).toBeNull(); expect(screen.queryByRole("columnheader", { name: "Acciones" })).toBeNull();
  expect(calls.every((call) => call.method === "GET")).toBe(true);
});

test("reactivar conceptos, tarifas y becas requiere una acción explícita y conserva sus condiciones", async () => {
  let conceptActive = false, rateActive = false, benefitActive = false;
  const calls = backend({
    [`GET ${BASE}/conceptos`]: () => [pension, { ...materials, activo: conceptActive }],
    [`GET ${BASE}/tarifas`]: () => [{ ...rate, activo: rateActive }],
    [`GET ${BASE}/beneficios`]: () => [{ ...benefit, activo: benefitActive }],
    [`PUT ${BASE}/conceptos/2`]: () => { conceptActive = true; return { ...materials, activo: true }; },
    [`PUT ${BASE}/tarifas/4`]: () => { rateActive = true; return { ...rate, activo: true }; },
    [`PUT ${BASE}/beneficios/7`]: () => { benefitActive = true; return { ...benefit, activo: true }; },
  });
  const user = userEvent.setup(); render(<PaymentSettings canWrite />);
  await user.click(await screen.findByRole("button", { name: "Reactivar concepto Materiales" }));
  expect(screen.getByRole("dialog", { name: "Reactivar concepto de cobro" })).toBeTruthy();
  expect(screen.getByText(/este concepto volverá a estar disponible/)).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Cancelar", exact: true })); expect(calls.some((call) => call.method === "PUT")).toBe(false);
  await user.click(screen.getByRole("button", { name: "Reactivar concepto Materiales" }));
  await user.click(screen.getByRole("button", { name: "Guardar y reactivar" }));
  await screen.findByRole("button", { name: "Editar concepto Materiales" });
  expect(calls.find((call) => call.path === `${BASE}/conceptos/2` && call.method === "PUT").body).toMatchObject({ codigo: "MATERIAL", activo: true, version: 3 });

  await tab(user, "Tarifas por año y grado"); await user.click(await screen.findByRole("button", { name: "Reactivar tarifa 2027 grado 1" }));
  expect(screen.getByRole("dialog", { name: "Reactivar tarifa de pensión" })).toBeTruthy();
  expect(screen.getByText(/esta tarifa volverá a utilizarse/)).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Guardar y reactivar" })); await screen.findByRole("button", { name: "Editar tarifa 2027 grado 1" });
  expect(calls.find((call) => call.path === `${BASE}/tarifas/4` && call.method === "PUT").body).toMatchObject({ anioAcademico: 2027, grado: 1, montoMensual: 400, activo: true, version: 5 });

  await tab(user, "Becas y descuentos"); expect(await screen.findByRole("button", { name: "Reactivar beneficio Beca parcial de Ana Flores" })).toBeTruthy();
  expect(screen.queryByRole("button", { name: /Revisar aplicación/ })).toBeNull();
  await user.click(screen.getByRole("button", { name: "Reactivar beneficio Beca parcial de Ana Flores" }));
  expect(screen.getByRole("dialog", { name: "Reactivar beneficio del estudiante" })).toBeTruthy();
  expect(screen.getByText(/este beneficio volverá a aplicarse a nuevas obligaciones/)).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Guardar y reactivar" })); await screen.findByRole("button", { name: "Revisar aplicación de Beca parcial a Ana Flores" });
  expect(calls.find((call) => call.path === `${BASE}/beneficios/7` && call.method === "PUT").body).toMatchObject({ idAlumno: 9, idConcepto: 1, mesInicio: 3, mesFin: 12, valor: 25, activo: true, version: 6 });
});

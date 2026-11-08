import { beforeEach, expect, test, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { Finance } from "../finance";
import { CommitmentForm } from "../commitment-form";
import { dueDateLabel, moneyLabel } from "../commitment-format";

const record = {
  id: 12, idMatricula: 7, idAlumno: 3, alumno: "Lucía Flores", codigoAlumno: "EST-003",
  codigoMatricula: "MAT-007", anioAcademico: 2026, codigoCompromiso: "CP-012",
  concepto: "Pensión de marzo", numeroCuota: 1, descripcion: "Mensualidad escolar", montoTotal: 350,
  montoPagado: 0, saldo: 350, moneda: "PEN", fechaVencimiento: "2026-03-10",
  estado: "PENDIENTE", estadoFinanciero: "PENDIENTE", estadoAdministrativo: "ACTIVO",
  fechaCreacion: "2026-03-01T12:00:00Z", fechaActualizacion: "2026-03-01T12:00:00Z",
  motivoAnulacion: null, fechaAnulacion: null, version: 2,
};
const enrollment = { id: 7, codigoMatricula: "MAT-007", alumno: "Lucía Flores", codigoAlumno: "EST-003", alumnoId: 3, anioAcademico: 2026, grado: 1, seccion: "A", estado: "ACTIVA" };

beforeEach(() => signIn({ permissions: ["PAGOS_LEER", "PAGOS_ESCRIBIR"] }));

async function openDetail(user) {
  await user.click(await screen.findByRole("button", { name: "Ver compromiso CP-012" }));
  await screen.findByRole("button", { name: "Modificar monto o vencimiento" });
}

async function fillIssue(user) {
  await user.click(await screen.findByRole("button", { name: "Elegir matrícula de Lucía Flores" }));
  await user.type(screen.getByLabelText(/Concepto/), "Pensión de marzo");
  await user.type(screen.getByLabelText(/Número de cuota/), "1");
  await user.type(screen.getByLabelText(/Monto en soles/), "350.50");
  fireEvent.change(screen.getByLabelText(/Fecha de vencimiento/), { target: { value: "2026-03-10" } });
}

test("consulta compromisos, aplica filtros y pagina sin depender de edición", async () => {
  const calls = fakeBackend({ "GET /api/compromisos-pago": ({ query }) => ({ ...page([record], 2), number: Number(query.page), page: Number(query.page), totalElements: 30 }) });
  const user = userEvent.setup();
  render(<Finance canWrite={false} />);
  expect(await screen.findByText("Lucía Flores")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Emitir compromiso" })).toBeNull();
  expect(screen.getByText("Cuota 1 · CP-012")).toBeTruthy();
  await user.type(screen.getByLabelText("Buscar estudiante o compromiso"), "Lucía");
  await user.type(screen.getByLabelText("Año académico"), "2026");
  await user.selectOptions(screen.getByLabelText("Estado del compromiso"), "PENDIENTE");
  await user.click(screen.getByRole("button", { name: "Buscar", exact: true }));
  await waitFor(() => expect(calls.at(-1).query).toMatchObject({ busqueda: "Lucía", anioAcademico: "2026", estado: "PENDIENTE", page: "0" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "Siguiente" }).disabled).toBe(false));
  await user.click(screen.getByRole("button", { name: "Siguiente" }));
  await waitFor(() => expect(calls.at(-1).query.page).toBe("1"));
  await user.click(screen.getByRole("button", { name: "Actualizar" }));
  await waitFor(() => expect(calls.filter((call) => call.query.page === "1")).toHaveLength(2));
  await user.selectOptions(screen.getByLabelText("Estado del compromiso"), "ANULADO");
  await user.click(screen.getByRole("button", { name: "Buscar", exact: true }));
  await waitFor(() => expect(calls.at(-1).query).toMatchObject({ estado: "ANULADO", page: "0" }));
  expect(calls.at(-1).query.estadoAdministrativo).toBeUndefined();
});

test("informa un fallo del listado y permite recuperar una consulta vacía", async () => {
  let failing = true;
  fakeBackend({ "GET /api/compromisos-pago": () => failing ? reply(503, { message: "Servicio temporalmente no disponible" }) : page([]) });
  const user = userEvent.setup();
  render(<Finance canWrite />);
  expect(await screen.findByText("Servicio temporalmente no disponible")).toBeTruthy();
  failing = false;
  await user.click(screen.getByRole("button", { name: "Actualizar" }));
  expect(await screen.findByText("No hay compromisos que coincidan con la consulta.")).toBeTruthy();
});

test("un compromiso pagado muestra su monto y un único estado aunque siga vigente internamente", async () => {
  const paid = { ...record, montoPagado: 350, saldo: 0, estadoFinanciero: "PAGADO" };
  fakeBackend({ "GET /api/compromisos-pago": page([paid]), "GET /api/compromisos-pago/12": paid });
  const user = userEvent.setup();
  render(<Finance canWrite />);
  await screen.findByText("Lucía Flores");
  expect(screen.getByRole("columnheader", { name: "Monto", exact: true })).toBeTruthy();
  expect(screen.queryByRole("columnheader", { name: "Saldo" })).toBeNull();
  expect(screen.queryByText("Activo", { exact: true })).toBeNull();
  expect(screen.getByText("Pagado", { selector: ".status" })).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Ver compromiso CP-012" }));
  const detail = await screen.findByRole("dialog");
  await within(detail).findByText("Pagado");
  expect(within(detail).queryByText("Importe pendiente")).toBeNull();
  expect(within(detail).queryByText("Activo")).toBeNull();
  expect(within(detail).getByText(/^S\/\s350\.00$/)).toBeTruthy();
  expect(within(detail).queryByRole("button", { name: "Modificar monto o vencimiento" })).toBeNull();
});

test("emite en soles para la matrícula elegida y actualiza el listado", async () => {
  let issued = false;
  const calls = fakeBackend({
    "GET /api/compromisos-pago": () => page(issued ? [record] : []),
    "GET /api/compromisos-pago/matriculas": page([enrollment]),
    "POST /api/compromisos-pago": () => { issued = true; return { ...record, montoTotal: 350.5 }; },
  });
  const user = userEvent.setup();
  render(<Finance canWrite />);
  await user.click(screen.getByRole("button", { name: "Emitir compromiso" }));
  await fillIssue(user);
  await user.type(screen.getByLabelText("Descripción (opcional)"), " Pago mensual ");
  await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Emitir compromiso" }));
  expect(await screen.findByText("Compromiso CP-012 emitido.")).toBeTruthy();
  expect(calls.find((call) => call.method === "POST").body).toEqual({ idMatricula: 7, concepto: "Pensión de marzo", numeroCuota: 1, montoTotal: 350.5, moneda: "PEN", fechaVencimiento: "2026-03-10", descripcion: "Pago mensual" });
  expect(await screen.findByText("Lucía Flores")).toBeTruthy();
});

test("valida los campos antes de emitir y lleva el foco al dato faltante", async () => {
  const calls = fakeBackend({ "GET /api/compromisos-pago/matriculas": page([enrollment]) });
  const user = userEvent.setup();
  render(<CommitmentForm onClose={vi.fn()} onSaved={vi.fn()} />);
  await user.click(screen.getByRole("button", { name: "Emitir compromiso" }));
  expect(screen.getByText("Selecciona la matrícula del estudiante.")).toBeTruthy();
  expect(document.activeElement).toBe(screen.getByLabelText("Buscar estudiante con matrícula vigente"));
  expect(screen.getByText("Escribe el concepto del compromiso.")).toBeTruthy();
  await user.click(await screen.findByRole("button", { name: "Elegir matrícula de Lucía Flores" }));
  await user.type(screen.getByLabelText(/Concepto/), "Pensión");
  await user.type(screen.getByLabelText(/Número de cuota/), "0");
  await user.type(screen.getByLabelText(/Monto en soles/), "-1");
  await user.click(screen.getByRole("button", { name: "Emitir compromiso" }));
  expect(document.activeElement).toBe(screen.getByLabelText(/Número de cuota/));
  expect(screen.getByText("Ingresa un monto mayor que cero, con hasta dos decimales.")).toBeTruthy();
  expect(calls.some((call) => call.method === "POST")).toBe(false);
});

test("muestra cinco matrículas por página, busca con teclado y permite elegir en la segunda", async () => {
  const enrollments = Array.from({ length: 7 }, (_, index) => ({
    ...enrollment,
    id: index + 1,
    alumnoId: index + 1,
    alumno: `Estudiante ${index + 1}`,
    codigoAlumno: `EST-00${index + 1}`,
    codigoMatricula: `MAT-00${index + 1}`,
  }));
  const calls = fakeBackend({ "GET /api/compromisos-pago/matriculas": ({ query }) => {
    const filtered = enrollments.filter((item) => item.codigoAlumno.includes(query.busqueda));
    const pageNumber = Number(query.page);
    const pageSize = Number(query.size);
    return {
      content: filtered.slice(pageNumber * pageSize, (pageNumber + 1) * pageSize),
      page: pageNumber,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / pageSize),
    };
  } });
  const user = userEvent.setup();
  render(<CommitmentForm onClose={vi.fn()} onSaved={vi.fn()} />);
  const choices = () => within(screen.getByRole("list", { name: "Matrículas disponibles" }));
  await screen.findByRole("button", { name: "Elegir matrícula de Estudiante 1" });
  expect(choices().getAllByRole("listitem")).toHaveLength(5);
  expect(calls.at(-1).query).toMatchObject({ page: "0", size: "5" });
  expect(screen.queryByRole("button", { name: "Elegir matrícula de Estudiante 6" })).toBeNull();
  await user.type(screen.getByLabelText("Buscar estudiante con matrícula vigente"), "EST-{Enter}");
  await waitFor(() => expect(calls.at(-1).query.busqueda).toBe("EST-"));
  await waitFor(() => expect(screen.getByRole("button", { name: "Siguiente" }).disabled).toBe(false));
  await user.click(screen.getByRole("button", { name: "Siguiente" }));
  await screen.findByRole("button", { name: "Elegir matrícula de Estudiante 7" });
  expect(choices().getAllByRole("listitem")).toHaveLength(2);
  expect(calls.at(-1).query).toMatchObject({ page: "1", size: "5", busqueda: "EST-" });
  expect(screen.getByRole("button", { name: "Siguiente" }).disabled).toBe(true);
  expect(screen.queryByText("Selecciona la matrícula del estudiante.")).toBeNull();
  expect(screen.queryByText("Escribe el concepto del compromiso.")).toBeNull();
  await user.click(screen.getByRole("button", { name: "Anterior" }));
  await screen.findByRole("button", { name: "Elegir matrícula de Estudiante 1" });
  expect(choices().getAllByRole("listitem")).toHaveLength(5);
  expect(screen.queryByText("Selecciona la matrícula del estudiante.")).toBeNull();
  await user.click(screen.getByRole("button", { name: "Siguiente" }));
  await user.click(await screen.findByRole("button", { name: "Elegir matrícula de Estudiante 7" }));
  expect(screen.getByText("Matrícula MAT-007")).toBeTruthy();
  expect(screen.getByText("Estudiante 7")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Cambiar matrícula" }));
  await user.clear(screen.getByLabelText("Buscar estudiante con matrícula vigente"));
  await user.type(screen.getByLabelText("Buscar estudiante con matrícula vigente"), "EST-007");
  await user.click(screen.getByRole("button", { name: "Buscar matrícula" }));
  await waitFor(() => expect(calls.at(-1).query).toMatchObject({ busqueda: "EST-007", page: "0", size: "5" }));
  await waitFor(() => expect(choices().getAllByRole("listitem")).toHaveLength(1));
  expect(screen.queryByText("Selecciona la matrícula del estudiante.")).toBeNull();
  expect(calls.some((call) => call.method !== "GET")).toBe(false);
});

test("recupera errores del selector y muestra ausencia de matrículas vigentes", async () => {
  let failing = true;
  fakeBackend({ "GET /api/compromisos-pago/matriculas": () => failing ? reply(503, { message: "No se pudo consultar las matrículas" }) : page([]) });
  const user = userEvent.setup();
  render(<CommitmentForm onClose={vi.fn()} onSaved={vi.fn()} />);
  await screen.findByText("No se pudo consultar las matrículas");
  failing = false;
  await user.click(screen.getByRole("button", { name: "Reintentar búsqueda" }));
  expect(await screen.findByText("No hay matrículas vigentes que coincidan con la búsqueda.")).toBeTruthy();
});

test("protege los cambios al cerrar y permite seguir editando o descartarlos", async () => {
  const calls = fakeBackend({
    "GET /api/compromisos-pago/matriculas": ({ query }) => ({
      ...page([enrollment], 2),
      page: Number(query.page),
      totalElements: 30,
    }),
  });
  const close = vi.fn();
  const user = userEvent.setup();
  render(<CommitmentForm onClose={close} onSaved={vi.fn()} />);
  const formDialog = screen.getByRole("dialog", { name: "Emitir compromiso de pago" });
  const search = screen.getByLabelText("Buscar estudiante con matrícula vigente");
  await screen.findByRole("button", { name: "Elegir matrícula de Lucía Flores" });
  await user.type(search, "EST-003{Enter}");
  await waitFor(() => expect(calls.at(-1).query.busqueda).toBe("EST-003"));
  await waitFor(() => expect(screen.getByRole("button", { name: "Siguiente" }).disabled).toBe(false));
  await user.click(screen.getByRole("button", { name: "Siguiente" }));
  await waitFor(() => expect(calls.at(-1).query.page).toBe("1"));
  await waitFor(() => expect(screen.getByRole("button", { name: "Anterior" }).disabled).toBe(false));
  const concept = screen.getByLabelText(/Concepto/);
  await user.type(concept, "Pensión");
  const cancel = screen.getByRole("button", { name: "Cancelar" });
  await user.click(cancel);

  let confirmation = screen.getByRole("alertdialog", { name: "¿Descartar los cambios?" });
  expect(within(confirmation).getByText("Los cambios que todavía no guardaste se perderán.")).toBeTruthy();
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(document.contains(formDialog)).toBe(true);
  expect(close).not.toHaveBeenCalled();
  let keepEditing = within(confirmation).getByRole("button", { name: "Seguir editando" });
  const discard = within(confirmation).getByRole("button", { name: "Descartar cambios" });
  const closeConfirmation = within(confirmation).getByRole("button", { name: "Cerrar ventana" });
  await waitFor(() => expect(document.activeElement).toBe(keepEditing));
  await user.tab();
  expect(document.activeElement).toBe(discard);
  await user.tab();
  expect(document.activeElement).toBe(closeConfirmation);
  await user.tab();
  expect(document.activeElement).toBe(keepEditing);
  await user.tab({ shift: true });
  expect(document.activeElement).toBe(closeConfirmation);

  const requestsBeforeClosing = calls.length;
  await user.keyboard("{Escape}");
  expect(screen.queryByRole("alertdialog")).toBeNull();
  expect(screen.getByRole("dialog", { name: "Emitir compromiso de pago" })).toBe(formDialog);
  expect(concept.value).toBe("Pensión");
  expect(search.value).toBe("EST-003");
  expect(screen.getByRole("button", { name: "Anterior" }).disabled).toBe(false);
  expect(calls).toHaveLength(requestsBeforeClosing);
  await waitFor(() => expect(document.activeElement).toBe(cancel));
  expect(close).not.toHaveBeenCalled();

  await user.click(screen.getByRole("button", { name: "Elegir matrícula de Lucía Flores" }));
  expect(screen.getByText("Matrícula MAT-007")).toBeTruthy();
  await user.click(cancel);
  confirmation = screen.getByRole("alertdialog", { name: "¿Descartar los cambios?" });
  keepEditing = within(confirmation).getByRole("button", { name: "Seguir editando" });
  await user.click(keepEditing);
  expect(screen.queryByRole("button", { name: "Descartar cambios" })).toBeNull();
  expect(screen.getByText("Matrícula MAT-007")).toBeTruthy();
  expect(concept.value).toBe("Pensión");

  await user.click(cancel);
  confirmation = screen.getByRole("alertdialog", { name: "¿Descartar los cambios?" });
  await user.click(within(confirmation).getByRole("button", { name: "Cerrar ventana" }));
  expect(screen.queryByRole("alertdialog")).toBeNull();
  expect(screen.getByText("Matrícula MAT-007")).toBeTruthy();
  expect(close).not.toHaveBeenCalled();
  await waitFor(() => expect(document.activeElement).toBe(cancel));

  await user.click(cancel);
  confirmation = screen.getByRole("alertdialog", { name: "¿Descartar los cambios?" });
  await user.click(within(confirmation).getByRole("button", { name: "Descartar cambios" }));
  expect(close).toHaveBeenCalledOnce();
});

test("modifica solo monto y vencimiento con motivo y versión del detalle actual", async () => {
  const calls = fakeBackend({
    "GET /api/compromisos-pago": page([record]),
    "GET /api/compromisos-pago/12": record,
    "PUT /api/compromisos-pago/12": { ...record, montoTotal: 400 },
  });
  const user = userEvent.setup();
  render(<Finance canWrite />);
  await openDetail(user);
  expect(screen.getByText("Mensualidad escolar")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Modificar monto o vencimiento" }));
  await user.clear(screen.getByLabelText(/Monto en soles/));
  await user.type(screen.getByLabelText(/Monto en soles/), "400");
  await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
  expect(document.activeElement).toBe(screen.getByLabelText(/Motivo del cambio/));
  await user.type(screen.getByLabelText(/Motivo del cambio/), "Corrección del monto acordado");
  await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
  expect(await screen.findByText("Cambios guardados en el compromiso.")).toBeTruthy();
  expect(calls.find((call) => call.method === "PUT").body).toEqual({ montoTotal: 400, fechaVencimiento: "2026-03-10", motivo: "Corrección del monto acordado", version: 2 });
});

test("anula con motivo y conserva el registro consultable", async () => {
  let current = record;
  const calls = fakeBackend({
    "GET /api/compromisos-pago": () => page([current]),
    "GET /api/compromisos-pago/12": () => current,
    "POST /api/compromisos-pago/12/anulacion": ({ body }) => {
      current = { ...record, numeroCuota: null, estadoAdministrativo: "ANULADO", estadoFinanciero: "PENDIENTE", motivoAnulacion: body.motivo, fechaAnulacion: "2026-03-02T12:00:00Z" };
      return current;
    },
  });
  const user = userEvent.setup();
  render(<Finance canWrite />);
  await openDetail(user);
  await user.click(screen.getByRole("button", { name: "Anular compromiso" }));
  await user.click(screen.getByRole("button", { name: "Confirmar anulación" }));
  expect(screen.getByText("Explica el motivo para registrar este cambio.")).toBeTruthy();
  await user.type(screen.getByLabelText(/Motivo de anulación/), "Emisión equivocada");
  await user.click(screen.getByRole("button", { name: "Confirmar anulación" }));
  expect(await screen.findByText("Compromiso anulado. El registro se conserva para su consulta.")).toBeTruthy();
  expect(await screen.findByText("Anulado", { selector: ".status" })).toBeTruthy();
  expect(screen.getByText("Sin cuota registrada · CP-012")).toBeTruthy();
  expect(calls.find((call) => call.path.endsWith("/anulacion")).body).toEqual({ motivo: "Emisión equivocada", version: 2 });
  await user.click(await screen.findByRole("button", { name: "Ver compromiso CP-012" }));
  expect(await screen.findByText("Emisión equivocada")).toBeTruthy();
  expect(within(screen.getByRole("dialog")).getByText("Sin cuota registrada")).toBeTruthy();
  expect(within(screen.getByRole("dialog")).getByText("Anulado")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Anular compromiso" })).toBeNull();
  expect(calls.some((call) => call.method === "DELETE")).toBe(false);
});

test("bloquea acciones sobre compromisos con pagos y respeta consulta sin escritura", async () => {
  fakeBackend({ "GET /api/compromisos-pago": page([record]), "GET /api/compromisos-pago/12": { ...record, montoPagado: 50, saldo: 300, estadoFinanciero: "PARCIAL" } });
  const user = userEvent.setup();
  const view = render(<Finance canWrite />);
  await user.click(await screen.findByRole("button", { name: "Ver compromiso CP-012" }));
  expect(await screen.findByText("Este compromiso ya tiene pagos registrados. No se puede modificar ni anular.")).toBeTruthy();
  expect(within(screen.getByRole("dialog")).getByText("Importe abonado")).toBeTruthy();
  expect(within(screen.getByRole("dialog")).getByText(/^S\/\s50\.00$/)).toBeTruthy();
  expect(within(screen.getByRole("dialog")).getByText("Importe pendiente")).toBeTruthy();
  expect(within(screen.getByRole("dialog")).getByText(/^S\/\s300\.00$/)).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Anular compromiso" })).toBeNull();
  await user.click(screen.getByRole("button", { name: "Cerrar", exact: true }));
  view.rerender(<Finance canWrite={false} />);
  await user.click(screen.getByRole("button", { name: "Ver compromiso CP-012" }));
  await within(screen.getByRole("dialog")).findByText("Pago parcial");
  expect(screen.queryByRole("button", { name: "Modificar monto o vencimiento" })).toBeNull();
});

test("recupera un fallo al consultar el detalle y permite volver sin editar", async () => {
  let failing = true;
  fakeBackend({ "GET /api/compromisos-pago": page([record]), "GET /api/compromisos-pago/12": () => failing ? reply(503, { message: "No se pudo cargar el compromiso" }) : record });
  const user = userEvent.setup();
  render(<Finance canWrite />);
  await user.click(await screen.findByRole("button", { name: "Ver compromiso CP-012" }));
  await screen.findByText("No se pudo cargar el compromiso");
  failing = false;
  await user.click(screen.getByRole("button", { name: "Reintentar consulta" }));
  await user.click(await screen.findByRole("button", { name: "Modificar monto o vencimiento" }));
  await user.click(screen.getByRole("button", { name: "Cancelar" }));
  expect(await screen.findByRole("button", { name: "Anular compromiso" })).toBeTruthy();
});

test("conserva datos y muestra el conflicto del servidor sin duplicar emisión", async () => {
  const calls = fakeBackend({
    "GET /api/compromisos-pago/matriculas": page([enrollment]),
    "POST /api/compromisos-pago": reply(409, { message: "Ya existe un compromiso activo para este concepto y cuota." }),
  });
  const saved = vi.fn();
  const user = userEvent.setup();
  render(<CommitmentForm onClose={vi.fn()} onSaved={saved} />);
  await fillIssue(user);
  await user.click(screen.getByRole("button", { name: "Emitir compromiso" }));
  expect(await screen.findByText("Ya existe un compromiso activo para este concepto y cuota.")).toBeTruthy();
  expect(screen.getByLabelText(/Concepto/).value).toBe("Pensión de marzo");
  expect(saved).not.toHaveBeenCalled();
  expect(calls.filter((call) => call.method === "POST")).toHaveLength(1);
});

test("sitúa el foco en el campo rechazado por el servidor", async () => {
  fakeBackend({ "PUT /api/compromisos-pago/12": reply(400, { message: "Revisa el monto", fieldErrors: [{ field: "montoTotal", message: "Monto fuera de rango" }] }) });
  const user = userEvent.setup();
  render(<CommitmentForm commitment={record} action="edit" onClose={vi.fn()} onSaved={vi.fn()} />);
  await user.type(screen.getByLabelText(/Motivo del cambio/), "Corregir importe");
  await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
  expect(await screen.findByText("Monto fuera de rango")).toBeTruthy();
  await waitFor(() => expect(document.activeElement).toBe(screen.getByLabelText(/Monto en soles/)));
});

test("impide cerrar o volver a enviar mientras se guarda", async () => {
  let finish;
  fakeBackend({ "POST /api/compromisos-pago/12/anulacion": () => new Promise((resolve) => { finish = resolve; }) });
  const close = vi.fn(), saved = vi.fn();
  const user = userEvent.setup();
  render(<CommitmentForm commitment={record} action="annul" onClose={close} onSaved={saved} />);
  await user.type(screen.getByLabelText(/Motivo de anulación/), "Corrección administrativa");
  await user.click(screen.getByRole("button", { name: "Confirmar anulación" }));
  expect(screen.getByLabelText(/Motivo de anulación/).disabled).toBe(true);
  expect(screen.getByRole("button", { name: "Cancelar" }).disabled).toBe(true);
  await user.keyboard("{Escape}");
  expect(close).not.toHaveBeenCalled();
  await act(async () => finish({ ...record, estadoAdministrativo: "ANULADO" }));
  expect(saved).toHaveBeenCalledOnce();
});

test("cierra una emisión sin cambios y mantiene el día del vencimiento", async () => {
  fakeBackend({ "GET /api/compromisos-pago": page([]), "GET /api/compromisos-pago/matriculas": page([]) });
  const user = userEvent.setup();
  render(<Finance canWrite />);
  await user.click(screen.getByRole("button", { name: "Emitir compromiso" }));
  await user.click(screen.getByRole("button", { name: "Cancelar" }));
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(dueDateLabel("2026-03-10")).toContain("10");
});

test("conserva la moneda histórica sin permitir cambiar un importe en dólares", async () => {
  const historic = { ...record, moneda: "USD", numeroCuota: null };
  fakeBackend({
    "GET /api/compromisos-pago": page([historic]),
    "GET /api/compromisos-pago/12": historic,
  });
  const user = userEvent.setup();
  render(<Finance canWrite />);
  await user.click(await screen.findByRole("button", { name: "Ver compromiso CP-012" }));
  expect(await screen.findByText("Este compromiso se conserva en su moneda original.")).toBeTruthy();
  expect(within(screen.getByRole("dialog")).getByText("Sin cuota registrada")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Modificar monto o vencimiento" })).toBeNull();
  expect(screen.getByRole("button", { name: "Anular compromiso" })).toBeTruthy();
  expect(moneyLabel(350, "USD")).toMatch(/USD|US\$/);
  expect(moneyLabel(350, "moneda histórica")).toBe("350.00 moneda histórica");
});

test("mapea el rechazo de matrícula y enfoca el control para cambiarla", async () => {
  fakeBackend({
    "GET /api/compromisos-pago/matriculas": page([enrollment]),
    "POST /api/compromisos-pago": reply(422, {
      message: "La matrícula ya no está vigente",
      fieldErrors: [{ field: "idMatricula", message: "Selecciona otra matrícula vigente" }],
    }),
  });
  const user = userEvent.setup();
  render(<CommitmentForm onClose={vi.fn()} onSaved={vi.fn()} />);
  await fillIssue(user);
  await user.click(screen.getByRole("button", { name: "Emitir compromiso" }));
  expect(await screen.findByText("Selecciona otra matrícula vigente")).toBeTruthy();
  await waitFor(() => expect(document.activeElement).toBe(screen.getByRole("button", { name: "Cambiar matrícula" })));
});

test("un error de un campo desconocido lleva el foco al mensaje general", async () => {
  fakeBackend({ "PUT /api/compromisos-pago/12": reply(409, {
    message: "El compromiso fue actualizado por otra persona. Vuelve a consultarlo.",
    fieldErrors: [{ field: "version", message: "Versión desactualizada" }],
  }) });
  const user = userEvent.setup();
  render(<CommitmentForm commitment={record} action="edit" onClose={vi.fn()} onSaved={vi.fn()} />);
  await user.type(screen.getByLabelText(/Motivo del cambio/), "Ajuste del vencimiento");
  await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
  const message = await screen.findByText("El compromiso fue actualizado por otra persona. Vuelve a consultarlo.");
  await waitFor(() => expect(document.activeElement).toBe(message.parentElement));
});

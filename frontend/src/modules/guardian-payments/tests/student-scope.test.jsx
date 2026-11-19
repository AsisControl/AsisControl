import { beforeEach, expect, test } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, signIn } from "../../../../tests/support/backend.js";
import { GuardianPayments } from "../guardian-payments";
import { complete, obligation, sibling, student } from "./fixtures";

const OBLIGATIONS = "/api/apoderado/obligaciones";
const RECEIPTS = "/api/apoderado/comprobantes";
const ORDERS = "/api/pagos/ordenes";

beforeEach(() => signIn({ role: "APODERADO", permissions: ["PAGOS_LEER"] }));

const backend = () => fakeBackend({
  [`GET ${OBLIGATIONS}/estudiantes`]: [student, sibling],
  [`GET ${OBLIGATIONS}`]: page([obligation()]),
  [`GET ${RECEIPTS}`]: page([]),
  [`GET ${ORDERS}`]: page([complete({ items: [
    { idCompromiso: 1, codigoCompromiso: "CP-000001", alumno: "Lucía Flores", concepto: "Pensión", numeroCuota: 1, monto: 350 },
    { idCompromiso: 2, codigoCompromiso: "CP-000002", alumno: "Luis Flores", concepto: "Pensión", numeroCuota: 1, monto: 350 },
  ] })]),
});
const lastQuery = (calls, path) => calls.filter((call) => call.path === path).at(-1)?.query;

test("el selector muestra cada hijo y «Todos los estudiantes», que es el estado de cuenta consolidado", async () => {
  backend();
  render(<GuardianPayments />);
  const select = await screen.findByRole("combobox", { name: "Estudiante" });
  await waitFor(() => expect(select.disabled).toBe(false));
  expect([...select.options].map((option) => option.textContent)).toEqual(["Todos los estudiantes", "Lucía Flores", "Luis Flores"]);
  const scope = screen.getByRole("region", { name: "Estudiante seleccionado" });
  expect(within(scope).getByText("Estado de cuenta consolidado")).toBeTruthy();
  expect(within(scope).getByText("2 estudiantes asociados")).toBeTruthy();
  expect(screen.getByRole("heading", { name: "Obligaciones de mis estudiantes" })).toBeTruthy();
});

test("el estudiante elegido filtra Obligaciones, Historial y Seguimiento y siempre se ve quién es", async () => {
  const calls = backend();
  const user = userEvent.setup();
  render(<GuardianPayments />);
  const select = await screen.findByRole("combobox", { name: "Estudiante" });
  await waitFor(() => expect(select.disabled).toBe(false));
  await user.selectOptions(select, "4");

  const scope = screen.getByRole("region", { name: "Estudiante seleccionado" });
  expect(within(scope).getByText("Luis Flores", { selector: "strong" })).toBeTruthy();
  expect(within(scope).getByText("Código EST-004")).toBeTruthy();
  await waitFor(() => expect(lastQuery(calls, OBLIGATIONS)).toMatchObject({ idAlumno: "4", page: "0" }));
  expect(screen.getByRole("heading", { name: "Obligaciones de Luis Flores" })).toBeTruthy();

  await user.click(screen.getByRole("tab", { name: "Historial de pagos" }));
  await waitFor(() => expect(lastQuery(calls, RECEIPTS)).toMatchObject({ idAlumno: "4" }));
  expect(await screen.findByRole("heading", { name: "Historial de pagos de Luis Flores" })).toBeTruthy();
  expect(screen.getByText("Todavía no hay pagos registrados de Luis Flores.")).toBeTruthy();
  expect(screen.getByRole("combobox", { name: "Estudiante" }).value).toBe("4");

  await user.click(screen.getByRole("tab", { name: "Seguimiento de pagos" }));
  await waitFor(() => expect(lastQuery(calls, ORDERS)).toMatchObject({ idAlumno: "4" }));
  expect(await screen.findByRole("heading", { name: "Seguimiento de pagos de Luis Flores" })).toBeTruthy();
  expect(await screen.findByText("Lucía Flores, Luis Flores")).toBeTruthy();

  await user.selectOptions(screen.getByRole("combobox", { name: "Estudiante" }), "");
  await waitFor(() => expect(lastQuery(calls, ORDERS).idAlumno).toBeUndefined());
  expect(screen.getByRole("heading", { name: "Seguimiento de pagos de mis estudiantes" })).toBeTruthy();
});

test("cambiar de estudiante limpia la selección a pagar y conserva el tipo de cobro y la situación elegidos", async () => {
  const calls = backend();
  const user = userEvent.setup();
  render(<GuardianPayments />);
  await user.click(await screen.findByRole("checkbox", { name: "Seleccionar CP-000001 de Lucía Flores" }));
  expect(screen.getByText("1 obligación seleccionada")).toBeTruthy();
  await user.selectOptions(screen.getByLabelText("Tipo de cobro"), "PENSION");
  await user.selectOptions(screen.getByLabelText("Situación de pago"), "VENCIDO");
  await user.click(screen.getByRole("button", { name: "Buscar", exact: true }));
  await user.selectOptions(screen.getByRole("combobox", { name: "Estudiante" }), "3");
  await waitFor(() => expect(lastQuery(calls, OBLIGATIONS)).toMatchObject({ idAlumno: "3", tipo: "PENSION", estado: "VENCIDO" }));
  expect(screen.getByText("0 obligaciones seleccionadas")).toBeTruthy();
});

test("en Actividades y servicios no se muestra el selector porque no filtra esa sección", async () => {
  fakeBackend({
    [`GET ${OBLIGATIONS}/estudiantes`]: [student, sibling],
    [`GET ${OBLIGATIONS}`]: page([]),
    "GET /api/apoderado/ofertas": page([]),
  });
  const user = userEvent.setup();
  render(<GuardianPayments />);
  await screen.findByRole("region", { name: "Estudiante seleccionado" });
  await user.click(screen.getByRole("tab", { name: "Actividades y servicios" }));
  expect(screen.queryByRole("region", { name: "Estudiante seleccionado" })).toBeNull();
});

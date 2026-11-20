import { beforeEach, expect, test, vi } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { currentUrl, setUrl } from "../../../../tests/support/navigation.js";
import { Administration } from "@/modules/administration";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const person = {
  id: 70, nombreCompleto: "Patricia Morales", numeroDocumento: "40404040",
  perfiles: ["APODERADO"], rolesConAcceso: ["APODERADO"],
};
const created = {
  id: 90, nombreCompleto: "Patricia Morales",
  username: "20260003", correo: "patricia@colegio.edu.pe",
  contrasenaTemporal: "Temporal2026",
  contrasenaTemporalExpira: "2027-01-15T12:00:00Z",
};
const path = "/api/usuarios/administradores";
const setup = (response = created) => fakeBackend({
  "GET /api/usuarios/personas": page([person]),
  [`POST ${path}`]: response,
});
const renderForm = () =>
  render(<Administration currentUserId="1" tab="usuarios" view="nuevo" />);

beforeEach(() => {
  signIn();
  setUrl("/administracion/usuarios/nuevo");
});

test("Registra el perfil a una persona existente y conserva su cuenta", async () => {
  const { promise: response, resolve } = Promise.withResolvers();
  const calls = setup(() => response);
  const user = userEvent.setup();
  renderForm();
  expect(screen.getByRole("button", { name: "Registrar perfil" }).disabled).toBe(true);
  await user.click(await screen.findByRole("button", { name: /Patricia Morales/ }));
  await user.click(screen.getByRole("button", { name: "Registrar perfil" }));
  const pending = screen.getByRole("button", { name: "Registrando…" });
  expect(pending.matches(":disabled")).toBe(true);
  fireEvent.submit(pending.closest("form"));
  expect(calls.filter((call) => call.path === path)).toHaveLength(1);
  resolve({ ...created, contrasenaTemporal: null });
  expect(await screen.findByText("Perfil de administrador registrado para Patricia Morales.")).toBeTruthy();
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(calls.find((call) => call.path === path).body).toEqual({ personaId: 70 });
  await user.click(screen.getByRole("button", { name: "Volver a usuarios" }));
  expect(currentUrl()).toBe("/administracion/usuarios");
});

test("Busca personas sin enviar el alta al presionar Enter", async () => {
  const calls = fakeBackend({
    "GET /api/usuarios/personas": ({ query }) => query.search
      ? page([])
      : page([person, { id: 82, nombreCompleto: "Julia Soto", numeroDocumento: "44220101", perfiles: [] }]),
    [`POST ${path}`]: created,
  });
  const user = userEvent.setup();
  renderForm();
  const patricia = await screen.findByRole("button", { name: /Patricia Morales/ });
  expect(within(patricia).getByText("Apoderado")).toBeTruthy();
  const julia = screen.getByRole("button", { name: /Julia Soto/ });
  expect(within(julia).getByText("Sin perfil institucional")).toBeTruthy();
  await user.click(julia);
  expect(julia.getAttribute("aria-pressed")).toBe("true");
  expect(screen.getByRole("button", { name: "Registrar perfil" }).disabled).toBe(false);

  await user.type(screen.getByRole("searchbox", { name: "Buscar persona" }), " Nadie {Enter}");
  expect(await screen.findByText("No hay personas para esta búsqueda.")).toBeTruthy();
  expect(calls.at(-1).query).toMatchObject({ search: "Nadie", page: "0" });
  expect(calls.some((call) => call.path === path)).toBe(false);
});

test("Registra una persona nueva y entrega la credencial temporal una sola vez", async () => {
  const calls = setup();
  const user = userEvent.setup();
  renderForm();
  await user.click(screen.getByRole("button", { name: "Persona nueva" }));
  await user.type(screen.getByLabelText(/^Nombres/), "Patricia");
  await user.type(screen.getByLabelText(/^Apellido paterno/), "Morales");
  await user.type(screen.getByLabelText(/^Apellido materno/), "Vega");
  await user.selectOptions(screen.getByLabelText(/Tipo de documento/), "PASAPORTE");
  await user.type(screen.getByLabelText(/Número de documento/), "AB123456");
  await user.type(screen.getByLabelText(/Fecha de nacimiento/), "1985-05-15");
  await user.click(screen.getByRole("button", { name: "Registrar perfil" }));
  const dialog = await screen.findByRole("dialog", { name: "Credenciales iniciales" });
  expect(within(dialog).getByText("20260003")).toBeTruthy();
  expect(within(dialog).getByText("Temporal2026")).toBeTruthy();
  expect(calls.find((call) => call.path === path).body).toMatchObject({
    persona: { tipoDocumento: "PASAPORTE", numeroDocumento: "AB123456" },
  });
  expect(calls.find((call) => call.path === path).body.personaId).toBeUndefined();
  const sheet = { document: { write: vi.fn(), close: vi.fn() }, focus: vi.fn(), print: vi.fn() };
  const open = vi.spyOn(window, "open").mockReturnValueOnce(null).mockReturnValueOnce(sheet);
  const print = within(dialog).getByRole("button", { name: "Imprimir o guardar PDF" });
  await user.click(print);
  expect(sheet.print).not.toHaveBeenCalled();
  expect(within(dialog).getByText("Temporal2026")).toBeTruthy();
  await user.click(print);
  expect(open).toHaveBeenCalledWith("", "_blank");
  expect(sheet.document.write.mock.calls[0][0]).toContain("<code>20260003</code>");
  expect(sheet.document.write.mock.calls[0][0]).toContain("<code>Temporal2026</code>");
  expect(sheet.print).toHaveBeenCalledOnce();
  await user.click(within(dialog).getByRole("button", { name: "Entendido" }));
  expect(screen.queryByText("Temporal2026")).toBeNull();
  expect(screen.queryByRole("button", { name: "Imprimir o guardar PDF" })).toBeNull();
});

test("Informa el rechazo del servidor y permite volver a usuarios", async () => {
  const calls = setup(reply(409, { message: "La persona ya tiene ese perfil." }));
  const user = userEvent.setup();
  renderForm();
  await user.click(await screen.findByRole("button", { name: /Patricia Morales/ }));
  await user.click(screen.getByRole("button", { name: "Registrar perfil" }));
  expect((await screen.findByRole("alert")).textContent).toBe("La persona ya tiene ese perfil.");
  await user.click(screen.getByRole("button", { name: "Cancelar" }));
  expect(currentUrl()).toBe("/administracion/usuarios");
  expect(calls.filter((call) => call.path === path)).toHaveLength(1);
});

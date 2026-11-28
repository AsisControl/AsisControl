import { beforeEach, expect, test, vi } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { usePathname } from "next/navigation";
import { setUrl } from "../../../../tests/support/navigation.js";
import { AcademicRecords } from "@/modules/academic-records";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const BASE = "/matricula/fichas";
function RecordsScreen() {
  return <AcademicRecords basePath={BASE} view={usePathname().split("/")[3]} />;
}
const carmen = { id: 1, apoderadoId: 7, nombreCompleto: "Carmen Flores", parentesco: "MADRE", principal: true };
const pedro = { id: 2, apoderadoId: 9, nombreCompleto: "Pedro Flores", parentesco: "TUTOR_LEGAL", principal: false };

/** Backend con estado: retirar quita el vínculo del estudiante y de la ficha del apoderado. */
function backend(routes = {}) {
  let links = [carmen, pedro];
  const lucia = () => ({
    id: 3,
    codigoAlumno: "EST-003",
    persona: { id: 3, nombreCompleto: "Lucía Flores", numeroDocumento: "71234567" },
    activo: true,
    apoderados: links,
  });
  const guardian = () => ({
    id: 9,
    codigoApoderado: "APO-009",
    persona: { id: 90, nombreCompleto: "Pedro Flores", tipoDocumento: "DNI", numeroDocumento: "40998877" },
    activo: true,
    alumnos: links
      .filter((link) => link.apoderadoId === 9)
      .map((link) => ({ ...link, alumnoId: 3, codigoAlumno: "EST-003", nombreCompleto: "Lucía Flores" })),
  });
  return fakeBackend({
    "GET /api/alumnos": () => page([lucia()]),
    "GET /api/apoderados": () => page([guardian()]),
    "GET /api/apoderados/:id": () => guardian(),
    "DELETE /api/alumnos/:id/apoderados/:apoderadoId": ({ path }) => {
      links = links.filter((link) => link.apoderadoId !== Number(path.split("/")[5]));
      return reply(204);
    },
    ...routes,
  });
}

beforeEach(() => {
  signIn();
  setUrl(`${BASE}/parentescos`);
});

async function openDialog(user, guardian) {
  await user.click(await screen.findByRole("button", { name: `Retirar a ${guardian} de Lucía Flores` }));
  return screen.getByRole("dialog", { name: "Retirar vínculo" });
}
const reason = "Solicitud del apoderado";
async function confirm(user, dialog) {
  const field = within(dialog).getByLabelText("Motivo del cambio");
  if (!field.value) await user.type(field, reason);
  await user.click(within(dialog).getByRole("button", { name: "Retirar vínculo" }));
}
const deletes = (calls) => calls.filter((call) => call.method === "DELETE");

test("Retira con confirmación y el vínculo desaparece del listado y de la ficha del apoderado", async () => {
  const calls = backend();
  const user = userEvent.setup();
  const view = render(<RecordsScreen />);
  const dialog = await openDialog(user, "Pedro Flores");
  expect(dialog.textContent).toContain("Pedro Flores · Tutor legal de Lucía Flores");
  const motivo = within(dialog).getByLabelText("Motivo del cambio");
  expect(motivo.required).toBe(true);
  await user.click(within(dialog).getByRole("button", { name: "Retirar vínculo" }));
  expect(deletes(calls)).toHaveLength(0);
  await confirm(user, dialog);
  expect((await screen.findByRole("status")).textContent).toBe(
    "Se retiró el vínculo de Pedro Flores con Lucía Flores.",
  );
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(await screen.findByText("Carmen Flores")).toBeTruthy();
  expect(screen.queryByText("Pedro Flores")).toBeNull();
  expect(deletes(calls).map((call) => call.path)).toEqual(["/api/alumnos/3/apoderados/9"]);
  expect(deletes(calls)[0].query.motivo).toBe(reason);

  setUrl(`${BASE}/apoderados`);
  view.rerender(<RecordsScreen />);
  await user.click(await screen.findByRole("button", { name: "Ver ficha de Pedro Flores" }));
  const record = screen.getByRole("dialog", { name: "Ficha del apoderado" });
  expect(await within(record).findByText("No tiene estudiantes vinculados.")).toBeTruthy();
});

test("Cancelar no retira el vínculo", async () => {
  const calls = backend();
  const user = userEvent.setup();
  render(<RecordsScreen />);
  const dialog = await openDialog(user, "Pedro Flores");
  await user.click(within(dialog).getByRole("button", { name: "Cancelar" }));
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(screen.getByText("Pedro Flores")).toBeTruthy();
  expect(deletes(calls)).toHaveLength(0);
});

test("Retirar al principal con otros vínculos muestra el motivo de la API y cómo reemplazarlo", async () => {
  backend({
    "DELETE /api/alumnos/:id/apoderados/:apoderadoId": reply(422, {
      code: "APODERADO_PRINCIPAL_REQUERIDO",
      message: "Designe otro apoderado principal antes de retirar el actual",
    }),
  });
  const user = userEvent.setup();
  render(<RecordsScreen />);
  const dialog = await openDialog(user, "Carmen Flores");
  await confirm(user, dialog);
  expect((await within(dialog).findByRole("alert")).textContent).toBe(
    "Designe otro apoderado principal antes de retirar el actual",
  );
  expect(within(dialog).getByText(/Para reemplazar al principal/)).toBeTruthy();
  expect(screen.getByText("Carmen Flores")).toBeTruthy();
});

test("Un error del servidor mantiene el vínculo, sin la ayuda del principal, y permite reintentar", async () => {
  let attempts = 0;
  backend({
    "DELETE /api/alumnos/:id/apoderados/:apoderadoId": () =>
      ++attempts === 1 ? reply(500, { message: "No se pudo retirar" }) : reply(204),
  });
  const user = userEvent.setup();
  render(<RecordsScreen />);
  const dialog = await openDialog(user, "Pedro Flores");
  await confirm(user, dialog);
  expect((await within(dialog).findByRole("alert")).textContent).toBe("No se pudo retirar");
  expect(within(dialog).queryByText(/Para reemplazar al principal/)).toBeNull();
  await confirm(user, dialog);
  expect(await screen.findByRole("status")).toBeTruthy();
  expect(attempts).toBe(2);
});

test("Si el vínculo ya no existe, avisa y actualiza el listado", async () => {
  const calls = backend({
    "DELETE /api/alumnos/:id/apoderados/:apoderadoId": reply(404, {
      code: "VINCULO_NO_ENCONTRADO",
      message: "No se encontro el vinculo entre alumno y apoderado",
    }),
  });
  const user = userEvent.setup();
  render(<RecordsScreen />);
  const dialog = await openDialog(user, "Pedro Flores");
  const reads = () => calls.filter((call) => call.path === "/api/alumnos").length;
  const before = reads();
  await confirm(user, dialog);
  expect(await within(dialog).findByRole("alert")).toBeTruthy();
  await vi.waitFor(() => expect(reads()).toBe(before + 1));
});

test("Un doble envío retira una sola vez y bloquea los botones", async () => {
  let respond;
  const calls = backend({
    "DELETE /api/alumnos/:id/apoderados/:apoderadoId": () => new Promise((resolve) => (respond = resolve)),
  });
  const user = userEvent.setup();
  render(<RecordsScreen />);
  const dialog = await openDialog(user, "Pedro Flores");
  const form = within(dialog).getByRole("button", { name: "Retirar vínculo" }).closest("form");
  await user.type(within(dialog).getByLabelText("Motivo del cambio"), reason);
  fireEvent.submit(form);
  fireEvent.submit(form);
  expect(within(dialog).getByRole("button", { name: "Cancelar" }).disabled).toBe(true);
  expect(deletes(calls)).toHaveLength(1);
  respond(reply(204));
  expect(await screen.findByRole("status")).toBeTruthy();
});

test("Sin conexión avisa y conserva el vínculo", async () => {
  backend({
    "DELETE /api/alumnos/:id/apoderados/:apoderadoId": () => {
      throw new TypeError("Failed to fetch");
    },
  });
  const user = userEvent.setup();
  render(<RecordsScreen />);
  const dialog = await openDialog(user, "Pedro Flores");
  await confirm(user, dialog);
  expect((await within(dialog).findByRole("alert")).textContent).toMatch(/No se pudo conectar con el servidor/);
  expect(screen.getByText("Pedro Flores")).toBeTruthy();
});

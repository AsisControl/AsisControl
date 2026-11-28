import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend";
import { setUrl } from "../../../../tests/support/navigation";
import { AcademicRecords } from "@/modules/academic-records";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const base = "/matricula/fichas";
const api = "/api/alumnos/3/apoderados";
const student = {
  id: 3, codigoAlumno: "EST-003", activo: true,
  persona: { nombreCompleto: "Lucía Flores", activo: true },
  apoderados: [],
};
const guardianLink = {
  id: 11, apoderadoId: 7, nombreCompleto: "Carmen Flores", parentesco: "MADRE",
  principal: false, activo: true, apoderadoActivo: true,
};

beforeEach(() => {
  signIn();
  setUrl(`${base}/parentescos`);
});

const manager = () => within(screen.getByRole("dialog", { name: "Gestionar vínculos" }));
const actionDialog = (name) => within(screen.getByRole("dialog", { name }));
const reasonField = (dialog) => dialog.getByLabelText("Motivo del cambio");

async function openManager(user) {
  render(<AcademicRecords basePath={base} view="parentescos" />);
  await user.click(await screen.findByRole("button", { name: "Gestionar vínculos de Lucía Flores" }));
}

test("edita, finaliza y reactiva el vínculo existente con su motivo y actualiza el listado", async () => {
  let link = guardianLink;
  const calls = fakeBackend({
    "GET /api/alumnos": page([student]),
    [`GET ${api}`]: () => [link],
    [`PUT ${api}/7`]: ({ body }) => {
      link = { ...link, parentesco: body.parentesco, principal: body.principal };
      return link;
    },
    [`DELETE ${api}/7`]: () => {
      link = { ...link, activo: false };
      return reply(204);
    },
    [`PUT ${api}/7/estado`]: ({ body }) => {
      link = { ...link, activo: body.activo };
      return link;
    },
  });
  const user = userEvent.setup();
  await openManager(user);
  await user.click(await manager().findByRole("button", { name: "Editar" }));
  const edit = actionDialog("Editar vínculo");
  await user.selectOptions(edit.getByLabelText("Parentesco"), "TUTOR_LEGAL");
  await user.click(edit.getByLabelText("Apoderado principal"));
  await user.type(reasonField(edit), "Actualización de ficha");
  await user.click(edit.getByRole("button", { name: "Guardar cambios" }));
  expect(await manager().findByText("Tutor legal · Apoderado principal")).toBeTruthy();

  await user.click(manager().getByRole("button", { name: "Finalizar" }));
  const finish = actionDialog("Finalizar vínculo");
  await user.type(reasonField(finish), "Solicitud familiar");
  await user.click(finish.getByRole("button", { name: "Finalizar vínculo" }));
  expect(await manager().findByText("Finalizado")).toBeTruthy();
  expect(manager().queryByRole("button", { name: "Editar" })).toBeNull();

  await user.click(manager().getByRole("button", { name: "Reactivar" }));
  const reopen = actionDialog("Reactivar vínculo");
  await user.type(reasonField(reopen), "Confirmación familiar");
  await user.click(reopen.getByRole("button", { name: "Reactivar vínculo" }));
  expect(await manager().findByText("Activo")).toBeTruthy();
  expect(calls.filter(({ method }) => method !== "GET")).toEqual([
    { method: "PUT", path: `${api}/7`, query: {}, body: {
      parentesco: "TUTOR_LEGAL", principal: true, motivo: "Actualización de ficha",
    } },
    { method: "DELETE", path: `${api}/7`, query: { motivo: "Solicitud familiar" }, body: undefined },
    { method: "PUT", path: `${api}/7/estado`, query: {}, body: {
      activo: true, motivo: "Confirmación familiar",
    } },
  ]);
  await waitFor(() => expect(calls.filter(({ path }) => path === "/api/alumnos")).toHaveLength(4));
});

test("reintenta una consulta fallida y distingue el resultado vacío", async () => {
  let attempts = 0;
  fakeBackend({
    "GET /api/alumnos": page([student]),
    [`GET ${api}`]: () => ++attempts === 1 ? reply(503, { message: "Servicio temporalmente no disponible" }) : [],
  });
  const user = userEvent.setup();
  await openManager(user);
  expect((await manager().findByRole("alert")).textContent).toContain("Servicio temporalmente no disponible");
  await user.click(manager().getByRole("button", { name: "Reintentar carga" }));
  expect(await manager().findByText("El estudiante todavía no tiene vínculos registrados.")).toBeTruthy();
  expect(attempts).toBe(2);
  await user.click(manager().getByRole("button", { name: "Cerrar ventana" }));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("consulta el historial de una ficha inactiva sin ofrecer cambios y distingue cuando aún está vacío", async () => {
  const when = "2026-11-27T18:00:00Z";
  let history = [
    { id: 1, accion: "ASOCIAR_APODERADO", actor: "SISTEMA", fecha: when },
    { id: 2, accion: "REACTIVAR_VINCULO_APODERADO", actor: "admin", actorNombre: "Ana Ruiz",
      fecha: when, motivo: "Solicitud autorizada" },
  ];
  fakeBackend({
    "GET /api/alumnos": page([{ ...student, activo: false }]),
    [`GET ${api}`]: [{ ...guardianLink, apoderadoActivo: false, activo: false }],
    [`GET ${api}/7/historial`]: () => history,
  });
  const user = userEvent.setup();
  await openManager(user);
  expect(await manager().findByText("Ficha del apoderado inactiva")).toBeTruthy();
  for (const action of ["Editar", "Finalizar", "Reactivar"]) {
    expect(manager().queryByRole("button", { name: action })).toBeNull();
  }
  await user.click(manager().getByRole("button", { name: "Ver historial" }));
  const audit = actionDialog("Historial del vínculo");
  const entries = await audit.findAllByRole("listitem");
  expect(within(entries[0]).getByText("Vínculo reactivado")).toBeTruthy();
  expect(within(entries[0]).getByText(/Ana Ruiz/)).toBeTruthy();
  expect(within(entries[0]).getByText("Solicitud autorizada")).toBeTruthy();
  expect(within(entries[1]).getByText(/Sistema/)).toBeTruthy();
  history = [];
  await user.click(audit.getByRole("button", { name: "Cerrar ventana" }));
  await user.click(await manager().findByRole("button", { name: "Ver historial" }));
  expect(await actionDialog("Historial del vínculo").findByText("No hay movimientos registrados para este vínculo.")).toBeTruthy();
});

test("rechaza un motivo vacío y conserva el formulario si falla el guardado", async () => {
  const calls = fakeBackend({
    "GET /api/alumnos": page([student]),
    [`GET ${api}`]: [guardianLink],
    [`PUT ${api}/7`]: reply(409, { message: "El vínculo cambió. Vuelve a consultar sus datos." }),
  });
  const user = userEvent.setup();
  await openManager(user);
  await user.click(await manager().findByRole("button", { name: "Editar" }));
  const edit = actionDialog("Editar vínculo");
  await user.type(reasonField(edit), "   ");
  await user.click(edit.getByRole("button", { name: "Guardar cambios" }));
  expect((await edit.findByRole("alert")).textContent).toContain("Ingresa el motivo del cambio.");
  expect(calls.some(({ method }) => method === "PUT")).toBe(false);

  await user.clear(reasonField(edit));
  await user.type(reasonField(edit), "Revisión de la ficha");
  await user.click(edit.getByRole("button", { name: "Guardar cambios" }));
  expect((await edit.findByRole("alert")).textContent).toContain("El vínculo cambió.");
  expect(reasonField(edit).value).toBe("Revisión de la ficha");
  await user.click(edit.getByRole("button", { name: "Cancelar" }));
  expect(await manager().findByText("Carmen Flores")).toBeTruthy();
});

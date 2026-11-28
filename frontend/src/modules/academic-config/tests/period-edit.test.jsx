import { beforeEach, expect, test } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { reply, signIn } from "../../../../tests/support/backend.js";
import { AcademicConfiguration } from "@/modules/academic-config";
import { academicBackend, period, year } from "./fixtures.js";

const BASE = "/api/configuracion-academica";
const first = period({ estado: "PLANIFICADO", version: 3 });
const second = period({
  id: 12,
  nombre: "Período II",
  orden: 2,
  fechaInicio: "2026-06-01",
  fechaFin: "2026-08-31",
  estado: "PLANIFICADO",
});
const draft = () => year({ estado: "BORRADOR", periodos: [first, second], secciones: [] });

beforeEach(() => {
  signIn();
});

async function openEdit(routes) {
  const backend = academicBackend([draft()], routes);
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await user.click(await screen.findByRole("button", { name: "Editar Período I" }));
  const dialog = screen.getByRole("dialog", { name: "Editar período" });
  const field = (label) => within(dialog).getByLabelText(label);
  const replace = async (label, value) => {
    await user.clear(field(label));
    if (value) await user.type(field(label), value);
  };
  const save = () => user.click(within(dialog).getByText("Guardar cambios"));
  const puts = () => backend.calls.filter((call) => call.method === "PUT");
  return { ...backend, user, dialog, field, replace, save, puts };
}

test("Editar período: parte de los datos actuales y envía nombre, fechas, versión y motivo", async () => {
  const context = await openEdit({
    [`PUT ${BASE}/periodos/:id`]: ({ body }) => {
      context.store[2026] = {
        ...draft(),
        periodos: [{ ...first, nombre: body.nombre, fechaFin: body.fechaFin }, second],
      };
      return context.store[2026];
    },
  });
  const { dialog, field, replace, save, puts } = context;
  expect(field(/Nombre del período/).value).toBe("Período I");
  expect(field(/Fecha de inicio/).value).toBe("2026-03-01");
  expect(field(/Fecha de fin/).value).toBe("2026-05-31");
  expect(within(dialog).queryByLabelText(/Estado inicial/)).toBeNull();
  expect(within(dialog).queryByLabelText(/Orden/)).toBeNull();
  await replace(/Nombre del período/, " Primer período ");
  await replace(/Fecha de fin/, "2026-05-15");
  await replace(/Motivo/, " Ajuste del calendario ");
  await save();
  expect(await screen.findByText("Período actualizado.")).toBeTruthy();
  expect(screen.getByText("1. Primer período")).toBeTruthy();
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(puts()).toEqual([
    expect.objectContaining({
      path: `${BASE}/periodos/11`,
      body: {
        nombre: "Primer período",
        fechaInicio: "2026-03-01",
        fechaFin: "2026-05-15",
        version: 3,
        motivo: "Ajuste del calendario",
      },
    }),
  ]);
});

test("Editar período: revisa campos y rango de fechas sin llamar al servidor", async () => {
  const { dialog, replace, save, puts } = await openEdit();
  await save();
  expect(within(dialog).getByText("Indique el motivo del cambio.")).toBeTruthy();
  await replace(/Motivo/, "Ajuste");
  await replace(/Nombre del período/, "");
  await replace(/Fecha de fin/, "2026-02-01");
  await save();
  expect(within(dialog).getByText("El nombre del período es obligatorio.")).toBeTruthy();
  expect(
    within(dialog).getByText("La fecha de fin no puede ser anterior al inicio."),
  ).toBeTruthy();
  await replace(/Fecha de fin/, "2027-02-01");
  await save();
  expect(within(dialog).getByText("La fecha de fin debe estar dentro del año.")).toBeTruthy();
  expect(puts()).toHaveLength(0);
});

test("Editar período: solapes y demás reglas los responde el servidor", async () => {
  const reason = "Las fechas del período se superponen con Período II";
  const { user, dialog, replace, save, puts } = await openEdit({
    [`PUT ${BASE}/periodos/:id`]: reply(422, {
      code: "PERIODOS_SUPERPUESTOS",
      message: reason,
    }),
  });
  await replace(/Fecha de fin/, "2026-06-15");
  await replace(/Motivo/, "Ajuste");
  await save();
  expect((await within(dialog).findByRole("alert")).textContent).toBe(reason);
  expect(puts()).toHaveLength(1);
  expect(within(dialog).getByText("Guardar cambios").disabled).toBe(false);
  await user.click(within(dialog).getByText("Cancelar"));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Editar período: mientras guarda no admite un segundo envío", async () => {
  let release;
  const context = await openEdit({
    [`PUT ${BASE}/periodos/:id`]: () =>
      new Promise((resolve) => {
        release = () => resolve(draft());
      }),
  });
  const { user, dialog, replace, save, puts } = context;
  await replace(/Motivo/, "Ajuste");
  await save();
  const busy = within(dialog).getByText("Guardando…");
  expect(busy.disabled).toBe(true);
  await user.click(busy);
  expect(puts()).toHaveLength(1);
  release();
  expect(await screen.findByText("Período actualizado.")).toBeTruthy();
});

test("Editar período: con una versión vieja cierra, recarga y avisa", async () => {
  const context = await openEdit({
    [`PUT ${BASE}/periodos/:id`]: () => {
      context.store[2026] = {
        ...draft(),
        periodos: [{ ...first, nombre: "Cambiado por otra persona", version: 4 }],
      };
      return reply(409, {
        code: "VERSION_DESACTUALIZADA",
        message: "La configuración cambió; vuelva a cargarla",
      });
    },
  });
  const { user, replace, save } = context;
  await replace(/Motivo/, "Ajuste");
  await save();
  expect(
    await screen.findByText(
      "La configuración cambió; vuelva a cargarla Se recargaron los períodos.",
    ),
  ).toBeTruthy();
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(screen.getByText("1. Cambiado por otra persona")).toBeTruthy();
  // El aviso se limpia al volver a editar.
  await user.click(screen.getByRole("button", { name: "Editar Cambiado por otra persona" }));
  await waitFor(() => expect(screen.queryByText(/Se recargaron los períodos/)).toBeNull());
});

test("Editar período: si la recarga falla, muestra ambos errores", async () => {
  let fail = false;
  const { replace, save } = await openEdit({
    [`GET ${BASE}/anios/:anio`]: () =>
      fail ? reply(500, { message: "Servidor caído" }) : draft(),
    [`PUT ${BASE}/periodos/:id`]: () => {
      fail = true;
      return reply(409, { code: "VERSION_DESACTUALIZADA", message: "Cambió." });
    },
  });
  await replace(/Motivo/, "Ajuste");
  await save();
  expect(await screen.findByText("Cambió. Servidor caído")).toBeTruthy();
});

test.each(["ACTIVO", "CERRADO"])("Editar período: no se ofrece con el año %s", async (estado) => {
  academicBackend([year({ estado })]);
  render(<AcademicConfiguration />);
  await screen.findByText("1. Período I");
  expect(screen.queryByRole("button", { name: /^Editar Período/ })).toBeNull();
});

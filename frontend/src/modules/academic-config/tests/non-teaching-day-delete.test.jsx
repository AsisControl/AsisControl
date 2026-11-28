import { beforeEach, expect, test } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { reply, signIn } from "../../../../tests/support/backend.js";
import { AcademicConfiguration } from "@/modules/academic-config";
import { academicBackend, year } from "./fixtures.js";

const BASE = "/api/configuracion-academica";
const holiday = { id: 7, fecha: "2026-07-28", descripcion: "Fiestas Patrias", version: 2 };

beforeEach(() => {
  signIn();
});

async function openDelete(remove, days = [holiday], estado = "ACTIVO") {
  const list = [...days];
  const backend = academicBackend([year({ estado })], {
    [`GET ${BASE}/anios/:anio/dias-no-lectivos`]: () => [...list],
    [`DELETE ${BASE}/dias-no-lectivos/:id`]: () => remove(list),
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await screen.findByRole("heading", { name: "Año 2026" });
  await user.click(screen.getByRole("button", { name: /Días no lectivos/ }));
  await user.click(await screen.findByRole("button", { name: "Eliminar 28/07/2026" }));
  const dialog = screen.getByRole("dialog", { name: "Eliminar día no lectivo" });
  const confirm = () => user.click(within(dialog).getByRole("button", { name: "Eliminar día" }));
  const deletes = () => backend.calls.filter((call) => call.method === "DELETE");
  return { user, dialog, confirm, deletes };
}

const removed = (list) => {
  list.length = 0;
  return reply(204);
};

test.each(["BORRADOR", "ACTIVO"])(
  "Eliminar día no lectivo con el año %s: confirma, envía la versión y recarga",
  async (estado) => {
    const { dialog, confirm, deletes } = await openDelete(removed, [holiday], estado);
    expect(within(dialog).getByText("28/07/2026 · Fiestas Patrias")).toBeTruthy();
    expect(within(dialog).queryByLabelText(/Motivo/)).toBeNull();
    await confirm();
    expect(await screen.findByText("Día no lectivo eliminado.")).toBeTruthy();
    expect(await screen.findByText("Sin días no lectivos")).toBeTruthy();
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(deletes()).toEqual([
      expect.objectContaining({ path: `${BASE}/dias-no-lectivos/7`, query: { version: "2" } }),
    ]);
  },
);

test("Eliminar día no lectivo: cancelar no envía nada", async () => {
  const { user, dialog, deletes } = await openDelete(removed, [
    { ...holiday, descripcion: null },
  ]);
  expect(within(dialog).getByText("28/07/2026")).toBeTruthy();
  await user.click(within(dialog).getByText("Cancelar"));
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(deletes()).toHaveLength(0);
});

test("Eliminar día no lectivo: el rechazo del servidor se muestra en la ventana", async () => {
  const reason = "Solo se pueden eliminar días no lectivos de hoy o de fechas futuras";
  const { dialog, confirm } = await openDelete(() =>
    reply(422, { code: "DIA_NO_LECTIVO_PASADO", message: reason }),
  );
  await confirm();
  expect((await within(dialog).findByRole("alert")).textContent).toBe(reason);
  expect(within(dialog).getByRole("button", { name: "Eliminar día" }).disabled).toBe(false);
});

test("Eliminar día no lectivo: mientras se envía no admite un segundo envío", async () => {
  let release;
  const { user, dialog, confirm, deletes } = await openDelete(
    (list) =>
      new Promise((resolve) => {
        release = () => resolve(removed(list));
      }),
  );
  await confirm();
  const busy = within(dialog).getByText("Guardando…");
  await user.click(busy);
  // Tampoco si el formulario se reenvía sin pasar por el botón.
  fireEvent.submit(busy.closest("form"));
  expect(busy.disabled).toBe(true);
  expect(deletes()).toHaveLength(1);
  release();
  expect(await screen.findByText("Día no lectivo eliminado.")).toBeTruthy();
});

test.each([
  [409, "VERSION_DESACTUALIZADA", "El día no lectivo fue modificado; vuelva a cargarlo."],
  [404, "DIA_NO_LECTIVO_NO_ENCONTRADO", "No se encontró el día no lectivo."],
])(
  "Eliminar día no lectivo: con un %i cierra, recarga la lista y avisa",
  async (status, code, reason) => {
    const { confirm } = await openDelete((list) => {
      list.length = 0;
      return reply(status, { code, message: reason });
    });
    await confirm();
    expect(await screen.findByText(`${reason} Se recargó la lista.`)).toBeTruthy();
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(screen.queryByRole("button", { name: /^Eliminar 28/ })).toBeNull();
  },
);

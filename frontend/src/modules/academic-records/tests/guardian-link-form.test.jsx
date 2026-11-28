import { expect, test, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, signIn } from "../../../../tests/support/backend.js";
import { GuardianLinkForm, useGuardianPicker } from "@/modules/academic-records";

const guardian = (id, nombreCompleto, activo = true) => ({
  id,
  activo,
  persona: { nombreCompleto, numeroDocumento: `4000000${id}` },
});

test("El formulario compartido bloquea vinculados e inactivos y entrega el vínculo elegido", async () => {
  signIn();
  const calls = fakeBackend({
    "GET /api/apoderados": page([
      guardian(1, "Carmen Flores"),
      guardian(2, "Jorge Rojas", false),
      guardian(3, "Inés Ramos"),
    ]),
  });
  const onSave = vi.fn();
  function Screen() {
    const picker = useGuardianPicker();
    return <GuardianLinkForm picker={picker} linked={[{ apoderadoId: 1 }]} busy={false} onSave={onSave} />;
  }
  const user = userEvent.setup();
  render(<Screen />);
  const row = async (name) => (await screen.findByText(name)).closest("li");
  await row("Carmen Flores");
  await user.type(screen.getByLabelText(/Buscar apoderado/), "ramos");
  await user.click(screen.getByRole("button", { name: "Buscar" }));
  await vi.waitFor(() => expect(calls.at(-1).query.search).toBe("ramos"));
  await user.clear(screen.getByLabelText(/Buscar apoderado/));
  await user.click(screen.getByRole("button", { name: "Buscar" }));
  expect(within(await row("Carmen Flores")).getByRole("button", { name: "Ya asociado" }).disabled).toBe(true);
  expect(within(await row("Jorge Rojas")).getByRole("button", { name: "Elegir" }).disabled).toBe(true);
  expect(screen.queryByRole("button", { name: "Guardar vínculo" })).toBeNull();
  await user.click(within(await row("Inés Ramos")).getByRole("button", { name: "Elegir" }));
  expect(screen.getByLabelText(/Parentesco/).value).toBe("NO_DECLARADO");
  await user.selectOptions(screen.getByLabelText(/Parentesco/), "TUTOR_LEGAL");
  await user.click(screen.getByRole("button", { name: "Guardar vínculo" }));
  expect(onSave).toHaveBeenCalledWith({ apoderadoId: 3, parentesco: "TUTOR_LEGAL", principal: false });
});

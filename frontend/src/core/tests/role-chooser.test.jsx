import { expect, test, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RoleChooser } from "@/core/layout/role-chooser";

test("El selector conserva el rol activo si el servidor rechaza el cambio y permite cancelar", async () => {
  const browser = userEvent.setup();
  const onClose = vi.fn();
  const onSelect = vi.fn().mockRejectedValue(new Error("Ya no tienes acceso a este rol."));
  const activeUser = { role: "DOCENTE", availableRoles: ["DOCENTE", "APODERADO"] };
  render(<RoleChooser user={activeUser} onClose={onClose} onSelect={onSelect} />);

  const dialog = within(screen.getByRole("dialog", { name: "Cambiar de rol" }));
  expect(dialog.getByRole("radio", { name: /^Docente/ }).disabled).toBe(true);
  const submit = dialog.getByRole("button", { name: "Cambiar rol" });
  expect(submit.disabled).toBe(true);
  await browser.click(dialog.getByRole("radio", { name: /^Apoderado/ }));
  await browser.click(submit);

  expect(onSelect).toHaveBeenCalledWith("APODERADO");
  expect((await dialog.findByRole("alert")).textContent).toBe("Ya no tienes acceso a este rol.");
  expect(dialog.getByRole("radio", { name: /^Apoderado/ }).checked).toBe(true);
  expect(onClose).not.toHaveBeenCalled();
  await browser.click(dialog.getByRole("button", { name: "Cerrar ventana" }));
  expect(onClose).toHaveBeenCalledOnce();
});

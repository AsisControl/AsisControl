import { expect, test, vi } from "vitest";
import { createRef } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Dialog } from "../dialog";

test("al guardar con los controles bloqueados el teclado permanece dentro del diálogo", async () => {
  const close = vi.fn();
  const user = userEvent.setup();
  render(
    <>
      <button>Acción fuera de la ventana</button>
      <Dialog title="Guardando compromiso" onClose={close} busy>
        <input aria-label="Motivo" disabled />
        <button disabled>Guardando…</button>
      </Dialog>
    </>,
  );
  const dialog = screen.getByRole("dialog", { name: "Guardando compromiso" });
  expect(document.activeElement).toBe(dialog);
  await user.tab();
  expect(document.activeElement).toBe(dialog);
  await user.tab({ shift: true });
  expect(document.activeElement).toBe(dialog);
  await user.keyboard("{Escape}");
  expect(close).not.toHaveBeenCalled();
});

test("un diálogo inactivo conserva el foco exterior y deja el teclado a la confirmación activa", async () => {
  const closeForm = vi.fn();
  const closeConfirmation = vi.fn();
  const keepEditingRef = createRef();
  const user = userEvent.setup();
  const view = render(
    <>
      <button>Acción fuera de la ventana</button>
      {null}
      {null}
    </>,
  );
  const outside = screen.getByRole("button", { name: "Acción fuera de la ventana" });
  await user.click(outside);

  function layers(withConfirmation) {
    return (
      <>
        <button>Acción fuera de la ventana</button>
        <Dialog title="Formulario en pausa" onClose={closeForm} active={false}>
          <input aria-label="Concepto pendiente" defaultValue="Pensión" />
          <button>Guardar formulario</button>
        </Dialog>
        {withConfirmation && (
          <Dialog
            title="¿Descartar los cambios?"
            role="alertdialog"
            onClose={closeConfirmation}
            initialFocusRef={keepEditingRef}
          >
            <button ref={keepEditingRef}>Seguir editando</button>
            <button>Descartar cambios</button>
          </Dialog>
        )}
      </>
    );
  }

  view.rerender(layers(false));
  expect(document.activeElement).toBe(outside);
  expect(screen.queryByRole("dialog")).toBeNull();
  await user.keyboard("{Escape}");
  expect(closeForm).not.toHaveBeenCalled();

  view.rerender(layers(true));
  const confirmation = screen.getByRole("alertdialog", { name: "¿Descartar los cambios?" });
  expect(document.activeElement).toBe(keepEditingRef.current);
  await user.tab();
  expect(document.activeElement).toBe(screen.getByRole("button", { name: "Descartar cambios" }));
  await user.tab();
  expect(document.activeElement).toBe(screen.getByRole("button", { name: "Cerrar ventana" }));
  await user.tab({ shift: true });
  expect(document.activeElement).toBe(screen.getByRole("button", { name: "Descartar cambios" }));
  expect(confirmation.contains(document.activeElement)).toBe(true);
  await user.keyboard("{Escape}");
  expect(closeConfirmation).toHaveBeenCalledOnce();
  expect(closeForm).not.toHaveBeenCalled();
});

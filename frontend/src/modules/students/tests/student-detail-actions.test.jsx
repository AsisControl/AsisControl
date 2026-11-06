import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { StudentDetail } from "../student-detail";
import { changeStudentState } from "../services/student-service";

vi.mock("../services/student-service", async (importOriginal) => ({
  ...(await importOriginal()),
  changeStudentState: vi.fn(),
}));

const student = (overrides = {}) => ({
  id: 42,
  codigoAlumno: "ESTU-000042",
  activo: true,
  version: 0,
  accionesPermitidas: { editar: true, inactivar: true, reactivar: false, eliminar: true },
  apoderados: [{ id: 1, nombreCompleto: "Ana Flores", parentesco: "MADRE", principal: true }],
  persona: {
    id: 17,
    nombres: "Lucía",
    apellidoPaterno: "Flores",
    nombreCompleto: "Lucía Flores",
    fechaNacimiento: "2012-05-14",
  },
  ...overrides,
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("acciones de la ficha del estudiante", () => {
  it("valida el motivo y confirma la inactivación sin apilar ventanas", async () => {
    const user = userEvent.setup();
    const current = student();
    const updated = student({ activo: false });
    const onChanged = vi.fn();
    changeStudentState.mockResolvedValue(updated);
    render(<StudentDetail student={current} onClose={vi.fn()} onEdit={vi.fn()} onChanged={onChanged} />);

    expect(screen.getByText("Ana Flores")).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "Inactivar ficha" }));
    expect(screen.queryByText("Ficha del estudiante")).toBeNull();
    await user.click(screen.getByRole("button", { name: "Inactivar ficha" }));
    expect(changeStudentState).not.toHaveBeenCalled();
    expect(screen.getByRole("alert").textContent).toMatch(/motivo/i);

    await user.type(screen.getByRole("textbox", { name: /motivo/i }), "Cambio administrativo");
    await user.click(screen.getByRole("button", { name: "Inactivar ficha" }));
    await waitFor(() => expect(onChanged).toHaveBeenCalledWith(updated));
    expect(changeStudentState).toHaveBeenCalledWith(current, "inactivar", "Cambio administrativo");
  });

  it("explica el error del servidor y permite cancelar la confirmación", async () => {
    const user = userEvent.setup();
    changeStudentState.mockRejectedValue(new Error("Operación no disponible"));
    render(<StudentDetail student={student()} onClose={vi.fn()} onEdit={vi.fn()} onChanged={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: "Eliminar ficha" }));
    expect(screen.getByText(/eliminación es definitiva/i)).toBeTruthy();
    await user.type(screen.getByRole("textbox", { name: /motivo/i }), "Ficha duplicada sin historial");
    await user.click(screen.getByRole("button", { name: "Eliminar ficha" }));
    expect((await screen.findByRole("alert")).textContent).toMatch(/no se pudo|operación/i);
    await user.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(screen.getByText("Ficha del estudiante")).toBeTruthy();
  });

  it("solo ofrece reactivar para una ficha inactiva", async () => {
    const user = userEvent.setup();
    const current = student({
      activo: false,
      apoderados: [],
      accionesPermitidas: { editar: false, inactivar: false, reactivar: true, eliminar: false },
    });
    const onEdit = vi.fn();
    render(<StudentDetail student={current} onClose={vi.fn()} onEdit={onEdit} onChanged={vi.fn()} />);
    expect(screen.getByText(/no tiene apoderados vinculados/i)).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Editar ficha" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Inactivar ficha" })).toBeNull();
    await user.click(screen.getByRole("button", { name: "Reactivar ficha" }));
    expect(screen.getByText(/volverá a estar habilitada/i)).toBeTruthy();
    expect(onEdit).not.toHaveBeenCalled();
  });

  it("evita enviar dos veces una inactivación pendiente", async () => {
    const user = userEvent.setup();
    const current = student();
    let finishSave;
    changeStudentState.mockReturnValue(new Promise((resolve) => { finishSave = resolve; }));
    const onChanged = vi.fn();
    render(<StudentDetail student={current} onClose={vi.fn()} onEdit={vi.fn()} onChanged={onChanged} />);
    await user.click(screen.getByRole("button", { name: "Inactivar ficha" }));
    await user.type(screen.getByRole("textbox", { name: /motivo/i }), "Cambio administrativo");
    const form = screen.getByRole("textbox", { name: /motivo/i }).closest("form");
    await act(async () => {
      fireEvent.submit(form);
      fireEvent.submit(form);
    });
    expect(changeStudentState).toHaveBeenCalledOnce();
    await act(async () => finishSave(student({ activo: false })));
    expect(onChanged).toHaveBeenCalledOnce();
  });
});

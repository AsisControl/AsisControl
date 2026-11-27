// cobertura: calificar sin entrega por competencias
import { beforeEach, expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, reply, signIn } from "../../../../tests/support/backend.js";
import { MissingDeliveryGrades } from "../missing-delivery-grades";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const data = {
  alumnos: [
    { id: 1, nombre: "Lucía Prado", codigo: "E1", competencias: [10] },
    { id: 2, nombre: "Mateo Silva", codigo: null, competencias: [11] },
  ],
  competencias: [{ id: 10, nombre: "Lee" }, { id: 11, nombre: "Escribe" }],
  maximo: 20,
};

beforeEach(() => signIn({ role: "DOCENTE", permissions: ["TAREAS_CALIFICAR"] }));

test("filtra por competencia y estudiante, y confirma la calificación con observación", async () => {
  let fallar = true;
  const calls = fakeBackend({
    "POST /api/tareas/8/calificaciones-sin-entrega": () => (fallar ? reply(500, { message: "No se registró" }) : { estudiantes: 1, calificaciones: 1 }),
  });
  const onSaved = vi.fn();
  render(<MissingDeliveryGrades taskId={8} data={data} onClose={vi.fn()} onSaved={onSaved} />);
  await userEvent.click(screen.getByRole("button", { name: "Seleccionar todos" }));
  expect(screen.getByRole("status").textContent).toBe("2 estudiantes · 0 competencias · 0 notas");
  await userEvent.click(screen.getByRole("checkbox", { name: "Lee" }));
  expect(screen.getByRole("status").textContent).toBe("1 estudiantes · 1 competencias · 1 notas");
  expect(screen.queryByText("Mateo Silva")).toBeNull();
  await userEvent.click(screen.getByRole("checkbox", { name: "Lee" }));
  await userEvent.type(screen.getByRole("searchbox", { name: "Buscar estudiante" }), "zzz");
  expect(screen.getByText("No hay estudiantes disponibles para esta selección.")).toBeTruthy();
  await userEvent.clear(screen.getByRole("searchbox", { name: "Buscar estudiante" }));
  await userEvent.click(screen.getByRole("checkbox", { name: "Lee" }));
  await userEvent.click(screen.getByRole("button", { name: "Limpiar selección" }));
  await userEvent.click(screen.getByRole("checkbox", { name: /Lucía Prado/ }));
  await userEvent.type(screen.getByLabelText("Calificación (0 a 20)"), "0");
  await userEvent.type(screen.getByLabelText(/Observación opcional/), " No presentó ");
  await userEvent.click(screen.getByRole("button", { name: "Aplicar calificación" }));
  await userEvent.click(await screen.findByRole("button", { name: "Volver" }));
  await userEvent.click(screen.getByRole("button", { name: "Aplicar calificación" }));
  await userEvent.click(await screen.findByRole("button", { name: "Confirmar calificación" }));
  expect(await screen.findByText("No se registró")).toBeTruthy();
  fallar = false;
  await userEvent.click(screen.getByRole("button", { name: "Aplicar calificación" }));
  await userEvent.click(await screen.findByRole("button", { name: "Confirmar calificación" }));
  expect(onSaved).toHaveBeenCalledWith(1);
  expect(calls.at(-1).body).toEqual({ alumnos: [1], competencias: [10], nota: "0", observacion: "No presentó" });
});

test("una práctica sin competencias en el modelo cualitativo califica con nivel", async () => {
  const calls = fakeBackend({ "POST /api/tareas/8/calificaciones-sin-entrega": { estudiantes: 2, calificaciones: 2 } });
  const onSaved = vi.fn();
  render(<MissingDeliveryGrades taskId={8} data={{ ...data, competencias: [], modeloCualitativo: true }}
    onClose={vi.fn()} onSaved={onSaved} />);
  expect(screen.getByText("Nota de práctica · Sin peso")).toBeTruthy();
  await userEvent.click(screen.getByRole("button", { name: "Seleccionar todos" }));
  expect(screen.getByRole("status").textContent).toBe("2 estudiantes · 2 notas");
  await userEvent.click(screen.getByRole("button", { name: "C: Nivel para la selección" }));
  await userEvent.click(screen.getByRole("button", { name: "Aplicar calificación" }));
  await userEvent.click(await screen.findByRole("button", { name: "Confirmar calificación" }));
  expect(onSaved).toHaveBeenCalledWith(2);
  expect(calls.at(-1).body).toEqual({ alumnos: [1, 2], competencias: [], nivelLogro: "C" });
});

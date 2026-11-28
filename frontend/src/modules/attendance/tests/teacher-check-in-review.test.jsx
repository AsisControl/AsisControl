// cobertura: revisión de marcaciones docentes del día
import { beforeEach, expect, test, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { TeacherCheckInReview } from "../teacher-check-in/teacher-check-in-review";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const marcacion = {
  id: 1, docenteId: 4, docenteNombre: "Ana Rojas", fechaJornada: "2026-11-19", fechaHoraMarcada: "2026-11-19T07:50:00",
  estado: "PRESENTE", verificacion: "OBSERVADA",
};

beforeEach(() => signIn({ permissions: ["ASISTENCIA_LEER"] }));

test("abre las clases del docente, filtra las pendientes y cambia de día", async () => {
  let fallarClases = true;
  const calls = fakeBackend({
    "GET /api/asistencias/docentes": ({ query }) => page(query.verificacion ? [] : [marcacion]),
    "GET /api/asistencias/docentes/4/clases": () => (fallarClases ? reply(500, { message: "Clases no disponibles" }) : []),
  });
  const onDayChange = vi.fn();
  render(<TeacherCheckInReview day="2026-11-19" onDayChange={onDayChange} refreshKey={0} />);
  const fila = await screen.findByRole("button", { name: "Ana Rojas" });
  await userEvent.click(fila);
  expect(await screen.findByText("Clases no disponibles")).toBeTruthy();
  await userEvent.click(fila);
  expect(screen.queryByText("Clases no disponibles")).toBeNull();
  fallarClases = false;
  await userEvent.click(fila);
  expect(await screen.findByText("No tiene clases programadas este día.")).toBeTruthy();
  await userEvent.click(screen.getByRole("button", { name: "Pendientes de confirmación" }));
  expect(await screen.findByText("No hay marcaciones pendientes de confirmación.")).toBeTruthy();
  expect(calls.filter(c => c.path === "/api/asistencias/docentes").at(-1).query.verificacion).toBe("OBSERVADA");
  await userEvent.click(screen.getByRole("button", { name: "Todas" }));
  await screen.findByRole("button", { name: "Ana Rojas" });
  fireEvent.change(screen.getByLabelText("Día"), { target: { value: "2026-11-18" } });
  expect(onDayChange).toHaveBeenCalledWith("2026-11-18");
});

test("informa si fallan las marcaciones y si el día no tiene marcaciones", async () => {
  fakeBackend({ "GET /api/asistencias/docentes": reply(500, { message: "Marcaciones no disponibles" }) });
  const { unmount } = render(<TeacherCheckInReview day="2026-11-19" onDayChange={vi.fn()} refreshKey={0} />);
  expect(await screen.findByText("Marcaciones no disponibles")).toBeTruthy();
  unmount();
  fakeBackend({ "GET /api/asistencias/docentes": page([]) });
  render(<TeacherCheckInReview day="2026-11-19" onDayChange={vi.fn()} refreshKey={0} />);
  expect(await screen.findByText("No hay marcaciones en este día.")).toBeTruthy();
});

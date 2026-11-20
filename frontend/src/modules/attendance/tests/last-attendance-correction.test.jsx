import { beforeEach, expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { LastAttendanceCorrection } from "../reports/last-attendance-correction";
import { listarCorrecciones, obtenerSesion } from "../services/asistencia-service";

vi.mock("../services/asistencia-service", () => ({ listarCorrecciones: vi.fn(), obtenerSesion: vi.fn() }));
beforeEach(() => {
  obtenerSesion.mockResolvedValue({ id: 7, alumnos: [{ alumnoId: 10, asistenciaId: 1 }, { alumnoId: 20, asistenciaId: 2 }] });
  listarCorrecciones.mockResolvedValue([]);
});

test("muestra la última corrección del alumno y no la más reciente de su compañero", async () => {
  listarCorrecciones.mockResolvedValue([
    { id: 3, fechaHora: "2026-11-19T12:00:00", actorNombre: "Otra persona", motivo: "Cambio del compañero", cambios: [{ alumnoId: 20 }] },
    { id: 1, fechaHora: "2026-11-19T09:00:00", docente: "Profesor", motivo: "Motivo anterior", cambios: [{ alumnoId: 10 }] },
    { id: 2, fechaHora: "2026-11-19T11:00:00", actorNombre: "Ana Pérez", motivo: "Se verificó la presencia", cambios: [{ alumnoId: 10 }] },
  ]);
  render(<LastAttendanceCorrection sessionId={7} attendanceId={1} />);
  expect(await screen.findByText("Se verificó la presencia")).toBeTruthy();
  expect(screen.getByText("19/11/2026 a las 11:00 · Ana Pérez")).toBeTruthy();
  expect(screen.queryByText("Cambio del compañero")).toBeNull();
  expect(screen.queryByText("Motivo anterior")).toBeNull();
});

test("no inventa un resumen para un alumno sin correcciones propias", async () => {
  listarCorrecciones.mockResolvedValue([{ id: 1, fechaHora: "2026-11-19T12:00:00", motivo: "Cambio ajeno", cambios: [{ alumnoId: 20 }] }]);
  const { container } = render(<LastAttendanceCorrection sessionId={7} attendanceId={1} />);
  await vi.waitFor(() => expect(listarCorrecciones).toHaveBeenCalledWith(7));
  expect(container.textContent).toBe("");
});

test("un fallo de consulta no se presenta como ausencia de correcciones", async () => {
  listarCorrecciones.mockRejectedValue(new Error("No se pudo consultar la corrección"));
  render(<LastAttendanceCorrection sessionId={7} attendanceId={1} />);
  expect(await screen.findByText("No se pudo consultar la corrección")).toBeTruthy();
});

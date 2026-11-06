import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { listarMisMarcaciones } from "../teacher-check-in/services/asistencia-docente-service";
import { TeacherAttendance } from "../teacher-check-in/teacher-attendance";

vi.mock("../justification-inbox", () => ({
  JustificationInbox: () => <h2>Bandeja de justificaciones</h2>,
}));
vi.mock("../teacher-check-in/services/asistencia-docente-service", () => ({
  marcarAsistencia: vi.fn(),
  listarMisMarcaciones: vi.fn(),
}));

describe("US-15 · pestañas de Asistencia del docente", () => {
  beforeEach(() => {
    listarMisMarcaciones.mockResolvedValue([]);
  });
  afterEach(cleanup);

  it("por defecto mantiene la bandeja de justificaciones (US-14)", () => {
    render(<TeacherAttendance canCheckIn />);
    expect(screen.getByText("Bandeja de justificaciones")).toBeTruthy();
    expect(screen.getByRole("link", { name: "Justificaciones" }).getAttribute("aria-current")).toBe("page");
    expect(screen.getByRole("link", { name: "Mi marcación" }).getAttribute("href")).toBe("/asistencia/mi-marcacion");
  });

  it("en /asistencia/mi-marcacion muestra la marcación con QR", async () => {
    render(<TeacherAttendance tab="mi-marcacion" canCheckIn />);
    expect(await screen.findByRole("button", { name: "Escanear código QR" })).toBeTruthy();
    expect(screen.getByRole("link", { name: "Mi marcación" }).getAttribute("aria-current")).toBe("page");
    expect(screen.queryByText("Bandeja de justificaciones")).toBeNull();
  });
});

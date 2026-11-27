import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, signIn } from "../../../../tests/support/backend.js";
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

  it("el docente envía su hora de ingreso institucional y solo ve el seguimiento", async () => {
    signIn({ role: "DOCENTE" });
    let item;
    const path = "/api/asistencias/docentes/contingencias";
    const calls = fakeBackend({
      [`GET ${path}`]: () => page(item ? [item] : []),
      [`POST ${path}`]: ({ body }) => {
        item = { ...body, id: 4, estado: "PENDIENTE", fechaEnvio: "2026-11-20T12:00:00Z" };
        return item;
      },
    });
    render(<TeacherAttendance tab="contingencias" canCheckIn />);
    await userEvent.click(screen.getByRole("button", { name: "Reportar fallo de marcación" }));
    const dialog = within(screen.getByRole("dialog"));
    fireEvent.change(dialog.getByLabelText(/Fecha de la jornada/), { target: { value: "2025-10-15" } });
    fireEvent.change(dialog.getByLabelText(/Hora de llegada/), { target: { value: "07:35" } });
    await userEvent.type(dialog.getByLabelText(/Motivo del reporte/), "El lector no funcionó");
    await userEvent.click(dialog.getByRole("button", { name: "Enviar solicitud" }));
    await screen.findByText("Solicitud enviada para revisión.");
    const row = (await screen.findByText("El lector no funcionó")).closest("tr");
    expect(within(row).getByText("Pendiente")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Aprobar" })).toBeNull();
    expect(calls.find(({ method }) => method === "POST").body).toEqual({
      fechaJornada: "2025-10-15", horaReportada: "07:35", motivo: "El lector no funcionó",
    });
  });
});

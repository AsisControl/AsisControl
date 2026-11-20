import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AttendanceAdmin } from "../teacher-check-in/attendance-admin";
import { markingLink, tokenFromScan } from "../teacher-check-in/format";
import {
  clasesDelDia,
  emitirQr,
  listarMarcacionesDocentes,
  resumenMarcaciones,
} from "../teacher-check-in/services/asistencia-docente-service";

vi.mock("../teacher-check-in/services/asistencia-docente-service", () => ({
  QR_VIGENCIA_SEGUNDOS: 90,
  emitirQr: vi.fn(),
  renovarQr: vi.fn(),
  cancelarQr: vi.fn().mockResolvedValue(null),
  resumenMarcaciones: vi.fn(),
  listarMarcacionesDocentes: vi.fn(),
  clasesDelDia: vi.fn(),
}));

const marcacion = (id, overrides = {}) => ({
  id,
  docenteId: 10 + id,
  docenteNombre: id === 1 ? "Patricia Morales" : "Luis Rojas",
  codigoQRId: 3,
  fechaJornada: "2026-11-18",
  fechaHoraMarcada: "2026-11-18T19:02:00",
  estado: "PRESENTE",
  contingencia: false,
  motivoContingencia: null,
  version: 0,
  sesionClaseId: 5,
  verificacion: id === 1 ? "OBSERVADA" : "CONFIRMADA",
  ...overrides,
});

describe("asistencia docente · administración", () => {
  beforeEach(() => {
    emitirQr.mockResolvedValue({
      id: 1,
      token: "token-1",
      fechaHoraEmision: "2026-11-18T19:00:00",
      fechaHoraExpiracion: "2026-11-18T19:01:30",
      estado: "VIGENTE",
    });
    resumenMarcaciones.mockResolvedValue({
      fecha: "2026-11-18", totalDocentes: 12, marcaron: 8, pendientesConfirmacion: 2, sinMarcar: 4,
    });
    listarMarcacionesDocentes.mockResolvedValue([marcacion(1), marcacion(2)]);
    clasesDelDia.mockResolvedValue([
      { sesionId: 5, curso: "Matemática", seccion: "3° A", horaInicio: "19:00:00", horaFin: "21:00:00", estado: "PENDIENTE" },
    ]);
  });
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("muestra el resumen del día y las marcaciones con su verificación", async () => {
    render(<AttendanceAdmin canWrite />);
    expect(await screen.findByText("8 de 12")).toBeTruthy();
    expect(screen.getByText("Sin marcar").nextElementSibling.textContent).toBe("4");
    expect(await screen.findByText("Patricia Morales")).toBeTruthy();
    expect(screen.getByText("Pendiente de confirmación", { selector: "span" })).toBeTruthy();
    expect(screen.getByText("Confirmada")).toBeTruthy();
  });

  it("filtra las marcaciones pendientes de confirmación", async () => {
    const user = userEvent.setup();
    render(<AttendanceAdmin canWrite />);
    await screen.findByText("Patricia Morales");
    await user.click(screen.getByRole("button", { name: "Pendientes de confirmación" }));
    expect(listarMarcacionesDocentes).toHaveBeenLastCalledWith(
      expect.objectContaining({ verificacion: "OBSERVADA" }),
    );
  });

  it("abre el detalle con las clases del día y el estado de su lista de alumnos", async () => {
    const user = userEvent.setup();
    render(<AttendanceAdmin canWrite />);
    await user.click(await screen.findByRole("button", { name: "Patricia Morales" }));
    const detail = await screen.findByText(/Matemática · 3° A/);
    expect(clasesDelDia).toHaveBeenCalledWith(11, "2026-11-18");
    expect(within(detail.closest("li")).getByText("19:00 a 21:00")).toBeTruthy();
    expect(within(detail.closest("li")).getByText(/lista de alumnos: sin registrar/i)).toBeTruthy();
  });

  it("el QR del punto de marcación es un enlace que lleva a marcar con la cuenta del docente", () => {
    const link = markingLink("https://colegio.test", "abc 123");
    expect(link).toBe("https://colegio.test/asistencia/mi-marcacion?codigo=abc%20123");
    expect(tokenFromScan(link)).toBe("abc 123");
    expect(tokenFromScan("  codigo-suelto ")).toBe("codigo-suelto");
  });
});

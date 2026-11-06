import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/core/api/client";
import { todayInLima } from "../teacher-check-in/format";
import { listarMisMarcaciones, marcarAsistencia } from "../teacher-check-in/services/asistencia-docente-service";
import { TeacherCheckIn } from "../teacher-check-in/teacher-check-in";

vi.mock("../teacher-check-in/services/asistencia-docente-service", () => ({
  marcarAsistencia: vi.fn(),
  listarMisMarcaciones: vi.fn(),
}));

const record = (overrides = {}) => ({
  id: 1,
  docenteId: 7,
  codigoQRId: 30,
  fechaJornada: "2026-11-06",
  fechaHoraMarcada: "2026-11-06T07:52:41",
  estado: "PRESENTE",
  contingencia: false,
  motivoContingencia: null,
  version: 0,
  ...overrides,
});

async function typeCode(user, code) {
  await user.type(screen.getByLabelText(/ingresa el código/i), code);
  await user.click(screen.getByRole("button", { name: "Registrar con código" }));
}

describe("US-15 · marcación del docente", () => {
  beforeEach(() => {
    listarMisMarcaciones.mockResolvedValue([]);
  });
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    delete navigator.mediaDevices;
  });

  it("envía solo el token y muestra el estado y la hora del servidor", async () => {
    const user = userEvent.setup();
    marcarAsistencia.mockResolvedValue(record({ estado: "TARDANZA", fechaHoraMarcada: "2026-11-06T08:14:05" }));
    render(<TeacherCheckIn canWrite />);
    await typeCode(user, "  token-abc  ");
    expect(await screen.findByText("Tardanza")).toBeTruthy();
    expect(screen.getByText("08:14")).toBeTruthy();
    expect(marcarAsistencia).toHaveBeenCalledWith("token-abc");
    expect(marcarAsistencia.mock.calls[0]).toHaveLength(1);
  });

  it("no envía un código vacío y marca el campo en error", async () => {
    const user = userEvent.setup();
    render(<TeacherCheckIn canWrite />);
    await user.click(screen.getByRole("button", { name: "Registrar con código" }));
    expect(screen.getByText(/ingresa el código que aparece/i)).toBeTruthy();
    expect(screen.getByLabelText(/ingresa el código/i).getAttribute("aria-invalid")).toBe("true");
    expect(marcarAsistencia).not.toHaveBeenCalled();
    await user.type(screen.getByLabelText(/ingresa el código/i), "x");
    expect(screen.queryByText(/ingresa el código que aparece/i)).toBeNull();
  });

  it("rechaza un QR vencido", async () => {
    const user = userEvent.setup();
    marcarAsistencia.mockRejectedValue(new ApiError("El código QR ya expiró", 422, "QR_EXPIRADO"));
    render(<TeacherCheckIn canWrite />);
    await typeCode(user, "viejo");
    expect((await screen.findByRole("alert")).textContent).toMatch(/ya venció/i);
  });

  it("ante un duplicado muestra el registro existente de la jornada", async () => {
    const user = userEvent.setup();
    const today = todayInLima();
    marcarAsistencia.mockRejectedValue(new ApiError("Ya existe una marcación para la jornada", 409, "ASISTENCIA_DUPLICADA"));
    listarMisMarcaciones
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([record({ fechaJornada: today, fechaHoraMarcada: `${today}T07:40:00` })]);
    render(<TeacherCheckIn canWrite />);
    await typeCode(user, "otro");
    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toMatch(/ya existe una marcación/i);
    await waitFor(() => expect(alert.textContent).toMatch(/tu registro de hoy: presente a las 07:40/i));
  });

  it("explica el rechazo de un perfil no habilitado", async () => {
    const user = userEvent.setup();
    marcarAsistencia.mockRejectedValue(new ApiError("Forbidden", 403));
    render(<TeacherCheckIn canWrite />);
    await typeCode(user, "token");
    expect((await screen.findByRole("alert")).textContent).toMatch(/no está habilitado/i);
  });

  it("muestra el historial de marcaciones", async () => {
    listarMisMarcaciones.mockResolvedValue([
      record(),
      record({ id: 2, fechaJornada: "2026-11-05", fechaHoraMarcada: null, estado: "SIN_MARCACION" }),
    ]);
    render(<TeacherCheckIn canWrite />);
    expect(await screen.findByText("Sin marcación")).toBeTruthy();
    expect(screen.getByText("06/11/2026")).toBeTruthy();
    expect(screen.getByText("07:52")).toBeTruthy();
  });

  it("sin permiso de escritura no ofrece la marcación", () => {
    render(<TeacherCheckIn canWrite={false} />);
    expect(screen.getByText(/no está habilitado para marcar/i)).toBeTruthy();
    expect(screen.queryByRole("button", { name: /escanear/i })).toBeNull();
  });

  it("si el navegador no da cámara, lo indica y deja el ingreso manual", async () => {
    const user = userEvent.setup();
    render(<TeacherCheckIn canWrite />);
    await user.click(screen.getByRole("button", { name: "Escanear código QR" }));
    expect((await screen.findByRole("alert")).textContent).toMatch(/ingresa el código manualmente/i);
    await user.click(screen.getByRole("button", { name: "Cerrar cámara" }));
    expect(screen.getByRole("button", { name: "Escanear código QR" })).toBeTruthy();
  });

  it("avisa cuando se niega el permiso de la cámara", async () => {
    const user = userEvent.setup();
    Object.defineProperty(navigator, "mediaDevices", {
      configurable: true,
      value: { getUserMedia: vi.fn().mockRejectedValue(new DOMException("denied", "NotAllowedError")) },
    });
    render(<TeacherCheckIn canWrite />);
    await user.click(screen.getByRole("button", { name: "Escanear código QR" }));
    expect((await screen.findByRole("alert")).textContent).toMatch(/no diste permiso/i);
  });
});

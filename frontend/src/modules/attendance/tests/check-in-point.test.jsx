import { act, cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/core/api/client";
import { CheckInPoint } from "../teacher-check-in/check-in-point";
import {
  cancelarQr,
  emitirQr,
  renovarQr,
} from "../teacher-check-in/services/asistencia-docente-service";

vi.mock("../teacher-check-in/services/asistencia-docente-service", () => ({
  QR_VIGENCIA_SEGUNDOS: 60,
  emitirQr: vi.fn(),
  renovarQr: vi.fn(),
  cancelarQr: vi.fn(),
}));

const code = (id, overrides = {}) => ({
  id,
  token: `token-${id}`,
  fechaHoraEmision: "2026-11-06T07:50:00",
  fechaHoraExpiracion: "2026-11-06T07:51:00",
  estado: "VIGENTE",
  ...overrides,
});

const advance = (ms) => act(() => vi.advanceTimersByTimeAsync(ms));

describe("punto de marcación institucional con QR dinámico", () => {
  let user;
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    emitirQr.mockResolvedValue(code(1));
    cancelarQr.mockResolvedValue(null);
  });
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  async function open() {
    const view = render(<CheckInPoint canWrite />);
    await screen.findByText(/se renueva en/i);
    return view;
  }

  it("abre el punto sin elegir docente y muestra un QR con el enlace de marcación", async () => {
    const { container } = await open();
    expect(emitirQr).toHaveBeenCalledWith();
    expect(container.querySelector("svg")).toBeTruthy();
    expect(screen.getByText("60 s")).toBeTruthy();
    expect(screen.getByText("token-1")).toBeTruthy();
  });

  it("renueva el código antes de que venza", async () => {
    renovarQr.mockResolvedValue(code(2));
    await open();
    await advance(31000);
    expect(renovarQr).toHaveBeenCalledWith(1);
    expect(await screen.findByText("token-2")).toBeTruthy();
  });

  it("si falla la renovación retira el QR y muestra el error", async () => {
    renovarQr.mockRejectedValue(new ApiError("No se pudo renovar", 503));
    await open();
    await advance(31000);
    expect((await screen.findByRole("alert")).textContent).toMatch(/no se pudo renovar/i);
    expect(screen.queryByText("token-1")).toBeNull();
  });

  it("muestra el rechazo del backend y permite reintentar", async () => {
    emitirQr.mockRejectedValueOnce(new ApiError("No hay conexión con el servidor", 503));
    render(<CheckInPoint canWrite />);
    expect((await screen.findByRole("alert")).textContent).toMatch(/no hay conexión/i);
    await user.click(screen.getByRole("button", { name: "Reintentar" }));
    expect(await screen.findByText(/se renueva en/i)).toBeTruthy();
  });

  it("amplía el QR para la pantalla de la entrada y se cierra con Escape", async () => {
    await open();
    await user.click(screen.getByRole("button", { name: "Ampliar para la entrada" }));
    expect(screen.getByRole("dialog", { name: "Código QR de marcación" })).toBeTruthy();
    expect(screen.getByText("Escanea con la cámara de tu celular")).toBeTruthy();
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("al salir de la pantalla cierra el código vigente", async () => {
    const { unmount } = await open();
    unmount();
    expect(cancelarQr).toHaveBeenCalledWith(1);
  });

  it("sin permiso no emite códigos", () => {
    render(<CheckInPoint canWrite={false} />);
    expect(screen.getByText(/no tiene permiso para emitir/i)).toBeTruthy();
    expect(emitirQr).not.toHaveBeenCalled();
  });
});

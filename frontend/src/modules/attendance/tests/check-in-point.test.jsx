import { act, cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/core/api/client";
import { CheckInPoint } from "../teacher-check-in/check-in-point";
import {
  cancelarQr,
  consultarQr,
  emitirQr,
  listarDocentes,
  renovarQr,
} from "../teacher-check-in/services/asistencia-docente-service";

vi.mock("../teacher-check-in/services/asistencia-docente-service", () => ({
  QR_VIGENCIA_SEGUNDOS: 60,
  listarDocentes: vi.fn(),
  emitirQr: vi.fn(),
  renovarQr: vi.fn(),
  consultarQr: vi.fn(),
  cancelarQr: vi.fn(),
}));

const code = (id, overrides = {}) => ({
  id,
  docenteId: 7,
  token: `token-${id}`,
  fechaHoraEmision: "2026-11-06T07:50:00",
  fechaHoraExpiracion: "2026-11-06T07:51:00",
  estado: "VIGENTE",
  ...overrides,
});

const advance = (ms) => act(() => vi.advanceTimersByTimeAsync(ms));

describe("US-15 · punto de marcación con QR dinámico", () => {
  let user;
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    listarDocentes.mockResolvedValue([
      { id: 7, label: "Patricia Morales · 40112233" },
      { id: 8, label: "Jorge Ríos · 40998877" },
    ]);
    emitirQr.mockResolvedValue(code(1));
    consultarQr.mockImplementation((id) => Promise.resolve(code(id)));
    cancelarQr.mockResolvedValue(null);
  });
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  async function open(name = /patricia/i) {
    const view = render(<CheckInPoint canWrite />);
    await user.click(await screen.findByRole("button", { name }));
    await screen.findByText(/vence en/i);
    return view;
  }

  it("emite el QR del docente elegido con vigencia limitada", async () => {
    const { container } = await open();
    expect(emitirQr).toHaveBeenCalledWith(7);
    expect(container.querySelector("svg")).toBeTruthy();
    expect(screen.getByText("60 s")).toBeTruthy();
    expect(screen.getByText("token-1")).toBeTruthy();
  });

  it("filtra la lista de docentes", async () => {
    render(<CheckInPoint canWrite />);
    await screen.findByRole("button", { name: /patricia/i });
    await user.type(screen.getByLabelText("Buscar docente"), "jorge");
    expect(screen.queryByRole("button", { name: /patricia/i })).toBeNull();
    expect(screen.getByRole("button", { name: /jorge/i })).toBeTruthy();
  });

  it("renueva el código antes de que venza", async () => {
    renovarQr.mockResolvedValue(code(2));
    await open();
    await advance(51000);
    expect(renovarQr).toHaveBeenCalledWith(1);
    expect(await screen.findByText("token-2")).toBeTruthy();
  });

  it("detecta que el docente ya marcó", async () => {
    await open();
    consultarQr.mockResolvedValue(code(1, { estado: "UTILIZADO" }));
    await advance(3100);
    expect(await screen.findByText(/asistencia registrada para/i)).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "Atender a otro docente" }));
    expect(screen.getByText(/selecciona un docente/i)).toBeTruthy();
  });

  it("si al renovar el código ya fue usado, lo da por registrado", async () => {
    let renewAttempted = false;
    renovarQr.mockImplementation(() => {
      renewAttempted = true;
      return Promise.reject(new ApiError("ya registró asistencia", 409, "QR_YA_UTILIZADO"));
    });
    await open();
    consultarQr.mockImplementation((id) => Promise.resolve(code(id, {
      estado: renewAttempted ? "UTILIZADO" : "VIGENTE",
    })));
    await advance(51000);
    expect(await screen.findByText(/asistencia registrada para/i)).toBeTruthy();
  });

  it("si falla la renovación y el código sigue vigente, retira el QR y muestra el error", async () => {
    renovarQr.mockRejectedValue(new ApiError("No se pudo renovar", 503));
    await open();
    await advance(51000);
    expect(renovarQr).toHaveBeenCalledWith(1);
    expect((await screen.findByRole("alert")).textContent).toMatch(/no se pudo renovar/i);
    expect(screen.queryByText("token-1")).toBeNull();
  });

  it("un fallo temporal de consulta no marca al docente ni cancela el código", async () => {
    await open();
    consultarQr.mockRejectedValueOnce(new Error("Red intermitente"));
    await advance(3100);
    expect(screen.getByText("token-1")).toBeTruthy();
    consultarQr.mockResolvedValue(code(1, { estado: "UTILIZADO" }));
    await advance(3100);
    expect(await screen.findByText(/asistencia registrada para/i)).toBeTruthy();
  });

  it("un fallo de red al comprobar una renovación fallida retira el código", async () => {
    renovarQr.mockRejectedValue(new Error("Renovación sin respuesta"));
    await open();
    consultarQr.mockRejectedValue(new Error("Consulta sin respuesta"));
    await advance(51000);
    expect((await screen.findByRole("alert")).textContent).toMatch(/renovación sin respuesta/i);
    expect(screen.queryByText("token-1")).toBeNull();
  });

  it("ignora una renovación que termina después de cerrar el código", async () => {
    let finishRenewal;
    renovarQr.mockReturnValue(new Promise((resolve) => { finishRenewal = resolve; }));
    await open();
    await advance(51000);
    expect(renovarQr).toHaveBeenCalledWith(1);
    await user.click(screen.getByRole("button", { name: "Cerrar código" }));
    await act(async () => finishRenewal(code(2)));
    expect(screen.queryByText("token-2")).toBeNull();
    expect(screen.getByText(/selecciona un docente/i)).toBeTruthy();
  });

  it("ignora el rechazo tardío de renovación y el fallo al cancelar", async () => {
    let failRenewal;
    renovarQr.mockReturnValue(new Promise((_, reject) => { failRenewal = reject; }));
    cancelarQr.mockRejectedValueOnce(new Error("Ya expiró"));
    await open();
    await advance(51000);
    await user.click(screen.getByRole("button", { name: "Cerrar código" }));
    await act(async () => failRenewal(new Error("Renovación tardía")));
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.getByText(/selecciona un docente/i)).toBeTruthy();
  });

  it("ignora la consulta de una renovación fallida si se cerró el código durante la espera", async () => {
    let finishCheck;
    let renewAttempted = false;
    renovarQr.mockImplementation(() => {
      renewAttempted = true;
      return Promise.reject(new Error("Renovación fallida"));
    });
    consultarQr.mockImplementation((id) => renewAttempted
      ? new Promise((resolve) => { finishCheck = resolve; })
      : Promise.resolve(code(id)));
    await open();
    await advance(51000);
    expect(renewAttempted).toBe(true);
    await user.click(screen.getByRole("button", { name: "Cerrar código" }));
    await act(async () => finishCheck(code(1, { estado: "UTILIZADO" })));
    expect(screen.queryByText(/asistencia registrada para/i)).toBeNull();
    expect(screen.getByText(/selecciona un docente/i)).toBeTruthy();
  });

  it("muestra el rechazo del backend y permite reintentar", async () => {
    emitirQr.mockRejectedValueOnce(new ApiError("El docente ya tiene marcación en la jornada", 409));
    render(<CheckInPoint canWrite />);
    await user.click(await screen.findByRole("button", { name: /patricia/i }));
    expect((await screen.findByRole("alert")).textContent).toMatch(/ya tiene marcación/i);
    await user.click(screen.getByRole("button", { name: "Reintentar" }));
    expect(await screen.findByText(/vence en/i)).toBeTruthy();
  });

  it("cierra el código vigente", async () => {
    await open();
    await user.click(screen.getByRole("button", { name: "Cerrar código" }));
    expect(cancelarQr).toHaveBeenCalledWith(1);
    expect(screen.getByText(/selecciona un docente/i)).toBeTruthy();
  });

  it("sin permiso no carga docentes ni emite códigos", () => {
    render(<CheckInPoint canWrite={false} />);
    expect(screen.getByText(/no tiene permiso para emitir/i)).toBeTruthy();
    expect(listarDocentes).not.toHaveBeenCalled();
  });
});

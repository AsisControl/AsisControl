import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import jsQR from "jsqr";
import { QrScanner } from "../teacher-check-in/qr-scanner";

vi.mock("jsqr", () => ({ default: vi.fn() }));

describe("lector QR de asistencia", () => {
  let frames;
  let stop;
  let stream;

  beforeEach(() => {
    vi.clearAllMocks();
    frames = [];
    stop = vi.fn();
    stream = { getTracks: () => [{ stop }] };
    Object.defineProperty(window, "isSecureContext", { configurable: true, value: true });
    Object.defineProperty(navigator, "mediaDevices", {
      configurable: true,
      value: { getUserMedia: vi.fn().mockResolvedValue(stream) },
    });
    Object.defineProperty(HTMLVideoElement.prototype, "readyState", { configurable: true, value: 2 });
    Object.defineProperty(HTMLVideoElement.prototype, "videoWidth", { configurable: true, value: 1280 });
    Object.defineProperty(HTMLVideoElement.prototype, "videoHeight", { configurable: true, value: 720 });
    vi.spyOn(HTMLMediaElement.prototype, "play").mockResolvedValue();
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({
      drawImage: vi.fn(),
      getImageData: () => ({ data: new Uint8ClampedArray(4), width: 1, height: 1 }),
    });
    vi.stubGlobal("requestAnimationFrame", vi.fn((callback) => frames.push(callback)));
    vi.stubGlobal("cancelAnimationFrame", vi.fn());
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    delete navigator.mediaDevices;
    delete window.isSecureContext;
  });

  it("lee el QR una sola vez y apaga la cámara al detectar el código", async () => {
    const onDetected = vi.fn();
    jsQR.mockReturnValueOnce(null).mockReturnValueOnce({ data: "token-del-docente" });
    render(<QrScanner onDetected={onDetected} onCancel={vi.fn()} />);

    expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith({
      video: { facingMode: "environment" }, audio: false,
    });
    await waitFor(() => expect(screen.getByLabelText(/vista de la cámara/i).srcObject).toBe(stream));
    await waitFor(() => expect(frames).toHaveLength(1));
    await act(async () => frames.shift()());
    expect(onDetected).not.toHaveBeenCalled();
    const detectedFrame = frames.shift();
    await act(async () => detectedFrame());
    await act(async () => detectedFrame());
    expect(onDetected).toHaveBeenCalledOnce();
    expect(onDetected).toHaveBeenCalledWith("token-del-docente");
    expect(stop).toHaveBeenCalled();
    expect(jsQR).toHaveBeenCalledTimes(2);
  });

  it("no solicita cámara en una página insegura y permite cerrar", async () => {
    const onCancel = vi.fn();
    Object.defineProperty(window, "isSecureContext", { configurable: true, value: false });
    render(<QrScanner onDetected={vi.fn()} onCancel={onCancel} />);
    expect(screen.getByRole("alert").textContent).toMatch(/conexión segura/i);
    expect(navigator.mediaDevices.getUserMedia).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "Cerrar cámara" }));
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it("libera una cámara que se abre después de cerrar el lector", async () => {
    let openCamera;
    navigator.mediaDevices.getUserMedia.mockReturnValue(new Promise((resolve) => { openCamera = resolve; }));
    const view = render(<QrScanner onDetected={vi.fn()} onCancel={vi.fn()} />);
    view.unmount();
    await act(async () => openCamera(stream));
    expect(stop).toHaveBeenCalled();
  });

  it("muestra una alternativa cuando falla la cámara", async () => {
    navigator.mediaDevices.getUserMedia.mockRejectedValue(new Error("Cámara ocupada"));
    render(<QrScanner onDetected={vi.fn()} onCancel={vi.fn()} />);
    expect((await screen.findByRole("alert")).textContent).toMatch(/ingresa el código manualmente/i);
  });

  it("explica cuando el navegador no ofrece la API de cámara", () => {
    delete navigator.mediaDevices;
    render(<QrScanner onDetected={vi.fn()} onCancel={vi.fn()} />);
    expect(screen.getByRole("alert").textContent).toMatch(/navegador no permite usar la cámara/i);
  });

  it("continúa leyendo si el navegador rechaza reproducir el video", async () => {
    HTMLMediaElement.prototype.play.mockRejectedValueOnce(new Error("Autoplay bloqueado"));
    render(<QrScanner onDetected={vi.fn()} onCancel={vi.fn()} />);
    await waitFor(() => expect(frames).toHaveLength(1));
    expect(screen.getByLabelText(/vista de la cámara/i)).toBeTruthy();
  });

  it("ignora errores de cámara recibidos después de cerrar el lector", async () => {
    let rejectCamera;
    navigator.mediaDevices.getUserMedia.mockReturnValue(new Promise((_, reject) => { rejectCamera = reject; }));
    const view = render(<QrScanner onDetected={vi.fn()} onCancel={vi.fn()} />);
    view.unmount();
    await act(async () => rejectCamera(new Error("Cámara cerrada")));
    expect(frames).toHaveLength(0);
  });

  it("espera otra imagen cuando el canvas no tiene contexto", async () => {
    HTMLCanvasElement.prototype.getContext.mockReturnValue(null);
    render(<QrScanner onDetected={vi.fn()} onCancel={vi.fn()} />);
    await waitFor(() => expect(frames).toHaveLength(1));
    await act(async () => frames.shift()());
    expect(frames).toHaveLength(1);
    expect(jsQR).not.toHaveBeenCalled();
  });
});

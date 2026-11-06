import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/core/api/client";
import {
  cancelarQr,
  consultarQr,
  emitirQr,
  listarDocentes,
  listarMisMarcaciones,
  marcarAsistencia,
  renovarQr,
  toDocenteOption,
} from "../teacher-check-in/services/asistencia-docente-service";

vi.mock("@/core/api/client", () => ({ api: vi.fn() }));

const page = (content) => ({ content, totalElements: content.length, totalPages: 1, number: 0, size: 20 });

describe("US-15 · contrato con /api/asistencias/docentes", () => {
  afterEach(() => vi.clearAllMocks());

  it("marcar envía únicamente el token, sin hora del dispositivo", async () => {
    await marcarAsistencia("abc");
    expect(api).toHaveBeenCalledWith("/api/asistencias/docentes/marcar", {
      method: "POST",
      body: JSON.stringify({ token: "abc" }),
    });
  });

  it("emite, renueva, consulta y cancela el QR", async () => {
    await emitirQr(7);
    expect(api).toHaveBeenLastCalledWith("/api/asistencias/docentes/qr", {
      method: "POST",
      body: JSON.stringify({ docenteId: 7, vigenciaSegundos: 60 }),
    });
    await renovarQr(3);
    expect(api).toHaveBeenLastCalledWith("/api/asistencias/docentes/qr/3/renovar?vigenciaSegundos=60", { method: "POST" });
    await consultarQr(3);
    expect(api).toHaveBeenLastCalledWith("/api/asistencias/docentes/qr/3");
    await cancelarQr(3);
    expect(api).toHaveBeenLastCalledWith("/api/asistencias/docentes/qr/3/cancelar", { method: "POST" });
  });

  it("lista las marcaciones propias", async () => {
    api.mockResolvedValue(page([{ id: 1 }]));
    expect(await listarMisMarcaciones()).toEqual([{ id: 1 }]);
  });

  it("arma el nombre del docente desde datos planos o anidados", async () => {
    api.mockResolvedValue(
      page([
        { id: 2, persona: { nombres: "Patricia", apellidoPaterno: "Morales", numeroDocumento: "40112233" } },
        { id: 1, nombreCompleto: "Ana Díaz" },
        { id: 3 },
        { id: "x" },
      ]),
    );
    expect(await listarDocentes()).toEqual([
      { id: 1, label: "Ana Díaz" },
      { id: 3, label: "Docente 3" },
      { id: 2, label: "Patricia Morales · 40112233" },
    ]);
    expect(toDocenteOption({ id: 0 })).toEqual([]);
  });
});

import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/core/api/client";
import {
  cancelarQr,
  clasesDelDia,
  consultarQr,
  emitirQr,
  listarDocentes,
  listarMarcacionesDocentes,
  listarMisMarcaciones,
  marcarAsistencia,
  renovarQr,
  resumenMarcaciones,
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
    await emitirQr();
    expect(api).toHaveBeenLastCalledWith("/api/asistencias/docentes/qr", {
      method: "POST",
      body: JSON.stringify({ vigenciaSegundos: 90 }),
    });
    await renovarQr(3);
    expect(api).toHaveBeenLastCalledWith("/api/asistencias/docentes/qr/3/renovar?vigenciaSegundos=90", { method: "POST" });
    await consultarQr(3);
    expect(api).toHaveBeenLastCalledWith("/api/asistencias/docentes/qr/3");
    await cancelarQr(3);
    expect(api).toHaveBeenLastCalledWith("/api/asistencias/docentes/qr/3/cancelar", { method: "POST" });
  });

  it("pide el resumen, las marcaciones de un día y las clases del docente", async () => {
    await resumenMarcaciones("2026-11-18");
    expect(api).toHaveBeenLastCalledWith("/api/asistencias/docentes/resumen?fecha=2026-11-18");
    api.mockResolvedValue(page([]));
    await listarMarcacionesDocentes({ fecha: "2026-11-18", verificacion: "OBSERVADA" });
    expect(api).toHaveBeenLastCalledWith(
      "/api/asistencias/docentes?page=0&size=50&sort=fechaHoraMarcada%2Cdesc&verificacion=OBSERVADA&fecha=2026-11-18",
    );
    await clasesDelDia(7, "2026-11-18");
    expect(api).toHaveBeenLastCalledWith("/api/asistencias/docentes/7/clases?fecha=2026-11-18");
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

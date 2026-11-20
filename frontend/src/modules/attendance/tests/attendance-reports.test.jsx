import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AttendanceReports } from "../reports/attendance-reports";
import { attendanceReport, reportOptions } from "../reports/report-service";
import { obtenerSesion, listarCorrecciones } from "../services/asistencia-service";

vi.mock("../services/asistencia-service", async importOriginal => ({
  ...await importOriginal(), obtenerSesion: vi.fn(), listarCorrecciones: vi.fn(),
}));

vi.mock("../reports/report-service", () => ({ attendanceReport: vi.fn(), reportOptions: vi.fn() }));
const result = () => ({
  total: 2, indicadores: [{ estado: "PRESENTE", cantidad: 1, porcentaje: 50 }, { estado: "TARDANZA", cantidad: 1, porcentaje: 50 }],
  registros: [{ id: 1, personaId: 10, persona: "Lucía Prado", fecha: "2026-11-19", hora: "09:01:00", curso: "Matemática", seccion: "1° A", estado: "PRESENTE", origen: "REGISTRO", sesionId: 4 }],
  pagina: 0, totalPaginas: 2, totalDetalle: 2,
});
describe("US-16 reportes institucionales", () => {
  beforeEach(() => {
    obtenerSesion.mockResolvedValue({ id: 4, alumnos: [{ alumnoId: 10, asistenciaId: 1 }] });
    listarCorrecciones.mockResolvedValue([]);
    reportOptions.mockResolvedValue({ periodos: [{ id: 5, nombre: "2026 · Primero" }], secciones: [{ id: 7, nombre: "2026 · 1° A" }] });
    attendanceReport.mockResolvedValue(result());
  });
  afterEach(() => { cleanup(); vi.clearAllMocks(); });
  it("muestra el denominador y permite abrir el detalle sin inventar hora de llegada", async () => {
    const user = userEvent.setup(); render(<AttendanceReports />);
    await user.click(await screen.findByRole("button", { name: "Ver detalle de Lucía Prado" }));
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText("Hora de registro")).toBeTruthy();
    expect(within(dialog).getByText("Matemática")).toBeTruthy();
    expect(within(dialog).getByText("09:01:00")).toBeTruthy();
    expect(screen.getByText("2 registros")).toBeTruthy();
  });
  it("envía los filtros al backend y los mantiene al paginar y abrir un indicador", async () => {
    const user = userEvent.setup(); render(<AttendanceReports />);
    await screen.findByText("Lucía Prado");
    await user.selectOptions(screen.getByLabelText("Período"), "5");
    await user.selectOptions(screen.getByLabelText("Sección"), "7");
    await user.type(screen.getByLabelText("Buscar persona"), "Prado");
    await user.click(screen.getByRole("button", { name: "Aplicar filtros" }));
    await screen.findByText("Lucía Prado");
    let params = new URLSearchParams(attendanceReport.mock.lastCall[0]);
    expect(params.get("periodoId")).toBe("5"); expect(params.get("seccionId")).toBe("7"); expect(params.get("busqueda")).toBe("Prado");
    await user.click(screen.getByRole("button", { name: /Tardanza.*Ver registros/ }));
    await screen.findByText("Lucía Prado");
    params = new URLSearchParams(attendanceReport.mock.lastCall[0]); expect(params.get("estado")).toBe("TARDANZA");
    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    await screen.findByText("Lucía Prado");
    params = new URLSearchParams(attendanceReport.mock.lastCall[0]); expect(params.get("pagina")).toBe("1"); expect(params.get("periodoId")).toBe("5");
  });
  it("cambia a docentes y limpia los filtros de estudiantes", async () => {
    const user = userEvent.setup(); render(<AttendanceReports />); await screen.findByText("Lucía Prado");
    await user.selectOptions(screen.getByLabelText("Tipo de reporte"), "DOCENTES");
    await screen.findByRole("columnheader", { name: "Hora de marcación" });
    expect(new URLSearchParams(attendanceReport.mock.lastCall[0]).get("tipo")).toBe("DOCENTES");
    expect(screen.queryByRole("columnheader", { name: "Curso y sección" })).toBeNull();
  });
  it("un informe vacío no muestra porcentajes históricos de cero", async () => {
    attendanceReport.mockResolvedValue({ total: 0, indicadores: [], registros: [], pagina: 0, totalPaginas: 0, totalDetalle: 0 });
    render(<AttendanceReports />); expect(await screen.findByText("Sin registros para los filtros seleccionados.")).toBeTruthy();
    expect(screen.queryByLabelText("Indicadores de asistencia")).toBeNull();
  });
  it("muestra los errores del servidor sin presentarlos como un informe vacío", async () => {
    attendanceReport.mockRejectedValue(new Error("No tienes permiso institucional")); render(<AttendanceReports />);
    expect(await screen.findByText("No tienes permiso institucional")).toBeTruthy();
    expect(screen.queryByText("Sin registros para los filtros seleccionados.")).toBeNull();
  });
});

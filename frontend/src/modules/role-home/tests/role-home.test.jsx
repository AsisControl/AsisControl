import { beforeEach, expect, test, vi } from "vitest";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StudentHome, GuardianHome } from "@/modules/role-home";
import { loadStudentHome, loadHomeStudents, loadGuardianHome } from "../data";
import { obtenerPanelIndicadores } from "@/core/api/dashboard";

vi.mock("../data", () => ({
  loadStudentHome: vi.fn(),
  loadHomeStudents: vi.fn(),
  loadGuardianHome: vi.fn(),
}));
vi.mock("@/core/api/dashboard", () => ({ obtenerPanelIndicadores: vi.fn() }));

beforeEach(() => vi.clearAllMocks());

test("el inicio del alumno muestra sus datos y enlaces a las pantallas reales", async () => {
  let resolveHome;
  loadStudentHome.mockReturnValue(new Promise((resolve) => { resolveHome = resolve; }));
  render(<StudentHome fullName="Luis García" />);
  expect(screen.getByRole("status").getAttribute("aria-busy")).toBe("true");
  expect(screen.getByRole("status").textContent).toContain("Cargando tu panel");
  await act(async () => resolveHome({
    tasks: { content: [{ curso: "Matemática", tarea: { id: 4, nombre: "Ejercicios", fechaLimite: "2026-11-25T18:00:00" } }], totalElements: 1 },
    schedule: [], attendance: [],
    reports: [{ idAlumno: 5, idAsignacionCurso: 9, idPeriodoAcademico: 2, curso: "Comunicación", periodo: "Bimestre II", anio: 2026 }],
  }));
  expect(await screen.findByRole("heading", { name: "Bienvenido, Luis García" })).toBeTruthy();
  expect(await screen.findByText("Ejercicios")).toBeTruthy();
  expect(screen.getByText("No hay clases programadas para hoy.")).toBeTruthy();
  expect(screen.getByText("Comunicación")).toBeTruthy();
  expect(screen.getByRole("link", { name: /Ver detalle y entregar/ }).getAttribute("href")).toBe("/tareas");
  expect(screen.getByRole("link", { name: /Ver progreso/ }).getAttribute("href")).toBe("/progreso");
});

test("el apoderado cambia de estudiante y consulta solo el panel seleccionado", async () => {
  obtenerPanelIndicadores.mockResolvedValue({ indicadores: [
    { codigo: "NOTIFICACIONES_NO_LEIDAS", etiqueta: "Notificaciones no leídas", valor: 2, tono: "neutral" },
  ] });
  loadHomeStudents.mockResolvedValue([
    { id: 5, nombreCompleto: "Luis García" },
    { id: 6, nombreCompleto: "María García" },
  ]);
  let resolveSecond;
  loadGuardianHome.mockImplementation((id) => id === 6 ? new Promise((resolve) => { resolveSecond = resolve; }) : Promise.resolve({
    obligations: [{ id, concepto: id === 5 ? "Pensión Luis" : "Pensión María", fechaVencimiento: "2026-11-25", saldo: 850, estadoAdministrativo: "ACTIVO", estadoFinanciero: "PENDIENTE" }],
    requests: [], attendance: [], reports: [],
  }));
  render(<GuardianHome user={{ id: 9, fullName: "Ana García", role: "APODERADO", permissions: [] }} />);
  expect(screen.getByRole("status").getAttribute("aria-busy")).toBe("true");
  const select = await screen.findByRole("combobox", { name: "Estudiante" });
  expect(await screen.findByText("Pensión Luis")).toBeTruthy();
  await userEvent.selectOptions(select, "6");
  expect(screen.getByRole("status").getAttribute("aria-busy")).toBe("true");
  expect(screen.queryByText("Pensión Luis")).toBeNull();
  await act(async () => resolveSecond({
    obligations: [{ id: 6, concepto: "Pensión María", fechaVencimiento: "2026-11-25", saldo: 850, estadoAdministrativo: "ACTIVO", estadoFinanciero: "PENDIENTE" }],
    requests: [], attendance: [], reports: [],
  }));
  expect(await screen.findByText("Pensión María")).toBeTruthy();
  const summary = screen.getByRole("region", { name: "Resumen familiar" });
  expect(within(summary).getByText("Notificaciones no leídas")).toBeTruthy();
  expect(obtenerPanelIndicadores).toHaveBeenCalledTimes(1);
  expect(screen.queryByText("Pensión Luis")).toBeNull();
  expect(loadGuardianHome.mock.calls.map(([id]) => id)).toEqual([5, 6]);
  const card = screen.getByRole("heading", { name: "Próximos compromisos" }).closest("section");
  expect(within(card).getByText("María García")).toBeTruthy();
});

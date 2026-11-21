import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StudentHome, GuardianHome } from "@/modules/role-home";
import { loadStudentHome, loadHomeStudents, loadGuardianHome } from "../data";

vi.mock("../data", () => ({
  loadStudentHome: vi.fn(),
  loadHomeStudents: vi.fn(),
  loadGuardianHome: vi.fn(),
}));

beforeEach(() => vi.clearAllMocks());

test("el inicio del alumno muestra sus datos y enlaces a las pantallas reales", async () => {
  loadStudentHome.mockResolvedValue({
    tasks: { content: [{ curso: "Matemática", tarea: { id: 4, nombre: "Ejercicios", fechaLimite: "2026-11-25T18:00:00" } }], totalElements: 1 },
    schedule: [], attendance: [],
    reports: [{ idAlumno: 5, idAsignacionCurso: 9, idPeriodoAcademico: 2, curso: "Comunicación", periodo: "Bimestre II", anio: 2026 }],
  });
  render(<StudentHome fullName="Luis García" />);
  expect(await screen.findByRole("heading", { name: "Bienvenido, Luis García" })).toBeTruthy();
  expect(await screen.findByText("Ejercicios")).toBeTruthy();
  expect(screen.getByText("No hay clases programadas para hoy.")).toBeTruthy();
  expect(screen.getByText("Comunicación")).toBeTruthy();
  expect(screen.getByRole("link", { name: /Ver detalle y entregar/ }).getAttribute("href")).toBe("/tareas");
  expect(screen.getByRole("link", { name: /Ver progreso/ }).getAttribute("href")).toBe("/progreso");
});

test("el apoderado cambia de estudiante y consulta solo el panel seleccionado", async () => {
  loadHomeStudents.mockResolvedValue([
    { id: 5, nombreCompleto: "Luis García" },
    { id: 6, nombreCompleto: "María García" },
  ]);
  loadGuardianHome.mockImplementation(async (id) => ({
    obligations: [{ id, concepto: id === 5 ? "Pensión Luis" : "Pensión María", fechaVencimiento: "2026-11-25", saldo: 850, estadoAdministrativo: "ACTIVO", estadoFinanciero: "PENDIENTE" }],
    requests: [], attendance: [], reports: [],
  }));
  render(<GuardianHome />);
  const select = await screen.findByRole("combobox", { name: "Estudiante" });
  expect(await screen.findByText("Pensión Luis")).toBeTruthy();
  await userEvent.selectOptions(select, "6");
  expect(await screen.findByText("Pensión María")).toBeTruthy();
  expect(screen.queryByText("Pensión Luis")).toBeNull();
  expect(loadGuardianHome.mock.calls.map(([id]) => id)).toEqual([5, 6]);
  const card = screen.getByRole("heading", { name: "Próximos compromisos" }).closest("section");
  expect(within(card).getByText("María García")).toBeTruthy();
});

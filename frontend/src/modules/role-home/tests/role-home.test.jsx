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

// cobertura: clases de hoy, asistencia del mes, errores y estados vacíos
const asistencia = (id, estado, fecha = "2026-11-18", extra = {}) => ({ id, estado, fechaRegistro: fecha, curso: "Matemática", ...extra });

test("el inicio del alumno resalta la clase en curso, resume la asistencia del mes y se actualiza", async () => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-19T15:00:00Z")); // jueves 10:00 en Lima
  loadStudentHome.mockResolvedValue({
    tasks: { content: [], totalElements: 0 },
    schedule: [
      { id: 2, curso: "Historia", docente: "Ana Ríos", aula: "Aula 2", diaSemana: "JUEVES", horaInicio: "11:00:00", horaFin: "12:00:00" },
      { id: 1, curso: "Matemática", docente: "Luis Vega", aula: "Aula 1", diaSemana: "JUEVES", horaInicio: "09:30:00", horaFin: "10:30:00" },
      { id: 3, curso: "Arte", docente: "Eva Paz", aula: "Taller", diaSemana: "VIERNES", horaInicio: "08:00:00", horaFin: "09:00:00" },
    ],
    attendance: [asistencia(1, "PRESENTE"), asistencia(2, "TARDANZA"), asistencia(3, "INASISTENCIA"), asistencia(4, "PRESENTE", "2026-10-02")],
    reports: [],
  });
  render(<StudentHome fullName="Luis García" />);
  const clases = (await screen.findByRole("heading", { name: "Clases de hoy" })).closest("section");
  const filas = within(clases).getAllByText(/Aula/).map((nodo) => nodo.textContent);
  expect(filas).toEqual(["Luis Vega · Aula 1", "Ana Ríos · Aula 2"]);
  expect(within(clases).getByText("En curso")).toBeTruthy();
  expect(screen.getByText("Estás al día con tus tareas.")).toBeTruthy();
  expect(screen.getByText("Aún no hay informes de progreso publicados.")).toBeTruthy();
  const mes = screen.getByRole("heading", { name: "Asistencia — mes actual" }).closest("section");
  expect(within(mes).getByText("Presentes").textContent).toBe("1Presentes");
  expect(within(mes).getByText("Inasistencias").textContent).toBe("1Inasistencias");
  expect(screen.getByText(/Última actualización/)).toBeTruthy();
  await userEvent.click(screen.getByRole("button", { name: "Actualizar" }));
  expect(loadStudentHome).toHaveBeenCalledTimes(2);
  vi.useRealTimers();
});

test("el inicio del alumno informa el error y permite reintentar", async () => {
  loadStudentHome.mockRejectedValueOnce(new Error("Servidor no disponible"))
    .mockResolvedValue({ tasks: { content: [], totalElements: 0 }, schedule: [], attendance: [], reports: [] });
  render(<StudentHome fullName="Luis García" />);
  expect(await screen.findByText(/No se pudo cargar el panel/)).toBeTruthy();
  await userEvent.click(screen.getByRole("button", { name: "Actualizar" }));
  expect(await screen.findByText("Aún no hay asistencias registradas este mes.")).toBeTruthy();
});

test("el apoderado ve matrícula, asistencia, inasistencias e informe del estudiante", async () => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-19T15:00:00Z"));
  obtenerPanelIndicadores.mockRejectedValue(new Error("caído"));
  loadHomeStudents.mockResolvedValue([{ id: 5, nombreCompleto: "Luis García" }]);
  loadGuardianHome.mockResolvedValue({
    obligations: [
      { id: 1, concepto: "Pensión pagada", fechaVencimiento: "2026-11-01", saldo: 0, estadoAdministrativo: "ACTIVO", estadoFinanciero: "PAGADO" },
      { id: 2, concepto: "Pensión anulada", fechaVencimiento: "2026-11-02", saldo: 300, estadoAdministrativo: "ANULADO", estadoFinanciero: "PENDIENTE" },
    ],
    requests: [{ id: 1, anioAcademico: 2026, estado: "EN_REVISION" }, { id: 2, anioAcademico: 2027, estado: "MATRICULA_FINALIZADA" }],
    attendance: [
      asistencia(1, "INASISTENCIA", "2026-11-17", { estadoJustificacion: "APROBADA", curso: "Historia" }),
      asistencia(2, "INASISTENCIA", "2026-11-18"),
      asistencia(3, "PRESENTE", "2026-11-19"),
    ],
    reports: [{ idAlumno: 5, idAsignacionCurso: 9, idPeriodoAcademico: 2, curso: "Comunicación", periodo: "Bimestre II", anio: 2026 }],
  });
  render(<GuardianHome user={{ id: 9, fullName: "Ana García", role: "APODERADO", permissions: [] }} />);
  expect(await screen.findByText("matricula finalizada")).toBeTruthy();
  expect(screen.getByText("No se pudo cargar el resumen familiar.")).toBeTruthy();
  expect(screen.getByText("Al día")).toBeTruthy();
  expect(screen.getByText("No hay compromisos pendientes.")).toBeTruthy();
  expect(screen.getByText("Disponible")).toBeTruthy();
  const faltas = screen.getByRole("heading", { name: "Últimas inasistencias" }).closest("section");
  expect(within(faltas).getByText("Justificada")).toBeTruthy();
  expect(within(faltas).getByText("Inasistencia")).toBeTruthy();
  expect(screen.getByRole("link", { name: "Ver informe completo" }).getAttribute("href")).toBe("/progreso");
  await userEvent.click(screen.getByRole("button", { name: "Actualizar" }));
  expect(loadHomeStudents).toHaveBeenCalledTimes(2);
  vi.useRealTimers();
});

test("el apoderado sin estudiantes asociados ve el aviso y un error de carga se informa", async () => {
  obtenerPanelIndicadores.mockResolvedValue({ indicadores: [] });
  loadHomeStudents.mockResolvedValueOnce([]);
  const { unmount } = render(<GuardianHome user={{ id: 9, fullName: "Ana García", role: "APODERADO", permissions: [] }} />);
  expect(await screen.findByText(/No tienes estudiantes asociados/)).toBeTruthy();
  expect(screen.queryByRole("region", { name: "Resumen familiar" })).toBeNull();
  unmount();
  loadHomeStudents.mockRejectedValueOnce(new Error("Sin conexión"));
  render(<GuardianHome user={{ id: 9, fullName: "Ana García", role: "APODERADO", permissions: [] }} />);
  expect(await screen.findByText(/No se pudo cargar el panel. Sin conexión/)).toBeTruthy();
});

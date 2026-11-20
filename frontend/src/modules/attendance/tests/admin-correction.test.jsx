import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, signIn } from "../../../../tests/support/backend.js";
import { AttendanceReports } from "../reports/attendance-reports";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const session = {
  id: 7, asignacionCursoId: 42, curso: "Matemática", seccion: "1° A", fecha: "2026-11-19",
  horaInicio: "09:00:00", horaFin: "10:00:00", estado: "TOMADA", puedeTomar: false, puedeVer: true,
  puedeEditar: true, pendientes: 0, registradoPor: "Docente de la clase", registradoEn: "2026-11-19T10:00:00", version: 3,
  alumnos: [
    { alumnoId: 1, nombreCompleto: "Lucía Prado", codigoAlumno: "E1", estado: "INASISTENCIA", asistenciaId: 1, justificacionId: null },
    { alumnoId: 2, nombreCompleto: "Mateo Silva", codigoAlumno: "E2", estado: "TARDANZA", asistenciaId: 2, justificacionId: null },
  ],
};
function backend({ conflict = false, attendanceId = 1, plazoVencido = false } = {}) {
  let saved = false;
  const calls = fakeBackend({
    "GET /api/reportes/asistencia/filtros": { periodos: [], secciones: [] },
    "GET /api/reportes/asistencia": () => ({
      total: 1, indicadores: [{ estado: saved ? "PRESENTE" : "INASISTENCIA", cantidad: 1, porcentaje: 100 }],
      pagina: 0, totalPaginas: 1, totalDetalle: 1,
      registros: [{ id: attendanceId, personaId: attendanceId, persona: attendanceId === 2 ? "Mateo Silva" : "Lucía Prado", fecha: "2026-11-19", hora: "09:00:00", curso: "Matemática", seccion: "1° A", estado: saved ? "PRESENTE" : "INASISTENCIA", origen: "REGISTRO", sesionId: 7 }],
    }),
    "GET /api/asistencias/sesiones/7": plazoVencido
      ? { ...session, puedeEditar: false, limiteEdicion: "2026-11-20T00:00:00" } : session,
    "GET /api/asistencias/sesiones/7/correcciones": [],
    "PUT /api/asistencias/sesiones/7": () => {
      if (conflict) return { status: 409, body: { message: "La asistencia cambió; vuelva a cargarla" }, isReply: true };
      saved = true; return { ...session, version: 4 };
    },
  });
  return calls;
}
beforeEach(() => signIn({ permissions: ["ASISTENCIA_LEER", "ASISTENCIA_ESCRIBIR"] }));

test("el administrador corrige desde el reporte con motivo y actualiza los indicadores", async () => {
  const user = userEvent.setup(), calls = backend();
  render(<AttendanceReports canCorrect />);
  await user.click(await screen.findByRole("button", { name: "Ver detalle de Lucía Prado" }));
  await user.click(screen.getByRole("button", { name: "Corregir asistencia", exact: true }));
  const marks = await screen.findByRole("group", { name: "Asistencia de Lucía Prado" });
  expect(screen.queryByText("Mateo Silva")).toBeNull();
  expect(screen.getAllByRole("group", { name: /^Asistencia de/ })).toHaveLength(1);
  expect(screen.queryByRole("button", { name: "Aplicar filtros" })).toBeNull();
  expect(screen.getByText(/Registrada originalmente por Docente de la clase/)).toBeTruthy();
  await user.click(within(marks).getByRole("button", { name: "Presente" }));
  expect(screen.queryByText("Sin justificación")).toBeNull();
  await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
  const dialog = screen.getByRole("dialog", { name: "Confirmar corrección" });
  const confirm = within(dialog).getByRole("button", { name: /Confirmar/ });
  expect(confirm.disabled).toBe(true);
  await user.type(within(dialog).getByRole("textbox"), "Se verificó la presencia con el docente");
  await user.click(confirm);
  expect(await screen.findByText("Corrección guardada. El reporte se actualizó.")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Aplicar filtros" })).toBeTruthy();
  expect(calls.find(c => c.method === "PUT").body).toEqual({ marcas: [{ alumnoId: 1, estado: "PRESENTE" }], motivo: "Se verificó la presencia con el docente", version: 3 });
  expect(calls.filter(c => c.path === "/api/reportes/asistencia").length).toBeGreaterThan(1);
});

test("sin permiso de escritura solo consulta y no ofrece corregir", async () => {
  const user = userEvent.setup(); backend(); render(<AttendanceReports />);
  await user.click(await screen.findByRole("button", { name: "Ver detalle de Lucía Prado" }));
  expect(screen.queryByRole("button", { name: "Corregir asistencia", exact: true })).toBeNull();
});

test("un registro que no corresponde a la sesión no abre la corrección de otro alumno", async () => {
  const user = userEvent.setup(), calls = backend({ attendanceId: 999 });
  render(<AttendanceReports canCorrect />);
  await user.click(await screen.findByRole("button", { name: "Ver detalle de Lucía Prado" }));
  await user.click(screen.getByRole("button", { name: "Corregir asistencia", exact: true }));
  expect(await screen.findByText(/El registro seleccionado ya no está disponible/)).toBeTruthy();
  expect(screen.queryByRole("group", { name: /^Asistencia de/ })).toBeNull();
  expect(calls.some(call => call.method === "PUT")).toBe(false);
});

test("seleccionar al segundo alumno no muestra ni permite editar al primero", async () => {
  const user = userEvent.setup(); backend({ attendanceId: 2 });
  render(<AttendanceReports canCorrect />);
  await user.click(await screen.findByRole("button", { name: "Ver detalle de Mateo Silva" }));
  await user.click(screen.getByRole("button", { name: "Corregir asistencia", exact: true }));
  expect(await screen.findByRole("group", { name: "Asistencia de Mateo Silva" })).toBeTruthy();
  expect(screen.queryByText("Lucía Prado")).toBeNull();
  expect(screen.queryByPlaceholderText("Buscar por nombre o apellido")).toBeNull();
});

test("con el plazo vencido explica por qué no se corrige y permite volver al reporte", async () => {
  const user = userEvent.setup(); backend({ plazoVencido: true }); render(<AttendanceReports canCorrect />);
  await user.click(await screen.findByRole("button", { name: "Ver detalle de Lucía Prado" }));
  await user.click(screen.getByRole("button", { name: "Corregir asistencia", exact: true }));
  const aviso = await screen.findByRole("status");
  expect(within(aviso).getByRole("heading", { name: "Corrección no disponible" })).toBeTruthy();
  expect(aviso.textContent).toMatch(/plazo para corregir esta asistencia ya venció/);
  expect(aviso.textContent).toMatch(/hasta el final del día/);
  await user.click(within(aviso).getByRole("button", { name: "Volver al reporte" }));
  expect(await screen.findByRole("button", { name: "Aplicar filtros" })).toBeTruthy();
});

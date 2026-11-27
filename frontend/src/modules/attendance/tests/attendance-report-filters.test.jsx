// cobertura: filtros, validación de fechas, errores de opciones y detalle por estado
import { beforeEach, expect, test, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, reply, signIn } from "../../../../tests/support/backend.js";
import { AttendanceReports } from "../reports/attendance-reports";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

beforeEach(() => signIn({ permissions: ["ASISTENCIA_LEER"] }));
test("valida el rango de fechas, aplica filtros y permite volver a todos los estados", async () => {
  const user = userEvent.setup();
  const calls = fakeBackend({
    "GET /api/reportes/asistencia/filtros": { periodos: [{ id: 1, nombre: "Bimestre 1" }], secciones: [{ id: 2, nombre: "1° A" }] },
    "GET /api/reportes/asistencia": {
      total: 2, indicadores: [{ estado: "TARDANZA", cantidad: 1, porcentaje: 50 }, { estado: "SIN_MARCACION", cantidad: 1, porcentaje: 50 }],
      pagina: 0, totalPaginas: 1, totalDetalle: 2,
      registros: [{ id: 5, personaId: 5, persona: "Ana Rojas", fecha: "2026-11-19", hora: null, curso: null, seccion: null,
        estado: "SIN_MARCACION", origen: "JORNADA", sesionId: null }],
    },
  });
  render(<AttendanceReports />);
  await user.selectOptions(screen.getByLabelText("Tipo de reporte"), "DOCENTES");
  await screen.findByRole("option", { name: "Bimestre 1" });
  fireEvent.change(screen.getByLabelText("Desde"), { target: { value: "2026-11-20" } });
  fireEvent.change(screen.getByLabelText(/^Hasta/), { target: { value: "2026-11-01" } });
  await user.click(screen.getByRole("button", { name: "Aplicar filtros" }));
  expect(screen.getByRole("alert").textContent).toBe("La fecha final debe ser igual o posterior a la inicial.");
  fireEvent.change(screen.getByLabelText(/^Hasta/), { target: { value: "2026-11-30" } });
  await user.selectOptions(screen.getByLabelText("Sección"), "2");
  await user.type(screen.getByLabelText("Buscar persona"), " Ana ");
  await user.click(screen.getByRole("button", { name: "Aplicar filtros" }));
  await waitFor(() => expect(calls.filter(c => c.path === "/api/reportes/asistencia").at(-1).query).toMatchObject({ tipo: "DOCENTES", seccionId: "2", busqueda: "Ana", desde: "2026-11-20", hasta: "2026-11-30" }));
  await user.click(await screen.findByRole("button", { name: /Tardanza/ }));
  await user.click(await screen.findByRole("button", { name: "Ver todos los estados" }));
  await user.click(await screen.findByRole("button", { name: "Ver detalle de Ana Rojas" }));
  expect(screen.getByText("Jornada programada que cerró sin una marcación válida")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Corregir marcación" })).toBeNull();
});

test("informa si fallan las opciones del reporte o el propio reporte", async () => {
  fakeBackend({
    "GET /api/reportes/asistencia/filtros": reply(500, { message: "Filtros no disponibles" }),
    "GET /api/reportes/asistencia": reply(500, { message: "Reporte no disponible" }),
  });
  render(<AttendanceReports />);
  expect(await screen.findByText("Filtros no disponibles")).toBeTruthy();
  expect(await screen.findByText("Reporte no disponible")).toBeTruthy();
});

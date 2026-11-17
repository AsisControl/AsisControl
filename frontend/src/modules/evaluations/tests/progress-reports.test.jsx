import { test, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ProgressReports, PublishPeriod } from "../progress-reports";
import { fakeBackend } from "../../../../tests/support/backend.js";
import { progressReport } from "./report-fixture";

test("publicar requiere confirmar y envia curso y periodo", async () => {
  const calls = fakeBackend({
    "GET /api/evaluaciones/periodos-informe": [{ id: 4, nombre: "Bimestre 1" }],
    "POST /api/evaluaciones/publicar-periodo": { evaluacionesPublicadas: 2 },
  });
  render(<PublishPeriod canWrite assignments={[{id:3,curso:"Matematica",seccion:"A",anioAcademico:2026}]} />);
  const user = userEvent.setup();
  await user.selectOptions(screen.getByLabelText("Curso y sección"), "3");
  await screen.findByRole("option", {name:"Bimestre 1"});
  await user.selectOptions(screen.getByLabelText("Período académico"), "4");
  await user.click(screen.getByRole("button", {name:"Publicar notas"}));
  expect(calls.some(c => c.method === "POST")).toBe(false);
  await user.click(screen.getByRole("button", {name:"Confirmar publicación"}));
  await screen.findByText(/Se publicaron 2 evaluaciones/);
  expect(calls.find(c => c.method === "POST").body).toEqual({idAsignacionCurso:3,idPeriodoAcademico:4});
});

test("progreso muestra notas y descarga el informe elegido", async () => {
  const calls = fakeBackend({
    "GET /api/calificaciones/informes-disponibles": [{idAlumno:8,alumno:"Ana",idAsignacionCurso:3,curso:"Matematica",anio:2026,idPeriodoAcademico:4,periodo:"Bimestre 1"}],
    "GET /api/calificaciones/avance": {...progressReport,alumno:"Ana",curso:"Matematica",filas:[{competencia:"Cantidad",evaluacion:"Practica",fecha:"01/03/2026",resultado:"A",ponderacion:"30 %",observacion:"Buen avance"},{competencia:"Forma",evaluacion:"Practica",fecha:"01/03/2026",resultado:"Sin registrar",ponderacion:"30 %",observacion:"Sin observaciones"}]},
    "GET /api/calificaciones/reporte-avance": {},
  });
  URL.createObjectURL = vi.fn(() => "blob:pdf"); URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype,"click").mockImplementation(() => {});
  render(<ProgressReports />);
  await screen.findByText(/Ana · Matematica/);
  const user = userEvent.setup();
  await user.selectOptions(screen.getByRole("combobox"), "0");
  await user.click(screen.getByText("Ver notas"));
  await screen.findByText("Buen avance");
  expect(screen.getByText("Sin registrar")).toBeTruthy();
  await user.click(screen.getByText("Descargar PDF"));
  expect(calls.find(c => c.path.endsWith("reporte-avance")).query).toEqual({idAlumno:"8",idAsignacionCurso:"3",idPeriodoAcademico:"4"});
});

test("sin notas publicadas no ofrece alumnos", async () => {
  fakeBackend({ "GET /api/calificaciones/informes-disponibles": [] });
  render(<ProgressReports />);
  await screen.findByText(/Todavía no hay notas publicadas/);
  expect(screen.queryByRole("combobox")).toBeNull();
});

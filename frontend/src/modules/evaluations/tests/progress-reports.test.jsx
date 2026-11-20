import { test, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ProgressReports, PublishPeriod } from "../progress-reports";
import { fakeBackend } from "../../../../tests/support/backend.js";

test("publicar requiere confirmar y envia curso y periodo", async () => {
  const calls = fakeBackend({
    "GET /api/evaluaciones/periodos-informe": [{ id: 4, nombre: "Bimestre 1" }],
    "POST /api/calificaciones/informes/publicar": { informesProcesados: 2 },
  });
  render(<PublishPeriod canWrite assignments={[{id:3,curso:"Matematica",seccion:"A",anioAcademico:2026}]} />);
  const user = userEvent.setup();
  await user.selectOptions(screen.getByLabelText("Curso y sección"), "3");
  await screen.findByRole("option", {name:"Bimestre 1"});
  await user.selectOptions(screen.getByLabelText("Período académico"), "4");
  await user.click(screen.getByRole("button", {name:"Publicar libretas"}));
  expect(calls.some(c => c.method === "POST")).toBe(false);
  await user.click(screen.getByRole("button", {name:"Confirmar publicación"}));
  await screen.findByText(/Se publicaron 2 libretas/);
  expect(calls.find(c => c.method === "POST").body).toEqual({idAsignacionCurso:3,idPeriodoAcademico:4});
});

test("progreso muestra notas y descarga el informe elegido", async () => {
  const calls = fakeBackend({
    "GET /api/calificaciones/informes-disponibles": [{idAlumno:8,alumno:"Ana",idAsignacionCurso:3,curso:"Matematica",anio:2026,idPeriodoAcademico:4,periodo:"Bimestre 1"}],
    "GET /api/calificaciones/avance": {filas:[{competencia:"Cantidad",evaluacion:"Practica",fecha:"01/03/2026",resultado:"A",ponderacion:"30 %",observacion:"Buen avance"},{competencia:"Forma",evaluacion:"Practica",fecha:"01/03/2026",resultado:"Sin registrar",ponderacion:"30 %",observacion:"Sin observaciones"}]},
    "GET /api/calificaciones/reporte-avance": {},
  });
  URL.createObjectURL = vi.fn(() => "blob:pdf"); URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype,"click").mockImplementation(() => {});
  render(<ProgressReports />);
  await screen.findByRole("option", { name: /Bimestre 1/ });
  const user = userEvent.setup();
  await user.selectOptions(screen.getByLabelText("Período académico"), "2026-4");
  await user.selectOptions(screen.getByLabelText("Curso"), "3");
  await user.click(screen.getByText("Ver notas"));
  await screen.findByText("Buen avance");
  expect(screen.getByText("Sin registrar")).toBeTruthy();
  await user.click(screen.getByText("Descargar PDF"));
  expect(calls.find(c => c.path.endsWith("reporte-avance")).query).toEqual({idAlumno:"8",idAsignacionCurso:"3",idPeriodoAcademico:"4"});
});

test("sin notas publicadas no ofrece períodos", async () => {
  fakeBackend({ "GET /api/calificaciones/informes-disponibles": [] });
  render(<ProgressReports />);
  await screen.findByText(/Todavía no hay libretas publicadas/);
  expect(screen.queryByRole("combobox")).toBeNull();
});

test("el estudiante cambia de período y consulta los niveles y comentarios de la libreta correspondiente", async () => {
  const primerInforme = {
    idAlumno: 8, alumno: "Ana", idAsignacionCurso: 3, curso: "Matemática",
    anio: 2026, idPeriodoAcademico: 4, periodo: "Bimestre 1",
  };
  const calls = fakeBackend({
    "GET /api/calificaciones/informes-disponibles": [
      primerInforme, { ...primerInforme, idPeriodoAcademico: 5, periodo: "Bimestre 2" },
    ],
    "GET /api/calificaciones/avance": ({ query }) => ({
      filas: [{
        competencia: "Resuelve problemas de cantidad",
        resultado: query.idPeriodoAcademico === "4" ? "B" : "AD",
        observacion: query.idPeriodoAcademico === "4" ? "Practicar fracciones" : "Explica su procedimiento",
      }],
    }),
  });
  render(<ProgressReports />);
  const user = userEvent.setup();
  await screen.findByRole("option", { name: "Bimestre 1 · 2026" });
  expect(screen.queryByLabelText("Estudiante")).toBeNull();
  await user.selectOptions(screen.getByLabelText("Período académico"), "2026-4");
  await user.selectOptions(screen.getByLabelText("Curso"), "3");
  await user.click(screen.getByRole("button", { name: "Ver notas" }));
  await screen.findByRole("cell", { name: "Practicar fracciones" });
  expect(screen.getByRole("cell", { name: "B", exact: true })).toBeTruthy();

  await user.selectOptions(screen.getByLabelText("Período académico"), "2026-5");
  expect(screen.queryByText("Practicar fracciones")).toBeNull();
  await user.selectOptions(screen.getByLabelText("Curso"), "3");
  await user.click(screen.getByRole("button", { name: "Ver notas" }));
  await screen.findByRole("cell", { name: "Explica su procedimiento" });
  expect(screen.getByRole("cell", { name: "AD", exact: true })).toBeTruthy();
  expect(screen.getByRole("cell", { name: "Resuelve problemas de cantidad" })).toBeTruthy();
  expect(calls.filter(call => call.path.endsWith("/avance")).map(call => call.query)).toEqual([
    { idAlumno: "8", idAsignacionCurso: "3", idPeriodoAcademico: "4" },
    { idAlumno: "8", idAsignacionCurso: "3", idPeriodoAcademico: "5" },
  ]);
});

test("el apoderado cambia de hijo y solo consulta los cursos publicados del seleccionado", async () => {
  const ana = {idAlumno:8,alumno:"Ana",idAsignacionCurso:3,curso:"Matemática",anio:2026,idPeriodoAcademico:4,periodo:"Bimestre 1"};
  const calls = fakeBackend({
    "GET /api/familia/estudiantes": [{id:8,nombreCompleto:"Ana"},{id:9,nombreCompleto:"Luis"},{id:10,nombreCompleto:"Rosa"}],
    "GET /api/calificaciones/informes-disponibles": [
      ana,
      {...ana,idAsignacionCurso:5,curso:"Ciencia"},
      {...ana,idAlumno:9,alumno:"Luis",idAsignacionCurso:6,curso:"Historia"},
    ],
    "GET /api/calificaciones/avance": {filas:[{resultado:"AD",observacion:"Buen avance"}]},
  });
  render(<ProgressReports guardian />);
  const user = userEvent.setup();
  await screen.findByRole("option", { name: "Ana" });
  await user.selectOptions(screen.getByLabelText("Estudiante"), "8");
  await user.selectOptions(screen.getByLabelText("Período académico"), "2026-4");
  expect(screen.getByRole("option", { name: "Ciencia" })).toBeTruthy();
  expect(screen.queryByRole("option", { name: "Historia" })).toBeNull();
  await user.selectOptions(screen.getByLabelText("Curso"), "3");
  await user.click(screen.getByRole("button", { name: "Ver notas" }));
  await screen.findByText("Buen avance");
  expect(calls.find(c => c.path.endsWith("/avance")).query)
    .toEqual({idAlumno:"8",idAsignacionCurso:"3",idPeriodoAcademico:"4"});

  await user.selectOptions(screen.getByLabelText("Estudiante"), "9");
  expect(screen.queryByText("Buen avance")).toBeNull();
  await user.selectOptions(screen.getByLabelText("Período académico"), "2026-4");
  expect(screen.getByRole("option", { name: "Historia" })).toBeTruthy();
  expect(screen.queryByRole("option", { name: "Matemática" })).toBeNull();

  await user.selectOptions(screen.getByLabelText("Estudiante"), "10");
  expect(screen.getByText(/Todavía no hay libretas publicadas/)).toBeTruthy();
});


test("genera y revisa un borrador antes de publicar", async () => {
  const calls = fakeBackend({
    "GET /api/evaluaciones/periodos-informe": [{ id: 4, nombre: "Bimestre 1" }],
    "POST /api/calificaciones/informes/generar": { informesProcesados: 1, informes: [{idAlumno:8,alumno:"Ana",idAsignacionCurso:3,curso:"Matematica",anio:2026,idPeriodoAcademico:4,periodo:"Bimestre 1"}] },
    "GET /api/calificaciones/informes/borrador": {periodo:"Bimestre 1 | Version 2 | BORRADOR",filas:[{competencia:"Cantidad",evaluacion:"Practica",fecha:"01/03/2026",resultado:"Sin registrar",ponderacion:"30 %",observacion:"Sin observaciones"}]},
  });
  render(<PublishPeriod canWrite assignments={[{id:3,curso:"Matematica",seccion:"A",anioAcademico:2026}]} />);
  const user = userEvent.setup();
  await user.selectOptions(screen.getByLabelText("Curso y sección"), "3");
  await screen.findByRole("option", {name:"Bimestre 1"});
  await user.selectOptions(screen.getByLabelText("Período académico"), "4");
  await user.click(screen.getByRole("button", {name:"Generar borradores"}));
  await screen.findByText(/Se generaron 1 borradores/);
  await user.selectOptions(screen.getByLabelText("Alumno para revisar"), "8");
  await user.click(screen.getByRole("button", {name:"Revisar borrador"}));
  await screen.findByText("Sin registrar");
  expect(screen.getByText(/Version 2/)).toBeTruthy();
  expect(calls.filter(c => c.method === "POST")).toHaveLength(1);
  expect(calls.find(c => c.path.endsWith("borrador")).query).toEqual({idAlumno:"8",idAsignacionCurso:"3",idPeriodoAcademico:"4"});
});

test("sin permiso de escritura no ofrece generar ni publicar", () => {
  render(<PublishPeriod canWrite={false} assignments={[]} />);
  expect(screen.queryByRole("button", {name:"Generar borradores"})).toBeNull();
  expect(screen.queryByRole("button", {name:"Publicar libretas"})).toBeNull();
});

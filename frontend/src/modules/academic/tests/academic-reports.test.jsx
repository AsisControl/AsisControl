import { expect, test } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page } from "../../../../tests/support/backend";
import { progressReport } from "../../evaluations/tests/report-fixture";
import { AcademicCoursePanel } from "../academic-course-panel";

test("Evaluaciones consulta el curso y abre su planilla en modo lectura", async () => {
  const evaluacion = { id: 33, titulo: "Práctica de álgebra", curso: "Matemática", seccion: "3° A",
    estado: "PUBLICADA", periodoEvaluacion: "SEMANAL", fechaEvaluacion: "2026-03-20",
    componente: "Prácticas", pesoComponente: 30, competencias: [{ id: 2, nombre: "Argumenta" }] };
  const calls = fakeBackend({
    "GET /api/configuracion-academica/anios": page([{ id: 1, anio: 2026, periodos: [{ id: 4, nombre: "Bimestre 1" }],
      grados: [{ id: 7, numero: 3 }], secciones: [{ id: 8, grado: 3, nombre: "A" }] }]),
    "GET /api/planes-estudios/2026/3": { cursos: [{ id: 10, nombre: "Matemática", codigo: "MAT" }] },
    "GET /api/evaluaciones/academicas": page([{ evaluacion, registradas: 3, esperadas: 4 }]),
    "GET /api/evaluaciones/33/planilla": { evaluacion, alumnos: [] },
  });
  render(<AcademicCoursePanel tab="evaluaciones" />);
  const user = userEvent.setup();
  for (const [label, value] of [["Año académico", "1"], ["Período académico", "4"], ["Grado", "3"], ["Sección", "8"], ["Curso", "10"]]) {
    await user.selectOptions(await screen.findByLabelText(label), value);
  }
  const table = within(await screen.findByRole("table", { name: "Evaluaciones del curso" }));
  expect(calls.find(call => call.path === "/api/evaluaciones/academicas").query)
    .toMatchObject({ anio: "2026", periodo: "4", grado: "3", seccion: "8", curso: "10" });
  expect(table.getByText("Publicada")).toBeTruthy();
  expect(table.getByText("Peso del componente: 30 %")).toBeTruthy();
  expect(table.getByText("75 %")).toBeTruthy();
  await user.click(table.getByRole("button", { name: /Ver planilla/ }));
  expect(await screen.findByText("Consulta de solo lectura.")).toBeTruthy();
  expect(screen.queryByRole("button", { name: /guardar|publicar|corregir/i })).toBeNull();
  expect(calls.every(call => call.method === "GET")).toBe(true);
});

test("consulta por contexto, cuenta una versión por alumno y abre el detalle elegido", async () => {
  const base = "/api/calificaciones/informes/academicos";
  const student = { idAlumno: 11, alumno: "Ana Ramos", codigoAlumno: "E11", pendientes: 0, noEvaluadas: 0 };
  const calls = fakeBackend({
    "GET /api/configuracion-academica/anios": page([{ id: 1, anio: 2026, periodos: [{ id: 4, nombre: "Primer bimestre" }],
      grados: [{ id: 7, numero: 3 }], secciones: [{ id: 8, grado: 3, nombre: "A" }] }]),
    "GET /api/planes-estudios/2026/3": { cursos: [{ id: 10, nombre: "Matemática", codigo: "MAT" }] },
    [`GET ${base}/resumen`]: {
      estudiantes: [
        { id: 11, nombre: "Ana Ramos", codigoAlumno: "E11" },
        { id: 20, nombre: "Luis Salas", codigoAlumno: "E20" },
        { id: 30, nombre: "Lucía Vega", codigoAlumno: "E30" },
      ],
      libretas: [
        { ...student, id: 13, numeroVersion: 2, estado: "BORRADOR", logros: { A: 3 }, pendientes: 1 },
        { ...student, id: 12, numeroVersion: 1, estado: "PUBLICADA", logros: { C: 2 } },
        { ...student, id: 14, idAlumno: 20, alumno: "Luis Salas", numeroVersion: 1, estado: "BORRADOR", logros: { AD: 2 } },
      ],
    },
    [`GET ${base}/12`]: { ...progressReport, periodo: "Primer bimestre | Version 1 | PUBLICADA" },
  });
  render(<AcademicCoursePanel tab="reportes" />);
  const user = userEvent.setup();
  for (const [label, value] of [["Año académico", "1"], ["Período académico", "4"], ["Grado", "3"], ["Sección", "8"], ["Curso", "10"]]) {
    await user.selectOptions(await screen.findByLabelText(label), value);
  }
  const table = await screen.findByRole("table", { name: "Libretas por estudiante" });
  expect(within(table).getAllByRole("row")).toHaveLength(4);
  expect(within(table).getAllByText("Sin libreta publicada")).toHaveLength(2);
  expect(calls.find(call => call.path === base + "/resumen").query)
    .toEqual({ anio: "2026", periodo: "4", grado: "3", seccion: "8", curso: "10" });
  const summary = within(screen.getByRole("region", { name: "Resumen de libretas" }));
  expect(summary.getByText("Sin libreta publicada: 2")).toBeTruthy();
  expect(summary.getAllByRole("cell").map(cell => cell.textContent)).toEqual(["0", "0", "0", "2", "0", "0"]);
  await user.click(within(table).getByRole("button", { name: /ver publicada/i }));
  expect(await within(screen.getByRole("region", { name: "Libreta de Ana Ramos" })).findByText("Resuelve problemas")).toBeTruthy();
  expect(calls.some(call => call.path === base + "/12")).toBe(true);
  expect(calls.every(call => call.method === "GET")).toBe(true);
  await user.click(screen.getByRole("button", { name: "Cerrar detalle" }));
  await user.selectOptions(screen.getByLabelText("Año académico"), "");
  expect(screen.queryByRole("table")).toBeNull();
});

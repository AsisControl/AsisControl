// cobertura: detalle de progreso del estudiante (US-50)
import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, reply, signIn } from "../../../../tests/support/backend.js";
import { StudentProgress } from "../student-progress";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const evaluation = {
  id: 9, idAsignacionCurso: 42, titulo: "Práctica 1", descripcion: null, tipoEvaluacion: "PRACTICA",
  periodoEvaluacion: "BIMESTRE_1", estado: "PUBLICADA", fechaEvaluacion: "2026-04-10", ponderacionPorcentaje: 20,
  competencias: [{ id: 1, nombre: "Resuelve problemas" }, { id: 2, nombre: "Comunica ideas" }],
};
const student = {
  idAlumno: 7, codigoAlumno: "E7", nombreCompleto: "Lucía Prado",
  notas: [{ idCalificacion: 50, idCompetencia: 1, corregida: true }, { idCalificacion: 51, idCompetencia: 2, corregida: false }],
};
const CONSOLIDADO = "GET /api/calificaciones/consolidados";

beforeEach(() => signIn({ role: "DOCENTE", permissions: ["EVALUACIONES_LEER"] }));

test("sin estudiante elegido pide seleccionar uno", () => {
  render(<StudentProgress student={null} evaluation={evaluation} />);
  expect(screen.getByText("Selecciona un estudiante para ver su detalle de progreso")).toBeTruthy();
});

test("muestra los niveles consolidados, lo que falta y el historial de correcciones", async () => {
  const calls = fakeBackend({
    [CONSOLIDADO]: [
      { idCompetencia: 1, nombreCompetencia: "Resuelve problemas", nivelConsolidado: "A", evaluacionesConsideradas: 2 },
      { idCompetencia: 2, nombreCompetencia: "Comunica ideas", nivelConsolidado: null, estadoAvance: "PENDIENTE",
        evaluacionesConsideradas: 1, componentesFaltantes: ["Examen", "Proyecto"] },
      { idCompetencia: 3, nombreCompetencia: "Indaga", nivelConsolidado: null, estadoAvance: "NO_EVALUADA", evaluacionesConsideradas: 0 },
      { idCompetencia: 4, nombreCompetencia: "Explica", nivelConsolidado: null, evaluacionesConsideradas: 0 },
    ],
    "GET /api/calificaciones/50/correcciones": [{ numeroVersion: 2, nivelAnterior: "B", nivelNuevo: "A", notaAnterior: 13,
      notaNueva: 16, motivo: "Error de digitación", autor: "Prof. García", fecha: "2026-04-12T10:00:00" }],
  });
  render(<StudentProgress student={student} evaluation={evaluation} />);
  const detalle = await screen.findByRole("region", { name: "Detalle del estudiante" });
  expect(await within(detalle).findByText("Pendiente")).toBeTruthy();
  expect(within(detalle).getByText("Faltan: Examen, Proyecto")).toBeTruthy();
  expect(within(detalle).getByText("Aún no evaluada")).toBeTruthy();
  expect(within(detalle).getByText("Datos insuficientes")).toBeTruthy();
  expect(within(detalle).getByText("Resuelve problemas: B → A")).toBeTruthy();
  expect(within(detalle).getByText("Nota: 13 → 16")).toBeTruthy();
  expect(within(detalle).getByText("Motivo: Error de digitación")).toBeTruthy();
  expect(calls.find(c => c.path === "/api/calificaciones/consolidados").query).toEqual({ idAlumno: "7", idAsignacionCurso: "42" });
  expect(calls.some(c => c.path === "/api/calificaciones/51/correcciones")).toBe(false);
});

test("sin evaluaciones publicadas lo indica", async () => {
  fakeBackend({ [CONSOLIDADO]: [] });
  render(<StudentProgress student={{ ...student, notas: [] }} evaluation={evaluation} />);
  expect(await screen.findByText("Aún no tiene evaluaciones publicadas en este curso.")).toBeTruthy();
});

test("informa si no se pudo cargar el detalle", async () => {
  fakeBackend({ [CONSOLIDADO]: reply(500, { message: "Servicio no disponible" }) });
  render(<StudentProgress student={student} evaluation={evaluation} />);
  expect(await screen.findByText("No se pudo cargar el detalle: Servicio no disponible")).toBeTruthy();
});

test("en el modelo cualitativo consulta por el período que contiene la evaluación y permite cambiarlo", async () => {
  const calls = fakeBackend({
    "GET /api/evaluaciones/periodos-informe": [
      { id: 1, nombre: "Bimestre 1", fechaInicio: "2026-03-01", fechaFin: "2026-05-15" },
      { id: 2, nombre: "Bimestre 2", fechaInicio: "2026-05-16", fechaFin: "2026-07-20" },
    ],
    [CONSOLIDADO]: ({ query }) => [{ idCompetencia: 1, nombreCompetencia: "Resuelve problemas",
      nivelConsolidado: query.idPeriodoAcademico === "2" ? "AD" : "B", evaluacionesConsideradas: 1 }],
  });
  render(<StudentProgress student={{ ...student, notas: [] }} evaluation={{ ...evaluation, modeloCualitativo: true }} />);
  expect(await screen.findByText("B")).toBeTruthy();
  const periodo = screen.getByLabelText("Período académico");
  expect(periodo.value).toBe("1");
  await userEvent.selectOptions(periodo, "2");
  expect(await screen.findByText("AD")).toBeTruthy();
  expect(calls.filter(c => c.path === "/api/calificaciones/consolidados").at(-1).query.idPeriodoAcademico).toBe("2");
});

test("en el modelo cualitativo pide elegir el período si la evaluación no cae en ninguno", async () => {
  const calls = fakeBackend({
    "GET /api/evaluaciones/periodos-informe": [{ id: 1, nombre: "Bimestre 1", fechaInicio: "2026-03-01", fechaFin: "2026-03-31" }],
    [CONSOLIDADO]: [],
  });
  render(<StudentProgress student={student} evaluation={{ ...evaluation, modeloCualitativo: true }} />);
  expect(await screen.findByText("Selecciona un período académico para consultar los resultados.")).toBeTruthy();
  expect(calls.some(c => c.path === "/api/calificaciones/consolidados")).toBe(false);
});

test("en el modelo cualitativo informa si fallan los períodos", async () => {
  fakeBackend({ "GET /api/evaluaciones/periodos-informe": reply(500, { message: "Sin períodos" }) });
  render(<StudentProgress student={student} evaluation={{ ...evaluation, modeloCualitativo: true }} />);
  expect(await screen.findByText("No se pudo cargar el detalle: Sin períodos")).toBeTruthy();
});

// cobertura: evaluar el riesgo académico de un estudiante
import { beforeEach, expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { RiskEvaluationDialog } from "../risk/risk-evaluation-dialog";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const alumno = { id: 7, activo: true, codigoAlumno: "E7", persona: { nombres: "Lucía", apellidoPaterno: "Prado", apellidoMaterno: "Vega" } };
const inactivo = { ...alumno, id: 8, activo: false, codigoAlumno: "E8" };
const anio = { id: 1, anio: 2026, periodos: [{ id: 4, nombre: "Bimestre 1" }] };

beforeEach(() => signIn({ permissions: ["ACADEMICO_LEER", "ACADEMICO_ESCRIBIR"] }));

async function elegir(user) {
  await screen.findByRole("option", { name: "Lucía Prado Vega · E7" });
  expect(screen.queryByRole("option", { name: /E8/ })).toBeNull();
  await user.selectOptions(screen.getByLabelText("Estudiante"), "7");
  await user.selectOptions(screen.getByLabelText("Año académico"), "2026");
  await user.selectOptions(screen.getByLabelText("Período"), "4");
}

test("ejecuta la evaluación y muestra el resultado de cada criterio", async () => {
  const calls = fakeBackend({
    "GET /api/alumnos": page([alumno, inactivo]),
    "GET /api/configuracion-academica/anios": page([anio]),
    "POST /api/evaluaciones-riesgo": { resultado: "EN_RIESGO", reglas: [
      { idCriterio: 1, nombreCriterio: "Faltas del bimestre", indicador: "INASISTENCIAS", operador: "MAYOR_QUE", umbral: 3,
        valor: 5, resultado: "EN_RIESGO", motivo: "Supera el umbral" },
      { idCriterio: 2, nombreCriterio: "Promedio", indicador: "PROMEDIO_ACADEMICO", operador: null, umbral: null,
        valor: null, resultado: "DATOS_INSUFICIENTES", motivo: "Sin notas publicadas" },
    ] },
  });
  const onEvaluated = vi.fn();
  const user = userEvent.setup();
  render(<RiskEvaluationDialog onClose={vi.fn()} onEvaluated={onEvaluated} />);
  await elegir(user);
  await user.click(screen.getByRole("button", { name: "Ejecutar evaluación" }));
  expect(await screen.findByText("Faltas del bimestre")).toBeTruthy();
  expect(screen.getByText("Inasistencias · Umbral > 3 · Valor evaluado 5")).toBeTruthy();
  expect(screen.getByText("Promedio académico · Umbral —")).toBeTruthy();
  expect(screen.getByText("Sin notas publicadas")).toBeTruthy();
  expect(screen.queryByText("Supera el umbral")).toBeNull();
  expect(onEvaluated).toHaveBeenCalled();
  expect(calls.find(c => c.method === "POST").body).toEqual({ idAlumno: 7, idPeriodo: 4 });
});

test("sin criterios activos lo indica y un error se informa", async () => {
  let fallar = true;
  fakeBackend({
    "GET /api/alumnos": page([alumno]),
    "GET /api/configuracion-academica/anios": page([anio]),
    "POST /api/evaluaciones-riesgo": () => (fallar ? reply(422, { message: "El estudiante no tiene matrícula" })
      : { resultado: "DATOS_INSUFICIENTES", reglas: [] }),
  });
  const user = userEvent.setup();
  render(<RiskEvaluationDialog onClose={vi.fn()} onEvaluated={vi.fn()} />);
  await elegir(user);
  await user.click(screen.getByRole("button", { name: "Ejecutar evaluación" }));
  expect(await screen.findByText("El estudiante no tiene matrícula")).toBeTruthy();
  fallar = false;
  await user.click(screen.getByRole("button", { name: "Ejecutar evaluación" }));
  expect(await screen.findByText("No hay criterios de riesgo activos.")).toBeTruthy();
  expect(screen.getByText("Datos insuficientes")).toBeTruthy();
  await user.type(screen.getByLabelText("Buscar estudiante"), "Lu");
  expect(screen.queryByText("Datos insuficientes")).toBeNull();
});

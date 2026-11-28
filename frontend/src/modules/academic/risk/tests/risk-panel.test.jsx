import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../../tests/support/backend.js";
import { RiskPanel } from "../risk-panel";

vi.mock("next/navigation", () => import("../../../../../tests/support/navigation.js"));

const alert = { id: 9, nombreAlumno: "Eva Flores", nombreCriterio: "Nivel bajo", periodo: "Bimestre 1",
  indicador: "PROMEDIO_ACADEMICO", nivelRiesgo: "ALTO", estado: "ACTIVA", valorDetectado: 1,
  operador: "MENOR_QUE", umbral: 3, mensaje: "Resultado inferior al umbral" };

beforeEach(() => signIn({ permissions: ["RIESGOS_LEER", "RIESGOS_ESCRIBIR"] }));

test("guarda el umbral del criterio y actualiza el listado", async () => {
  let current = { id: 5, nombre: "Asistencia", indicador: "INASISTENCIAS", operador: "MAYOR_IGUAL",
    umbral: 2, nivelRiesgo: "ALTO", minimoDatos: 1 };
  const calls = fakeBackend({
    "GET /api/alertas-riesgo": page([]),
    "GET /api/criterios-riesgo": () => page([current]),
    "PUT /api/criterios-riesgo/5": ({ body }) => { current = { id: 5, ...body }; return current; },
  });
  const user = userEvent.setup();
  render(<RiskPanel canWrite />);
  await user.click(screen.getByRole("button", { name: "Configurar criterios" }));
  await user.click(await screen.findByRole("button", { name: "Editar" }));
  const threshold = screen.getByLabelText(/Umbral/);
  await user.clear(threshold);
  await user.type(threshold, "4");
  await user.click(screen.getByRole("button", { name: "Guardar criterio" }));
  await screen.findByText("≥ 4");
  expect(calls.find(({ method }) => method === "PUT").body).toMatchObject({ umbral: 4, indicador: "INASISTENCIAS" });
});

test("crea un criterio y muestra el conflicto si se intenta desactivar con alertas abiertas", async () => {
  let criterion;
  let canDisable = false;
  const calls = fakeBackend({
    "GET /api/alertas-riesgo": page([]),
    "GET /api/criterios-riesgo": () => page(criterion ? [criterion] : []),
    "POST /api/criterios-riesgo": ({ body }) => { criterion = { id: 7, ...body }; return reply(201, criterion); },
    "DELETE /api/criterios-riesgo/7": () => {
      if (!canDisable) return reply(409, { message: "Hay alertas abiertas con este criterio." });
      criterion = undefined;
      return reply(204);
    },
  });
  const user = userEvent.setup();
  render(<RiskPanel canWrite />);
  await user.click(screen.getByRole("button", { name: "Configurar criterios" }));
  await user.click(screen.getByRole("button", { name: "Nuevo criterio" }));
  const dialog = within(screen.getByRole("dialog"));
  await user.type(dialog.getByLabelText(/Nombre/), "Promedio en inicio");
  await user.selectOptions(dialog.getByLabelText(/Comparación/), "MENOR_QUE");
  await user.type(dialog.getByLabelText(/Umbral/), "2.5");
  await user.selectOptions(dialog.getByLabelText(/Nivel de riesgo/), "MEDIO");
  await user.click(dialog.getByRole("button", { name: "Guardar criterio" }));
  await screen.findByText("Promedio en inicio");
  expect(calls.find(({ method }) => method === "POST").body).toMatchObject({
    indicador: "PROMEDIO_ACADEMICO", operador: "MENOR_QUE", umbral: 2.5, nivelRiesgo: "MEDIO",
  });
  await user.click(screen.getByRole("button", { name: "Desactivar", exact: true }));
  await user.click(screen.getByRole("button", { name: "Desactivar criterio" }));
  await screen.findByText("Hay alertas abiertas con este criterio.");
  canDisable = true;
  await user.click(screen.getByRole("button", { name: "Desactivar criterio" }));
  await screen.findByText("Todavía no hay criterios de riesgo activos.");
  await user.click(screen.getByRole("button", { name: "Volver a alertas" }));
  await screen.findByRole("heading", { name: "Estudiantes en riesgo" });
});

test("filtra alertas, muestra evidencias y permite resolver con observación", async () => {
  const user = userEvent.setup();
  const calls = fakeBackend({
    "GET /api/alertas-riesgo": ({ query }) => page(query.nivel === "BAJO" ? [] : [alert]),
    "GET /api/alertas-riesgo/9": alert,
    "GET /api/alertas-riesgo/9/historial": [{ id: 1, fecha: "2026-10-10T12:00:00Z",
      evaluacion: { resultado: "EN_RIESGO", motivo: "Resultado insuficiente", evidencias: ["Evaluación de Matemática"] } }],
    "PUT /api/alertas-riesgo/9/estado": { ...alert, estado: "RESUELTA", observacion: "Se revisó con tutoría" },
  });
  render(<RiskPanel canWrite />);
  await screen.findByText("Eva Flores");
  await user.selectOptions(screen.getByLabelText("Nivel"), "BAJO");
  await screen.findByText("No se encontraron alertas con estos filtros.");
  await user.selectOptions(screen.getByLabelText("Nivel"), "ALTO");
  await user.click(await screen.findByRole("button", { name: /Ver alerta de Eva Flores/ }));
  const dialog = within(await screen.findByRole("dialog"));
  await dialog.findByText("Evaluación de Matemática");
  await user.selectOptions(dialog.getByLabelText("Nuevo estado"), "RESUELTA");
  await user.type(dialog.getByLabelText("Observación"), "Se revisó con tutoría");
  await user.click(dialog.getByRole("button", { name: "Guardar cambio" }));
  await dialog.findByText("Estado actualizado.");
  expect(calls.find(({ method }) => method === "PUT").body)
    .toEqual({ estado: "RESUELTA", observacion: "Se revisó con tutoría" });
});

test("el modo de consulta no ofrece acciones de escritura", async () => {
  fakeBackend({
    "GET /api/alertas-riesgo": page([alert]),
    "GET /api/alertas-riesgo/9": alert,
    "GET /api/alertas-riesgo/9/historial": [],
  });
  render(<RiskPanel canWrite={false} />);
  expect(screen.queryByRole("button", { name: "Evaluar riesgo" })).toBeNull();
  expect(screen.queryByRole("button", { name: "Configurar criterios" })).toBeNull();
  await userEvent.click(await screen.findByRole("button", { name: /Ver alerta de Eva Flores/ }));
  await screen.findByText("No hay evaluaciones registradas para esta alerta.");
  expect(screen.queryByLabelText("Nuevo estado")).toBeNull();
});

test.each([
  ["EN_RIESGO", "En riesgo"],
  ["SIN_RIESGO", "Sin riesgo"],
  ["DATOS_INSUFICIENTES", "Datos insuficientes"],
])("evalúa un estudiante y muestra el resultado %s", async (resultado, label) => {
  const user = userEvent.setup();
  const calls = fakeBackend({
    "GET /api/alertas-riesgo": page([]),
    "GET /api/alumnos": page([{ id: 15, activo: true, codigoAlumno: "EST015",
      persona: { nombres: "Eva", apellidoPaterno: "Flores" } }]),
    "GET /api/configuracion-academica/anios": page([{ id: 6, anio: 2026,
      periodos: [{ id: 21, nombre: "Primer bimestre" }] }]),
    "POST /api/evaluaciones-riesgo": { resultado, reglas: [
      { idCriterio: 2, nombreCriterio: "Asistencia", resultado, motivo: "Resultado del indicador",
        indicador: "INASISTENCIAS", umbral: 2, operador: "MAYOR_IGUAL", valor: 3 },
    ] },
  });
  render(<RiskPanel canWrite />);
  await user.click(screen.getByRole("button", { name: "Evaluar riesgo" }));
  const dialog = within(screen.getByRole("dialog", { name: "Evaluar riesgo" }));
  await dialog.findByRole("option", { name: /EST015/ });
  await user.selectOptions(dialog.getByLabelText("Estudiante"), "15");
  await user.selectOptions(dialog.getByLabelText("Año académico"), "2026");
  await user.selectOptions(dialog.getByLabelText("Período"), "21");
  await user.click(dialog.getByRole("button", { name: "Ejecutar evaluación" }));
  expect(await dialog.findByText(label, { selector: "strong" })).toBeTruthy();
  expect(dialog.getByText("Inasistencias · Umbral ≥ 2 · Valor evaluado 3")).toBeTruthy();
  expect(calls.find(({ method }) => method === "POST").body).toEqual({ idAlumno: 15, idPeriodo: 21 });
});

import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, signIn } from "../../../../../tests/support/backend.js";
import { RiskPanel } from "../risk-panel";

vi.mock("next/navigation", () => import("../../../../../tests/support/navigation.js"));

const alert = { id: 9, nombreAlumno: "Eva Flores", nombreCriterio: "Nivel bajo", periodo: "Bimestre 1",
  indicador: "PROMEDIO_ACADEMICO", nivelRiesgo: "ALTO", estado: "ACTIVA", valorDetectado: 1,
  operador: "MENOR_QUE", umbral: 3, mensaje: "Resultado inferior al umbral" };

beforeEach(() => signIn({ permissions: ["RIESGOS_LEER", "RIESGOS_ESCRIBIR"] }));

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
  await userEvent.click(await screen.findByRole("button", { name: /Ver alerta de Eva Flores/ }));
  await screen.findByText("No hay evaluaciones registradas para esta alerta.");
  expect(screen.queryByLabelText("Nuevo estado")).toBeNull();
});

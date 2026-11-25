import { beforeEach, expect, test } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, signIn } from "../../../../tests/support/backend.js";
import { GradingRulesPanel } from "../grading-rules/grading-rules-panel";

beforeEach(() => signIn());

test("configura un curso del plan, muestra la vista previa y activa una versión", async () => {
  const user = userEvent.setup();
  const componente = { tipoEvaluacion: "EXAMEN", nombre: "Exámenes", peso: 100 };
  const draft = { id: 6, estado: "BORRADOR", numeroVersion: 1, version: 0, componentes: [componente] };
  const calls = fakeBackend({
    "GET /api/configuracion-academica/anios": page([{ id: 1, anio: 2026,
      grados: [{ id: 8, numero: 1, activo: true }] }]),
    "GET /api/planes-estudios/2026/1": { cursos: [{ id: 4, nombre: "Matemática", codigo: "MAT" }] },
    "GET /api/reglas-calificacion": [],
    "PUT /api/reglas-calificacion/borrador": draft,
    "POST /api/reglas-calificacion/vista-previa": { nivelConsolidado: "AD", promedioPonderado: 4 },
    "POST /api/reglas-calificacion/6/activar": { ...draft, estado: "VIGENTE", version: 1 },
  });
  render(<GradingRulesPanel canWrite />);
  await screen.findByRole("option", { name: "2026" });
  await user.selectOptions(screen.getByLabelText("Año académico"), "2026");
  await user.selectOptions(screen.getByLabelText("Grado"), "8");
  await screen.findByRole("option", { name: "Matemática · MAT" });
  await user.selectOptions(screen.getByLabelText("Curso"), "4");
  await user.click(await screen.findByRole("button", { name: "Agregar componente" }));
  const second = within(screen.getByRole("group", { name: "Componente 2" }));
  await user.type(second.getByLabelText("Peso (%)"), "15");
  expect(screen.getByRole("button", { name: "Vista previa" }).disabled).toBe(true);
  await user.click(second.getByRole("button", { name: "Quitar componente" }));
  await user.selectOptions(screen.getByLabelText("Nivel de ejemplo"), "AD");
  await user.click(screen.getByRole("button", { name: "Vista previa" }));
  await screen.findByText("Resultado de ejemplo: AD (4)");
  await user.click(screen.getByRole("button", { name: "Guardar borrador" }));
  await user.click(await screen.findByRole("button", { name: "Activar regla" }));
  await screen.findByText("Regla vigente · versión 1");
  expect(calls.find(call => call.method === "PUT").body).toEqual({
    idCurso: 4, idGrado: 8, formula: "PROMEDIO_PONDERADO", componentes: [componente],
  });
  expect(calls.find(call => call.path.endsWith("/activar")).body).toEqual({ version: 0 });
});

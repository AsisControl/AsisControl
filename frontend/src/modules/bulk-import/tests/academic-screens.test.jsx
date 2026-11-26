import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { Schedules } from "@/modules/schedules";
import { StudyPlansPanel } from "@/modules/academic/study-plans-panel";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

// OBS-05 · ASISCTRL-230/231: cada pantalla abre la carga masiva de su propio módulo.
const excel = () =>
  new File(["x".repeat(10)], "carga.xlsx", {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
const result = { total: 1, validas: 1, creadas: 0, omitidas: [], errores: [] };

async function reviewIn(user, dialogName, fileLabel) {
  const dialog = screen.getByRole("dialog", { name: dialogName });
  await user.upload(within(dialog).getByLabelText(fileLabel, { exact: false }), excel());
  await user.click(within(dialog).getByRole("button", { name: "Revisar archivo" }));
  expect(await within(dialog).findByText(/1 lista para importar/)).toBeTruthy();
}

beforeEach(() => {
  signIn({ permissions: ["ACADEMICO_LEER", "ACADEMICO_ESCRIBIR"] });
});

test("Asignaciones docentes abre su carga masiva", async () => {
  setUrl("/horarios/asignaciones-docentes");
  const calls = fakeBackend({
    "GET /api/asignaciones-cursos": page([]),
    "GET /api/docentes": page([]),
    "GET /api/secciones": page([]),
    "POST /api/asignaciones-cursos/importaciones": result,
  });
  const user = userEvent.setup();
  render(<Schedules tab="asignaciones-docentes" canWrite />);
  await user.click(await screen.findByRole("button", { name: "Carga masiva" }));
  await reviewIn(user, "Carga masiva de asignaciones docentes", "Archivo Excel de asignaciones");
  expect(calls.some((call) => call.path === "/api/asignaciones-cursos/importaciones")).toBe(true);
});

test("Sin permiso de escritura, asignaciones no muestra Carga masiva", async () => {
  setUrl("/horarios/asignaciones-docentes");
  fakeBackend({
    "GET /api/asignaciones-cursos": page([]),
    "GET /api/docentes": page([]),
    "GET /api/secciones": page([]),
  });
  render(<Schedules tab="asignaciones-docentes" canWrite={false} />);
  expect(await screen.findByRole("heading", { name: "Asignaciones docentes" })).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Carga masiva" })).toBeNull();
});

test("Plan de estudios abre su carga masiva", async () => {
  const calls = fakeBackend({
    "GET /api/configuracion-academica/anios": page([]),
    "POST /api/planes-estudios/importaciones": result,
  });
  const user = userEvent.setup();
  render(<StudyPlansPanel canWrite />);
  await user.click(await screen.findByRole("button", { name: "Carga masiva" }));
  await reviewIn(user, "Carga masiva del plan de estudios", "Archivo Excel del plan de estudios");
  expect(calls.some((call) => call.path === "/api/planes-estudios/importaciones")).toBe(true);
});

test("Sin permiso de escritura, el plan de estudios no muestra Carga masiva", async () => {
  fakeBackend({ "GET /api/configuracion-academica/anios": page([]) });
  render(<StudyPlansPanel canWrite={false} />);
  expect(await screen.findByText("Plan de estudios")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Carga masiva" })).toBeNull();
});

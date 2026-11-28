import { expect, test, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { Enrollments } from "@/modules/enrollment";
import { PaymentSettings } from "@/modules/payment-settings/payment-settings";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

// OBS-05 · ASISCTRL-232/233: tarifas y matrículas abren la carga masiva de su propio módulo.
const SETTINGS = "/api/configuracion-pagos";
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

function settingsBackend(routes = {}) {
  return fakeBackend({
    [`GET ${SETTINGS}/conceptos`]: [],
    [`GET ${SETTINGS}/anios`]: [2027],
    [`GET ${SETTINGS}/tarifas`]: [],
    [`GET ${SETTINGS}/beneficios`]: [],
    [`GET ${SETTINGS}/estudiantes`]: page([]),
    ...routes,
  });
}

test("Tarifas abre su carga masiva", async () => {
  signIn({ permissions: ["PAGOS_LEER", "PAGOS_ESCRIBIR"] });
  const calls = settingsBackend({ [`POST ${SETTINGS}/tarifas/importaciones`]: result });
  const user = userEvent.setup();
  render(<PaymentSettings canWrite />);
  await user.click(screen.getByRole("button", { name: "Tarifas por año y grado", exact: true }));
  await user.click(await screen.findByRole("button", { name: "Carga masiva" }));
  await reviewIn(user, "Carga masiva de tarifas", "Archivo Excel de tarifas");
  expect(calls.some((call) => call.path === `${SETTINGS}/tarifas/importaciones`)).toBe(true);
});

test("Sin permiso de escritura, tarifas no muestra Carga masiva", async () => {
  signIn({ permissions: ["PAGOS_LEER"] });
  settingsBackend();
  const user = userEvent.setup();
  render(<PaymentSettings canWrite={false} />);
  await user.click(screen.getByRole("button", { name: "Tarifas por año y grado", exact: true }));
  expect(await screen.findByRole("heading", { name: "Tarifas por año y grado" })).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Carga masiva" })).toBeNull();
});

test("Matrículas abre su carga masiva solo para quien puede matricular", async () => {
  signIn({ permissions: ["MATRICULA_LEER", "MATRICULA_ESCRIBIR"] });
  setUrl("/matricula/matriculas");
  const calls = fakeBackend({
    "GET /api/matriculas": page([]),
    "POST /api/matriculas/importaciones": result,
  });
  const user = userEvent.setup();
  const { unmount } = render(<Enrollments tab="matriculas" canManageRenewals />);
  await user.click(await screen.findByRole("button", { name: "Carga masiva" }));
  await reviewIn(user, "Carga masiva de matrículas", "Archivo Excel de matrículas");
  expect(calls.some((call) => call.path === "/api/matriculas/importaciones")).toBe(true);
  unmount();

  render(<Enrollments tab="matriculas" canManageRenewals={false} />);
  expect(await screen.findByRole("heading", { name: "Matrículas registradas" })).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Carga masiva" })).toBeNull();
});

test("Solicitudes no muestra la carga masiva de matrículas", async () => {
  signIn({ permissions: ["MATRICULA_LEER", "MATRICULA_ESCRIBIR"] });
  setUrl("/matricula/solicitudes");
  fakeBackend({ "GET /api/solicitudes-matricula": page([]) });
  render(<Enrollments tab="solicitudes" canManageRenewals />);
  expect(await screen.findByRole("heading", { name: "Solicitudes de matrícula" })).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Carga masiva" })).toBeNull();
});

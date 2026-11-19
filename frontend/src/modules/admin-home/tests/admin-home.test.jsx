import { beforeEach, expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { fakeBackend, reply, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { AdminHome } from "@/modules/admin-home";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const RUTA = "GET /api/dashboard";
const indicador = (codigo, etiqueta, valor, tono = "neutral") => ({ codigo, etiqueta, valor, tono, rutaDestino: `/${codigo}` });
const panel = (overrides = {}) => ({
  rol: "ADMINISTRADOR", personaId: null, generadoEn: "2026-11-19T10:00:00Z",
  indicadores: [
    indicador("ALUMNOS_ACTIVOS", "Alumnos activos", 120),
    indicador("PAGOS_VENCIDOS", "Pagos vencidos", 7, "danger"),
    indicador("INASISTENCIAS_HOY", "Inasistencias de hoy", 4, "warning"),
    indicador("NOTIFICACIONES_NO_LEIDAS", "Notificaciones no leídas", 2),
  ],
  ...overrides,
});

beforeEach(() => {
  signIn({ role: "ADMINISTRADOR", id: "1", permissions: [] });
  setUrl("/");
});

test("Muestra los indicadores reales del backend con su valor", async () => {
  fakeBackend({ [RUTA]: panel() });
  render(<AdminHome fullName="Ana Pérez" />);
  expect(screen.getByRole("heading", { name: "Panel de administración" })).toBeTruthy();
  expect(screen.getByText(/Bienvenido\/a, Ana Pérez/)).toBeTruthy();
  expect((await screen.findByText("Alumnos activos")).previousElementSibling.textContent).toBe("120");
  expect(screen.getByText("Pagos vencidos").previousElementSibling.textContent).toBe("7");
  expect(screen.getByText("Inasistencias de hoy").previousElementSibling.textContent).toBe("4");
});

test("Los indicadores con pantalla destino son enlaces del frontend y los demás no", async () => {
  fakeBackend({ [RUTA]: panel() });
  render(<AdminHome fullName="Ana Pérez" />);
  await screen.findByText("Alumnos activos");
  expect(screen.getByText("Alumnos activos").closest("a").getAttribute("href")).toBe("/matricula");
  expect(screen.getByText("Pagos vencidos").closest("a").getAttribute("href")).toBe("/finanzas");
  expect(screen.getByText("Inasistencias de hoy").closest("a")).toBeNull();
  expect(screen.getByText("Notificaciones no leídas").closest("a")).toBeNull();
});

test("Informa el error y mantiene las acciones rápidas", async () => {
  fakeBackend({ [RUTA]: reply(500, { message: "No se pudo cargar el panel." }) });
  render(<AdminHome fullName="Ana Pérez" quickActions={<p>Atajos del administrador</p>} />);
  expect((await screen.findByRole("alert")).textContent).toContain("No se pudo cargar");
  expect(screen.getByText("Atajos del administrador")).toBeTruthy();
});

test("Sin indicadores avisa que no hay datos", async () => {
  fakeBackend({ [RUTA]: panel({ indicadores: [] }) });
  render(<AdminHome fullName="Ana Pérez" />);
  expect(await screen.findByText("No hay indicadores disponibles.")).toBeTruthy();
});

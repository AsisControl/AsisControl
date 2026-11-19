import { beforeEach, expect, test, vi } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, signIn, signOut } from "../../../tests/support/backend.js";
import { currentUrl, setUrl } from "../../../tests/support/navigation.js";
import { AppShell } from "../layout/app-shell";
import { Brand } from "../layout/brand";
import { HomeQuickActions } from "../layout/home-quick-actions";
import { RoleContext } from "../layout/role-context";
import { ModuleOutlet } from "../navigation/module-outlet";
import { roleDetails, roleLabels } from "../navigation/navigation";

vi.mock("next/navigation", () => import("../../../tests/support/navigation.js"));
vi.mock("next/link", () => import("../../../tests/support/next-link.jsx"));

const permissions = ["MATRICULA_LEER", "ACADEMICO_LEER", "USUARIOS_LEER", "ASISTENCIA_LEER", "CALIFICACIONES_LEER", "TAREAS_LEER", "PAGOS_LEER"];
const user = { id: "1", username: "ana", fullName: "Ana Ramos", role: "DOCENTE", permissions };
beforeEach(() => { signOut(); setUrl("/"); });

function renderShell(role = "DOCENTE", granted = permissions) {
  fakeBackend({
    "GET /api/auth/me": { id: 1, username: "ana", nombreCompleto: "Ana Ramos", rol: role === "ESTUDIANTE" ? "ALUMNO" : role,
      permisos: granted, requiereCambioContrasena: false },
    "GET /api/asignaciones-cursos/mias": page([]),
  });
  signIn({ ...user, role, permissions: granted });
  return render(<AppShell><ModuleOutlet /></AppShell>);
}

test.each([
  ["ADMINISTRADOR", "Panel de administración", ["Matrícula", "Académico", "Administración"]],
  ["DOCENTE", "Aula docente", ["Mis cursos", "Evaluaciones", "Tareas", "Horario"]],
  ["ESTUDIANTE", "Portal del estudiante", ["Tareas", "Progreso", "Horario"]],
  ["APODERADO", "Portal del apoderado", ["Matrícula", "Pagos", "Asistencia"]],
])("%s integra identidad en un solo header y acciones dentro de Inicio", async (role, title, shortcuts) => {
  renderShell(role);
  const context = await screen.findByRole("group", { name: "Espacio del usuario" });
  const header = context.closest("header");
  expect(header).not.toBeNull();
  expect(document.querySelectorAll("header.top-nav")).toHaveLength(1);
  expect(context.closest("main").dataset.role).toBe(role);
  expect(within(context).getByText(title)).toBeTruthy();
  expect(context.querySelector("svg[aria-hidden='true']")).toBeTruthy();
  expect(within(header).getByText("AsisControl")).toBeTruthy();
  const account = within(header).getByRole("button", { name: "Abrir menú de usuario" });
  const name = within(account).getByText("Ana Ramos");
  const badge = within(account).getByText(role);
  expect(badge.nextElementSibling).toBe(name);
  expect(within(account).getByText("AR")).toBeTruthy();
  expect(within(account).queryByText(roleLabels[role])).toBeNull();
  expect(within(header).queryByText("Colegio PUCP")).toBeNull();
  expect(within(header).queryByRole("link")).toBeNull();
  expect(screen.queryByRole("complementary", { name: "Espacio del usuario" })).toBeNull();
  const mainNav = screen.getByRole("navigation", { name: "Navegación principal" });
  expect(within(mainNav).getAllByRole("button").map((button) => button.textContent)).toEqual(roleDetails[role].nav);
  const actions = screen.getByRole("region", { name: "Acciones rápidas" });
  expect(actions.closest(".home-content")).not.toBeNull();
  expect(screen.getAllByRole("heading", { name: "Acciones rápidas" })).toHaveLength(1);
  expect(within(actions).getAllByRole("link")).toHaveLength(shortcuts.length);
  for (const label of shortcuts) expect(within(actions).getByRole("link", { name: label })).toBeTruthy();
  expect(screen.queryByRole("navigation", { name: /Accesos rápidos|Acciones rápidas/ })).toBeNull();
});

test("Una acción de Inicio navega, activa el menú y desaparece junto con la tarjeta", async () => {
  renderShell();
  const actions = await screen.findByRole("region", { name: "Acciones rápidas" });
  const link = within(actions).getByRole("link", { name: "Evaluaciones" });
  expect(link.getAttribute("href")).toBe("/evaluaciones");
  await userEvent.click(link);
  expect(currentUrl()).toBe("/evaluaciones");
  const active = within(screen.getByRole("navigation", { name: "Navegación principal" })).getByRole("button", { name: "Evaluaciones" });
  expect(active.getAttribute("aria-current")).toBe("page");
  expect(screen.queryByRole("region", { name: "Acciones rápidas" })).toBeNull();
  expect(screen.getByRole("group", { name: "Espacio del usuario" }).closest("header")).not.toBeNull();
});

test("Una ruta distinta de Inicio nunca muestra acciones rápidas", async () => {
  setUrl("/evaluaciones");
  renderShell();
  await screen.findByRole("group", { name: "Espacio del usuario" });
  expect(screen.queryByRole("region", { name: "Acciones rápidas" })).toBeNull();
});

test.each([
  ["DOCENTE", ["CALIFICACIONES_LEER"], ["/evaluaciones"]],
  ["DOCENTE", ["ACADEMICO_LEER"], ["/horario"]],
  ["DOCENTE", [], []],
  ["APODERADO", ["PAGOS_LEER"], ["/pagos", "/asistencia"]],
  ["ADMINISTRADOR", ["MATRICULA_LEER"], ["/matricula"]],
])("Las acciones de %s respetan los permisos existentes (%j)", (role, granted, expected) => {
  render(<HomeQuickActions user={{ ...user, role, permissions: granted }} />);
  expect(screen.queryAllByRole("link").map((link) => link.getAttribute("href"))).toEqual(expected);
  if (!expected.length) expect(screen.queryByRole("region", { name: "Acciones rápidas" })).toBeNull();
});

test("Al cambiar rol no conserva identidad ni acciones del anterior", () => {
  const { rerender } = render(<><RoleContext role="DOCENTE" /><HomeQuickActions user={user} /></>);
  expect(screen.getByRole("link", { name: "Evaluaciones" })).toBeTruthy();
  rerender(<><RoleContext role="APODERADO" /><HomeQuickActions user={{ ...user, role: "APODERADO", permissions: ["PAGOS_LEER"] }} /></>);
  expect(screen.getByText("Portal del apoderado")).toBeTruthy();
  expect(screen.queryByText("Aula docente")).toBeNull();
  expect(screen.queryByRole("link", { name: "Evaluaciones" })).toBeNull();
  expect(screen.getAllByRole("link").map((link) => link.getAttribute("href"))).toEqual(["/pagos", "/asistencia"]);
});

test("El teclado activa acciones y los clics modificados conservan el enlace nativo", async () => {
  render(<HomeQuickActions user={user} />);
  const interaction = userEvent.setup();
  await interaction.tab();
  expect(document.activeElement).toBe(screen.getByRole("link", { name: "Mis cursos" }));
  const link = screen.getByRole("link", { name: "Tareas" });
  link.addEventListener("click", (event) => event.preventDefault(), { once: true });
  fireEvent.click(link, { ctrlKey: true });
  expect(currentUrl()).toBe("/");
  await interaction.keyboard("{Enter}");
  expect(currentUrl()).toBe("/mis-cursos");
  expect(screen.queryByRole("region", { name: "Acciones rápidas" })).toBeNull();
});

test("La identidad se conserva al abrir el menú móvil y se cierra con Escape", async () => {
  renderShell();
  const toggle = await screen.findByLabelText("Abrir menú de navegación");
  const interaction = userEvent.setup();
  await interaction.click(toggle);
  expect(toggle.getAttribute("aria-expanded")).toBe("true");
  expect(screen.getByRole("group", { name: "Espacio del usuario" }).closest("header")).not.toBeNull();
  await interaction.keyboard("{Escape}");
  expect(toggle.getAttribute("aria-expanded")).toBe("false");
  expect(document.activeElement).toBe(toggle);
});

test("Sin sesión conserva la marca institucional sin identidad ni acciones privadas", async () => {
  fakeBackend();
  render(<AppShell><p>Contenido privado</p></AppShell>);
  await screen.findByRole("heading", { name: "Iniciar sesión" });
  expect(screen.queryByRole("group", { name: "Espacio del usuario" })).toBeNull();
  expect(screen.queryByRole("region", { name: "Acciones rápidas" })).toBeNull();
  expect(document.querySelector("[data-role]")).toBeNull();
  expect(screen.getAllByText("Colegio PUCP").length).toBeGreaterThan(0);
});

test("Brand conserva los valores originales fuera de la cabecera autenticada", () => {
  render(<Brand />);
  expect(screen.getByText("Colegio PUCP")).toBeTruthy();
  expect(screen.getByText("Sistema de Gestión Académica")).toBeTruthy();
});

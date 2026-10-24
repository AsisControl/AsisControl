import { beforeEach, expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, reply, signIn, signOut } from "../../../tests/support/backend.js";
import RecoveryPage from "@/app/recuperar-contrasena/page";

vi.mock("next/link", () => import("../../../tests/support/next-link.jsx"));

beforeEach(() => {
  signOut();
  window.history.replaceState(null, "", "/recuperar-contrasena");
});

test("Solicita el enlace con el usuario indicado aunque haya una sesión abierta", async () => {
  const calls = fakeBackend({
    "POST /api/auth/recovery/request": { message: "Si el usuario existe, recibirá un correo." },
  });
  signIn();
  const user = userEvent.setup();
  render(<RecoveryPage />);
  expect(screen.getByText("Recuperar contraseña", { selector: "h1" })).toBeTruthy();
  expect(screen.getByText(/Volver al inicio de sesión/).getAttribute("href")).toBe("/");
  await user.type(screen.getByLabelText(/Usuario institucional/), " pmorales ");
  await user.click(screen.getByText("Enviar instrucciones"));
  expect(await screen.findByText("Revisa tu correo")).toBeTruthy();
  expect(screen.getByRole("status").textContent).toBe("Si el usuario existe, recibirá un correo.");
  expect(calls[0].body).toEqual({ identifier: "pmorales" });
  await user.click(screen.getByText("Solicitar otro enlace"));
  expect(screen.getByLabelText(/Usuario institucional/)).toBeTruthy();
});

test("Informa el límite de solicitudes del backend", async () => {
  fakeBackend({
    "POST /api/auth/recovery/request": reply(429, { message: "Demasiadas solicitudes." }),
  });
  const user = userEvent.setup();
  render(<RecoveryPage />);
  await user.type(screen.getByLabelText(/Usuario institucional/), "pmorales");
  await user.click(screen.getByText("Enviar instrucciones"));
  expect((await screen.findByRole("alert")).textContent).toBe("Demasiadas solicitudes.");
});

test("Con un enlace válido valida el token, lo quita de la URL y cambia la contraseña", async () => {
  const calls = fakeBackend({
    "POST /api/auth/recovery/validate": reply(204),
    "POST /api/auth/recovery/reset": { message: "Contraseña actualizada correctamente." },
  });
  window.history.replaceState(null, "", "/recuperar-contrasena#token=enlace-123");
  const user = userEvent.setup();
  render(<RecoveryPage />);
  expect(await screen.findByText("Elige una nueva contraseña")).toBeTruthy();
  expect(window.location.hash).toBe("");
  const submit = async (next, confirm) => {
    for (const [label, value] of [
      [/^Nueva contraseña/, next],
      [/Confirmar contraseña/, confirm],
    ]) {
      const input = screen.getByLabelText(label);
      await user.clear(input);
      await user.type(input, value);
    }
    await user.click(screen.getByText("Guardar contraseña"));
  };
  await submit("corta", "corta");
  expect(screen.getByRole("alert").textContent).toMatch(/8 a 64 caracteres/);
  await submit("Nueva2026x", "Nueva2026y");
  expect(screen.getByRole("alert").textContent).toBe("Las contraseñas no coinciden.");
  await submit("Nueva2026x", "Nueva2026x");
  expect(await screen.findByText("Contraseña actualizada", { selector: "h1" })).toBeTruthy();
  expect(screen.queryByText(/Volver al inicio de sesión/)).toBeNull();
  expect(screen.getByText("Ir al inicio de sesión").getAttribute("href")).toBe("/");
  expect(calls.map((c) => c.body)).toEqual([
    { token: "enlace-123" },
    { token: "enlace-123", newPassword: "Nueva2026x", confirmPassword: "Nueva2026x" },
  ]);
});

test("Un enlace vencido explica el problema y permite pedir otro", async () => {
  fakeBackend({
    "POST /api/auth/recovery/validate": reply(400, { message: "El enlace venció." }),
  });
  window.history.replaceState(null, "", "/recuperar-contrasena#token=viejo");
  const user = userEvent.setup();
  render(<RecoveryPage />);
  expect(await screen.findByText("Enlace no disponible")).toBeTruthy();
  expect(screen.getByRole("alert").textContent).toBe("El enlace venció.");
  await user.click(screen.getByText("Solicitar nuevo enlace"));
  expect(screen.getByText("Recuperar contraseña", { selector: "h1" })).toBeTruthy();
  expect(screen.queryByRole("alert")).toBeNull();
});

import { expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import { EnrollmentHistory } from "../enrollment-history";

const event = (overrides = {}) => ({
  id: 1,
  actor: "admin",
  actorNombre: "Ana Rojas",
  rol: "ADMINISTRADOR",
  fecha: "2026-11-04T16:41:26Z",
  accion: "CREAR_MATRICULA",
  motivo: null,
  campos: [],
  ...overrides,
});

test("Una matrícula nueva muestra estado y sección sin códigos internos", () => {
  render(
    <EnrollmentHistory
      events={[
        event({
          motivo: "Aprobación administrativa",
          campos: [
            { campo: "estado", anterior: null, nuevo: "ACTIVA" },
            { campo: "seccion", anterior: null, nuevo: "2027 · 1° A" },
          ],
        }),
      ]}
    />,
  );
  expect(screen.getByText("Matrícula creada")).toBeTruthy();
  expect(screen.getByText(/Ana Rojas · Administrador$/)).toBeTruthy();
  expect(screen.getByText("Estado: Activa")).toBeTruthy();
  expect(screen.getByText("Sección: 2027 · 1° A")).toBeTruthy();
  expect(screen.getByText("Motivo: Aprobación administrativa")).toBeTruthy();
  expect(screen.queryByText(/ACTIVA|seccion=|Nuevo:/)).toBeNull();
});

test("Un cambio de estado muestra el valor anterior y el nuevo con sus tildes", () => {
  render(
    <EnrollmentHistory
      events={[
        event({
          accion: "CAMBIAR_ESTADO_MATRICULA",
          campos: [{ campo: "estado", anterior: "EN_REVISION", nuevo: "MATRICULA_FINALIZADA" }],
        }),
      ]}
    />,
  );
  expect(screen.getByText("Cambio de estado de la matrícula")).toBeTruthy();
  expect(screen.getByText("Estado: En revisión → Matrícula finalizada")).toBeTruthy();
});

test("Oculta lo que no cambió y usa el usuario si el acceso ya no está activo", () => {
  render(
    <EnrollmentHistory
      events={[
        event({
          accion: "ACCION_NUEVA",
          actorNombre: null,
          actor: "usuario.inactivo",
          rol: null,
          campos: [
            { campo: "estado", anterior: "ENVIADA", nuevo: "EN_REVISION" },
            { campo: "seccion", anterior: "2027 · 1° A", nuevo: "2027 · 1° A" },
          ],
        }),
      ]}
    />,
  );
  expect(screen.getByText("Accion nueva")).toBeTruthy();
  expect(screen.getByText(/usuario\.inactivo$/)).toBeTruthy();
  expect(screen.getByText("Estado: Enviada → En revisión")).toBeTruthy();
  expect(screen.queryByText(/Sección:/)).toBeNull();
});

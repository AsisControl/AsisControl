import { expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import { Loading } from "../loading";

test("por defecto muestra un esqueleto de tabla y anuncia el texto solo a lectores de pantalla", () => {
  render(<Loading>Cargando cursos…</Loading>);
  const estado = screen.getByRole("status");
  expect(estado.getAttribute("aria-busy")).toBe("true");
  expect(estado.textContent).toBe("Cargando cursos…");
  const esqueleto = estado.querySelector(":scope > [aria-hidden='true']");
  expect(esqueleto).toBeTruthy();
  expect(esqueleto.textContent).toBe("");
  // 1 encabezado + 5 filas de 4 columnas.
  expect(esqueleto.children).toHaveLength(6);
  expect(esqueleto.children[1].children).toHaveLength(4);
});

test.each([
  ["table", { rows: 3, columns: 2 }, 4],
  ["list", { rows: 2 }, 2],
  ["detail", { rows: 5 }, 5],
  ["cards", { rows: 3 }, 3],
  ["timetable", { rows: 2 }, 18],
])("el esqueleto %s respeta la cantidad pedida", (skeleton, props, hijos) => {
  render(<Loading skeleton={skeleton} {...props}>Cargando…</Loading>);
  const esqueleto = screen.getByRole("status").querySelector(":scope > [aria-hidden='true']");
  expect(esqueleto.children).toHaveLength(hijos);
});

test("acepta un esqueleto propio de la pantalla", () => {
  render(<Loading skeleton={<div data-testid="propio" aria-hidden="true" />}>Cargando tu inicio…</Loading>);
  expect(screen.getByRole("status").textContent).toBe("Cargando tu inicio…");
  expect(screen.getByTestId("propio")).toBeTruthy();
});

test.each(["inline", "screen"])("la variante %s mantiene el anillo con el texto visible", (variant) => {
  render(<Loading variant={variant} className="extra">Cargando…</Loading>);
  const estado = screen.getByRole("status");
  expect(estado.className).toContain(variant);
  expect(estado.className).toContain("extra");
  expect(estado.textContent).toBe("Cargando…");
  expect(estado.querySelector("[aria-hidden='true']").textContent).toBe("");
});

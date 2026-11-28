import { expect, test } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { HomeCard, HomeCardsSkeleton, HomeColumns, HomeIndicator, HomeIndicators, HomeIndicatorsSkeleton } from "../home";

test("un indicador con enlace lleva a su pantalla y uno sin enlace es una tarjeta informativa", () => {
  render(
    <HomeIndicators>
      <HomeIndicator icon={<svg data-testid="icono" />} value={7} label="Tareas pendientes" tone="warning" href="/tareas" />
      <HomeIndicator icon={<svg />} value="Al día" label="Próximo vencimiento" />
    </HomeIndicators>,
  );
  const enlace = screen.getByRole("link", { name: /Tareas pendientes/ });
  expect(enlace.getAttribute("href")).toBe("/tareas");
  expect(within(enlace).getByText("7")).toBeTruthy();
  expect(screen.getAllByRole("link")).toHaveLength(1);
  expect(screen.getByText("Próximo vencimiento").closest("article")).toBeTruthy();
});

test("la tarjeta muestra su título, el enlace a la pantalla completa y su contenido", () => {
  render(
    <HomeColumns layout="equal">
      <HomeCard title="Próximos compromisos" action={{ label: "Ver pagos", href: "/pagos" }} as="section">
        <p>Sin pendientes</p>
      </HomeCard>
    </HomeColumns>,
  );
  const tarjeta = screen.getByRole("heading", { name: "Próximos compromisos" }).closest("section");
  expect(tarjeta).toBeTruthy();
  expect(within(tarjeta).getByRole("link", { name: /Ver pagos/ }).getAttribute("href")).toBe("/pagos");
  expect(within(tarjeta).getByText("Sin pendientes")).toBeTruthy();
});

test("los esqueletos mantienen la cantidad pedida de indicadores y tarjetas", () => {
  const { container } = render(
    <>
      <HomeIndicatorsSkeleton count={6} />
      <HomeCardsSkeleton count={2} />
    </>,
  );
  expect(container.children[0].children).toHaveLength(6);
  expect(container.children[1].children).toHaveLength(2);
});

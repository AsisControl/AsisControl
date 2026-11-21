import { screen } from "@testing-library/react";

export const simulatedCard = {
  number: "4242 4242 4242 4242",
  holder: "Titular de prueba",
  securityCode: "123",
};

export function testCardExpiration(yearOffset = 1) {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  return `${month}/${String(now.getFullYear() + yearOffset).slice(-2)}`;
}

export const payWithCard = () => screen.findByRole("button", { name: /^Pagar S\//i });

export async function enterTestCard(user, overrides = {}) {
  const card = { ...simulatedCard, expiration: testCardExpiration(), ...overrides };
  const fields = [
    ["Número de tarjeta", card.number],
    ["Nombre del titular", card.holder],
    ["Vencimiento", card.expiration],
    ["Código de seguridad (CVV)", card.securityCode],
  ];
  for (const [label, value] of fields) {
    const input = await screen.findByLabelText(label, { exact: true });
    if (input.value) await user.clear(input);
    await user.type(input, value);
  }
}

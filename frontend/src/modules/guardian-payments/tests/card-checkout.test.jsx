import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, reply, signIn } from "../../../../tests/support/backend.js";
import { PaymentCheckout } from "../payment-checkout";
import { complete, obligation, order, receipt, sibling } from "./fixtures";
import { enterTestCard, payWithCard, testCardExpiration } from "./card-form-support";

const CONFIRM_PATH = "/api/pagos/ordenes/10/confirmar-tarjeta";
const ORDER_PATH = "/api/pagos/ordenes/10";
const checkout = (props = {}) => render(
  <PaymentCheckout obligations={[obligation()]} initialOrder={order()} onClose={vi.fn()} onSettled={vi.fn()} {...props} />,
);

beforeEach(() => signIn({ role: "APODERADO", permissions: ["PAGOS_LEER"] }));
afterEach(() => vi.useRealTimers());

test("los errores se asocian a los campos y una tarjeta fuera del catálogo no se envía", async () => {
  const calls = fakeBackend({});
  const user = userEvent.setup();
  checkout();

  await enterTestCard(user, { number: "1111 1111 1111 1111", holder: "123", expiration: "13/30", securityCode: "12" });
  await user.tab();
  expect((await payWithCard()).disabled).toBe(true);
  const errors = [
    ["Número de tarjeta", "Revisa el número e ingresa una tarjeta de prueba válida."],
    ["Nombre del titular", "Ingresa el nombre que aparece en la tarjeta de prueba."],
    ["Vencimiento", "Ingresa una fecha válida en formato MM/AA."],
    ["Código de seguridad (CVV)", "Revisa el código de seguridad de la tarjeta."],
  ];
  for (const [label, error] of errors) {
    const field = screen.getByLabelText(label, { exact: true });
    expect(field.getAttribute("aria-invalid")).toBe("true");
    const descriptionIds = field.getAttribute("aria-describedby")?.split(/\s+/) ?? [];
    expect(descriptionIds.some((id) => document.getElementById(id)?.textContent === error)).toBe(true);
  }
  const invalidNumber = screen.getByLabelText("Número de tarjeta", { exact: true });
  fireEvent.submit(invalidNumber.form);
  expect(document.activeElement).toBe(invalidNumber);
  expect(calls.some((call) => call.method === "POST")).toBe(false);

  await enterTestCard(user, { expiration: testCardExpiration(-1) });
  await user.tab();
  expect((await payWithCard()).disabled).toBe(true);
  expect(screen.getByText(/tarjeta está vencida/i)).toBeTruthy();

  await enterTestCard(user, { number: "4242-4242-4242-4242", expiration: testCardExpiration(0).replace("/", "") });
  await user.tab();
  expect((await payWithCard()).disabled).toBe(false);
  const number = screen.getByLabelText("Número de tarjeta", { exact: true });
  expect(number.value).toBe("4242 4242 4242 4242");
  expect(screen.getByLabelText("Vencimiento", { exact: true }).value).toBe(testCardExpiration(0));
  expect(Array.from(new FormData(number.form))).toEqual([]);
});

test("filtra los caracteres al escribir y pegar sin impedir que se confirme una tarjeta válida", async () => {
  const calls = fakeBackend({
    [`POST ${CONFIRM_PATH}`]: complete(),
    [`GET ${ORDER_PATH}/comprobante`]: receipt(),
  });
  const user = userEvent.setup();
  checkout();

  const number = screen.getByLabelText("Número de tarjeta");
  const holder = screen.getByLabelText("Nombre del titular");
  const expiry = screen.getByLabelText("Vencimiento");
  const cvv = screen.getByLabelText("Código de seguridad (CVV)");

  await user.type(number, "a42B42");
  expect(number.value).toBe("4242");
  await user.clear(number);
  await user.paste("42x42-4242 4242 4242!7");
  await user.type(holder, "Ána9 M.! D’Ávila-Ñúñez2");
  expect(number.value).toBe("4242 4242 4242 4242");
  expect(holder.value).toBe("Ána M. D’Ávila-Ñúñez");

  await user.type(expiry, "1x3z30");
  expect(expiry.value).toBe("13/30");
  await user.type(cvv, "1x2");
  expect(cvv.value).toBe("12");
  expect((await payWithCard()).disabled).toBe(true);
  expect(screen.getByText("Ingresa una fecha válida en formato MM/AA.")).toBeTruthy();

  const expiration = testCardExpiration();
  const [month, year] = expiration.split("/");
  await user.clear(expiry);
  await user.paste(`${month}x/${year}!7`);
  expect(expiry.value).toBe(expiration);
  await user.clear(cvv);
  await user.paste("a12x34!5");
  expect(cvv.value).toBe("1234");
  expect((await payWithCard()).disabled).toBe(true);
  expect(calls.some((call) => call.path === CONFIRM_PATH)).toBe(false);

  await user.clear(cvv);
  await user.type(cvv, "1x2y3");
  await user.click(await payWithCard());
  expect(await screen.findByText("Referencias: REC-000022")).toBeTruthy();
  expect(calls.find((call) => call.path === CONFIRM_PATH).body).toEqual({ tarjetaPrueba: "TP-001" });
});

test("presenta el estudiante y el concepto de cada cargo de la orden con el total en soles", async () => {
  const extras = obligation(2, {
    idAlumno: sibling.id,
    alumno: sibling.nombreCompleto,
    codigoAlumno: sibling.codigoAlumno,
    concepto: "Materiales de arte",
    numeroCuota: null,
    montoTotal: 70,
    saldo: 70,
  });
  const pending = order();
  const mixedOrder = order({
    montoBase: 420,
    montoTotal: 420,
    items: [
      { ...pending.items[0], tipo: "PENSION", mes: 3, anioAcademico: 2026 },
      {
        idCompromiso: 2, codigoCompromiso: extras.codigoCompromiso, alumno: extras.alumno,
        concepto: extras.concepto, numeroCuota: null, monto: 70, tipo: "OTRO", referenciaCobro: "ARTE-2026",
      },
    ],
  });
  checkout({ obligations: [obligation(), extras], initialOrder: mixedOrder });

  const dialog = screen.getByRole("dialog");
  for (const text of ["Lucía Flores", "Luis Flores", "Pensión de marzo", "Marzo 2026", "Materiales de arte", "ARTE-2026"]) {
    expect(dialog.textContent).toContain(text);
  }
  expect((await payWithCard()).textContent).toMatch(/Pagar S\/\s*420[.,]00/);
});

test("un CVV de formato válido pero incorrecto genera el rechazo simulado sin enviar el código", async () => {
  const decline = "El código de seguridad no coincide. Revísalo e inténtalo de nuevo.";
  const calls = fakeBackend({ [`POST ${CONFIRM_PATH}`]: order({ estado: "RECHAZADO", mensaje: decline }) });
  const user = userEvent.setup();
  checkout();

  await enterTestCard(user, { securityCode: "999" });
  await user.click(await payWithCard());

  expect(await screen.findByText(decline)).toBeTruthy();
  expect(calls.find((call) => call.path === CONFIRM_PATH).body).toEqual({ tarjetaPrueba: "TP-005" });
  expect(calls.some((call) => call.path.endsWith("/comprobante"))).toBe(false);
});

test("la tarjeta ficticia de quince dígitos requiere un código de seguridad de cuatro caracteres", async () => {
  const calls = fakeBackend({
    [`POST ${CONFIRM_PATH}`]: complete(),
    [`GET ${ORDER_PATH}/comprobante`]: receipt(),
  });
  const user = userEvent.setup();
  checkout();

  await enterTestCard(user, { number: "3782 822463 10005", holder: "Alicia D'Ávila-García" });
  await user.tab();
  expect((await payWithCard()).disabled).toBe(true);
  expect(screen.getByText("Revisa el código de seguridad de la tarjeta.")).toBeTruthy();

  await user.type(screen.getByLabelText("Código de seguridad (CVV)"), "4");
  expect((await payWithCard()).disabled).toBe(false);
  await user.click(await payWithCard());
  expect(await screen.findByText("Referencias: REC-000022")).toBeTruthy();
  expect(calls.find((call) => call.path === CONFIRM_PATH).body).toEqual({ tarjetaPrueba: "TP-001" });
});

test("solo permite volver a pagar tras comprobar que la misma operación sigue pendiente", async () => {
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
  let attempts = 0;
  let statusAvailable = false;
  const calls = fakeBackend({
    [`POST ${CONFIRM_PATH}`]: () => ++attempts === 1
      ? reply(503, { code: "PAGO_SERVICIO_NO_DISPONIBLE", message: "Servicio temporalmente no disponible." })
      : complete(),
    [`GET ${ORDER_PATH}`]: () => statusAvailable ? order() : reply(503, { message: "No hay respuesta." }),
    [`GET ${ORDER_PATH}/comprobante`]: receipt(),
  });
  const user = userEvent.setup();
  checkout();

  await enterTestCard(user);
  await user.click(await payWithCard());
  expect(await screen.findByText(/todavía estamos verificando tu pago/i)).toBeTruthy();
  expect(screen.getByText("Servicio temporalmente no disponible.")).toBeTruthy();
  expect(screen.queryByLabelText("Número de tarjeta")).toBeNull();
  expect(screen.queryByRole("button", { name: "Continuar con tarjeta" })).toBeNull();
  await user.click(screen.getByRole("button", { name: "Consultar estado" }));
  expect(await screen.findByText("No pudimos consultar el estado. Inténtalo nuevamente.")).toBeTruthy();
  expect(screen.queryByLabelText("Número de tarjeta")).toBeNull();
  statusAvailable = true;
  await user.click(screen.getByRole("button", { name: "Consultar estado" }));
  expect((await payWithCard()).disabled).toBe(true);

  await enterTestCard(user);
  await user.click(await payWithCard());
  expect(await screen.findByText("Referencias: REC-000022")).toBeTruthy();
  expect(calls.filter((call) => call.method === "POST").map((call) => call.path)).toEqual([CONFIRM_PATH, CONFIRM_PATH]);
  expect(calls.some((call) => call.method === "GET" && call.path === ORDER_PATH)).toBe(true);
});

test("una referencia rechazada por el servidor vuelve al campo de número para corregirla", async () => {
  const invalid = "La tarjeta de prueba no está disponible.";
  const calls = fakeBackend({
    [`POST ${CONFIRM_PATH}`]: reply(422, {
      code: "TARJETA_PRUEBA_INVALIDA",
      message: invalid,
      fieldErrors: [{ field: "tarjetaPrueba", message: invalid }],
    }),
  });
  const user = userEvent.setup();
  checkout();

  await enterTestCard(user);
  await user.click(await payWithCard());
  expect(await screen.findByText(invalid)).toBeTruthy();
  const number = screen.getByLabelText("Número de tarjeta", { exact: true });
  expect(document.activeElement).toBe(number);
  expect(number.getAttribute("aria-invalid")).toBe("true");
  expect(calls.some((call) => call.path === ORDER_PATH)).toBe(false);
  await enterTestCard(user);
  expect((await payWithCard()).disabled).toBe(false);
});

test("un total inválido mantiene el formulario de tarjeta bloqueado", async () => {
  const calls = fakeBackend({});
  checkout({ initialOrder: order({ items: [] }) });

  expect(screen.getByText("No pudimos validar el importe. Vuelve al estado de cuenta y revisa tu selección.")).toBeTruthy();
  expect((await payWithCard()).disabled).toBe(true);
  expect(screen.getByLabelText("Número de tarjeta").disabled).toBe(true);
  expect(calls.some((call) => call.method === "POST")).toBe(false);
});

test("un doble clic en Pagar envía una sola confirmación", async () => {
  let finish;
  const calls = fakeBackend({
    [`POST ${CONFIRM_PATH}`]: () => new Promise((resolve) => { finish = resolve; }),
    [`GET ${ORDER_PATH}/comprobante`]: receipt(),
  });
  const user = userEvent.setup();
  checkout();

  await enterTestCard(user);
  await user.dblClick(await payWithCard());
  expect(calls.filter((call) => call.path === CONFIRM_PATH)).toHaveLength(1);
  await act(async () => { finish(complete()); });
  expect(await screen.findByText("Referencias: REC-000022")).toBeTruthy();
});

test("un sondeo anterior no puede volver al formulario después de iniciar la verificación de tarjeta", async () => {
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
  let resolvePoll;
  const calls = fakeBackend({
    [`GET ${ORDER_PATH}`]: () => new Promise((resolve) => { resolvePoll = resolve; }),
    [`POST ${CONFIRM_PATH}`]: order({ estado: "REQUIERE_AUTENTICACION" }),
  });
  const user = userEvent.setup();
  checkout();

  await enterTestCard(user, { number: "4000 0025 0000 3155" });
  await act(async () => vi.advanceTimersByTimeAsync(2000));
  await user.click(await payWithCard());
  expect(await screen.findByRole("button", { name: "Completar autenticación" })).toBeTruthy();

  await act(async () => { resolvePoll(order()); });
  expect(screen.getByRole("button", { name: "Completar autenticación" })).toBeTruthy();
  expect(screen.queryByLabelText("Número de tarjeta")).toBeNull();
  expect(calls.find((call) => call.path === CONFIRM_PATH).body).toEqual({ tarjetaPrueba: "TP-009" });
});

test("repite la solicitud de inicio con la misma clave si se pierde la conexión", async () => {
  let attempts = 0;
  fakeBackend({
    "POST /api/pagos/iniciar": () => {
      if (++attempts === 1) throw new TypeError("Failed to fetch");
      return order();
    },
  });
  const user = userEvent.setup();
  checkout({ initialOrder: undefined });

  await user.click(screen.getByRole("button", { name: "Continuar con tarjeta" }));
  expect(await screen.findByText("No pudimos preparar la operación. Revisa tu conexión e inténtalo nuevamente.")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Continuar con tarjeta" }));
  expect(await screen.findByLabelText("Número de tarjeta")).toBeTruthy();

  const keys = vi.mocked(fetch).mock.calls
    .filter(([, options]) => options.method === "POST")
    .map(([, options]) => options.headers.get("Idempotency-Key"));
  expect(keys).toHaveLength(2);
  expect(keys[0]).toBeTruthy();
  expect(keys[1]).toBe(keys[0]);
});

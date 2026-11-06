import { expect, test } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PersonFields } from "../person-fields";

// HTML compila pattern con v y exige coincidencia de todo el valor.
const compilePattern = (input) => new RegExp(`^(?:${input.pattern})$`, "v");

test.each(["PASAPORTE", "CARNET_EXTRANJERIA"])("Documento %s conserva caracteres y límites con pattern válido en v", async (type) => {
  render(<PersonFields />);
  await userEvent.setup().selectOptions(screen.getByLabelText(/Tipo de documento/), type);
  const input = screen.getByLabelText(/Número de documento/);
  const pattern = compilePattern(input);
  for (const value of ["ABC123", "AB-123", "-ABC12", "A".repeat(20)]) {
    expect(pattern.test(value), value).toBe(true);
    fireEvent.change(input, { target: { value } });
    expect(input.validity.patternMismatch, value).toBe(false);
    expect(input.checkValidity(), value).toBe(true);
  }
  for (const value of ["ABC12", "A".repeat(21), "AB/123", "AB_123", "ÑBC123", "AB 123"]) {
    expect(pattern.test(value), value).toBe(false);
    fireEvent.change(input, { target: { value } });
    expect(input.validity.patternMismatch, value).toBe(true);
    expect(input.checkValidity(), value).toBe(false);
  }
  // Preserva exactamente el conjunto ASCII original, incluido el guion literal.
  const previous = /^(?:[A-Za-z0-9-]{6,20})$/;
  for (let code = 0; code < 128; code++) {
    const value = `ABC12${String.fromCharCode(code)}`;
    expect(pattern.test(value), JSON.stringify(value)).toBe(previous.test(value));
  }
});

test("Teléfono conserva dígitos, +, paréntesis, espacio y guion; sigue siendo opcional", () => {
  render(<PersonFields />);
  const input = screen.getByLabelText("Teléfono");
  const pattern = compilePattern(input);
  expect(input.required).toBe(false);
  expect(input.checkValidity()).toBe(true);
  for (const value of ["1234567", "+51 (01) 555-1234", "1".repeat(20), " ".repeat(7)]) {
    expect(pattern.test(value), value).toBe(true);
    fireEvent.change(input, { target: { value } });
    expect(input.checkValidity(), value).toBe(true);
  }
  for (const value of ["123456", "1".repeat(21), "123a456", "123/456", "123_456", "[123456]", "12|3456"]) {
    expect(pattern.test(value), value).toBe(false);
    fireEvent.change(input, { target: { value } });
    expect(input.validity.patternMismatch, value).toBe(true);
    expect(input.checkValidity(), value).toBe(false);
  }
  const previous = /^(?:[0-9+() -]{7,20})$/;
  for (let code = 0; code < 128; code++) {
    const value = `123456${String.fromCharCode(code)}`;
    expect(pattern.test(value), JSON.stringify(value)).toBe(previous.test(value));
  }
  fireEvent.change(input, { target: { value: "" } });
  expect(input.checkValidity()).toBe(true);
});

test("DNI conserva la validación de exactamente ocho dígitos al cambiar de tipo", async () => {
  const { container } = render(<PersonFields />);
  for (const input of container.querySelectorAll("input[pattern]")) {
    expect(() => compilePattern(input)).not.toThrow();
  }
  const user = userEvent.setup();
  const select = screen.getByLabelText(/Tipo de documento/);
  await user.selectOptions(select, "PASAPORTE");
  await user.selectOptions(select, "DNI");
  const input = screen.getByLabelText(/Número de documento/);
  const pattern = compilePattern(input);
  expect(pattern.test("12345678")).toBe(true);
  for (const value of ["1234567", "123456789", "AB123456", "1234-678"]) {
    expect(pattern.test(value)).toBe(false);
  }
});

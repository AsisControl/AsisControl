export function passwordError(value: string, username = ""): string {
  return value.length < 8 ||
    value.length > 64 ||
    new TextEncoder().encode(value).length > 72 ||
    /[\s\p{Cc}]/u.test(value) ||
    !/\p{Lu}/u.test(value) ||
    !/\p{Ll}/u.test(value) ||
    !/\p{Nd}/u.test(value) ||
    value.toLowerCase() === username.toLowerCase()
    ? "Usa de 8 a 64 caracteres, mayúscula, minúscula y número, sin espacios. No uses tu usuario (máximo 72 bytes UTF-8)."
    : "";
}
export const passwordHint =
  "De 8 a 64 caracteres, mayúscula, minúscula y número, sin espacios. Máximo 72 bytes UTF-8.";

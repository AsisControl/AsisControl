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
/** Requisitos visibles mientras se escribe; passwordError sigue siendo la validación final. */
export const passwordRules: { label: string; isMet: (value: string) => boolean }[] = [
  { label: "De 8 a 64 caracteres", isMet: (value) => value.length >= 8 && value.length <= 64 },
  { label: "Una letra mayúscula", isMet: (value) => /\p{Lu}/u.test(value) },
  { label: "Una letra minúscula", isMet: (value) => /\p{Ll}/u.test(value) },
  { label: "Un número", isMet: (value) => /\p{Nd}/u.test(value) },
  { label: "Sin espacios", isMet: (value) => value !== "" && !/[\s\p{Cc}]/u.test(value) },
];
export const passwordHint =
  "De 8 a 64 caracteres, mayúscula, minúscula y número, sin espacios. Máximo 72 bytes UTF-8.";

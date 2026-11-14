/** UUID seguro también en HTTP de una red local, donde randomUUID puede no existir. */
export function paymentRequestKey(): string {
  const source = globalThis.crypto;
  if (source?.randomUUID) return source.randomUUID();
  if (!source?.getRandomValues) {
    throw new Error("No pudimos preparar un pago seguro en este navegador. Usa un navegador actualizado e inténtalo nuevamente.");
  }
  const bytes = source.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

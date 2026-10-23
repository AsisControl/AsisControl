import { afterEach } from "vitest";

// Solo las pantallas usan Testing Library; las pruebas de lógica pura corren en node.
if (typeof document !== "undefined") {
  const { cleanup } = await import("@testing-library/react");
  afterEach(cleanup);
}

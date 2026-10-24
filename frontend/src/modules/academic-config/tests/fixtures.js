import { fakeBackend, page } from "../../../../tests/support/backend.js";

export const period = (overrides = {}) => ({
  id: 11,
  nombre: "Período I",
  orden: 1,
  fechaInicio: "2026-03-01",
  fechaFin: "2026-05-31",
  estado: "ACTIVO",
  version: 1,
  ...overrides,
});

export const grade = (overrides = {}) => ({
  id: 21,
  numero: 1,
  capacidadDefault: 35,
  activo: true,
  version: 1,
  ...overrides,
});

export const section = (overrides = {}) => ({
  id: 31,
  nombre: "A",
  grado: 1,
  anioAcademico: 2026,
  capacidadMaxima: 35,
  ocupacion: 30,
  vacantes: 5,
  activo: true,
  version: 2,
  ...overrides,
});

export const year = (overrides = {}) => ({
  id: 2026,
  anio: 2026,
  fechaInicio: "2026-03-01",
  fechaFin: "2026-12-20",
  estado: "ACTIVO",
  version: 4,
  admisionAbierta: false,
  periodos: [
    period(),
    period({ id: 12, nombre: "Período II", orden: 2, fechaInicio: "2026-06-01", fechaFin: "2026-08-31", estado: "PLANIFICADO" }),
    period({ id: 13, nombre: "Período 0", orden: 0, fechaInicio: "2026-03-01", fechaFin: "2026-03-02", estado: "CERRADO" }),
  ],
  grados: [grade(), grade({ id: 22, numero: 2, capacidadDefault: 30 })],
  secciones: [
    section(),
    section({ id: 32, nombre: "B", ocupacion: 36, vacantes: -1 }),
    section({ id: 33, nombre: "A", grado: 2, ocupacion: 0, vacantes: 30, capacidadMaxima: 30, activo: false }),
  ],
  ...overrides,
});

const BASE = "/api/configuracion-academica";

/**
 * Backend de configuración académica con estado: los años se guardan en
 * memoria para que cada recarga devuelva lo último que "guardó" la pantalla.
 */
export function academicBackend(years, routes = {}) {
  const store = Object.fromEntries(years.map((value) => [value.anio, value]));
  const calls = fakeBackend({
    [`GET ${BASE}/anios`]: () => page(Object.values(store)),
    [`GET ${BASE}/anios/:anio`]: ({ path }) => store[path.split("/").at(-1)],
    ...routes,
  });
  return { calls, store };
}

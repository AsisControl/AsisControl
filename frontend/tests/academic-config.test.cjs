/* eslint-disable @typescript-eslint/no-require-imports -- Node test harness loads isolated TypeScript modules. */
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");
function load(relative) {
  const file = path.join(__dirname, "..", relative);
  const code = ts.transpileModule(fs.readFileSync(file, "utf8"), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText;
  const loadedModule = { exports: {} };
  vm.runInNewContext(code, {
    module: loadedModule,
    exports: loadedModule.exports,
  });
  return loadedModule.exports;
}
const v = load("src/modules/academic-config/lib/validation.ts");
// Los objetos nacen en otro contexto de vm: se comparan por claves, no por prototipo.
const empty = (value) => assert.equal(Object.keys(value).length, 0);
const year = { fechaInicio: "2026-03-01", fechaFin: "2026-12-20" };
const periods = [
  { nombre: "Período I", orden: 1, fechaInicio: "2026-03-01", fechaFin: "2026-05-31" },
  { nombre: "Período II", orden: 3, fechaInicio: "2026-06-15", fechaFin: "2026-08-31" },
];
const valid = {
  nombre: "Período III",
  fechaInicio: "2026-09-01",
  fechaFin: "2026-12-15",
};
test("El orden es max(orden) + 1 y arranca en 1", () => {
  assert.equal(v.nextPeriodOrder([]), 1);
  assert.equal(v.nextPeriodOrder(periods), 4);
});
test("Nombre obligatorio, máximo 100 y no repetido", () => {
  assert.notEqual(v.validatePeriodName("   ", periods), "");
  assert.equal(v.validatePeriodName("a".repeat(100), periods), "");
  assert.notEqual(v.validatePeriodName("a".repeat(101), periods), "");
  assert.notEqual(v.validatePeriodName(" período i ", periods), "");
  assert.equal(v.validatePeriodName("Período III", periods), "");
});
test("Fecha fin debe ser mayor o igual al inicio", () => {
  const same = v.validatePeriodDates("2026-09-01", "2026-09-01", year, periods);
  empty(same);
  const inverted = v.validatePeriodDates("2026-09-02", "2026-09-01", year, periods);
  assert.ok(inverted.fechaFin);
});
test("Las fechas son obligatorias", () => {
  const errors = v.validatePeriodDates("", "", year, periods);
  assert.ok(errors.fechaInicio && errors.fechaFin);
});
test("Las fechas deben estar dentro del año", () => {
  assert.ok(v.validatePeriodDates("2026-02-28", "2026-09-10", year, []).fechaInicio);
  assert.ok(v.validatePeriodDates("2026-09-01", "2026-12-21", year, []).fechaFin);
  empty(v.validatePeriodDates("2026-03-01", "2026-12-20", year, []));
});
test("No se solapa con otros períodos, ni compartiendo un día", () => {
  assert.ok(v.validatePeriodDates("2026-05-31", "2026-06-10", year, periods).fechaInicio);
  assert.ok(v.validatePeriodDates("2026-04-01", "2026-04-02", year, periods).fechaInicio);
  assert.ok(v.validatePeriodDates("2026-05-01", "2026-09-30", year, periods).fechaInicio);
  empty(v.validatePeriodDates("2026-06-01", "2026-06-14", year, periods));
});
const days = [{ fecha: "2026-04-02" }, { fecha: "2026-07-28" }];
test("Día no lectivo: la fecha es obligatoria", () => {
  assert.notEqual(v.validateNonTeachingDate("", year, days), "");
});
test("Día no lectivo: la fecha debe estar dentro del año", () => {
  assert.notEqual(v.validateNonTeachingDate("2026-02-28", year, days), "");
  assert.notEqual(v.validateNonTeachingDate("2026-12-21", year, days), "");
  assert.equal(v.validateNonTeachingDate("2026-03-01", year, days), "");
  assert.equal(v.validateNonTeachingDate("2026-12-20", year, days), "");
});
test("Día no lectivo: no admite fechas repetidas", () => {
  assert.notEqual(v.validateNonTeachingDate("2026-04-02", year, days), "");
  assert.equal(v.validateNonTeachingDate("2026-04-03", year, days), "");
  assert.equal(v.validateNonTeachingDate("2026-04-02", year, []), "");
});
test("Día no lectivo: descripción opcional, máximo 255", () => {
  assert.equal(v.validateNonTeachingDescription(""), "");
  assert.equal(v.validateNonTeachingDescription("a".repeat(255)), "");
  assert.notEqual(v.validateNonTeachingDescription("a".repeat(256)), "");
});
test("Día no lectivo: la descripción se mide sin espacios laterales", () => {
  assert.equal(
    v.validateNonTeachingDescription(` ${"a".repeat(255)} `),
    "",
  );
  assert.equal(v.validateNonTeachingDescription("   "), "");
});
test("Día no lectivo: la lista vacía no genera conflictos de fecha", () => {
  const ok = { fecha: "2026-04-02", descripcion: "" };
  assert.equal(v.hasErrors(v.validateNonTeachingDay(ok, year, [])), false);
  assert.ok(v.validateNonTeachingDay(ok, year, days).fecha);
});
test("validateNonTeachingDay reúne los errores", () => {
  const ok = { fecha: "2026-05-01", descripcion: "Día del Trabajo" };
  assert.equal(v.hasErrors(v.validateNonTeachingDay(ok, year, days)), false);
  const errors = v.validateNonTeachingDay(
    { fecha: "", descripcion: "a".repeat(256) },
    year,
    days,
  );
  assert.ok(errors.fecha && errors.descripcion);
  assert.equal(v.hasErrors(errors), true);
});
test("validatePeriod reúne los errores y hasErrors los detecta", () => {
  assert.equal(v.hasErrors(v.validatePeriod(valid, year, periods)), false);
  const errors = v.validatePeriod(
    { nombre: "", fechaInicio: "", fechaFin: "" },
    year,
    periods,
  );
  assert.ok(errors.nombre && errors.fechaInicio && errors.fechaFin);
  assert.equal(v.hasErrors(errors), true);
});
const m = load("src/modules/academic-config/lib/section-metrics.ts");
test("Porcentaje de ocupación redondeado y sin tope", () => {
  assert.equal(m.occupancyPercent({ capacidadMaxima: 35, ocupacion: 33 }), 94);
  assert.equal(m.occupancyPercent({ capacidadMaxima: 35, ocupacion: 35 }), 100);
  assert.equal(m.occupancyPercent({ capacidadMaxima: 35, ocupacion: 0 }), 0);
  assert.equal(m.occupancyPercent({ capacidadMaxima: 35, ocupacion: 36 }), 103);
});
test("Capacidad 0 o datos faltantes no dan NaN ni dividen por cero", () => {
  assert.equal(m.occupancyPercent({ capacidadMaxima: 0, ocupacion: 5 }), 0);
  assert.equal(m.occupancyPercent({ capacidadMaxima: undefined, ocupacion: 5 }), 0);
  assert.equal(m.occupancyPercent({ capacidadMaxima: 30, ocupacion: null }), 0);
  assert.equal(m.occupancyPercent({ capacidadMaxima: NaN, ocupacion: NaN }), 0);
  assert.equal(m.occupancyBarWidth({ capacidadMaxima: 0, ocupacion: 0 }), 0);
});
test("El ancho de la barra se limita a 100", () => {
  assert.equal(m.occupancyBarWidth({ capacidadMaxima: 35, ocupacion: 36 }), 100);
  assert.equal(m.occupancyBarWidth({ capacidadMaxima: 10, ocupacion: 5 }), 50);
});
test("Sobre capacidad solo cuando ocupación > capacidad", () => {
  assert.equal(m.isOverCapacity({ capacidadMaxima: 35, ocupacion: 36 }), true);
  assert.equal(m.isOverCapacity({ capacidadMaxima: 35, ocupacion: 35 }), false);
  assert.equal(m.isOverCapacity({ capacidadMaxima: 0, ocupacion: 1 }), true);
  assert.equal(m.isOverCapacity({ capacidadMaxima: 0, ocupacion: 0 }), false);
});
test("Vacantes: valor del backend, nunca negativas, con respaldo calculado", () => {
  assert.equal(m.vacancies({ capacidadMaxima: 35, ocupacion: 33, vacantes: 2 }), 2);
  assert.equal(m.vacancies({ capacidadMaxima: 35, ocupacion: 36, vacantes: -1 }), 0);
  assert.equal(m.vacancies({ capacidadMaxima: 35, ocupacion: 30 }), 5);
  assert.equal(m.vacancies({ capacidadMaxima: 0, ocupacion: 3 }), 0);
});

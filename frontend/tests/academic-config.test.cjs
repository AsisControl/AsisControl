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

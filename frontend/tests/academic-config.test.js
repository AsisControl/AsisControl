// @vitest-environment node
import { test } from "vitest";
import assert from "node:assert/strict";
import * as v from "@/modules/academic-config/lib/validation";
import * as m from "@/modules/academic-config/lib/section-metrics";
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
const sections = [
  { id: 1, nombre: "A", grado: 1 },
  { id: 2, nombre: "B", grado: 1 },
  { id: 3, nombre: "A", grado: 2 },
];
test("Sección: nombre obligatorio y máximo 20", () => {
  assert.notEqual(v.validateSectionName("  ", 1, sections), "");
  assert.equal(v.validateSectionName("a".repeat(20), 1, sections), "");
  assert.notEqual(v.validateSectionName("a".repeat(21), 1, sections), "");
});
test("Sección: no se repite en el mismo grado, sin distinguir mayúsculas", () => {
  assert.notEqual(v.validateSectionName(" a ", 1, sections), "");
  assert.equal(v.validateSectionName("C", 1, sections), "");
  assert.equal(v.validateSectionName("B", 2, sections), "");
  assert.equal(v.validateSectionName("a", 1, sections, 1), "");
});
test("Sección: capacidad entera de 1 a 100", () => {
  assert.notEqual(v.validateSectionCapacity(""), "");
  assert.notEqual(v.validateSectionCapacity("0"), "");
  assert.notEqual(v.validateSectionCapacity("101"), "");
  assert.notEqual(v.validateSectionCapacity("3.5"), "");
  assert.notEqual(v.validateSectionCapacity("-4"), "");
  assert.equal(v.validateSectionCapacity("1"), "");
  assert.equal(v.validateSectionCapacity("100"), "");
});
test("Sección: la capacidad no baja de los matriculados", () => {
  assert.match(v.validateSectionCapacity("20", 25), /25 alumnos matriculados/);
  assert.equal(v.validateSectionCapacity("25", 25), "");
  assert.equal(v.validateSectionCapacity("5"), "");
});
test("Sección: el grado debe estar habilitado", () => {
  assert.notEqual(v.validateSectionGrade(null, [1, 2]), "");
  assert.notEqual(v.validateSectionGrade(3, [1, 2]), "");
  assert.equal(v.validateSectionGrade(2, [1, 2]), "");
});
test("Sección: motivo obligatorio solo si se exige, máximo 700", () => {
  assert.notEqual(v.validateSectionReason("  ", true), "");
  assert.equal(v.validateSectionReason("", false), "");
  assert.equal(v.validateSectionReason("a".repeat(700), true), "");
  assert.notEqual(v.validateSectionReason("a".repeat(701), false), "");
});
test("validateSection reúne los errores", () => {
  const context = {
    sections,
    enabledGrades: [1, 2],
    reasonRequired: false,
  };
  const ok = { nombre: "C", grado: 1, capacidad: "35", motivo: "" };
  assert.equal(v.hasErrors(v.validateSection(ok, context)), false);
  const bad = v.validateSection(
    { nombre: "", grado: null, capacidad: "0", motivo: "" },
    { ...context, reasonRequired: true },
  );
  assert.ok(bad.nombre && bad.grado && bad.capacidad && bad.motivo);
  const edit = v.validateSection(
    { nombre: "A", grado: 1, capacidad: "10", motivo: "x" },
    { ...context, reasonRequired: true, editing: { id: 1, enrolled: 12 } },
  );
  assert.ok(edit.capacidad);
  assert.equal(edit.nombre, undefined);
});
test("Grado: solo se ofrecen los números 1 a 5 que no existen", () => {
  assert.deepEqual([...v.availableGradeNumbers([])], [1, 2, 3, 4, 5]);
  assert.deepEqual([...v.availableGradeNumbers([1, 3])], [2, 4, 5]);
  assert.deepEqual([...v.availableGradeNumbers([1, 2, 3, 4, 5])], []);
});
test("Grado: número entre 1 y 5 y sin repetir", () => {
  assert.notEqual(v.validateGradeNumber(null, []), "");
  assert.notEqual(v.validateGradeNumber(0, []), "");
  assert.notEqual(v.validateGradeNumber(6, []), "");
  assert.notEqual(v.validateGradeNumber(2.5, []), "");
  assert.notEqual(v.validateGradeNumber(2, [1, 2]), "");
  assert.equal(v.validateGradeNumber(1, []), "");
  assert.equal(v.validateGradeNumber(5, [1, 2]), "");
});
test("Grado: capacidad entera de 1 a 100", () => {
  assert.notEqual(v.validateGradeCapacity(""), "");
  assert.notEqual(v.validateGradeCapacity("0"), "");
  assert.notEqual(v.validateGradeCapacity("101"), "");
  assert.notEqual(v.validateGradeCapacity("3.5"), "");
  assert.notEqual(v.validateGradeCapacity("-4"), "");
  assert.equal(v.validateGradeCapacity("1"), "");
  assert.equal(v.validateGradeCapacity(" 100 "), "");
});
test("Grado: motivo obligatorio si se exige, máximo 700", () => {
  assert.notEqual(v.validateGradeReason("  ", true), "");
  assert.equal(v.validateGradeReason("", false), "");
  assert.equal(v.validateGradeReason("a".repeat(700), true), "");
  assert.notEqual(v.validateGradeReason("a".repeat(701), false), "");
});
test("validateGrade: crear no pide motivo; editar sí", () => {
  const create = { existing: [1], creating: true };
  assert.equal(
    v.hasErrors(v.validateGrade({ numero: 2, capacidad: "35", motivo: "" }, create)),
    false,
  );
  const bad = v.validateGrade({ numero: 1, capacidad: "0", motivo: "" }, create);
  assert.ok(bad.numero && bad.capacidad);
  assert.equal(bad.motivo, undefined);
  const edit = { existing: [1], creating: false };
  const noReason = v.validateGrade({ numero: null, capacidad: "30", motivo: " " }, edit);
  assert.ok(noReason.motivo);
  assert.equal(noReason.numero, undefined);
  assert.equal(
    v.hasErrors(v.validateGrade({ numero: null, capacidad: "30", motivo: "ok" }, edit)),
    false,
  );
});

// @vitest-environment node
import { test } from "vitest";
import assert from "node:assert/strict";
import * as v from "@/modules/schedules/lib/validation";
import { canAccess } from "@/core/navigation/access-policy";
const classrooms = [
  { id: 1, codigo: "AULA-201" },
  { id: 2, codigo: "LAB 01" },
];
const valid = {
  codigo: "AULA-305",
  nombre: "Aula 305",
  tipoAula: "AULA_REGULAR",
  capacidad: "30",
  ubicacion: "",
};
test("El código se normaliza como en el servidor", () => {
  assert.equal(v.normalizeClassroomCode("  lab   01 "), "LAB 01");
});
test("Código obligatorio, máximo 30 y sin repetir salvo el propio", () => {
  assert.notEqual(v.validateClassroomCode("   ", classrooms), "");
  assert.equal(v.validateClassroomCode("a".repeat(30), classrooms), "");
  assert.notEqual(v.validateClassroomCode("a".repeat(31), classrooms), "");
  assert.notEqual(v.validateClassroomCode(" aula-201 ", classrooms), "");
  assert.notEqual(v.validateClassroomCode("lab  01", classrooms), "");
  assert.equal(v.validateClassroomCode("AULA-201", classrooms, 1), "");
});
test("Nombre obligatorio y máximo 100", () => {
  assert.notEqual(v.validateClassroomName(" "), "");
  assert.equal(v.validateClassroomName("a".repeat(100)), "");
  assert.notEqual(v.validateClassroomName("a".repeat(101)), "");
});
test("Capacidad entera de 1 a 300", () => {
  assert.notEqual(v.validateClassroomCapacity(""), "");
  assert.notEqual(v.validateClassroomCapacity("2.5"), "");
  assert.notEqual(v.validateClassroomCapacity("-3"), "");
  assert.notEqual(v.validateClassroomCapacity("0"), "");
  assert.equal(v.validateClassroomCapacity("1"), "");
  assert.equal(v.validateClassroomCapacity(" 300 "), "");
  assert.notEqual(v.validateClassroomCapacity("301"), "");
});
test("Tipo de aula obligatorio y del catálogo del servidor", () => {
  assert.notEqual(v.validateClassroomType(""), "");
  assert.notEqual(v.validateClassroomType("AUDITORIO"), "");
  assert.equal(v.validateClassroomType("SALA_USOS_MULTIPLES"), "");
});
test("Ubicación opcional de hasta 180 caracteres", () => {
  assert.equal(v.validateClassroomLocation(""), "");
  assert.equal(v.validateClassroomLocation("a".repeat(180)), "");
  assert.notEqual(v.validateClassroomLocation("a".repeat(181)), "");
});
test("validateClassroom solo devuelve los campos con error", () => {
  assert.deepEqual(v.validateClassroom(valid, { classrooms }), {});
  const errors = v.validateClassroom(
    { ...valid, codigo: "aula-201", capacidad: "0" },
    { classrooms },
  );
  assert.deepEqual(Object.keys(errors).sort(), ["capacidad", "codigo"]);
  assert.equal(v.hasErrors(errors), true);
  assert.equal(v.hasErrors({ codigo: undefined }), false);
});
test("El motivo es obligatorio al inhabilitar y tiene máximo 500", () => {
  assert.notEqual(v.validateClassroomReason("  ", true), "");
  assert.equal(v.validateClassroomReason("", false), "");
  assert.equal(v.validateClassroomReason("a".repeat(500), true), "");
  assert.notEqual(v.validateClassroomReason("a".repeat(501), false), "");
});
test("Horarios exige ACADEMICO_LEER", () => {
  const admin = (permissions) => ({
    id: "1",
    username: "admin",
    fullName: "Admin",
    role: "ADMINISTRADOR",
    permissions,
  });
  assert.equal(canAccess(admin(["ACADEMICO_LEER"]), "Horarios", ["aulas"]), true);
  assert.equal(canAccess(admin([]), "Horarios"), false);
});

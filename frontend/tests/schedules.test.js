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

import * as t from "@/modules/schedules/lib/timetable";
import { ApiError } from "@/core/api/client";
const block = (id, overrides = {}) => ({
  id,
  asignacionCursoId: 1,
  cursoId: 1,
  curso: "Matemática",
  seccionId: 8,
  seccion: "A",
  docenteId: 9,
  docente: "Patricia Morales",
  aulaId: 1,
  aula: "Aula 201",
  diaSemana: "LUNES",
  horaInicio: "08:00:00",
  horaFin: "09:30:00",
  activo: true,
  version: 0,
  ...overrides,
});
const draft = {
  diaSemana: "LUNES",
  horaInicio: "09:00",
  horaFin: "10:00",
  aulaId: 2,
  docenteId: 3,
  seccionId: 4,
  capacidadSeccion: 30,
  capacidadAula: 30,
};
test("La matriz muestra el sábado solo si tiene bloques", () => {
  assert.deepEqual(t.visibleDays([block(1)]), ["LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES"]);
  assert.equal(t.visibleDays([block(1, { diaSemana: "SABADO" })]).at(-1), "SABADO");
});
test("Las filas son intervalos distintos ordenados por inicio y fin", () => {
  const rows = t.timeRows([
    block(1, { horaInicio: "10:00:00", horaFin: "11:00:00" }),
    block(2),
    block(3, { diaSemana: "MARTES" }),
    block(4, { horaFin: "09:00:00" }),
  ]);
  assert.deepEqual(rows, [
    { inicio: "08:00", fin: "09:00" },
    { inicio: "08:00", fin: "09:30" },
    { inicio: "10:00", fin: "11:00" },
  ]);
  assert.deepEqual(t.blocksAt([block(1), block(2, { diaSemana: "MARTES" })], "LUNES", rows[1]).map((b) => b.id), [1]);
});
test("Dos intervalos que solo se tocan no se cruzan", () => {
  assert.equal(t.overlaps({ inicio: "08:00", fin: "09:00" }, { inicio: "09:00", fin: "10:00" }), false);
  assert.equal(t.overlaps({ inicio: "08:00", fin: "09:01" }, { inicio: "09:00", fin: "10:00" }), true);
});
test("Detecta rango inválido, aforo y cruces por aula, docente y sección", () => {
  assert.deepEqual(t.detectConflicts({ ...draft, horaFin: "09:00" }, []).map((c) => c.kind), ["rango"]);
  assert.deepEqual(t.detectConflicts({ ...draft, capacidadAula: 29 }, []).map((c) => c.kind), ["aforo"]);
  const blocks = [
    block(1, { aulaId: 2, docenteId: 3 }),
    block(2, { docenteId: 3 }),
    block(3, { seccionId: 4 }),
    block(3, { seccionId: 4 }),
    block(4, { aulaId: 2, diaSemana: "MARTES" }),
    block(5, { aulaId: 2, horaInicio: "10:00:00", horaFin: "11:00:00" }),
    block(6, { aulaId: 2, activo: false }),
    block(7, { aulaId: 2 }),
    block(8),
  ];
  assert.deepEqual(
    t.detectConflicts({ ...draft, id: 7 }, blocks).map((c) => c.kind),
    ["aula", "docente", "seccion"],
  );
});
test("Traduce los rechazos del servidor a conflictos", () => {
  const error = (status, code) => new ApiError("mensaje", status, code);
  assert.equal(t.serverConflict(error(409, "HORARIO_DOCENTE_OCUPADO")).kind, "docente");
  assert.equal(t.serverConflict(error(409, "VERSION_DESACTUALIZADA")).title, "Bloque modificado por otro usuario");
  assert.equal(t.serverConflict(error(404, "AULA_NO_ENCONTRADA")).kind, "inhabilitado");
  assert.equal(t.serverConflict(error(400, "OTRO")).kind, "otro");
  assert.equal(t.serverConflict(error(403, "")), null);
  assert.equal(t.serverConflict(error(401, "")), null);
  assert.equal(t.serverConflict(new Error("red")), null);
  assert.equal(t.needsReload(t.serverConflict(error(409, "HORARIO_AULA_OCUPADA"))), true);
  assert.equal(t.needsReload(t.serverConflict(error(422, "AULA_CAPACIDAD_INSUFICIENTE"))), false);
});
test("El bloque exige curso, aula, día y un rango válido", () => {
  const empty = { asignacionCursoId: "", aulaId: "", diaSemana: "DOMINGO", horaInicio: "", horaFin: "" };
  assert.deepEqual(Object.keys(t.validateBlock(empty)).sort(), ["asignacionCursoId", "aulaId", "diaSemana", "horaFin", "horaInicio"]);
  const valid = { asignacionCursoId: "1", aulaId: "2", diaSemana: "LUNES", horaInicio: "08:00", horaFin: "09:00" };
  assert.deepEqual(t.validateBlock(valid), {});
  assert.deepEqual(Object.keys(t.validateBlock({ ...valid, horaFin: "08:00" })), ["horaFin"]);
});

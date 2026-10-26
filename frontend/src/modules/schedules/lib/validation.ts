/** Validaciones del catálogo de aulas: funciones puras, sin React. El servidor es la autoridad. */
import { CLASSROOM_TYPES } from "../types";
export const CLASSROOM_CODE_MAX = 30;
export const CLASSROOM_NAME_MAX = 100;
export const CLASSROOM_CAPACITY_MIN = 1;
export const CLASSROOM_CAPACITY_MAX = 300;
export const CLASSROOM_LOCATION_MAX = 180;
export const CLASSROOM_REASON_MAX = 500;

/** Valores del formulario tal como se escriben, antes de convertirlos. */
export type ClassroomFormValues = {
  codigo: string;
  nombre: string;
  tipoAula: string;
  capacidad: string;
  ubicacion: string;
};
export type ClassroomErrors = Partial<Record<keyof ClassroomFormValues, string>>;

/** Misma normalización que el servidor: sin espacios repetidos y en mayúsculas. */
export const normalizeClassroomCode = (code: string) =>
  code.trim().replace(/\s+/g, " ").toUpperCase();

/** Obligatorio, máximo 30 y sin repetir entre las aulas cargadas (aunque estén inhabilitadas). */
export function validateClassroomCode(
  code: string,
  classrooms: { id: number; codigo: string }[],
  currentId?: number,
): string {
  const value = normalizeClassroomCode(code);
  if (!value) return "El código del aula es obligatorio.";
  if (value.length > CLASSROOM_CODE_MAX)
    return `El código no puede superar ${CLASSROOM_CODE_MAX} caracteres.`;
  const repeated = classrooms.some(
    (c) => c.id !== currentId && normalizeClassroomCode(c.codigo) === value,
  );
  if (repeated) return "Ya existe un aula con ese código.";
  return "";
}

export function validateClassroomName(name: string): string {
  const value = name.trim();
  if (!value) return "El nombre del aula es obligatorio.";
  if (value.length > CLASSROOM_NAME_MAX)
    return `El nombre no puede superar ${CLASSROOM_NAME_MAX} caracteres.`;
  return "";
}

/** Entero de 1 a 300. */
export function validateClassroomCapacity(value: string): string {
  const text = value.trim();
  if (!text) return "La capacidad es obligatoria.";
  if (!/^\d+$/.test(text)) return "La capacidad debe ser un número entero.";
  const capacity = Number(text);
  if (capacity < CLASSROOM_CAPACITY_MIN || capacity > CLASSROOM_CAPACITY_MAX)
    return `La capacidad debe estar entre ${CLASSROOM_CAPACITY_MIN} y ${CLASSROOM_CAPACITY_MAX} estudiantes.`;
  return "";
}

export function validateClassroomType(type: string): string {
  if (!type) return "Seleccione el tipo de aula.";
  if (!(CLASSROOM_TYPES as readonly string[]).includes(type))
    return "El tipo de aula no es válido.";
  return "";
}

/** Opcional; máximo 180 caracteres. */
export function validateClassroomLocation(location: string): string {
  if (location.trim().length > CLASSROOM_LOCATION_MAX)
    return `La ubicación no puede superar ${CLASSROOM_LOCATION_MAX} caracteres.`;
  return "";
}

export function validateClassroom(
  input: ClassroomFormValues,
  context: { classrooms: { id: number; codigo: string }[]; currentId?: number },
): ClassroomErrors {
  const checks: [keyof ClassroomFormValues, string][] = [
    [
      "codigo",
      validateClassroomCode(input.codigo, context.classrooms, context.currentId),
    ],
    ["nombre", validateClassroomName(input.nombre)],
    ["tipoAula", validateClassroomType(input.tipoAula)],
    ["capacidad", validateClassroomCapacity(input.capacidad)],
    ["ubicacion", validateClassroomLocation(input.ubicacion)],
  ];
  return Object.fromEntries(checks.filter(([, error]) => error));
}

/** Inhabilitar exige motivo; habilitar lo deja opcional. Máximo 500 caracteres. */
export function validateClassroomReason(
  reason: string,
  required: boolean,
): string {
  const value = reason.trim();
  if (required && !value) return "Indique el motivo del cambio de estado.";
  if (value.length > CLASSROOM_REASON_MAX)
    return `El motivo no puede superar ${CLASSROOM_REASON_MAX} caracteres.`;
  return "";
}

export const hasErrors = (errors: Partial<Record<string, string>>) =>
  Object.values(errors).some(Boolean);

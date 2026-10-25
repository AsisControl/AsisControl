import { api } from "@/core/api/client";
import { reasonError } from "../student-rules";
import type { Student, StudentAction, StudentInput } from "../types";

export function getStudent(id: number) {
  return api<Student>(`/api/alumnos/${id}`);
}
export async function saveStudent(input: StudentInput, id?: number) {
  const saved = await api<Student>(
    id === undefined ? "/api/alumnos" : `/api/alumnos/${id}`,
    {
      method: id === undefined ? "POST" : "PUT",
      body: JSON.stringify(input),
    },
  );
  if (
    !saved ||
    typeof saved.id !== "number" ||
    !saved.codigoAlumno ||
    !saved.persona
  )
    throw new Error(
      "El servidor no devolvió la ficha guardada. Consulta el listado antes de volver a registrar.",
    );
  return saved;
}

export function canPerform(student: Student, action: StudentAction): boolean {
  if (student.accionesPermitidas?.[action] !== true) return false;
  return action === "inactivar"
    ? student.activo
    : action === "reactivar"
      ? !student.activo
      : true;
}
export async function changeStudentState(
  student: Student,
  action: StudentAction,
  motivo: string,
) {
  if (!canPerform(student, action))
    throw new Error("Esta operación no está disponible para la ficha.");
  const invalidReason = reasonError(motivo);
  if (invalidReason) throw new Error(invalidReason);
  // No reutilizar el DELETE antiguo para inactivar: afecta Persona y todos sus accesos.
  const updated = await api<Student | null>(
    action === "eliminar"
      ? `/api/alumnos/${student.id}`
      : `/api/alumnos/${student.id}/estado`,
    {
      method: action === "eliminar" ? "DELETE" : "PATCH",
      body: JSON.stringify({
        motivo: motivo.trim(),
        ...(action === "eliminar" ? {} : { activo: action === "reactivar" }),
        ...(student.version !== undefined ? { version: student.version } : {}),
      }),
    },
  );
  if (action !== "eliminar" && (!updated || updated.id !== student.id))
    throw new Error(
      "El servidor no devolvió el estado actualizado. Vuelve a consultar la ficha antes de continuar.",
    );
  return updated;
}

import { api } from "@/core/api/client";
import { normalizePage } from "@/shared/lib/pagination";
import type { Page } from "@/shared/types/api";
import type { Guardian, Teacher } from "../types";

/** Datos de PersonaRequest tal como los arma `personData` del formulario compartido. */
export type PersonaInput = {
  nombres: string;
  apellidoPaterno: string;
  apellidoMaterno: string;
  tipoDocumento: string;
  numeroDocumento: string;
  fechaNacimiento: string;
  sexo: string;
  correo: string | null;
  telefono: string | null;
  direccion: string | null;
  nacionalidadId: number | null;
};

export type Parentesco = "PADRE" | "MADRE" | "TUTOR_LEGAL" | "OTRO_FAMILIAR" | "NO_DECLARADO";

/** Resultado de buscar un estudiante o un apoderado para vincularlos. */
export type PersonaOpcion = { id: number; etiqueta: string };

export function registrarApoderado(persona: PersonaInput) {
  return api<Guardian>("/api/apoderados", {
    method: "POST",
    body: JSON.stringify({ persona }),
  });
}

export function registrarDocente(input: {
  persona: PersonaInput;
  fechaIngreso: string;
  especialidad: string;
}) {
  return api<Teacher>("/api/docentes", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function vincularApoderado(
  alumnoId: number,
  vinculo: { apoderadoId: number; parentesco: Parentesco; principal: boolean },
) {
  return api<unknown>(`/api/alumnos/${alumnoId}/apoderados`, {
    method: "POST",
    body: JSON.stringify(vinculo),
  });
}

async function buscar<T>(ruta: string, texto: string) {
  const page = await api<Page<T>>(`${ruta}?page=0&size=8&search=${encodeURIComponent(texto)}`);
  return normalizePage(page).content;
}

export async function buscarEstudiantes(texto: string): Promise<PersonaOpcion[]> {
  const estudiantes = await buscar<{ id: number; codigoAlumno: string; persona: { nombreCompleto: string } }>(
    "/api/alumnos",
    texto,
  );
  return estudiantes.map((e) => ({ id: e.id, etiqueta: `${e.persona.nombreCompleto} · ${e.codigoAlumno}` }));
}

export async function buscarApoderados(texto: string): Promise<PersonaOpcion[]> {
  const apoderados = await buscar<Guardian>("/api/apoderados", texto);
  return apoderados.map((a) => ({
    id: a.id,
    etiqueta: `${a.persona.nombreCompleto} · ${a.persona.numeroDocumento ?? "sin documento"}`,
  }));
}

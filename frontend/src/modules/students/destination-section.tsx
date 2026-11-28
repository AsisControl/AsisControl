"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { api, message } from "@/core/api/client";
import { Feedback } from "@/shared/ui";

export type DestinationSection = {
  id: number;
  nombre: string;
  grado: number;
  anioAcademico: number;
};

/** Sección indicada en la URL (?seccion=id) cuando se llega desde «Agregar alumno». */
export function useDestinationSection() {
  const id = useSearchParams()?.get("seccion");
  const [section, setSection] = useState<DestinationSection | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    if (!id) return;
    let active = true;
    api<DestinationSection>(`/api/secciones/${encodeURIComponent(id)}`).then(
      (value) => { if (active) setSection(value); },
      (cause) => { if (active) setError(message(cause)); },
    );
    return () => { active = false; };
  }, [id]);
  return { section: id && section && String(section.id) === id ? section : null, error: id ? error : "" };
}

export function DestinationNotice({ section, error }: ReturnType<typeof useDestinationSection>) {
  return (
    <>
      <Feedback error={error} />
      {section && (
        <p className="dialog-note" role="status">
          Agregando alumnos a <strong>{section.grado}° {section.nombre} · {section.anioAcademico}</strong>.
          Abre la ficha del estudiante para asignarlo.{" "}
          <Link href="/matricula/fichas/estudiantes">Quitar destino</Link>
        </p>
      )}
    </>
  );
}

"use client";
import { useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { SessionDetail, SessionHistory } from "@/modules/attendance";
import { TeacherCourses } from "./teacher-courses";

const esId = (valor: string | undefined) => !!valor && /^[1-9]\d*$/.test(valor);

function EvaluationsRedirect() {
  const router = useRouter();
  useEffect(() => {
    router.replace("/evaluaciones");
  }, [router]);
  return <p role="status">Abriendo evaluaciones…</p>;
}

/** Rutas de Mis cursos y compatibilidad con enlaces anteriores. */
export function TeacherCoursesModule({ subpath }: { subpath: string[] }) {
  if (!subpath.length) return <TeacherCourses />;
  const [id, seccion, sesionId] = subpath;
  if (esId(id) && seccion === "asistencia" && subpath.length === 2)
    return <SessionHistory key={id} asignacionId={Number(id)} />;
  if (esId(id) && seccion === "asistencia" && esId(sesionId) && subpath.length === 3)
    return <SessionDetail key={sesionId} asignacionId={Number(id)} sesionId={Number(sesionId)} />;
  if (esId(id) && seccion === "evaluaciones" && subpath.length === 2)
    return <EvaluationsRedirect />;
  return (
    <>
      <p>La dirección no es válida.</p>
      <Link href="/mis-cursos">Volver a Mis cursos</Link>
    </>
  );
}

"use client";
import Link from "next/link";
import { SessionDetail, SessionHistory } from "@/modules/attendance";
import { PageHeader } from "@/shared/ui";
import { TeacherCourses } from "./teacher-courses";

const esId = (valor: string | undefined) => !!valor && /^[1-9]\d*$/.test(valor);

/** Rutas de Mis cursos; las pantallas de cada curso se agregan aquí al implementarse. */
export function TeacherCoursesModule({ subpath }: { subpath: string[] }) {
  if (!subpath.length) return <TeacherCourses />;
  const [id, seccion, sesionId] = subpath;
  if (esId(id) && seccion === "asistencia" && subpath.length === 2)
    return <SessionHistory key={id} asignacionId={Number(id)} />;
  if (esId(id) && seccion === "asistencia" && esId(sesionId) && subpath.length === 3)
    return <SessionDetail key={sesionId} asignacionId={Number(id)} sesionId={Number(sesionId)} />;
  if (esId(id) && seccion === "evaluaciones" && subpath.length === 2)
    return (
      <section>
        <PageHeader title="Evaluaciones" description="Esta pantalla está en construcción." />
        <Link href="/mis-cursos">Volver a Mis cursos</Link>
      </section>
    );
  return (
    <>
      <p>La dirección no es válida.</p>
      <Link href="/mis-cursos">Volver a Mis cursos</Link>
    </>
  );
}

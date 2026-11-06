"use client";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { fieldErrorsOf, message } from "@/core/api/client";
import { FormDialog } from "@/shared/ui";
import type {
  CourseOption,
  SectionOption,
  TeacherAssignment,
  TeacherOption,
} from "../types";
import {
  actualizarAsignacion,
  crearAsignacion,
  cursosDelGrado,
  docentesActivos,
  seccionesDelAnio,
} from "./services/asignacionService";
type Errors = Partial<Record<"anio" | "seccionId" | "cursoId" | "docenteId", string>>;
/** "1.° A". */
export const sectionLabel = (grado: number, nombre: string) => `${grado}.° ${nombre}`;
/**
 * Alta o edición de una asignación docente (US-58). Primero el año y la sección; los cursos
 * se cargan del grado de esa sección y el backend valida que pertenezcan a él.
 */
export function AssignmentFormDialog({
  assignment,
  onClose,
  onSaved,
}: {
  /** Si viene, se edita esa asignación (con su versión); si no, se crea una nueva. */
  assignment?: TeacherAssignment;
  onClose: () => void;
  onSaved: (assignment: TeacherAssignment) => void;
}) {
  const [anio, setAnio] = useState(
    String(assignment?.anioAcademico ?? new Date().getFullYear()),
  );
  const [seccionId, setSeccionId] = useState(assignment ? String(assignment.seccionId) : "");
  const [cursoId, setCursoId] = useState(assignment ? String(assignment.cursoId) : "");
  const [docenteId, setDocenteId] = useState(assignment ? String(assignment.docenteId) : "");
  // Cada lista recuerda para qué año o sección se cargó; si ya no coincide, se muestra vacía.
  const [loadedSections, setLoadedSections] = useState<{ year: number; items: SectionOption[] } | null>(null);
  const [loadedCourses, setLoadedCourses] = useState<{ sectionId: number; items: CourseOption[] } | null>(null);
  const [teachers, setTeachers] = useState<TeacherOption[]>([]);
  const [errors, setErrors] = useState<Errors>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const sending = useRef(false);
  const year = Number(anio);
  const validYear = Number.isInteger(year) && year >= 2000 && year <= 2100;
  const sections = validYear && loadedSections?.year === year ? loadedSections.items : [];
  const section = sections.find((item) => String(item.id) === seccionId);
  const courses = section && loadedCourses?.sectionId === section.id ? loadedCourses.items : [];
  useEffect(() => {
    let active = true;
    docentesActivos()
      .then((items) => active && setTeachers(items))
      .catch((e) => active && setError(message(e)));
    return () => {
      active = false;
    };
  }, []);
  useEffect(() => {
    if (!validYear) return;
    let active = true;
    seccionesDelAnio(year)
      .then((items) => active && setLoadedSections({ year, items }))
      .catch((e) => active && setError(message(e)));
    return () => {
      active = false;
    };
  }, [validYear, year]);
  useEffect(() => {
    if (!section) return;
    let active = true;
    cursosDelGrado(section.anioAcademico, section.grado)
      .then((items) => active && setLoadedCourses({ sectionId: section.id, items }))
      .catch((e) => active && setError(message(e)));
    return () => {
      active = false;
    };
  }, [section]);
  function validate(): Errors {
    const found: Errors = {};
    if (!validYear) found.anio = "Ingrese un año entre 2000 y 2100.";
    if (!seccionId) found.seccionId = "Seleccione la sección.";
    if (!cursoId) found.cursoId = "Seleccione el curso.";
    if (!docenteId) found.docenteId = "Seleccione el docente.";
    return found;
  }
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current) return;
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length) return;
    const input = {
      seccionId: Number(seccionId),
      cursoId: Number(cursoId),
      docenteId: Number(docenteId),
    };
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      onSaved(
        await (assignment
          ? actualizarAsignacion(assignment, input)
          : crearAsignacion(input)),
      );
    } catch (e) {
      setError(message(e));
      setErrors(fieldErrorsOf(e));
      sending.current = false;
      setBusy(false);
    }
  }
  const fieldError = (key: keyof Errors) =>
    errors[key] ? (
      <small className="field-error" role="alert">
        {errors[key]}
      </small>
    ) : null;
  return (
    <FormDialog
      title={assignment ? "Editar asignación docente" : "Nueva asignación docente"}
      description={
        assignment
          ? `${assignment.curso} · ${sectionLabel(assignment.grado, assignment.seccion)} · ${assignment.anioAcademico}`
          : "Define qué docente dicta el curso en la sección."
      }
      submitLabel={assignment ? "Guardar cambios" : "Asignar docente"}
      busy={busy}
      error={error}
      onClose={onClose}
      onSubmit={save}
      noValidate
    >
      <label className="field">
        Año académico
        <span className="field-required"> *</span>
        <input
          name="anio"
          type="number"
          min={2000}
          max={2100}
          value={anio}
          disabled={busy}
          aria-invalid={errors.anio ? true : undefined}
          onChange={(e) => {
            setAnio(e.target.value);
            setSeccionId("");
            setCursoId("");
            setErrors((current) => ({ ...current, anio: undefined }));
          }}
        />
        {fieldError("anio")}
      </label>
      <label className="field">
        Sección
        <span className="field-required"> *</span>
        <select
          name="seccionId"
          value={seccionId}
          disabled={busy}
          aria-invalid={errors.seccionId ? true : undefined}
          onChange={(e) => {
            setSeccionId(e.target.value);
            setCursoId("");
            setErrors((current) => ({ ...current, seccionId: undefined }));
          }}
        >
          <option value="">
            {sections.length ? "Seleccione…" : "Sin secciones habilitadas en ese año"}
          </option>
          {sections.map((item) => (
            <option key={item.id} value={item.id}>
              {sectionLabel(item.grado, item.nombre)}
            </option>
          ))}
        </select>
        {fieldError("seccionId")}
      </label>
      <label className="field">
        Curso
        <span className="field-required"> *</span>
        <select
          name="cursoId"
          value={cursoId}
          disabled={busy || !section}
          aria-invalid={errors.cursoId ? true : undefined}
          onChange={(e) => {
            setCursoId(e.target.value);
            setErrors((current) => ({ ...current, cursoId: undefined }));
          }}
        >
          <option value="">
            {section ? "Seleccione…" : "Elija primero la sección"}
          </option>
          {courses.map((item) => (
            <option key={item.id} value={item.id}>
              {item.nombre} ({item.codigo})
            </option>
          ))}
        </select>
        {fieldError("cursoId")}
      </label>
      <label className="field">
        Docente
        <span className="field-required"> *</span>
        <select
          name="docenteId"
          value={docenteId}
          disabled={busy}
          aria-invalid={errors.docenteId ? true : undefined}
          onChange={(e) => {
            setDocenteId(e.target.value);
            setErrors((current) => ({ ...current, docenteId: undefined }));
          }}
        >
          <option value="">Seleccione…</option>
          {teachers.map((item) => (
            <option key={item.id} value={item.id}>
              {item.persona.nombreCompleto} ({item.codigoDocente})
            </option>
          ))}
        </select>
        {fieldError("docenteId")}
      </label>
      {assignment && assignment.horariosActivos > 0 && (
        <p className="full-field hint">
          Esta asignación tiene bloques de horario: puede cambiar de docente si
          no se le cruzan las clases, pero no el curso ni la sección.
        </p>
      )}
    </FormDialog>
  );
}

"use client";
import { useState } from "react";
import type { Student } from "@/modules/students";
import { Feedback, Field, Loading, Pager } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { STUDENT_RECORDS_PATH } from "./services/guardian-link-service";

/** Búsqueda y selección de un estudiante habilitado. */
export function StudentPicker({
  onSelect,
  selected,
}: {
  onSelect: (student: Student) => void;
  selected: Student | null;
}) {
  const [search, setSearch] = useState("");
  const students = usePaged<Student>(STUDENT_RECORDS_PATH, search);
  return (
    <div className="picker-card">
      <h4>Estudiante</h4>
      <form
        className="toolbar"
        onSubmit={(event) => {
          event.preventDefault();
          setSearch(
            String(new FormData(event.currentTarget).get("search") || ""),
          );
          students.setPage(0);
          students.refresh();
        }}
      >
        <Field label="Buscar por nombre, documento o código" name="search" />
        <button>Buscar</button>
      </form>
      <Feedback error={students.error} />
      {students.error && <button type="button" onClick={students.refresh}>Reintentar búsqueda</button>}
      {selected && (
        <p className="selected-person">
          Seleccionado: <strong>{selected.persona.nombreCompleto}</strong> ·{" "}
          {selected.codigoAlumno}
        </p>
      )}
      {students.loading ? (
        <Loading>Cargando estudiantes…</Loading>
      ) : !students.error && (students.data?.content.length ? (
        <ul className="choice-list">
          {students.data?.content.map((student) => (
            <li key={student.id}>
              <span>
                {student.persona.nombreCompleto}
                <small>
                  {student.codigoAlumno} · {student.persona.numeroDocumento}
                </small>
              </span>
              <button
                disabled={!student.activo || student.persona.activo === false || selected?.id === student.id}
                onClick={() => onSelect(student)}
              >
                {selected?.id === student.id ? "Seleccionado" : "Elegir"}
              </button>
            </li>
          ))}
        </ul>
      ) : <p className="empty">No se encontraron estudiantes para esta búsqueda.</p>)}
      <Pager
        data={students.data}
        loading={students.loading}
        setPage={students.setPage}
      />
    </div>
  );
}

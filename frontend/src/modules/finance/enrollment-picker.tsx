"use client";
import { useState } from "react";
import { usePaged } from "@/shared/hooks/use-paged";
import { Feedback, Pager } from "@/shared/ui";
import { ELIGIBLE_ENROLLMENTS_PATH } from "./services/commitment-service";
import type { EligibleEnrollment } from "./types";
import styles from "./finance.module.css";

export function EnrollmentPicker({ selected, disabled, onSelect }: {
  selected: EligibleEnrollment | null;
  disabled: boolean;
  onSelect: (enrollment: EligibleEnrollment | null) => void;
}) {
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const list = usePaged<EligibleEnrollment>(
    ELIGIBLE_ENROLLMENTS_PATH, "", `busqueda=${encodeURIComponent(query)}`, 5,
  );

  function searchEnrollments() {
    setQuery(search.trim());
    list.setPage(0);
  }

  return (
    <div className="full-field">
      <h4 className={styles.pickerTitle}>1. Selecciona la matrícula</h4>
      {selected ? (
        <div className={styles.selectedEnrollment}>
          <p>
            <strong>{selected.alumno}</strong>
            <span>
              {selected.codigoAlumno} · {selected.anioAcademico} · {selected.grado}° {selected.seccion}
            </span>
            <span>Matrícula {selected.codigoMatricula}</span>
          </p>
          <button
            type="button"
            className="secondary-action"
            disabled={disabled}
            data-enrollment-control
            onClick={() => onSelect(null)}
          >
            Cambiar matrícula
          </button>
        </div>
      ) : (
        <>
          <div className={styles.pickerSearch}>
            <label className={`field ${styles.searchField}`}>
              Buscar estudiante con matrícula vigente
              <input
                value={search}
                data-enrollment-control
                placeholder="Nombre o código del estudiante"
                disabled={disabled}
                onChange={(event) => setSearch(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.preventDefault();
                    searchEnrollments();
                  }
                }}
              />
            </label>
            <button
              type="button"
              className="secondary-action"
              disabled={disabled}
              onClick={searchEnrollments}
            >
              Buscar matrícula
            </button>
          </div>
          <Feedback error={list.error} />
          {list.loading ? (
            <p role="status">Buscando matrículas…</p>
          ) : list.error ? (
            <button type="button" className="secondary-action" disabled={disabled} onClick={list.refresh}>
              Reintentar búsqueda
            </button>
          ) : list.data?.content.length ? (
            <ul className={styles.enrollmentChoices} aria-label="Matrículas disponibles">
              {list.data.content.map((enrollment) => (
                <li key={enrollment.id}>
                  <span>
                    <strong>{enrollment.alumno}</strong>
                    <small>
                      {enrollment.codigoAlumno} · {enrollment.anioAcademico} · {enrollment.grado}° {enrollment.seccion}
                    </small>
                  </span>
                  <button
                    type="button"
                    className="secondary-action"
                    disabled={disabled}
                    onClick={() => onSelect(enrollment)}
                    aria-label={`Elegir matrícula de ${enrollment.alumno}`}
                  >
                    Elegir
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <p className="empty">
              No hay matrículas vigentes que coincidan con la búsqueda.
            </p>
          )}
          <Pager
            data={list.data}
            loading={list.loading || disabled}
            setPage={list.setPage}
          />
        </>
      )}
    </div>
  );
}

"use client";
import { useState } from "react";
import { Field } from "@/shared/ui";
import { roleLabel } from "@/shared/lib/formatters";
import { teacherSpecialties, type Role } from "../types";
import { rolePillClass } from "../users/user-format";
import { specialtyLabel } from "./new-user-format";
import { CheckIcon, StepCard } from "./step-parts";
import styles from "./new-user.module.css";
const STUDENT_ROLES = ["ALUMNO", "ESTUDIANTE"];
/** Qué podrá hacer cada rol; si llega uno nuevo se usa la descripción del backend. */
const ROLE_DESCRIPTIONS: Record<string, string> = {
  ADMINISTRADOR: "Gestiona usuarios, configuración académica y auditoría.",
  DOCENTE: "Registra asistencia y evaluaciones de sus cursos.",
  ALUMNO: "Consulta sus tareas, horario, asistencia y progreso.",
  ESTUDIANTE: "Consulta sus tareas, horario, asistencia y progreso.",
  APODERADO: "Sigue a sus estudiantes asociados, matrícula y pagos.",
};
/** Paso 2: rol del acceso y, si la persona aún no lo tiene, su perfil institucional. */
export function RoleStep({
  roles,
  roleId,
  onRoleChange,
  needsProfile,
  rolesWithAccess,
}: {
  roles: Role[];
  roleId: string;
  onRoleChange: (roleId: string) => void;
  needsProfile: boolean;
  /** Roles en los que la persona elegida ya tiene acceso. */
  rolesWithAccess: string[];
}) {
  const [today] = useState(() => new Date().toLocaleDateString("en-CA"));
  const role = roles.find((r) => String(r.id) === roleId)?.nombre ?? "";
  return (
    <StepCard
      number={2}
      title="Rol y perfil"
      description="Define qué podrá hacer esta persona en el sistema."
      done={Boolean(role) && !rolesWithAccess.includes(role)}
    >
      <div
        className={styles.roleGrid}
        role="radiogroup"
        aria-label="Rol del acceso"
      >
        {roles.map((option) => {
          const selected = String(option.id) === roleId;
          const hasAccess = rolesWithAccess.includes(option.nombre);
          return (
            <button
              key={option.id}
              type="button"
              role="radio"
              aria-checked={selected}
              disabled={hasAccess}
              className={`${styles.choice} ${styles.roleCard}`}
              onClick={() => onRoleChange(String(option.id))}
            >
              <span className={styles.roleCardHeader}>
                <span className={rolePillClass(option.nombre)}>
                  {roleLabel(option.nombre)}
                </span>
                {selected && (
                  <span className={styles.check}>
                    <CheckIcon size={16} />
                  </span>
                )}
              </span>
              <small>
                {hasAccess
                  ? "Esta persona ya tiene este acceso."
                  : (ROLE_DESCRIPTIONS[option.nombre] ?? option.descripcion)}
              </small>
            </button>
          );
        })}
      </div>
      {role === "DOCENTE" && needsProfile && (
        <div className={styles.subsection}>
          <div className="form-grid">
            <Field
              label="Código docente"
              name="codigoDocente"
              maxLength={30}
              required
              placeholder="DOC-2025-001"
            />
            <Field
              label="Fecha de ingreso"
              name="fechaIngreso"
              type="date"
              required
              max={today}
            />
            <label className="field">
              Especialidad *
              <select name="especialidad" required defaultValue="">
                <option value="" disabled>
                  Seleccionar
                </option>
                {teacherSpecialties.map((specialty) => (
                  <option key={specialty} value={specialty}>
                    {specialtyLabel(specialty)}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </div>
      )}
      {STUDENT_ROLES.includes(role) && needsProfile && (
        <div className={styles.subsection}>
          <Field
            label="Código de estudiante"
            name="codigoAlumno"
            required
            maxLength={30}
            placeholder="EST-2025-001"
          />
        </div>
      )}
      {role && !needsProfile && (
        <p className={styles.note}>
          Se utilizará el perfil de {roleLabel(role).toLowerCase()} que esta
          persona ya tiene registrado.
        </p>
      )}
    </StepCard>
  );
}

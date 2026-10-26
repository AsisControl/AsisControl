"use client";
import { useState } from "react";
import { PersonCell, StatusPill } from "@/shared/ui";
import { specialtyLabel } from "@/shared/lib/formatters";
import { PendingAction, RecordsPanel } from "./records-panel";
import type { Teacher } from "./types";
import { TeacherDetail } from "./record-details";

export function TeachersPanel() {
  const [openId, setOpenId] = useState<number | null>(null);
  return (
    <>
      <RecordsPanel<Teacher>
        title="Docentes"
        path="/api/docentes"
        noun={["docente", "docentes"]}
        searchLabel="Buscar por nombre, código o documento"
        columns={[
          "Código",
          "Docente",
          "Documento",
          "Especialidad",
          "Estado",
          "Acción",
        ]}
        emptyText="Todavía no hay docentes registrados."
        actions={() => (
          <PendingAction
            className="primary-action"
            label="+ Registrar docente"
            hint="El formulario de docentes se habilitará en una próxima entrega."
          />
        )}
        renderRows={(teachers) =>
          teachers.map((teacher) => (
            <tr key={teacher.id}>
              <td data-label="Código">{teacher.codigoDocente}</td>
              <td data-label="Docente">
                <PersonCell
                  name={teacher.persona.nombreCompleto}
                  detail={teacher.persona.correo || "Sin correo de contacto"}
                />
              </td>
              <td data-label="Documento">
                {teacher.persona.numeroDocumento || "—"}
              </td>
              <td data-label="Especialidad">
                {specialtyLabel(teacher.especialidad)}
              </td>
              <td data-label="Estado">
                <StatusPill
                  state={teacher.activo ? "ACTIVO" : "INACTIVO"}
                  label={teacher.activo ? "Activo" : "Inactivo"}
                  tone={teacher.activo ? "success" : "neutral"}
                />
              </td>
              <td data-label="Acción">
                <button
                  type="button"
                  aria-label={`Ver ficha de ${teacher.persona.nombreCompleto}`}
                  onClick={() => setOpenId(teacher.id)}
                >
                  Ver ficha
                </button>
              </td>
            </tr>
          ))
        }
      />
      {openId !== null && (
        <TeacherDetail id={openId} onClose={() => setOpenId(null)} />
      )}
    </>
  );
}

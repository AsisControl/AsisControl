"use client";
import { useState } from "react";
import { FilterSelect, PersonCell, StatusPill } from "@/shared/ui";
import { SPECIALTY_LABELS, specialtyLabel } from "@/shared/lib/formatters";
import { RecordsPanel } from "./records-panel";
import { RegisterProfile } from "./register-profile";
import { TeacherImportDialog } from "./teacher-import-dialog";
import type { Teacher } from "./types";
import { TeacherDetail } from "./record-details";

/** "Carga masiva": abre la carga de docentes con plantilla de Excel (US-51). */
function TeacherImportAction({ onImported }: { onImported: () => void }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button onClick={() => setOpen(true)}>Carga masiva</button>
      {open && (
        <TeacherImportDialog
          onClose={() => setOpen(false)}
          onImported={onImported}
        />
      )}
    </>
  );
}

export function TeachersPanel() {
  const [openId, setOpenId] = useState<number | null>(null);
  const [specialty, setSpecialty] = useState("");
  return (
    <>
      <RecordsPanel<Teacher>
        title="Docentes"
        path="/api/docentes"
        filterQuery={specialty && `especialidad=${specialty}`}
        filters={
          <FilterSelect
            label="Especialidad"
            allLabel="Todas"
            value={specialty}
            onChange={setSpecialty}
            options={Object.entries(SPECIALTY_LABELS)}
          />
        }
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
        actions={(refresh) => (
          <>
            <TeacherImportAction onImported={refresh} />
            <RegisterProfile role="docente" onRegistered={refresh} />
          </>
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
        detail={(refresh) => openId !== null &&
          <TeacherDetail id={openId} onClose={() => setOpenId(null)} onSaved={refresh} />}
      />
    </>
  );
}

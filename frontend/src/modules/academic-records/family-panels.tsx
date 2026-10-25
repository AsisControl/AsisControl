"use client";
import { useState } from "react";
import { FamilyImportDialog, type Student } from "@/modules/students";
import { PersonCell, StatusPill } from "@/shared/ui";
import { stateLabel } from "@/shared/lib/formatters";
import { PendingAction, RecordsPanel } from "./records-panel";
import { GuardianDetail } from "./record-details";
import type { Guardian } from "./types";
import styles from "./academic-records.module.css";

/** "Carga masiva": abre la carga de estudiantes, apoderados y parentescos. */
function FamilyImportAction({ onImported }: { onImported: () => void }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button onClick={() => setOpen(true)}>Carga masiva</button>
      {open && (
        <FamilyImportDialog
          onClose={() => setOpen(false)}
          onImported={onImported}
        />
      )}
    </>
  );
}

const documentOf = ({ persona }: { persona: Guardian["persona"] }) =>
  persona.tipoDocumento && persona.numeroDocumento
    ? `${persona.tipoDocumento} ${persona.numeroDocumento}`
    : "—";

export function GuardiansPanel() {
  const [openId, setOpenId] = useState<number | null>(null);
  return (
    <>
      <RecordsPanel<Guardian>
        title="Apoderados"
        path="/api/apoderados"
        noun={["apoderado", "apoderados"]}
        searchLabel="Buscar por nombre o documento"
        columns={[
          "Apoderado",
          "Documento",
          "Estudiantes a cargo",
          "Estado",
          "Acción",
        ]}
        emptyText="Todavía no hay apoderados registrados."
        actions={(refresh) => (
          <>
            <FamilyImportAction onImported={refresh} />
            <PendingAction
              className="primary-action"
              label="+ Registrar apoderado"
              hint="El formulario de apoderados se habilitará en una próxima entrega."
            />
          </>
        )}
        renderRows={(guardians) =>
          guardians.map((guardian) => (
            <tr key={guardian.id}>
              <td data-label="Apoderado">
                <PersonCell
                  name={guardian.persona.nombreCompleto}
                  detail={guardian.persona.correo || "Sin correo de contacto"}
                />
              </td>
              <td data-label="Documento">{documentOf(guardian)}</td>
              <td data-label="Estudiantes a cargo">
                {guardian.alumnos.length ? (
                  <ul className={styles.inlineList}>
                    {guardian.alumnos.map((link) => (
                      <li key={link.id}>
                        {link.nombreCompleto}
                        <span className={styles.muted}>
                          {" "}
                          · {stateLabel(link.parentesco)}
                        </span>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <span className={styles.muted}>
                    Sin estudiantes vinculados
                  </span>
                )}
              </td>
              <td data-label="Estado">
                <StatusPill
                  state={guardian.activo ? "ACTIVO" : "INACTIVO"}
                  label={guardian.activo ? "Activo" : "Inactivo"}
                  tone={guardian.activo ? "success" : "neutral"}
                />
              </td>
              <td data-label="Acción">
                <button
                  type="button"
                  aria-label={`Ver ficha de ${guardian.persona.nombreCompleto}`}
                  onClick={() => setOpenId(guardian.id)}
                >
                  Ver ficha
                </button>
              </td>
            </tr>
          ))
        }
      />
      {openId !== null && (
        <GuardianDetail id={openId} onClose={() => setOpenId(null)} />
      )}
    </>
  );
}

/**
 * Un vínculo por fila. El backend no tiene un listado propio de vínculos: salen de cada
 * estudiante (AlumnoResponse.apoderados), así que la página y la búsqueda son de estudiantes.
 */
export function RelationshipsPanel() {
  return (
    <RecordsPanel<Student>
      title="Parentescos"
      path="/api/alumnos"
      noun={["estudiante", "estudiantes"]}
      searchLabel="Buscar estudiante por nombre, código o documento"
      columns={["Estudiante", "Apoderado", "Parentesco", "Principal"]}
      emptyText="Todavía no hay estudiantes registrados."
      actions={(refresh) => (
        <>
          <FamilyImportAction onImported={refresh} />
          <PendingAction
            className="primary-action"
            label="+ Vincular apoderado"
            hint="Vincular desde aquí se habilitará en una próxima entrega."
          />
        </>
      )}
      renderRows={(students) =>
        students.flatMap((student) => {
          const name = (
            <td data-label="Estudiante">
              <PersonCell
                name={student.persona.nombreCompleto}
                detail={student.codigoAlumno}
              />
            </td>
          );
          if (!student.apoderados.length)
            return (
              <tr key={student.id}>
                {name}
                <td data-label="Apoderado" colSpan={3}>
                  <span className={styles.warning}>
                    Sin apoderado registrado
                  </span>
                </td>
              </tr>
            );
          return student.apoderados.map((link) => (
            <tr key={`${student.id}-${link.id}`}>
              {name}
              <td data-label="Apoderado">{link.nombreCompleto}</td>
              <td data-label="Parentesco">{stateLabel(link.parentesco)}</td>
              <td data-label="Principal">{link.principal ? "Sí" : "No"}</td>
            </tr>
          ));
        })
      }
    />
  );
}

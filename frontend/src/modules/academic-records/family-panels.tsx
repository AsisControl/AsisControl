"use client";
import { useState, type ReactNode } from "react";
import { FamilyImportDialog, type Student } from "@/modules/students";
import { documentLabel, PersonCell, StatusPill } from "@/shared/ui";
import { stateLabel } from "@/shared/lib/formatters";
import { RecordsPanel } from "./records-panel";
import { GuardianFormDialog } from "./guardian-form-dialog";
import { LinkGuardianDialog } from "./link-guardian-dialog";
import type { PersonaOpcion } from "./services/registro-service";
import { GuardianDetail } from "./record-details";
import type { Guardian } from "./types";
import styles from "./academic-records.module.css";

/** Botón que abre un formulario de registro y refresca el listado al guardar. */
function RegisterAction({
  label,
  onSaved,
  children,
}: {
  label: string;
  onSaved: () => void;
  children: (close: () => void, saved: () => void) => ReactNode;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        type="button"
        className="primary-action"
        onClick={() => setOpen(true)}
      >
        {label}
      </button>
      {open && children(() => setOpen(false), onSaved)}
    </>
  );
}

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
          "Código",
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
            <RegisterAction label="+ Registrar apoderado" onSaved={refresh}>
              {(close, saved) => (
                <GuardianFormDialog onClose={close} onSaved={saved} />
              )}
            </RegisterAction>
          </>
        )}
        renderRows={(guardians) =>
          guardians.map((guardian) => (
            <tr key={guardian.id}>
              <td data-label="Código">{guardian.codigoApoderado ?? "—"}</td>
              <td data-label="Apoderado">
                <PersonCell
                  name={guardian.persona.nombreCompleto}
                  detail={guardian.persona.correo || "Sin correo de contacto"}
                />
              </td>
              <td data-label="Documento">{documentLabel(guardian.persona)}</td>
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
  const [linking, setLinking] = useState<{
    student: PersonaOpcion;
    refresh: () => void;
  } | null>(null);
  return (
    <>
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
            <RegisterAction label="+ Vincular apoderado" onSaved={refresh}>
              {(close, saved) => (
                <LinkGuardianDialog onClose={close} onSaved={saved} />
              )}
            </RegisterAction>
          </>
        )}
        renderRows={(students, refresh) =>
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
                    </span>{" "}
                    <button
                      type="button"
                      aria-label={`Vincular apoderado a ${student.persona.nombreCompleto}`}
                      onClick={() =>
                        setLinking({
                          student: {
                            id: student.id,
                            etiqueta: `${student.persona.nombreCompleto} · ${student.codigoAlumno}`,
                          },
                          refresh,
                        })
                      }
                    >
                      Vincular
                    </button>
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
      {linking && (
        <LinkGuardianDialog
          student={linking.student}
          onClose={() => setLinking(null)}
          onSaved={linking.refresh}
        />
      )}
    </>
  );
}

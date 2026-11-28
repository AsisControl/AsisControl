"use client";
import { useState } from "react";
import { FamilyImportDialog, type Student } from "@/modules/students";
import { documentLabel, Feedback, FilterSelect, PersonCell, StatusPill } from "@/shared/ui";
import { stateLabel } from "@/shared/lib/formatters";
import { RecordsPanel } from "./records-panel";
import { RegisterProfile } from "./register-profile";
import { GuardianDetail } from "./record-details";
import { RelationshipCreateDialog } from "./relationship-create-dialog";
import { RelationshipManagementDialog } from "./relationship-management-dialog";
import { kinshipLabel } from "./relationship-fields";
import { STUDENT_RECORDS_PATH } from "./services/relationship-service";
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

export function GuardiansPanel() {
  const [openId, setOpenId] = useState<number | null>(null);
  const [linked, setLinked] = useState("");
  return (
    <>
      <RecordsPanel<Guardian>
        title="Apoderados"
        path="/api/apoderados"
        filterQuery={linked && `conEstudiantes=${linked}`}
        filters={
          <FilterSelect
            label="Estudiantes a cargo"
            value={linked}
            onChange={setLinked}
            options={[["true", "Con estudiantes"], ["false", "Sin estudiantes"]]}
          />
        }
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
            <RegisterProfile role="apoderado" onRegistered={refresh} />
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
        detail={(refresh) => openId !== null &&
          <GuardianDetail id={openId} onClose={() => setOpenId(null)} onSaved={refresh} />}
      />
    </>
  );
}

export function RelationshipsPanel() {
  const [creating, setCreating] = useState(false);
  const [selected, setSelected] = useState<Student | null>(null);
  const [notice, setNotice] = useState("");
  return (
    <>
    <Feedback success={notice} />
    <RecordsPanel<Student>
      title="Parentescos"
      path={STUDENT_RECORDS_PATH}
      noun={["estudiante", "estudiantes"]}
      searchLabel="Buscar estudiante por nombre, código o documento"
      columns={["Estudiante", "Apoderados activos", "Acción"]}
      emptyText="Todavía no hay estudiantes registrados."
      actions={(refresh) => (
        <>
          <FamilyImportAction onImported={refresh} />
          <button className="primary-action" type="button" onClick={() => {
            setNotice("");
            setCreating(true);
          }}>Vincular apoderado</button>
        </>
      )}
      renderRows={(students) => students.map(student => <tr key={student.id}>
        <td data-label="Estudiante"><PersonCell name={student.persona.nombreCompleto} detail={student.codigoAlumno} /></td>
        <td data-label="Apoderados activos">{student.apoderados.length ?
          <ul className={styles.inlineList}>{student.apoderados.map(link => <li key={link.id}>
            {link.nombreCompleto}<span className={styles.muted}>
              {" · "}{kinshipLabel(link.parentesco)}{link.principal ? " · Principal" : ""}
            </span>
          </li>)}</ul> : <span className={styles.muted}>Sin apoderados vinculados</span>}
        </td>
        <td data-label="Acción"><button type="button" aria-label={`Gestionar vínculos de ${student.persona.nombreCompleto}`}
          onClick={() => setSelected(student)}>Gestionar vínculos</button></td>
      </tr>)}
      detail={(refresh) => <>
        {creating && <RelationshipCreateDialog onClose={() => setCreating(false)} onSaved={() => {
          setCreating(false);
          setNotice("Apoderado vinculado correctamente.");
          refresh();
        }} />}
        {selected && <RelationshipManagementDialog key={selected.id} student={selected}
          onClose={() => setSelected(null)} onChanged={refresh} />}
      </>}
    />
    </>
  );
}

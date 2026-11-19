"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { FormDialog } from "@/shared/ui";
import { RecordPicker } from "./record-picker";
import {
  buscarApoderados,
  buscarEstudiantes,
  vincularApoderado,
  type Parentesco,
  type PersonaOpcion,
} from "./services/registro-service";

const PARENTESCOS: { value: Parentesco; label: string }[] = [
  { value: "MADRE", label: "Madre" },
  { value: "PADRE", label: "Padre" },
  { value: "TUTOR_LEGAL", label: "Tutor legal" },
  { value: "OTRO_FAMILIAR", label: "Otro familiar" },
  { value: "NO_DECLARADO", label: "No declarado" },
];

/** "+ Vincular apoderado": une a un estudiante con un apoderado y fija el parentesco. */
export function LinkGuardianDialog({
  student,
  onClose,
  onSaved,
}: {
  /** Estudiante ya elegido cuando se abre desde su fila. */
  student?: PersonaOpcion;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [chosenStudent, setChosenStudent] = useState<PersonaOpcion | null>(student ?? null);
  const [guardian, setGuardian] = useState<PersonaOpcion | null>(null);
  const [relationship, setRelationship] = useState<Parentesco>("MADRE");
  const [principal, setPrincipal] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!chosenStudent || !guardian) {
      setError("Elige al estudiante y al apoderado para vincularlos.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await vincularApoderado(chosenStudent.id, {
        apoderadoId: guardian.id,
        parentesco: relationship,
        principal,
      });
      onSaved();
      onClose();
    } catch (problem) {
      setError(message(problem));
    } finally {
      setBusy(false);
    }
  }

  return (
    <FormDialog
      title="Vincular apoderado"
      description="Relaciona a un apoderado con un estudiante."
      busy={busy}
      error={error}
      onClose={onClose}
      onSubmit={submit}
      submitLabel="Vincular"
    >
      <RecordPicker
        label="Estudiante"
        search={buscarEstudiantes}
        selected={chosenStudent}
        onSelect={setChosenStudent}
        disabled={busy}
      />
      <RecordPicker
        label="Apoderado"
        search={buscarApoderados}
        selected={guardian}
        onSelect={setGuardian}
        disabled={busy}
      />
      <label className="field">
        Parentesco *
        <select value={relationship} onChange={(event) => setRelationship(event.target.value as Parentesco)}>
          {PARENTESCOS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </label>
      <label className="check-field full-field">
        <input type="checkbox" checked={principal} onChange={(event) => setPrincipal(event.target.checked)} />
        Es el apoderado principal
      </label>
    </FormDialog>
  );
}

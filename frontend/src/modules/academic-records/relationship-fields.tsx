"use client";

import type { Kinship } from "./services/relationship-service";
import styles from "./academic-records.module.css";

const kinships: Record<Kinship, string> = {
  PADRE: "Padre",
  MADRE: "Madre",
  TUTOR_LEGAL: "Tutor legal",
  OTRO_FAMILIAR: "Otro familiar",
  NO_DECLARADO: "Sin declarar",
};

export function kinshipLabel(value: string) {
  return kinships[value as Kinship] ?? "Sin declarar";
}

export function RelationshipFields({ kinship = "NO_DECLARADO", principal = false }: {
  kinship?: Kinship;
  principal?: boolean;
}) {
  return <>
    <label className="field">
      Parentesco
      <select name="parentesco" defaultValue={kinship}>
        {Object.entries(kinships).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select>
    </label>
    <label className={styles.principalField}>
      <input type="checkbox" name="principal" defaultChecked={principal} />
      Apoderado principal
    </label>
  </>;
}

export function readRelationshipFields(form: FormData) {
  return {
    parentesco: String(form.get("parentesco")) as Kinship,
    principal: form.has("principal"),
  };
}

"use client";
import { useState } from "react";
import { Field } from "@/shared/ui";
import type { Persona } from "@/shared/types/person";
type PersonDefaults = { [K in keyof Persona]?: Persona[K] | null };
export function PersonFields({ person, requireSecondSurname = true, errors = {} }: {
  person?: PersonDefaults;
  requireSecondSurname?: boolean;
  errors?: Record<string, string>;
}) {
  const error = (field: string) => errors[`persona.${field}`] ?? errors[field];
  const [documentType, setDocumentType] = useState(
    person?.tipoDocumento || "DNI",
  );
  const [maxBirth] = useState(() =>
    new Date(Date.now() - 86400000).toISOString().slice(0, 10),
  );
  return (
    <div className="form-grid">
      <Field
        label="Nombres"
        name="nombres"
        required
        maxLength={100}
        defaultValue={person?.nombres ?? undefined}
        error={error("nombres")}
      />
      <Field
        label="Apellido paterno"
        name="apellidoPaterno"
        required
        maxLength={80}
        defaultValue={person?.apellidoPaterno ?? undefined}
        error={error("apellidoPaterno")}
      />
      <Field
        label="Apellido materno"
        name="apellidoMaterno"
        required={requireSecondSurname}
        maxLength={80}
        defaultValue={person?.apellidoMaterno ?? undefined}
        error={error("apellidoMaterno")}
      />
      <label className="field">
        Tipo de documento *
        <select
          name="tipoDocumento"
          value={documentType}
          onChange={(e) => setDocumentType(e.target.value)}
        >
          <option>DNI</option>
          <option value="CARNET_EXTRANJERIA">Carné de extranjería</option>
          <option>PASAPORTE</option>
        </select>
      </label>
      <Field
        label="Número de documento"
        name="numeroDocumento"
        required
        pattern={documentType === "DNI" ? "[0-9]{8}" : "[A-Za-z0-9\\-]{6,20}"}
        maxLength={documentType === "DNI" ? 8 : 20}
        defaultValue={person?.numeroDocumento ?? undefined}
        error={error("numeroDocumento")}
      />
      <Field
        label="Fecha de nacimiento"
        name="fechaNacimiento"
        type="date"
        required
        max={maxBirth}
        defaultValue={person?.fechaNacimiento ?? undefined}
        error={error("fechaNacimiento")}
      />
      <label className="field">
        Sexo *
        <select
          name="sexo"
          defaultValue={person?.sexo || "PREFIERE_NO_INDICAR"}
        >
          <option value="FEMENINO">Femenino</option>
          <option value="MASCULINO">Masculino</option>
          <option value="OTRO">Otro</option>
          <option value="PREFIERE_NO_INDICAR">Prefiere no indicar</option>
        </select>
      </label>
      <Field
        label="Correo de contacto"
        name="personaCorreo"
        type="email"
        maxLength={150}
        defaultValue={person?.correo ?? undefined}
        error={error("correo")}
      />
      <Field
        label="Teléfono"
        name="telefono"
        pattern={"[0-9+\\(\\) \\-]{7,20}"}
        defaultValue={person?.telefono || ""}
        error={error("telefono")}
      />
      <Field
        label="Dirección"
        name="direccion"
        maxLength={250}
        defaultValue={person?.direccion || ""}
        error={error("direccion")}
      />
    </div>
  );
}
export function personData(form: FormData, nationality: number | null = null) {
  const str = (key: string) => String(form.get(key) || "").trim();
  return {
    nombres: str("nombres"),
    apellidoPaterno: str("apellidoPaterno"),
    apellidoMaterno: str("apellidoMaterno"),
    tipoDocumento: str("tipoDocumento"),
    numeroDocumento: str("numeroDocumento"),
    fechaNacimiento: str("fechaNacimiento"),
    sexo: str("sexo"),
    correo: str("personaCorreo") || null,
    telefono: str("telefono") || null,
    direccion: str("direccion") || null,
    nacionalidadId: nationality,
  };
}

"use client";
import { useRef, useState, type FormEvent } from "react";
import { fieldErrorsOf, message } from "@/core/api/client";
import { Field, FormDialog } from "@/shared/ui";
import { classroomTypeLabels } from "../lib/labels";
import {
  CLASSROOM_CAPACITY_MAX,
  CLASSROOM_CAPACITY_MIN,
  CLASSROOM_CODE_MAX,
  CLASSROOM_LOCATION_MAX,
  CLASSROOM_NAME_MAX,
  hasErrors,
  validateClassroom,
  type ClassroomErrors,
  type ClassroomFormValues,
} from "../lib/validation";
import { PendingBadge } from "../pending/pending-badge";
import { CLASSROOM_TYPES, type Classroom, type ClassroomType } from "../types";
import { actualizarAula, crearAula } from "./services/aulaService";
/** Alta de un aula o edición de una existente (mismo formulario, "Nueva aula" del prototipo). */
export function ClassroomFormDialog({
  classroom,
  classrooms,
  onClose,
  onSaved,
}: {
  /** Si viene, se edita esa aula; si no, se crea una nueva. */
  classroom?: Classroom;
  /** Aulas ya cargadas, para avisar de un código repetido antes de enviar. */
  classrooms: Classroom[];
  onClose: () => void;
  onSaved: (classroom: Classroom) => void;
}) {
  const [values, setValues] = useState<ClassroomFormValues>({
    codigo: classroom?.codigo ?? "",
    nombre: classroom?.nombre ?? "",
    tipoAula: classroom?.tipoAula ?? "AULA_REGULAR",
    capacidad: classroom ? String(classroom.capacidad) : "",
    ubicacion: classroom?.ubicacion ?? "",
  });
  const [errors, setErrors] = useState<ClassroomErrors>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const sending = useRef(false);
  const set = (key: keyof ClassroomFormValues, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  };
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current) return;
    const found = validateClassroom(values, {
      classrooms,
      currentId: classroom?.id,
    });
    setErrors(found);
    if (hasErrors(found)) return;
    const input = {
      codigo: values.codigo.trim(),
      nombre: values.nombre.trim(),
      tipoAula: values.tipoAula as ClassroomType,
      capacidad: Number(values.capacidad),
      ubicacion: values.ubicacion.trim() || null,
    };
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      onSaved(
        await (classroom ? actualizarAula(classroom, input) : crearAula(input)),
      );
    } catch (e) {
      setError(message(e));
      setErrors(fieldErrorsOf(e));
      sending.current = false;
      setBusy(false);
    }
  }
  return (
    <FormDialog
      title={classroom ? "Editar aula" : "Nueva aula"}
      description={
        classroom
          ? `${classroom.nombre} · ${classroom.codigo}`
          : "Registra un aula para asignarla en los bloques de horario."
      }
      submitLabel={classroom ? "Guardar cambios" : "Guardar aula"}
      busy={busy}
      error={error}
      onClose={onClose}
      onSubmit={save}
      noValidate
    >
      <Field
        label="Código / Identificador"
        name="codigo"
        required
        maxLength={CLASSROOM_CODE_MAX}
        placeholder="Ej. AULA-301"
        hint={
          classroom
            ? "No se puede cambiar si el aula ya se usa en horarios."
            : "Único; se guarda en mayúsculas."
        }
        value={values.codigo}
        error={errors.codigo}
        disabled={busy}
        onChange={(e) => set("codigo", e.target.value)}
      />
      <Field
        label="Nombre del aula"
        name="nombre"
        required
        maxLength={CLASSROOM_NAME_MAX}
        placeholder="Ej. Aula 301"
        value={values.nombre}
        error={errors.nombre}
        disabled={busy}
        onChange={(e) => set("nombre", e.target.value)}
      />
      <Field
        label="Capacidad (estudiantes)"
        name="capacidad"
        type="number"
        inputMode="numeric"
        required
        min={CLASSROOM_CAPACITY_MIN}
        max={CLASSROOM_CAPACITY_MAX}
        step={1}
        placeholder="Ej. 30"
        hint={
          classroom
            ? "No puede quedar por debajo de la capacidad de las secciones que la usan."
            : `Entre ${CLASSROOM_CAPACITY_MIN} y ${CLASSROOM_CAPACITY_MAX} estudiantes.`
        }
        value={values.capacidad}
        error={errors.capacidad}
        disabled={busy}
        onChange={(e) => set("capacidad", e.target.value)}
      />
      <label className="field">
        Estado
        <select
          name="estado"
          disabled
          value={classroom && !classroom.activo ? "INHABILITADA" : "DISPONIBLE"}
        >
          <option value="DISPONIBLE">Disponible</option>
          <option value="INHABILITADA">Inhabilitada</option>
        </select>
        <small className="field-hint">
          {classroom
            ? "Se cambia con «Inhabilitar» o «Habilitar» en el menú del aula."
            : "Las aulas nuevas quedan disponibles."}
        </small>
      </label>
      <label className="field full-field">
        Tipo de aula
        <span className="field-required"> *</span>
        <select
          name="tipoAula"
          required
          value={values.tipoAula}
          disabled={busy}
          aria-invalid={errors.tipoAula ? true : undefined}
          onChange={(e) => set("tipoAula", e.target.value)}
        >
          <option value="">Seleccionar tipo</option>
          {CLASSROOM_TYPES.map((type) => (
            <option key={type} value={type}>
              {classroomTypeLabels[type]}
            </option>
          ))}
        </select>
        {errors.tipoAula && (
          <small className="field-error" role="alert">
            {errors.tipoAula}
          </small>
        )}
      </label>
      <Field
        label="Ubicación / Pabellón"
        name="ubicacion"
        fullWidth
        maxLength={CLASSROOM_LOCATION_MAX}
        placeholder="Ej. Pabellón B — 2° piso"
        hint="Opcional."
        value={values.ubicacion}
        error={errors.ubicacion}
        disabled={busy}
        onChange={(e) => set("ubicacion", e.target.value)}
      />
      <label className="field full-field">
        <span>
          Equipamiento <PendingBadge feature="classroomEquipment" />
        </span>
        <input
          name="equipamiento"
          disabled
          placeholder="Ej. Proyector, Pizarra, Computadoras"
        />
      </label>
    </FormDialog>
  );
}

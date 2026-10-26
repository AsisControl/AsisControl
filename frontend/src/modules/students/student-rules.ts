import type {
  Student,
  StudentDraft,
  StudentErrors,
  StudentInput,
} from "./types";

export const STUDENT_MIN_AGE = 10;
export const STUDENT_MAX_AGE = 20;
export const STUDENT_MIN_REASON_LENGTH = 10;
export const STUDENT_MAX_REASON_LENGTH = 1000;
export const STUDENT_DOCUMENT_RULES = {
  DNI: {
    max: 8,
    pattern: /^\d{8}$/,
    placeholder: "Ej. 12345678",
  },
  CARNET_EXTRANJERIA: {
    max: 9,
    pattern: /^\d{9}$/,
    placeholder: "Ej. 001234567",
  },
  PASAPORTE: {
    max: 12,
    pattern: /^[A-Za-z0-9]{8,12}$/,
    placeholder: "Ej. AB123456",
  },
} as const;

const STUDENT_NAME_FIELDS = [
  "nombres",
  "apellidoPaterno",
  "apellidoMaterno",
] as const;
const STUDENT_SENSITIVE_FIELDS = [
  ...STUDENT_NAME_FIELDS,
  "tipoDocumento",
  "numeroDocumento",
  "fechaNacimiento",
] as const;
const STUDENT_SEX_OPTIONS = [
  "FEMENINO",
  "MASCULINO",
  "OTRO",
  "PREFIERE_NO_INDICAR",
];

export function sanitizeStudentField(
  fieldName: keyof StudentDraft,
  value: string,
  documentType = "",
) {
  if (STUDENT_NAME_FIELDS.some((nameField) => nameField === fieldName)) {
    return value
      .normalize("NFC")
      .replace(/[^\p{L}\p{M} '\-’]/gu, "")
      .slice(0, fieldName === "nombres" ? 100 : 80);
  }
  if (fieldName === "telefono") {
    return value.replace(/\D/g, "").slice(0, 9);
  }
  if (fieldName === "numeroDocumento") {
    return sanitizeDocumentNumber(value, documentType);
  }
  return value;
}

function sanitizeDocumentNumber(value: string, documentType: string) {
  const rule =
    STUDENT_DOCUMENT_RULES[documentType as keyof typeof STUDENT_DOCUMENT_RULES];
  const numericOnly =
    documentType === "DNI" || documentType === "CARNET_EXTRANJERIA";
  const cleanedValue = numericOnly
    ? value.replace(/\D/g, "")
    : value.replace(/[^A-Za-z0-9]/g, "").toUpperCase();
  return cleanedValue.slice(0, rule?.max ?? 12);
}

export function studentBirthRange(today = todayInLima()) {
  const [year, month, day] = today.split("-").map(Number);
  // Se usan cumpleaños y se ajusta el 29 de febrero al año de destino.
  function subtractYears(years: number) {
    const lastDay = new Date(Date.UTC(year - years, month, 0)).getUTCDate();
    return new Date(Date.UTC(year - years, month - 1, Math.min(day, lastDay)));
  }
  const oldestBirthDate = subtractYears(STUDENT_MAX_AGE + 1);
  oldestBirthDate.setUTCDate(oldestBirthDate.getUTCDate() + 1);
  const newestBirthDate = subtractYears(STUDENT_MIN_AGE);
  return {
    min: oldestBirthDate.toISOString().slice(0, 10),
    max: newestBirthDate.toISOString().slice(0, 10),
  };
}

export function todayInLima(now = new Date()): string {
  const dateParts = new Intl.DateTimeFormat("en-US", {
    timeZone: "America/Lima",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(now);
  const getPart = (type: string) =>
    dateParts.find((datePart) => datePart.type === type)?.value;
  return `${getPart("year")}-${getPart("month")}-${getPart("day")}`;
}

export function studentDraft(student?: Student): StudentDraft {
  const persona = student?.persona;
  return {
    nombres: persona?.nombres ?? "",
    apellidoPaterno: persona?.apellidoPaterno ?? "",
    apellidoMaterno: persona?.apellidoMaterno ?? "",
    tipoDocumento: persona?.tipoDocumento ?? "",
    numeroDocumento: persona?.numeroDocumento ?? "",
    fechaNacimiento: persona?.fechaNacimiento ?? "",
    sexo: persona?.sexo ?? "",
    correo: persona?.correo ?? "",
    telefono: persona?.telefono ?? "",
    direccion: persona?.direccion ?? "",
    motivo: "",
  };
}

export function studentInput(
  draft: StudentDraft,
  original?: Student,
  confirmarHomonimia = false,
): StudentInput {
  const optionalValue = (text: string) => text.trim() || null;
  return {
    persona: {
      nombres: draft.nombres.trim(),
      apellidoPaterno: draft.apellidoPaterno.trim(),
      apellidoMaterno: optionalValue(draft.apellidoMaterno),
      tipoDocumento: optionalValue(draft.tipoDocumento),
      numeroDocumento: optionalValue(draft.numeroDocumento.toUpperCase()),
      fechaNacimiento: draft.fechaNacimiento,
      sexo: optionalValue(draft.sexo),
      correo: optionalValue(draft.correo),
      telefono: optionalValue(draft.telefono),
      direccion: optionalValue(draft.direccion),
      nacionalidadId: original?.persona.nacionalidadId ?? null,
    },
    ...(draft.motivo.trim() ? { motivo: draft.motivo.trim() } : {}),
    ...(confirmarHomonimia ? { confirmarHomonimia: true } : {}),
    ...(original?.version !== undefined ? { version: original.version } : {}),
  };
}

export function hasSensitiveChanges(
  draft: StudentDraft,
  original?: Student,
): boolean {
  if (!original) return false;
  const before = studentInput(studentDraft(original)).persona;
  const after = studentInput(draft).persona;
  return STUDENT_SENSITIVE_FIELDS.some(
    (fieldName) => before[fieldName] !== after[fieldName],
  );
}

export function reasonError(reason: string): string {
  const length = reason.trim().length;
  return length < STUDENT_MIN_REASON_LENGTH ||
    length > STUDENT_MAX_REASON_LENGTH
    ? "Escribe un motivo de 10 a 1000 caracteres."
    : "";
}

function validateNames(draft: StudentDraft): StudentErrors {
  const errors: StudentErrors = {};
  for (const [fieldName, label] of [
    ["nombres", "Los nombres"],
    ["apellidoPaterno", "El primer apellido"],
  ] as const) {
    const length = draft[fieldName].trim().length;
    const maximumLength = fieldName === "nombres" ? 100 : 80;
    if (length < 2 || length > maximumLength) {
      errors[fieldName] =
        `${label}: escribe entre 2 y ${maximumLength} caracteres.`;
    }
  }
  for (const fieldName of STUDENT_NAME_FIELDS) {
    const value = draft[fieldName];
    const allowedCharacters = /^[\p{L}\p{M} '\-’]+$/u.test(value);
    if (value.trim() && (!allowedCharacters || !/\p{L}/u.test(value))) {
      errors[fieldName] =
        "Usa solo letras, espacios, guion o apóstrofo; no números ni otros símbolos.";
    }
  }
  if (draft.apellidoMaterno.trim().length > 80) {
    errors.apellidoMaterno = "El segundo apellido admite hasta 80 caracteres.";
  }
  return errors;
}

function validateBirthDate(birthDate: string, today: string): StudentErrors {
  const parsedDate = new Date(`${birthDate}T00:00:00Z`);
  const validDate =
    /^\d{4}-\d{2}-\d{2}$/.test(birthDate) &&
    Number.isFinite(parsedDate.getTime()) &&
    parsedDate.toISOString().slice(0, 10) === birthDate &&
    birthDate >= "0001-01-01";
  if (!validDate) {
    return { fechaNacimiento: "Ingresa una fecha de nacimiento válida." };
  }
  if (birthDate > today) {
    return { fechaNacimiento: "La fecha de nacimiento no puede ser futura." };
  }
  const range = studentBirthRange(today);
  if (birthDate < range.min || birthDate > range.max) {
    return {
      fechaNacimiento:
        "Para esta ficha de secundaria, el estudiante debe tener entre " +
        "10 y 20 años cumplidos. Revisa el día, mes y año.",
    };
  }
  return {};
}

function documentNumberError(documentType: string): string {
  if (documentType === "DNI") {
    return "El DNI debe tener exactamente 8 dígitos.";
  }
  if (documentType === "CARNET_EXTRANJERIA") {
    return "El carné de extranjería debe tener exactamente 9 dígitos.";
  }
  return "El pasaporte debe tener de 8 a 12 letras o números, sin espacios ni guiones.";
}

function validateDocument(draft: StudentDraft): StudentErrors {
  const errors: StudentErrors = {};
  const documentType = draft.tipoDocumento;
  const documentNumber = draft.numeroDocumento.trim();
  const rule =
    STUDENT_DOCUMENT_RULES[documentType as keyof typeof STUDENT_DOCUMENT_RULES];
  if (!documentType && documentNumber) {
    errors.tipoDocumento = "Selecciona el tipo del documento informado.";
  }
  if (documentType && !documentNumber) {
    errors.numeroDocumento =
      "Completa el número o deja ambos campos sin documento.";
  }
  if (documentType && !rule) {
    errors.tipoDocumento = "Selecciona un tipo de documento válido.";
  }
  if (documentType && documentNumber && !rule?.pattern.test(documentNumber)) {
    errors.numeroDocumento = documentNumberError(documentType);
  }
  return errors;
}

function validateContact(draft: StudentDraft): StudentErrors {
  const errors: StudentErrors = {};
  const email = draft.correo.trim();
  if (
    email &&
    (email.length > 150 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))
  ) {
    errors.correo = "Ingresa un correo válido de hasta 150 caracteres.";
  }
  if (draft.telefono.trim() && !/^9\d{8}$/.test(draft.telefono.trim())) {
    errors.telefono =
      "El teléfono debe tener exactamente 9 dígitos y empezar con 9.";
  }
  if (draft.direccion.trim().length > 250) {
    errors.direccion = "La dirección admite hasta 250 caracteres.";
  }
  return errors;
}

export function validateStudent(
  draft: StudentDraft,
  original?: Student,
  today = todayInLima(),
): StudentErrors {
  const errors: StudentErrors = {
    ...validateNames(draft),
    ...validateBirthDate(draft.fechaNacimiento, today),
    ...validateDocument(draft),
    ...validateContact(draft),
  };
  if (draft.sexo && !STUDENT_SEX_OPTIONS.includes(draft.sexo)) {
    errors.sexo = "Selecciona una opción válida.";
  }
  if (hasSensitiveChanges(draft, original) || draft.motivo.trim()) {
    const invalidReason = reasonError(draft.motivo);
    if (invalidReason) errors.motivo = invalidReason;
  }
  return errors;
}

export function studentName(student: Student): string {
  const persona = student.persona;
  return (
    persona.nombreCompleto ||
    [persona.nombres, persona.apellidoPaterno, persona.apellidoMaterno]
      .filter(Boolean)
      .join(" ")
  );
}

export function birthLabel(date: string | null | undefined): string {
  if (!date) return "Sin registrar";
  // Una fecha civil no se convierte a UTC: evita mostrar el día anterior en Perú.
  const [year, month, day] = date.split("-");
  return `${day}/${month}/${year}`;
}

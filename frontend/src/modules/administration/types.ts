/** Valores del enum Especialidad del backend (perfil docente al crear un acceso). */
export const teacherSpecialties = [
  "MATEMATICA",
  "COMUNICACION",
  "CIENCIA_Y_TECNOLOGIA",
  "CIENCIAS_SOCIALES",
  "EDUCACION_FISICA",
  "INGLES",
  "ARTE_Y_CULTURA",
  "TUTORIA",
] as const;
export type Account = {
  id: number;
  personaId: number;
  username: string;
  correo: string;
  nombreCompleto: string;
  rolId: number;
  rol: string;
  estado: string;
  requiereCambioContrasena: boolean;
  intentosFallidos: number;
  bloqueadoHasta: string | null;
  ultimoAcceso: string | null;
  fechaCreacion: string;
  correoVerificado: boolean;
  contrasenaTemporalExpira: string | null;
};
export type Permission = {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string | null;
  categoria: string | null;
};
export type Role = {
  id: number;
  nombre: string;
  descripcion: string;
  permisos: Permission[];
};
export type PersonChoice = {
  id: number;
  nombreCompleto: string;
  numeroDocumento: string;
  perfiles: string[];
  rolesConAcceso: string[];
};
/** Respuesta de GET /api/auditoria/registros (AuditResponse). */
export type Audit = {
  id: number;
  actorIdentificador: string;
  /** Nombre de la persona del acceso; null para "SISTEMA" o accesos eliminados. */
  actorNombre: string | null;
  rolActor: string | null;
  accion: string;
  modulo: string;
  entidad: string;
  registroId: string;
  fechaHora: string;
  resultado: "EXITOSO" | "RECHAZADO" | "FALLIDO";
  motivo: string | null;
  correlationId: string;
};

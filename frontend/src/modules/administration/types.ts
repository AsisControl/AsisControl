export type AccountRole = {
  id: number;
  nombre: string;
  activo: boolean;
  codigoPerfil: string | null;
};
export type Account = {
  id: number;
  personaId: number;
  /** Código permanente de la cuenta (AAAANNNN). */
  codigo: string;
  username: string;
  correo: string;
  nombreCompleto: string;
  roles: AccountRole[];
  rolId?: number;
  rol?: string;
  estado: string;
  requiereCambioContrasena: boolean;
  intentosFallidos: number;
  bloqueadoHasta: string | null;
  ultimoAcceso: string | null;
  fechaCreacion: string;
  correoVerificado: boolean;
  contrasenaTemporalExpira: string | null;
  /** Código institucional del perfil del acceso (EST20260001, DOC20260003…). */
  codigoPerfil?: string | null;
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
  /** IP desde la que se hizo la operación; null en procesos internos. */
  direccionIp: string | null;
  /** Valores antes y después del cambio, tal como los guarda el backend. */
  valorAnterior: string | null;
  nuevoValor: string | null;
};

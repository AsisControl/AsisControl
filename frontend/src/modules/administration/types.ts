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
export type Audit = {
  id: number;
  actorIdentificador: string;
  rolActor: string;
  accion: string;
  modulo: string;
  entidad: string;
  registroId: string;
  fechaHora: string;
  resultado: string;
  motivo: string | null;
};

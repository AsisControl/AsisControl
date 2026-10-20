export type History = {
  id: number;
  actor: string;
  rol: string;
  fecha: string;
  accion: string;
  motivo: string | null;
  anterior: string | null;
  nuevo: string | null;
};

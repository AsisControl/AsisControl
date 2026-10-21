export type Persona = {
  id: number;
  nombres: string;
  apellidoPaterno: string;
  apellidoMaterno: string;
  nombreCompleto: string;
  tipoDocumento: string;
  numeroDocumento: string;
  sexo: string;
  fechaNacimiento: string;
  correo: string;
  telefono: string | null;
  direccion: string | null;
  nacionalidadId: number | null;
};

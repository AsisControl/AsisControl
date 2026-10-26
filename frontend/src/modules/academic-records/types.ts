/** PersonaDtos.PersonaResponse: los datos opcionales pueden venir nulos. */
export type RecordPerson = {
  id: number;
  nombres: string;
  apellidoPaterno: string;
  apellidoMaterno: string | null;
  nombreCompleto: string;
  tipoDocumento: string | null;
  numeroDocumento: string | null;
  nacionalidad: string | null;
  sexo: string | null;
  fechaNacimiento: string;
  telefono: string | null;
  correo: string | null;
  direccion: string | null;
  activo: boolean;
};
/** PersonaDtos.ApoderadoResponse. */
export type Guardian = {
  id: number;
  persona: RecordPerson;
  alumnos: {
    id: number;
    alumnoId: number;
    codigoAlumno: string;
    nombreCompleto: string;
    parentesco: string;
    principal: boolean;
  }[];
  activo: boolean;
};
/** PersonaDtos.DocenteResponse. */
export type Teacher = {
  id: number;
  codigoDocente: string;
  fechaIngreso: string;
  especialidad: string;
  persona: RecordPerson;
  activo: boolean;
};

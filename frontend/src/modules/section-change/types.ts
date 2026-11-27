/** Datos de una matrícula necesarios para cambiar su sección. */
export type SectionEnrollment = {
  id: number;
  alumno: string;
  codigoAlumno: string;
  seccionId: number | null;
  seccion: string;
  grado: number;
  anioAcademico: number;
  estado: string;
  version: number;
};

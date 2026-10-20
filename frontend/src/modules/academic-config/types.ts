export type Period = {
  id: number;
  nombre: string;
  orden: number;
  fechaInicio: string;
  fechaFin: string;
  estado: string;
  version: number;
};
export type Grade = {
  id: number;
  numero: number;
  capacidadDefault: number;
  activo: boolean;
  version: number;
};
export type Section = {
  id: number;
  nombre: string;
  grado: number;
  anioAcademico: number;
  capacidadMaxima: number;
  ocupacion: number;
  vacantes: number;
  activo: boolean;
  version: number;
};
export type AcademicYear = {
  id: number;
  anio: number;
  fechaInicio: string;
  fechaFin: string;
  estado: string;
  version: number;
  admisionAbierta: boolean;
  periodos: Period[];
  grados: Grade[];
  secciones: Section[];
};
/** Props comunes de los paneles que trabajan sobre el año seleccionado. */
export type YearPanelProps = {
  year: AcademicYear;
  /** false cuando el año está cerrado: solo consulta histórica. */
  editable: boolean;
  /** Recibe el año devuelto por el backend tras guardar. */
  onYearChange: (year: AcademicYear) => void;
  /** Vuelve a consultar el año al backend. */
  onReload: () => Promise<void>;
  /** Muestra un mensaje de éxito en la cabecera ("" lo limpia). */
  onSuccess: (message: string) => void;
};

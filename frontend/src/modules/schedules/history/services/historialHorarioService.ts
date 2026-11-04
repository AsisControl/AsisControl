import type { Classroom } from "../../types";
import { HORARIOS_PATH, listAll } from "../../timetable/services/horarioService";
/** Ruta del historial (US-27); el listado paginado se consume con usePaged. */
export const HISTORIAL_PATH = `${HORARIOS_PATH}/historial`;
/** Filtros de la pantalla; un texto vacío significa "sin filtro". */
export type ScheduleHistoryFilters = {
  anioAcademico: string;
  seccionId: string;
  docenteId: string;
  aulaId: string;
  desde: string;
  hasta: string;
};
/** Filtros en el formato de usePaged ("anioAcademico=2026&aulaId=3"). */
export function historyFilters(filters: ScheduleHistoryFilters) {
  return new URLSearchParams(
    Object.entries(filters).filter(([, value]) => value !== ""),
  ).toString();
}
/** Habilitadas e inhabilitadas: el historial también recuerda las aulas que ya no se usan. */
export function listarTodasLasAulas() {
  return listAll<Classroom>("/api/aulas?estado=TODAS&sort=codigo");
}

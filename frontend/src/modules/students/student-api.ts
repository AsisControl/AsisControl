// Compatibilidad con las importaciones de FE2 existentes antes de v0.4.
export {
  getStudent,
  saveStudent,
  canPerform,
  changeStudentState,
} from "./services/student-service";
export {
  homonyms,
  studentFieldErrors,
  studentErrorMessage,
} from "./student-errors";

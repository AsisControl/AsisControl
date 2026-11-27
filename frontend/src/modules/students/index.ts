// API pública del módulo de estudiantes (US-09). Otros módulos importan solo desde aquí.
export { Students } from "./students";
export { StudentDetail } from "./student-detail";
export { getStudent } from "./services/student-service";
export type { Student } from "./types";
export { FamilyImportDialog, ImportSummary, validateImportFile } from "./import-dialog";
export { DownloadIcon, ExcelFilePicker } from "./excel-file-picker";
export type { AccessCredential, ImportResult } from "./types";
export { AccessCredentials, NewAccountCredentials } from "./access-credentials";

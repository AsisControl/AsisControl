// API pública del módulo de Horario Académico (menú Horarios: aulas y, más adelante, horarios).
export { Schedules } from "./schedules";
export { TeacherSchedule } from "./teacher/teacher-schedule";
export { StudentSchedule } from "./student/student-schedule";
/** Todas las aulas habilitadas, para elegirlas desde otros módulos. */
export { listarAulasHabilitadas } from "./timetable/services/horarioService";
export type { Classroom, ClassroomSchedule } from "./types";
export type { ScheduleBlock } from "./types";

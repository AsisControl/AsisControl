export const reportAssignment = {
  id: 5, cursoId: 10, curso: "Matemática", seccionId: 8, seccion: "A", grado: 3, anioAcademico: 2026,
};
export const availableReport = {
  idAlumno: 11, alumno: "Ana Ramos", idAsignacionCurso: 5, curso: "Matemática",
  anio: 2026, idPeriodoAcademico: 40, periodo: "Primer bimestre",
};
export const progressReport = {
  alumno: "Ana Ramos", codigoAlumno: "E11", curso: "Matemática", docente: "María Pérez",
  grado: 3, seccion: "A", anio: 2026, periodo: "Primer bimestre", fechas: "01/03/2026 - 30/04/2026",
  generadoEn: "2026-04-30T20:00:00Z",
  filas: [
    { idEvaluacion: 20, competencia: "Resuelve problemas", evaluacion: "Práctica", fecha: "15/03/2026", resultado: "AD", ponderacion: "30 %", observacion: "Explica su procedimiento.\nDebe revisar las unidades." },
    { idEvaluacion: 21, competencia: "Argumenta", evaluacion: "Examen", fecha: "30/04/2026", resultado: "Sin registrar", ponderacion: "70 %", observacion: "Sin observaciones" },
  ],
};

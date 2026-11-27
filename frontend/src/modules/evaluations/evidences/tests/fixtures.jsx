import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, signIn } from "../../../../../tests/support/backend.js";
import { setUrl } from "../../../../../tests/support/navigation.js";
import { Evaluations } from "@/modules/evaluations";

export const assignment = { id: 5, cursoId: 100, curso: "Matemática", seccionId: 8, seccion: "A", grado: 3, anioAcademico: 2026 };
export const evaluation = {
  id: 10, idAsignacionCurso: 5, titulo: "Examen", estado: "BORRADOR", periodoEvaluacion: "BIMESTRAL",
  fechaEvaluacion: "2026-09-22", competencias: [{ id: 1 }], idCurso: 100, curso: "Matemática", idSeccion: 8, seccion: "3° A", anioAcademico: 2026,
};
export const students = [
  { idAlumno: 1, codigoAlumno: "E1", nombreCompleto: "Alvarado Medina, Diego", notas: [] },
  { idAlumno: 2, codigoAlumno: "E2", nombreCompleto: "Bravo Castillo, Ana", notas: [] },
];
export const evidence = (overrides = {}) => ({
  id: 70, asignacionId: 5, alumnoId: 1, cursoId: 100, competenciaId: 1, periodoId: 3, autorId: 9,
  descripcion: "Maqueta del sistema solar", archivo: null, evaluaciones: [],
  fechaCreacion: "2026-10-01T15:00:00", fechaActualizacion: "2026-10-01T15:00:00", ...overrides,
});
export const withFile = { id: 31, nombreOriginal: "maqueta.pdf", tipoMime: "application/pdf", tamanioBytes: 2048 };

export function backend(routes = {}) {
  return fakeBackend({
    "GET /api/asignaciones-cursos/mias": page([assignment]),
    "GET /api/evaluaciones": page([evaluation]),
    "GET /api/evaluaciones/:id/planilla": { evaluacion: evaluation, alumnos: students },
    "GET /api/evaluaciones/periodos-informe": [
      { id: 3, nombre: "I Bimestre", fechaInicio: "2026-08-01", fechaFin: "2026-09-22" },
      { id: 4, nombre: "II Bimestre", fechaInicio: "2026-10-01", fechaFin: "2026-11-30" },
    ],
    "GET /api/evaluaciones/competencias": [
      { id: 1, codigo: "MAT-C1", nombre: "Resuelve problemas" },
      { id: 2, codigo: "MAT-C2", nombre: "Razona en geometría" },
    ],
    "GET /api/evidencias-pedagogicas": page([]),
    ...routes,
  });
}
export function open({ canWrite = true } = {}) {
  signIn({
    role: "DOCENTE",
    permissions: ["CALIFICACIONES_LEER", ...(canWrite ? ["CALIFICACIONES_ESCRIBIR"] : [])],
  });
  setUrl("/evaluaciones/evidencias");
  return render(<Evaluations tab="evidencias" canWrite={canWrite} />);
}
/** Elige curso y estudiante y espera a que el selector de estudiantes se habilite. */
export async function chooseStudent(name = "Alvarado Medina, Diego") {
  const user = userEvent.setup();
  await user.selectOptions(await screen.findByLabelText("Curso y sección"), "5");
  const select = screen.getByLabelText(/Estudiante/);
  await screen.findByRole("option", { name });
  await user.selectOptions(select, name);
  return user;
}
export const pdf = (name = "maqueta.pdf") => new File(["%PDF"], name, { type: "application/pdf" });

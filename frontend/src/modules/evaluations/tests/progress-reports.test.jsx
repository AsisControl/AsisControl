import { test, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ProgressReports, PublishPeriod } from "../progress-reports";
import { fakeBackend, page, reply } from "../../../../tests/support/backend.js";
import { progressReport } from "./report-fixture";

const updatedReports = "Todas las libretas del período están publicadas y actualizadas.";
const staleReports = "Todos los estudiantes tienen una libreta publicada, pero existen cambios pendientes de actualizar.";
const staleWarning = "Las evaluaciones o notas cambiaron. Genera un nuevo borrador para actualizar la libreta.";
const pendingDraft = "Hay un borrador pendiente con los cambios actuales. Revísalo y publícalo cuando esté listo.";
const blockedDraft = "Hay borradores pendientes. Revísalos antes de publicar.";
const staleDraftImpediment = "Las evaluaciones o notas cambiaron. Vuelve a generar y revisar los borradores antes de publicar.";

test("El apoderado consulta tareas sin peso del estudiante seleccionado aunque no tenga libreta", async () => {
  const calls = fakeBackend({
    "GET /api/calificaciones/informes-disponibles": [],
    "GET /api/familia/estudiantes": [{ id: 8, nombreCompleto: "Ana" }, { id: 9, nombreCompleto: "Luis" }],
    "GET /api/tareas/familia": ({ query }) => page([
      { id: 1, nombre: query.alumnoId === "8" ? "Fracciones" : "Geometría", curso: "Matemática", fechaLimite: "2026-06-21T18:00:00" },
    ]),
  });
  const user = userEvent.setup();
  render(<ProgressReports guardian />);
  await user.selectOptions(await screen.findByLabelText("Estudiante"), "8");
  await user.click(screen.getByText("Tareas del estudiante"));
  expect(await screen.findByText(/Fracciones.*Sin peso/)).toBeTruthy();
  expect(calls.find(call => call.path === "/api/tareas/familia").query.alumnoId).toBe("8");
  await user.selectOptions(screen.getByLabelText("Estudiante"), "9");
  expect(screen.queryByText(/Fracciones/)).toBeNull();
  await user.click(screen.getByText("Tareas del estudiante"));
  expect(await screen.findByText(/Geometría.*Sin peso/)).toBeTruthy();
});

test("publicar requiere confirmar y envia curso y periodo", async () => {
  const ana = { idAlumno: 8, alumno: "Ana", idAsignacionCurso: 3, idPeriodoAcademico: 4 };
  const luis = { ...ana, idAlumno: 9, alumno: "Luis" };
  let generated = false;
  let published = false;
  const calls = fakeBackend({
    "GET /api/evaluaciones/periodos-informe": [{ id: 4, nombre: "Bimestre 1" }],
    "GET /api/calificaciones/informes/estado": () => ({ alumnos: [ana, luis].map(item => ({...item, borrador: generated && !published, publicada: published})), publicable: generated && !published, desactualizada: false }),
    "POST /api/calificaciones/informes/generar": () => { generated = true; return { informesProcesados: 2, informes: [ana, luis] }; },
    "POST /api/calificaciones/informes/publicar": () => {
      published = true;
      return { informesProcesados: 2 };
    },
    "GET /api/calificaciones/avance": { ...progressReport, alumno: "Luis", periodo: "Bimestre 1 | Version 2 | PUBLICADA" },
  });
  render(<PublishPeriod canWrite assignments={[{id:3,curso:"Matematica",seccion:"A",anioAcademico:2026}]} />);
  const user = userEvent.setup();
  await user.selectOptions(screen.getByLabelText("Curso y sección"), "3");
  await screen.findByRole("option", {name:"Bimestre 1"});
  await user.selectOptions(screen.getByLabelText("Período académico"), "4");
  await screen.findByText(/Todavía no hay libretas para este curso/);
  expect(screen.queryByRole("button", {name:"Publicar libretas"})).toBeNull();
  await user.click(screen.getByRole("button", {name:"Generar borradores"}));
  expect(await screen.findAllByRole("cell", {name:"BORRADOR"})).toHaveLength(2);
  expect(screen.getByText(pendingDraft)).toBeTruthy();
  await user.click(screen.getByRole("button", {name:"Publicar libretas"}));
  expect(calls.some(c => c.path.endsWith("/publicar"))).toBe(false);
  await user.click(screen.getByRole("button", {name:"Confirmar publicación"}));
  await screen.findByText(/Se publicaron 2 libretas/);
  expect(calls.find(c => c.path.endsWith("/publicar")).body).toEqual({idAsignacionCurso:3,idPeriodoAcademico:4});
  expect(await screen.findAllByRole("cell", {name:"PUBLICADA"})).toHaveLength(2);
  expect(screen.getByText(updatedReports)).toBeTruthy();
  expect(screen.queryByText(staleWarning)).toBeNull();
  expect(screen.queryByRole("button", {name:"Publicar libretas"})).toBeNull();
  expect(screen.queryByRole("cell", {name:"BORRADOR"})).toBeNull();
  await user.click(screen.getByRole("button", {name:"Ver libreta de Luis"}));
  await screen.findByText("Versión 2");
  expect(calls.find(c => c.path.endsWith("/avance")).query).toEqual({idAlumno:"9",idAsignacionCurso:"3",idPeriodoAcademico:"4"});
});

test("una publicación desactualizada cambia de aviso al generar el borrador y queda actualizada al publicar", async () => {
  const ana = { idAlumno: 8, alumno: "Ana", publicada: true };
  let generated = false;
  let published = false;
  fakeBackend({
    "GET /api/evaluaciones/periodos-informe": [{ id: 4, nombre: "Bimestre 1" }],
    "GET /api/calificaciones/informes/estado": () => ({
      alumnos: [{ ...ana, borrador: generated && !published }],
      publicable: generated && !published,
      desactualizada: !published,
    }),
    "POST /api/calificaciones/informes/generar": () => { generated = true; return { informesProcesados: 1 }; },
    "POST /api/calificaciones/informes/publicar": () => { published = true; return { informesProcesados: 1 }; },
    "GET /api/calificaciones/avance": () => ({
      ...progressReport, periodo: `Bimestre 1 | Version ${published ? 2 : 1} | PUBLICADA`,
    }),
  });
  render(<PublishPeriod canWrite assignments={[{ id: 3, curso: "Matemática", seccion: "A", anioAcademico: 2026 }]} />);
  const user = userEvent.setup();
  await user.selectOptions(screen.getByLabelText("Curso y sección"), "3");
  await screen.findByRole("option", { name: "Bimestre 1" });
  await user.selectOptions(screen.getByLabelText("Período académico"), "4");
  expect(await screen.findByText(staleWarning)).toBeTruthy();
  expect(screen.queryByText(staleReports)).toBeNull();
  expect(screen.queryByText(pendingDraft)).toBeNull();
  expect(screen.queryByText(blockedDraft)).toBeNull();
  expect(screen.queryByText(updatedReports)).toBeNull();
  await user.click(screen.getByRole("button", { name: "Ver libreta de Ana" }));
  await screen.findByText("Versión 1");
  await user.click(screen.getByRole("button", { name: "Generar borradores" }));
  await screen.findByText(pendingDraft);
  expect(screen.queryByText(staleWarning)).toBeNull();
  expect(screen.queryByText(blockedDraft)).toBeNull();
  expect(screen.queryByText(updatedReports)).toBeNull();
  expect(screen.getByRole("cell", { name: /BORRADOR.*PUBLICADA/ })).toBeTruthy();
  expect(screen.getByRole("button", { name: "Revisar borrador de Ana" })).toBeTruthy();
  expect(screen.getByRole("button", { name: "Ver libreta de Ana" })).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Publicar libretas" }));
  await user.click(screen.getByRole("button", { name: "Confirmar publicación" }));
  await screen.findByText(updatedReports);
  expect(screen.queryByText(pendingDraft)).toBeNull();
  expect(screen.queryByText(staleWarning)).toBeNull();
  expect(screen.queryByText(blockedDraft)).toBeNull();
  expect(screen.getByRole("cell", { name: "PUBLICADA" })).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Revisar borrador de Ana" })).toBeNull();
  await user.click(screen.getByRole("button", { name: "Ver libreta de Ana" }));
  await screen.findByText("Versión 2");
});

test("publicar una sección solo envía el curso elegido y muestra su error de validación", async () => {
  const first = { id: 3, curso: "Matemática", seccion: "A", seccionId: 11, anioAcademico: 2026 };
  const calls = fakeBackend({
    "GET /api/evaluaciones/periodos-informe": [{ id: 4, nombre: "Bimestre 1" }],
    "GET /api/calificaciones/informes/estado": { alumnos: [{idAlumno: 8, alumno: "Ana", borrador: true, publicada: false}], publicable: true },
    "POST /api/calificaciones/informes/publicar": reply(422, { message: "Completa los niveles de logro" }),
  });
  render(<PublishPeriod canWrite assignments={[
    first,
    { ...first, id: 5, curso: "Ciencia" },
    { ...first, id: 6, curso: "Historia", seccionId: 12 },
  ]} />);
  const user = userEvent.setup();
  await user.selectOptions(screen.getByLabelText("Curso y sección"), "3");
  await screen.findByRole("option", { name: "Bimestre 1" });
  await user.selectOptions(screen.getByLabelText("Período académico"), "4");
  expect(screen.queryByRole("button", { name: "Publicar mis cursos de esta sección" })).toBeNull();
  await user.click(await screen.findByRole("button", { name: "Publicar libretas" }));
  expect(screen.getByText(/libretas pendientes de Matemática, sección A, para Bimestre 1/)).toBeTruthy();
  expect(calls.some(call => call.method === "POST")).toBe(false);
  await user.click(screen.getByRole("button", { name: "Confirmar publicación" }));
  expect((await screen.findByRole("alert")).textContent).toContain("Completa los niveles de logro");
  expect(screen.queryByText(/Se publicaron/)).toBeNull();
  expect(calls.filter(call => call.method === "POST").map(call => call.body)).toEqual([
    { idAsignacionCurso: 3, idPeriodoAcademico: 4 },
  ]);
});

test("progreso muestra notas y descarga el informe elegido", async () => {
  const calls = fakeBackend({
    "GET /api/calificaciones/informes-disponibles": [{idAlumno:8,alumno:"Ana",idAsignacionCurso:3,curso:"Matematica",anio:2026,idPeriodoAcademico:4,periodo:"Bimestre 1"}],
    "GET /api/calificaciones/avance": {...progressReport,alumno:"Ana",curso:"Matematica",filas:[{competencia:"Cantidad",evaluacion:"Practica",fecha:"01/03/2026",resultado:"A",ponderacion:"30 %",observacion:"Buen avance"},{competencia:"Forma",evaluacion:"Practica",fecha:"01/03/2026",resultado:"Sin registrar",ponderacion:"30 %",observacion:"Sin observaciones"}]},
    "GET /api/calificaciones/reporte-avance": {},
  });
  URL.createObjectURL = vi.fn(() => "blob:pdf"); URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype,"click").mockImplementation(() => {});
  render(<ProgressReports />);
  await screen.findByRole("option", { name: /Bimestre 1/ });
  const user = userEvent.setup();
  await user.selectOptions(screen.getByLabelText("Período académico"), "2026-4");
  await user.selectOptions(screen.getByLabelText("Curso"), "3");
  await user.click(screen.getByText("Ver notas"));
  await screen.findByText("Buen avance");
  expect(screen.getByText("Sin registrar")).toBeTruthy();
  await user.click(screen.getByText("Descargar PDF"));
  expect(calls.find(c => c.path.endsWith("reporte-avance")).query).toEqual({idAlumno:"8",idAsignacionCurso:"3",idPeriodoAcademico:"4"});
});

test("sin notas publicadas no ofrece períodos", async () => {
  fakeBackend({ "GET /api/calificaciones/informes-disponibles": [] });
  render(<ProgressReports />);
  await screen.findByText(/Todavía no hay libretas publicadas/);
  expect(screen.queryByRole("combobox")).toBeNull();
});

test("el estudiante cambia de período y consulta los niveles y comentarios de la libreta correspondiente", async () => {
  const primerInforme = {
    idAlumno: 8, alumno: "Ana", idAsignacionCurso: 3, curso: "Matemática",
    anio: 2026, idPeriodoAcademico: 4, periodo: "Bimestre 1",
  };
  const calls = fakeBackend({
    "GET /api/calificaciones/informes-disponibles": [
      primerInforme, { ...primerInforme, idPeriodoAcademico: 5, periodo: "Bimestre 2" },
    ],
    "GET /api/calificaciones/avance": ({ query }) => ({ ...progressReport,
      filas: [{ ...progressReport.filas[0],
        competencia: "Resuelve problemas de cantidad",
        resultado: query.idPeriodoAcademico === "4" ? "B" : "AD",
        observacion: query.idPeriodoAcademico === "4" ? "Practicar fracciones" : "Explica su procedimiento",
      }],
    }),
  });
  render(<ProgressReports />);
  const user = userEvent.setup();
  await screen.findByRole("option", { name: "Bimestre 1 · 2026" });
  expect(screen.queryByLabelText("Estudiante")).toBeNull();
  await user.selectOptions(screen.getByLabelText("Período académico"), "2026-4");
  await user.selectOptions(screen.getByLabelText("Curso"), "3");
  await user.click(screen.getByRole("button", { name: "Ver notas" }));
  await screen.findByRole("cell", { name: "Practicar fracciones" });
  expect(screen.getByRole("cell", { name: "B", exact: true })).toBeTruthy();

  await user.selectOptions(screen.getByLabelText("Período académico"), "2026-5");
  expect(screen.queryByText("Practicar fracciones")).toBeNull();
  await user.selectOptions(screen.getByLabelText("Curso"), "3");
  await user.click(screen.getByRole("button", { name: "Ver notas" }));
  await screen.findByRole("cell", { name: "Explica su procedimiento" });
  expect(screen.getByRole("cell", { name: "AD", exact: true })).toBeTruthy();
  expect(screen.getByRole("rowheader", { name: "Resuelve problemas de cantidad" })).toBeTruthy();
  expect(calls.filter(call => call.path.endsWith("/avance")).map(call => call.query)).toEqual([
    { idAlumno: "8", idAsignacionCurso: "3", idPeriodoAcademico: "4" },
    { idAlumno: "8", idAsignacionCurso: "3", idPeriodoAcademico: "5" },
  ]);
});

test("el apoderado cambia de hijo y solo consulta los cursos publicados del seleccionado", async () => {
  const ana = {idAlumno:8,alumno:"Ana",idAsignacionCurso:3,curso:"Matemática",anio:2026,idPeriodoAcademico:4,periodo:"Bimestre 1"};
  const calls = fakeBackend({
    "GET /api/familia/estudiantes": [{id:8,nombreCompleto:"Ana"},{id:9,nombreCompleto:"Luis"},{id:10,nombreCompleto:"Rosa"}],
    "GET /api/calificaciones/informes-disponibles": [
      ana,
      {...ana,idAsignacionCurso:5,curso:"Ciencia"},
      {...ana,idAlumno:9,alumno:"Luis",idAsignacionCurso:6,curso:"Historia"},
    ],
    "GET /api/calificaciones/avance": {...progressReport, filas:[{...progressReport.filas[0], resultado:"AD", observacion:"Buen avance"}]},
  });
  render(<ProgressReports guardian />);
  const user = userEvent.setup();
  await screen.findByRole("option", { name: "Ana" });
  await user.selectOptions(screen.getByLabelText("Estudiante"), "8");
  await user.selectOptions(screen.getByLabelText("Período académico"), "2026-4");
  expect(screen.getByRole("option", { name: "Ciencia" })).toBeTruthy();
  expect(screen.queryByRole("option", { name: "Historia" })).toBeNull();
  await user.selectOptions(screen.getByLabelText("Curso"), "3");
  await user.click(screen.getByRole("button", { name: "Ver notas" }));
  await screen.findByText("Buen avance");
  expect(calls.find(c => c.path.endsWith("/avance")).query)
    .toEqual({idAlumno:"8",idAsignacionCurso:"3",idPeriodoAcademico:"4"});

  await user.selectOptions(screen.getByLabelText("Estudiante"), "9");
  expect(screen.queryByText("Buen avance")).toBeNull();
  await user.selectOptions(screen.getByLabelText("Período académico"), "2026-4");
  expect(screen.getByRole("option", { name: "Historia" })).toBeTruthy();
  expect(screen.queryByRole("option", { name: "Matemática" })).toBeNull();

  await user.selectOptions(screen.getByLabelText("Estudiante"), "10");
  expect(screen.getByText(/Todavía no hay libretas publicadas/)).toBeTruthy();
});


test("genera y revisa un borrador antes de publicar", async () => {
  const calls = fakeBackend({
    "GET /api/evaluaciones/periodos-informe": [{ id: 4, nombre: "Bimestre 1" }],
    "GET /api/calificaciones/informes/estado": { alumnos: [{idAlumno:8, alumno:"Ana", borrador:true, publicada:true}], publicable:false, impedimento:staleDraftImpediment, desactualizada:true },
    "POST /api/calificaciones/informes/generar": { informesProcesados: 1, informes: [{idAlumno:8,alumno:"Ana",idAsignacionCurso:3,curso:"Matematica",anio:2026,idPeriodoAcademico:4,periodo:"Bimestre 1"}] },
    "GET /api/calificaciones/informes/borrador": {...progressReport, periodo:"Bimestre 1 | Version 2 | BORRADOR",filas:[{competencia:"Cantidad",evaluacion:"Practica",fecha:"01/03/2026",resultado:"Sin registrar",ponderacion:"30 %",observacion:"Sin observaciones"}]},
  });
  render(<PublishPeriod canWrite assignments={[{id:3,curso:"Matematica",seccion:"A",anioAcademico:2026}]} />);
  const user = userEvent.setup();
  await user.selectOptions(screen.getByLabelText("Curso y sección"), "3");
  await screen.findByRole("option", {name:"Bimestre 1"});
  await user.selectOptions(screen.getByLabelText("Período académico"), "4");
  await user.click(screen.getByRole("button", {name:"Generar borradores"}));
  await screen.findByText(/Se generaron 1 borradores/);
  await user.click(await screen.findByRole("button", {name:"Revisar borrador de Ana"}));
  expect(screen.getByText(blockedDraft)).toBeTruthy();
  expect(screen.queryByText(staleDraftImpediment)).toBeNull();
  expect(screen.queryByText(staleWarning)).toBeNull();
  expect(screen.queryByText(pendingDraft)).toBeNull();
  expect(screen.queryByText(updatedReports)).toBeNull();
  expect(screen.queryByText(staleReports)).toBeNull();
  expect(screen.getByRole("button", {name:"Ver libreta de Ana"})).toBeTruthy();
  expect(screen.queryByRole("button", {name:"Publicar libretas"})).toBeNull();
  await screen.findByText("Sin registrar");
  expect(screen.getByText("Versión 2")).toBeTruthy();
  expect(calls.filter(c => c.method === "POST")).toHaveLength(1);
  expect(calls.find(c => c.path.endsWith("borrador")).query).toEqual({idAlumno:"8",idAsignacionCurso:"3",idPeriodoAcademico:"4"});
});

test("sin permiso de escritura no ofrece generar ni publicar", () => {
  render(<PublishPeriod canWrite={false} assignments={[]} />);
  expect(screen.queryByRole("button", {name:"Generar borradores"})).toBeNull();
  expect(screen.queryByRole("button", {name:"Publicar libretas"})).toBeNull();
});

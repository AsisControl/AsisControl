import { test, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PartialProgress } from "../partial-progress";
import { ProgressReports, PublishPeriod } from "../progress-reports";
import { fakeBackend, reply } from "../../../../tests/support/backend.js";
import { progressReport } from "./report-fixture";

const updatedReports = "Todas las libretas del período están publicadas y actualizadas.";
const staleReports = "Todos los estudiantes tienen una libreta publicada, pero existen cambios pendientes de actualizar.";
const staleWarning = "Las evaluaciones o notas cambiaron. Genera un nuevo borrador para actualizar la libreta.";
const pendingDraft = "Los borradores están listos para publicar.";
const blockedDraft = "Hay borradores pendientes. Revísalos antes de publicar.";
const staleDraftImpediment = "Las evaluaciones o notas cambiaron. Vuelve a generar y revisar los borradores antes de publicar.";

test("El apoderado solo consulta el progreso publicado", async () => {
  const calls = fakeBackend({
    "GET /api/calificaciones/informes-disponibles": [],
    "GET /api/familia/estudiantes": [{ id: 8, nombreCompleto: "Ana" }],
  });
  render(<ProgressReports guardian />);
  await userEvent.setup().selectOptions(await screen.findByLabelText("Estudiante"), "8");
  expect(await screen.findByText("Todavía no hay libretas publicadas para consultar.")).toBeTruthy();
  expect(screen.queryByText("Tareas del estudiante")).toBeNull();
  expect(calls.some(call => call.path === "/api/tareas/familia")).toBe(false);
});

test("selecciona actividades publicadas y consulta un informe parcial para el estudiante", async () => {
  let consultas = 0;
  const calls = fakeBackend({
    "GET /api/evaluaciones/periodos-informe": [{ id: 4, nombre: "Bimestre 1" }],
    "GET /api/calificaciones/informes/estado": { alumnos: [{ idAlumno: 8, alumno: "Ana", borrador: false, publicada: false }],
      publicable: false, impedimento: null },
    "GET /api/calificaciones/informes/parcial/evaluaciones": [
      { id: 18, nombre: "Examen de números", tipo: "EXAMEN", fecha: "2026-06-01", competencias: [{ id: 7, nombre: "Argumenta" }, { id: 8, nombre: "Modela" }] },
      { id: 19, nombre: "Proyecto de medidas", tipo: "PROYECTO", fecha: "2026-06-03", competencias: [{ id: 7, nombre: "Argumenta" }] },
    ],
    "GET /api/calificaciones/informes/parcial": () => ++consultas === 1
      ? reply(503, { message: "Vuelve a consultar el informe." })
      : { ...progressReport, consolidado: [{ idCompetencia: 7, nombreCompetencia: "Argumenta", nivelConsolidado: null,
        estadoAvance: "NO_EVALUADA", componentesFaltantes: [] }] },
    "GET /api/calificaciones/informes/parcial/pdf": reply(200, "%PDF-1.7", { "Content-Type": "application/pdf",
      "Content-Disposition": 'attachment; filename="Informe_parcial_Ana_Ramos_Matematica_Bimestre_1.pdf"' }),
  });
  render(<PublishPeriod canWrite assignments={[{ id: 3, curso: "Matemática", seccion: "A", anioAcademico: 2026 }]} />);
  const user = userEvent.setup();
  await user.selectOptions(screen.getByLabelText("Curso y sección"), "3");
  await screen.findByRole("option", { name: "Bimestre 1" });
  await user.selectOptions(screen.getByLabelText("Período académico"), "4");
  await user.selectOptions(screen.getByLabelText("Tipo de informe"), "partial");
  await user.selectOptions(await screen.findByLabelText("Estudiante"), "8");
  await user.click(screen.getByRole("button", { name: "Todas las competencias" }));
  await user.click(screen.getByRole("checkbox", { name: "Modela" }));
  const preview = screen.getByRole("button", { name: "Consultar informe parcial" });
  expect(preview.disabled).toBe(true);
  await user.click(screen.getByRole("button", { name: "Seleccionar evaluaciones" }));
  await user.click(screen.getByRole("button", { name: "Seleccionar todas" }));
  expect(screen.getByRole("button", { name: "Todas las evaluaciones" })).toBeTruthy();
  await user.type(screen.getByLabelText("Buscar evaluaciones"), "Proyecto");
  expect(screen.queryByRole("checkbox", { name: /Examen de números/ })).toBeNull();
  await user.click(screen.getByRole("checkbox", { name: /Proyecto de medidas/ }));
  expect(screen.getByRole("button", { name: "1 evaluación seleccionada" })).toBeTruthy();
  await user.click(preview);
  await screen.findByText("Vuelve a consultar el informe.");
  await user.click(preview);
  expect(await screen.findByText("Informe parcial de progreso")).toBeTruthy();
  expect(screen.queryByText("Aún no evaluada")).toBeNull();
  expect(calls.find(call => call.path.endsWith("/parcial")).query.idEvaluacion).toBe("18");
  URL.createObjectURL = vi.fn(() => "blob:parcial");
  URL.revokeObjectURL = vi.fn();
  const link = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  await user.click(screen.getByRole("button", { name: "Descargar PDF parcial" }));
  await waitFor(() => expect(link).toHaveBeenCalledOnce());
  expect(link.mock.instances[0].download).toBe("Informe_parcial_Ana_Ramos_Matematica_Bimestre_1.pdf");
  expect(calls.find(call => call.path.endsWith("/parcial/pdf")).query).toMatchObject({ idEvaluacion: "18", idCompetencia: "7" });
  expect(calls.find(call => call.path.endsWith("/parcial")).query.idCompetencia).toBe("7");
  expect(calls.every(call => call.method === "GET")).toBe(true);
});

test.each([
  [[], "Publica una evaluación desde Mis evaluaciones", null],
  [reply(503, { message: "No se pudieron consultar las evaluaciones." }), "No se pudieron consultar las evaluaciones.", "Publica una evaluación desde Mis evaluaciones"],
])("distingue la lista parcial vacía de un error de carga (%j)", async (payload, expected, absent) => {
  fakeBackend({ "GET /api/calificaciones/informes/parcial/evaluaciones": payload });
  render(<PartialProgress assignment={3} period={4} students={[]} />);
  await screen.findByText(new RegExp(expected));
  if (absent) expect(screen.queryByText(new RegExp(absent))).toBeNull();
  expect(screen.queryByRole("button", { name: "Consultar informe parcial" })).toBeNull();
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
    "GET /api/calificaciones/avance": {...progressReport,alumno:"Ana",curso:"Matematica",filas:[{idEvaluacion:10,competencia:"Cantidad",evaluacion:"Practica",fecha:"01/03/2026",resultado:"A",ponderacion:"30 %",observacion:"Buen avance"},{idEvaluacion:10,competencia:"Forma",evaluacion:"Practica",fecha:"01/03/2026",resultado:"Sin registrar",ponderacion:"30 %",observacion:"Sin observaciones"}]},
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
  expect(screen.getByText("—")).toBeTruthy();
  const full = screen.getByRole("button", { name: "Descargar libreta completa" });
  const partial = screen.getByRole("button", { name: "Descargar evaluaciones seleccionadas" });
  expect(full.parentElement.contains(partial)).toBe(true);
  expect(partial.compareDocumentPosition(screen.getByRole("article")) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  await user.click(full);
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
  expect(screen.getByRole("heading", { name: "Resuelve problemas de cantidad" })).toBeTruthy();
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


test.each([
  staleDraftImpediment,
  "Faltan resultados para los siguientes componentes: Cantidad (Proyectos)",
])("mantiene visible el bloqueo al revisar el borrador: %s", async (impedimento) => {
  const calls = fakeBackend({
    "GET /api/evaluaciones/periodos-informe": [{ id: 4, nombre: "Bimestre 1" }],
    "GET /api/calificaciones/informes/estado": { alumnos: [{idAlumno:8, alumno:"Ana", borrador:true, publicada:true}], publicable:false, impedimento, desactualizada:true },
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
  expect(screen.getByText("Publicación pendiente")).toBeTruthy();
  expect(screen.getByText(impedimento)).toBeTruthy();
  expect(screen.queryByText(staleWarning)).toBeNull();
  expect(screen.queryByText(pendingDraft)).toBeNull();
  expect(screen.queryByText(updatedReports)).toBeNull();
  expect(screen.queryByText(staleReports)).toBeNull();
  expect(screen.getByRole("button", {name:"Ver libreta de Ana"})).toBeTruthy();
  expect(screen.getByRole("button", {name:"Publicar libretas"}).disabled).toBe(true);
  await screen.findByText("—");
  expect(screen.getByText("Versión 2")).toBeTruthy();
  expect(calls.filter(c => c.method === "POST")).toHaveLength(1);
  expect(calls.find(c => c.path.endsWith("borrador")).query).toEqual({idAlumno:"8",idAsignacionCurso:"3",idPeriodoAcademico:"4"});
});

test("sin permiso de escritura no ofrece generar ni publicar", () => {
  render(<PublishPeriod canWrite={false} assignments={[]} />);
  expect(screen.queryByRole("button", {name:"Generar borradores"})).toBeNull();
  expect(screen.queryByRole("button", {name:"Publicar libretas"})).toBeNull();
});

// cobertura: libreta inexistente, errores de consulta y descarga, y estados del apoderado
const disponible = [{ idAlumno: 8, alumno: "Ana", idAsignacionCurso: 3, curso: "Matematica", anio: 2026, idPeriodoAcademico: 4, periodo: "Bimestre 1" }];

async function elegirCurso() {
  await screen.findByRole("option", { name: /Bimestre 1/ });
  const user = userEvent.setup();
  await user.selectOptions(screen.getByLabelText("Período académico"), "2026-4");
  await user.selectOptions(screen.getByLabelText("Curso"), "3");
  return user;
}

test("avisa cuando no hay libreta publicada y cuando falla la descarga del PDF", async () => {
  fakeBackend({
    "GET /api/calificaciones/informes-disponibles": disponible,
    "GET /api/calificaciones/avance": reply(404, { code: "INFORME_NO_ENCONTRADO", message: "x" }),
    "GET /api/calificaciones/reporte-avance": reply(500, { message: "Servidor ocupado" }),
  });
  render(<ProgressReports />);
  const user = await elegirCurso();
  await user.click(screen.getByText("Ver notas"));
  expect(await screen.findByText("No hay una libreta publicada para este período y curso.")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: /Descargar/ }));
  expect(await screen.findByText(/No se pudo descargar el PDF/)).toBeTruthy();
});

test("informa si falla la consulta de la libreta o la carga inicial", async () => {
  fakeBackend({
    "GET /api/calificaciones/informes-disponibles": disponible,
    "GET /api/calificaciones/avance": reply(500, { message: "Libreta no disponible" }),
  });
  const { unmount } = render(<ProgressReports />);
  const user = await elegirCurso();
  await user.click(screen.getByText("Ver notas"));
  expect(await screen.findByText("Libreta no disponible")).toBeTruthy();
  unmount();
  fakeBackend({ "GET /api/calificaciones/informes-disponibles": reply(500, { message: "Sin conexión" }) });
  render(<ProgressReports />);
  expect(await screen.findByText("Sin conexión")).toBeTruthy();
});

test("el apoderado sin estudiantes asociados lo ve indicado", async () => {
  fakeBackend({
    "GET /api/calificaciones/informes-disponibles": [],
    "GET /api/familia/estudiantes": [],
  });
  render(<ProgressReports guardian />);
  expect(await screen.findByText("No tienes estudiantes asociados.")).toBeTruthy();
});

test("la publicación informa si falla el estado de las libretas y permite reintentar", async () => {
  let fallar = true;
  fakeBackend({
    "GET /api/evaluaciones/periodos-informe": [{ id: 4, nombre: "Bimestre 1" }],
    "GET /api/calificaciones/informes/estado": () => (fallar ? reply(500, { message: "Estado no disponible" })
      : { alumnos: [], publicable: false, impedimento: null }),
  });
  render(<PublishPeriod canWrite assignments={[{ id: 3, curso: "Matemática", seccion: "A", anioAcademico: 2026 }]} />);
  const user = userEvent.setup();
  await user.selectOptions(screen.getByLabelText("Curso y sección"), "3");
  await screen.findByRole("option", { name: "Bimestre 1" });
  await user.selectOptions(screen.getByLabelText("Período académico"), "4");
  expect(await screen.findByText(/No se pudo consultar el estado de las libretas. Estado no disponible/)).toBeTruthy();
  fallar = false;
  await user.click(screen.getByRole("button", { name: "Reintentar consulta" }));
  expect(await screen.findByText("Todavía no hay libretas para este curso y período. Genera los borradores para empezar.")).toBeTruthy();
});

test("la publicación informa si fallan los períodos", async () => {
  fakeBackend({ "GET /api/evaluaciones/periodos-informe": reply(500, { message: "Períodos no disponibles" }) });
  render(<PublishPeriod canWrite assignments={[{ id: 3, curso: "Matemática", seccion: "A", anioAcademico: 2026 }]} />);
  await userEvent.setup().selectOptions(screen.getByLabelText("Curso y sección"), "3");
  expect(await screen.findByText("Períodos no disponibles")).toBeTruthy();
});

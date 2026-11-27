import { beforeEach, expect, test, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { TaskDeliveries } from "../task-deliveries";
import { DeliveryGradeDialog } from "../delivery-grade-dialog";
import { Tasks } from "@/modules/tasks";
import { ModuleOutlet } from "@/core/navigation/module-outlet";
import { SessionUserProvider } from "@/core/layout/session-user";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));
vi.mock("next/link", () => import("../../../../tests/support/next-link.jsx"));

const task = (overrides = {}) => ({ id: 1, asignacionCursoId: 42, nombre: "Fracciones", descripcion: "Resolver ejercicios", fechaPublicacion: "2026-10-01T12:00:00", fechaLimite: "2026-10-10T18:00:00", puntajeMaximo: 20, maxArchivosEntrega: 2, estado: "PUBLICADA", archivos: [], version: 3, ...overrides });
const file = { id: 80, nombreOriginal: "respuesta.pdf", tipoMime: "application/pdf", tamanioBytes: 10, sha256: "hash", cargadoPorPersonaId: 5, fechaSubida: "2026-10-11T12:00:00Z" };
const delivery = (overrides = {}) => ({ id: 10, tareaId: 1, alumnoId: 8, fechaHoraEntrega: "2026-10-11T12:30:45", archivos: [file], nota: null, comentario: "Mi respuesta", estado: "ENTREGADO_TARDE", version: 4, ...overrides });
const student = (id = 8) => ({ id, codigoAlumno: `AL-${id}`, persona: { nombreCompleto: id === 8 ? "Lucía Pérez" : "Luis Ramos" } });
function backend(routes = {}) {
  return fakeBackend({
    "GET /api/tareas/1": task(),
    "GET /api/tareas/1/entregas": page([delivery()]),
    "GET /api/entregas/10": delivery(),
    "GET /api/alumnos/:id": ({ path }) => student(Number(path.split("/").at(-1))),
    "POST /api/entregas/10/calificar": ({ body }) => delivery({ ...body, estado: "CALIFICADO", version: 5 }),
    ...routes,
  });
}
beforeEach(() => {
  signIn({ role: "DOCENTE", permissions: ["TAREAS_LEER", "TAREAS_ESCRIBIR"] });
  setUrl("/tareas/1/entregas");
});

test("califica sin entrega con decimales y conserva la selección al buscar", async () => {
  const calls = backend({
    "GET /api/tareas/1": task({ estado: "CERRADA", evaluacionAcademica: { id: 31, titulo: "Fracciones", estado: "BORRADOR" } }),
    "GET /api/tareas/1/calificaciones-sin-entrega": { maximo: 20, competencias: [{ id: 3, nombre: "Resuelve" }],
      alumnos: [{ id: 9, nombre: "Luis Ramos", codigo: "AL-9", competencias: [3] }, { id: 10, nombre: "Ana Ramos", competencias: [3] }] },
    "POST /api/tareas/1/calificaciones-sin-entrega": { estudiantes: 2, calificaciones: 2 },
  });
  const user = userEvent.setup();
  render(<TaskDeliveries taskId={1} canWrite />);
  await user.click(await screen.findByRole("button", { name: "Calificar sin entrega" }));
  const dialog = within(screen.getByRole("dialog", { name: "Calificar sin entrega" }));
  await user.click(dialog.getByRole("button", { name: "Seleccionar todos" }));
  await user.type(dialog.getByRole("searchbox"), "Ana");
  expect(dialog.getByRole("status").textContent).toContain("2 estudiantes");
  await user.type(dialog.getByRole("spinbutton"), "5.5");
  await user.click(dialog.getByRole("button", { name: "Aplicar calificación" }));
  expect(calls.some(call => call.method === "POST")).toBe(false);
  await user.click(screen.getByRole("button", { name: "Confirmar calificación" }));
  await screen.findByText("Se registraron 2 calificaciones.");
  expect(calls.find(call => call.method === "POST").body).toEqual({ alumnos: [9, 10], competencias: [3], nota: "5.5" });
});

test("una práctica cualitativa sin entrega exige seleccionar el nivel y conserva el contrato de lote", async () => {
  const calls = backend({
    "GET /api/tareas/1": task({ estado: "CERRADA", modeloCualitativo: true }),
    "GET /api/tareas/1/calificaciones-sin-entrega": { modeloCualitativo: true, competencias: [],
      alumnos: [{ id: 9, nombre: "Luis Ramos", codigo: "AL-9", competencias: [] }] },
    "POST /api/tareas/1/calificaciones-sin-entrega": { estudiantes: 1, calificaciones: 1 },
  });
  const user = userEvent.setup();
  render(<TaskDeliveries taskId={1} canWrite />);
  await user.click(await screen.findByRole("button", { name: "Calificar sin entrega" }));
  const dialog = within(screen.getByRole("dialog", { name: "Calificar sin entrega" }));
  await user.click(dialog.getByRole("checkbox", { name: /Luis Ramos/ }));
  const apply = dialog.getByRole("button", { name: "Aplicar calificación" });
  expect(apply.disabled).toBe(true);
  expect(dialog.queryByRole("spinbutton")).toBeNull();
  await user.click(dialog.getByRole("button", { name: "AD: Nivel para la selección" }));
  await user.click(apply);
  await user.click(screen.getByRole("button", { name: "Confirmar calificación" }));
  await screen.findByText("Se registraron 1 calificaciones.");
  expect(calls.find(call => call.method === "POST").body).toEqual({ alumnos: [9], competencias: [], nivelLogro: "AD" });
});

test("califica práctica sin competencias, respeta el máximo y reintenta tras un conflicto", async () => {
  let attempts = 0;
  const calls = backend({
    "GET /api/tareas/1": task({ estado: "CERRADA", puntajeMaximo: 25 }),
    "GET /api/tareas/1/calificaciones-sin-entrega": { maximo: 25, competencias: [],
      alumnos: [{ id: 9, nombre: "Luis Ramos", codigo: "AL-9", competencias: [] }] },
    "POST /api/tareas/1/calificaciones-sin-entrega": () => ++attempts === 1
      ? reply(409, { message: "Actualiza la selección." }) : { estudiantes: 1, calificaciones: 1 },
  });
  const user = userEvent.setup();
  render(<TaskDeliveries taskId={1} canWrite />);
  await user.click(await screen.findByRole("button", { name: "Calificar sin entrega" }));
  const dialog = within(screen.getByRole("dialog", { name: "Calificar sin entrega" }));
  expect(dialog.getByText("Nota de práctica · Sin peso")).toBeTruthy();
  const grade = dialog.getByRole("spinbutton");
  const apply = dialog.getByRole("button", { name: "Aplicar calificación" });
  await user.type(dialog.getByRole("searchbox"), "AL-9");
  await user.click(dialog.getByRole("checkbox"));
  await user.type(grade, "25.1");
  expect(apply.disabled).toBe(true);
  await user.clear(grade);
  await user.type(grade, "24.5");
  await user.click(apply);
  await user.click(screen.getByRole("button", { name: "Confirmar calificación" }));
  await dialog.findByText("Actualiza la selección.");
  await user.click(apply);
  await user.click(screen.getByRole("button", { name: "Confirmar calificación" }));
  await screen.findByText("Se registraron 1 calificaciones.");
  expect(calls.filter(call => call.method === "POST").map(call => call.body)).toEqual([
    { alumnos: [9], competencias: [], nota: "24.5" }, { alumnos: [9], competencias: [], nota: "24.5" },
  ]);
});

test("Lista solo entregas recibidas con identidad, fecha, estado, comentario, nota y adjuntos", async () => {
  const calls = backend();
  render(<TaskDeliveries taskId={1} canWrite />);
  expect(screen.getByRole("status").textContent).toBe("Cargando tarea…");
  expect(await screen.findByText("Lucía Pérez")).toBeTruthy();
  expect(screen.getByText("AL-8")).toBeTruthy();
  expect(screen.getByText("11/10/2026 12:30:45").getAttribute("datetime")).toBe("2026-10-11T12:30:45");
  expect(screen.getByText("Entregado tarde")).toBeTruthy();
  expect(screen.getByText("Mi respuesta")).toBeTruthy();
  expect(screen.getByText("Sin calificar")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Descargar respuesta.pdf" })).toBeTruthy();
  expect(screen.getByRole("button", { name: "Calificar Lucía Pérez" })).toBeTruthy();
  expect(screen.getByRole("link", { name: "Volver a tareas" }).getAttribute("href")).toBe("/tareas");
  expect(screen.getByRole("link", { name: "Volver a tareas" }).textContent).toBe("← Volver a tareas");
  expect(calls.find((call) => call.path.endsWith("/entregas")).query).toEqual({ page: "0", size: "20" });
  expect(calls.some((call) => call.path === "/api/alumnos" || call.path === "/api/matriculas")).toBe(false);
});

const roster = (overrides = {}) => ({
  tareaId: 1, totalAlumnos: 3, entregados: 1, calificados: 0, pendientes: 2,
  alumnos: [
    { alumnoId: 8, codigoAlumno: "AL-8", nombreCompleto: "Lucía Pérez", matriculaActiva: true, entregaId: 10, estado: "ENTREGADO_TARDE", fechaHoraEntrega: "2026-10-11T12:30:45", nota: null },
    { alumnoId: 9, codigoAlumno: "AL-9", nombreCompleto: "Luis Ramos", matriculaActiva: true, entregaId: null, estado: "PENDIENTE", fechaHoraEntrega: null, nota: null },
    { alumnoId: 7, codigoAlumno: null, nombreCompleto: "Ana Soto", matriculaActiva: true, entregaId: null, estado: "PENDIENTE", fechaHoraEntrega: null, nota: null },
    { alumnoId: 6, codigoAlumno: "AL-6", nombreCompleto: "Retirado Díaz", matriculaActiva: false, entregaId: null, estado: "PENDIENTE", fechaHoraEntrega: null, nota: null },
  ],
  ...overrides,
});

test("calificación individual sin entrega reutiliza Revisar entrega y guarda nivel y devolución", async () => {
  const path = "/api/tareas/1/calificaciones-sin-entrega/9";
  const calls = backend({
    "GET /api/tareas/1": task({ estado: "CERRADA", modeloCualitativo: true }),
    "GET /api/tareas/1/seguimiento": roster(),
    "GET /api/tareas/1/calificaciones-sin-entrega": { alumnos: [], competencias: [] },
    [`GET ${path}`]: { nota: null, nivelLogro: "B", observacion: null, devolucion: null },
    [`PUT ${path}`]: reply(204),
    [`PUT ${path}/devolucion`]: reply(204),
  });
  const user = userEvent.setup();
  render(<TaskDeliveries taskId={1} canWrite />);
  await user.click(await screen.findByRole("button", { name: "Calificar Luis Ramos" }));
  const modal = within(screen.getByRole("dialog", { name: "Revisar entrega" }));
  expect(await modal.findByText(/El estudiante no presentó una entrega/)).toBeTruthy();
  await user.type(modal.getByLabelText("Devolución docente (opcional)"), "Revisa la guía");
  await user.click(modal.getByRole("button", { name: "Guardar devolución" }));
  await modal.findByText("Devolución guardada.");
  await user.click(modal.getByRole("button", { name: "A: Nivel general" }));
  await user.type(modal.getByLabelText("Observación opcional"), "Trabajo pendiente");
  await user.click(modal.getByRole("button", { name: "Guardar calificación" }));
  await screen.findByText("Calificación guardada.");
  expect(calls.filter(call => call.method === "PUT").map(call => call.body)).toEqual([
    { comentario: "Revisa la guía" }, { nivelLogro: "A", observacion: "Trabajo pendiente" },
  ]);
});

test("Padrón: resume quiénes entregaron y lista a quienes no, y se recarga al calificar", async () => {
  let current = roster();
  const calls = backend({ "GET /api/tareas/1/seguimiento": () => current });
  render(<TaskDeliveries taskId={1} canWrite />);
  const region = await screen.findByRole("region", { name: "Padrón de la sección" });
  expect(await within(region).findByText("1 de 3 estudiantes entregaron")).toBeTruthy();
  expect(within(region).getByText(/0 calificados/)).toBeTruthy();
  expect(within(region).getByText("Sin entrega (2)")).toBeTruthy();
  expect(within(region).getByText("Luis Ramos")).toBeTruthy();
  expect(within(region).getByText("Ana Soto")).toBeTruthy();
  expect(within(region).queryByText(/Retirado Díaz/)).toBeNull();
  expect(within(region).getByText("Lucía Pérez")).toBeTruthy();

  current = roster({ calificados: 1 });
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "Calificar Lucía Pérez" }));
  const dialog = await screen.findByRole("dialog");
  await user.type(within(dialog).getByLabelText(/^Nota/), "15");
  await user.click(within(dialog).getByRole("button", { name: /Guardar/ }));
  expect(await within(region).findByText(/1 calificado$/)).toBeTruthy();
  expect(calls.filter((call) => call.path === "/api/tareas/1/seguimiento")).toHaveLength(2);
});

test("Padrón: si no se puede cargar, las entregas se siguen mostrando", async () => {
  backend({ "GET /api/tareas/1/seguimiento": reply(403, { message: "Acceso denegado" }) });
  render(<TaskDeliveries taskId={1} canWrite />);
  expect(await screen.findByText("Lucía Pérez")).toBeTruthy();
  expect(await screen.findByText(/No se pudo cargar el padrón de la sección/)).toBeTruthy();
});

test("filtra estados independientes y califica una sola entrega con la misma planilla", async () => {
  const calls = backend({
    "GET /api/tareas/1": task({ evaluacionAcademica: { id: 31, titulo: "Fracciones", estado: "BORRADOR" } }),
    "GET /api/tareas/1/seguimiento": roster({ alumnos: [
      { ...roster().alumnos[0], tieneRespuesta: true, cantidadArchivos: 1, competenciasCalificadas: 1, totalCompetencias: 2 },
      { ...roster().alumnos[1], nota: 7 },
    ] }),
    "GET /api/entregas/10": delivery({ respuesta: "Esta respuesta solo aparece al revisar la entrega." }),
    "GET /api/evaluaciones/31/planilla": { evaluacion: { estado: "BORRADOR", competencias: [
      { id: 3, nombre: "Resuelve" }, { id: 4, nombre: "Comunica" },
    ] }, alumnos: [{ idAlumno: 8, notas: [{ idCompetencia: 3, notaNumerica: 12, version: 2 }] }] },
    "PUT /api/tareas/1/calificaciones": [],
  });
  const user = userEvent.setup();
  render(<TaskDeliveries taskId={1} canWrite />);
  await screen.findByText("1 de 2 competencias calificadas");
  expect(screen.queryByText(/solo aparece al revisar/)).toBeNull();
  await user.selectOptions(screen.getByLabelText("Estado de entrega"), "missing");
  await user.selectOptions(screen.getByLabelText("Estado de calificación"), "graded");
  expect(screen.getByText("7 / 20")).toBeTruthy();
  expect(screen.queryByText("Lucía Pérez")).toBeNull();
  await user.selectOptions(screen.getByLabelText("Estado de entrega"), "all");
  await user.selectOptions(screen.getByLabelText("Estado de calificación"), "partial");
  await user.click(screen.getByRole("button", { name: "Ver entrega" }));
  const modal = within(await screen.findByRole("dialog", { name: "Revisar entrega" }));
  expect(await modal.findByText(/solo aparece al revisar/)).toBeTruthy();
  expect(modal.queryByText(/Luis Ramos/)).toBeNull();
  const grade = within(modal.getByRole("group", { name: "Resuelve" }));
  await user.clear(grade.getByRole("spinbutton"));
  await user.type(grade.getByRole("spinbutton"), "15.5");
  await user.click(modal.getByRole("button", { name: "Guardar calificación" }));
  await screen.findByText("Calificación guardada.");
  expect(calls.find(call => call.path.endsWith("/planilla")).query).toEqual({ idAlumno: "8" });
  expect(calls.find(call => call.path === "/api/tareas/1/calificaciones").body.calificaciones).toEqual([
    { idEvaluacion: 31, idAlumno: 8, idCompetencia: 3, notaNumerica: "15.5", observacion: null, versionEsperada: 2 },
  ]);
});

test("la devolución preserva la respuesta y las notas publicadas son de consulta", async () => {
  const calls = backend({
    "GET /api/tareas/1": task({ estado: "CERRADA", evaluacionAcademica: { id: 31, titulo: "Fracciones", estado: "PUBLICADA" } }),
    "GET /api/tareas/1/seguimiento": roster({ alumnos: [
      { ...roster().alumnos[0], tieneRespuesta: true, cantidadArchivos: 2, competenciasCalificadas: 1, totalCompetencias: 1 },
    ] }),
    "GET /api/entregas/10": delivery({ respuesta: "Texto original", comentario: null,
      archivos: [file, { ...file, id: 81, nombreOriginal: "anexo con nombre largo.docx" }] }),
    "GET /api/evaluaciones/31/planilla": { evaluacion: { estado: "PUBLICADA", competencias: [{ id: 3, nombre: "Resuelve" }] },
      alumnos: [{ idAlumno: 8, notas: [{ idCompetencia: 3, notaNumerica: 17, observacion: "Buen avance", version: 2 }] }] },
    "PUT /api/entregas/10/devolucion": ({ body }) => delivery({ respuesta: "Texto original", comentario: body.comentario, version: 5 }),
  });
  const user = userEvent.setup();
  render(<TaskDeliveries taskId={1} canWrite />);
  await screen.findByText("Respuesta escrita · 2 archivos");
  expect(screen.queryByText("Texto original")).toBeNull();
  await user.click(screen.getByRole("button", { name: "Ver entrega" }));
  const modal = within(await screen.findByRole("dialog", { name: "Revisar entrega" }));
  expect(await modal.findByText("Texto original")).toBeTruthy();
  expect(modal.getByRole("button", { name: "Descargar anexo con nombre largo.docx" })).toBeTruthy();
  expect(modal.getByText("17 · Buen avance")).toBeTruthy();
  expect(modal.queryByRole("spinbutton")).toBeNull();
  await user.type(modal.getByLabelText("Devolución docente (opcional)"), "Revisa el segundo paso");
  await user.click(modal.getByRole("button", { name: "Guardar devolución" }));
  await modal.findByText("Devolución guardada.");
  expect(modal.getByText("Texto original")).toBeTruthy();
  expect(calls.find(call => call.path.endsWith("/devolucion")).body).toEqual({ comentario: "Revisa el segundo paso", version: 4 });
  expect(calls.some(call => call.path === "/api/tareas/1/calificaciones")).toBe(false);
});

test("corrige un nivel publicado desde Tareas con motivo, versión y confirmación", async () => {
  const calls = backend({
    "GET /api/tareas/1": task({ estado: "CERRADA", evaluacionAcademica: { id: 31, titulo: "Fracciones", estado: "PUBLICADA" } }),
    "GET /api/tareas/1/seguimiento": roster({ alumnos: [roster().alumnos[0]] }),
    "GET /api/evaluaciones/31/planilla": { evaluacion: { estado: "PUBLICADA", modeloCualitativo: true,
      competencias: [{ id: 3, nombre: "Resuelve" }] },
      alumnos: [{ idAlumno: 8, notas: [{ idCalificacion: 112, idCompetencia: 3, nivelLogro: "B", version: 2 }] }] },
    "POST /api/tareas/1/correcciones": [],
  });
  const user = userEvent.setup();
  render(<TaskDeliveries taskId={1} canWrite />);
  await user.click(await screen.findByRole("button", { name: "Ver entrega" }));
  const modal = within(await screen.findByRole("dialog", { name: "Revisar entrega" }));
  await user.click(await modal.findByRole("button", { name: "Corregir calificación" }));
  await user.click(modal.getByRole("button", { name: "A: Resuelve" }));
  expect(modal.getByRole("button", { name: "Revisar corrección" }).disabled).toBe(true);
  await user.type(modal.getByLabelText("Motivo de la corrección"), "Revisión de evidencia");
  await user.click(modal.getByRole("button", { name: "Revisar corrección" }));
  expect(calls.some(call => call.path.endsWith("/correcciones"))).toBe(false);
  await user.click(screen.getByRole("button", { name: "Confirmar corrección" }));
  await waitFor(() => expect(calls.find(call => call.path.endsWith("/correcciones"))?.body)
    .toEqual({ motivo: "Revisión de evidencia", correcciones: [{ idCalificacion: 112, nivelLogro: "A", version: 2 }] }));
});

test("permite quitar un nivel en borrador y publicar las calificaciones con confirmación", async () => {
  const calls = backend({
    "GET /api/tareas/1": task({ estado: "CERRADA", evaluacionAcademica: {
      id: 31, titulo: "Fracciones", estado: "BORRADOR", cuentaParaResultadoAcademico: true,
    } }),
    "GET /api/tareas/1/seguimiento": roster({ alumnos: [roster().alumnos[0]] }),
    "GET /api/evaluaciones/31/planilla": { evaluacion: { estado: "BORRADOR", modeloCualitativo: true,
      competencias: [{ id: 3, nombre: "Resuelve" }] },
      alumnos: [{ idAlumno: 8, notas: [{ idCalificacion: 112, idCompetencia: 3, nivelLogro: "B", version: 2 }] }] },
    "DELETE /api/tareas/1/calificaciones/112": reply(204),
    "POST /api/tareas/1/publicar-calificaciones": { estado: "PUBLICADA" },
  });
  const user = userEvent.setup();
  render(<TaskDeliveries taskId={1} canWrite />);
  await user.click(await screen.findByRole("button", { name: "Ver entrega" }));
  const review = within(await screen.findByRole("dialog", { name: "Revisar entrega" }));
  await user.click(await review.findByRole("button", { name: "Quitar nivel de Resuelve" }));
  expect(calls.some(call => call.method === "DELETE")).toBe(false);
  await user.click(screen.getByRole("button", { name: "Quitar nivel", exact: true }));
  await waitFor(() => expect(calls.find(call => call.method === "DELETE")?.query).toEqual({ version: "2" }));
  await user.click(await screen.findByRole("button", { name: "Publicar calificaciones" }));
  const publication = within(screen.getByRole("dialog", { name: "Publicar calificaciones" }));
  expect(calls.some(call => call.path.endsWith("/publicar-calificaciones"))).toBe(false);
  await user.click(publication.getByRole("button", { name: "Publicar", exact: true }));
  await screen.findByText("Calificaciones publicadas.");
});

test("la publicación incompleta muestra el conteo y orienta al docente dentro del modal", async () => {
  backend({
    "GET /api/tareas/1": task({ estado: "CERRADA", evaluacionAcademica: {
      id: 31, estado: "BORRADOR", cuentaParaResultadoAcademico: true,
    } }),
    "POST /api/tareas/1/publicar-calificaciones": reply(422, {
      code: "EVALUACION_INCOMPLETA", message: "Faltan 3 niveles por registrar antes de publicar la evaluación",
    }),
  });
  const user = userEvent.setup();
  render(<TaskDeliveries taskId={1} canWrite />);
  await user.click(await screen.findByRole("button", { name: "Publicar calificaciones" }));
  const dialog = within(screen.getByRole("dialog", { name: "Publicar calificaciones" }));
  await user.click(dialog.getByRole("button", { name: "Publicar", exact: true }));
  const alert = await dialog.findByRole("alert");
  expect(alert.textContent).toMatch(/Faltan 3 niveles.*Revisa las entregas.*tras cerrar la recepción/);
  expect(screen.queryByText("Calificaciones publicadas.")).toBeNull();
});

test("los contadores de seguimiento usan el padrón completo y el buscador reinicia la página", async () => {
  const alumnos = Array.from({ length: 25 }, (_, index) => ({
    ...roster().alumnos[1], alumnoId: index + 1, nombreCompleto: `Estudiante ${index + 1}`, codigoAlumno: `AL-${index + 1}`,
  }));
  backend({ "GET /api/tareas/1/seguimiento": roster({ totalAlumnos: 25, entregados: 0, pendientes: 25, alumnos }) });
  const user = userEvent.setup();
  render(<TaskDeliveries taskId={1} canWrite={false} />);
  await screen.findByText("Estudiante 20");
  expect(screen.queryByText("Estudiante 21")).toBeNull();
  await user.click(screen.getByRole("button", { name: /Siguiente/ }));
  expect(screen.getByText("Estudiante 25")).toBeTruthy();
  expect(screen.getByText("Sin entrega (25)")).toBeTruthy();
  await user.type(screen.getByRole("searchbox"), "AL-7");
  expect(screen.getByText("Estudiante 7")).toBeTruthy();
  expect(screen.queryByText("Estudiante 25")).toBeNull();
  await user.selectOptions(screen.getByLabelText("Estado de calificación"), "graded");
  expect(screen.getByText("No hay estudiantes para estos filtros.")).toBeTruthy();
});

test("Seguimiento combina pendientes, entregados a tiempo, tarde y calificados con el total del padrón", async () => {
  backend({
    "GET /api/tareas/1/seguimiento": roster({
      totalAlumnos: 4, entregados: 3, calificados: 1, pendientes: 1,
      alumnos: [
        { ...roster().alumnos[0], estado: "ENTREGADO" },
        { ...roster().alumnos[1], entregaId: 11, estado: "ENTREGADO_TARDE" },
        { ...roster().alumnos[2], entregaId: 12, estado: "CALIFICADO", nota: 18 },
        { ...roster().alumnos[1], alumnoId: 5, nombreCompleto: "Sin entregar" },
      ],
    }),
    "GET /api/tareas/1/entregas": page([
      delivery({ estado: "ENTREGADO" }),
      delivery({ id: 11, alumnoId: 9 }),
      delivery({ id: 12, alumnoId: 7, estado: "CALIFICADO", nota: 18 }),
    ]),
  });
  render(<TaskDeliveries taskId={1} canWrite />);
  expect(await screen.findByText("3 de 4 estudiantes entregaron")).toBeTruthy();
  const table = await screen.findByRole("table");
  expect(within(table).getAllByText("Entregado")).toHaveLength(2);
  expect(within(table).getByText("Entregado tarde")).toBeTruthy();
  expect(within(table).getByText("18 / 20")).toBeTruthy();
  expect(screen.getByText("Sin entrega (1)")).toBeTruthy();
  expect(screen.getByText("Sin entregar")).toBeTruthy();
});

test("Paginación y actualización conservan caché de alumnos ya resueltos", async () => {
  const calls = backend({ "GET /api/tareas/1/entregas": ({ query }) => ({
    content: [delivery(query.page === "1" ? { id: 11, alumnoId: 9 } : {})],
    number: Number(query.page), totalPages: 2, totalElements: 21,
  }) });
  const user = userEvent.setup();
  render(<TaskDeliveries taskId={1} canWrite={false} />);
  await screen.findByText("Lucía Pérez");
  await user.click(screen.getByText("Siguiente"));
  await screen.findByText("Luis Ramos");
  expect(screen.getByText("21 registros · Página 2 de 2")).toBeTruthy();
  await user.click(screen.getByText("Anterior"));
  await screen.findByText("Lucía Pérez");
  await user.click(screen.getByText("Actualizar"));
  await waitFor(() => expect(calls.filter((call) => call.path.endsWith("/entregas"))).toHaveLength(4));
  await screen.findByText("Lucía Pérez");
  expect(calls.filter((call) => call.path === "/api/alumnos/8")).toHaveLength(1);
  expect(calls.filter((call) => call.path === "/api/alumnos/9")).toHaveLength(1);
});

test("El fallo de identidad muestra fallback y se reintenta al actualizar", async () => {
  let fail = true;
  backend({ "GET /api/alumnos/:id": () => fail ? reply(403) : student() });
  render(<TaskDeliveries taskId={1} canWrite />);
  await screen.findByText("Estudiante no disponible");
  fail = false;
  await userEvent.setup().click(screen.getByText("Actualizar"));
  expect(await screen.findByText("Lucía Pérez")).toBeTruthy();
});

test.each(["PUBLICADA", "CERRADA"])("Califica y recalifica en %s con nota cero, comentario y versión exacta", async (estado) => {
  let current = delivery();
  const calls = backend({
    "GET /api/tareas/1": task({ estado }),
    "GET /api/tareas/1/entregas": () => page([current]),
    "GET /api/entregas/10": () => current,
    "POST /api/entregas/10/calificar": ({ body }) => { current = delivery({ ...body, estado: "CALIFICADO", version: current.version + 1 }); return current; },
  });
  render(<TaskDeliveries taskId={1} canWrite />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "Calificar Lucía Pérez" }));
  const nota = await screen.findByLabelText(/^Nota/);
  await user.type(nota, "0");
  const comment = screen.getByLabelText("Comentario");
  expect(comment.value).toBe("Mi respuesta");
  await user.clear(comment);
  await user.type(comment, " Revisado ");
  await user.click(screen.getByText("Guardar calificación"));
  await screen.findByText("Calificación guardada.");
  expect(calls.find((call) => call.method === "POST").body).toEqual({ nota: 0, comentario: "Revisado", version: 4 });
  await screen.findByText("0 / 20");
  await user.click(await screen.findByRole("button", { name: "Modificar calificación de Lucía Pérez" }));
  const newNota = await screen.findByLabelText(/^Nota/);
  expect(newNota.value).toBe("0");
  await user.clear(newNota);
  await user.type(newNota, "20");
  await user.click(screen.getByText("Guardar calificación"));
  await screen.findByText("20 / 20");
  expect(calls.filter((call) => call.method === "POST").at(-1).body.version).toBe(5);
});

test("Archivar, falta de escritura y entrega anulada impiden calificar", async () => {
  backend({ "GET /api/tareas/1": task({ estado: "ARCHIVADA" }), "GET /api/tareas/1/entregas": page([delivery({ nota: 18, estado: "CALIFICADO" })]) });
  const view = render(<TaskDeliveries taskId={1} canWrite />);
  await screen.findByText("18 / 20");
  expect(screen.getByText("Tarea archivada: consulta de solo lectura.")).toBeTruthy();
  expect(screen.queryByRole("button", { name: /Calificar|Modificar calificación/ })).toBeNull();
  view.unmount();
  backend({ "GET /api/tareas/1/entregas": page([delivery({ estado: "ANULADO" })]) });
  const second = render(<TaskDeliveries taskId={1} canWrite />);
  await screen.findByText("Anulado");
  expect(screen.queryByRole("button", { name: /Calificar/ })).toBeNull();
  second.unmount();
  backend();
  render(<TaskDeliveries taskId={1} canWrite={false} />);
  await screen.findByText("Lucía Pérez");
  expect(screen.queryByRole("button", { name: /Calificar/ })).toBeNull();
});

test("Rechaza notas fuera del rango y permite una nota decimal", async () => {
  const calls = backend();
  render(<DeliveryGradeDialog taskId={1} deliveryId={10} student="Lucía" onClose={vi.fn()} onRefresh={vi.fn()} onSaved={vi.fn()} />);
  const input = await screen.findByLabelText(/^Nota/);
  const user = userEvent.setup();
  for (const value of ["", "-1", "21"]) {
    fireEvent.change(input, { target: { value } });
    await user.click(screen.getByText("Guardar calificación"));
    expect(screen.getByText("Escribe una nota válida entre 0 y 20.")).toBeTruthy();
  }
  expect(calls.some((call) => call.method === "POST")).toBe(false);
  fireEvent.change(input, { target: { value: "1.5" } });
  await user.click(screen.getByText("Guardar calificación"));
  await waitFor(() => expect(calls.find((call) => call.method === "POST")?.body.nota).toBe(1.5));
});

test("Conflicto de versión exige recarga explícita y usa la versión nueva", async () => {
  let current = delivery();
  let attempts = 0;
  const calls = backend({ "GET /api/entregas/10": () => current,
    "POST /api/entregas/10/calificar": () => ++attempts === 1 ? reply(409, { code: "VERSION_DESACTUALIZADA" }) : current });
  const saved = vi.fn();
  render(<DeliveryGradeDialog taskId={1} deliveryId={10} student="Lucía" onClose={vi.fn()} onRefresh={vi.fn()} onSaved={saved} />);
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText(/^Nota/), "15");
  await user.click(screen.getByText("Guardar calificación"));
  await screen.findByText(/La entrega cambió/);
  expect(screen.queryByText("Guardar calificación")).toBeNull();
  expect(attempts).toBe(1);
  current = delivery({ version: 9, nota: 12 });
  await user.click(screen.getByText("Descartar cambios y actualizar"));
  expect((await screen.findByLabelText(/^Nota/)).value).toBe("12");
  await user.click(screen.getByText("Guardar calificación"));
  await waitFor(() => expect(saved).toHaveBeenCalledOnce());
  expect(calls.filter((call) => call.method === "POST").at(-1).body.version).toBe(9);
});

test.each([
  ["NOTA_FUERA_DE_RANGO", /La nota no puede superar/, false],
  ["TAREA_NO_CALIFICABLE", /La tarea ya no permite calificar/, true],
  ["ENTREGA_ANULADA", /La entrega está anulada/, true],
  ["ENTREGA_NO_ENCONTRADA", /La entrega ya no está disponible/, true],
])("Gestiona %s sin éxito falso ni reenvío automático", async (code, expected, blocked) => {
  const calls = backend({ "POST /api/entregas/10/calificar": reply(code === "ENTREGA_NO_ENCONTRADA" ? 404 : 422, { code }) });
  const saved = vi.fn(), refresh = vi.fn(), close = vi.fn();
  render(<DeliveryGradeDialog taskId={1} deliveryId={10} student="Lucía" onClose={close} onRefresh={refresh} onSaved={saved} />);
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText(/^Nota/), "18");
  await user.click(screen.getByText("Guardar calificación"));
  expect((await screen.findByRole("alert")).textContent).toMatch(expected);
  expect(!!screen.queryByText("Guardar calificación")).toBe(!blocked);
  expect(saved).not.toHaveBeenCalled();
  expect(calls.filter((call) => call.method === "POST")).toHaveLength(1);
  await user.click(screen.getByText(blocked ? "Actualizar seguimiento" : "Cancelar"));
  if (blocked) expect(refresh).toHaveBeenCalledOnce();
  else {
    expect(close).not.toHaveBeenCalled();
    const discard = within(screen.getByRole("alertdialog", { name: "¿Descartar cambios de la entrega?" }));
    await user.click(discard.getByRole("button", { name: "Descartar cambios" }));
    expect(close).toHaveBeenCalledOnce();
    expect(refresh).not.toHaveBeenCalled();
  }
});

test("Descarga autenticada con nombre original y libera la URL; informa errores", async () => {
  const StandardURL = globalThis.URL;
  vi.stubGlobal("URL", class extends StandardURL {
    static createObjectURL = vi.fn(() => "blob:entrega");
    static revokeObjectURL = vi.fn();
  });
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  backend();
  const ordinaryFetch = globalThis.fetch;
  let fail = false;
  const requests = [];
  vi.stubGlobal("fetch", vi.fn(async (url, options) => {
    if (!url.endsWith("/api/archivos/80/descarga")) return ordinaryFetch(url, options);
    requests.push(options);
    return fail ? { ok: false, status: 403, json: async () => ({ message: "Sin acceso al archivo" }) }
      : { ok: true, status: 200, blob: async () => new Blob(["%PDF"], { type: "application/pdf" }) };
  }));
  render(<TaskDeliveries taskId={1} canWrite={false} />);
  const user = userEvent.setup();
  const download = await screen.findByRole("button", { name: "Descargar respuesta.pdf" });
  await user.click(download);
  await waitFor(() => expect(click).toHaveBeenCalledOnce());
  expect(click.mock.instances[0].download).toBe("respuesta.pdf");
  expect(requests[0].headers.get("Authorization")).toBe("Bearer test-token");
  expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:entrega");
  fail = true;
  await user.click(download);
  expect((await screen.findByRole("alert")).textContent).toContain("Sin acceso al archivo");
});

test("Vacío y error del listado permiten actualización, sin filas ficticias", async () => {
  let fail = true;
  backend({ "GET /api/tareas/1/entregas": () => fail ? reply(500, { message: "No se pudieron consultar entregas" }) : page([]) });
  render(<TaskDeliveries taskId={1} canWrite={false} />);
  await screen.findByText("No se pudieron consultar entregas");
  expect(screen.queryByRole("table")).toBeNull();
  fail = false;
  await userEvent.setup().click(screen.getByText("Actualizar"));
  expect(await screen.findByText("Esta tarea aún no tiene entregas recibidas.")).toBeTruthy();
});

test("Con la asignación retirada (US-58) muestra las entregas sin permitir calificar", async () => {
  backend({ "GET /api/asignaciones-cursos/42": { id: 42, activo: false } });
  render(<TaskDeliveries taskId={1} canWrite />);
  await screen.findByText("Lucía Pérez");
  expect(await screen.findByText("Ya no estás asignado a este curso y sección: consulta de solo lectura.")).toBeTruthy();
  expect(screen.queryByRole("button", { name: /Calificar|Modificar calificación/ })).toBeNull();
});

test("Reintenta tarea inaccesible sin consultar alumnos o entregas antes", async () => {
  let fail = true;
  const calls = backend({ "GET /api/tareas/1": () => fail ? reply(403, { message: "No autorizada" }) : task() });
  render(<TaskDeliveries taskId={1} canWrite />);
  await screen.findByText("No autorizada");
  expect(calls).toHaveLength(1);
  fail = false;
  await userEvent.setup().click(screen.getByText("Reintentar carga"));
  await screen.findByText("Lucía Pérez");
});

test("Si la tarea se archivó o la entrega se anuló antes de abrir, bloquea el formulario", async () => {
  backend({ "GET /api/tareas/1": task({ estado: "ARCHIVADA" }) });
  render(<DeliveryGradeDialog taskId={1} deliveryId={10} student="Lucía" onClose={vi.fn()} onRefresh={vi.fn()} onSaved={vi.fn()} />);
  expect(await screen.findByText(/Solo se pueden calificar entregas no anuladas/)).toBeTruthy();
  expect(screen.queryByText("Guardar calificación")).toBeNull();
});

test("Bloquea doble envío mientras guarda y admite comentario vacío como null", async () => {
  let release;
  const calls = backend({ "POST /api/entregas/10/calificar": () => new Promise((resolve) => { release = resolve; }) });
  const saved = vi.fn();
  render(<DeliveryGradeDialog taskId={1} deliveryId={10} student="Lucía" onClose={vi.fn()} onRefresh={vi.fn()} onSaved={saved} />);
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText(/^Nota/), "18");
  await user.clear(screen.getByLabelText("Comentario"));
  await user.dblClick(screen.getByText("Guardar calificación"));
  expect(screen.getByText("Guardando…").disabled).toBe(true);
  expect(screen.getByText("Cancelar").disabled).toBe(true);
  expect(calls.filter((call) => call.method === "POST")).toHaveLength(1);
  expect(calls.find((call) => call.method === "POST").body.comentario).toBeNull();
  await act(async () => { release(delivery({ nota: 18 })); });
  expect(saved).toHaveBeenCalledOnce();
});

test("Acceso Entregas por estado y ruta protegida por TAREAS_LEER", async () => {
  backend({ "GET /api/tareas": page(["BORRADOR", "PUBLICADA", "CERRADA", "ARCHIVADA"].map((estado, id) => task({ id: id + 1, nombre: estado, estado }))) });
  const view = render(<Tasks />);
  await screen.findByRole("link", { name: "Entregas de PUBLICADA" });
  expect(screen.queryByRole("link", { name: "Entregas de BORRADOR" })).toBeNull();
  expect(screen.getAllByRole("link", { name: /Entregas de/ })).toHaveLength(3);
  expect(screen.getByRole("link", { name: "Entregas de ARCHIVADA" }).getAttribute("href")).toBe("/tareas/4/entregas");
  view.unmount();
  const calls = backend();
  const user = { id: "99", role: "DOCENTE", fullName: "Ana", permissions: [] };
  const root = render(<SessionUserProvider value={user}><ModuleOutlet /></SessionUserProvider>);
  expect(screen.getByText("No tienes permiso para acceder a esta opción.")).toBeTruthy();
  expect(calls).toHaveLength(0);
  root.rerender(<SessionUserProvider value={{ ...user, permissions: ["TAREAS_LEER"] }}><ModuleOutlet /></SessionUserProvider>);
  await screen.findByText("Lucía Pérez");
  expect(screen.queryByRole("button", { name: /Calificar/ })).toBeNull();
});

test("Ruta inválida no consulta la API; borrador no consulta entregas", async () => {
  setUrl("/tareas/no/entregas");
  const calls = backend({ "GET /api/tareas/1": task({ estado: "BORRADOR" }) });
  const user = { id: "99", role: "DOCENTE", permissions: ["TAREAS_LEER"] };
  render(<SessionUserProvider value={user}><ModuleOutlet /></SessionUserProvider>);
  expect(screen.getByText("La dirección de seguimiento no es válida.")).toBeTruthy();
  expect(calls).toHaveLength(0);
  await act(async () => setUrl("/tareas/1/entregas"));
  expect(await screen.findByText(/El seguimiento está disponible/)).toBeTruthy();
  expect(calls.some((call) => call.path.endsWith("/entregas"))).toBe(false);
});

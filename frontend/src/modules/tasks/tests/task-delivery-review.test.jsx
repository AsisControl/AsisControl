// cobertura: revisar la entrega de un estudiante (calificar, corregir, quitar nivel y devolución)
import { beforeEach, expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, reply, signIn } from "../../../../tests/support/backend.js";
import { TaskDeliveryReview } from "../task-delivery-review";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const tarea = { id: 8, nombre: "Ensayo", estado: "PUBLICADA", puntajeMaximo: 20, modeloCualitativo: false, evaluacionAcademica: { id: 30 } };
const alumno = { alumnoId: 7, nombreCompleto: "Lucía Prado", codigoAlumno: "E7", entregaId: 3, estado: "ENTREGADO", nota: null, nivelLogro: null };
const entrega = {
  id: 3, tareaId: 8, alumnoId: 7, estado: "ENTREGADO", comentario: null, version: 2,
  fechaHoraEntrega: "2026-11-18T10:30:00", respuesta: "Mi ensayo", archivos: [],
};
const planilla = (estado, notas = [], modeloCualitativo = false) => ({
  evaluacion: { estado, modeloCualitativo, competencias: [{ id: 1, nombre: "Escribe textos" }] },
  alumnos: [{ idAlumno: 7, notas }],
});
const nota = { idCalificacion: 90, idCompetencia: 1, notaNumerica: null, nivelLogro: "B", observacion: "Mejorar cierre", version: 5 };

function abrir({ task = tarea, student = alumno, canGrade = true, routes = {} } = {}) {
  const calls = fakeBackend({ "GET /api/entregas/3": entrega, ...routes });
  const props = { onClose: vi.fn(), onSaved: vi.fn() };
  render(<TaskDeliveryReview task={task} student={student} canGrade={canGrade} {...props} />);
  return { calls, ...props };
}

beforeEach(() => signIn({ role: "DOCENTE", permissions: ["TAREAS_CALIFICAR"] }));

test("valida las notas del borrador y las guarda con su observación", async () => {
  const { calls, onSaved } = abrir({ routes: {
    "GET /api/evaluaciones/30/planilla": planilla("BORRADOR"), "PUT /api/tareas/8/calificaciones": {},
  } });
  expect(await screen.findByText("Mi ensayo")).toBeTruthy();
  const campo = screen.getByLabelText(/^Nota/);
  await userEvent.type(campo, "15.123456789012");
  await userEvent.click(screen.getByRole("button", { name: "Guardar calificación" }));
  expect(await screen.findByText("Escribe notas válidas entre 0 y 20.")).toBeTruthy();
  await userEvent.clear(campo);
  await userEvent.type(campo, "17");
  await userEvent.type(screen.getByLabelText("Observación opcional"), " Bien ");
  await userEvent.click(screen.getByRole("button", { name: "Guardar calificación" }));
  expect(onSaved).toHaveBeenCalled();
  expect(calls.find(c => c.method === "PUT").body.calificaciones[0]).toMatchObject({
    idEvaluacion: 30, idAlumno: 7, idCompetencia: 1, notaNumerica: "17", observacion: "Bien", versionEsperada: -1,
  });
});

test("en el modelo cualitativo pide nivel, permite quitar el elegido y el ya registrado", async () => {
  let fallar = true;
  const { onSaved } = abrir({ routes: {
    "GET /api/evaluaciones/30/planilla": planilla("BORRADOR", [nota], true),
    "DELETE /api/tareas/8/calificaciones/90": () => (fallar ? reply(409, { message: "La calificación cambió" }) : {}),
  } });
  await screen.findByText("Mi ensayo");
  await userEvent.click(screen.getByRole("button", { name: "A: Escribe textos" }));
  await userEvent.click(screen.getByRole("button", { name: "Quitar nivel de Escribe textos" }));
  await userEvent.click(screen.getByRole("button", { name: "Quitar nivel de Escribe textos" }));
  const confirmar = await screen.findByRole("alertdialog", { name: "¿Quitar el nivel registrado?" });
  expect(confirmar).toBeTruthy();
  await userEvent.click(screen.getByRole("button", { name: "Quitar nivel" }));
  expect(await screen.findByText("La calificación cambió")).toBeTruthy();
  fallar = false;
  await userEvent.click(screen.getByRole("button", { name: "Quitar nivel de Escribe textos" }));
  await userEvent.click(await screen.findByRole("button", { name: "Quitar nivel" }));
  expect(onSaved).toHaveBeenCalled();
});

test("corrige una calificación publicada con motivo y confirma", async () => {
  let fallar = true;
  const notaNumerica = { ...nota, notaNumerica: 12, nivelLogro: "B" };
  const { calls, onSaved } = abrir({ routes: {
    "GET /api/evaluaciones/30/planilla": planilla("PUBLICADA", [notaNumerica]),
    "POST /api/tareas/8/correcciones": () => (fallar ? reply(409, { code: "VERSION_DESACTUALIZADA", message: "x" }) : {}),
  } });
  expect(await screen.findByText("Calificaciones publicadas")).toBeTruthy();
  expect(screen.getByText("12 · Mejorar cierre")).toBeTruthy();
  await userEvent.click(screen.getByRole("button", { name: "Corregir calificación" }));
  await userEvent.click(screen.getByRole("button", { name: "Cancelar corrección" }));
  await userEvent.click(screen.getByRole("button", { name: "Corregir calificación" }));
  const campo = screen.getByLabelText(/^Nota/);
  await userEvent.clear(campo);
  await userEvent.type(campo, "15");
  await userEvent.type(screen.getByLabelText("Motivo de la corrección"), "Error de suma");
  await userEvent.click(screen.getByRole("button", { name: "Revisar corrección" }));
  await userEvent.click(await screen.findByRole("button", { name: "Volver" }));
  await userEvent.click(screen.getByRole("button", { name: "Revisar corrección" }));
  await userEvent.click(await screen.findByRole("button", { name: "Confirmar corrección" }));
  expect(await screen.findByText("Los datos cambiaron. Actualiza la información antes de guardar.")).toBeTruthy();
  fallar = false;
  await userEvent.click(screen.getByRole("button", { name: "Revisar corrección" }));
  await userEvent.click(await screen.findByRole("button", { name: "Confirmar corrección" }));
  expect(onSaved).toHaveBeenCalled();
  expect(calls.filter(c => c.method === "POST").at(-1).body).toEqual({
    motivo: "Error de suma", correcciones: [{ idCalificacion: 90, version: 5, notaNumerica: "15" }],
  });
});

test("sin entrega y con la tarea cerrada califica de forma general y guarda la devolución", async () => {
  const task = { ...tarea, estado: "CERRADA", evaluacionAcademica: null };
  const ruta = "/api/tareas/8/calificaciones-sin-entrega/7";
  const { calls, onSaved } = abrir({ task, student: { ...alumno, entregaId: null, codigoAlumno: null }, routes: {
    [`GET ${ruta}`]: { nota: null, nivelLogro: null, observacion: "Sin presentar", devolucion: null },
    [`PUT ${ruta}/devolucion`]: {}, [`PUT ${ruta}`]: {},
  } });
  expect(await screen.findByText("Sin entrega. El estudiante no presentó una entrega.")).toBeTruthy();
  await userEvent.type(screen.getByLabelText("Devolución docente (opcional)"), "Conversemos");
  await userEvent.click(screen.getByRole("button", { name: "Guardar devolución" }));
  expect(await screen.findByText("Devolución guardada.")).toBeTruthy();
  expect(calls.find(c => c.path === `${ruta}/devolucion`).body).toEqual({ comentario: "Conversemos" });
  await userEvent.type(screen.getByLabelText(/^Nota/), "0");
  await userEvent.click(screen.getByRole("button", { name: "Guardar calificación" }));
  expect(onSaved).toHaveBeenCalled();
  expect(calls.find(c => c.method === "PUT" && c.path === ruta).body).toEqual({ nota: "0", observacion: "Sin presentar" });
});

test("guarda la devolución de una entrega y avisa si falla", async () => {
  let fallar = true;
  abrir({ routes: {
    "GET /api/evaluaciones/30/planilla": planilla("PUBLICADA", []),
    "PUT /api/entregas/3/devolucion": () => (fallar ? reply(500, { message: "No se guardó" }) : { ...entrega, comentario: "Ok", version: 3 }),
  } });
  await userEvent.type(await screen.findByLabelText("Devolución docente (opcional)"), "Ok");
  await userEvent.click(screen.getByRole("button", { name: "Guardar devolución" }));
  expect(await screen.findByText("No se guardó")).toBeTruthy();
  fallar = false;
  await userEvent.click(screen.getByRole("button", { name: "Guardar devolución" }));
  expect(await screen.findByText("Devolución guardada.")).toBeTruthy();
});

test("sin permiso solo consulta la calificación y la devolución", async () => {
  const task = { ...tarea, evaluacionAcademica: null };
  fakeBackend({ "GET /api/entregas/3": { ...entrega, comentario: "Buen ensayo" } });
  render(<TaskDeliveryReview task={task} student={{ ...alumno, estado: "ENTREGADO_TARDE", nota: 14 }} canGrade={false}
    onClose={vi.fn()} onSaved={vi.fn()} />);
  expect(await screen.findByText(/Entregado tarde/)).toBeTruthy();
  expect(screen.getByText("Nota: 14 / 20")).toBeTruthy();
  expect(screen.getByText("Devolución docente: Buen ensayo")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Guardar calificación" })).toBeNull();
});

test("si la entrega no corresponde al estudiante lo informa", async () => {
  fakeBackend({ "GET /api/entregas/3": { ...entrega, alumnoId: 99 }, "GET /api/evaluaciones/30/planilla": planilla("BORRADOR") });
  render(<TaskDeliveryReview task={tarea} student={alumno} canGrade onClose={vi.fn()} onSaved={vi.fn()} />);
  expect(await screen.findByText("La entrega no corresponde a este estudiante.")).toBeTruthy();
});

test("al cerrar con cambios pide confirmar y sin cambios cierra directo", async () => {
  const { onClose } = abrir({ routes: { "GET /api/evaluaciones/30/planilla": planilla("BORRADOR") } });
  await screen.findByText("Mi ensayo");
  await userEvent.keyboard("{Escape}");
  expect(onClose).toHaveBeenCalledTimes(1);
  await userEvent.type(screen.getByLabelText(/^Nota/), "10");
  await userEvent.keyboard("{Escape}");
  await userEvent.click(await screen.findByRole("button", { name: "Seguir editando" }));
  await userEvent.keyboard("{Escape}");
  await userEvent.click(await screen.findByRole("button", { name: "Descartar cambios" }));
  expect(onClose).toHaveBeenCalledTimes(2);
});

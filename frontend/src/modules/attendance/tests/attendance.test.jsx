import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, reply, signIn } from "../../../../tests/support/backend.js";
import { currentUrl, setUrl } from "../../../../tests/support/navigation.js";
import { SessionDetail, SessionHistory } from "@/modules/attendance";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const sesionClase = (overrides = {}) => ({
  id: 1, asignacionCursoId: 42, fecha: "2026-08-03", horaInicio: "07:30:00", horaFin: "09:00:00",
  estado: "PENDIENTE", puedeTomar: false, puedeVer: true, puedeEditar: false, version: 0, ...overrides,
});
const alumno = (overrides = {}) => ({
  alumnoId: 1, codigoAlumno: "EST-001", nombreCompleto: "Álvarez, Diego", estado: null,
  asistenciaId: null, justificacionId: null, estadoJustificacion: null, ...overrides,
});
const sesion = (overrides = {}) => ({
  id: 7, asignacionCursoId: 42, curso: "Matemática", seccion: "3° Grado A", fecha: "2026-08-17",
  horaInicio: "07:30:00", horaFin: "09:00:00", estado: "PENDIENTE", puedeTomar: true, puedeVer: true,
  puedeEditar: false, pendientes: 2, registradoPor: null, registradoEn: null, version: 3,
  alumnos: [alumno(), alumno({ alumnoId: 2, codigoAlumno: "EST-002", nombreCompleto: "Bravo, Ana" })],
  ...overrides,
});
const tomada = (overrides = {}) => sesion({
  estado: "TOMADA", puedeTomar: false, puedeEditar: true, pendientes: 0,
  registradoPor: "Patricia Morales", registradoEn: "2026-08-17T09:40:00",
  alumnos: [
    alumno({ estado: "PRESENTE", asistenciaId: 10 }),
    alumno({ alumnoId: 2, nombreCompleto: "Bravo, Ana", estado: "INASISTENCIA", asistenciaId: 11, justificacionId: 5, estadoJustificacion: "APROBADA" }),
  ],
  ...overrides,
});
const SESION = "GET /api/asistencias/sesiones/7";
const CORRECCIONES = "GET /api/asistencias/sesiones/7/correcciones";

beforeEach(() => {
  signIn({ role: "DOCENTE", id: "999", permissions: ["ASISTENCIA_LEER", "ASISTENCIA_ESCRIBIR"] });
  setUrl("/mis-cursos/42/asistencia/7");
});

test("El historial muestra los estados, el resumen y una sola acción por sesión", async () => {
  fakeBackend({
    "GET /api/asistencias/asignaciones/42/sesiones": [
      sesionClase({ id: 1, estado: "TOMADA", puedeEditar: true }),
      sesionClase({ id: 2 }),
      sesionClase({ id: 3, fecha: "2026-08-17", estado: "BORRADOR", puedeTomar: true }),
      sesionClase({ id: 4, fecha: "2026-08-19", puedeVer: false }),
    ],
    "GET /api/asistencias/asignaciones": [{ id: 42, curso: "Matemática", seccion: "3° Grado A" }],
  });
  render(<SessionHistory asignacionId={42} />);
  expect(await screen.findByRole("heading", { name: "Asistencia: Matemática - 3° Grado A" })).toBeTruthy();
  expect(screen.getByText("4 sesiones")).toBeTruthy();
  expect(screen.getByText("1 pendiente")).toBeTruthy();
  expect(screen.getByText("1 sin registrar")).toBeTruthy();
  const filas = screen.getAllByRole("row").slice(1);
  expect(within(filas[0]).getByRole("link", { name: "Ver asistencia" }).getAttribute("href")).toBe("/mis-cursos/42/asistencia/1");
  expect(within(filas[1]).getByText("Sin registrar")).toBeTruthy();
  expect(within(filas[1]).getByRole("link", { name: "Ver sesión" })).toBeTruthy();
  expect(within(filas[2]).getByRole("link", { name: "Continuar" })).toBeTruthy();
  expect(within(filas[2]).getByText("Hoy")).toBeTruthy();
  expect(within(filas[3]).queryByRole("link")).toBeNull();
  expect(within(filas[3]).getByLabelText("Sin acción disponible").textContent).toBe("—");
});

test("Guardar exige una marca y Finalizar exige a todos los estudiantes", async () => {
  const calls = fakeBackend({
    [SESION]: sesion(),
    "PUT /api/asistencias/sesiones/7/borrador": sesion({ estado: "BORRADOR", version: 4, alumnos: [alumno({ estado: "PRESENTE" }), alumno({ alumnoId: 2, nombreCompleto: "Bravo, Ana" })] }),
  });
  render(<SessionDetail asignacionId={42} sesionId={7} />);
  const guardar = await screen.findByRole("button", { name: "Guardar borrador" });
  const finalizar = screen.getByRole("button", { name: "Finalizar asistencia" });
  expect(guardar.disabled).toBe(true);
  expect(finalizar.disabled).toBe(true);
  expect(screen.getByText("Faltan 2 estudiantes")).toBeTruthy();
  const grupo = screen.getByRole("group", { name: "Asistencia de Álvarez, Diego" });
  await userEvent.click(within(grupo).getByRole("button", { name: "Presente" }));
  expect(within(grupo).getByRole("button", { name: "Presente" }).getAttribute("aria-pressed")).toBe("true");
  expect(finalizar.disabled).toBe(true);
  await userEvent.click(guardar);
  expect(await screen.findByText("Borrador guardado.")).toBeTruthy();
  expect(calls.find((c) => c.method === "PUT").body).toEqual({ marcas: [{ alumnoId: 1, estado: "PRESENTE" }] });
  expect(screen.getByRole("button", { name: "Guardar borrador" }).disabled).toBe(true);
});

test("Todos presentes marca a todos sin guardar, aunque haya búsqueda, y permite finalizar", async () => {
  const calls = fakeBackend({
    [SESION]: sesion(),
    "POST /api/asistencias/sesiones/7/finalizar": tomada(),
    [CORRECCIONES]: [],
  });
  render(<SessionDetail asignacionId={42} sesionId={7} />);
  await userEvent.type(await screen.findByLabelText("Buscar estudiante"), "alvarez");
  expect(screen.queryByText("Bravo, Ana")).toBeNull();
  await userEvent.click(screen.getByRole("button", { name: "Todos presentes" }));
  expect(screen.getByText("2 de 2 marcados")).toBeTruthy();
  expect(calls.filter((c) => c.method !== "GET")).toHaveLength(0);
  await userEvent.click(screen.getByRole("button", { name: "Finalizar asistencia" }));
  expect(await screen.findByText("Asistencia finalizada correctamente.")).toBeTruthy();
  const cuerpo = calls.find((c) => c.method === "POST").body;
  expect(cuerpo.version).toBe(3);
  expect(cuerpo.marcas).toHaveLength(2);
});

test("Cancelar con cambios pide confirmación y Seguir editando conserva las marcas", async () => {
  fakeBackend({ [SESION]: sesion() });
  render(<SessionDetail asignacionId={42} sesionId={7} />);
  const grupo = await screen.findByRole("group", { name: "Asistencia de Bravo, Ana" });
  await userEvent.click(within(grupo).getByRole("button", { name: "Tardanza" }));
  await userEvent.click(screen.getByRole("button", { name: "Cancelar" }));
  await userEvent.click(within(screen.getByRole("alertdialog")).getByRole("button", { name: "Seguir editando" }));
  expect(screen.queryByRole("alertdialog")).toBeNull();
  expect(screen.getByText("50%")).toBeTruthy();
  await userEvent.click(screen.getByRole("button", { name: "Cancelar" }));
  await userEvent.click(screen.getByRole("button", { name: "Salir sin guardar" }));
  await waitFor(() => expect(currentUrl()).toBe("/mis-cursos/42/asistencia"));
});

test("Solo lectura muestra auditoría y abre la justificación sin salir de la sesión", async () => {
  fakeBackend({
    [SESION]: tomada(),
    [CORRECCIONES]: [{ id: 1, fechaHora: "2026-08-18T08:15:00", docenteId: 2, docente: "Patricia Morales", motivo: "Error de digitación", cambios: [] }],
    "GET /api/justificaciones-asistencia/5": {
      id: 5, motivo: "Cita médica", fechaHoraRevision: "2026-08-18T10:15:00", observacionRevision: null,
      estado: "APROBADA", archivos: [{ id: 3, nombreOriginal: "constancia.pdf" }],
    },
  });
  render(<SessionDetail asignacionId={42} sesionId={7} />);
  expect(await screen.findByText("Registrada por Patricia Morales · 17-08-2026 a las 09:40")).toBeTruthy();
  expect(await screen.findByText("Corregida por Patricia Morales · 18-08-2026 a las 08:15")).toBeTruthy();
  expect(screen.getByText("Motivo: Error de digitación").getAttribute("title")).toBe("Error de digitación");
  expect(screen.getAllByText("Tomada")).toHaveLength(1);
  expect(screen.getAllByText(/17-08-2026 · 07:30 AM/)).toHaveLength(1);
  expect(screen.getByText("Presente")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Editar asistencia" })).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Guardar borrador" })).toBeNull();
  await userEvent.click(screen.getByRole("button", { name: "Justificada" }));
  const dialogo = await screen.findByRole("dialog");
  expect(await within(dialogo).findByText("Cita médica")).toBeTruthy();
  expect(within(dialogo).getByText("constancia.pdf")).toBeTruthy();
  expect(within(dialogo).queryByText("Observación")).toBeNull();
});

test("Una sesión pasada sin asistencia avisa y no ofrece registrar ni editar", async () => {
  fakeBackend({ [SESION]: sesion({ puedeTomar: false, fecha: "2026-08-10" }) });
  render(<SessionDetail asignacionId={42} sesionId={7} />);
  expect(await screen.findByText("Esta sesión no tiene asistencia registrada y ya no se puede registrar retroactivamente.")).toBeTruthy();
  expect(screen.getByText("Sin registrar")).toBeTruthy();
  expect(screen.queryByText("Presentes")).toBeNull();
  expect(screen.queryByText("Registro")).toBeNull();
  expect(screen.queryByRole("button", { name: "Editar asistencia" })).toBeNull();
  expect(screen.queryByRole("group")).toBeNull();
});

test("Una sesión futura no se abre", async () => {
  fakeBackend({ [SESION]: sesion({ puedeTomar: false, puedeVer: false }) });
  render(<SessionDetail asignacionId={42} sesionId={7} />);
  expect(await screen.findByText("Esta sesión todavía no está disponible.")).toBeTruthy();
});

test("Corregir exige cambios y motivo, y envía solo lo que cambió", async () => {
  const calls = fakeBackend({
    [SESION]: tomada(),
    [CORRECCIONES]: [],
    "PUT /api/asistencias/sesiones/7": tomada({ version: 5 }),
  });
  render(<SessionDetail asignacionId={42} sesionId={7} />);
  await userEvent.click(await screen.findByRole("button", { name: "Editar asistencia" }));
  expect(screen.getByText("Modo corrección")).toBeTruthy();
  expect(screen.getByText("Sin cambios por guardar")).toBeTruthy();
  expect(screen.queryByText("Sesión tomada")).toBeNull();
  const guardar = screen.getByRole("button", { name: "Guardar cambios" });
  expect(guardar.disabled).toBe(true);
  const grupo = screen.getByRole("group", { name: "Asistencia de Álvarez, Diego" });
  await userEvent.click(within(grupo).getByRole("button", { name: "Tardanza" }));
  expect(screen.getByText("antes: Presente")).toBeTruthy();
  expect(screen.getByText("Con cambios sin guardar")).toBeTruthy();
  expect(guardar.disabled).toBe(false);
  await userEvent.click(guardar);
  const modal = screen.getByRole("dialog");
  expect(within(modal).getByText("Presente → Tardanza")).toBeTruthy();
  const confirmar = within(modal).getByRole("button", { name: "Confirmar y guardar" });
  expect(confirmar.disabled).toBe(true);
  expect(within(modal).getByText("Escribe el motivo para confirmar.")).toBeTruthy();
  expect(document.activeElement).toBe(within(modal).getByLabelText(/Motivo de la corrección/));
  await userEvent.type(within(modal).getByLabelText(/Motivo de la corrección/), "Llegó tarde");
  expect(within(modal).getByText("11 / 500")).toBeTruthy();
  expect(confirmar.disabled).toBe(false);
  await userEvent.click(confirmar);
  expect(await screen.findByText("Cambios guardados correctamente.")).toBeTruthy();
  expect(calls.find((c) => c.method === "PUT").body).toEqual({
    marcas: [{ alumnoId: 1, estado: "TARDANZA" }], motivo: "Llegó tarde", version: 3,
  });
});

test("Muestra el error del backend y conserva las marcas", async () => {
  fakeBackend({
    [SESION]: sesion(),
    "PUT /api/asistencias/sesiones/7/borrador": reply(409, { message: "La asistencia fue modificada en otra pantalla" }),
  });
  render(<SessionDetail asignacionId={42} sesionId={7} />);
  const grupo = await screen.findByRole("group", { name: "Asistencia de Bravo, Ana" });
  await userEvent.click(within(grupo).getByRole("button", { name: "Presente" }));
  await userEvent.click(screen.getByRole("button", { name: "Guardar borrador" }));
  expect(await screen.findByRole("alert")).toBeTruthy();
  expect(screen.getByText("50%")).toBeTruthy();
});

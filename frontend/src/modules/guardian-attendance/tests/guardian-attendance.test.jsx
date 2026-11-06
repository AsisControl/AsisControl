// asistencia alumnos
import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { GuardianAttendance } from "@/modules/guardian-attendance";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const fila = (overrides = {}) => ({
  id: 1, fechaRegistro: "2026-09-08", horaRegistro: "07:30:00", estado: "PRESENTE", comentario: null, curso: "Matemática",
  justificacionId: null, estadoJustificacion: null, motivoJustificacion: null, observacionRevision: null, ...overrides,
});
const FILAS = [
  fila({ id: 1 }),
  fila({ id: 2, curso: "Comunicación" }),
  fila({ id: 3, estado: "TARDANZA", horaRegistro: "08:10:00", comentario: "Transporte" }),
  fila({ id: 4, estado: "PRESENTE", fechaRegistro: "2026-08-29" }),
  fila({ id: 5, estado: "INASISTENCIA_JUSTIFICADA", horaRegistro: null, curso: "Inglés", justificacionId: 8, estadoJustificacion: "APROBADA", motivoJustificacion: "Cita médica" }),
  fila({ id: 6, estado: "INASISTENCIA", horaRegistro: null, fechaRegistro: "2026-08-28", curso: "Comunicación" }),
  fila({ id: 7, estado: "INASISTENCIA", horaRegistro: null, curso: "Historia", justificacionId: 9, estadoJustificacion: "RECHAZADA", observacionRevision: "Sin sustento" }),
];
const ESTUDIANTES = "GET /api/familia/estudiantes";
const ASISTENCIAS = "GET /api/asistencias/alumnos";
const ENVIAR = "POST /api/justificaciones-asistencia";
const luis = { id: 5, codigoAlumno: "A-5", nombreCompleto: "Luis García" };

beforeEach(() => {
  signIn({ role: "APODERADO", id: "1", permissions: ["ASISTENCIA_LEER", "ARCHIVOS_ESCRIBIR"] });
});

const abrirModal = async () => {
  await userEvent.click(await screen.findByRole("button", { name: "Justificar Comunicación del 28 Ago 2026" }));
  return screen.findByRole("dialog");
};

test("Resume la asistencia y solo ofrece justificar inasistencias sin solicitud", async () => {
  const calls = fakeBackend({ [ESTUDIANTES]: [luis], [ASISTENCIAS]: page(FILAS) });
  render(<GuardianAttendance />);
  expect(await screen.findByText("57%")).toBeTruthy();
  expect(calls.find((call) => call.path === "/api/asistencias/alumnos").query.alumnoId).toBe("5");
  expect(screen.getAllByRole("button", { name: /^Justificar/ })).toHaveLength(1);
  expect(screen.getByText("08:10 · Transporte")).toBeTruthy();
  expect(screen.getByText("Justificada")).toBeTruthy();
  expect(screen.getByText("— · Cita médica")).toBeTruthy();
  expect(screen.getByText("Rechazada")).toBeTruthy();
  expect(screen.getByText("— · Sin sustento")).toBeTruthy();
});

test("Los filtros recalculan la tabla y los totales", async () => {
  fakeBackend({ [ESTUDIANTES]: [luis], [ASISTENCIAS]: page(FILAS) });
  render(<GuardianAttendance />);
  await screen.findByText("57%");
  await userEvent.selectOptions(screen.getByLabelText("Curso"), "Comunicación");
  expect(screen.getByText("50%")).toBeTruthy();
  expect(screen.queryByRole("cell", { name: "Historia" })).toBeNull();
  await userEvent.selectOptions(screen.getByLabelText("Mes"), "2026-08");
  expect(screen.getByText("0%")).toBeTruthy();
});

test("Envía el motivo con las observaciones y el sustento, y queda pendiente", async () => {
  let enviado = false;
  const calls = fakeBackend({
    [ESTUDIANTES]: [luis],
    [ASISTENCIAS]: () => page(FILAS.map((item) => item.id === 6 && enviado
      ? { ...item, justificacionId: 12, estadoJustificacion: "ENVIADA", motivoJustificacion: "Cita médica — Control" }
      : item)),
    "POST /api/archivos": { id: 30, nombreOriginal: "constancia.pdf" },
    [ENVIAR]: () => {
      enviado = true;
      return reply(201, { id: 12 });
    },
  });
  render(<GuardianAttendance />);
  const modal = await abrirModal();
  const boton = within(modal).getByRole("button", { name: "Enviar justificación" });
  expect(boton.disabled).toBe(true);
  await userEvent.selectOptions(within(modal).getByLabelText(/Motivo/), "Cita médica");
  await userEvent.type(within(modal).getByLabelText(/Observaciones/), "Control");
  await userEvent.upload(
    within(modal).getByLabelText("Archivo de sustento"),
    new File(["x"], "constancia.pdf", { type: "application/pdf" }),
  );
  expect(within(modal).getByText("constancia.pdf")).toBeTruthy();
  await userEvent.click(boton);
  expect(await screen.findByText("La justificación fue enviada y está pendiente de revisión.")).toBeTruthy();
  expect(calls.find((call) => call.method === "POST" && call.path === "/api/justificaciones-asistencia").body)
    .toEqual({ asistenciaAlumnoId: 6, motivo: "Cita médica — Control", archivoIds: [30] });
  expect(await screen.findByText("Justificación pendiente")).toBeTruthy();
  expect(screen.queryByRole("button", { name: /^Justificar/ })).toBeNull();
});

test("El motivo Otro exige observaciones", async () => {
  fakeBackend({ [ESTUDIANTES]: [luis], [ASISTENCIAS]: page(FILAS) });
  render(<GuardianAttendance />);
  const modal = await abrirModal();
  const boton = within(modal).getByRole("button", { name: "Enviar justificación" });
  await userEvent.selectOptions(within(modal).getByLabelText(/Motivo/), "Otro");
  expect(boton.disabled).toBe(true);
  await userEvent.type(within(modal).getByLabelText(/Observaciones/), "Viaje");
  expect(boton.disabled).toBe(false);
});

test("Un error del backend mantiene el modal y no repite la carga del archivo", async () => {
  const calls = fakeBackend({
    [ESTUDIANTES]: [luis],
    [ASISTENCIAS]: page(FILAS),
    "POST /api/archivos": { id: 30, nombreOriginal: "constancia.pdf" },
    [ENVIAR]: reply(409, { message: "La asistencia ya posee una justificación activa" }),
  });
  render(<GuardianAttendance />);
  const modal = await abrirModal();
  await userEvent.selectOptions(within(modal).getByLabelText(/Motivo/), "Enfermedad");
  await userEvent.upload(
    within(modal).getByLabelText("Archivo de sustento"),
    new File(["x"], "constancia.pdf", { type: "application/pdf" }),
  );
  const boton = within(modal).getByRole("button", { name: "Enviar justificación" });
  await userEvent.click(boton);
  expect(await within(modal).findByText("La asistencia ya posee una justificación activa")).toBeTruthy();
  await waitFor(() => expect(boton.disabled).toBe(false));
  await userEvent.click(boton);
  await waitFor(() => expect(calls.filter((call) => call.path === "/api/justificaciones-asistencia")).toHaveLength(2));
  expect(calls.filter((call) => call.path === "/api/archivos")).toHaveLength(1);
});

test("Avisa cuando el apoderado no tiene estudiantes", async () => {
  fakeBackend({ [ESTUDIANTES]: [] });
  render(<GuardianAttendance />);
  expect(await screen.findByText("No tienes estudiantes asociados.")).toBeTruthy();
});

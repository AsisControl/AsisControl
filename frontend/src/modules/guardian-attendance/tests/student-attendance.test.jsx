// asistencia alumnos
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { StudentAttendance } from "@/modules/guardian-attendance";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const fila = (overrides = {}) => ({
  id: 1, fechaRegistro: "2026-09-08", horaRegistro: "02:30:00", horaSesion: "07:30:00", estado: "PRESENTE",
  comentario: null, curso: "Matemática", anioAcademico: 2026, periodo: "Bimestre III",
  justificacionId: null, estadoJustificacion: null, motivoJustificacion: null, observacionRevision: null, ...overrides,
});
const FILAS = [
  fila({ id: 1 }),
  fila({ id: 2, estado: "TARDANZA", horaSesion: "09:00:00", comentario: "Llegó tarde" }),
  fila({ id: 3, estado: "INASISTENCIA", curso: "Comunicación" }),
  fila({ id: 4, estado: "INASISTENCIA", fechaRegistro: "2026-08-20", curso: "Historia", periodo: "Bimestre II",
    justificacionId: 9, estadoJustificacion: "ENVIADA", motivoJustificacion: "Cita médica" }),
];
const ASISTENCIAS = "GET /api/asistencias/alumnos";
const filasTabla = () => screen.getAllByRole("row").length - 1;

beforeEach(() => {
  signIn({ role: "ESTUDIANTE", id: "7", permissions: ["ASISTENCIA_LEER"] });
});
afterEach(() => {
  vi.useRealTimers();
});

const abrir = async (filas = FILAS) => {
  const calls = fakeBackend({ [ASISTENCIAS]: page(filas) });
  render(<StudentAttendance fullName="Mateo Rojas" />);
  await screen.findByText("50%");
  return calls;
};

test("Muestra solo su historial con la hora de la clase y el estado de cada justificación", async () => {
  const calls = await abrir();
  expect(screen.getByText("Mateo Rojas · Consulta de tu asistencia")).toBeTruthy();
  expect(calls.find((call) => call.path === "/api/asistencias/alumnos").query.alumnoId).toBeUndefined();
  expect(screen.getByText("Todo el año · Todos los cursos · 4 registros")).toBeTruthy();
  expect(screen.getAllByText("Mar, 08 Sep 2026")).toHaveLength(3);
  expect(screen.getByText("09:00")).toBeTruthy();
  expect(screen.queryByText("02:30")).toBeNull();
  expect(screen.getByText("Sin justificar")).toBeTruthy();
  expect(screen.getByText("Pendiente").closest("[title]").getAttribute("title")).toBe("Cita médica");
  expect(screen.getByText("Tienes 1 inasistencia sin justificar. Pídele a tu apoderado que la justifique.")).toBeTruthy();
});

test("Solo consulta: no hay selector de estudiante ni opción de justificar", async () => {
  await abrir();
  expect(screen.queryByLabelText("Estudiante:")).toBeNull();
  expect(screen.queryByRole("button", { name: /^Justificar/ })).toBeNull();
  expect(screen.queryByRole("columnheader", { name: "Acciones" })).toBeNull();
});

test("Las tarjetas resumen el periodo, los ceros quedan en gris y filtran la tabla por estado", async () => {
  await abrir();
  const tarjetas = screen.getAllByRole("button", { pressed: false });
  const inasistencias = tarjetas.find((tarjeta) => tarjeta.textContent === "2Inasistencias");
  await userEvent.click(inasistencias);
  expect(inasistencias.getAttribute("aria-pressed")).toBe("true");
  expect(filasTabla()).toBe(2);
  expect(screen.getByText("50%")).toBeTruthy();
  await userEvent.click(inasistencias);
  expect(filasTabla()).toBe(4);
  await userEvent.selectOptions(screen.getByLabelText("Curso"), "Comunicación");
  const cero = screen.getAllByRole("button").find((tarjeta) => tarjeta.textContent === "0Presentes");
  expect(cero.querySelector("strong").className).toMatch(/cero/);
});

test("Filtra por período académico y por rango personalizado, y Limpiar filtros vuelve al inicio", async () => {
  await abrir();
  expect(screen.queryByRole("button", { name: "Limpiar filtros" })).toBeNull();
  await userEvent.selectOptions(screen.getByLabelText("Periodo"), "Bimestre II");
  expect(screen.getByText("Bimestre II · Todos los cursos · 1 registro")).toBeTruthy();
  expect(screen.getByText("0%")).toBeTruthy();
  await userEvent.selectOptions(screen.getByLabelText("Periodo"), "Personalizado");
  await userEvent.type(screen.getByLabelText("Desde"), "01092026");
  expect(filasTabla()).toBe(3);
  await userEvent.click(screen.getByRole("button", { name: "Limpiar filtros" }));
  expect(screen.getByText("Todo el año · Todos los cursos · 4 registros")).toBeTruthy();
});

test("Este mes usa la fecha del dispositivo", async () => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(2026, 7, 25));
  await abrir();
  await userEvent.selectOptions(screen.getByLabelText("Periodo"), "Este mes");
  expect(filasTabla()).toBe(1);
  expect(screen.getByRole("cell", { name: /Historia/ })).toBeTruthy();
});

test("Sin resultados ofrece limpiar los filtros", async () => {
  await abrir();
  await userEvent.selectOptions(screen.getByLabelText("Curso"), "Comunicación");
  await userEvent.selectOptions(screen.getByLabelText("Estado"), "TARDANZA");
  expect(screen.getByRole("button", { name: "Filtros · 2" })).toBeTruthy();
  const vacio = screen.getByText("Aún no hay asistencias en este periodo").parentElement;
  await userEvent.click(within(vacio).getByRole("button", { name: "Limpiar filtros" }));
  expect(filasTabla()).toBe(4);
});

test("Muestra 20 registros y Ver más carga los siguientes", async () => {
  const muchas = Array.from({ length: 25 }, (_, indice) =>
    fila({ id: indice + 1, fechaRegistro: `2026-09-${String(indice + 1).padStart(2, "0")}` }));
  fakeBackend({ [ASISTENCIAS]: page(muchas) });
  render(<StudentAttendance fullName="Mateo Rojas" />);
  await screen.findByText("100%");
  expect(filasTabla()).toBe(20);
  expect(screen.getAllByRole("cell")[0].textContent).toBe("Vie, 25 Sep 2026");
  await userEvent.click(screen.getByRole("button", { name: "Ver más" }));
  expect(filasTabla()).toBe(25);
  expect(screen.queryByRole("button", { name: "Ver más" })).toBeNull();
});

test("Con registros de varios años muestra el selector de año", async () => {
  await abrir([...FILAS, fila({ id: 9, fechaRegistro: "2025-10-02", anioAcademico: 2025, periodo: "Bimestre IV" })]);
  expect(screen.getByLabelText("Año").value).toBe("2026");
  await userEvent.selectOptions(screen.getByLabelText("Año"), "2025");
  expect(screen.getByText("2025 · Todo el año · Todos los cursos · 1 registro")).toBeTruthy();
  expect(screen.queryByRole("option", { name: "Este mes" })).toBeNull();
});

test("Muestra el error del backend", async () => {
  fakeBackend({ [ASISTENCIAS]: reply(500, { message: "No se pudo cargar la asistencia" }) });
  render(<StudentAttendance fullName="Mateo Rojas" />);
  expect(await screen.findByText("No se pudo cargar la asistencia")).toBeTruthy();
});

test("Avisa cuando todavía no hay asistencias registradas", async () => {
  fakeBackend({ [ASISTENCIAS]: page([]) });
  render(<StudentAttendance fullName="Mateo Rojas" />);
  expect(await screen.findByText("Aún no hay asistencias registradas.")).toBeTruthy();
});

const bloque = (overrides = {}) => ({
  asignacionCursoId: 10, curso: "Matemática", diaSemana: "JUEVES", horaInicio: "07:30:00", horaFin: "08:15:00",
  ...overrides,
});
const HORARIO = "GET /api/horarios/mio";

test("Hoy muestra las clases del día con su asistencia o Por registrar", async () => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(2026, 10, 19, 10, 0));
  const calls = fakeBackend({
    [ASISTENCIAS]: page([
      ...FILAS,
      fila({ id: 20, fechaRegistro: "2026-11-19", asignacionCursoId: 10, horaSesion: "07:30:00", estado: "TARDANZA" }),
    ]),
    [HORARIO]: [
      bloque(),
      bloque({ horaInicio: "08:15:00", horaFin: "09:00:00" }),
      bloque({ asignacionCursoId: 11, curso: "Comunicación", horaInicio: "09:00:00", horaFin: "09:45:00" }),
      bloque({ asignacionCursoId: 12, curso: "Historia", diaSemana: "VIERNES" }),
    ],
  });
  render(<StudentAttendance fullName="Mateo Rojas" />);
  const hoy = await screen.findByRole("region", { name: "Hoy · Jue, 19 Nov 2026" });
  expect(calls.find((call) => call.path === "/api/horarios/mio").query.anioAcademico).toBe("2026");
  const clases = within(hoy).getAllByRole("listitem");
  expect(clases.map((clase) => clase.textContent)).toEqual([
    "Matemática07:30Tardanza",
    "Comunicación09:00Por registrar",
  ]);
});

test("Hoy avisa cuando no hay clases y no bloquea la pantalla si falla el horario", async () => {
  fakeBackend({ [ASISTENCIAS]: page(FILAS), [HORARIO]: reply(500, { message: "Error" }) });
  render(<StudentAttendance fullName="Mateo Rojas" />);
  expect(await screen.findByText("Hoy no tienes clases.")).toBeTruthy();
  expect(screen.getByText("50%")).toBeTruthy();
});

test("El alumno abre el detalle de un registro solo para consultar", async () => {
  await abrir();
  await userEvent.click(screen.getByRole("button", { name: "Ver detalle de Historia del Jue, 20 Ago 2026" }));
  const detalle = screen.getByRole("dialog", { name: "Detalle de asistencia" });
  expect(within(detalle).getByText("Bimestre II")).toBeTruthy();
  expect(within(detalle).getByText("Pendiente")).toBeTruthy();
  expect(within(detalle).getByText("Cita médica")).toBeTruthy();
  expect(within(detalle).queryByRole("button", { name: "Justificar" })).toBeNull();
});

test("Una clase sin marcar se ve aparte, no cuenta en el % y no se justifica", async () => {
  fakeBackend({ [ASISTENCIAS]: page([...FILAS, fila({ id: 30, estado: "SIN_MARCACION", curso: "Arte" })]) });
  render(<StudentAttendance fullName="Mateo Rojas" />);
  expect(await screen.findByText("50%")).toBeTruthy();
  const tarjeta = screen.getAllByRole("button").find((boton) => boton.textContent === "1Sin marcar");
  expect(tarjeta).toBeTruthy();
  const chip = screen.getByText("Sin marcar", { selector: ".status" });
  expect(chip.closest("[title]").getAttribute("title")).toBe("El docente no registró esta asistencia");
  expect(screen.getByText("Tienes 1 inasistencia sin justificar. Pídele a tu apoderado que la justifique.")).toBeTruthy();
  await userEvent.click(chip);
  expect(within(screen.getByRole("dialog")).getByText("El docente no registró esta asistencia.")).toBeTruthy();
});

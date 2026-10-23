import { beforeEach, expect, test } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { GuardianEnrollment } from "@/modules/enrollment";

const children = [
  { id: 42, codigoAlumno: "EST-042", nombreCompleto: "Lucía Flores" },
  { id: 43, codigoAlumno: "EST-043", nombreCompleto: "Mateo Flores" },
];
const offers = [{ anio: 2027, grados: [2, 3] }];

const request = (overrides = {}) => ({
  id: 100,
  alumnoId: 42,
  alumno: "Lucía Flores",
  codigoAlumno: "EST-042",
  anioAcademico: 2027,
  grado: 2,
  seccion: "Por asignar",
  estado: "BORRADOR",
  version: 1,
  fechaEnvio: null,
  fechaRevision: null,
  observaciones: null,
  motivoRechazo: null,
  ...overrides,
});

function backend(routes = {}) {
  return fakeBackend({
    "GET /api/familia/estudiantes": children,
    "GET /api/familia/oferta": offers,
    "GET /api/solicitudes-matricula": page([request()]),
    ...routes,
  });
}

beforeEach(() => {
  signIn({ role: "APODERADO", permissions: ["MATRICULA_LEER"] });
});

test("Lista las solicitudes de sus estudiantes", async () => {
  backend({
    "GET /api/solicitudes-matricula": page([
      request(),
      request({ id: 101, alumno: "Mateo Flores", estado: "ENVIADA", fechaEnvio: "2026-11-02T14:00:00Z" }),
    ]),
  });
  render(<GuardianEnrollment />);
  const row = (await screen.findByText("Mateo Flores", { selector: "strong" })).closest("tr");
  expect(within(row).getByText("2027 · 2°")).toBeTruthy();
  expect(within(row).getByText("Enviada")).toBeTruthy();
  expect(screen.getByText("Nueva solicitud").disabled).toBe(false);
});

test("Guarda un borrador con estudiante, año y grado ofertados", async () => {
  const calls = backend({
    "POST /api/solicitudes-matricula": ({ body }) => request({ alumnoId: body.alumnoId, grado: body.grado }),
  });
  const user = userEvent.setup();
  render(<GuardianEnrollment />);
  await user.click(await screen.findByText("Nueva solicitud"));
  const dialog = screen.getByRole("dialog", { name: "Nueva solicitud de matrícula" });
  const grade = within(dialog).getByLabelText(/Grado solicitado/);
  expect(grade.disabled).toBe(true);
  await user.selectOptions(within(dialog).getByLabelText(/Estudiante asociado/), "43");
  await user.selectOptions(within(dialog).getByLabelText(/Año con admisión abierta/), "2027");
  await user.selectOptions(within(dialog).getByLabelText(/Grado solicitado/), "3");
  await user.type(within(dialog).getByLabelText(/Observaciones/), "Hermano en 4°");
  await user.click(within(dialog).getByText("Guardar borrador"));
  const detail = await screen.findByRole("dialog", { name: "Detalle de mi solicitud" });
  expect(within(detail).getByText(/Borrador guardado/)).toBeTruthy();
  expect(calls.find((c) => c.method === "POST").body).toEqual({
    alumnoId: 43,
    anioAcademico: 2027,
    grado: 3,
    observaciones: "Hermano en 4°",
  });
});

test("Un error al guardar el borrador se muestra en la ventana", async () => {
  backend({
    "POST /api/solicitudes-matricula": reply(409, { message: "Ya existe una solicitud para este año" }),
  });
  const user = userEvent.setup();
  render(<GuardianEnrollment />);
  await user.click(await screen.findByText("Nueva solicitud"));
  const dialog = screen.getByRole("dialog");
  await user.selectOptions(within(dialog).getByLabelText(/Estudiante asociado/), "42");
  await user.selectOptions(within(dialog).getByLabelText(/Año con admisión abierta/), "2027");
  await user.selectOptions(within(dialog).getByLabelText(/Grado solicitado/), "2");
  await user.click(within(dialog).getByText("Guardar borrador"));
  expect((await within(dialog).findByRole("alert")).textContent).toBe(
    "Ya existe una solicitud para este año",
  );
  await user.click(within(dialog).getByText("Cancelar"));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Envía un borrador y luego lo cancela con motivo", async () => {
  let current = request();
  const calls = backend({
    "PUT /api/solicitudes-matricula/:id": ({ body }) => {
      current = request({
        ...body,
        version: current.version + 1,
        fechaEnvio: body.estado === "ENVIADA" ? "2026-11-02T14:00:00Z" : current.fechaEnvio,
      });
      return current;
    },
    "GET /api/solicitudes-matricula/:id": () => current,
  });
  const user = userEvent.setup();
  render(<GuardianEnrollment />);
  await user.click(await screen.findByText("Ver solicitud"));
  const dialog = screen.getByRole("dialog", { name: "Detalle de mi solicitud" });
  await user.click(within(dialog).getByText("Enviar solicitud"));
  expect(within(dialog).getByText("Confirmar envío")).toBeTruthy();
  await user.click(within(dialog).getByText("Confirmar"));
  expect(await within(dialog).findByText(/Solicitud enviada/)).toBeTruthy();
  expect(within(dialog).getByText("Enviada a administración")).toBeTruthy();
  expect(within(dialog).queryByText("Editar observaciones")).toBeNull();
  await user.click(within(dialog).getByText("Cancelar solicitud"));
  await user.type(within(dialog).getByLabelText(/Motivo de cancelación/), "Cambio de colegio");
  await user.click(within(dialog).getByText("Confirmar"));
  expect(await within(dialog).findByText("Cambio guardado.")).toBeTruthy();
  expect(calls.filter((c) => c.method === "PUT").map((c) => c.body)).toEqual([
    { estado: "ENVIADA", observaciones: null, version: 1 },
    { estado: "CANCELADA", observaciones: "Cambio de colegio", version: 2 },
  ]);
  await user.click(within(dialog).getByText("Actualizar estado"));
  await waitFor(() => expect(within(dialog).getAllByText("Cancelada").length).toBeGreaterThan(0));
});

test("Edita las observaciones y reenvía una solicitud observada", async () => {
  const observed = request({
    estado: "OBSERVADA",
    observaciones: "Falta la partida",
    fechaRevision: "2026-11-03T14:00:00Z",
    motivoRechazo: "Revisión previa",
  });
  const calls = backend({
    "GET /api/solicitudes-matricula": page([observed]),
    "PUT /api/solicitudes-matricula/:id": ({ body }) => ({ ...observed, ...body }),
  });
  const user = userEvent.setup();
  render(<GuardianEnrollment />);
  await user.click(await screen.findByText("Ver solicitud"));
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByText("Última revisión")).toBeTruthy();
  expect(within(dialog).getByText(/Motivo de rechazo:/)).toBeTruthy();
  await user.click(within(dialog).getByText("Editar observaciones", { selector: "button" }));
  const notes = within(dialog).getByLabelText(/Observaciones/);
  expect(notes.value).toBe("Falta la partida");
  await user.clear(notes);
  await user.type(notes, "Partida adjunta");
  await user.click(within(dialog).getByText("Confirmar"));
  await within(dialog).findByText("Cambio guardado.");
  await user.click(within(dialog).getByText("Reenviar subsanación"));
  await user.click(within(dialog).getByText("Volver"));
  expect(within(dialog).queryByText("Confirmar envío")).toBeNull();
  expect(calls.find((c) => c.method === "PUT").body).toEqual({
    estado: "OBSERVADA",
    observaciones: "Partida adjunta",
    version: 1,
  });
  await user.click(within(dialog).getByLabelText("Cerrar ventana"));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Informa errores al actualizar y al cambiar la solicitud", async () => {
  backend({
    "PUT /api/solicitudes-matricula/:id": reply(409, { message: "La solicitud cambió" }),
    "GET /api/solicitudes-matricula/:id": reply(500, { message: "No se pudo consultar" }),
  });
  const user = userEvent.setup();
  render(<GuardianEnrollment />);
  await user.click(await screen.findByText("Ver solicitud"));
  const dialog = screen.getByRole("dialog");
  await user.click(within(dialog).getByText("Enviar solicitud"));
  await user.click(within(dialog).getByText("Confirmar"));
  expect((await within(dialog).findByRole("alert")).textContent).toBe("La solicitud cambió");
  await user.click(within(dialog).getByText("Actualizar estado"));
  await waitFor(() =>
    expect(within(dialog).getByRole("alert").textContent).toBe("No se pudo consultar"),
  );
});

test("Sin estudiantes asociados ni convocatoria no permite crear solicitudes", async () => {
  backend({
    "GET /api/familia/estudiantes": [],
    "GET /api/familia/oferta": [],
    "GET /api/solicitudes-matricula": page([]),
  });
  render(<GuardianEnrollment />);
  expect(await screen.findByText(/No tienes estudiantes asociados y habilitados/)).toBeTruthy();
  expect(screen.getByText(/no tiene una convocatoria abierta/)).toBeTruthy();
  expect(screen.getByText("Todavía no tienes solicitudes registradas.")).toBeTruthy();
  expect(screen.getByText("Nueva solicitud").disabled).toBe(true);
});

test("Un error al cargar los estudiantes se informa y Actualizar reintenta", async () => {
  let attempts = 0;
  const calls = backend({
    "GET /api/familia/estudiantes": () =>
      ++attempts === 1 ? reply(500, { message: "Servicio de familia caído" }) : children,
  });
  const user = userEvent.setup();
  render(<GuardianEnrollment />);
  expect((await screen.findByRole("alert")).textContent).toBe("Servicio de familia caído");
  await user.click(screen.getByText("Actualizar"));
  await waitFor(() => expect(screen.queryByRole("alert")).toBeNull());
  expect(calls.filter((c) => c.path === "/api/familia/estudiantes")).toHaveLength(2);
});

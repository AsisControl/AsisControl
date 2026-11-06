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
  expect(within(row).getByText("2°")).toBeTruthy();
  expect(within(row).getByText("Año 2027")).toBeTruthy();
  expect(within(row).getByText("Enviada")).toBeTruthy();
  expect(screen.getByText("+ Nueva solicitud").disabled).toBe(false);
});

test("Guarda un borrador con estudiante, año y grado ofertados", async () => {
  const calls = backend({
    "POST /api/solicitudes-matricula": ({ body }) => request({ alumnoId: body.alumnoId, grado: body.grado }),
  });
  const user = userEvent.setup();
  render(<GuardianEnrollment />);
  await user.click(await screen.findByText("+ Nueva solicitud"));
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
  await user.click(await screen.findByText("+ Nueva solicitud"));
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
  await user.click(await screen.findByText("Ver detalle"));
  const dialog = screen.getByRole("dialog", { name: "Detalle de mi solicitud" });
  await user.click(within(dialog).getByText("Enviar solicitud"));
  expect(within(dialog).getByText("Confirmar envío")).toBeTruthy();
  await user.click(within(dialog).getByText("Confirmar"));
  expect(await within(dialog).findByText(/Solicitud enviada/)).toBeTruthy();
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
  await user.click(await screen.findByText("Ver detalle"));
  const dialog = screen.getByRole("dialog");
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
  await user.click(await screen.findByText("Ver detalle"));
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
  expect(screen.getByText("+ Nueva solicitud").disabled).toBe(true);
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

const event = (id, fecha, accion, campos, overrides = {}) => ({
  id, actor: "admin", actorNombre: "Ana Admin", rol: "ADMINISTRADOR", fecha, accion,
  motivo: null, anterior: null, nuevo: null, campos, ...overrides,
});
const enrollment = (overrides = {}) => ({
  id: 7, codigoMatricula: "MAT-2027-7", solicitudMatriculaId: 100, seccion: "A", grado: 2,
  estado: "ACTIVA", ...overrides,
});
const openDetail = async (user) => {
  render(<GuardianEnrollment />);
  await user.click(await screen.findByText("Ver detalle"));
  return screen.getByRole("dialog", { name: "Detalle de mi solicitud" });
};

test("US-49: sin finalizar aclara que no hay matrícula ni vacante y muestra la cronología", async () => {
  backend({
    "GET /api/matriculas": page([]),
    "GET /api/solicitudes-matricula": page([request({ estado: "ENVIADA" })]),
    "GET /api/solicitudes-matricula/:id/historial": [
      event(1, "2026-11-01T14:00:00Z", "GUARDAR_SOLICITUD", [{ campo: "estado", anterior: null, nuevo: "BORRADOR" }]),
      event(2, "2026-11-02T14:00:00Z", "GUARDAR_SOLICITUD", [{ campo: "estado", anterior: "BORRADOR", nuevo: "ENVIADA" }]),
    ],
  });
  const dialog = await openDetail(userEvent.setup());
  expect(await within(dialog).findByText(/todavía no existe una matrícula ni se ha reservado una vacante/)).toBeTruthy();
  const items = [...(await within(dialog).findAllByRole("listitem"))];
  expect(items.map((item) => item.querySelector("p").textContent)).toEqual([
    "Estado: Borrador",
    "Estado: Borrador → Enviada",
  ]);
  expect(within(dialog).queryByText(/Ana Admin|Administrador/)).toBeNull();
  expect(within(dialog).getByText("Por asignar")).toBeTruthy();
});

test("US-49: con matrícula muestra sección, estado y une ambas cronologías en orden", async () => {
  const calls = backend({
    "GET /api/matriculas": page([enrollment({ id: 8, solicitudMatriculaId: 999, seccion: "Z" }), enrollment()]),
    "GET /api/solicitudes-matricula": page([request({ estado: "MATRICULA_FINALIZADA" })]),
    "GET /api/solicitudes-matricula/:id/historial": [
      event(1, "2026-11-01T14:00:00Z", "GUARDAR_SOLICITUD", [{ campo: "estado", anterior: null, nuevo: "ENVIADA" }]),
      event(3, "2026-11-03T14:00:00Z", "FINALIZAR_SOLICITUD", []),
    ],
    "GET /api/matriculas/:id/historial": [
      event(2, "2026-11-02T14:00:00Z", "CREAR_MATRICULA", [{ campo: "seccion", anterior: null, nuevo: "2027 · 2° A" }]),
    ],
  });
  const dialog = await openDetail(userEvent.setup());
  expect(await within(dialog).findByText("2° A")).toBeTruthy();
  expect(within(dialog).getByText("MAT-2027-7")).toBeTruthy();
  expect(within(dialog).queryByText(/no existe una matrícula/)).toBeNull();
  await within(dialog).findByText("Matrícula creada");
  expect(within(dialog).getAllByRole("listitem").map((item) => item.querySelector("strong").textContent)).toEqual([
    "Solicitud guardada",
    "Matrícula creada",
    "Solicitud finalizada",
  ]);
  expect(calls.some((c) => c.path === "/api/matriculas/7/historial")).toBe(true);
  expect(calls.some((c) => c.path === "/api/matriculas/8/historial")).toBe(false);
});

test("US-49: un historial sin eventos o que falla no impide usar el detalle", async () => {
  backend({ "GET /api/matriculas": page([]), "GET /api/solicitudes-matricula/:id/historial": [] });
  const view = render(<GuardianEnrollment />);
  const user = userEvent.setup();
  await user.click(await screen.findByText("Ver detalle"));
  expect(await screen.findByText("Aún no hay cambios registrados.")).toBeTruthy();
  view.unmount();
  backend({ "GET /api/matriculas": reply(500, { message: "Sin conexión" }) });
  render(<GuardianEnrollment />);
  await user.click(await screen.findByText("Ver detalle"));
  expect(await screen.findByText("No se pudo cargar el historial: Sin conexión")).toBeTruthy();
  expect(screen.getByText("Actualizar estado")).toBeTruthy();
});

test("US-49: con varios estudiantes filtra solo entre los asociados", async () => {
  const calls = backend();
  const user = userEvent.setup();
  render(<GuardianEnrollment />);
  const select = await screen.findByLabelText("Estudiante");
  expect([...select.options].map((option) => option.textContent)).toEqual([
    "Todos mis estudiantes",
    "Lucía Flores",
    "Mateo Flores",
  ]);
  await user.selectOptions(select, "43");
  await waitFor(() =>
    expect(calls.some((c) => c.path === "/api/solicitudes-matricula" && c.query.alumnoId === "43")).toBe(true),
  );
});

test("US-49: con un solo estudiante no muestra el filtro", async () => {
  backend({ "GET /api/familia/estudiantes": [children[0]] });
  render(<GuardianEnrollment />);
  await screen.findByText("Ver detalle");
  expect(screen.queryByLabelText("Estudiante")).toBeNull();
});

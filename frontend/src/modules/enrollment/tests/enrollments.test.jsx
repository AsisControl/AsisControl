import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { usePathname } from "next/navigation";
import { currentUrl, setUrl } from "../../../../tests/support/navigation.js";
import { Enrollments } from "@/modules/enrollment";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

/** Como ModuleOutlet: la pestaña sale de /matricula/{pestaña}. */
function EnrollmentsScreen() {
  return <Enrollments tab={usePathname().split("/")[2]} />;
}

const persona = (nombreCompleto, overrides = {}) => ({
  id: 500,
  nombres: nombreCompleto.split(" ")[0],
  apellidoPaterno: nombreCompleto.split(" ")[1] ?? "",
  apellidoMaterno: "Vega",
  nombreCompleto,
  fechaNacimiento: "2012-05-14",
  tipoDocumento: "DNI",
  numeroDocumento: "70707070",
  sexo: "FEMENINO",
  correo: "lucia@correo.pe",
  telefono: null,
  direccion: null,
  nacionalidadId: 1,
  ...overrides,
});

const lucia = {
  id: 42,
  codigoAlumno: "EST-042",
  activo: true,
  persona: persona("Lucía Flores"),
  apoderados: [
    { id: 1, apoderadoId: 9, nombreCompleto: "Carmen Flores", parentesco: "MADRE", principal: true },
  ],
};
const inactiveStudent = { ...lucia, id: 43, codigoAlumno: "EST-043", activo: false, persona: persona("Mario Díaz") };

const activeYear = {
  id: 7,
  anio: 2026,
  estado: "ACTIVO",
  grados: [
    { id: 1, numero: 1, activo: true },
    { id: 2, numero: 2, activo: false },
  ],
  secciones: [
    { id: 31, nombre: "A", grado: 1, activo: true, vacantes: 5, capacidadMaxima: 35 },
    { id: 32, nombre: "B", grado: 1, activo: true, vacantes: 0, capacidadMaxima: 35 },
    { id: 33, nombre: "C", grado: 1, activo: false, vacantes: 9, capacidadMaxima: 35 },
  ],
};

const request = (overrides = {}) => ({
  id: 100,
  alumnoId: 42,
  codigoAlumno: "EST-042",
  alumno: "Lucía Flores",
  seccionId: 31,
  seccion: "A",
  grado: 1,
  anioAcademico: 2026,
  estado: "BORRADOR",
  version: 3,
  fechaEnvio: null,
  fechaRevision: null,
  observaciones: "Viene de otro colegio",
  motivoRechazo: null,
  ...overrides,
});

const enrollment = (overrides = {}) =>
  request({
    id: 200,
    estado: "ACTIVA",
    codigoMatricula: "MAT-2026-0001",
    fechaMatricula: "2026-03-02T15:00:00Z",
    observaciones: null,
    ...overrides,
  });

const guardians = [
  { id: 9, activo: true, persona: persona("Carmen Flores", { numeroDocumento: "40000009" }) },
  { id: 10, activo: true, persona: persona("Jorge Flores", { numeroDocumento: "40000010" }) },
  { id: 11, activo: false, persona: persona("Inés Ramos", { numeroDocumento: "40000011" }) },
];

function backend(routes = {}) {
  return fakeBackend({
    "GET /api/solicitudes-matricula": page([request()]),
    "GET /api/matriculas": page([enrollment()]),
    "GET /api/solicitudes-matricula/:id/historial": [
      {
        id: 1,
        actor: "admin",
        rol: "ADMINISTRADOR",
        fecha: "2026-03-01T15:00:00Z",
        accion: "CREADA",
        motivo: "Registro inicial",
        anterior: "—",
        nuevo: "BORRADOR",
      },
    ],
    "GET /api/matriculas/:id/historial": [],
    "GET /api/configuracion-academica/anios": page([activeYear, { ...activeYear, id: 6, anio: 2025, estado: "CERRADO" }]),
    "GET /api/configuracion-academica/anios/:anio": activeYear,
    "GET /api/alumnos": page([lucia, inactiveStudent]),
    "GET /api/alumnos/:id": lucia,
    "GET /api/apoderados": page(guardians),
    ...routes,
  });
}

async function openRequest(user, label = "Ver detalle") {
  await user.click(await screen.findByText(label));
  return screen.getByRole("dialog");
}

beforeEach(() => {
  signIn();
  setUrl("/matricula/solicitudes");
});

test("Lista solicitudes y matrículas en pestañas", async () => {
  backend();
  const user = userEvent.setup();
  render(<EnrollmentsScreen />);
  const row = (await screen.findByText("Lucía Flores")).closest("tr");
  expect(within(row).getByText("1° A")).toBeTruthy();
  expect(within(row).getByText("Año 2026")).toBeTruthy();
  expect(within(row).getByText("Borrador").className).toContain("status-neutral");
  expect(within(row).getByText("—")).toBeTruthy();
  await user.click(screen.getByText("Matrículas"));
  expect(currentUrl()).toBe("/matricula/matriculas");
  expect(await screen.findByText("MAT-2026-0001")).toBeTruthy();
  expect(screen.getByText("Activa").className).toContain("status-success");
  expect(screen.queryByText("+ Nueva solicitud")).toBeNull();
});

test("Sin registros muestra el mensaje y un error del backend se informa", async () => {
  backend({
    "GET /api/solicitudes-matricula": page([]),
    "GET /api/matriculas": reply(500, { message: "Matrículas no disponibles" }),
  });
  const user = userEvent.setup();
  render(<EnrollmentsScreen />);
  expect(await screen.findByText("No hay solicitudes registradas.")).toBeTruthy();
  await user.click(screen.getByText("Matrículas"));
  expect((await screen.findByRole("alert")).textContent).toBe("Matrículas no disponibles");
});

test("Crea una solicitud eligiendo estudiante, grado y sección del año activo", async () => {
  const calls = backend({
    "POST /api/solicitudes-matricula": request({ id: 101 }),
  });
  const user = userEvent.setup();
  render(<EnrollmentsScreen />);
  await user.click(await screen.findByText("+ Nueva solicitud"));
  const dialog = screen.getByRole("dialog", { name: "Nueva solicitud de matrícula" });
  expect(within(dialog).getByText("Guardar solicitud").disabled).toBe(true);
  const mario = (await within(dialog).findByText("Mario Díaz")).closest("li");
  expect(within(mario).getByText("Elegir").disabled).toBe(true);
  await user.click(within((await within(dialog).findByText("Lucía Flores")).closest("li")).getByText("Elegir"));
  expect(within(dialog).getByText("Seleccionado")).toBeTruthy();
  expect(within(dialog).getByLabelText(/Año académico activo/).value).toBe("7");
  const grade = within(dialog).getByLabelText(/^Grado/);
  expect([...grade.options].map((o) => o.textContent)).toEqual([
    "Seleccionar grado",
    "1° de secundaria",
  ]);
  await user.selectOptions(grade, "1");
  const sectionSelect = within(dialog).getByLabelText(/Sección solicitada/);
  expect([...sectionSelect.options].map((o) => o.textContent)).toEqual([
    "Seleccionar sección",
    "A · 5 vacantes / 35",
    "B · 0 vacantes / 35",
  ]);
  await user.selectOptions(sectionSelect, "31");
  await user.type(within(dialog).getByLabelText(/Observaciones/), "  Traslado  ");
  await user.click(within(dialog).getByText("Guardar solicitud"));
  expect(await screen.findByText("Solicitud guardada en borrador.")).toBeTruthy();
  expect(calls.find((c) => c.method === "POST").body).toEqual({
    alumnoId: 42,
    seccionId: 31,
    observaciones: "Traslado",
  });
  expect(screen.getByRole("dialog", { name: "Gestionar solicitud" })).toBeTruthy();
});

test("La nueva solicitud busca estudiantes, informa errores y avisa si no hay año activo", async () => {
  const calls = backend({
    "GET /api/configuracion-academica/anios": page([]),
    "POST /api/solicitudes-matricula": reply(409, { message: "Ya existe una solicitud" }),
  });
  const user = userEvent.setup();
  render(<EnrollmentsScreen />);
  await user.click(await screen.findByText("+ Nueva solicitud"));
  const dialog = screen.getByRole("dialog");
  expect(
    await within(dialog).findByText("Configure y active un año académico en Administración."),
  ).toBeTruthy();
  await user.type(within(dialog).getByLabelText(/Buscar por nombre, documento o código/), "lucía");
  await user.click(within(dialog).getByText("Buscar"));
  await waitFor(() =>
    expect(calls.some((c) => c.path === "/api/alumnos" && c.query.search === "lucía")).toBe(true),
  );
  await user.click(within(dialog).getByText("Cancelar"));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Informa el rechazo del backend al guardar la solicitud", async () => {
  backend({
    "POST /api/solicitudes-matricula": reply(409, { message: "Ya existe una solicitud" }),
  });
  const user = userEvent.setup();
  render(<EnrollmentsScreen />);
  await user.click(await screen.findByText("+ Nueva solicitud"));
  const dialog = screen.getByRole("dialog");
  await user.click(within((await within(dialog).findByText("Lucía Flores")).closest("li")).getByText("Elegir"));
  await user.selectOptions(within(dialog).getByLabelText(/^Grado/), "1");
  await user.selectOptions(within(dialog).getByLabelText(/Sección solicitada/), "32");
  await user.click(within(dialog).getByText("Guardar solicitud"));
  expect((await within(dialog).findByRole("alert")).textContent).toBe("Ya existe una solicitud");
});

test("El detalle muestra historial y apoderados, y envía la solicitud", async () => {
  const calls = backend({
    "GET /api/solicitudes-matricula/:id": request({ estado: "ENVIADA" }),
    "PUT /api/solicitudes-matricula/:id": ({ body }) => request({ ...body, version: 4 }),
  });
  const user = userEvent.setup();
  render(<EnrollmentsScreen />);
  const dialog = await openRequest(user);
  expect(await within(dialog).findByText("Registro inicial", { exact: false })).toBeTruthy();
  expect(within(dialog).getByText("Nuevo: BORRADOR")).toBeTruthy();
  expect(await within(dialog).findByText("Madre · Principal")).toBeTruthy();
  expect(within(dialog).getByText(/Viene de otro colegio/)).toBeTruthy();
  await user.click(within(dialog).getByText("Enviar solicitud"));
  expect(within(dialog).getByText("Confirma el cambio de estado de la solicitud.")).toBeTruthy();
  await user.click(within(dialog).getByText("Confirmar"));
  expect(await within(dialog).findByText("Cambio registrado con historial.")).toBeTruthy();
  expect(calls.find((c) => c.method === "PUT").body).toEqual({
    estado: "ENVIADA",
    seccionId: null,
    observaciones: "Viene de otro colegio",
    motivoRechazo: null,
    version: 3,
  });
  await user.click(within(dialog).getByText("Actualizar datos"));
  await waitFor(() => expect(within(dialog).getByText("Enviada")).toBeTruthy());
  expect(within(dialog).getByText("Iniciar revisión")).toBeTruthy();
});

test("Edita la sección y las observaciones de un borrador", async () => {
  const calls = backend({
    "PUT /api/solicitudes-matricula/:id": ({ body }) => request({ ...body, seccion: "B" }),
  });
  const user = userEvent.setup();
  render(<EnrollmentsScreen />);
  const dialog = await openRequest(user);
  await user.click(within(dialog).getByText("Editar sección / observaciones"));
  const sectionSelect = await within(dialog).findByLabelText(/Sección del mismo grado y año/);
  await waitFor(() => expect(sectionSelect.options.length).toBe(3));
  await user.selectOptions(sectionSelect, "32");
  const notes = within(dialog).getByLabelText(/^Observaciones/);
  await user.clear(notes);
  await user.type(notes, "Prefiere la tarde");
  await user.click(within(dialog).getByText("Confirmar"));
  await within(dialog).findByText("Cambio registrado con historial.");
  expect(calls.find((c) => c.method === "PUT").body).toEqual({
    estado: "BORRADOR",
    seccionId: 32,
    observaciones: "Prefiere la tarde",
    motivoRechazo: null,
    version: 3,
  });
});

test("Rechaza una solicitud en revisión con motivo y muestra el error del servidor", async () => {
  const calls = backend({
    "GET /api/solicitudes-matricula": page([request({ estado: "EN_REVISION" })]),
    "PUT /api/solicitudes-matricula/:id": ({ body }) =>
      body.motivoRechazo === "Error"
        ? reply(409, { message: "La solicitud cambió" })
        : request({ estado: "RECHAZADA", motivoRechazo: body.motivoRechazo }),
  });
  const user = userEvent.setup();
  render(<EnrollmentsScreen />);
  const dialog = await openRequest(user);
  await user.click(within(dialog).getByText("Rechazar"));
  await user.type(within(dialog).getByLabelText(/^Motivo/), "Error");
  await user.click(within(dialog).getByText("Confirmar"));
  expect((await within(dialog).findByRole("alert")).textContent).toBe("La solicitud cambió");
  const reason = within(dialog).getByLabelText(/^Motivo/);
  await user.clear(reason);
  await user.type(reason, "Documentos incompletos");
  await user.click(within(dialog).getByText("Confirmar"));
  expect(await within(dialog).findByText(/Motivo de rechazo:/)).toBeTruthy();
  expect(calls.filter((c) => c.method === "PUT").at(-1).body.motivoRechazo).toBe(
    "Documentos incompletos",
  );
  expect(within(dialog).queryByText("Rechazar")).toBeNull();
});

test("Observar pide el detalle a subsanar y Volver cierra el formulario", async () => {
  const calls = backend({
    "GET /api/solicitudes-matricula": page([request({ estado: "EN_REVISION" })]),
    "PUT /api/solicitudes-matricula/:id": ({ body }) => request({ estado: "OBSERVADA", observaciones: body.observaciones }),
  });
  const user = userEvent.setup();
  render(<EnrollmentsScreen />);
  const dialog = await openRequest(user);
  await user.click(within(dialog).getByText("Observar"));
  await user.click(within(dialog).getByText("Volver"));
  expect(within(dialog).queryByLabelText(/Detalle a subsanar/)).toBeNull();
  await user.click(within(dialog).getByText("Observar"));
  await user.type(within(dialog).getByLabelText(/Detalle a subsanar/), "Falta la partida");
  await user.click(within(dialog).getByText("Confirmar"));
  await within(dialog).findByText("Cambio registrado con historial.");
  expect(calls.find((c) => c.method === "PUT").body.observaciones).toBe("Falta la partida");
});

test("Finaliza la matrícula de una solicitud en revisión", async () => {
  const calls = backend({
    "GET /api/solicitudes-matricula": page([request({ estado: "EN_REVISION" })]),
    "POST /api/matriculas": enrollment(),
    "GET /api/solicitudes-matricula/:id": request({ estado: "MATRICULA_FINALIZADA" }),
  });
  const user = userEvent.setup();
  render(<EnrollmentsScreen />);
  const dialog = await openRequest(user);
  await user.click(within(dialog).getByText("Finalizar matrícula", { selector: ".primary-action" }));
  expect(within(dialog).getByText(/Se creará la matrícula y finalizará la solicitud/)).toBeTruthy();
  await user.click(within(dialog).getByText("Confirmar"));
  expect(await within(dialog).findByText(/Matrícula MAT-2026-0001 creada/)).toBeTruthy();
  expect(calls.find((c) => c.method === "POST").body).toEqual({
    solicitudMatriculaId: 100,
    versionSolicitud: 3,
  });
  expect(within(dialog).getByText("Matricula finalizada")).toBeTruthy();
});

test("Retira una matrícula activa con motivo", async () => {
  const calls = backend({
    "PUT /api/matriculas/:id": ({ body }) => enrollment({ estado: body.estado }),
  });
  const user = userEvent.setup();
  render(<EnrollmentsScreen />);
  await user.click(await screen.findByText("Matrículas"));
  const dialog = await openRequest(user);
  expect(await within(dialog).findByText("Sin cambios registrados.")).toBeTruthy();
  expect(within(dialog).getByText("MAT-2026-0001")).toBeTruthy();
  await user.click(within(dialog).getByText("Retirar"));
  await user.type(within(dialog).getByLabelText(/^Motivo/), "Traslado");
  await user.click(within(dialog).getByText("Confirmar"));
  await within(dialog).findByText("Cambio registrado con historial.");
  expect(calls.find((c) => c.method === "PUT")).toMatchObject({
    path: "/api/matriculas/200",
    body: { estado: "RETIRADA", motivo: "Traslado", version: 3 },
  });
  expect(within(dialog).queryByText("Anular matrícula")).toBeNull();
  await user.click(within(dialog).getByLabelText("Cerrar ventana"));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Informa errores al cargar el detalle y al actualizarlo", async () => {
  backend({
    "GET /api/solicitudes-matricula/:id/historial": reply(500, { message: "Historial no disponible" }),
    "GET /api/solicitudes-matricula/:id": reply(404, { message: "Solicitud eliminada" }),
    "GET /api/alumnos/:id": reply(403, null),
  });
  const user = userEvent.setup();
  render(<EnrollmentsScreen />);
  const dialog = await openRequest(user);
  await waitFor(() =>
    expect(within(dialog).getAllByRole("alert").map((a) => a.textContent)).toEqual(
      expect.arrayContaining(["Historial no disponible", "No tienes permiso para realizar esta operación."]),
    ),
  );
  await user.click(within(dialog).getByText("Actualizar datos"));
  await waitFor(() =>
    expect(within(dialog).getAllByRole("alert").map((a) => a.textContent)).toContain("Solicitud eliminada"),
  );
});

test("Asocia un apoderado existente al estudiante", async () => {
  let linked = false;
  const calls = backend({
    "GET /api/alumnos/:id": () =>
      linked
        ? {
            ...lucia,
            apoderados: [
              ...lucia.apoderados,
              { id: 2, apoderadoId: 10, nombreCompleto: "Jorge Flores", parentesco: "PADRE", principal: false },
            ],
          }
        : { ...lucia, apoderados: [] },
    "POST /api/alumnos/:id/apoderados": () => {
      linked = true;
      return reply(201, {});
    },
  });
  const user = userEvent.setup();
  render(<EnrollmentsScreen />);
  const dialog = await openRequest(user);
  expect(await within(dialog).findByText("Sin apoderados asociados.")).toBeTruthy();
  await user.click(within(dialog).getByText("Asociar apoderado"));
  const ines = (await within(dialog).findByText("Inés Ramos")).closest("li");
  expect(within(ines).getByText("Elegir").disabled).toBe(true);
  await user.type(within(dialog).getByLabelText(/Buscar apoderado/), "flores");
  await user.click(within(dialog).getAllByText("Buscar").at(-1));
  await waitFor(() => expect(calls.some((c) => c.query.search === "flores")).toBe(true));
  await user.click(within((await within(dialog).findByText("Jorge Flores")).closest("li")).getByText("Elegir"));
  expect(within(dialog).getByText("Apoderado seleccionado:", { exact: false })).toBeTruthy();
  await user.selectOptions(within(dialog).getByLabelText(/Parentesco/), "PADRE");
  await user.click(within(dialog).getByLabelText(/Apoderado principal/));
  await user.click(within(dialog).getByText("Guardar vínculo"));
  expect(await within(dialog).findByText("Padre")).toBeTruthy();
  expect(calls.find((c) => c.path === "/api/alumnos/42/apoderados").body).toEqual({
    apoderadoId: 10,
    parentesco: "PADRE",
    principal: true,
  });
  expect(within(dialog).getByText("Asociar apoderado")).toBeTruthy();
});

test("Un error al asociar el apoderado se muestra en la sección", async () => {
  backend({
    "POST /api/alumnos/:id/apoderados": reply(409, { message: "Ya está asociado" }),
  });
  const user = userEvent.setup();
  render(<EnrollmentsScreen />);
  const dialog = await openRequest(user);
  await user.click(await within(dialog).findByText("Asociar apoderado"));
  await within(dialog).findByText("Inés Ramos");
  const carmen = within(dialog).getAllByText("Carmen Flores").at(-1).closest("li");
  expect(within(carmen).getByText("Ya asociado")).toBeTruthy();
  await user.click(within((await within(dialog).findByText("Jorge Flores")).closest("li")).getByText("Elegir"));
  await user.click(within(dialog).getByText("Guardar vínculo"));
  expect((await within(dialog).findByRole("alert")).textContent).toBe("Ya está asociado");
  await user.click(within(dialog).getByText("Cerrar asociación"));
  expect(within(dialog).queryByText("Guardar vínculo")).toBeNull();
});

test("La pestaña de fichas registra, consulta y edita estudiantes", async () => {
  const calls = backend({
    "POST /api/alumnos": ({ body }) => ({ ...lucia, id: 50, codigoAlumno: "ESTU-000050", persona: { ...lucia.persona, ...body.persona }, apoderados: [] }),
    "PUT /api/alumnos/:id": ({ body }) => ({ ...lucia, persona: { ...lucia.persona, ...body.persona } }),
  });
  const user = userEvent.setup();
  render(<EnrollmentsScreen />);
  await user.click(screen.getByRole("button", { name: "Fichas de estudiantes" }));
  expect(await screen.findByText("Mario Díaz")).toBeTruthy();
  await user.type(screen.getByLabelText(/Buscar por nombre, código o documento/), "flores");
  await user.click(screen.getByText("Buscar"));
  await waitFor(() => expect(calls.at(-1).query.search).toBe("flores"));
  await user.click(screen.getByText("+ Registrar estudiante"));
  await user.type(screen.getByLabelText(/^Nombres/), "Ana");
  await user.type(screen.getByLabelText(/^Primer apellido/), "Soto");
  await user.type(screen.getByLabelText(/^Segundo apellido/), "Lima");
  await user.selectOptions(screen.getByLabelText(/Tipo de documento/), "DNI");
  await user.type(screen.getByLabelText(/Número de documento/), "71717171");
  await user.type(screen.getByLabelText(/Fecha de nacimiento/), "2013-01-10");
  await user.type(screen.getByLabelText(/Correo de contacto/), "ana@correo.pe");
  await user.click(screen.getByText("Guardar ficha"));
  expect(await screen.findByText(/Ficha guardada/)).toBeTruthy();
  expect(screen.getByText("No tiene apoderados asociados.")).toBeTruthy();
  expect(calls.find((c) => c.method === "POST").body).toMatchObject({
    persona: { nombres: "Ana", numeroDocumento: "71717171", nacionalidadId: null },
  });
  expect(calls.find((c) => c.method === "POST").body).not.toHaveProperty("codigoAlumno");
  await user.click(screen.getByText("Volver a fichas"));
  await user.click((await screen.findAllByText("Ver ficha"))[0]);
  expect(await screen.findByText("Carmen Flores")).toBeTruthy();
  expect(screen.getByText("Principal")).toBeTruthy();
  expect(screen.getAllByText("Sin registrar").length).toBeGreaterThanOrEqual(2);
  await user.click(screen.getByText("Editar ficha"));
  expect(screen.getByLabelText(/Código institucional/).value).toBe("EST-042");
  expect(screen.getByLabelText(/Código institucional/).readOnly).toBe(true);
  await user.click(screen.getByText("Guardar ficha"));
  await waitFor(() => expect(calls.some((c) => c.method === "PUT")).toBe(true));
  expect(calls.find((c) => c.method === "PUT").body.persona.nacionalidadId).toBe(1);
});

test("La pestaña de fichas informa errores al abrir y guardar", async () => {
  backend({
    "GET /api/alumnos": page([]),
    "GET /api/alumnos/:id": reply(404, { message: "Ficha no encontrada" }),
    "POST /api/alumnos": reply(409, { code: "DOCUMENTO_DUPLICADO", message: "Documento repetido" }),
  });
  const user = userEvent.setup();
  render(<EnrollmentsScreen />);
  await user.click(screen.getByRole("button", { name: "Fichas de estudiantes" }));
  expect(await screen.findByText("Todavía no hay fichas de estudiantes registradas.")).toBeTruthy();
  await user.click(screen.getByText("+ Registrar estudiante"));
  await user.type(screen.getByLabelText(/^Nombres/), "Ana");
  await user.type(screen.getByLabelText(/^Primer apellido/), "Soto");
  await user.type(screen.getByLabelText(/^Segundo apellido/), "Lima");
  await user.selectOptions(screen.getByLabelText(/Tipo de documento/), "DNI");
  await user.type(screen.getByLabelText(/Número de documento/), "71717171");
  await user.type(screen.getByLabelText(/Fecha de nacimiento/), "2013-01-10");
  await user.type(screen.getByLabelText(/Correo de contacto/), "ana@correo.pe");
  await user.click(screen.getByText("Guardar ficha"));
  expect((await screen.findByRole("alert")).textContent).toContain("El documento ya está registrado");
  await user.click(screen.getByText("Cancelar"));
  await user.click(screen.getByText("Descartar cambios"));
  expect(screen.getByText("+ Registrar estudiante")).toBeTruthy();
});

test("Abrir una ficha que no existe muestra el error", async () => {
  backend({ "GET /api/alumnos/:id": reply(404, { message: "Ficha no encontrada" }) });
  const user = userEvent.setup();
  render(<EnrollmentsScreen />);
  await user.click(screen.getByRole("button", { name: "Fichas de estudiantes" }));
  await user.click((await screen.findAllByText("Ver ficha"))[0]);
  expect((await screen.findByRole("alert")).textContent).toBe("La ficha ya no está disponible. Actualiza el listado.");
});

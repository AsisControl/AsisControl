import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { usePathname } from "next/navigation";
import { Enrollments, GuardianEnrollmentPortal } from "@/modules/enrollment";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { currentUrl, setUrl } from "../../../../tests/support/navigation.js";
import { paymentPlan } from "./payment-plan-fixture";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const record = (overrides = {}) => ({
  id: 19, idAlumno: 42, alumno: "Lucía Flores", codigoAlumno: "EST-042",
  idMatriculaAnterior: 68, anioAnterior: 2026, gradoAnterior: 1,
  anioDestino: 2027, gradoDestino: 2, idSeccion: 30, seccion: "B",
  estado: "EN_REVISION", codigoMotivo: null, motivo: null, idMatricula: null,
  fechaCreacion: "2026-11-16T14:05:00Z", fechaDecision: null, version: 3,
  ...overrides,
});

const option = {
  idAlumno: 42, alumno: "Lucía Flores", codigoAlumno: "EST-042",
  idMatriculaAnterior: 68, anioAnterior: 2026, gradoAnterior: 1,
  anioDestino: 2027, gradoDestino: 2, promocion: "PROMOVIDO",
  secciones: [{ id: 30, nombre: "B", vacantes: 4 }, { id: 31, nombre: "C", vacantes: 0 }],
};

const promotion = {
  idMatriculaAnterior: 68, idAlumno: 42, alumno: "Lucía Flores", codigoAlumno: "EST-042",
  anioAnterior: 2026, gradoAnterior: 1, promocion: null, motivo: null, version: 5,
};

function backend(routes = {}) {
  return fakeBackend({
    "GET /api/renovaciones": page([record()]),
    "GET /api/renovaciones/opciones": [option],
    "GET /api/renovaciones/19": record(),
    "GET /api/renovaciones/19/plan": paymentPlan(),
    "GET /api/renovaciones/promociones": page([promotion]),
    "GET /api/familia/estudiantes": [],
    "GET /api/familia/oferta": [],
    "GET /api/solicitudes-matricula": page([]),
    ...routes,
  });
}

function FamilyScreen({ canWrite = true }) {
  const [, , tab] = usePathname().split("/");
  return <GuardianEnrollmentPortal tab={tab} canWrite={canWrite} />;
}

function AdminScreen({ canWrite = true }) {
  const [, , tab, view] = usePathname().split("/");
  return <Enrollments tab={tab} view={view} canManageRenewals={canWrite} />;
}

beforeEach(() => {
  signIn({ permissions: ["MATRICULA_LEER", "MATRICULA_ESCRIBIR"] });
  setUrl("/matricula/renovaciones");
});

test("Separa ingreso nuevo y renovación e inicia el trámite como pendiente de revisión", async () => {
  signIn({ role: "APODERADO", permissions: ["MATRICULA_LEER", "SOLICITUDES_ESCRIBIR"] });
  setUrl("/matricula");
  const calls = backend({ "POST /api/renovaciones": reply(201, record()) });
  const user = userEvent.setup();
  render(<FamilyScreen />);
  expect(screen.getByRole("heading", { name: "Solicitudes de mis estudiantes" })).toBeTruthy();
  expect(calls.some((call) => call.path === "/api/renovaciones/opciones")).toBe(false);
  await user.click(screen.getByRole("button", { name: "Renovación anual" }));
  expect(currentUrl()).toBe("/matricula/renovaciones");
  await user.click(screen.getByRole("button", { name: "Solicitar renovación" }));
  const request = screen.getByRole("dialog", { name: "Solicitar renovación anual" });
  await user.selectOptions(await within(request).findByLabelText("Estudiante"), "68");
  expect(within(request).getByText("Grado aprobado")).toBeTruthy();
  expect(within(request).getByRole("option", { name: "C · Sin vacantes" })).toBeTruthy();
  await user.selectOptions(within(request).getByLabelText("Sección solicitada"), "30");
  await user.click(within(request).getByRole("button", { name: "Enviar solicitud" }));
  const detail = await screen.findByRole("dialog", { name: "Detalle de la renovación" });
  expect(within(detail).getByText("En revisión")).toBeTruthy();
  expect(within(detail).queryByRole("button", { name: "Revisar y aprobar" })).toBeNull();
  expect(calls.find((call) => call.method === "POST")).toMatchObject({
    path: "/api/renovaciones", body: { idMatriculaAnterior: 68, idSeccion: 30 },
  });
  await user.click(within(detail).getByRole("button", { name: "Cerrar", exact: true }));
  await user.click(screen.getByRole("button", { name: "Ingreso nuevo" }));
  expect(currentUrl()).toBe("/matricula/solicitudes");
});

test("Una familia puede consultar el motivo de un rechazo y filtrar los trámites", async () => {
  signIn({ role: "APODERADO", permissions: ["MATRICULA_LEER"] });
  const rejected = record({ estado: "RECHAZADA", codigoMotivo: "DEUDA_VENCIDA", motivo: "El estudiante tiene pagos vencidos.", fechaDecision: "2026-11-17T12:00:00Z" });
  const calls = backend({ "GET /api/renovaciones": page([rejected]), "GET /api/renovaciones/19": rejected });
  const user = userEvent.setup();
  render(<FamilyScreen canWrite={false} />);
  expect(screen.queryByRole("button", { name: "Solicitar renovación" })).toBeNull();
  await user.selectOptions(screen.getByLabelText("Estado del trámite"), "RECHAZADA");
  await waitFor(() => expect(calls.some((call) => call.query.estado === "RECHAZADA")).toBe(true));
  await user.click(await screen.findByRole("button", { name: "Ver detalle" }));
  const detail = screen.getByRole("dialog", { name: "Detalle de la renovación" });
  expect(within(detail).getByText("El estudiante tiene pagos vencidos.")).toBeTruthy();
  expect(within(detail).getByText("Fecha de decisión")).toBeTruthy();
});

test("El administrador revisa las pensiones y aprueba con la firma del plan", async () => {
  let current = record();
  const calls = backend({
    "GET /api/renovaciones": () => page([current]),
    "GET /api/renovaciones/19": () => current,
    "POST /api/renovaciones/19/aprobar": () => (current = record({ estado: "APROBADA", idMatricula: 140, version: 4 })),
  });
  const user = userEvent.setup();
  render(<AdminScreen />);
  await user.click(await screen.findByRole("button", { name: "Revisar trámite" }));
  const detail = screen.getByRole("dialog", { name: "Detalle de la renovación" });
  await user.click(await within(detail).findByRole("button", { name: "Revisar y aprobar" }));
  expect(await within(detail).findByText(/Total de las diez pensiones/)).toBeTruthy();
  expect(calls.some((call) => call.method === "POST")).toBe(false);
  await user.click(within(detail).getByRole("button", { name: "Confirmar revisión" }));
  expect(await within(detail).findByText(/Renovación aprobada/)).toBeTruthy();
  expect(within(detail).getByText("Aprobada")).toBeTruthy();
  expect(calls.find((call) => call.method === "POST")).toMatchObject({
    path: "/api/renovaciones/19/aprobar", body: { version: 3, firmaPlan: "plan-v2" },
  });
});

test.each([
  ["GRADO_NO_APROBADO", "El estudiante no aprobó el grado anterior."],
  ["DEUDA_VENCIDA", "El estudiante tiene pagos vencidos."],
  ["SECCION_SIN_VACANTES", "La sección solicitada no tiene vacantes."],
])("La revisión muestra un rechazo persistido por %s", async (codigoMotivo, motivo) => {
  let current = record();
  backend({
    "GET /api/renovaciones": () => page([current]),
    "GET /api/renovaciones/19": () => current,
    "POST /api/renovaciones/19/aprobar": () => (current = record({ estado: "RECHAZADA", codigoMotivo, motivo, version: 4 })),
  });
  const user = userEvent.setup();
  render(<AdminScreen />);
  await user.click(await screen.findByRole("button", { name: "Revisar trámite" }));
  const detail = screen.getByRole("dialog", { name: "Detalle de la renovación" });
  await user.click(await within(detail).findByRole("button", { name: "Revisar y aprobar" }));
  await within(detail).findByRole("table", { name: "Pensiones que se asignarán" });
  await user.click(within(detail).getByRole("button", { name: "Confirmar revisión" }));
  expect(await within(detail).findByText(motivo)).toBeTruthy();
  expect(within(detail).getByText("Rechazada")).toBeTruthy();
  expect(within(detail).queryByRole("button", { name: "Revisar y aprobar" })).toBeNull();
});

test("Al cambiar la tarifa exige una segunda revisión antes de volver a aprobar", async () => {
  let planRequests = 0;
  let decisions = 0;
  const calls = backend({
    "GET /api/renovaciones/19/plan": () => paymentPlan({ firmaPlan: ++planRequests === 1 ? "plan-v2" : "plan-v3" }),
    "POST /api/renovaciones/19/aprobar": () => ++decisions === 1
      ? reply(409, { code: "PLAN_ACTUALIZADO", message: "Revisa la nueva tarifa antes de aprobar." })
      : record({ estado: "APROBADA", idMatricula: 140 }),
  });
  const user = userEvent.setup();
  render(<AdminScreen />);
  await user.click(await screen.findByRole("button", { name: "Revisar trámite" }));
  const detail = screen.getByRole("dialog");
  await user.click(await within(detail).findByRole("button", { name: "Revisar y aprobar" }));
  await within(detail).findByRole("table");
  await user.click(within(detail).getByRole("button", { name: "Confirmar revisión" }));
  expect(await within(detail).findByRole("alert")).toHaveProperty("textContent", "Revisa la nueva tarifa antes de aprobar.");
  await waitFor(() => expect(within(detail).getByRole("button", { name: "Confirmar revisión" }).disabled).toBe(false));
  expect(decisions).toBe(1);
  await user.click(within(detail).getByRole("button", { name: "Confirmar revisión" }));
  await within(detail).findByText(/Renovación aprobada/);
  expect(calls.filter((call) => call.method === "POST").map((call) => call.body.firmaPlan)).toEqual(["plan-v2", "plan-v3"]);
});

test("Rechazar de forma manual exige motivo y conserva el resultado en el detalle", async () => {
  const calls = backend({
    "POST /api/renovaciones/19/rechazar": () => record({ estado: "RECHAZADA", motivo: "Solicitud ingresada por error", version: 4 }),
  });
  const user = userEvent.setup();
  render(<AdminScreen />);
  await user.click(await screen.findByRole("button", { name: "Revisar trámite" }));
  const detail = screen.getByRole("dialog");
  await user.click(await within(detail).findByRole("button", { name: "Rechazar", exact: true }));
  await user.type(within(detail).getByLabelText("Motivo del rechazo"), "   ");
  await user.click(within(detail).getByRole("button", { name: "Confirmar rechazo" }));
  expect(await within(detail).findByRole("alert")).toHaveProperty("textContent", "Indica por qué se rechaza la renovación.");
  const reason = within(detail).getByLabelText("Motivo del rechazo");
  await user.clear(reason);
  await user.type(reason, "Solicitud ingresada por error");
  await user.click(within(detail).getByRole("button", { name: "Confirmar rechazo" }));
  expect(await within(detail).findByText("Solicitud ingresada por error")).toBeTruthy();
  expect(calls.find((call) => call.method === "POST").body).toEqual({ version: 3, motivo: "Solicitud ingresada por error" });
});

test("El administrador registra el resultado oficial con sustento y versión", async () => {
  let current = promotion;
  const calls = backend({
    "GET /api/renovaciones/promociones": () => page([current]),
    "PUT /api/renovaciones/promociones/68": ({ body }) => (current = { ...promotion, promocion: body.resultado, motivo: body.motivo, version: 6 }),
  });
  const user = userEvent.setup();
  render(<AdminScreen />);
  await user.click(screen.getByRole("button", { name: "Resultados de promoción" }));
  expect(currentUrl()).toBe("/matricula/renovaciones/promociones");
  await user.type(screen.getByLabelText("Estudiante"), "Lucía");
  await user.click(screen.getByRole("button", { name: "Buscar" }));
  await waitFor(() => expect(calls.some((call) => call.query.busqueda === "Lucía")).toBe(true));
  await user.click(await screen.findByRole("button", { name: "Registrar resultado" }));
  const dialog = screen.getByRole("dialog");
  await user.selectOptions(within(dialog).getByLabelText("Resultado del acta"), "PROMOVIDO");
  await user.type(within(dialog).getByLabelText("Acta o sustento"), "Acta anual 2026");
  await user.click(within(dialog).getByRole("button", { name: "Guardar resultado" }));
  expect(await screen.findByText("Resultado de promoción guardado.")).toBeTruthy();
  expect(await screen.findByText("Grado aprobado")).toBeTruthy();
  expect(calls.find((call) => call.method === "PUT")).toMatchObject({
    path: "/api/renovaciones/promociones/68", body: { resultado: "PROMOVIDO", motivo: "Acta anual 2026", version: 5 },
  });
});

test("El apoderado recupera una consulta fallida y ve cuándo puede pedir la renovación", async () => {
  signIn({ role: "APODERADO", permissions: ["MATRICULA_LEER", "SOLICITUDES_ESCRIBIR"] });
  let attempts = 0;
  const calls = backend({
    "GET /api/renovaciones/opciones": () => ++attempts === 1
      ? reply(503, { message: "No se pudo consultar la convocatoria." })
      : [],
  });
  const user = userEvent.setup();
  render(<FamilyScreen />);

  await user.click(screen.getByRole("button", { name: "Solicitar renovación" }));
  const dialog = screen.getByRole("dialog");
  expect(await within(dialog).findByRole("alert")).toHaveProperty("textContent", "No se pudo consultar la convocatoria.");
  await user.click(within(dialog).getByRole("button", { name: "Volver a consultar" }));
  expect(await within(dialog).findByText(/No hay opciones de renovación/)).toBeTruthy();
  expect(within(dialog).queryByRole("button", { name: "Enviar solicitud" })).toBeNull();
  expect(calls.filter((call) => call.path === "/api/renovaciones/opciones")).toHaveLength(2);
});

test("Administración recupera el trámite y conserva la revisión si todavía falta la tarifa", async () => {
  let details = 0;
  let plans = 0;
  const calls = backend({
    "GET /api/renovaciones/19": () => ++details === 1
      ? reply(503, { message: "No se pudo consultar el trámite." })
      : record(),
    "GET /api/renovaciones/19/plan": () => ++plans === 1
      ? reply(503, { message: "No se pudo consultar el plan." })
      : paymentPlan({ puedeGenerar: false, firmaPlan: null, items: [], mensaje: "" }),
    "POST /api/renovaciones/19/aprobar": reply(422, {
      code: "REVISION_PLAN_REQUERIDA", message: "Revisa el plan de pensiones antes de aprobar.",
    }),
  });
  const user = userEvent.setup();
  render(<AdminScreen />);

  await user.click(await screen.findByRole("button", { name: "Revisar trámite" }));
  const dialog = screen.getByRole("dialog");
  expect(await within(dialog).findByRole("alert")).toHaveProperty("textContent", "No se pudo consultar el trámite.");
  expect(within(dialog).queryByRole("button", { name: "Revisar y aprobar" })).toBeNull();
  await user.click(within(dialog).getByRole("button", { name: "Volver a consultar" }));
  await user.click(await within(dialog).findByRole("button", { name: "Revisar y aprobar" }));
  expect(await within(dialog).findByRole("alert")).toHaveProperty("textContent", "No se pudo consultar el plan.");
  expect(within(dialog).getByRole("button", { name: "Confirmar revisión" })).toHaveProperty("disabled", true);
  await user.click(within(dialog).getByRole("button", { name: "Volver a consultar el plan" }));
  await within(dialog).findByText(/La tarifa del nuevo año aún no está disponible/);
  await user.click(within(dialog).getByRole("button", { name: "Confirmar revisión" }));
  expect(await within(dialog).findByRole("alert")).toHaveProperty("textContent", "Revisa el plan de pensiones antes de aprobar.");
  expect(within(dialog).getByText("En revisión")).toBeTruthy();
  expect(calls.find((call) => call.method === "POST").body).toEqual({ version: 3 });
});

import { beforeEach, expect, test } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { reply, signIn } from "../../../../tests/support/backend.js";
import { AcademicConfiguration } from "@/modules/academic-config";
import { academicBackend, period, year } from "./fixtures.js";

const BASE = "/api/configuracion-academica";
const draft = year({
  id: 2027,
  anio: 2027,
  fechaInicio: "2027-03-01",
  fechaFin: "2027-12-20",
  estado: "BORRADOR",
  periodos: [period({ fechaInicio: "2027-03-01", fechaFin: "2027-05-31", estado: "PLANIFICADO" })],
  grados: [],
  secciones: [],
});
const closed = year({ id: 2025, anio: 2025, estado: "CERRADO", periodos: [] });

const heading = (anio) => screen.findByRole("heading", { name: `Año ${anio}` });

beforeEach(() => {
  signIn();
});

test("Abre el año activo por defecto y muestra sus períodos", async () => {
  academicBackend([closed, year(), draft]);
  render(<AcademicConfiguration />);
  await heading(2026);
  expect(screen.getByText("Año activo")).toBeTruthy();
  expect(screen.getByText(/Admisión del apoderado:/).textContent).toMatch(/Cerrada/);
  expect(screen.getByText("1. Período I")).toBeTruthy();
  expect(screen.getAllByText("01 Mar 2026")).toHaveLength(2);
  expect(screen.getByRole("button", { name: "Activar" })).toBeTruthy();
  expect(screen.getAllByText(/^Próximamente/).length).toBeGreaterThan(0);
  expect(screen.queryByText(/backend/i)).toBeNull();
  expect(screen.getByText("+ Nuevo período").disabled).toBe(true);
  const notice = screen.getByRole("note");
  expect(notice.textContent).toMatch(/No se pueden agregar períodos/);
  expect(notice.textContent).toMatch(/el año 2026 ya está activo/);
  const picker = screen.getByLabelText("Año académico");
  expect([...picker.options].map((o) => o.textContent)).toEqual([
    "2027 · Borrador",
    "2026 · Activo",
    "2025 · Cerrado",
  ]);
});

test("Sin años invita a crear el primero", async () => {
  academicBackend([]);
  render(<AcademicConfiguration />);
  expect(
    await screen.findByText(/Usa «Crear año» para empezar/),
  ).toBeTruthy();
});

test("Sin año activo abre el más reciente y el año cerrado es de solo lectura", async () => {
  academicBackend([closed]);
  render(<AcademicConfiguration />);
  await heading(2025);
  expect(screen.getByText("Año cerrado: consulta histórica disponible.")).toBeTruthy();
  expect(screen.queryByText("Cerrar año")).toBeNull();
  expect(screen.queryByText("+ Nueva sección")).toBeNull();
  expect(
    screen.getByText("Sin períodos configurados. Sus fechas y cantidad se definen aquí."),
  ).toBeTruthy();
});

test("Crea un año académico y lo selecciona", async () => {
  const created = year({ id: 2028, anio: 2028, estado: "BORRADOR", periodos: [] });
  const { calls, store } = academicBackend([year()], {
    [`POST ${BASE}/anios`]: () => {
      store[2028] = created;
      return created;
    },
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await heading(2026);
  await user.click(screen.getByText("Crear año"));
  const dialog = screen.getByRole("dialog", { name: "Crear año académico" });
  await user.type(within(dialog).getByLabelText(/^Año/), "2028");
  await user.type(within(dialog).getByLabelText(/Fecha inicial/), "2028-03-01");
  await user.type(within(dialog).getByLabelText(/Fecha final/), "2028-12-20");
  await user.click(within(dialog).getByText("Confirmar"));
  await heading(2028);
  expect(screen.getByText("Configuración guardada.")).toBeTruthy();
  expect(calls.find((c) => c.method === "POST").body).toEqual({
    anio: 2028,
    fechaInicio: "2028-03-01",
    fechaFin: "2028-12-20",
  });
});

test("Un error al crear el año se muestra dentro de la ventana", async () => {
  academicBackend([year()], {
    [`POST ${BASE}/anios`]: reply(409, { message: "El año ya existe" }),
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await heading(2026);
  await user.click(screen.getByText("Crear año"));
  const dialog = screen.getByRole("dialog");
  // 2027 no está en la lista: la validación local no lo detiene y responde el servidor.
  await user.type(within(dialog).getByLabelText(/^Año/), "2027");
  await user.type(within(dialog).getByLabelText(/Fecha inicial/), "2027-03-01");
  await user.type(within(dialog).getByLabelText(/Fecha final/), "2027-12-20");
  await user.click(within(dialog).getByText("Confirmar"));
  expect((await within(dialog).findByRole("alert")).textContent).toBe("El año ya existe");
  await user.click(within(dialog).getByText("Cancelar"));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Abre la admisión del año activo con motivo y versión", async () => {
  const { calls, store } = academicBackend([year()], {
    [`PUT ${BASE}/anios/:anio/admision`]: () => {
      store[2026] = year({ admisionAbierta: true, version: 5 });
      return store[2026];
    },
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await heading(2026);
  await user.click(screen.getByText("Abrir admisión"));
  const dialog = screen.getByRole("dialog", { name: "Recepción de solicitudes del apoderado" });
  expect(within(dialog).getByText(/podrán solicitar los grados habilitados/)).toBeTruthy();
  await user.type(within(dialog).getByLabelText(/Motivo/), "Inicio de admisión");
  await user.click(within(dialog).getByText("Confirmar"));
  expect(await screen.findByText("Cerrar admisión")).toBeTruthy();
  expect(calls.find((c) => c.method === "PUT").body).toEqual({
    abierta: true,
    version: 4,
    motivo: "Inicio de admisión",
  });
  await user.click(screen.getByText("Cerrar admisión"));
  expect(screen.getByText(/Dejarán de aceptarse solicitudes nuevas/)).toBeTruthy();
});

test("Cierra el año activo y activa un año en borrador", async () => {
  const { calls, store } = academicBackend([year(), draft], {
    [`PUT ${BASE}/anios/:anio/estado`]: ({ path, body }) => {
      const anio = path.split("/")[4];
      store[anio] = { ...store[anio], estado: body.estado };
      return store[anio];
    },
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await heading(2026);
  await user.click(screen.getByText("Cerrar año"));
  let dialog = screen.getByRole("dialog", { name: "Cambiar estado del año" });
  expect(within(dialog).getByText("Cerrado")).toBeTruthy();
  await user.type(within(dialog).getByLabelText(/Motivo/), "Fin del año");
  await user.click(within(dialog).getByText("Confirmar"));
  expect(await screen.findByText("Año cerrado: consulta histórica disponible.")).toBeTruthy();
  await user.selectOptions(screen.getByLabelText("Año académico"), "2027");
  await heading(2027);
  await user.click(screen.getByText("Activar año"));
  dialog = screen.getByRole("dialog");
  await user.type(within(dialog).getByLabelText(/Motivo/), "Inicio");
  await user.click(within(dialog).getByText("Confirmar"));
  await waitFor(() => expect(store[2027].estado).toBe("ACTIVO"));
  expect(calls.filter((c) => c.method === "PUT").map((c) => c.body.estado)).toEqual([
    "CERRADO",
    "ACTIVO",
  ]);
});

test("Actualizar vuelve a consultar el año e informa si falla", async () => {
  let fail = false;
  const { calls } = academicBackend([year()], {
    [`GET ${BASE}/anios/:anio`]: () => (fail ? reply(500, { message: "Servidor caído" }) : year()),
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await heading(2026);
  const before = calls.length;
  await user.click(screen.getByText("Actualizar"));
  await waitFor(() => expect(calls.length).toBeGreaterThan(before));
  fail = true;
  await user.click(screen.getByText("Actualizar"));
  expect((await screen.findByRole("alert")).textContent).toBe("Servidor caído");
});

test("Activa un período con motivo y muestra el error si el backend lo rechaza", async () => {
  const { calls, store } = academicBackend([year()], {
    [`PUT ${BASE}/periodos/:id/estado`]: ({ body }) => {
      if (body.motivo === "Rechazar") return reply(409, { message: "Ya hay un período activo" });
      store[2026] = year({ periodos: [period({ id: 12, nombre: "Período II", estado: "ACTIVO" })] });
      return store[2026];
    },
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await heading(2026);
  await user.click(screen.getByRole("button", { name: "Activar" }));
  const dialog = screen.getByRole("dialog", { name: "Cambiar estado del período" });
  await user.type(within(dialog).getByLabelText(/Motivo/), "Rechazar");
  await user.click(within(dialog).getByText("Confirmar"));
  expect((await within(dialog).findByRole("alert")).textContent).toBe("Ya hay un período activo");
  const reason = within(dialog).getByLabelText(/Motivo/);
  await user.clear(reason);
  await user.type(reason, "Inicio del período");
  await user.click(within(dialog).getByText("Confirmar"));
  expect(await screen.findByText("Configuración guardada.")).toBeTruthy();
  expect(calls.filter((c) => c.method === "PUT").at(-1)).toMatchObject({
    path: `${BASE}/periodos/12/estado`,
    body: { estado: "ACTIVO", version: 1, motivo: "Inicio del período" },
  });
  await user.click(screen.getByRole("button", { name: "Cerrar" }));
  await user.click(within(screen.getByRole("dialog")).getByText("Cancelar"));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Crea un período en un año en borrador validando nombre y fechas", async () => {
  const { calls, store } = academicBackend([draft], {
    [`POST ${BASE}/anios/:anio/periodos`]: ({ body }) => {
      store[2027] = { ...draft, periodos: [...draft.periodos, { ...period(), ...body, id: 99 }] };
      return store[2027];
    },
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await heading(2027);
  await user.click(screen.getByText("+ Nuevo período"));
  const dialog = screen.getByRole("dialog", { name: "Nuevo período" });
  await user.click(within(dialog).getByText("Crear período"));
  expect(within(dialog).getAllByRole("alert")).toHaveLength(3);
  await user.type(within(dialog).getByLabelText(/Nombre del período/), " período i ");
  await user.type(within(dialog).getByLabelText(/Fecha de inicio/), "2027-05-01");
  await user.type(within(dialog).getByLabelText(/Fecha de fin/), "2027-06-30");
  await user.click(within(dialog).getByText("Crear período"));
  expect(within(dialog).getAllByRole("alert")).toHaveLength(2);
  const name = within(dialog).getByLabelText(/Nombre del período/);
  await user.clear(name);
  await user.type(name, " Período II ");
  const start = within(dialog).getByLabelText(/Fecha de inicio/);
  await user.clear(start);
  await user.type(start, "2027-06-01");
  await user.click(within(dialog).getByText("Crear período"));
  expect(await screen.findByText("Período creado.")).toBeTruthy();
  expect(calls.find((c) => c.method === "POST").body).toEqual({
    nombre: "Período II",
    orden: 2,
    fechaInicio: "2027-06-01",
    fechaFin: "2027-06-30",
  });
});

test("Un error al crear el período permite corregir y reintentar", async () => {
  academicBackend([draft], {
    [`POST ${BASE}/anios/:anio/periodos`]: reply(400, { message: "Fechas fuera del año" }),
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await heading(2027);
  await user.click(screen.getByText("+ Nuevo período"));
  const dialog = screen.getByRole("dialog");
  await user.type(within(dialog).getByLabelText(/Nombre del período/), "Período II");
  await user.type(within(dialog).getByLabelText(/Fecha de inicio/), "2027-06-01");
  await user.type(within(dialog).getByLabelText(/Fecha de fin/), "2027-07-01");
  await user.click(within(dialog).getByText("Crear período"));
  expect((await within(dialog).findByRole("alert")).textContent).toBe("Fechas fuera del año");
  expect(within(dialog).getByText("Crear período").disabled).toBe(false);
  await user.click(within(dialog).getByText("Cancelar"));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Informa si no carga la lista de años o el detalle del año", async () => {
  academicBackend([year()], {
    [`GET ${BASE}/anios/:anio`]: reply(403, null),
  });
  render(<AcademicConfiguration />);
  expect((await screen.findByRole("alert")).textContent).toBe(
    "No tienes permiso para realizar esta operación.",
  );
});

test("Crear año: la fecha final anterior se marca en su campo sin llamar al servidor", async () => {
  const { calls } = academicBackend([year()]);
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await heading(2026);
  await user.click(screen.getByText("Crear año"));
  const dialog = screen.getByRole("dialog");
  await user.type(within(dialog).getByLabelText(/^Año/), "2027");
  await user.type(within(dialog).getByLabelText(/Fecha inicial/), "2027-03-01");
  await user.type(within(dialog).getByLabelText(/Fecha final/), "2027-02-01");
  const before = calls.length;
  await user.click(within(dialog).getByText("Confirmar"));
  const end = within(dialog).getByLabelText(/Fecha final/);
  expect(end.getAttribute("aria-invalid")).toBe("true");
  expect(end.closest("label").textContent).toMatch(/no puede ser anterior al inicio/);
  expect(calls.slice(before).some((c) => c.method === "POST")).toBe(false);
});

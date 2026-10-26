import { beforeEach, expect, test } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { reply, signIn } from "../../../../tests/support/backend.js";
import { AcademicConfiguration } from "@/modules/academic-config";
import { academicBackend, grade, year } from "./fixtures.js";

const BASE = "/api/configuracion-academica";

const ready = () => screen.findByRole("heading", { name: "Año 2026" });
const row = (text) => screen.getByText(text).closest("tr");
/** Fila de un grado dentro de la tabla plegable de grados. */
const gradeRow = (numero) =>
  within(screen.getByRole("button", { name: /Grados habilitados/ }).closest("section"))
    .getByText(`${numero}° Grado`, { selector: "td" })
    .closest("tr");

beforeEach(() => {
  signIn();
});

test("Secciones: muestra ocupación, vacantes y sobrecupo, y filtra por grado", async () => {
  academicBackend([year()]);
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await ready();
  const first = row("Sección B");
  expect(within(first).getByText(/Sobre capacidad/)).toBeTruthy();
  expect(within(first).getByText("103%")).toBeTruthy();
  expect(within(screen.getByLabelText("Editar 1° Grado, Sección A").closest("tr")).getByText("5")).toBeTruthy();
  expect(screen.getByText("Inhabilitada")).toBeTruthy();
  await user.selectOptions(screen.getByLabelText("Filtrar por grado"), "2");
  expect(screen.queryByText("Sección B")).toBeNull();
  expect(screen.getByRole("button", { name: "Habilitar" })).toBeTruthy();
});

test("Secciones: sin grados activos no se pueden crear y lo explica", async () => {
  academicBackend([
    year({ grados: [grade({ activo: false })], secciones: [] }),
  ]);
  render(<AcademicConfiguration />);
  await ready();
  expect(screen.getByText("+ Nueva sección").disabled).toBe(true);
  expect(
    screen.getByText(
      "El año no tiene grados habilitados. Habilita uno en «Grados habilitados» para crear secciones.",
    ),
  ).toBeTruthy();
});

test("Secciones: crea una sección con la capacidad sugerida del grado", async () => {
  const { calls } = academicBackend([year()], {
    "POST /api/secciones": reply(201, { id: 40 }),
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await ready();
  await user.click(screen.getByText("+ Nueva sección"));
  const dialog = screen.getByRole("dialog", { name: "Nueva sección" });
  const capacity = within(dialog).getByLabelText(/Capacidad máxima/);
  expect(capacity.value).toBe("35");
  await user.selectOptions(within(dialog).getByLabelText(/^Grado/), "2");
  expect(capacity.value).toBe("30");
  await user.click(within(dialog).getByText("Guardar sección"));
  expect(within(dialog).getAllByRole("alert").length).toBeGreaterThan(0);
  await user.type(within(dialog).getByLabelText(/^Sección/), " c ");
  await user.type(within(dialog).getByLabelText(/^Motivo/), "Más alumnos");
  await user.click(within(dialog).getByText("Guardar sección"));
  expect(await screen.findByText("Sección creada correctamente.")).toBeTruthy();
  expect(calls.find((c) => c.method === "POST").body).toEqual({
    nombre: "c",
    grado: 2,
    anioAcademico: 2026,
    capacidadMaxima: 30,
    docenteTutorId: null,
    motivo: "Más alumnos",
  });
});

test("Secciones: editar conserva el tutor y no baja de los matriculados", async () => {
  const { calls } = academicBackend([year()], {
    "GET /api/secciones/:id": { docenteTutorId: 77 },
    "PUT /api/secciones/:id": reply(200, {}),
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await ready();
  await user.click(screen.getByLabelText("Editar 1° Grado, Sección A"));
  const dialog = screen.getByRole("dialog", { name: "Editar sección" });
  expect(within(dialog).getByText(/Alumnos matriculados: 30/)).toBeTruthy();
  const capacity = within(dialog).getByLabelText(/Capacidad máxima/);
  await user.clear(capacity);
  await user.type(capacity, "20");
  await user.type(within(dialog).getByLabelText(/^Motivo/), "Aula más pequeña");
  await user.click(within(dialog).getByText("Guardar sección"));
  expect(within(dialog).getByText(/30 alumnos matriculados/)).toBeTruthy();
  await user.clear(capacity);
  await user.type(capacity, "40");
  await user.click(within(dialog).getByText("Guardar sección"));
  expect(await screen.findByText("Sección actualizada.")).toBeTruthy();
  expect(calls.find((c) => c.method === "PUT")).toMatchObject({
    path: "/api/secciones/31",
    body: { capacidadMaxima: 40, docenteTutorId: 77, version: 2, motivo: "Aula más pequeña" },
  });
});

test("Secciones: un error del servidor se muestra y si la recarga falla se avisa", async () => {
  let reloads = 0;
  academicBackend([year()], {
    "POST /api/secciones": ({ body }) =>
      body.nombre === "X" ? reply(409, { message: "La sección ya existe" }) : reply(201, {}),
    [`GET ${BASE}/anios/:anio`]: () => (++reloads > 1 ? reply(500, null) : year()),
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await ready();
  await user.click(screen.getByText("+ Nueva sección"));
  const dialog = screen.getByRole("dialog");
  await user.type(within(dialog).getByLabelText(/^Sección/), "X");
  await user.type(within(dialog).getByLabelText(/^Motivo/), "Nueva");
  await user.click(within(dialog).getByText("Guardar sección"));
  expect((await within(dialog).findByRole("alert")).textContent).toBe("La sección ya existe");
  const name = within(dialog).getByLabelText(/^Sección/);
  await user.clear(name);
  await user.type(name, "D");
  await user.click(within(dialog).getByText("Guardar sección"));
  expect(await screen.findByText(/No se pudo actualizar la tabla; recarga la página/)).toBeTruthy();
});

test("Secciones: inhabilita con motivo y muestra el rechazo del servidor", async () => {
  const { calls } = academicBackend([year()], {
    "PUT /api/secciones/:id/estado": ({ body }) =>
      body.motivo === "Con matrículas" ? reply(409, { message: "La sección tiene matrículas" }) : {},
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await ready();
  await user.click(within(row("Sección B")).getByText("Inhabilitar"));
  const dialog = screen.getByRole("dialog", { name: "Cambiar estado de sección" });
  await user.type(within(dialog).getByLabelText(/Motivo/), "Con matrículas");
  await user.click(within(dialog).getByText("Confirmar"));
  expect((await within(dialog).findByRole("alert")).textContent).toBe("La sección tiene matrículas");
  const reason = within(dialog).getByLabelText(/Motivo/);
  await user.clear(reason);
  await user.type(reason, "Sin uso");
  await user.click(within(dialog).getByText("Confirmar"));
  expect(await screen.findByText(/Estado de sección actualizado/)).toBeTruthy();
  expect(calls.filter((c) => c.method === "PUT").at(-1)).toMatchObject({
    path: "/api/secciones/32/estado",
    body: { activo: false, version: 2, motivo: "Sin uso" },
  });
});

test("Grados: lista capacidades y habilita un grado nuevo", async () => {
  const { calls } = academicBackend([year()], {
    [`POST ${BASE}/anios/:anio/grados`]: reply(201, {}),
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await ready();
  await user.click(screen.getByRole("button", { name: /Grados habilitados/ }));
  expect(within(gradeRow(1)).getByText("70")).toBeTruthy();
  await user.click(screen.getByText("Habilitar grado"));
  const dialog = screen.getByRole("dialog", { name: "Habilitar grado" });
  await user.click(within(dialog).getByText("Guardar"));
  expect(within(dialog).getByRole("alert")).toBeTruthy();
  expect([...within(dialog).getByLabelText(/^Grado/).options].map((o) => o.value)).toEqual([
    "",
    "3",
    "4",
    "5",
  ]);
  await user.selectOptions(within(dialog).getByLabelText(/^Grado/), "3");
  const capacity = within(dialog).getByLabelText(/Capacidad por defecto/);
  await user.clear(capacity);
  await user.type(capacity, "32");
  await user.click(within(dialog).getByText("Guardar"));
  expect(await screen.findByText("Grado habilitado correctamente.")).toBeTruthy();
  expect(calls.find((c) => c.method === "POST").body).toEqual({ numero: 3, capacidadDefault: 32 });
});

test("Grados: editar capacidad e inhabilitar avisan de las secciones activas", async () => {
  const { calls } = academicBackend([year()], {
    [`PUT ${BASE}/grados/:id`]: reply(200, {}),
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await ready();
  await user.click(screen.getByRole("button", { name: /Grados habilitados/ }));
  await user.click(within(gradeRow(1)).getByText("Editar"));
  let dialog = screen.getByRole("dialog", { name: "Editar grado" });
  await user.click(within(dialog).getByText("Guardar"));
  expect(within(dialog).getByRole("alert")).toBeTruthy();
  await user.type(within(dialog).getByLabelText(/^Motivo/), "Ajuste");
  await user.click(within(dialog).getByText("Guardar"));
  expect(await screen.findByText("Grado actualizado.")).toBeTruthy();
  await user.click(within(gradeRow(1)).getByText("Inhabilitar"));
  dialog = screen.getByRole("dialog", { name: "Cambiar estado del grado" });
  expect(within(dialog).getByText(/tiene 2 sección\(es\) activa\(s\)/)).toBeTruthy();
  await user.type(within(dialog).getByLabelText(/^Motivo/), "Cierre");
  await user.click(within(dialog).getByText("Guardar"));
  expect(await screen.findByText("Grado inhabilitado.")).toBeTruthy();
  expect(calls.filter((c) => c.method === "PUT").map((c) => c.body)).toEqual([
    { activo: true, capacidadDefault: 35, version: 1, motivo: "Ajuste" },
    { activo: false, capacidadDefault: 35, version: 1, motivo: "Cierre" },
  ]);
});

test("Grados: con los cinco configurados e inhabilitados se explica cómo seguir", async () => {
  const grades = [1, 2, 3, 4, 5].map((numero) => grade({ id: numero, numero, activo: false }));
  const { calls } = academicBackend([year({ grados: grades, secciones: [] })], {
    [`PUT ${BASE}/grados/:id`]: reply(409, { message: "Versión desactualizada" }),
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await ready();
  // Sin grados habilitados la sección ya aparece abierta.
  expect(screen.getByRole("button", { name: /Grados habilitados/ }).getAttribute("aria-expanded")).toBe("true");
  expect(screen.getByText("Los cinco grados ya están configurados.")).toBeTruthy();
  expect(screen.getByText(/Todos los grados están inhabilitados/)).toBeTruthy();
  await user.click(within(gradeRow(3)).getByText("Habilitar"));
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByText(/Se habilitará el 3° Grado/)).toBeTruthy();
  await user.type(within(dialog).getByLabelText(/^Motivo/), "Reapertura");
  const before = calls.length;
  await user.click(within(dialog).getByText("Guardar"));
  expect((await within(dialog).findByRole("alert")).textContent).toBe("Versión desactualizada");
  await waitFor(() => expect(calls.length).toBeGreaterThan(before + 1));
  await user.click(within(dialog).getByText("Cancelar"));
  await user.click(screen.getByRole("button", { name: /Grados habilitados/ }));
  expect(screen.queryByText("Habilitar grado")).toBeNull();
});

test("Grados: un año sin grados y un año cerrado de solo lectura", async () => {
  academicBackend([year({ estado: "CERRADO", grados: [], secciones: [] })]);
  render(<AcademicConfiguration />);
  await ready();
  expect(screen.getByText("Año cerrado: los grados son de solo lectura.")).toBeTruthy();
  expect(screen.getByText("Habilita al menos un grado para poder crear secciones.")).toBeTruthy();
});

test("Días no lectivos: se cargan al expandir y se agrega uno validado", async () => {
  const days = [
    { id: 2, fecha: "2026-07-28", descripcion: "Fiestas Patrias", version: 1 },
    { id: 1, fecha: "2026-05-01", descripcion: null, version: 1 },
  ];
  const { calls } = academicBackend([year()], {
    [`GET ${BASE}/anios/:anio/dias-no-lectivos`]: () => days,
    [`POST ${BASE}/anios/:anio/dias-no-lectivos`]: ({ body }) => {
      days.push({ id: 3, version: 0, descripcion: null, ...body });
      return reply(201, days.at(-1));
    },
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await ready();
  await user.click(screen.getByRole("button", { name: /Días no lectivos/ }));
  const rows = await screen.findAllByText(/^\d{2} \w{3} 2026$/, { selector: "td" });
  expect(rows.map((cell) => cell.textContent)).toEqual(["01 May 2026", "28 Jul 2026"]);
  expect(within(rows[0].closest("tr")).getByText("—")).toBeTruthy();
  await user.click(screen.getByText("Agregar día no lectivo"));
  const dialog = screen.getByRole("dialog", { name: "Agregar día no lectivo" });
  await user.type(within(dialog).getByLabelText(/^Fecha/), "2026-07-28");
  await user.click(within(dialog).getByText("Guardar"));
  expect(within(dialog).getByRole("alert")).toBeTruthy();
  const date = within(dialog).getByLabelText(/^Fecha/);
  await user.clear(date);
  await user.type(date, "2026-08-30");
  await user.type(within(dialog).getByLabelText(/Descripción/), " Santa Rosa ");
  await user.click(within(dialog).getByText("Guardar"));
  expect(await screen.findByText("Día no lectivo agregado.")).toBeTruthy();
  expect(await screen.findByText("30 Ago 2026")).toBeTruthy();
  expect(calls.find((c) => c.method === "POST").body).toEqual({
    fecha: "2026-08-30",
    descripcion: "Santa Rosa",
  });
});

test("Días no lectivos: editar la descripción y recargar si la versión es vieja", async () => {
  let version = 1;
  const { calls } = academicBackend([year()], {
    [`GET ${BASE}/anios/:anio/dias-no-lectivos`]: () => [
      { id: 1, fecha: "2026-05-01", descripcion: "Día del Trabajo", version },
    ],
    [`PATCH ${BASE}/dias-no-lectivos/:id`]: ({ body }) => {
      if (body.version !== version) return reply(409, { message: "Otro usuario lo modificó." });
      version += 1;
      return { id: 1, ...body };
    },
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await ready();
  await user.click(screen.getByRole("button", { name: /Días no lectivos/ }));
  await user.click(await screen.findByText("Editar descripción", { selector: "button" }));
  let dialog = screen.getByRole("dialog", { name: "Editar descripción" });
  expect(within(dialog).getByText("01 May 2026")).toBeTruthy();
  const text = within(dialog).getByLabelText(/Descripción/);
  await user.clear(text);
  await user.type(text, "a".repeat(256));
  await user.click(within(dialog).getByText("Guardar"));
  expect(within(dialog).getByRole("alert")).toBeTruthy();
  await user.clear(text);
  await user.type(text, "Feriado nacional");
  await user.click(within(dialog).getByText("Guardar"));
  expect(await screen.findByText("Descripción actualizada.")).toBeTruthy();
  version = 5;
  await user.click(await screen.findByText("Editar descripción", { selector: "button" }));
  dialog = screen.getByRole("dialog");
  await user.click(within(dialog).getByText("Guardar"));
  expect(await screen.findByText("Otro usuario lo modificó. Se recargó la lista.")).toBeTruthy();
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(calls.filter((c) => c.method === "PATCH")).toHaveLength(2);
});

test("Días no lectivos: lista vacía, errores y año cerrado", async () => {
  academicBackend([year()], {
    [`GET ${BASE}/anios/:anio/dias-no-lectivos`]: () => [],
    [`POST ${BASE}/anios/:anio/dias-no-lectivos`]: reply(400, { message: "Fecha inválida" }),
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await ready();
  const toggle = screen.getByRole("button", { name: /Días no lectivos/ });
  await user.click(toggle);
  expect(await screen.findByText("Sin días no lectivos")).toBeTruthy();
  await user.click(screen.getByText("Agregar día no lectivo"));
  const dialog = screen.getByRole("dialog");
  await user.type(within(dialog).getByLabelText(/^Fecha/), "2026-04-02");
  await user.click(within(dialog).getByText("Guardar"));
  expect((await within(dialog).findByRole("alert")).textContent).toBe("Fecha inválida");
  await user.click(within(dialog).getByText("Cancelar"));
  await user.click(toggle);
  expect(screen.queryByText("Sin días no lectivos")).toBeNull();
});

test("Días no lectivos: error al cargar y solo lectura en año cerrado", async () => {
  academicBackend([year({ estado: "CERRADO" })], {
    [`GET ${BASE}/anios/:anio/dias-no-lectivos`]: reply(500, { message: "No disponible" }),
  });
  const user = userEvent.setup();
  render(<AcademicConfiguration />);
  await ready();
  await user.click(screen.getByRole("button", { name: /Días no lectivos/ }));
  expect((await screen.findByRole("alert")).textContent).toBe("No disponible");
  expect(screen.getByText("Año cerrado: los días no lectivos son de solo lectura.")).toBeTruthy();
});

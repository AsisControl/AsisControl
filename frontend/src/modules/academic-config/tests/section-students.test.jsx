import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { SectionsPanel } from "../sections/sections-panel";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { currentUrl, setUrl } from "../../../../tests/support/navigation.js";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const section = (overrides = {}) => ({
  id: 31, nombre: "C", grado: 1, anioAcademico: 2026, activo: true,
  capacidadMaxima: 5, ocupacion: 1, vacantes: 4, version: 0, ...overrides,
});

const enrolled = {
  id: 200, alumnoId: 42, codigoAlumno: "EST-042", alumno: "Lucía Flores",
  numeroDocumento: "70707070", estado: "ACTIVA",
};

const student = {
  id: 42, codigoAlumno: "EST-042", activo: true, apoderados: [],
  accionesPermitidas: { editar: true, inactivar: true, eliminar: true },
  persona: { id: 17, nombres: "Lucía", apellidoPaterno: "Flores", nombreCompleto: "Lucía Flores" },
};

function renderPanel({ editable = true, sections = [section()] } = {}) {
  const onReload = vi.fn(async () => {});
  render(<SectionsPanel year={{ id: 7, anio: 2026, grados: [{ id: 1, numero: 1, activo: true }], secciones: sections }}
    editable={editable} onReload={onReload} onSuccess={vi.fn()} />);
  return onReload;
}

const openRoster = async (user) => {
  await user.click(screen.getByRole("button", { name: "Ver alumnos de 1° Grado, Sección C" }));
  return screen.getByRole("dialog", { name: "Alumnos de 1° Grado, Sección C" });
};

beforeEach(() => {
  signIn();
  setUrl("/administracion/configuracion-academica");
});

it("lista los alumnos vigentes de la sección, abre su ficha en consulta y vuelve a la lista", async () => {
  const calls = fakeBackend({
    "GET /api/matriculas": page([enrolled]),
    "GET /api/alumnos/:id": student,
  });
  const onReload = renderPanel();
  const user = userEvent.setup();
  const dialog = await openRoster(user);
  const row = (await within(dialog).findByText("Lucía Flores")).closest("tr");
  expect(within(row).getByText("EST-042")).toBeTruthy();
  expect(within(row).getByText("70707070")).toBeTruthy();
  expect(within(row).getByText("Activa")).toBeTruthy();
  expect(calls.find((call) => call.path === "/api/matriculas").query).toMatchObject({
    seccionId: "31", vigentes: "true",
  });
  await user.click(within(row).getByRole("button", { name: "Ver ficha de Lucía Flores" }));
  const record = await screen.findByRole("dialog", { name: "Ficha del estudiante" });
  expect(screen.getAllByRole("dialog")).toHaveLength(1);
  expect(within(record).queryByRole("button", { name: "Editar ficha" })).toBeNull();
  expect(within(record).queryByRole("button", { name: "Inactivar ficha" })).toBeNull();
  await user.click(within(record).getByRole("button", { name: "Cerrar", exact: true }));
  expect(await screen.findByRole("dialog", { name: "Alumnos de 1° Grado, Sección C" })).toBeTruthy();
  expect(onReload).toHaveBeenCalled();
});

it("Salir cierra la ventana y Agregar alumno lleva a las fichas con la sección de destino", async () => {
  fakeBackend({ "GET /api/matriculas": page([]) });
  renderPanel();
  const user = userEvent.setup();
  let dialog = await openRoster(user);
  expect(await within(dialog).findByText("Esta sección todavía no tiene alumnos.")).toBeTruthy();
  await user.click(within(dialog).getByRole("button", { name: "Salir" }));
  expect(screen.queryByRole("dialog")).toBeNull();
  dialog = await openRoster(user);
  await user.click(within(dialog).getByRole("button", { name: "Agregar alumno" }));
  expect(currentUrl()).toBe("/matricula/fichas/estudiantes?seccion=31");
});

it("no permite agregar en una sección sin vacantes y muestra los errores", async () => {
  fakeBackend({ "GET /api/matriculas": reply(500, { message: "No se pudo listar" }) });
  renderPanel({ sections: [section({ ocupacion: 5, vacantes: 0 })] });
  const user = userEvent.setup();
  const dialog = await openRoster(user);
  expect((await within(dialog).findByRole("alert")).textContent).toBe("No se pudo listar");
  expect(within(dialog).getByRole("button", { name: "Agregar alumno" }).disabled).toBe(true);
});

it("en un año de solo consulta permite ver alumnos sin agregar", async () => {
  fakeBackend({ "GET /api/matriculas": page([enrolled]), "GET /api/alumnos/:id": reply(404, { message: "Ficha no encontrada" }) });
  renderPanel({ editable: false });
  const user = userEvent.setup();
  const dialog = await openRoster(user);
  expect(within(dialog).queryByRole("button", { name: "Agregar alumno" })).toBeNull();
  await user.click(await within(dialog).findByRole("button", { name: "Ver ficha de Lucía Flores" }));
  expect((await within(dialog).findByRole("alert")).textContent).toBe("Ficha no encontrada");
});

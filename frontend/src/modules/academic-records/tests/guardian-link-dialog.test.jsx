import { beforeEach, expect, test, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { usePathname } from "next/navigation";
import { setUrl } from "../../../../tests/support/navigation.js";
import { AcademicRecords } from "@/modules/academic-records";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const BASE = "/matricula/fichas";
function RecordsScreen() {
  return <AcademicRecords basePath={BASE} view={usePathname().split("/")[3]} />;
}
const persona = (id, nombreCompleto, numeroDocumento) => ({ id, nombreCompleto, numeroDocumento, tipoDocumento: "DNI" });

/** Backend con estado: el vínculo creado aparece en las dos fichas, como en la API real. */
function backend(routes = {}) {
  const links = [];
  const student = (id, nombre, documento) => ({
    id,
    codigoAlumno: `EST-00${id}`,
    persona: persona(id, nombre, documento),
    activo: true,
    apoderados: links
      .filter((link) => link.alumnoId === id)
      .map((link) => ({ ...link, nombreCompleto: "Jorge Rojas" })),
  });
  const students = () => [student(3, "Lucía Flores", "71234567"), student(4, "Mario Díaz", "71000111")];
  const guardian = () => ({
    id: 8,
    codigoApoderado: "APO-008",
    persona: persona(80, "Jorge Rojas", "40998877"),
    activo: true,
    alumnos: links.map((link) => {
      const found = students().find((item) => item.id === link.alumnoId);
      return { ...link, codigoAlumno: found.codigoAlumno, nombreCompleto: found.persona.nombreCompleto };
    }),
  });
  return fakeBackend({
    "GET /api/alumnos": () => page(students()),
    "GET /api/alumnos/:id": () => students()[1],
    "GET /api/apoderados": () => page([guardian()]),
    "GET /api/apoderados/:id": () => guardian(),
    "POST /api/alumnos/:id/apoderados": ({ path, body }) => {
      links.push({ id: 20, alumnoId: Number(path.split("/")[3]), ...body });
      return reply(201, {});
    },
    ...routes,
  });
}

beforeEach(() => {
  signIn();
  setUrl(`${BASE}/parentescos`);
});

const rowOf = (dialog, text) => within(dialog).getByText(text).closest("li");

async function openDialog(user) {
  await user.click(await screen.findByRole("button", { name: "+ Vincular apoderado" }));
  const dialog = screen.getByRole("dialog", { name: "Vincular apoderado" });
  await within(dialog).findByText("Mario Díaz");
  return dialog;
}

async function chooseBoth(user, dialog) {
  await user.click(within(rowOf(dialog, "Mario Díaz")).getByRole("button", { name: "Elegir" }));
  await within(dialog).findByText("Jorge Rojas");
  await user.click(within(rowOf(dialog, "Jorge Rojas")).getByRole("button", { name: "Elegir" }));
}

test("Vincula con parentesco, refresca el listado y la relación aparece en ambas fichas", async () => {
  const calls = backend();
  const user = userEvent.setup();
  const view = render(<RecordsScreen />);
  const dialog = await openDialog(user);
  await chooseBoth(user, dialog);
  await user.selectOptions(within(dialog).getByLabelText(/Parentesco/), "PADRE");
  await user.click(within(dialog).getByLabelText(/Apoderado principal/));
  await user.click(within(dialog).getByRole("button", { name: "Guardar vínculo" }));
  expect((await screen.findByRole("status")).textContent).toBe("Apoderado vinculado a Mario Díaz.");
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(calls.find((call) => call.method === "POST")).toMatchObject({
    path: "/api/alumnos/4/apoderados",
    body: { apoderadoId: 8, parentesco: "PADRE", principal: true },
  });
  const row = (await screen.findByText("Jorge Rojas")).closest("tr");
  expect(within(row).getByText("Mario Díaz")).toBeTruthy();
  expect(within(row).getByText("Padre")).toBeTruthy();

  setUrl(`${BASE}/apoderados`);
  view.rerender(<RecordsScreen />);
  await user.click(await screen.findByRole("button", { name: "Ver ficha de Jorge Rojas" }));
  const guardianRecord = screen.getByRole("dialog", { name: "Ficha del apoderado" });
  const linked = (await within(guardianRecord).findByText("Mario Díaz")).closest("tr");
  expect(within(linked).getByText("Padre")).toBeTruthy();
  await user.click(within(guardianRecord).getByRole("button", { name: "Cerrar" }));

  setUrl(`${BASE}/estudiantes`);
  view.rerender(<RecordsScreen />);
  await user.click(await screen.findByRole("button", { name: /Ver ficha de Mario Díaz/ }));
  const studentRecord = screen.getByRole("dialog");
  expect(await within(studentRecord).findByText("Jorge Rojas")).toBeTruthy();
  expect(within(studentRecord).getByText(/Padre/)).toBeTruthy();
});

test("Exige estudiante y apoderado, y no arrastra la elección a otro estudiante", async () => {
  const calls = backend();
  const user = userEvent.setup();
  render(<RecordsScreen />);
  const dialog = await openDialog(user);
  expect(within(dialog).queryByText("Jorge Rojas")).toBeNull();
  expect(within(dialog).queryByRole("button", { name: "Guardar vínculo" })).toBeNull();
  await chooseBoth(user, dialog);
  await user.click(within(rowOf(dialog, "Lucía Flores")).getByRole("button", { name: "Elegir" }));
  await within(dialog).findByText("Jorge Rojas");
  expect(within(dialog).queryByRole("button", { name: "Guardar vínculo" })).toBeNull();
  await user.click(within(dialog).getByRole("button", { name: "Cancelar" }));
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(calls.some((call) => call.method !== "GET")).toBe(false);
});

test("Muestra el error de la API, bloquea mientras envía y permite reintentar", async () => {
  let respond;
  const calls = backend({
    "POST /api/alumnos/:id/apoderados": () => new Promise((resolve) => (respond = resolve)),
  });
  const user = userEvent.setup();
  render(<RecordsScreen />);
  const dialog = await openDialog(user);
  await chooseBoth(user, dialog);
  const form = within(dialog).getByRole("button", { name: "Guardar vínculo" }).closest("form");
  fireEvent.submit(form);
  fireEvent.submit(form);
  expect(within(dialog).getByRole("button", { name: "Asociando…" }).disabled).toBe(true);
  expect(within(dialog).getByRole("button", { name: "Cancelar" }).disabled).toBe(true);
  respond(reply(409, { code: "VINCULO_DUPLICADO", message: "El apoderado ya esta vinculado al alumno" }));
  expect((await within(dialog).findByRole("alert")).textContent).toBe("El apoderado ya esta vinculado al alumno");
  await waitFor(() =>
    expect(within(dialog).getByRole("button", { name: "Guardar vínculo" }).disabled).toBe(false),
  );
  expect(calls.filter((call) => call.method === "POST")).toHaveLength(1);
});

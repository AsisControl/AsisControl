import { beforeEach, expect, test, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { signIn, page } from "../../../../tests/support/backend.js";
import { FamilyImportDialog } from "../import-dialog";
import { fileSizeLabel } from "../excel-file-picker";
import { Students } from "../students";

const NativeURL = URL;
const reply = (body, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => body,
  blob: async () => new Blob(["plantilla"]),
});
const excel = (name = "alumnos.xlsx", size) => {
  const file = new File(["contenido"], name);
  if (size) Object.defineProperty(file, "size", { value: size });
  return file;
};
/** Respuesta de /api/alumnos/importaciones/familias. */
const bulk = (files, { valid = true, saved = false } = {}) => ({ valid, saved, files });
const ok = (rows) => ({ error: null, result: { totalRows: rows, importedRows: rows, rejectedRows: 0, issues: [] } });
const renderDialog = (props = {}) =>
  render(<FamilyImportDialog onClose={vi.fn()} onImported={vi.fn()} {...props} />);
/** Tarjeta de un paso de la carga: Estudiantes, Apoderados o Parentescos. */
const slot = (title) =>
  screen.getByRole("heading", { name: new RegExp(`^${title}`) }).closest("li");
const studentsInput = () => screen.getByLabelText("Archivo Excel de estudiantes");

beforeEach(() => {
  signIn();
  vi.stubGlobal("URL", class extends NativeURL {
    static createObjectURL = vi.fn(() => "blob:plantilla");
    static revokeObjectURL = vi.fn();
  });
});

test("El tamaño del archivo se muestra en B, KB o MB", () => {
  expect(fileSizeLabel(850)).toBe("850 B");
  expect(fileSizeLabel(24 * 1024)).toBe("24 KB");
  expect(fileSizeLabel(1.25 * 1024 * 1024)).toBe("1.3 MB");
});

test("Las tres plantillas se descargan y solo el primer paso empieza habilitado", () => {
  renderDialog();
  const templates = screen.getByRole("region", { name: "1. Prepara las plantillas" });
  for (const name of ["estudiantes", "apoderados", "parentescos"])
    expect(within(templates).getByRole("button", { name: `Descargar plantilla de ${name}` }).disabled).toBe(false);
  expect(
    screen.getAllByRole("heading", { level: 5 }).map((heading) => heading.firstChild.textContent),
  ).toEqual(["Estudiantes", "Apoderados", "Parentescos"]);
  expect(screen.queryByText("Próximamente")).toBeNull();
  expect(within(slot("Estudiantes")).getByRole("button", { name: "Seleccionar archivo Excel" }).disabled).toBe(false);
  for (const title of ["Apoderados", "Parentescos"]) {
    expect(within(slot(title)).getByRole("button", { name: "Seleccionar archivo Excel" }).disabled).toBe(true);
    expect(slot(title).getAttribute("aria-disabled")).toBe("true");
  }
  expect(within(slot("Apoderados")).getByText("Primero elige el archivo de estudiantes.")).toBeTruthy();
  expect(studentsInput().accept).toContain(".xlsx");
  expect(within(slot("Estudiantes")).getByText("Solo Excel (.xlsx), máximo 5 MB")).toBeTruthy();
});

test("La ficha abre la carga masiva, valida sin guardar y muestra los errores por fila", async () => {
  const fetcher = vi.fn(async (_, options = {}) => options.method === "POST"
    ? reply(bulk({ estudiantes: { error: null, result: { totalRows: 3, importedRows: 1, rejectedRows: 2, issues: [
      { row: 3, field: "nombres", message: "Solo letras" },
      { row: 4, field: null, message: "Fila incompleta" },
    ] } } }, { valid: false }))
    : reply(page([])));
  vi.stubGlobal("fetch", fetcher);
  const user = userEvent.setup();
  render(<Students />);
  await screen.findByText("Todavía no hay estudiantes registrados.");
  await user.click(screen.getByRole("button", { name: "Carga masiva" }));
  const dialog = screen.getByRole("dialog");
  await user.upload(within(dialog).getByLabelText("Archivo Excel de estudiantes"), excel());
  await user.click(within(dialog).getByRole("button", { name: "Validar archivos" }));
  expect(await within(dialog).findByText("Solo letras")).toBeTruthy();
  expect(within(dialog).getByText("Fila incompleta")).toBeTruthy();
  expect(within(dialog).getByText("2 filas con error")).toBeTruthy();
  expect(within(dialog).getByText(/No se guardó ninguna fila/)).toBeTruthy();
  expect(within(dialog).getByRole("button", { name: "Importar" }).disabled).toBe(true);
  const upload = fetcher.mock.calls.find(([, options]) => options.method === "POST");
  expect(new URL(upload[0]).pathname).toBe("/api/alumnos/importaciones/familias");
  expect(new URL(upload[0]).searchParams.get("confirmar")).toBe("false");
  expect(upload[1].body.get("estudiantes").name).toBe("alumnos.xlsx");
  expect(upload[1].headers.has("Content-Type")).toBe(false);
  expect(upload[1].headers.get("Authorization")).toBe("Bearer test-token");
  // Nada se guardó: el listado no se vuelve a consultar.
  expect(fetcher.mock.calls.filter(([, options]) => !options.method).length).toBe(1);
  await user.click(within(dialog).getByRole("button", { name: "Cancelar" }));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Solo importa tras una validación sin errores y entonces guarda todo", async () => {
  const imported = vi.fn(), close = vi.fn();
  const fetcher = vi.fn()
    .mockResolvedValueOnce(reply({ message: "El servidor no respondió" }, 503))
    .mockResolvedValueOnce(reply(bulk({ estudiantes: ok(2) })))
    .mockResolvedValueOnce(reply(bulk({ estudiantes: ok(2) })))
    .mockResolvedValueOnce(reply(bulk({ estudiantes: ok(2) }, { saved: true })));
  vi.stubGlobal("fetch", fetcher);
  const user = userEvent.setup();
  renderDialog({ onClose: close, onImported: imported });
  const importButton = () => screen.getByRole("button", { name: "Importar" });
  expect(importButton().disabled).toBe(true);
  await user.click(screen.getByRole("button", { name: "Validar archivos" }));
  expect(await screen.findByText("Selecciona al menos un archivo Excel para importar.")).toBeTruthy();
  expect(fetcher).not.toHaveBeenCalled();
  await user.upload(studentsInput(), excel());
  await user.click(screen.getByRole("button", { name: "Validar archivos" }));
  expect(await screen.findByText("El servidor no respondió")).toBeTruthy();
  expect(importButton().disabled).toBe(true);
  await user.click(screen.getByRole("button", { name: "Validar archivos" }));
  expect(await screen.findByText(/no tienen errores/)).toBeTruthy();
  expect(within(slot("Estudiantes")).getByText("Válidas").nextSibling.textContent).toBe("2");
  expect(importButton().disabled).toBe(false);
  // Cambiar el archivo obliga a validar de nuevo.
  await user.upload(studentsInput(), excel("otro.xlsx"));
  expect(importButton().disabled).toBe(true);
  expect(screen.queryByText("Válidas")).toBeNull();
  await user.click(screen.getByRole("button", { name: "Validar archivos" }));
  await screen.findByText(/no tienen errores/);
  await user.click(importButton());
  expect(await screen.findByRole("heading", { name: "¡Carga completada!" })).toBeTruthy();
  expect(screen.getByText("Se registraron 2 estudiantes. Ya puedes verlos en Fichas académicas.")).toBeTruthy();
  expect(document.activeElement).toBe(screen.getByRole("heading", { name: "¡Carga completada!" }).closest("[tabindex]"));
  expect(new URL(fetcher.mock.calls.at(-1)[0]).searchParams.get("confirmar")).toBe("true");
  expect(within(slot("Estudiantes")).getByText("Registradas").nextSibling.textContent).toBe("2");
  expect(imported).toHaveBeenCalledOnce();
  // Ya no hay nada que validar ni importar: solo queda terminar.
  expect(screen.queryByRole("button", { name: "Importar" })).toBeNull();
  expect(screen.queryByRole("button", { name: "Validar archivos" })).toBeNull();
  expect(screen.queryByRole("region", { name: "1. Prepara las plantillas" })).toBeNull();
  await user.click(screen.getByRole("button", { name: "Listo" }));
  expect(close).toHaveBeenCalledOnce();
});

test("Descarga la plantilla de estudiantes con la sesión actual y libera el recurso", async () => {
  const fetcher = vi.fn(async () => reply(null));
  vi.stubGlobal("fetch", fetcher);
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  const user = userEvent.setup();
  renderDialog();
  await user.click(screen.getByRole("button", { name: "Descargar plantilla de estudiantes" }));
  expect(fetcher.mock.calls[0][0]).toContain("/api/alumnos/importaciones/plantilla");
  expect(fetcher.mock.calls[0][1].headers.get("Authorization")).toBe("Bearer test-token");
  expect(click).toHaveBeenCalledOnce();
  expect(click.mock.instances[0].download).toBe("plantilla_alumnos.xlsx");
  await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:plantilla"), { timeout: 2000 });
});

test("Valida los tres archivos juntos y señala el error de cada uno", async () => {
  const fetcher = vi.fn(async () => reply(bulk({
    estudiantes: ok(2),
    apoderados: { error: "Faltan las columnas: correo", result: null },
    parentescos: { error: null, result: { totalRows: 2, importedRows: 1, rejectedRows: 1, issues: [
      { row: 3, field: "fila", message: "No hay un estudiante habilitado con el codigo X" },
    ] } },
  }, { valid: false })));
  vi.stubGlobal("fetch", fetcher);
  const imported = vi.fn();
  const user = userEvent.setup();
  renderDialog({ onImported: imported });
  await user.upload(studentsInput(), excel("alumnos.xlsx"));
  await user.upload(screen.getByLabelText("Archivo Excel de apoderados"), excel("apoderados.xlsx"));
  await user.upload(screen.getByLabelText("Archivo Excel de parentescos"), excel("parentescos.xlsx"));
  await user.click(screen.getByRole("button", { name: "Validar archivos" }));
  expect(await within(slot("Parentescos")).findByText("No hay un estudiante habilitado con el codigo X")).toBeTruthy();
  expect(fetcher).toHaveBeenCalledOnce();
  const body = fetcher.mock.calls[0][1].body;
  expect(["estudiantes", "apoderados", "parentescos"].map((part) => body.get(part).name))
    .toEqual(["alumnos.xlsx", "apoderados.xlsx", "parentescos.xlsx"]);
  expect(within(slot("Apoderados")).getByText("Faltan las columnas: correo")).toBeTruthy();
  expect(within(slot("Estudiantes")).getByText("Válidas").nextSibling.textContent).toBe("2");
  expect(screen.getByRole("button", { name: "Importar" }).disabled).toBe(true);
  expect(imported).not.toHaveBeenCalled();
});

test("Quitar o invalidar un archivo vuelve a bloquear y vaciar los pasos siguientes", async () => {
  const user = userEvent.setup();
  renderDialog();
  const pick = (title) => within(slot(title)).getByRole("button", { name: "Seleccionar archivo Excel" });
  await user.upload(studentsInput(), excel("alumnos.xlsx"));
  expect(pick("Apoderados").disabled).toBe(false);
  expect(pick("Parentescos").disabled).toBe(true);
  await user.upload(screen.getByLabelText("Archivo Excel de apoderados"), excel("apoderados.xlsx"));
  expect(pick("Parentescos").disabled).toBe(false);
  await user.upload(studentsInput(), excel("alumnos.csv"));
  expect(within(slot("Apoderados")).queryByText("apoderados.xlsx")).toBeNull();
  expect(slot("Apoderados").getAttribute("aria-disabled")).toBe("true");
  expect(slot("Parentescos").getAttribute("aria-disabled")).toBe("true");
  await user.upload(studentsInput(), excel("alumnos.xlsx"));
  expect(pick("Apoderados").disabled).toBe(false);
  expect(pick("Parentescos").disabled).toBe(true);
});

test("Descarga las plantillas de apoderados y parentescos con su nombre", async () => {
  const fetcher = vi.fn(async () => reply(null));
  vi.stubGlobal("fetch", fetcher);
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  const user = userEvent.setup();
  renderDialog();
  await user.click(screen.getByRole("button", { name: "Descargar plantilla de apoderados" }));
  await user.click(screen.getByRole("button", { name: "Descargar plantilla de parentescos" }));
  expect(fetcher.mock.calls.map(([url]) => new URL(url).pathname)).toEqual([
    "/api/apoderados/importaciones/plantilla",
    "/api/apoderados/parentescos/importaciones/plantilla",
  ]);
  expect(click.mock.instances.map((link) => link.download)).toEqual([
    "plantilla_apoderados.xlsx",
    "plantilla_parentescos.xlsx",
  ]);
});

test("Muestra el error de descarga y mantiene disponible el formulario", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => reply({ message: "Plantilla no disponible" }, 503)));
  const user = userEvent.setup();
  renderDialog();
  await user.click(screen.getByRole("button", { name: "Descargar plantilla de estudiantes" }));
  expect(await screen.findByText("Plantilla no disponible")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Validar archivos" }).disabled).toBe(false);
});

test("Al elegir el archivo muestra nombre y tamaño, y se puede cambiar o quitar", async () => {
  const user = userEvent.setup();
  renderDialog();
  const card = slot("Estudiantes");
  const chooseFile = within(card).getByRole("button", { name: "Seleccionar archivo Excel" });
  const activateInput = vi.spyOn(studentsInput(), "click");
  chooseFile.focus();
  await user.keyboard("{Enter}");
  expect(activateInput).toHaveBeenCalledOnce();
  await user.upload(studentsInput(), excel("estudiantes-segundo-grado.xlsx", 24 * 1024));
  expect(within(card).getByRole("status").textContent).toBe("estudiantes-segundo-grado.xlsx");
  expect(within(card).getByText("24 KB")).toBeTruthy();
  expect(within(card).queryByText(/Arrastra el archivo aquí/)).toBeNull();
  await user.upload(studentsInput(), excel("segundo.xlsx", 2048));
  expect(within(card).getByRole("status").textContent).toBe("segundo.xlsx");
  await user.click(within(card).getByRole("button", { name: "Quitar segundo.xlsx" }));
  expect(within(card).getByRole("status").textContent).toBe("Ningún archivo seleccionado");
  expect(studentsInput().value).toBe("");
});

test("Arrastrar un archivo lo valida al soltarlo", () => {
  renderDialog();
  const zone = () => within(slot("Estudiantes")).getByText(/Arrastra el archivo aquí/).closest("label");
  fireEvent.dragOver(zone());
  expect(zone().className).toContain("dropzoneActive");
  fireEvent.dragLeave(zone());
  expect(zone().className).not.toContain("dropzoneActive");
  fireEvent.drop(zone(), { dataTransfer: { files: [excel("alumnos.csv")] } });
  expect(screen.getByRole("alert").textContent).toBe("El archivo debe tener formato .xlsx.");
  fireEvent.click(screen.getByRole("button", { name: "Quitar alumnos.csv" }));
  fireEvent.drop(zone(), { dataTransfer: { files: [excel("grande.xlsx", 6 * 1024 * 1024)] } });
  expect(screen.getByRole("alert").textContent).toBe("El archivo no puede superar 5 MB.");
});

test("Un archivo no válido dirige el foco al selector antes de enviar", async () => {
  const fetcher = vi.fn();
  vi.stubGlobal("fetch", fetcher);
  const user = userEvent.setup({ applyAccept: false });
  renderDialog();
  await user.upload(studentsInput(), excel("alumnos.csv"));
  expect(screen.getByRole("alert").textContent).toBe("El archivo debe tener formato .xlsx.");
  await user.click(screen.getByRole("button", { name: "Validar archivos" }));
  expect(document.activeElement).toBe(screen.getByRole("button", { name: "Cambiar archivo" }));
  expect(fetcher).not.toHaveBeenCalled();
});

test("Mientras se valida no permite cambiar de archivo ni cerrar la ventana", async () => {
  let finishImport;
  vi.stubGlobal("fetch", vi.fn(() => new Promise((resolve) => { finishImport = resolve; })));
  const close = vi.fn();
  const user = userEvent.setup();
  renderDialog({ onClose: close });
  await user.upload(studentsInput(), excel());
  await user.click(screen.getByRole("button", { name: "Validar archivos" }));
  expect(screen.getByRole("button", { name: "Validando…" }).disabled).toBe(true);
  expect(screen.getByRole("button", { name: "Cambiar archivo" }).disabled).toBe(true);
  expect(studentsInput().disabled).toBe(true);
  expect(screen.getByRole("button", { name: "Cancelar" }).disabled).toBe(true);
  await user.keyboard("{Escape}");
  expect(close).not.toHaveBeenCalled();
  await act(async () => finishImport(reply(bulk({ estudiantes: ok(0) }))));
  expect(studentsInput().disabled).toBe(false);
});

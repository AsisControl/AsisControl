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
const emptyResult = { totalRows: 0, importedRows: 0, rejectedRows: 0, issues: [] };
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

test("Las tres plantillas se descargan y los tres archivos se pueden subir", () => {
  renderDialog();
  const templates = screen.getByRole("region", { name: "1. Prepara las plantillas" });
  for (const name of ["estudiantes", "apoderados", "parentescos"])
    expect(within(templates).getByRole("button", { name: `Descargar plantilla de ${name}` }).disabled).toBe(false);
  expect(
    screen.getAllByRole("heading", { level: 5 }).map((heading) => heading.firstChild.textContent),
  ).toEqual(["Estudiantes", "Apoderados", "Parentescos"]);
  expect(screen.queryByText("Próximamente")).toBeNull();
  for (const title of ["Estudiantes", "Apoderados", "Parentescos"])
    expect(within(slot(title)).getByRole("button", { name: "Seleccionar archivo Excel" }).disabled).toBe(false);
  expect(studentsInput().accept).toContain(".xlsx");
  expect(within(slot("Estudiantes")).getByText("Solo Excel (.xlsx), máximo 5 MB")).toBeTruthy();
});

test("La ficha abre la carga masiva y actualiza el listado tras una importación parcial", async () => {
  const fetcher = vi.fn(async (_, options = {}) => options.method === "POST"
    ? reply({ totalRows: 3, importedRows: 1, rejectedRows: 2, issues: [
      { row: 3, field: "nombres", message: "Solo letras" },
      { row: 4, field: null, message: "Fila incompleta" },
    ] })
    : reply(page([])));
  vi.stubGlobal("fetch", fetcher);
  const user = userEvent.setup();
  render(<Students />);
  await screen.findByText("Todavía no hay fichas de estudiantes registradas.");
  await user.click(screen.getByRole("button", { name: "Carga masiva" }));
  const dialog = screen.getByRole("dialog");
  await user.upload(within(dialog).getByLabelText("Archivo Excel de estudiantes"), excel());
  await user.click(within(dialog).getByRole("button", { name: "Importar archivos" }));
  expect(await within(dialog).findByText("Solo letras")).toBeTruthy();
  expect(within(dialog).getByText("Fila incompleta")).toBeTruthy();
  expect(within(dialog).getByText("2 filas con error")).toBeTruthy();
  const upload = fetcher.mock.calls.find(([, options]) => options.method === "POST");
  expect(upload[0]).toContain("/api/alumnos/importaciones");
  expect(upload[1].body.get("archivo").name).toBe("alumnos.xlsx");
  expect(upload[1].headers.has("Content-Type")).toBe(false);
  expect(upload[1].headers.get("Authorization")).toBe("Bearer test-token");
  await waitFor(() => expect(fetcher.mock.calls.filter(([, options]) => !options.method).length).toBe(2));
  await user.click(within(dialog).getByRole("button", { name: "Cerrar", exact: true }));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Valida la selección, permite corregir un rechazo y no refresca si ninguna fila se importó", async () => {
  const imported = vi.fn(), close = vi.fn();
  const fetcher = vi.fn()
    .mockResolvedValueOnce(reply({ message: "El archivo está dañado" }, 400))
    .mockResolvedValueOnce(reply(emptyResult));
  vi.stubGlobal("fetch", fetcher);
  const user = userEvent.setup();
  renderDialog({ onClose: close, onImported: imported });
  await user.click(screen.getByRole("button", { name: "Importar archivos" }));
  expect(await screen.findByText("Selecciona al menos un archivo Excel para importar.")).toBeTruthy();
  expect(fetcher).not.toHaveBeenCalled();
  await user.upload(studentsInput(), excel());
  expect(screen.queryByText("Selecciona al menos un archivo Excel para importar.")).toBeNull();
  await user.click(screen.getByRole("button", { name: "Importar archivos" }));
  expect(await screen.findByText("El archivo está dañado")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Importar archivos" }));
  await screen.findByText("Filas en el archivo");
  expect(screen.queryByText("El archivo está dañado")).toBeNull();
  expect(imported).not.toHaveBeenCalled();
  await user.upload(studentsInput(), excel());
  expect(screen.queryByText("Filas en el archivo")).toBeNull();
  await user.click(screen.getByRole("button", { name: "Cancelar" }));
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

test("Sube los archivos elegidos en orden y muestra el resultado de cada uno", async () => {
  const uploads = [];
  const imported = vi.fn();
  vi.stubGlobal("fetch", vi.fn(async (url, options) => {
    uploads.push([new URL(url).pathname, options.body.get("archivo").name]);
    return uploads.length === 1
      ? reply({ totalRows: 2, importedRows: 2, rejectedRows: 0, issues: [] })
      : reply({ totalRows: 2, importedRows: 1, rejectedRows: 1, issues: [
        { row: 3, field: "fila", message: "No hay un estudiante habilitado con el codigo X" },
      ] });
  }));
  const user = userEvent.setup();
  renderDialog({ onImported: imported });
  await user.upload(screen.getByLabelText("Archivo Excel de parentescos"), excel("parentescos.xlsx"));
  await user.upload(screen.getByLabelText("Archivo Excel de apoderados"), excel("apoderados.xlsx"));
  await user.click(screen.getByRole("button", { name: "Importar archivos" }));
  expect(await within(slot("Parentescos")).findByText("No hay un estudiante habilitado con el codigo X")).toBeTruthy();
  expect(uploads).toEqual([
    ["/api/apoderados/importaciones", "apoderados.xlsx"],
    ["/api/apoderados/parentescos/importaciones", "parentescos.xlsx"],
  ]);
  expect(within(slot("Apoderados")).getByText("Registradas").nextSibling.textContent).toBe("2");
  expect(within(slot("Estudiantes")).queryByText("Registradas")).toBeNull();
  expect(imported).toHaveBeenCalledTimes(2);
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
  expect(screen.getByRole("button", { name: "Importar archivos" }).disabled).toBe(false);
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
  await user.click(screen.getByRole("button", { name: "Importar archivos" }));
  expect(document.activeElement).toBe(screen.getByRole("button", { name: "Cambiar archivo" }));
  expect(fetcher).not.toHaveBeenCalled();
});

test("Mientras se importa no permite cambiar de archivo ni cerrar la ventana", async () => {
  let finishImport;
  vi.stubGlobal("fetch", vi.fn(() => new Promise((resolve) => { finishImport = resolve; })));
  const close = vi.fn();
  const user = userEvent.setup();
  renderDialog({ onClose: close });
  await user.upload(studentsInput(), excel());
  await user.click(screen.getByRole("button", { name: "Importar archivos" }));
  expect(screen.getByRole("button", { name: "Cambiar archivo" }).disabled).toBe(true);
  expect(studentsInput().disabled).toBe(true);
  expect(screen.getByRole("button", { name: "Cancelar" }).disabled).toBe(true);
  await user.keyboard("{Escape}");
  expect(close).not.toHaveBeenCalled();
  await act(async () => finishImport(reply(emptyResult)));
  expect(studentsInput().disabled).toBe(false);
});

import { beforeEach, expect, test, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { signIn, page } from "../../../../tests/support/backend.js";
import { StudentImportDialog } from "../import-dialog";
import { Students } from "../students";

const NativeURL = URL;
const reply = (body, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => body,
  blob: async () => new Blob(["plantilla"]),
});
const excel = () => new File(["contenido"], "alumnos.xlsx");

beforeEach(() => {
  signIn();
  vi.stubGlobal("URL", class extends NativeURL {
    static createObjectURL = vi.fn(() => "blob:plantilla");
    static revokeObjectURL = vi.fn();
  });
});

test("La ficha conserva la carga masiva y actualiza el listado tras una importación parcial", async () => {
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
  await user.upload(within(dialog).getByLabelText(/Archivo Excel/), excel());
  await user.click(within(dialog).getByRole("button", { name: "Importar archivo" }));
  expect(await within(dialog).findByText("Solo letras")).toBeTruthy();
  expect(within(dialog).getByText("Fila incompleta")).toBeTruthy();
  const upload = fetcher.mock.calls.find(([, options]) => options.method === "POST");
  expect(upload[0]).toContain("/api/alumnos/importaciones");
  expect(upload[1].body.get("archivo").name).toBe("alumnos.xlsx");
  expect(upload[1].headers.has("Content-Type")).toBe(false);
  expect(upload[1].headers.get("Authorization")).toBe("Bearer test-token");
  await waitFor(() => expect(fetcher.mock.calls.filter(([, options]) => !options.method).length).toBe(2));
  await user.click(within(dialog).getByRole("button", { name: "Cerrar", exact: true }));
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(screen.getByRole("button", { name: /Registrar estudiante/ })).toBeTruthy();
});

test("Valida la selección, permite corregir un rechazo y no refresca si ninguna fila se importó", async () => {
  const imported = vi.fn(), close = vi.fn();
  const fetcher = vi.fn()
    .mockResolvedValueOnce(reply({ message: "El archivo está dañado" }, 400))
    .mockResolvedValueOnce(reply({ totalRows: 0, importedRows: 0, rejectedRows: 0, issues: [] }));
  vi.stubGlobal("fetch", fetcher);
  const user = userEvent.setup();
  render(<StudentImportDialog onClose={close} onImported={imported} />);
  await user.click(screen.getByRole("button", { name: "Importar archivo" }));
  expect(await screen.findByText("Selecciona el archivo Excel con los estudiantes.")).toBeTruthy();
  expect(fetcher).not.toHaveBeenCalled();
  await user.upload(screen.getByLabelText(/Archivo Excel/), excel());
  expect(screen.queryByText("Selecciona el archivo Excel con los estudiantes.")).toBeNull();
  await user.click(screen.getByRole("button", { name: "Importar archivo" }));
  expect(await screen.findByText("El archivo está dañado")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Importar archivo" }));
  await screen.findByText("Estudiantes en el archivo");
  expect(screen.queryByText("El archivo está dañado")).toBeNull();
  expect(imported).not.toHaveBeenCalled();
  await user.upload(screen.getByLabelText(/Archivo Excel/), excel());
  expect(screen.queryByText("Estudiantes en el archivo")).toBeNull();
  await user.click(screen.getByRole("button", { name: "Cancelar" }));
  expect(close).toHaveBeenCalledOnce();
});

test("Descarga la plantilla con la sesión actual y libera el recurso", async () => {
  const fetcher = vi.fn(async () => reply(null));
  vi.stubGlobal("fetch", fetcher);
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  const user = userEvent.setup();
  render(<StudentImportDialog onClose={vi.fn()} onImported={vi.fn()} />);
  await user.click(screen.getByRole("button", { name: "Descargar plantilla" }));
  expect(fetcher.mock.calls[0][0]).toContain("/api/alumnos/importaciones/plantilla");
  expect(fetcher.mock.calls[0][1].headers.get("Authorization")).toBe("Bearer test-token");
  expect(click).toHaveBeenCalledOnce();
  expect(click.mock.instances[0].download).toBe("plantilla_alumnos.xlsx");
  await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:plantilla"), { timeout: 2000 });
});

test("Muestra el error de descarga y mantiene disponible el formulario", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => reply({ message: "Plantilla no disponible" }, 503)));
  const user = userEvent.setup();
  render(<StudentImportDialog onClose={vi.fn()} onImported={vi.fn()} />);
  await user.click(screen.getByRole("button", { name: "Descargar plantilla" }));
  expect(await screen.findByText("Plantilla no disponible")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Importar archivo" }).disabled).toBe(false);
});

test("El selector se activa con teclado, anuncia el nombre y permite cambiar el archivo", async () => {
  const user = userEvent.setup();
  render(<StudentImportDialog onClose={vi.fn()} onImported={vi.fn()} />);
  const input = screen.getByLabelText(/Archivo Excel/);
  const chooseFile = screen.getByRole("button", { name: "Seleccionar archivo Excel" });
  const activateInput = vi.spyOn(input, "click");
  chooseFile.focus();
  await user.keyboard("{Enter}");
  expect(activateInput).toHaveBeenCalledOnce();
  await user.upload(input, new File(["contenido"], "estudiantes-segundo-grado.xlsx"));
  expect(screen.getByRole("status").textContent).toBe("estudiantes-segundo-grado.xlsx");
  expect(screen.getByRole("button", { name: "Cambiar archivo" })).toBeTruthy();
  fireEvent.change(input, { target: { files: [] } });
  expect(screen.getByRole("status").textContent).toBe("Ningún archivo seleccionado");
});

test("Un archivo no válido muestra el motivo y dirige el foco al selector antes de enviar", async () => {
  const fetcher = vi.fn();
  vi.stubGlobal("fetch", fetcher);
  const user = userEvent.setup({ applyAccept: false });
  render(<StudentImportDialog onClose={vi.fn()} onImported={vi.fn()} />);
  await user.upload(screen.getByLabelText(/Archivo Excel/), new File(["contenido"], "alumnos.csv"));
  expect(screen.getByRole("alert").textContent).toBe("El archivo debe tener formato .xlsx.");
  await user.click(screen.getByRole("button", { name: "Importar archivo" }));
  expect(document.activeElement).toBe(screen.getByRole("button", { name: "Cambiar archivo" }));
  expect(fetcher).not.toHaveBeenCalled();
});

test("Mientras se importa no permite cambiar de archivo ni cerrar la ventana", async () => {
  let finishImport;
  vi.stubGlobal("fetch", vi.fn(() => new Promise((resolve) => { finishImport = resolve; })));
  const close = vi.fn();
  const user = userEvent.setup();
  render(<StudentImportDialog onClose={close} onImported={vi.fn()} />);
  const input = screen.getByLabelText(/Archivo Excel/);
  await user.upload(input, excel());
  await user.click(screen.getByRole("button", { name: "Importar archivo" }));
  expect(screen.getByRole("button", { name: "Cambiar archivo" }).disabled).toBe(true);
  expect(input.disabled).toBe(true);
  expect(screen.getByRole("button", { name: "Cancelar" }).disabled).toBe(true);
  await user.keyboard("{Escape}");
  expect(close).not.toHaveBeenCalled();
  await act(async () => finishImport(reply({ totalRows: 0, importedRows: 0, rejectedRows: 0, issues: [] })));
  expect(input.disabled).toBe(false);
});

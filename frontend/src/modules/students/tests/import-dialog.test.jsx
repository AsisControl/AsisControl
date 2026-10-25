import { beforeEach, expect, test, vi } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { signIn } from "../../../../tests/support/backend.js";
import { StudentImportDialog, fileSizeLabel } from "@/modules/students/import-dialog";

const XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
const excel = (name = "alumnos.xlsx", size = 24 * 1024) => {
  const file = new File(["x"], name, { type: XLSX });
  Object.defineProperty(file, "size", { value: size });
  return file;
};
const json = (status, body) => ({ status, ok: status < 300, json: async () => body });

/** fakeBackend lee cuerpos JSON; aquí el envío es multipart y la plantilla, binaria. */
function stubFetch(importReply = json(200, { totalRows: 3, importedRows: 2, rejectedRows: 1, issues: [{ row: 4, field: "fila", message: "El correo ya existe" }] })) {
  const fetcher = vi.fn(async (url, options = {}) => {
    const { pathname } = new URL(url);
    if (pathname === "/api/alumnos/importaciones/plantilla")
      return { status: 200, ok: true, blob: async () => new Blob(["xlsx"]) };
    if (pathname === "/api/alumnos/importaciones" && options.method === "POST") return importReply;
    return json(404, { message: `Ruta no simulada: ${pathname}` });
  });
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}

const renderDialog = (props = {}) =>
  render(<StudentImportDialog onClose={vi.fn()} onImported={vi.fn()} {...props} />);
const dropzone = () => screen.getByText(/Arrastra el archivo aquí/).closest("label");

beforeEach(() => {
  signIn({ permissions: ["MATRICULA_LEER", "MATRICULA_ESCRIBIR"] });
});

test("El tamaño del archivo se muestra en B, KB o MB", () => {
  expect(fileSizeLabel(850)).toBe("850 B");
  expect(fileSizeLabel(24 * 1024)).toBe("24 KB");
  expect(fileSizeLabel(1.25 * 1024 * 1024)).toBe("1.3 MB");
});

test("Muestra los dos pasos y una zona para soltar o elegir el Excel", () => {
  stubFetch();
  renderDialog();
  const steps = screen.getAllByRole("listitem");
  expect(steps.map((step) => step.querySelector("p").textContent)).toEqual([
    "Descarga la plantilla",
    "Sube el archivo completado",
  ]);
  const input = screen.getByLabelText(/Arrastra el archivo aquí o selecciónalo/);
  expect(input.type).toBe("file");
  expect(input.accept).toContain(".xlsx");
  expect(screen.getByText("Solo Excel (.xlsx), máximo 5 MB")).toBeTruthy();
});

test("Al elegir el archivo muestra su nombre y tamaño, y se puede cambiar o quitar", async () => {
  stubFetch();
  const user = userEvent.setup();
  renderDialog();
  await user.upload(screen.getByLabelText(/Arrastra el archivo aquí/), excel());
  expect(screen.getByText("alumnos.xlsx")).toBeTruthy();
  expect(screen.getByText("24 KB")).toBeTruthy();
  expect(screen.queryByText(/Arrastra el archivo aquí/)).toBeNull();
  await user.upload(screen.getByLabelText(/Cambiar archivo alumnos.xlsx/), excel("segundo.xlsx", 2048));
  expect(screen.getByText("segundo.xlsx")).toBeTruthy();
  expect(screen.getByText("2 KB")).toBeTruthy();
  await user.click(screen.getByLabelText("Quitar segundo.xlsx"));
  expect(screen.getByText(/Arrastra el archivo aquí/)).toBeTruthy();
  expect(screen.queryByRole("alert")).toBeNull();
});

test("Arrastrar un archivo lo valida al soltarlo", () => {
  stubFetch();
  renderDialog();
  fireEvent.dragOver(dropzone());
  expect(dropzone().className).toContain("dropzoneActive");
  fireEvent.dragLeave(dropzone());
  expect(dropzone().className).not.toContain("dropzoneActive");
  fireEvent.drop(dropzone(), { dataTransfer: { files: [excel("alumnos.csv")] } });
  expect(screen.getByText("alumnos.csv")).toBeTruthy();
  expect(screen.getByRole("alert").textContent).toBe("El archivo debe tener formato .xlsx.");
  fireEvent.click(screen.getByLabelText("Quitar alumnos.csv"));
  fireEvent.drop(dropzone(), { dataTransfer: { files: [excel("grande.xlsx", 6 * 1024 * 1024)] } });
  expect(screen.getByRole("alert").textContent).toBe("El archivo no puede superar 5 MB.");
});

test("Importar sin archivo lo pide y no llama al servidor", async () => {
  const fetcher = stubFetch();
  const user = userEvent.setup();
  renderDialog();
  await user.click(screen.getByText("Importar archivo"));
  expect(screen.getByRole("alert").textContent).toBe("Selecciona el archivo Excel con los estudiantes.");
  expect(dropzone().className).toContain("dropzoneInvalid");
  expect(fetcher).not.toHaveBeenCalled();
});

test("Envía el archivo y muestra el resumen con las filas rechazadas", async () => {
  const fetcher = stubFetch();
  const onImported = vi.fn();
  const user = userEvent.setup();
  renderDialog({ onImported });
  await user.upload(screen.getByLabelText(/Arrastra el archivo aquí/), excel());
  await user.click(screen.getByText("Importar archivo"));
  expect(await screen.findByText("1 fila con error")).toBeTruthy();
  const row = screen.getByText("El correo ya existe").closest("tr");
  expect(within(row).getByText("4")).toBeTruthy();
  expect(onImported).toHaveBeenCalledTimes(1);
  const [, options] = fetcher.mock.calls.at(-1);
  expect(options.body.get("archivo").name).toBe("alumnos.xlsx");
  expect(screen.getByText("Cerrar", { selector: ".form-actions button" })).toBeTruthy();
});

test("Descarga la plantilla con la sesión actual", async () => {
  const fetcher = stubFetch();
  const createObjectURL = vi.fn(() => "blob:plantilla");
  vi.stubGlobal("URL", Object.assign(class extends URL {}, { createObjectURL, revokeObjectURL: vi.fn() }));
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  const user = userEvent.setup();
  renderDialog();
  await user.click(screen.getByText("Descargar plantilla"));
  expect(new URL(fetcher.mock.calls[0][0]).pathname).toBe("/api/alumnos/importaciones/plantilla");
  expect(fetcher.mock.calls[0][1].headers.get("Authorization")).toBe("Bearer test-token");
  expect(createObjectURL).toHaveBeenCalledTimes(1);
  expect(click).toHaveBeenCalledTimes(1);
});

import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { signIn, signOut } from "../../../../tests/support/backend";
import { StudentTasks } from "../student-tasks";
import { TaskDialog } from "../task-dialog";

const file = { id: 71, nombreOriginal: "trabajo.pdf", tamanioBytes: 50, tipoMime: "application/pdf" };
const task = {
  alumnoId: 23, curso: "Matemática", docente: "Saleth Galileo", seccion: "1° Grado A · 2090",
  tarea: { id: 8, nombre: "Fracciones", descripcion: "Resuelve los ejercicios", estado: "PUBLICADA",
    fechaLimite: "2090-03-10T23:59:00", fechaPublicacion: "2020-01-01T00:00:00", archivos: [], maxArchivosEntrega: 2, puntajeMaximo: 20 },
  entrega: null,
};
const delivery = { id: 45, tareaId: 8, alumnoId: 23, archivos: [file], estado: "ENTREGADO",
  fechaHoraEntrega: "2026-11-04T12:00:00", comentario: "Mi trabajo", nota: null, version: 0 };
const page = (content) => ({ content, number: 0, totalElements: content.length, totalPages: 1 });
const response = (body, status = 200) => ({ ok: status < 400, status, json: async () => body });

function backend(handler) {
  const fetch = vi.fn(async (url, options = {}) => handler(new URL(url), options));
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

beforeEach(() => signIn({ role: "ESTUDIANTE", id: "999", permissions: ["TAREAS_LEER", "TAREAS_ENTREGAR", "ARCHIVOS_ESCRIBIR"] }));
afterEach(signOut);

describe("Tareas del estudiante", () => {
  it.each([null, delivery])("bloquea entregas y ediciones al vencer el plazo, antes del cierre automático (%j)", async (entrega) => {
    const expired = { ...task, entrega, tarea: { ...task.tarea, fechaLimite: "2020-03-10T23:59:00" } };
    backend(() => response(page([expired])));
    render(<StudentTasks />);
    fireEvent.click(await screen.findByRole("button", { name: "Ver detalle de Fracciones" }));
    expect(screen.queryByRole("button", { name: "Entregar trabajo" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Editar entrega" })).toBeNull();
    expect(screen.queryByLabelText("Selecciona los archivos de tu trabajo")).toBeNull();
  });

  it("edita agregando y retirando archivos con la versión actual, sin enviar comentarios", async () => {
    const second = { ...file, id: 72, nombreOriginal: "nuevo.pdf" };
    const fetch = backend((url, options) => {
      if (url.pathname === "/api/archivos") return response(second, 201);
      if (options.method === "PUT") {
        expect(JSON.parse(options.body)).toEqual({ archivoIds: [72], version: 0 });
        return response({ ...delivery, archivos: [second], version: 1 });
      }
      return response(delivery);
    });
    const onSaved = vi.fn();
    render(<TaskDialog task={{ ...task, entrega: delivery }} onClose={vi.fn()} onSaved={onSaved} />);
    expect(screen.getByText("Pendiente de calificación")).toBeTruthy();
    expect(screen.queryByText("Mi trabajo")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Editar entrega" }));
    const input = await screen.findByLabelText("Agregar archivos a la entrega");
    expect(screen.getByRole("button", { name: "Guardar cambios" }).disabled).toBe(true);
    fireEvent.click(screen.getByRole("button", { name: "Eliminar trabajo.pdf" }));
    expect(screen.getByRole("button", { name: "Guardar cambios" }).disabled).toBe(true);
    fireEvent.change(input, { target: { files: [new File(["pdf"], "nuevo.pdf")] } });
    fireEvent.click(screen.getByRole("button", { name: "Guardar cambios" }));
    await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
    expect(fetch.mock.calls.filter(([, options]) => options.method === "PUT")).toHaveLength(1);
    expect(fetch.mock.calls.some(([, options]) => options.method === "DELETE")).toBe(false);
  });

  it("permite quitar un adjunto sin subir otro y cancelar no guarda cambios", async () => {
    const original = { ...delivery, archivos: [file, { ...file, id: 72, nombreOriginal: "segundo.pdf" }], version: 1 };
    const fetch = backend((url, options) => options.method === "PUT" ? response({ ...original, archivos: [file], version: 2 }) : response(original));
    const onSaved = vi.fn();
    render(<TaskDialog task={{ ...task, entrega: original }} onClose={vi.fn()} onSaved={onSaved} />);
    fireEvent.click(screen.getByRole("button", { name: "Editar entrega" }));
    fireEvent.click(await screen.findByRole("button", { name: "Eliminar segundo.pdf" }));
    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(screen.getByText("segundo.pdf", { exact: false })).toBeTruthy();
    expect(fetch.mock.calls.some(([, options]) => options.method === "PUT")).toBe(false);
    fireEvent.click(screen.getByRole("button", { name: "Editar entrega" }));
    fireEvent.click(await screen.findByRole("button", { name: "Eliminar segundo.pdf" }));
    fireEvent.click(screen.getByRole("button", { name: "Guardar cambios" }));
    await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
    const put = fetch.mock.calls.find(([, options]) => options.method === "PUT");
    expect(JSON.parse(put[1].body)).toEqual({ archivoIds: [71], version: 1 });
    expect(fetch.mock.calls.some(([url]) => url.endsWith("/api/archivos"))).toBe(false);
  });

  it("un conflicto no confirma la edición y permite recargar la nota más reciente", async () => {
    let conflict = false;
    backend((url, options) => {
      if (options.method === "PUT") { conflict = true; return response({ message: "La entrega fue modificada", code: "VERSION_DESACTUALIZADA" }, 409); }
      return response(conflict ? { ...delivery, estado: "CALIFICADO", nota: 0, version: 1 } : {
        ...delivery, archivos: [file, { ...file, id: 72, nombreOriginal: "segundo.pdf" }],
      });
    });
    const onSaved = vi.fn();
    render(<TaskDialog task={{ ...task, entrega: delivery }} onClose={vi.fn()} onSaved={onSaved} />);
    fireEvent.click(screen.getByRole("button", { name: "Editar entrega" }));
    fireEvent.click(await screen.findByRole("button", { name: "Eliminar segundo.pdf" }));
    fireEvent.click(screen.getByRole("button", { name: "Guardar cambios" }));
    await screen.findByText("La entrega fue modificada");
    expect(onSaved).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Guardar cambios" }).disabled).toBe(true);
    fireEvent.click(screen.getByRole("button", { name: "Recargar entrega" }));
    await screen.findByText("0 / 20");
    expect(screen.queryByRole("button", { name: "Editar entrega" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Guardar cambios" })).toBeNull();
  });

  it("carga datos reales, sube el archivo y registra el alumno del perfil, no el id del usuario", async () => {
    let saved = false;
    const fetch = backend((url, options) => {
      expect(options.headers.get("Authorization")).toBe("Bearer test-token");
      if (url.pathname === "/api/alumnos/me/tareas") return response(page([{ ...task, entrega: saved ? delivery : null }]));
      if (url.pathname === "/api/archivos") {
        expect(options.body).toBeInstanceOf(FormData);
        expect(options.body.get("archivo").name).toBe("trabajo.pdf");
        expect(options.headers.has("Content-Type")).toBe(false);
        return response(file, 201);
      }
      expect(url.pathname).toBe("/api/tareas/8/entregas");
      expect(JSON.parse(options.body)).toEqual({ alumnoId: 23, archivoIds: [71] });
      saved = true; return response(delivery, 201);
    });
    render(<StudentTasks />);
    fireEvent.click(await screen.findByRole("button", { name: "Entregar Fracciones" }));
    const send = screen.getByRole("button", { name: "Entregar trabajo" });
    expect(send.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText("Selecciona los archivos de tu trabajo"), { target: { files: [new File(["%PDF"], "trabajo.pdf", { type: "application/pdf" })] } });
    expect(screen.queryByLabelText("Comentario para el docente")).toBeNull();
    fireEvent.click(send);
    await screen.findByText("Tu trabajo se entregó correctamente.");
    await screen.findByRole("button", { name: "Ver detalle de Fracciones" });
    expect(fetch.mock.calls.filter(([url, options]) => url.endsWith("/entregas") && options.method === "POST")).toHaveLength(1);
  });

  it("conserva el formulario y reutiliza archivos cargados al reintentar un error", async () => {
    let fail = true;
    const fetch = backend((url, options) => {
      if (url.pathname === "/api/archivos") return response(file, 201);
      if (options.method !== "POST") return response(page([]));
      return fail ? response({ message: "No se pudo guardar" }, 503) : response(delivery, 201);
    });
    const onSaved = vi.fn();
    render(<TaskDialog task={task} onClose={vi.fn()} onSaved={onSaved} />);
    fireEvent.change(screen.getByLabelText("Selecciona los archivos de tu trabajo"), { target: { files: [new File(["%PDF"], "trabajo.pdf")] } });
    fireEvent.click(screen.getByRole("button", { name: "Entregar trabajo" }));
    await screen.findByText("No se pudo guardar");
    expect(onSaved).not.toHaveBeenCalled();
    fail = false;
    fireEvent.click(screen.getByRole("button", { name: "Entregar trabajo" }));
    await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
    expect(fetch.mock.calls.filter(([url]) => url.endsWith("/api/archivos"))).toHaveLength(1);
  });

  it("no registra entregas cuando falla la carga y valida cantidad y tamaño antes de enviar", async () => {
    const fetch = backend(() => response({ message: "Archivo rechazado" }, 422));
    const onSaved = vi.fn();
    render(<TaskDialog task={task} onClose={vi.fn()} onSaved={onSaved} />);
    const input = screen.getByLabelText("Selecciona los archivos de tu trabajo");
    fireEvent.change(input, { target: { files: [new File([], "vacio.pdf")] } });
    await screen.findByText(/Cada archivo debe tener contenido/);
    fireEvent.change(input, { target: { files: [1, 2, 3].map((n) => new File(["pdf"], `${n}.pdf`)) } });
    await screen.findByText("Puedes adjuntar hasta 2 archivos.");
    expect(fetch).not.toHaveBeenCalled();
    fireEvent.change(input, { target: { files: [new File(["pdf"], "trabajo.pdf")] } });
    fireEvent.click(screen.getByRole("button", { name: "Entregar trabajo" }));
    await screen.findByText("Archivo rechazado");
    expect(onSaved).not.toHaveBeenCalled();
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("recupera una entrega guardada cuando se pierde su respuesta", async () => {
    const onSaved = vi.fn();
    backend((url, options) => url.pathname === "/api/archivos" ? response(file, 201) : options.method === "POST" ? response({ message: "Tiempo agotado" }, 504) : response(page([delivery])));
    render(<TaskDialog task={task} onClose={vi.fn()} onSaved={onSaved} />);
    fireEvent.change(screen.getByLabelText("Selecciona los archivos de tu trabajo"), { target: { files: [new File(["pdf"], "trabajo.pdf")] } });
    fireEvent.click(screen.getByRole("button", { name: "Entregar trabajo" }));
    await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
  });

  it("consulta los filtros en el servidor y permite reintentar si falla la lista", async () => {
    let fail = true;
    const fetch = backend(() => fail ? response({ message: "Servicio no disponible" }, 503) : response(page([task])));
    render(<StudentTasks />);
    await screen.findByText("Servicio no disponible");
    fail = false; fireEvent.click(screen.getByRole("button", { name: "Reintentar" }));
    await screen.findByText("Fracciones");
    fireEvent.change(screen.getByLabelText("Buscar tarea o curso"), { target: { value: "Matemática" } });
    fireEvent.change(screen.getByLabelText("Filtrar por estado"), { target: { value: "submitted" } });
    await waitFor(() => {
      const query = new URL(fetch.mock.calls.at(-1)[0]).searchParams;
      expect(query.get("busqueda")).toBe("Matemática");
      expect(query.get("entregada")).toBe("true");
      expect(query.get("page")).toBe("0");
    });
  });

  it("muestra nota y descarga autenticada; una tarea cerrada no ofrece entregar", async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    URL.createObjectURL = vi.fn(() => "blob:archivo");
    URL.revokeObjectURL = vi.fn();
    const fetch = backend(() => ({ ok: true, status: 200, blob: async () => new Blob(["pdf"]) }));
    const { rerender } = render(<TaskDialog task={{ ...task, entrega: { ...delivery, estado: "CALIFICADO", nota: 18, comentario: "Buen trabajo. Revisa el segundo ejercicio." } }} onClose={vi.fn()} onSaved={vi.fn()} />);
    expect(screen.getByText("18 / 20")).toBeTruthy();
    expect(screen.getByRole("region", { name: "Comentario del profesor" }).textContent).toContain("Buen trabajo. Revisa el segundo ejercicio.");
    expect(screen.queryByRole("button", { name: "Entregar trabajo" })).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Descargar" }));
    await waitFor(() => expect(click).toHaveBeenCalledOnce());
    expect(fetch.mock.calls[0][1].headers.get("Authorization")).toBe("Bearer test-token");
    rerender(<TaskDialog task={{ ...task, tarea: { ...task.tarea, estado: "CERRADA" } }} onClose={vi.fn()} onSaved={vi.fn()} />);
    expect(screen.getByText("Esta tarea no está recibiendo entregas.")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Entregar trabajo" })).toBeNull();
  });
});

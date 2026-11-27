import { expect, test, vi } from "vitest";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { usePathname } from "next/navigation";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { currentUrl, setUrl } from "../../../../tests/support/navigation.js";
import { PublishedEvaluation } from "@/modules/evaluations/published-evaluation";
import { NotificationBell } from "../notification-bell";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const notice = {
  id: 8, titulo: "Resultado actualizado", mensaje: "Se corrigió una nota", leida: false,
  urlDestino: "/progreso/evaluaciones/21/7", fechaCreacion: "2026-11-20T14:00:00Z",
};

function Portal() {
  const [, , , evaluation, student, readId] = usePathname().split("/");
  return <>
    <NotificationBell />
    {evaluation && <PublishedEvaluation evaluation={evaluation} student={student} notice={readId} />}
  </>;
}

test("abre la nota vigente y solo después actualiza el contador", async () => {
  signIn({ role: "APODERADO" });
  setUrl("/progreso");
  let unread = 1;
  const calls = fakeBackend({
    "GET /api/notificaciones/resumen": () => ({ noLeidas: unread }),
    "GET /api/notificaciones": page([notice]),
    "GET /api/evaluaciones/21/resultado-publicado": {
      titulo: "Examen parcial", curso: "Matemática", estudiante: "Eva Flores", fechaEvaluacion: "2026-11-20",
      notas: [{ idCompetencia: 1, competencia: "Resuelve problemas", nivelLogro: "A", notaNumerica: null }],
    },
    "PUT /api/notificaciones/8/leida": () => { unread = 0; return reply(204); },
  });
  render(<Portal />);
  await userEvent.click(await screen.findByRole("button", { name: "Notificaciones, 1 sin leer" }));
  await userEvent.click(await screen.findByRole("button", { name: "Abrir" }));
  expect(await screen.findByRole("heading", { name: "Examen parcial" })).toBeTruthy();
  expect(screen.getByText("A")).toBeTruthy();
  await screen.findByRole("button", { name: "Notificaciones", exact: true });
  expect(calls.filter(({ path }) => path.includes("resultado-publicado") || path.endsWith("/leida")))
    .toEqual([
      expect.objectContaining({ method: "GET", query: { alumnoId: "7" } }),
      expect.objectContaining({ method: "PUT", path: "/api/notificaciones/8/leida" }),
    ]);
});

test("si el vínculo fue revocado ofrece volver a Progreso y conserva el aviso sin leer", async () => {
  signIn({ role: "APODERADO" });
  setUrl("/progreso");
  const calls = fakeBackend({
    "GET /api/notificaciones/resumen": { noLeidas: 1 },
    "GET /api/notificaciones": page([notice]),
    "GET /api/evaluaciones/21/resultado-publicado": reply(403),
  });
  render(<Portal />);
  await userEvent.click(await screen.findByRole("button", { name: "Notificaciones, 1 sin leer" }));
  await userEvent.click(await screen.findByRole("button", { name: "Abrir" }));
  await screen.findByText(/evaluación ya no está disponible/);
  expect(screen.getByRole("link", { name: "Volver a Progreso" }).getAttribute("href")).toBe("/progreso");
  expect(calls.some(({ method }) => method === "PUT")).toBe(false);
});

test("permite marcar un aviso como leído desde la bandeja", async () => {
  signIn({ role: "APODERADO" });
  let unread = 1;
  fakeBackend({
    "GET /api/notificaciones/resumen": () => ({ noLeidas: unread }),
    "GET /api/notificaciones": () => page([{ ...notice, leida: !unread }]),
    "PUT /api/notificaciones/8/leida": () => { unread = 0; return reply(204); },
  });
  render(<NotificationBell />);
  await userEvent.click(await screen.findByRole("button", { name: "Notificaciones, 1 sin leer" }));
  await userEvent.click(await screen.findByRole("button", { name: "Marcar como leída" }));
  await waitFor(() => expect(screen.queryByRole("button", { name: "Marcar como leída" })).toBeNull());
  expect(await screen.findByText("0 avisos sin leer")).toBeTruthy();
});

test("abre avisos anteriores y ofrece marcar el resto como leídos", async () => {
  signIn({ role: "APODERADO" });
  setUrl("/");
  const notices = [
    { ...notice, id: 10, titulo: "Aviso de asistencia", urlDestino: "/asistencia" },
    { ...notice, id: 11, titulo: "Aviso general", urlDestino: null },
  ];
  const calls = fakeBackend({
    "GET /api/notificaciones/resumen": () => ({ noLeidas: notices.filter((item) => !item.leida).length }),
    "GET /api/notificaciones": () => page(notices),
    "PUT /api/notificaciones/10/leida": () => { notices[0].leida = true; return reply(204); },
    "PUT /api/notificaciones/lectura": () => {
      notices.forEach((item) => { item.leida = true; });
      return reply(204);
    },
  });
  const user = userEvent.setup();
  render(<NotificationBell />);
  await user.click(await screen.findByRole("button", { name: "Notificaciones, 2 sin leer" }));
  const entry = (await screen.findByText("Aviso de asistencia")).closest("li");
  await user.click(within(entry).getByRole("button", { name: "Abrir" }));
  await waitFor(() => expect(currentUrl()).toBe("/asistencia"));
  await user.click(await screen.findByRole("button", { name: "Notificaciones, 1 sin leer" }));
  await screen.findByText("Aviso general");
  await user.click(screen.getByRole("button", { name: "Marcar todas como leídas" }));
  await screen.findByText("0 avisos sin leer");
  expect(calls.filter(({ method }) => method === "PUT").map(({ path }) => path))
    .toEqual(["/api/notificaciones/10/leida", "/api/notificaciones/lectura"]);
});

test("consulta el contador cada treinta segundos solo si la pestaña está visible", async () => {
  signIn({ role: "APODERADO" });
  const visibility = vi.spyOn(document, "visibilityState", "get").mockReturnValue("visible");
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
  try {
    let unread = 1;
    const calls = fakeBackend({ "GET /api/notificaciones/resumen": () => ({ noLeidas: unread }) });
    const view = render(<NotificationBell />);
    await screen.findByRole("button", { name: "Notificaciones, 1 sin leer" });
    visibility.mockReturnValue("hidden");
    unread = 2;
    await act(() => vi.advanceTimersByTimeAsync(30_000));
    expect(calls).toHaveLength(1);
    visibility.mockReturnValue("visible");
    await act(() => vi.advanceTimersByTimeAsync(30_000));
    await screen.findByRole("button", { name: "Notificaciones, 2 sin leer" });
    view.unmount();
    await act(() => vi.advanceTimersByTimeAsync(30_000));
    expect(calls).toHaveLength(2);
  } finally {
    vi.useRealTimers();
  }
});

test("muestra el error del contador y permite volver a consultar desde la bandeja", async () => {
  signIn({ role: "APODERADO" });
  let available = false;
  fakeBackend({
    "GET /api/notificaciones/resumen": () => available ? { noLeidas: 1 } : reply(503),
    "GET /api/notificaciones": page([notice]),
  });
  const view = render(<NotificationBell />);
  await userEvent.click(await screen.findByRole("button", { name: "Notificaciones, contador sin actualizar" }));
  const dialog = screen.getByRole("dialog");
  expect(view.container.contains(dialog)).toBe(false);
  expect(within(dialog).getByText("No se pudo actualizar el contador de notificaciones.")).toBeTruthy();
  available = true;
  await userEvent.click(within(dialog).getByRole("button", { name: "Reintentar contador" }));
  expect(await within(dialog).findByText("1 aviso sin leer")).toBeTruthy();
});

test("permite recuperar la nota y volver a marcar el aviso sin ocultar la calificación cargada", async () => {
  signIn({ role: "APODERADO" });
  let canLoad = false;
  let canMark = false;
  const result = {
    titulo: "Examen parcial", curso: "Matemática", estudiante: "Eva Flores", fechaEvaluacion: "2026-11-20",
    notas: [{ idCompetencia: 1, competencia: "Resuelve problemas", nivelLogro: "A" }],
  };
  fakeBackend({
    "GET /api/evaluaciones/21/resultado-publicado": () => canLoad ? result : reply(503),
    "PUT /api/notificaciones/8/leida": () => canMark ? reply(204) : reply(503),
  });
  render(<PublishedEvaluation evaluation="21" student="7" notice="8" />);
  const load = await screen.findByRole("button", { name: "Reintentar carga de la evaluación" });
  canLoad = true;
  await userEvent.click(load);
  const read = await screen.findByRole("button", { name: "Reintentar marcado" });
  expect(screen.getByRole("table", { name: "Calificación publicada" })).toBeTruthy();
  canMark = true;
  await userEvent.click(read);
  await waitFor(() => expect(screen.queryByRole("button", { name: "Reintentar marcado" })).toBeNull());
  expect(screen.getByText("A")).toBeTruthy();
});

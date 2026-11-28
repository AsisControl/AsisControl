import { expect, test } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { TeacherContingencies } from "../teacher-check-in/teacher-contingencies";

const path = "/api/asistencias/docentes/contingencias";
const pending = {
  id: 4, docente: "Rosa Pizarro", fechaJornada: "2026-11-20", horaReportada: "07:40",
  motivo: "El lector no respondió", estado: "PENDIENTE", fechaEnvio: "2026-11-20T13:00:00Z",
};

test("la administración aprueba según el horario o elige un estado cuando no hay horario", async () => {
  signIn();
  let items = [{ ...pending, estadoSugerido: "TARDANZA" },
    { ...pending, id: 5, docente: "Diego Ramos", estadoSugerido: null }];
  const calls = fakeBackend({
    [`GET ${path}`]: ({ query }) => page(items.filter((item) => !query.estado || query.estado === item.estado)),
    [`POST ${path}/:id/resoluciones`]: ({ path: route, body }) => {
      const id = Number(route.split("/").at(-2));
      items = items.map((item) => item.id === id ? {
        ...item, estado: "APROBADA", fechaResolucion: "2026-11-20T15:30:00Z",
        estadoRegistrado: item.estadoSugerido ?? body.estadoSinVentana,
      } : item);
      return items.find((item) => item.id === id);
    },
  });
  const user = userEvent.setup();
  render(<TeacherContingencies administration canWrite />);
  const rosa = (await screen.findByText("Rosa Pizarro")).closest("tr");
  await user.click(within(rosa).getByRole("button", { name: "Aprobar" }));
  let dialog = within(screen.getByRole("dialog"));
  expect(dialog.getByText("Tardanza")).toBeTruthy();
  expect(dialog.queryByLabelText(/Estado de la marcación/)).toBeNull();
  await user.click(dialog.getByRole("button", { name: "Aprobar solicitud" }));
  await screen.findByText(/Se registró la marcación docente/);
  const diego = (await screen.findByText("Diego Ramos")).closest("tr");
  await user.click(within(diego).getByRole("button", { name: "Aprobar" }));
  dialog = within(screen.getByRole("dialog"));
  await user.selectOptions(dialog.getByLabelText(/Estado de la marcación/), "PRESENTE");
  await user.click(dialog.getByRole("button", { name: "Aprobar solicitud" }));
  await screen.findByText("No hay solicitudes para este estado.");
  await user.selectOptions(screen.getByLabelText("Estado"), "APROBADA");
  await screen.findByText("Marcación: Presente");
  expect(calls.filter(({ method }) => method === "POST").map(({ body }) => body))
    .toEqual([
      { decision: "APROBAR", estadoSinVentana: null },
      { decision: "APROBAR", estadoSinVentana: "PRESENTE" },
    ]);
});

test("si la marcación QR se registró después, el conflicto se muestra y se puede rechazar con motivo", async () => {
  signIn();
  let item = { ...pending, estadoSugerido: "PRESENTE" };
  fakeBackend({
    [`GET ${path}`]: ({ query }) => page(!query.estado || query.estado === item.estado ? [item] : []),
    [`POST ${path}/4/resoluciones`]: ({ body }) => {
      if (body.decision === "APROBAR") {
        return reply(409, { message: "El docente ya tiene asistencia para la jornada indicada" });
      }
      item = { ...item, estado: "RECHAZADA", motivoRechazo: body.motivo };
      return item;
    },
  });
  const user = userEvent.setup();
  render(<TeacherContingencies administration canWrite />);
  await user.click(await screen.findByRole("button", { name: "Aprobar" }));
  await user.click(screen.getByRole("button", { name: "Aprobar solicitud" }));
  await screen.findByText(/ya tiene asistencia/);
  await user.click(screen.getByRole("button", { name: "Cancelar" }));
  await user.click(screen.getByRole("button", { name: "Rechazar" }));
  const dialog = within(screen.getByRole("dialog"));
  await user.type(dialog.getByLabelText(/Motivo del rechazo/), "La marcación por QR está registrada");
  await user.click(dialog.getByRole("button", { name: "Rechazar solicitud" }));
  await screen.findByText("Solicitud rechazada.");
  await user.selectOptions(screen.getByLabelText("Estado"), "RECHAZADA");
  await screen.findByText(/Motivo del rechazo: La marcación por QR está registrada/);
});

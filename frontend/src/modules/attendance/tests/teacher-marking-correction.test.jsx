// correccion marcacion docente
import { beforeEach, expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, signIn } from "../../../../tests/support/backend.js";
import { AttendanceReports } from "../reports/attendance-reports";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const marcacion = { id: 5, fechaJornada: "2026-11-19", fechaHoraMarcada: "2026-11-19T08:10:00", estado: "TARDANZA", version: 2 };

function backend() {
  let saved = false;
  return fakeBackend({
    "GET /api/reportes/asistencia/filtros": { periodos: [], secciones: [] },
    "GET /api/reportes/asistencia": () => ({
      total: 1, indicadores: [{ estado: saved ? "PRESENTE" : "TARDANZA", cantidad: 1, porcentaje: 100 }],
      pagina: 0, totalPaginas: 1, totalDetalle: 1,
      registros: [{ id: 5, personaId: 9, persona: "Ana Rojas", fecha: "2026-11-19", hora: saved ? "07:55:00" : "08:10:00",
        curso: null, seccion: null, estado: saved ? "PRESENTE" : "TARDANZA", origen: "REGISTRO", sesionId: null }],
    }),
    "GET /api/asistencias/docentes/5/correcciones": () => saved
      ? { marcacion: { ...marcacion, estado: "PRESENTE", fechaHoraMarcada: "2026-11-19T07:55:00", version: 3 },
        correcciones: [{ id: 1, horaAnterior: "2026-11-19T08:10:00", horaNueva: "2026-11-19T07:55:00",
          estadoAnterior: "TARDANZA", estadoNuevo: "PRESENTE", motivo: "Falla del lector QR", actor: "Admin Uno",
          fechaHora: "2026-11-20T09:00:00" }] }
      : { marcacion, correcciones: [] },
    "PUT /api/asistencias/docentes/5/correccion": () => { saved = true; return { ...marcacion, estado: "PRESENTE", version: 3 }; },
  });
}

beforeEach(() => signIn({ permissions: ["ASISTENCIA_LEER", "ASISTENCIA_ESCRIBIR"] }));

async function abrirDetalle(user) {
  await user.selectOptions(screen.getByLabelText("Tipo de reporte"), "DOCENTES");
  await user.click(await screen.findByRole("button", { name: "Ver detalle de Ana Rojas" }));
}

test("el administrador corrige la hora de una marcación docente con motivo y ve el valor anterior", async () => {
  const user = userEvent.setup(), calls = backend();
  render(<AttendanceReports canCorrect />);
  await abrirDetalle(user);
  await user.click(await screen.findByRole("button", { name: "Corregir marcación" }));
  const guardar = screen.getByRole("button", { name: "Guardar corrección" });
  expect(guardar.disabled).toBe(true);
  const hora = screen.getByLabelText("Hora de marcación");
  await user.clear(hora);
  await user.type(hora, "07:55");
  expect(guardar.disabled).toBe(true);
  await user.type(screen.getByLabelText("Motivo de la corrección"), "  Falla del lector QR ");
  await user.click(guardar);
  expect(await screen.findByText("Corrección de la marcación guardada. El reporte se actualizó.")).toBeTruthy();
  expect(calls.find(c => c.method === "PUT").body).toEqual({ hora: "07:55", estado: null, motivo: "Falla del lector QR", version: 2 });

  await user.click(await screen.findByRole("button", { name: "Ver detalle de Ana Rojas" }));
  expect(await screen.findByText("08:10 · Tardanza")).toBeTruthy();
  expect(screen.getByText("07:55 · Presente")).toBeTruthy();
  expect(screen.getByText("Falla del lector QR")).toBeTruthy();
});

test("sin permiso de corrección solo se consulta la marcación", async () => {
  const user = userEvent.setup();
  backend();
  render(<AttendanceReports />);
  await abrirDetalle(user);
  await screen.findByRole("dialog", { name: "Detalle de asistencia" });
  await screen.findByText("Registro vigente de asistencia");
  expect(screen.queryByRole("button", { name: "Corregir marcación" })).toBeNull();
});

import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import { ProgressReports } from "../progress-reports";
import { fakeBackend, reply, signIn } from "../../../../tests/support/backend.js";

const evidencePath = "/api/calificaciones/informes/41/filas/203/evidencias";
const attachmentPath = `${evidencePath}/51/archivo`;
const report = {
  periodo: "Primer bimestre | Version 1 | PUBLICADA",
  filas: [{
    idInforme: 41, idDetalleInforme: 203, competencia: "Resuelve problemas",
    evaluacion: "Práctica", fecha: "15/03/2026", resultado: "A",
    ponderacion: "30 %", observacion: "Buen avance",
  }],
};

function routes(detail) {
  return {
    "GET /api/calificaciones/informes-disponibles": [{
      idAlumno: 8, alumno: "Ana", idAsignacionCurso: 5, curso: "Matemática",
      anio: 2026, idPeriodoAcademico: 40, periodo: "Primer bimestre",
    }],
    "GET /api/familia/estudiantes": [{ id: 8, nombreCompleto: "Ana" }],
    "GET /api/calificaciones/avance": detail,
  };
}

async function openReport(guardian) {
  const user = userEvent.setup();
  render(<ProgressReports guardian={guardian} />);
  await screen.findByRole("option", { name: "Primer bimestre · 2026" });
  await user.selectOptions(screen.getByLabelText("Período académico"), "2026-40");
  await user.selectOptions(screen.getByLabelText("Curso"), "5");
  await user.click(screen.getByRole("button", { name: "Ver notas" }));
  return user;
}

test("el estudiante consulta el sustento de la fila y descarga el adjunto con su sesión", async () => {
  signIn({ role: "ESTUDIANTE", permissions: ["CALIFICACIONES_LEER"] });
  const calls = fakeBackend({
    ...routes(report),
    [`GET ${evidencePath}`]: { evidencias: [
      { idEvidencia: 51, descripcion: "Trabajo de clase", archivo: { nombreOriginal: "Trabajo.pdf" } },
      { idEvidencia: 52, descripcion: "Nota del docente", archivo: null },
    ] },
    [`GET ${attachmentPath}`]: {},
  });
  const previousCreate = URL.createObjectURL;
  const previousRevoke = URL.revokeObjectURL;
  URL.createObjectURL = vi.fn(() => "blob:trabajo");
  URL.revokeObjectURL = vi.fn();
  const link = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  try {
    const user = await openReport(false);
    const action = await screen.findByRole("button", { name: /^Ver sustento de Resuelve problemas/ });
    expect(calls.some(call => call.path === evidencePath)).toBe(false);
    await user.click(action);
    await screen.findByText("Trabajo de clase");
    expect(screen.getByText("Nota del docente")).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "Descargar Trabajo.pdf" }));
    await waitFor(() => expect(link.mock.instances[0]?.download).toBe("Trabajo.pdf"));
    expect(calls.at(-1).path).toBe(attachmentPath);
    const request = fetch.mock.calls.find(([url]) => String(url).endsWith(attachmentPath));
    expect(new Headers(request[1].headers).get("Authorization")).toBe("Bearer test-token");
  } finally {
    link.mockRestore();
    URL.createObjectURL = previousCreate;
    URL.revokeObjectURL = previousRevoke;
  }
});

test.each([
  [{ evidencias: [] }, "No hay sustento disponible para esta competencia."],
  [reply(403), "No tienes permiso para consultar este sustento."],
])("el apoderado distingue sustento vacío de acceso denegado", async (response, message) => {
  signIn({ role: "APODERADO", permissions: ["CALIFICACIONES_LEER"] });
  fakeBackend({ ...routes(report), [`GET ${evidencePath}`]: response });
  const user = await openReport(true);
  await user.click(await screen.findByRole("button", { name: /^Ver sustento de/ }));
  await screen.findByText(message);
  expect(screen.queryByRole("button", { name: /^Descargar archivo/ })).toBeNull();
});

test("la libreta familiar no revela las filas de un borrador", async () => {
  const calls = fakeBackend(routes({ ...report, periodo: "Primer bimestre | Version 1 | BORRADOR" }));
  await openReport(false);
  await screen.findByText("No hay una libreta publicada para este período y curso.");
  expect(screen.queryByText("Buen avance")).toBeNull();
  expect(screen.queryByRole("button", { name: /^Ver sustento de/ })).toBeNull();
  expect(calls.some(call => call.path === evidencePath)).toBe(false);
});

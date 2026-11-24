import { expect, test, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { useEvidenceContext } from "../use-evidence-context";
import { listarPeriodos, listarEstudiantes } from "../services/evidence-service";
import { listarCompetenciasDeAsignacion } from "../../services/evaluation-service";

vi.mock("../services/evidence-service", () => ({ listarPeriodos: vi.fn(), listarEstudiantes: vi.fn() }));
vi.mock("../../services/evaluation-service", () => ({ listarCompetenciasDeAsignacion: vi.fn() }));

const assignmentA = { id: 1, cursoId: 100 };
const assignmentB = { id: 2, cursoId: 100 };

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

/** Cada asignación recibe su propia respuesta controlada a mano (períodos, competencias, estudiantes). */
function control(byAssignment) {
  listarPeriodos.mockImplementation((id) => byAssignment[id].periods);
  listarCompetenciasDeAsignacion.mockImplementation((id) => byAssignment[id].competencies);
  listarEstudiantes.mockImplementation((id) => byAssignment[id].students);
}
const settled = (value) => Promise.resolve(value);
const data = (tag) => ({
  periods: settled([{ id: 1, nombre: `P-${tag}` }]),
  competencies: settled([]),
  students: settled([{ id: 1, nombre: `E-${tag}` }]),
});

test("Una respuesta tardía de la asignación anterior no pisa el contexto de la nueva", async () => {
  const slow = deferred();
  control({ 1: { ...data("A"), periods: slow.promise }, 2: data("B") });
  const { result, rerender } = renderHook(({ assignment }) => useEvidenceContext(assignment), {
    initialProps: { assignment: assignmentA },
  });
  rerender({ assignment: assignmentB });
  await waitFor(() => expect(result.current.context?.periods[0].nombre).toBe("P-B"));
  await act(async () => {
    slow.resolve([{ id: 9, nombre: "P-A-tardío" }]);
    await slow.promise;
  });
  expect(result.current.context?.assignmentId).toBe(2);
  expect(result.current.context?.periods[0].nombre).toBe("P-B");
  expect(result.current.loading).toBe(false);
});

test("Un fallo tardío de la asignación anterior no borra el error de la nueva", async () => {
  const slow = deferred();
  control({
    1: { ...data("A"), periods: slow.promise },
    2: { ...data("B"), periods: Promise.reject(new Error("Fallo de la asignación B")) },
  });
  const { result, rerender } = renderHook(({ assignment }) => useEvidenceContext(assignment), {
    initialProps: { assignment: assignmentA },
  });
  rerender({ assignment: assignmentB });
  await waitFor(() => expect(result.current.error).toBe("Fallo de la asignación B"));
  await act(async () => {
    slow.reject(new Error("Fallo tardío de A"));
    await slow.promise.catch(() => {});
  });
  expect(result.current.error).toBe("Fallo de la asignación B");
  expect(result.current.loading).toBe(false);
});

test("Si la carga falla, expone el mensaje de error y deja de cargar", async () => {
  control({ 1: { ...data("A"), students: Promise.reject(new Error("No se pudo cargar")) } });
  const { result } = renderHook(() => useEvidenceContext(assignmentA));
  await waitFor(() => expect(result.current.error).toBe("No se pudo cargar"));
  expect(result.current.context).toBeNull();
  expect(result.current.loading).toBe(false);
});

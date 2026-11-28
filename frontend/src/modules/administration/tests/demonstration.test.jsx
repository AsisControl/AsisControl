import { beforeEach, expect, test, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, reply, signIn } from "../../../../tests/support/backend.js";
import { Administration } from "@/modules/administration";
import { DemoPanel } from "../demonstration/demo-panel";
import { FILES } from "../demonstration/demo-service";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));
const PATH = "/api/administracion/demostracion";
const result = { total: 2, validas: 2, creadas: 2, errores: [], omitidas: [] };
const credential = (role, name) => ({ row: 2, fullName: name, role, code: role + "20260001", accessCode: "20260002",
  username: name.toLowerCase(), email: "demo@asiscontrol.test", temporaryPassword: "TemporalDemo123", existing: false, expiresAt: null });
const familyResult = { saved: true, valid: true, files: {
  estudiantes: { error: null, result: { totalRows: 12, importedRows: 12, rejectedRows: 0, issues: [], credentials: [credential("ALUMNO", "Mía")] } },
  apoderados: { error: null, result: { totalRows: 11, importedRows: 11, rejectedRows: 0, issues: [], credentials: [credential("APODERADO", "Carmen")] } },
  parentescos: { error: null, result: { totalRows: 12, importedRows: 12, rejectedRows: 0, issues: [] } },
} };
const stage = etapa => ({ etapa, recursos: etapa === "ESCENARIOS" ? { evaluacionMat2: 501, evaluacionMat3: 502 } : {},
  fechaReferencia: "2026-11-27", indicaciones: [] });
const sheet = count => ({ evaluacion: { estado: "BORRADOR" }, alumnos: [{ notas: Array.from({ length: count }, (_, i) => ({ idCalificacion: i + 1 })) }] });
beforeEach(() => signIn());

function backend(extra = {}, initial = []) {
  const etapas = [...initial];
  const notes = { 501: 0, 502: 0 };
  const routes = {
    [`GET ${PATH}`]: () => ({ paquete: "EXPO-2026-v1", etapas }),
    [`POST ${PATH}`]: call => { const value = stage(call.query.etapa); etapas.push(value); return value; },
    "POST /api/alumnos/importaciones/familias": familyResult,
    "POST /api/docentes/importaciones": { totalRows: 2, importedRows: 2, rejectedRows: 0, issues: [], credentials: [credential("DOCENTE", "Patricia")] },
    "POST /api/evaluaciones/501/calificaciones/importaciones": () => { notes[501] = 9; return result; },
    "POST /api/evaluaciones/502/calificaciones/importaciones": () => { notes[502] = 10; return result; },
    "GET /api/evaluaciones/501/planilla": () => sheet(notes[501]),
    "GET /api/evaluaciones/502/planilla": () => sheet(notes[502]),
  };
  FILES.filter(f => f.path && f.number !== 4).forEach(f => { routes[`POST ${f.path}`] = result; });
  return fakeBackend({ ...routes, ...extra });
}
async function open() {
  await userEvent.click(await screen.findByRole("button", { name: "Cargar datos de demostración" }));
}
async function selectAll() {
  const files = FILES.map(spec => new File(["excel"], spec.name, { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" }));
  await userEvent.upload(screen.getByLabelText("Seleccionar los Excel y SQL de demostración"), files);
}

test("Administración integra la carga y muestra cómo habilitarla si el servidor la desactiva", async () => {
  const calls = fakeBackend({ [`GET ${PATH}`]: reply(404, {}) });
  render(<Administration currentUserId="1" tab="demostracion" />);
  expect(await screen.findByRole("alert")).toHaveProperty("textContent", expect.stringContaining("DEMO_DATA_ENABLED=true"));
  expect(screen.queryByRole("button", { name: "Cargar datos de demostración" })).toBeNull();
  await userEvent.click(screen.getByRole("button", { name: "Volver a comprobar" }));
  await waitFor(() => expect(calls).toHaveLength(2));
  expect(screen.getByRole("button", { name: "Preparar demostración" }).getAttribute("aria-current")).toBe("page");
});

test("ejecuta la secuencia real, une los tres archivos de familia y conserva las credenciales", async () => {
  const calls = backend(); render(<DemoPanel />); await open(); await selectAll();
  await userEvent.click(screen.getByRole("button", { name: "Iniciar carga" }));
  await screen.findByText("Carga completada. Ya puedes recorrer los flujos de demostración.");
  const writes = calls.filter(c => c.method === "POST");
  expect(writes.map(c => c.path === PATH ? c.query.etapa : c.path)).toEqual([
    "INICIAL", "/api/alumnos/importaciones/familias", ...FILES.filter(f => f.number >= 4 && f.number <= 9).map(f => f.path),
    "HABILITAR_MATRICULAS", ...FILES.filter(f => f.number >= 10 && f.number <= 12).map(f => f.path), "ESCENARIOS",
    "/api/evaluaciones/501/calificaciones/importaciones", "/api/evaluaciones/502/calificaciones/importaciones",
  ]);
  const family = writes.find(c => c.path.includes("familias"));
  expect(writes.filter(c => c.path === PATH).map(c => c.body.get("archivo").name)).toEqual([
    "00_configuracion_inicial.sql", "09_habilitar_matriculas.sql", "12_escenarios.sql",
  ]);
  expect([...family.body.keys()]).toEqual(["estudiantes", "apoderados", "parentescos"]);
  expect(family.query.confirmar).toBe("true");
  expect(screen.getByText("Carmen")).toBeTruthy();
  expect(screen.getByText("Patricia")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Imprimir o guardar PDF" })).toBeTruthy();
  expect(screen.getByText(/Matricular antes a Sofía/)).toBeTruthy();
  await userEvent.click(screen.getByRole("button", { name: "Cerrar", exact: true }));
  await userEvent.click(screen.getByRole("button", { name: "Ver carga preparada" }));
  expect(screen.queryByRole("button", { name: "Iniciar carga" })).toBeNull();
});

test("detiene la carga ante una fila inválida y continúa desde el fallo sin repetir las cuentas", async () => {
  let fail = true;
  const calls = backend({ "POST /api/competencias/importaciones": () => fail
    ? { ...result, errores: [{ row: 3, field: "nombre", message: "Revisa la competencia" }] } : result });
  render(<DemoPanel />); await open(); await selectAll();
  await userEvent.click(screen.getByRole("button", { name: "Iniciar carga" }));
  expect((await screen.findByRole("alert")).textContent).toContain("Fila 3: Revisa la competencia");
  expect(calls.some(c => c.query?.etapa === "HABILITAR_MATRICULAS")).toBe(false);
  expect(screen.getByText("Patricia")).toBeTruthy();
  fail = false;
  await userEvent.click(screen.getByRole("button", { name: "Continuar carga" }));
  await screen.findByText("Carga completada. Ya puedes recorrer los flujos de demostración.");
  expect(calls.filter(c => c.path.includes("familias"))).toHaveLength(1);
  expect(calls.filter(c => c.path === "/api/docentes/importaciones")).toHaveLength(1);
  expect(calls.filter(c => c.path === "/api/competencias/importaciones")).toHaveLength(2);
});

test("al reabrir respeta notas existentes y solo solicita el archivo que todavía falta", async () => {
  const calls = backend({ "GET /api/evaluaciones/501/planilla": sheet(10) },
    [stage("INICIAL"), stage("HABILITAR_MATRICULAS"), stage("ESCENARIOS")]);
  render(<DemoPanel />); await open();
  expect(screen.queryByLabelText("Excel de Estudiantes")).toBeNull();
  expect(screen.queryByLabelText("Excel de Notas de Matemática · 2.º A")).toBeNull();
  await userEvent.upload(screen.getByLabelText("Excel de Notas de Matemática · 3.º A"), new File(["excel"], FILES[13].name));
  await userEvent.click(screen.getByRole("button", { name: "Iniciar carga" }));
  await screen.findByText("Carga completada. Ya puedes recorrer los flujos de demostración.");
  expect(calls.filter(c => c.method === "POST").map(c => c.path)).toEqual(["/api/evaluaciones/502/calificaciones/importaciones"]);
});

test("valida los archivos antes de escribir y conserva el error junto a su selector", async () => {
  const calls = backend(); render(<DemoPanel />); await open();
  await userEvent.click(screen.getByRole("button", { name: "Iniciar carga" }));
  expect(await screen.findByText("Selecciona 01_estudiantes.xlsx.")).toBeTruthy();
  const input = screen.getByLabelText("Excel de Estudiantes");
  fireEvent.change(input, { target: { files: [new File(["texto"], "otro.txt")] } });
  expect(await screen.findByText("El archivo debe tener formato .xlsx.")).toBeTruthy();
  expect(calls.filter(c => c.method === "POST")).toHaveLength(0);
});

test("exige los SQL externos y rechaza un archivo SQL vacío o demasiado grande antes de escribir", async () => {
  const calls = backend(); render(<DemoPanel />); await open();
  const input = screen.getByLabelText("SQL de Configuración inicial");
  fireEvent.change(input, { target: { files: [new File([], "00_configuracion_inicial.sql")] } });
  expect(await screen.findByText("Selecciona un SQL no vacío de hasta 1 MB.")).toBeTruthy();
  fireEvent.change(input, { target: { files: [new File([new Uint8Array(1024 * 1024 + 1)], "00_configuracion_inicial.sql")] } });
  expect(screen.getByText("Selecciona un SQL no vacío de hasta 1 MB.")).toBeTruthy();
  fireEvent.change(input, { target: { files: [new File(["SELECT 1"], "otro.sql")] } });
  expect(await screen.findByText("Selecciona 00_configuracion_inicial.sql.")).toBeTruthy();
  await userEvent.click(screen.getByRole("button", { name: "Iniciar carga" }));
  expect(calls.filter(c => c.method === "POST")).toHaveLength(0);
});

test("no guarda ni expone contraseñas si la importación atómica de familias se revierte", async () => {
  const calls = backend({ "POST /api/alumnos/importaciones/familias": { ...familyResult, saved: false,
    files: { estudiantes: { error: "Documento inválido", result: null } } } });
  render(<DemoPanel />); await open(); await selectAll();
  await userEvent.click(screen.getByRole("button", { name: "Iniciar carga" }));
  expect((await screen.findByRole("alert")).textContent).toContain("Documento inválido");
  expect(screen.queryByText("Credenciales iniciales")).toBeNull();
  expect(calls.some(c => c.path === "/api/docentes/importaciones")).toBe(false);
});

test("muestra una carga ya preparada sin volver a importar notas publicadas", async () => {
  const calls = backend({
    "GET /api/evaluaciones/501/planilla": { evaluacion: { estado: "PUBLICADA" }, alumnos: [] },
    "GET /api/evaluaciones/502/planilla": sheet(10),
  }, [stage("INICIAL"), stage("HABILITAR_MATRICULAS"), stage("ESCENARIOS")]);
  render(<DemoPanel />);
  await userEvent.click(await screen.findByRole("button", { name: "Ver carga preparada" }));
  expect(screen.queryByRole("button", { name: "Iniciar carga" })).toBeNull();
  expect(calls.filter(c => c.method === "POST")).toHaveLength(0);
});

test("una evaluación anulada bloquea la carga sin restablecerla", async () => {
  const calls = backend({ "GET /api/evaluaciones/501/planilla": { evaluacion: { estado: "ANULADA" }, alumnos: [] } },
    [stage("INICIAL"), stage("HABILITAR_MATRICULAS"), stage("ESCENARIOS")]);
  render(<DemoPanel />);
  expect((await screen.findByRole("alert")).textContent).toContain("fue anulada");
  expect(calls.filter(c => c.method === "POST")).toHaveLength(0);
});

test("permite recuperar la disponibilidad después de un error de conexión", async () => {
  let fail = true;
  backend({ [`GET ${PATH}`]: () => fail ? reply(503, { message: "Servidor ocupado" }) : { paquete: "EXPO-2026-v1", etapas: [] } });
  render(<DemoPanel />);
  expect((await screen.findByRole("alert")).textContent).toContain("Servidor ocupado");
  fail = false;
  await userEvent.click(screen.getByRole("button", { name: "Volver a comprobar" }));
  expect(await screen.findByRole("button", { name: "Cargar datos de demostración" })).toBeTruthy();
});

test("las credenciales temporales sobreviven a una respuesta posterior de cuenta existente", async () => {
  let first = true;
  backend({ "POST /api/docentes/importaciones": () => {
    const value = { totalRows: 2, importedRows: 1, rejectedRows: first ? 1 : 0,
      issues: first ? [{ row: 3, message: "Corrige el segundo docente" }] : [],
      credentials: [{ ...credential("DOCENTE", "Patricia"), temporaryPassword: first ? "ClavePatricia123" : null, existing: !first }] };
    first = false; return value;
  } });
  render(<DemoPanel />); await open(); await selectAll();
  await userEvent.click(screen.getByRole("button", { name: "Iniciar carga" }));
  await screen.findByRole("alert");
  await userEvent.click(screen.getByRole("button", { name: "Continuar carga" }));
  await screen.findByText("Carga completada. Ya puedes recorrer los flujos de demostración.");
  const row = screen.getByText("Patricia").closest("tr");
  expect(within(row).getByText("ClavePatricia123")).toBeTruthy();
});

test("ignora la guía y rechaza archivos ajenos al paquete antes de escribir", async () => {
  const calls = backend(); render(<DemoPanel />); await open();
  await userEvent.upload(screen.getByLabelText("Seleccionar los Excel y SQL de demostración"), [
    new File(["guía"], "00_LEER_ANTES_DE_IMPORTAR.xlsx"), new File(["otro"], "otro.xlsx"),
  ]);
  expect((await screen.findByRole("alert")).textContent).toContain("otro.xlsx");
  expect(screen.getByRole("button", { name: "Iniciar carga" }).disabled).toBe(true);
  expect(calls.filter(c => c.method === "POST")).toHaveLength(0);
});

test("si se abandona la pantalla no inicia la siguiente etapa después de la solicitud en curso", async () => {
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  const calls = backend({ [`POST ${PATH}`]: async () => { await pending; return stage("INICIAL"); } });
  const view = render(<DemoPanel />); await open(); await selectAll();
  await userEvent.click(screen.getByRole("button", { name: "Iniciar carga" }));
  await waitFor(() => expect(calls.filter(c => c.method === "POST")).toHaveLength(1));
  expect(screen.getByRole("button", { name: "Cerrar", exact: true }).disabled).toBe(true);
  view.unmount(); release();
  await new Promise(resolve => setTimeout(resolve, 20));
  expect(calls.filter(c => c.method === "POST")).toHaveLength(1);
});

test("no declara éxito si el Excel de notas está vacío aunque el importador no devuelva errores", async () => {
  const calls = backend({ "POST /api/evaluaciones/501/calificaciones/importaciones": { ...result, total: 0, creadas: 0 } });
  render(<DemoPanel />); await open(); await selectAll();
  await userEvent.click(screen.getByRole("button", { name: "Iniciar carga" }));
  expect((await screen.findByRole("alert")).textContent).toContain("no tiene los niveles esperados");
  expect(screen.queryByText("Carga completada. Ya puedes recorrer los flujos de demostración.")).toBeNull();
  expect(screen.getByLabelText("Excel de Notas de Matemática · 2.º A")).toBeTruthy();
  expect(calls.some(c => c.path === "/api/evaluaciones/501/planilla")).toBe(true);
});

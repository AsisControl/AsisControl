/* eslint-disable @typescript-eslint/no-require-imports -- Pruebas DOM con node:test y React act. */
const { test, afterEach, after } = require("node:test");
const assert = require("node:assert/strict");
const { JSDOM } = require("jsdom");
const React = require("react");
const dom = new JSDOM(
  '<!doctype html><html><body><div id="root"></div></body></html>',
  {
    url: "http://frontend.test",
    pretendToBeVisual: true,
  },
);
global.window = dom.window;
global.document = dom.window.document;
global.HTMLElement = dom.window.HTMLElement;
global.IS_REACT_ACT_ENVIRONMENT = true;
const { createRoot } = require("react-dom/client");
const harness = require("./loader.cjs");
const globals = {
  window: dom.window,
  document: dom.window.document,
  HTMLElement: dom.window.HTMLElement,
  FormData: dom.window.FormData,
};
const response = (status, body) => ({
  status,
  ok: status >= 200 && status < 300,
  json: async () => body,
});
const fixture = () => ({
  id: 42,
  codigoAlumno: "ESTU-000042",
  activo: true,
  apoderados: [],
  persona: {
    id: 17,
    nombres: "Lucía",
    apellidoPaterno: "Flores",
    apellidoMaterno: null,
    nombreCompleto: "Lucía Flores",
    fechaNacimiento: "2012-05-14",
    tipoDocumento: null,
    numeroDocumento: null,
    sexo: null,
    correo: null,
    telefono: null,
    direccion: null,
    nacionalidadId: null,
  },
});
let root;
async function mount(fetcher, student, onSaved = () => {}) {
  const load = harness(fetcher, globals);
  root = createRoot(document.getElementById("root"));
  await React.act(async () => {
    root.render(
      React.createElement(load("modules/students/student-form").StudentForm, {
        student,
        onSaved,
        onCancel() {},
      }),
    );
  });
  return load;
}
async function change(name, value) {
  const element = document.querySelector(`[name="${name}"]`);
  assert.ok(element, `Existe el campo ${name}`);
  const prototype =
    element.tagName === "TEXTAREA"
      ? dom.window.HTMLTextAreaElement.prototype
      : element.tagName === "SELECT"
        ? dom.window.HTMLSelectElement.prototype
        : dom.window.HTMLInputElement.prototype;
  await React.act(async () => {
    Object.getOwnPropertyDescriptor(prototype, "value").set.call(
      element,
      value,
    );
    element.dispatchEvent(new dom.window.Event("input", { bubbles: true }));
    element.dispatchEvent(new dom.window.Event("change", { bubbles: true }));
  });
}
async function submit(times = 1) {
  await React.act(async () => {
    for (let i = 0; i < times; i++)
      document
        .querySelector("form")
        .dispatchEvent(
          new dom.window.Event("submit", { bubbles: true, cancelable: true }),
        );
  });
}
async function fillMinimum() {
  await change("nombres", "Lucía");
  await change("apellidoPaterno", "Flores");
  await change("fechaNacimiento", "2012-05-14");
}
afterEach(async () => {
  if (root) await React.act(async () => root.unmount());
  root = undefined;
});
after(() => dom.window.close());

async function settleList() {
  await React.act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 10));
  });
}
async function mountList(fetcher) {
  const load = harness(fetcher, globals);
  load("core/auth/session-storage").saveSession({
    accessToken: "test-token",
    user: { role: "ADMINISTRADOR" },
  });
  root = createRoot(document.getElementById("root"));
  await React.act(async () => {
    root.render(
      React.createElement(load("modules/students/students").Students),
    );
  });
  await settleList();
}

test("CA-09 DOM: paginación consulta el servidor y una búsqueda nueva vuelve a página cero", async () => {
  const urls = [];
  await mountList(async (url) => {
    const parsed = new URL(url);
    urls.push(parsed);
    const page = Number(parsed.searchParams.get("page"));
    return response(200, {
      content: parsed.searchParams.get("search") ? [] : [fixture()],
      page,
      totalElements: 21,
      totalPages: 2,
      first: page === 0,
      last: page === 1,
    });
  });
  assert.match(document.body.textContent, /Lucía Flores/);
  await React.act(async () =>
    [...document.querySelectorAll("button")]
      .find((b) => b.textContent === "Siguiente")
      .click(),
  );
  await settleList();
  assert.equal(urls.at(-1).searchParams.get("page"), "1");
  await change("search", "  Sin coincidencias  ");
  await submit();
  await settleList();
  assert.equal(urls.at(-1).searchParams.get("page"), "0");
  assert.equal(urls.at(-1).searchParams.get("search"), "Sin coincidencias");
  assert.match(
    document.body.textContent,
    /No se encontraron estudiantes para esta búsqueda/,
  );
});

test("FE2-01 DOM: al fallar una recarga se ocultan datos anteriores y se permite reintentar sin filtrar información", async () => {
  let failing = false;
  await mountList(async () =>
    failing
      ? response(500, { message: "Error con documento 12345678" })
      : response(200, {
          content: [fixture()],
          page: 0,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        }),
  );
  assert.match(document.body.textContent, /Lucía Flores/);
  failing = true;
  await submit();
  await settleList();
  assert.doesNotMatch(document.body.textContent, /Lucía Flores|12345678/);
  assert.match(document.body.textContent, /No se pudo cargar el listado/);
  failing = false;
  await React.act(async () =>
    [...document.querySelectorAll("button")]
      .find((b) => b.textContent === "Reintentar carga")
      .click(),
  );
  await settleList();
  assert.match(document.body.textContent, /Lucía Flores/);
});

test("FE2-02/05 DOM: alta incompleta no envía HTTP y enfoca el primer campo inválido", async () => {
  let calls = 0;
  await mount(async () => {
    calls++;
    return response(201, fixture());
  });
  await submit();
  assert.equal(calls, 0);
  assert.equal(document.activeElement.name, "nombres");
  assert.match(
    document.querySelector('[role="alert"]').textContent,
    /Revisa los campos/,
  );
  assert.equal(
    document.querySelector('[name="nombres"]').getAttribute("aria-invalid"),
    "true",
  );
});
test("FE2-05 DOM: dos envíos simultáneos producen una sola petición y conservan el borrador", async () => {
  const calls = [];
  const saved = [];
  let release;
  const pending = new Promise((resolve) => {
    release = resolve;
  });
  await mount(
    async (url, options) => {
      calls.push({ url, body: JSON.parse(options.body) });
      return pending;
    },
    undefined,
    (s) => saved.push(s),
  );
  await fillMinimum();
  await submit(2);
  assert.equal(calls.length, 1);
  assert.equal(document.querySelector("fieldset").disabled, true);
  assert.equal(calls[0].body.persona.nombres, "Lucía");
  assert.equal(calls[0].body.persona.tipoDocumento, null);
  assert.equal("codigoAlumno" in calls[0].body, false);
  await React.act(async () => release(response(201, fixture())));
  assert.equal(saved.length, 1);
  assert.equal(saved[0].codigoAlumno, "ESTU-000042");
});
test("CA-06/07 DOM: homonimia exige confirmar y cualquier edición invalida esa confirmación", async () => {
  const calls = [];
  const saved = [];
  await mount(
    async (_, options) => {
      const body = JSON.parse(options.body);
      calls.push(body);
      return body.confirmarHomonimia
        ? response(201, fixture())
        : response(409, {
            code: "HOMONIMIA_DETECTADA",
            message: "Coincidencias",
            details: {
              coincidencias: [
                {
                  id: 1,
                  codigoAlumno: "ESTU-000001",
                  nombreCompleto: "Lucía Flores",
                  fechaNacimiento: "2012-05-14",
                },
              ],
            },
          });
    },
    undefined,
    (s) => saved.push(s),
  );
  await fillMinimum();
  await submit();
  assert.match(document.body.textContent, /ESTU-000001/);
  await submit();
  assert.equal(calls.length, 1);
  await React.act(async () =>
    document.querySelector('[type="checkbox"]').click(),
  );
  await change("apellidoPaterno", "Flores Paz");
  assert.equal(document.querySelector('[type="checkbox"]'), null);
  await submit();
  assert.equal(calls[1].confirmarHomonimia, undefined);
  assert.equal(document.querySelector('[type="checkbox"]').checked, false);
  await React.act(async () =>
    document.querySelector('[type="checkbox"]').click(),
  );
  await submit();
  assert.equal(calls[2].confirmarHomonimia, true);
  assert.equal(saved.length, 1);
});
test("CA-04 DOM: documento duplicado conserva datos y no ofrece confirmar persona diferente", async () => {
  await mount(async () =>
    response(409, {
      code: "DOCUMENTO_DUPLICADO",
      message: "Duplicado 12345678",
    }),
  );
  await fillMinimum();
  await change("tipoDocumento", "DNI");
  await change("numeroDocumento", "12345678");
  await submit();
  assert.match(
    document.querySelector('[role="alert"]').textContent,
    /no se puede confirmar/,
  );
  assert.equal(document.querySelector('[type="checkbox"]'), null);
  assert.equal(
    document.querySelector('[name="numeroDocumento"]').value,
    "12345678",
  );
  assert.equal(
    document.querySelector('[role="alert"]').textContent.includes("12345678"),
    false,
  );
});
test("CA-12/13 DOM: corrección exige motivo, preserva código y envía PUT con motivo válido", async () => {
  const calls = [];
  await mount(async (url, options) => {
    calls.push({ url, method: options.method, body: JSON.parse(options.body) });
    return response(200, fixture());
  }, fixture());
  await change("nombres", "Ana Lucía");
  await submit();
  assert.equal(calls.length, 0);
  assert.equal(document.activeElement.name, "motivo");
  await change("motivo", "x".repeat(9));
  await submit();
  assert.equal(calls.length, 0);
  await change("motivo", "Corrección según documento");
  await submit();
  assert.equal(calls.length, 1);
  assert.equal(calls[0].method, "PUT");
  assert.equal(calls[0].body.motivo, "Corrección según documento");
  assert.equal("codigoAlumno" in calls[0].body, false);
  assert.equal(document.querySelector("input[readonly]").value, "ESTU-000042");
});
test("CA-10 DOM: revertir identidad limpia un motivo parcial para no dejar un error oculto", async () => {
  let calls = 0;
  await mount(async () => {
    calls++;
    return response(200, fixture());
  }, fixture());
  await change("nombres", "Ana");
  await change("motivo", "corto");
  await change("nombres", "Lucía");
  assert.equal(document.querySelector('[name="motivo"]'), null);
  await change("correo", "contacto@example.test");
  await submit();
  assert.equal(calls, 1);
});
test("FE2-05 DOM: validación anidada del servidor se muestra por campo y recupera el foco", async () => {
  await mount(async () =>
    response(400, {
      code: "DATOS_INVALIDOS",
      message: "Validación",
      correlationId: "REF-NO-VISIBLE",
      fieldErrors: [
        { field: "persona.correo", message: "Correo de contacto no admitido" },
      ],
    }),
  );
  await fillMinimum();
  await change("correo", "contacto@example.test");
  await submit();
  assert.equal(document.activeElement.name, "correo");
  assert.match(
    document.getElementById("student-correo-error").textContent,
    /Correo de contacto no admitido/,
  );
  assert.equal(document.body.textContent.includes("REF-NO-VISIBLE"), false);
  assert.equal(
    document.querySelector('[name="correo"]').value,
    "contacto@example.test",
  );
});

async function blur(name) {
  await React.act(async () => {
    const field = document.querySelector(`[name="${name}"]`);
    field.focus();
    field.blur();
  });
}
test("FE2 regresión foco: fecha inválida no captura el cursor al corregir otro campo", async () => {
  await mount(async () => response(201, fixture()));
  await change("nombres", "Diego");
  await change("apellidoPaterno", "Ibarra");
  await change("fechaNacimiento", "");
  await submit();
  assert.equal(document.activeElement.name, "fechaNacimiento");
  const phone = document.querySelector('[name="telefono"]');
  await React.act(async () => phone.focus());
  for (const value of ["9", "99", "999", "999742200"]) {
    await change("telefono", value);
    assert.equal(document.activeElement, phone);
  }
  assert.equal(
    document
      .querySelector('[name="fechaNacimiento"]')
      .getAttribute("aria-invalid"),
    "true",
  );
});
test("FE2 regresión blur: errores independientes y sin validación por cada tecla", async () => {
  await mount(async () => response(201, fixture()));
  await change("telefono", "812345678");
  assert.equal(document.getElementById("student-telefono-error"), null);
  await blur("telefono");
  assert.match(
    document.getElementById("student-telefono-error").textContent,
    /empezar con 9/,
  );
  await change("telefono", "9");
  assert.equal(document.getElementById("student-telefono-error"), null);
  await blur("telefono");
  assert.match(
    document.getElementById("student-telefono-error").textContent,
    /9 dígitos/,
  );
  assert.equal(document.activeElement.name, undefined);
});
test("FE2 regresión filtros: restringe texto escrito/pegado y cambia reglas del documento", async () => {
  await mount(async () => response(201, fixture()));
  await change("nombres", "Diego123@ Ñúñez-Ü O’Neill");
  assert.equal(
    document.querySelector('[name="nombres"]').value,
    "Diego Ñúñez-Ü O’Neill",
  );
  await change("telefono", "9a99-742 20055");
  assert.equal(document.querySelector('[name="telefono"]').value, "999742200");
  await change("tipoDocumento", "DNI");
  await change("numeroDocumento", "01a234567890");
  assert.equal(
    document.querySelector('[name="numeroDocumento"]').value,
    "01234567",
  );
  assert.equal(document.querySelector('[name="numeroDocumento"]').maxLength, 8);
  assert.equal(
    document.querySelector('[name="numeroDocumento"]').required,
    true,
  );
  assert.equal(document.querySelector('[name="tipoDocumento"]').required, true);
  await change("tipoDocumento", "CARNET_EXTRANJERIA");
  assert.equal(document.querySelector('[name="numeroDocumento"]').maxLength, 9);
  assert.match(
    document.getElementById("student-numeroDocumento-hint").textContent,
    /9 dígitos/,
  );
  await change("tipoDocumento", "PASAPORTE");
  await change("numeroDocumento", "ab12-345678901");
  assert.equal(
    document.querySelector('[name="numeroDocumento"]').value,
    "AB1234567890",
  );
});
test("FE2 regresión autofill: submit usa los valores reales aun sin eventos React", async () => {
  let sent;
  await mount(async (_, options) => {
    sent = JSON.parse(options.body);
    return response(201, fixture());
  });
  for (const [name, value] of Object.entries({
    nombres: "Diego",
    apellidoPaterno: "Ibarra",
    fechaNacimiento: "2012-10-07",
    numeroDocumento: "12345678",
    correo: "estudiante.demo@example.com",
    telefono: "999742200",
  })) {
    Object.getOwnPropertyDescriptor(
      dom.window.HTMLInputElement.prototype,
      "value",
    ).set.call(document.querySelector(`[name="${name}"]`), value);
  }
  Object.getOwnPropertyDescriptor(
    dom.window.HTMLSelectElement.prototype,
    "value",
  ).set.call(document.querySelector('[name="tipoDocumento"]'), "DNI");
  await submit();
  assert.equal(sent.persona.nombres, "Diego");
  assert.equal(sent.persona.numeroDocumento, "12345678");
  assert.equal(sent.persona.correo, "estudiante.demo@example.com");
  assert.equal(sent.persona.apellidoMaterno, null);
});
test("FE2 regresión servidor: el foco cambia una vez y el mensaje se limpia al editar/reintentar", async () => {
  let count = 0;
  await mount(async () =>
    ++count === 1
      ? response(400, {
          code: "DATOS_INVALIDOS",
          message: "Datos invalidos",
          fieldErrors: [
            {
              campo: "persona.correo",
              mensaje: "Comprueba este correo de contacto",
            },
          ],
        })
      : response(201, fixture()),
  );
  await fillMinimum();
  await submit();
  assert.equal(document.activeElement.name, "correo");
  assert.match(
    document.getElementById("student-correo-error").textContent,
    /Comprueba/,
  );
  const phone = document.querySelector('[name="telefono"]');
  await React.act(async () => phone.focus());
  await change("telefono", "999742200");
  assert.equal(document.activeElement, phone);
  assert.equal(document.querySelector('[role="alert"]'), null);
  await change("correo", "contacto@example.test");
  await submit();
  assert.equal(count, 2);
});

test("FE2 regresión pegado: filtra antes del límite nativo y respeta la selección", async () => {
  await mount(async () => response(201, fixture()));
  await change("tipoDocumento", "DNI");
  const field = document.querySelector('[name="numeroDocumento"]');
  const paste = new dom.window.Event("paste", {
    bubbles: true,
    cancelable: true,
  });
  Object.defineProperty(paste, "clipboardData", {
    value: { getData: () => "12A34567890" },
  });
  await React.act(async () => field.dispatchEvent(paste));
  assert.equal(field.value, "12345678");
  field.setSelectionRange(2, 4);
  const replace = new dom.window.Event("paste", {
    bubbles: true,
    cancelable: true,
  });
  Object.defineProperty(replace, "clipboardData", {
    value: { getData: () => "a00-" },
  });
  await React.act(async () => field.dispatchEvent(replace));
  assert.equal(field.value, "12005678");
});

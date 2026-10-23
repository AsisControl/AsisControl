/* eslint-disable @typescript-eslint/no-require-imports -- Pruebas ejecutadas por node:test. */
const { test } = require("node:test");
const assert = require("node:assert/strict");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");
const harness = require("./loader.cjs");
const fixture = () => ({
  id: 42,
  codigoAlumno: "ESTU-000042",
  activo: true,
  version: 3,
  apoderados: [],
  persona: {
    id: 17,
    nombres: "Lucía",
    apellidoPaterno: "Flores",
    apellidoMaterno: null,
    nombreCompleto: "Lucía Flores",
    tipoDocumento: null,
    numeroDocumento: null,
    fechaNacimiento: "2012-05-14",
    sexo: null,
    correo: null,
    telefono: null,
    direccion: null,
    nacionalidadId: null,
    fechaCreacion: "2026-10-21T15:00:00Z",
  },
});
const response = (status, body) => ({
  status,
  ok: status >= 200 && status < 300,
  json: async () => body,
});
const rules = () => harness()("modules/students/student-rules");
const plain = (value) => JSON.parse(JSON.stringify(value));

test("CA-01/02: solo nombres, primer apellido y nacimiento permiten un alta sin documento", () => {
  const studentRules = rules();
  const draft = studentRules.studentDraft(fixture());
  assert.deepEqual(
    plain(studentRules.validateStudent(draft, undefined, "2026-10-21")),
    {},
  );
  const body = studentRules.studentInput(draft);
  assert.equal(body.persona.tipoDocumento, null);
  assert.equal(body.persona.numeroDocumento, null);
  assert.equal(body.persona.correo, null);
  assert.equal(body.persona.apellidoMaterno, null);
});
test("CA-01: límites alineados con la API (nombres 100, apellidos 80)", () => {
  const studentRules = rules();
  const draft = studentRules.studentDraft(fixture());
  for (const field of ["nombres", "apellidoPaterno"]) {
    const max = field === "nombres" ? 100 : 80;
    for (const value of ["", " A ", "A".repeat(max + 1)])
      assert.ok(
        studentRules.validateStudent({ ...draft, [field]: value })[field],
      );
    for (const value of ["Áb", "A".repeat(max)])
      assert.equal(
        studentRules.validateStudent({ ...draft, [field]: value })[field],
        undefined,
      );
  }
});
test("CA-03: se rechaza tanto el tipo solo como el número solo", () => {
  const studentRules = rules();
  const draft = studentRules.studentDraft(fixture());
  assert.ok(
    studentRules.validateStudent({ ...draft, tipoDocumento: "DNI" })
      .numeroDocumento,
  );
  assert.ok(
    studentRules.validateStudent({ ...draft, numeroDocumento: "12345678" })
      .tipoDocumento,
  );
});
test("RN-137 actualizada: DNI 8 dígitos, CE 9 y pasaporte alfanumérico 8–12", () => {
  const studentRules = rules();
  const draft = studentRules.studentDraft(fixture());
  for (const [type, valid, invalid] of [
    ["DNI", ["01234567"], ["1234567", "123456789", "1234567A"]],
    [
      "CARNET_EXTRANJERIA",
      ["001234567"],
      ["AB123456", "12345678", "1234567890"],
    ],
    [
      "PASAPORTE",
      ["AB123456", "AB1234567890"],
      ["A123456", "AB 123456", "A123456789012"],
    ],
  ]) {
    for (const value of valid)
      assert.equal(
        studentRules.validateStudent({
          ...draft,
          tipoDocumento: type,
          numeroDocumento: value,
        }).numeroDocumento,
        undefined,
      );
    for (const value of invalid)
      assert.ok(
        studentRules.validateStudent({
          ...draft,
          tipoDocumento: type,
          numeroDocumento: value,
        }).numeroDocumento,
      );
  }
});
test("CA-05 actualizada: cumpleaños, rango de secundaria y fecha civil de Lima", () => {
  const studentRules = rules();
  const draft = studentRules.studentDraft(fixture());
  assert.equal(
    studentRules.todayInLima(new Date("2026-10-22T02:30:00Z")),
    "2026-10-21",
  );
  for (const value of ["2016-10-21", "2005-10-22", "2006-10-21"])
    assert.equal(
      studentRules.validateStudent(
        { ...draft, fechaNacimiento: value },
        undefined,
        "2026-10-21",
      ).fechaNacimiento,
      undefined,
    );
  for (const value of ["2016-10-22", "2005-10-21", "2026-10-21", "1926-10-21"])
    assert.match(
      studentRules.validateStudent(
        { ...draft, fechaNacimiento: value },
        undefined,
        "2026-10-21",
      ).fechaNacimiento,
      /10 y 20/,
    );
  assert.match(
    studentRules.validateStudent(
      { ...draft, fechaNacimiento: "2026-10-22" },
      undefined,
      "2026-10-21",
    ).fechaNacimiento,
    /futura/,
  );
});
test("CA-05: fecha imposible y nacimiento vacío se rechazan; año bisiesto válido se admite", () => {
  const studentRules = rules();
  const draft = studentRules.studentDraft(fixture());
  for (const value of [
    "",
    "2012-02-30",
    "2011-02-29",
    "0000-01-01",
    "cualquier texto",
  ])
    assert.ok(
      studentRules.validateStudent({ ...draft, fechaNacimiento: value })
        .fechaNacimiento,
    );
  assert.equal(
    studentRules.validateStudent({ ...draft, fechaNacimiento: "2012-02-29" })
      .fechaNacimiento,
    undefined,
  );
});
test("CA-10/11/20/21: el payload excluye código, estado, accesos y matrícula incluso si se inyectan", () => {
  const studentRules = rules();
  const draft = {
    ...studentRules.studentDraft(fixture()),
    codigoAlumno: "HACK",
    activo: false,
    password: "secreto",
    matricula: {},
  };
  const body = studentRules.studentInput(draft, fixture());
  assert.deepEqual(Object.keys(body).sort(), ["persona", "version"]);
  assert.equal(body.version, 3);
  assert.equal(body.persona.nacionalidadId, null);
  assert.equal(JSON.stringify(body).includes("HACK"), false);
  assert.equal(JSON.stringify(body).includes("secreto"), false);
});
test("CA-12/13: todo cambio sensible exige motivo; contacto solo no lo exige", () => {
  const studentRules = rules();
  const original = fixture();
  const draft = studentRules.studentDraft(original);
  for (const [key, value] of [
    ["nombres", "Ana"],
    ["apellidoPaterno", "Díaz"],
    ["apellidoMaterno", "Paz"],
    ["fechaNacimiento", "2012-05-15"],
    ["tipoDocumento", "DNI"],
    ["numeroDocumento", "12345678"],
  ])
    assert.ok(
      studentRules.validateStudent({ ...draft, [key]: value }, original).motivo,
    );
  assert.equal(
    studentRules.validateStudent(
      { ...draft, correo: "familia@example.test", telefono: "999888777" },
      original,
    ).motivo,
    undefined,
  );
  assert.equal(
    studentRules.hasSensitiveChanges(
      { ...draft, nombres: " Lucía " },
      original,
    ),
    false,
  );
});
test("RN-172: motivo recortado admite los límites 10 y 1000, rechaza 9 y 1001", () => {
  const studentRules = rules();
  for (const length of [10, 1000])
    assert.equal(studentRules.reasonError("x".repeat(length)), "");
  for (const text of ["", " ".repeat(10), "x".repeat(9), "x".repeat(1001)])
    assert.notEqual(studentRules.reasonError(text), "");
});
test("CA-10: contacto opcional se valida solo si se informa", () => {
  const studentRules = rules();
  const draft = studentRules.studentDraft(fixture());
  assert.ok(
    studentRules.validateStudent({ ...draft, correo: "correo-invalido" })
      .correo,
  );
  assert.ok(
    studentRules.validateStudent({ ...draft, telefono: "abc" }).telefono,
  );
  assert.ok(
    studentRules.validateStudent({ ...draft, direccion: "x".repeat(251) })
      .direccion,
  );
  assert.equal(
    studentRules.validateStudent({ ...draft, correo: " familia@example.test " })
      .correo,
    undefined,
  );
});
test("CA-08: nacimiento se muestra como fecha civil sin retroceder un día", () => {
  assert.equal(rules().birthLabel("2012-05-14"), "14/05/2012");
});
test("CA-01/10: POST y PUT usan API real, Bearer y JSON del contrato sin generar códigos", async () => {
  const calls = [];
  const load = harness(async (url, options) => {
    calls.push({ url, options });
    return response(200, fixture());
  });
  load("core/auth/session-storage").saveSession({ accessToken: "test-token" });
  const input = load("modules/students/student-rules").studentInput(
    rules().studentDraft(fixture()),
  );
  const service = load("modules/students/student-api");
  await service.saveStudent(input);
  await service.saveStudent(input, 42);
  await service.getStudent(42);
  assert.deepEqual(
    calls.map((c) => [c.url, c.options.method || "GET"]),
    [
      ["http://api.test/api/alumnos", "POST"],
      ["http://api.test/api/alumnos/42", "PUT"],
      ["http://api.test/api/alumnos/42", "GET"],
    ],
  );
  assert.equal(
    calls[0].options.headers.get("Authorization"),
    "Bearer test-token",
  );
  assert.equal("codigoAlumno" in JSON.parse(calls[0].options.body), false);
});
test("CA-04/23: duplicado exacto no tiene confirmación y su mensaje oculta el documento", () => {
  const load = harness();
  const { ApiError } = load("core/api/client");
  const service = load("modules/students/student-api");
  const error = new ApiError("Duplicado 12345678", 409, "DOCUMENTO_DUPLICADO");
  assert.equal(service.homonyms(error), null);
  assert.equal(service.studentErrorMessage(error).includes("12345678"), false);
});
test("CA-06/07: homonimia solo en 409 específico y confirmación explícita", () => {
  const load = harness();
  const { ApiError } = load("core/api/client");
  const service = load("modules/students/student-api");
  const candidate = {
    id: 1,
    codigoAlumno: "ESTU-000001",
    nombreCompleto: "Lucía Flores",
    fechaNacimiento: "2012-05-14",
  };
  assert.equal(
    service.homonyms(
      new ApiError("Coincidencia", 409, "HOMONIMIA_DETECTADA", [], {
        coincidencias: [candidate, {}],
      }),
    ).length,
    1,
  );
  assert.equal(
    service.homonyms(new ApiError("Coincidencia", 422, "HOMONIMIA_DETECTADA")),
    null,
  );
  const studentRules = rules();
  const draft = studentRules.studentDraft(fixture());
  assert.equal(studentRules.studentInput(draft).confirmarHomonimia, undefined);
  assert.equal(
    studentRules.studentInput(draft, undefined, true).confirmarHomonimia,
    true,
  );
});
test("FE2-05: el cliente conserva validaciones anidadas y el formulario reconoce únicamente sus campos", async () => {
  const load = harness(async () =>
    response(400, {
      message: "Datos inválidos",
      code: "DATOS_INVALIDOS",
      correlationId: "NO-MOSTRAR",
      fieldErrors: [
        { field: "persona.nombres", message: "Faltan nombres" },
        { field: "motivo", message: "Falta motivo" },
        { field: "password", message: "Ajeno" },
        null,
      ],
    }),
  );
  const service = load("modules/students/student-api");
  await assert.rejects(service.saveStudent({ persona: {} }), (error) => {
    assert.deepEqual(plain(service.studentFieldErrors(error)), {
      nombres: "Faltan nombres",
      motivo: "Falta motivo",
    });
    assert.equal(error.status, 400);
    assert.equal(error.message.includes("NO-MOSTRAR"), false);
    return true;
  });
});
test("CA-14/17/18: backend antiguo sin acciones no habilita operaciones de ciclo de vida", async () => {
  let requests = 0;
  const service = harness(async () => {
    requests++;
    return response(204, null);
  })("modules/students/student-api");
  for (const action of ["inactivar", "reactivar", "eliminar"]) {
    assert.equal(service.canPerform(fixture(), action), false);
    await assert.rejects(
      service.changeStudentState(fixture(), action, "Motivo válido"),
    );
  }
  assert.equal(requests, 0);
});
test("CA-14/17: inactivar y reactivar usan PATCH de perfil, jamás DELETE de Persona", async () => {
  const calls = [];
  const service = harness(async (url, options) => {
    calls.push([url, options]);
    return response(200, fixture());
  })("modules/students/student-api");
  const active = { ...fixture(), accionesPermitidas: { inactivar: true } };
  await service.changeStudentState(active, "inactivar", " Corrección válida ");
  await service.changeStudentState(
    { ...active, activo: false, accionesPermitidas: { reactivar: true } },
    "reactivar",
    "Motivo válido",
  );
  assert.equal(calls[0][0], "http://api.test/api/alumnos/42/estado");
  assert.equal(calls[0][1].method, "PATCH");
  assert.equal(calls[1][1].method, "PATCH");
  assert.deepEqual(JSON.parse(calls[0][1].body), {
    motivo: "Corrección válida",
    activo: false,
    version: 3,
  });
  assert.equal(JSON.parse(calls[1][1].body).activo, true);
});
test("CA-15/19: rechazo del servidor se propaga sin transformar el conflicto en éxito", async () => {
  const service = harness(async () =>
    response(409, { code: "MATRICULA_VIGENTE", message: "No permitido" }),
  )("modules/students/student-api");
  await assert.rejects(
    service.changeStudentState(
      { ...fixture(), accionesPermitidas: { inactivar: true } },
      "inactivar",
      "Motivo válido",
    ),
    (e) => e.status === 409,
  );
});
test("CA-18: eliminación confirmada envía motivo y versión y acepta 204", async () => {
  let sent;
  const service = harness(async (url, options) => {
    sent = { url, options };
    return response(204, null);
  })("modules/students/student-api");
  assert.equal(
    await service.changeStudentState(
      { ...fixture(), accionesPermitidas: { eliminar: true } },
      "eliminar",
      "Creada por error",
    ),
    null,
  );
  assert.equal(sent.options.method, "DELETE");
  assert.deepEqual(JSON.parse(sent.options.body), {
    motivo: "Creada por error",
    version: 3,
  });
});
test("CA-08/11: render muestra documento pendiente, estado y código de solo lectura", () => {
  const load = harness();
  const s = fixture();
  const detail = renderToStaticMarkup(
    React.createElement(load("modules/students/student-detail").StudentDetail, {
      student: s,
      onBack() {},
      onEdit() {},
      onChanged() {},
    }),
  );
  assert.match(detail, /Documento pendiente/);
  assert.match(detail, /Habilitada/);
  assert.match(detail, /14\/05\/2012/);
  assert.doesNotMatch(detail, />Eliminar ficha</);
  const form = renderToStaticMarkup(
    React.createElement(load("modules/students/student-form").StudentForm, {
      student: s,
      onSaved() {},
      onCancel() {},
    }),
  );
  assert.match(
    form,
    /<input(?=[^>]*readOnly="")(?=[^>]*value="ESTU-000042")[^>]*>/i,
  );
  assert.doesNotMatch(form, /name="codigoAlumno"/);
  assert.match(form, /maxLength="100"/i);
});
test("CA-22: sin administrador el módulo no renderiza ni consulta fichas", () => {
  const load = harness();
  load("core/auth/session-storage").saveSession({
    accessToken: "x",
    user: { role: "DOCENTE" },
  });
  const html = renderToStaticMarkup(
    React.createElement(load("modules/students/students").Students),
  );
  assert.match(html, /No tienes permiso/);
  assert.doesNotMatch(html, /Lucía|Registrar estudiante|Listado de fichas/);
});

test("FE2-05: respuesta sin ficha no se transforma en alta o cambio de estado exitoso", async () => {
  const service = harness(async () => response(204, null))(
    "modules/students/student-api",
  );
  await assert.rejects(
    service.saveStudent({ persona: {} }),
    /Consulta el listado/,
  );
  await assert.rejects(
    service.changeStudentState(
      { ...fixture(), accionesPermitidas: { inactivar: true } },
      "inactivar",
      "Motivo válido",
    ),
    /Vuelve a consultar/,
  );
});

test("RN-172: motivo inválido de ciclo de vida no produce una petición HTTP", async () => {
  let calls = 0;
  const service = harness(async () => {
    calls++;
    return response(200, fixture());
  })("modules/students/student-api");
  await assert.rejects(
    service.changeStudentState(
      { ...fixture(), accionesPermitidas: { inactivar: true } },
      "inactivar",
      "corto",
    ),
    /10 a 1000/,
  );
  assert.equal(calls, 0);
});

test("FE2 regresión: letras españolas, apóstrofos, guiones y filtros de pegado", () => {
  const studentRules = rules();
  const draft = studentRules.studentDraft(fixture());
  for (const value of [
    "María José",
    "Muñoz",
    "Güemes",
    "D’Ávila",
    "O'Neill",
    "Pérez-López",
  ])
    assert.equal(
      studentRules.validateStudent({ ...draft, nombres: value }).nombres,
      undefined,
    );
  assert.equal(
    studentRules.sanitizeStudentField("nombres", "María123 @Ñúñez-Ü O’Neill"),
    "María Ñúñez-Ü O’Neill",
  );
  assert.equal(
    studentRules.sanitizeStudentField("telefono", "9a99-742 20055"),
    "999742200",
  );
  assert.equal(
    studentRules.sanitizeStudentField("numeroDocumento", "012a34567890", "DNI"),
    "01234567",
  );
  assert.equal(
    studentRules.sanitizeStudentField(
      "numeroDocumento",
      "ab12-345678901",
      "PASAPORTE",
    ),
    "AB1234567890",
  );
  assert.ok(
    studentRules.validateStudent({ ...draft, nombres: "Diego2" }).nombres,
  );
  assert.ok(
    studentRules.validateStudent({ ...draft, telefono: "812345678" }).telefono,
  );
});
test("FE2 regresión: fecha inválida de la captura y límites en año bisiesto", () => {
  const studentRules = rules();
  const draft = studentRules.studentDraft(fixture());
  assert.match(
    studentRules.validateStudent(
      { ...draft, fechaNacimiento: "2026-10-07" },
      undefined,
      "2026-10-22",
    ).fechaNacimiento,
    /10 y 20/,
  );
  assert.deepEqual(plain(studentRules.studentBirthRange("2028-02-29")), {
    min: "2007-03-01",
    max: "2018-02-28",
  });
});
test("FE2 regresión: errores sin campo conservan el motivo y no quedan genéricos", () => {
  const load = harness();
  const { ApiError } = load("core/api/client");
  const service = load("modules/students/student-api");
  assert.equal(
    service.studentErrorMessage(
      new ApiError("El año académico está cerrado", 422, "ANIO_CERRADO"),
    ),
    "El año académico está cerrado",
  );
  assert.match(
    service.studentErrorMessage(
      new ApiError("Datos invalidos", 400, "DATOS_INVALIDOS", [
        { field: "codigoAlumno", message: "Obligatorio" },
      ]),
    ),
    /código institucional manual/,
  );
});

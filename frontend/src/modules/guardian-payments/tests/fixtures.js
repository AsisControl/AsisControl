export const student = { id: 3, codigoAlumno: "EST-003", nombreCompleto: "Lucía Flores" };
export const sibling = { id: 4, codigoAlumno: "EST-004", nombreCompleto: "Luis Flores" };
export const obligation = (id = 1, overrides = {}) => ({
  id, idMatricula: 7, idAlumno: 3, alumno: "Lucía Flores", codigoAlumno: "EST-003",
  codigoMatricula: "MAT-007", anioAcademico: 2026, codigoCompromiso: `CP-${String(id).padStart(6, "0")}`,
  concepto: "Pensión de marzo", numeroCuota: id, descripcion: null, montoTotal: 350,
  montoPagado: 0, saldo: 350, moneda: "PEN", fechaVencimiento: "2026-03-10",
  estado: "PENDIENTE", estadoFinanciero: "PENDIENTE", estadoAdministrativo: "ACTIVO",
  fechaCreacion: "2026-03-01T12:00:00Z", fechaActualizacion: "2026-03-01T12:00:00Z",
  motivoAnulacion: null, fechaAnulacion: null, version: 0, ...overrides,
});
export const order = (overrides = {}) => ({
  id: 10, codigo: "OP-000010", metodo: "TARJETA", estado: "PENDIENTE", moneda: "PEN",
  montoBase: 350, mora: 0, montoTotal: 350, creadaEn: "2026-03-01T12:00:00Z",
  expiraEn: new Date(Date.now() + 300000).toISOString(), pagadaEn: null,
  institucion: "Colegio de prueba", apoderado: "Ana Flores",
  items: [{ idCompromiso: 1, codigoCompromiso: "CP-000001", alumno: "Lucía Flores", concepto: "Pensión de marzo", numeroCuota: 1, monto: 350 }],
  qrUrl: null, paymentIntentId: "pi_sim_prueba", clientSecret: "mock-secret", mensaje: null, simulador: true, ...overrides,
});
export const complete = (overrides = {}) => order({ estado: "COMPLETADO", pagadaEn: "2026-03-01T12:05:00Z", ...overrides });
export const receipt = (value = complete()) => ({ orden: value, comprobantes: [{ id: 22, idTransaccionPago: 33, tipoComprobante: "CONSTANCIA_PAGO_DIGITAL", serie: "SIM", numero: "000022", fechaEmision: "2026-03-01T12:05:00Z", montoTotal: 350, idArchivoAdjunto: null }] });

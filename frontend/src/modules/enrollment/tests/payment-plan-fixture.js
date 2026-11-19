export const paymentPlan = (overrides = {}) => ({
  idSolicitud: 100, idMatricula: null, idAlumno: 42, alumno: "Lucía Flores", anioAcademico: 2026,
  grado: 1, idTarifa: 8, tarifaVersion: 2, firmaPlan: "plan-v2", montoMensual: 400,
  puedeGenerar: true, mensaje: null, legados: [],
  items: Array.from({ length: 10 }, (_, index) => {
    const mes = index + 3;
    return { mes, numeroCuota: index + 1, concepto: `Pensión mensual ${index + 1}`,
      fechaVencimiento: new Date(Date.UTC(2026, mes, 0)).toISOString().slice(0, 10),
      montoBase: 400, montoDescuento: 100, montoTotal: 300, beneficioNombre: "Beca de estudios",
      idCompromiso: null, estado: "PENDIENTE" };
  }), ...overrides,
});

import type { DestinoIndicador } from "@/shared/ui/dashboard-indicators";

/**
 * Opción del menú (icono y, si existe, pantalla de destino) de cada indicador del backend.
 * El backend envía rutas propias de la API; aquí se traducen a las pantallas reales del frontend.
 */
const DESTINOS: Record<string, DestinoIndicador> = {
  ALUMNOS_ACTIVOS: { icono: "Matrícula", enlace: "/matricula" },
  DOCENTES_ACTIVOS: { icono: "Administración", enlace: "/administracion" },
  MATRICULAS_ACTIVAS: { icono: "Matrícula", enlace: "/matricula" },
  INASISTENCIAS_HOY: { icono: "Asistencia" },
  PAGOS_VENCIDOS: { icono: "Finanzas", enlace: "/finanzas" },
  ALERTAS_ACTIVAS: { icono: "Académico", enlace: "/academico/estudiantes-en-riesgo" },
  NOTIFICACIONES_NO_LEIDAS: { icono: "Inicio" },
};

const DESTINO_PREDETERMINADO: DestinoIndicador = { icono: "Administración" };

export const destinoDeIndicador = (codigo: string) => DESTINOS[codigo] ?? DESTINO_PREDETERMINADO;

package com.asiscontrol.dto.asistencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class ReporteAsistenciaDtos {
    private ReporteAsistenciaDtos() { }
    public enum Tipo { ESTUDIANTES, DOCENTES }
    public enum Estado { PRESENTE, TARDANZA, INASISTENCIA, SIN_MARCACION }
    public record Indicador(Estado estado, long cantidad, BigDecimal porcentaje) { }
    public record Registro(long id, long personaId, String persona, LocalDate fecha, LocalTime hora,
                           String curso, String seccion, String estado, String origen, Long sesionId) { }
    public record Informe(long total, List<Indicador> indicadores, List<Registro> registros,
                          int pagina, int totalPaginas, long totalDetalle) { }
    public record Opcion(long id, String nombre) { }
    public record Filtros(List<Opcion> periodos, List<Opcion> secciones) { }
}

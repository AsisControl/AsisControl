package com.asiscontrol.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class DemostracionDtos {
    private DemostracionDtos() {}
    public enum Etapa { INICIAL, HABILITAR_MATRICULAS, ESCENARIOS }
    public record Resultado(String paquete, Etapa etapa, boolean yaEjecutada,
            LocalDate fechaReferencia, Map<String, Long> recursos, List<String> indicaciones) {}
    public record Estado(String paquete, List<Resultado> etapas) {}
}

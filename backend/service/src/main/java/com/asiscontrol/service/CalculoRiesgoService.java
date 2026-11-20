package com.asiscontrol.service;

import com.asiscontrol.dto.RiesgoDto;
import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ResourceNotFoundException;


import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import static com.asiscontrol.dto.RiesgoDto.Resultado.*;

@Service
public class CalculoRiesgoService {
    private final EntityManager em;
    private final EvidenciaRiesgoService evidencia;


    public CalculoRiesgoService(EntityManager em, EvidenciaRiesgoService evidencia) {
        this.em = em;
        this.evidencia = evidencia;
    }

    @Transactional
    public RiesgoDto.EvaluacionResponse recalcular(RiesgoDto.RecalculoRequest request) {
        // Serializa los recalculos del mismo alumno: no puede haber dos alertas abiertas duplicadas.
        Alumno alumno = em.find(Alumno.class, request.idAlumno(), LockModeType.PESSIMISTIC_WRITE);
        if (alumno == null || !alumno.isActivo())
            throw new ResourceNotFoundException("ALUMNO_NO_ENCONTRADO", "Alumno inexistente o inactivo");
        PeriodoAcademico periodo = em.find(PeriodoAcademico.class, request.idPeriodo());
        if (periodo == null || !periodo.isActivo() || !periodo.getAnioAcademico().isActivo())
            throw new ResourceNotFoundException("PERIODO_NO_ENCONTRADO", "Periodo inexistente o inactivo");
        var matriculas = em.createQuery("""
                from Matricula m where m.alumno.id = :alumno and m.activo = true
                and m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                and m.anioAcademico = :anio
                """, Matricula.class).setParameter("alumno", alumno.getId())
                .setParameter("anio", periodo.getAnioAcademico().getAnio()).getResultList();
        if (matriculas.size() != 1)
            throw new BusinessRuleException("MATRICULA_REQUERIDA", "Se requiere una matricula vigente en el anio del periodo");
        Seccion seccion = matriculas.getFirst().getSeccion();
        var mediciones = calcularIndicadores(alumno, periodo, seccion);
        List<RiesgoDto.ResultadoRegla> resultados = new ArrayList<>();
        var criterios = em.createQuery("from CriterioRiesgo c where c.activo = true order by c.id", CriterioRiesgo.class)
                .getResultList();
        for (CriterioRiesgo criterio : criterios) {
            Medicion medicion = mediciones.get(criterio.getIndicador());
            int minimoDatos = evidencia.minimoDatos(criterio.getId());
            boolean suficiente = medicion.valor() != null && medicion.cantidad() >= minimoDatos;
            var resultado = !suficiente ? DATOS_INSUFICIENTES
                    : cumple(criterio.getOperador(), medicion.valor(), criterio.getUmbral()) ? EN_RIESGO : SIN_RIESGO;
            var abiertas = em.createQuery("""
                    from AlertaRiesgo a where a.alumno.id = :alumno and a.criterio.id = :criterio
                    and a.activo = true
                    and a.estado <> com.asiscontrol.entity.enums.EstadoAlerta.RESUELTA
                    """, AlertaRiesgo.class).setParameter("alumno", alumno.getId())
                    .setParameter("criterio", criterio.getId()).getResultList();
            AlertaRiesgo alerta = abiertas.stream().filter(a -> {
                var anterior = evidencia.ultima(a.getId());
                return anterior != null && anterior.idPeriodo().equals(periodo.getId());
            }).findFirst().orElse(null);
            String motivo = !suficiente
                    ? "No se puede determinar el riesgo: " + medicion.cantidad() + " datos disponibles; minimo " + minimoDatos
                    : "Indicador " + criterio.getIndicador() + ": valor " + medicion.valor().toPlainString()
                        + "; regla " + criterio.getOperador() + " " + criterio.getUmbral().toPlainString()
                        + "; resultado " + resultado + "; periodo " + periodo.getNombre();
            if (resultado == EN_RIESGO) {
                if (alerta == null) {
                    alerta = new AlertaRiesgo();
                    alerta.setAlumno(alumno); alerta.setCriterio(criterio);
                    alerta.setFechaDeteccion(Instant.now());
                }

                alerta.setValorDetectado(medicion.valor());

                alerta.setNivelRiesgo(criterio.getNivelRiesgo());
                alerta.setMensaje(motivo); alerta.setFechaAtencion(null);
                alerta.setObservacion(null);
                if (alerta.getId() == null) em.persist(alerta);
            } else if (alerta != null) {

                alerta.setObservacion(motivo);
                if (suficiente) {
                    // Se conserva el valor y motivo de deteccion. El valor de cierre queda en el historial.
                    alerta.setEstado(EstadoAlerta.RESUELTA); alerta.setFechaAtencion(Instant.now());
                }
            }
            var detalle = new RiesgoDto.ResultadoRegla(criterio.getId(), criterio.getNombre(), criterio.getNivelRiesgo(),
                    criterio.getIndicador(), resultado,
                    medicion.valor(), criterio.getUmbral(), criterio.getOperador(), medicion.cantidad(),
                    minimoDatos, motivo, medicion.evidencias(), alerta == null ? null : alerta.getId());
            evidencia.guardar(new EvidenciaRiesgoService.Instantanea(1, alumno.getId(), periodo.getId(),
                    periodo.getNombre(), seccion.getId(), seccion.getGrado(), seccion.getNombre(), detalle));
            resultados.add(detalle);
        }
        var estado = resultados.stream().anyMatch(r -> r.resultado() == EN_RIESGO) ? EN_RIESGO
                : resultados.isEmpty() || resultados.stream().anyMatch(r -> r.resultado() == DATOS_INSUFICIENTES)
                ? DATOS_INSUFICIENTES : SIN_RIESGO;
        return new RiesgoDto.EvaluacionResponse(alumno.getId(), periodo.getId(), estado, resultados);
    }

    static boolean cumple(OperadorComparacion operador, BigDecimal valor, BigDecimal umbral) {
        int c = valor.compareTo(umbral);
        return switch (operador) {
            case MENOR_QUE -> c < 0; case MENOR_IGUAL -> c <= 0;
            case MAYOR_QUE -> c > 0; case MAYOR_IGUAL -> c >= 0; case IGUAL -> c == 0;
        };
    }

    private record Medicion(BigDecimal valor, int cantidad, List<String> evidencias) {}

    private Map<IndicadorRiesgo, Medicion> calcularIndicadores(Alumno alumno, PeriodoAcademico periodo, Seccion seccion) {
        Map<IndicadorRiesgo, Medicion> datos = new EnumMap<>(IndicadorRiesgo.class);
        var notas = em.createQuery("""
                from Calificacion c where c.activo = true and c.alumno.id = :alumno
                and c.evaluacion.activo = true and c.evaluacion.asignacionCurso.activo = true
                and c.evaluacion.asignacionCurso.seccion.id = :seccion
                and c.evaluacion.estado in (com.asiscontrol.entity.enums.EstadoEvaluacion.PUBLICADA,
                    com.asiscontrol.entity.enums.EstadoEvaluacion.CERRADA)
                and c.evaluacion.fechaEvaluacion between :inicio and :fin order by c.id
                """, Calificacion.class).setParameter("alumno", alumno.getId()).setParameter("seccion", seccion.getId())
                .setParameter("inicio", periodo.getFechaInicio()).setParameter("fin", periodo.getFechaFin()).getResultList();
        // Misma escala y ponderacion del consolidado: C=1, B=2, A=3, AD=4.
        Map<String, BigDecimal[]> competencias = new LinkedHashMap<>();
        List<String> evidenciaNotas = new ArrayList<>();
        for (Calificacion nota : notas) {
            var peso = nota.getEvaluacion().getPonderacionPorcentaje();
            if (peso == null || peso.signum() <= 0) peso = BigDecimal.ONE;
            String clave = nota.getEvaluacion().getAsignacionCurso().getId() + ":" + nota.getCompetencia().getId();
            var acumulado = competencias.computeIfAbsent(clave, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            acumulado[0] = acumulado[0].add(BigDecimal.valueOf(nota.getNivelLogro().getValor()).multiply(peso));
            acumulado[1] = acumulado[1].add(peso);
            evidenciaNotas.add("calificacion=" + nota.getId() + ",curso:competencia=" + clave
                    + ",nivel=" + nota.getNivelLogro() + ",peso=" + peso);
        }
        var promedios = competencias.values().stream().map(a -> a[0].divide(a[1], 2, RoundingMode.HALF_UP)).toList();
        BigDecimal promedio = promedios.isEmpty() ? null : promedios.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(promedios.size()), 4, RoundingMode.HALF_UP);
        datos.put(IndicadorRiesgo.PROMEDIO_ACADEMICO, new Medicion(promedio, notas.size(), evidenciaNotas));
        datos.put(IndicadorRiesgo.COMPETENCIAS_EN_INICIO, new Medicion(promedios.isEmpty() ? null
                : BigDecimal.valueOf(promedios.stream().filter(p -> NivelLogro.desdePromedio(p.doubleValue()) == NivelLogro.C).count()),
                notas.size(), evidenciaNotas));
        var asistencias = em.createQuery("""
                from AsistenciaAlumno a where a.activo = true and a.alumno.id = :alumno
                and a.asignacionCurso.activo = true and a.asignacionCurso.seccion.id = :seccion
                and a.sesionClase.activo = true and a.sesionClase.fecha between :inicio and :fin
                and a.sesionClase.estado = com.asiscontrol.entity.asistencia.EstadoSesionClase.TOMADA
                and a.estadoAsistencia <> com.asiscontrol.entity.enums.EstadoAsistencia.SIN_MARCACION
                order by a.id
                """, AsistenciaAlumno.class).setParameter("alumno", alumno.getId()).setParameter("seccion", seccion.getId())
                .setParameter("inicio", periodo.getFechaInicio()).setParameter("fin", periodo.getFechaFin()).getResultList();
        var evidenciaAsistencia = asistencias.stream().map(a -> "asistencia=" + a.getId()
                + ",fecha=" + a.getSesionClase().getFecha() + ",estado=" + a.getEstadoAsistencia()).toList();
        for (var indicador : List.of(IndicadorRiesgo.INASISTENCIAS, IndicadorRiesgo.TARDANZAS)) {
            EstadoAsistencia estado = indicador == IndicadorRiesgo.INASISTENCIAS ? EstadoAsistencia.INASISTENCIA : EstadoAsistencia.TARDANZA;
            datos.put(indicador, new Medicion(asistencias.isEmpty() ? null
                    : BigDecimal.valueOf(asistencias.stream().filter(a -> a.getEstadoAsistencia() == estado).count()),
                    asistencias.size(), evidenciaAsistencia));
        }
        var tareas = em.createQuery("""
                from Tarea t where t.activo = true and t.asignacionCurso.activo = true
                and t.asignacionCurso.seccion.id = :seccion
                and t.estadoPublicacion in (com.asiscontrol.entity.enums.EstadoPublicacionTarea.PUBLICADA,
                    com.asiscontrol.entity.enums.EstadoPublicacionTarea.CERRADA)
                and t.fechaLimite >= :inicio and t.fechaLimite < :fin and t.fechaLimite < :ahora
                order by t.id
                """, Tarea.class).setParameter("seccion", seccion.getId())
                .setParameter("inicio", periodo.getFechaInicio().atStartOfDay())
                .setParameter("fin", periodo.getFechaFin().plusDays(1).atStartOfDay())
                .setParameter("ahora", LocalDateTime.now(ZoneId.of("America/Lima"))).getResultList();
        var entregas = em.createQuery("""
                select e.tarea.id from EntregaTarea e where e.activo = true and e.alumno.id = :alumno
                and e.estado in (com.asiscontrol.entity.enums.EstadoTarea.ENTREGADO,
                    com.asiscontrol.entity.enums.EstadoTarea.ENTREGADO_TARDE, com.asiscontrol.entity.enums.EstadoTarea.CALIFICADO)
                """, Long.class).setParameter("alumno", alumno.getId()).getResultList();
        Set<Long> entregadas = new HashSet<>(entregas);
        datos.put(IndicadorRiesgo.TAREAS_NO_ENTREGADAS, new Medicion(tareas.isEmpty() ? null
                : BigDecimal.valueOf(tareas.stream().filter(t -> !entregadas.contains(t.getId())).count()), tareas.size(),
                tareas.stream().map(t -> "tarea=" + t.getId() + ",limite=" + t.getFechaLimite()
                        + ",entregada=" + entregadas.contains(t.getId())).toList()));
        return datos;
    }

    @Transactional(readOnly = true)
    public List<RiesgoDto.HistorialResponse> historial(Long idAlerta) {
        return evidencia.registrosAlerta(idAlerta).stream().map(r ->
                new RiesgoDto.HistorialResponse(r.getId(), r.getFechaHora(), evidencia.leer(r).evaluacion())).toList();
    }

    private record Grupo(Long periodo, int grado, Long seccion, String nombre,
            IndicadorRiesgo causa, NivelRiesgo nivel) {}

    @Transactional(readOnly = true)
    public List<RiesgoDto.ResumenResponse> resumen(Long idPeriodo, Long docentePersonaId) {
        var alertas = em.createQuery("""
                from AlertaRiesgo a where a.activo = true
                and a.estado <> com.asiscontrol.entity.enums.EstadoAlerta.RESUELTA
                and (:docente is null or exists (
                    select ac.id from Matricula m, AsignacionCurso ac where m.activo = true
                    and m.estado = com.asiscontrol.entity.enums.EstadoMatricula.ACTIVA
                    and m.alumno.id = a.alumno.id and ac.activo = true
                    and ac.seccion.id = m.seccion.id and ac.docente.persona.id = :docente))
                order by a.id
                """, AlertaRiesgo.class).setParameter("docente", docentePersonaId).getResultList();
        Map<Grupo, List<AlertaRiesgo>> grupos = new LinkedHashMap<>();
        for (var alerta : alertas) {
            var ultima = evidencia.ultima(alerta.getId());
            if (ultima == null || !ultima.idPeriodo().equals(idPeriodo)
                    || ultima.evaluacion().resultado() != EN_RIESGO) continue;
            Grupo grupo = new Grupo(ultima.idPeriodo(), ultima.grado(), ultima.idSeccion(), ultima.seccion(),
                    ultima.evaluacion().indicador(), ultima.evaluacion().nivel());
            grupos.computeIfAbsent(grupo, g -> new ArrayList<>()).add(alerta);
        }
        return grupos.entrySet().stream().map(e -> new RiesgoDto.ResumenResponse(e.getKey().periodo(),
                e.getKey().grado(), e.getKey().seccion(), e.getKey().nombre(), e.getKey().causa(), e.getKey().nivel(),
                e.getValue().size(), e.getValue().stream().map(a -> a.getAlumno().getId()).distinct().count())).toList();
    }
}
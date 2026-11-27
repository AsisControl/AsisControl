package com.asiscontrol.service;

import com.asiscontrol.dto.CalificacionDto;
import com.asiscontrol.dto.ReglaCalificacionDto;
import com.asiscontrol.entity.AsignacionCurso;
import com.asiscontrol.entity.Competencia;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.entity.GradoAcademico;
import com.asiscontrol.entity.ReglaCalificacion;
import com.asiscontrol.entity.enums.NivelLogro;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.entity.enums.TipoEvaluacion;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AsignacionCursoRepository;
import com.asiscontrol.repository.GradoAcademicoRepository;
import com.asiscontrol.repository.ReglaCalificacionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReglaCalificacionService {
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final ReglaCalificacionRepository reglas;
    private final GradoAcademicoRepository grados;
    private final AsignacionCursoRepository asignaciones;
    private final ModuloAccessGuard acceso;
    private final AuditoriaService auditoria;
    private final EscalaInstitucionalService escalas;
    private final EntityManager em;

    public ReglaCalificacionService(ReglaCalificacionRepository reglas, GradoAcademicoRepository grados,
            AsignacionCursoRepository asignaciones, ModuloAccessGuard acceso, AuditoriaService auditoria,
            EntityManager em, EscalaInstitucionalService escalas) {
        this.reglas = reglas;
        this.grados = grados;
        this.asignaciones = asignaciones;
        this.acceso = acceso;
        this.auditoria = auditoria;
        this.em = em;
        this.escalas = escalas;
    }

    @Transactional(readOnly = true)
    public List<ReglaCalificacionDto.Regla> historial(Long curso, Long grado) {
        return reglas.findByCursoIdAndGradoIdOrderByNumeroVersionDesc(curso, grado).stream()
                .map(this::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public Optional<ReglaCalificacionDto.Regla> vigentePorAsignacion(Long id) {
        var asignacion = asignaciones.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("ASIGNACION_NO_ENCONTRADA", "Asignación no encontrada"));
        acceso.verificarAsignacionDocente(asignacion);
        return vigente(asignacion).map(this::respuesta);
    }

    Optional<ReglaCalificacion> vigente(AsignacionCurso asignacion) {
        var seccion = asignacion.getSeccion();
        return grados.findByAnioAcademicoAnioAndNumero(seccion.getAnioAcademico(), seccion.getGrado())
                .flatMap(grado -> vigente(asignacion.getCurso().getId(), grado.getId()));
    }

    Optional<ReglaCalificacion> vigenteParaLibreta(AsignacionCurso asignacion) {
        bloquearCurso(asignacion.getCurso().getId());
        return vigente(asignacion);
    }

    @Transactional
    public ReglaCalificacionDto.Regla guardarBorrador(ReglaCalificacionDto.Borrador request) {
        validarTipos(request.componentes());
        var curso = bloquearCurso(request.idCurso());
        var grado = gradoDelCurso(request.idGrado(), curso);
        var historial = reglas.findByCursoIdAndGradoIdOrderByNumeroVersionDesc(curso.getId(), grado.getId());
        var regla = historial.stream().filter(item -> item.getEstado() == ReglaCalificacion.Estado.BORRADOR)
                .findFirst().orElse(null);
        verificarConfiguracionVigente(historial.stream().filter(item -> item.getEstado() == ReglaCalificacion.Estado.VIGENTE)
                .findFirst().orElse(null), request.formula(), request.componentes());
        if (regla != null && request.version() == null && mismaConfiguracion(regla, request.formula(), request.componentes())) {
            return respuesta(regla);
        }
        String anterior = null;
        if (regla == null) {
            if (request.version() != null) throw versionDesactualizada();
            regla = new ReglaCalificacion();
            regla.setCurso(curso);
            regla.setGrado(grado);
            regla.setNumeroVersion(historial.isEmpty() ? 1 : historial.getFirst().getNumeroVersion() + 1);
        } else {
            verificarVersion(regla, request.version());
            if (mismaConfiguracion(regla, request.formula(), request.componentes())) return respuesta(regla);
            anterior = describir(regla);
        }
        regla.setFormula(request.formula());
        regla.getComponentes().clear();
        for (var componente : request.componentes()) {
            regla.getComponentes().put(componente.tipoEvaluacion(),
                    new ReglaCalificacion.Componente(componente.nombre().trim(), componente.peso()));
        }
        var guardada = reglas.saveAndFlush(regla);
        var id = guardada.getId();
        auditar("GUARDAR_BORRADOR_REGLA_CALIFICACION", id, null, anterior, describir(guardada));
        return respuesta(guardada);
    }

    @Transactional
    public ReglaCalificacionDto.Regla activar(Long id, long version, String motivo) {
        var regla = reglas.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("REGLA_NO_ENCONTRADA", "Regla no encontrada"));
        bloquearCurso(regla.getCurso().getId());
        em.refresh(regla);
        gradoDelCurso(regla.getGrado().getId(), regla.getCurso());
        verificarVersion(regla, version);
        if (regla.getEstado() != ReglaCalificacion.Estado.BORRADOR) {
            throw new BusinessRuleException("REGLA_NO_EDITABLE", "Solo se puede activar un borrador");
        }
        validarSuma(componentes(regla));
        var anterior = vigente(regla.getCurso().getId(), regla.getGrado().getId()).orElse(null);
        verificarConfiguracionVigente(anterior, regla.getFormula(), componentes(regla));
        if (anterior != null) anterior.setEstado(ReglaCalificacion.Estado.HISTORICA);
        regla.setEstado(ReglaCalificacion.Estado.VIGENTE);
        em.flush();
        if (anterior != null) auditar("ARCHIVAR_REGLA_CALIFICACION", anterior.getId(), motivo.strip(),
                "VIGENTE, " + describir(anterior), "HISTORICA");
        auditar("ACTIVAR_REGLA_CALIFICACION", id, motivo.strip(), "BORRADOR", "VIGENTE, " + describir(regla));
        return respuesta(regla);
    }

    private boolean mismaConfiguracion(ReglaCalificacion regla, ReglaCalificacion.Formula formula,
                                       List<ReglaCalificacionDto.Componente> componentes) {
        return regla.getFormula() == formula && regla.getComponentes().size() == componentes.size()
                && componentes.stream().allMatch(componente -> {
                    var guardado = regla.getComponentes().get(componente.tipoEvaluacion());
                    return guardado != null && guardado.getNombre().strip().equals(componente.nombre().strip())
                            && guardado.getPeso().compareTo(componente.peso()) == 0;
                });
    }

    private void verificarConfiguracionVigente(ReglaCalificacion vigente, ReglaCalificacion.Formula formula,
                                               List<ReglaCalificacionDto.Componente> componentes) {
        if (vigente != null && mismaConfiguracion(vigente, formula, componentes)) {
            throw new BusinessRuleException("REGLA_CONFIGURACION_VIGENTE",
                    "Esta configuración coincide con la versión vigente. No es necesario crear una nueva versión.");
        }
    }

    public ReglaCalificacionDto.Resultado vistaPrevia(ReglaCalificacionDto.VistaPrevia request) {
        validarTipos(request.componentes());
        validarSuma(request.componentes());
        return request.formula() == ReglaCalificacion.Formula.CUALITATIVA_DINAMICA_V1
                ? calcularCualitativo(request.componentes(), request.muestra())
                : calcular(request.componentes(), request.muestra());
    }

    @Transactional(readOnly = true)
    public ReglaCalificacionDto.ResultadoNumerico vistaPreviaNumerica(ReglaCalificacionDto.VistaPreviaNumerica request) {
        validarTipos(request.componentes());
        validarSuma(request.componentes());
        return calcularNumerico(request.componentes(), request.muestra(), escalas.exigirVigente());
    }

    static ReglaCalificacionDto.ResultadoNumerico calcularNumerico(List<ReglaCalificacionDto.Componente> componentes,
            List<ReglaCalificacionDto.MuestraNumerica> muestra, com.asiscontrol.entity.EscalaInstitucional escala) {
        var notasPorTipo = new EnumMap<TipoEvaluacion, List<BigDecimal>>(TipoEvaluacion.class);
        for (var nota : muestra) {
            if (nota.nota() != null) notasPorTipo.computeIfAbsent(nota.tipoEvaluacion(), tipo -> new ArrayList<>()).add(nota.nota());
        }
        var faltantes = new ArrayList<String>();
        BigDecimal numerador = BigDecimal.ZERO;
        BigDecimal denominador = BigDecimal.ONE;
        int cantidad = 0;
        for (var componente : componentes) {
            var notas = notasPorTipo.get(componente.tipoEvaluacion());
            if (notas == null) {
                faltantes.add(componente.nombre());
                continue;
            }
            cantidad += notas.size();
            var suma = notas.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            var cantidadDelComponente = BigDecimal.valueOf(notas.size());
            // Conserva la fracción exacta para no redondear promedios intermedios.
            numerador = numerador.multiply(cantidadDelComponente)
                    .add(suma.multiply(componente.peso()).multiply(denominador));
            denominador = denominador.multiply(cantidadDelComponente);
        }
        if (!faltantes.isEmpty()) return new ReglaCalificacionDto.ResultadoNumerico(
                null, null, null, escala.getNumeroVersion(), cantidad, faltantes);
        var divisor = denominador.multiply(CIEN);
        int redondeada = numerador.divide(divisor, 0, RoundingMode.HALF_UP).intValueExact();
        var nivel = EscalaInstitucionalService.nivel(escala, redondeada);
        // Solo se aproxima la representación de un promedio periódico, después de convertir el nivel.
        var promedioVisible = numerador.divide(divisor, 4, RoundingMode.HALF_UP).stripTrailingZeros();
        return new ReglaCalificacionDto.ResultadoNumerico(
                promedioVisible, redondeada, nivel, escala.getNumeroVersion(), cantidad, List.of());
    }

    record NotaCompetencia(Competencia competencia, ReglaCalificacionDto.Muestra nota, BigDecimal notaNumerica,
                           boolean modeloCualitativo) {
        NotaCompetencia(Competencia competencia, ReglaCalificacionDto.Muestra nota, BigDecimal notaNumerica) {
            this(competencia, nota, notaNumerica, false);
        }
    }

    List<CalificacionDto.ConsolidadoResponse> consolidar(ReglaCalificacion regla, Long asignacion,
                                                         List<NotaCompetencia> notas) {
        var escala = notas.stream().anyMatch(nota -> nota.notaNumerica() != null) ? escalas.vigente().orElse(null) : null;
        return consolidar(regla, asignacion, notas, escala);
    }

    List<CalificacionDto.ConsolidadoResponse> consolidar(ReglaCalificacion regla, Long asignacion,
            List<NotaCompetencia> notas, com.asiscontrol.entity.EscalaInstitucional escala) {
        var porCompetencia = new LinkedHashMap<Long, List<NotaCompetencia>>();
        for (var nota : notas) {
            porCompetencia.computeIfAbsent(nota.competencia().getId(), id -> new ArrayList<>()).add(nota);
        }
        var componentes = componentes(regla);
        return porCompetencia.values().stream().map(items -> {
            var competencia = items.getFirst().competencia();
            var elegibles = items.stream().filter(item -> regla.getComponentes().containsKey(item.nota().tipoEvaluacion())).toList();
            if (regla.getFormula() == ReglaCalificacion.Formula.CUALITATIVA_DINAMICA_V1) {
                var resultado = elegibles.stream().anyMatch(item -> item.notaNumerica() != null)
                        ? new ReglaCalificacionDto.Resultado(null, null, 0, List.of("Transición entre modelos de calificación"))
                        : calcularCualitativo(componentes, elegibles.stream().map(NotaCompetencia::nota).toList());
                return new CalificacionDto.ConsolidadoResponse(competencia.getId(), competencia.getCodigo(),
                        competencia.getNombre(), resultado.promedioPonderado(), resultado.nivelConsolidado(),
                        resultado.evaluacionesConsideradas(), asignacion, regla.getNumeroVersion(), resultado.componentesFaltantes(), null);
            }
            if (elegibles.stream().anyMatch(NotaCompetencia::modeloCualitativo)) {
                return new CalificacionDto.ConsolidadoResponse(competencia.getId(), competencia.getCodigo(),
                        competencia.getNombre(), null, null, 0, asignacion, regla.getNumeroVersion(),
                        List.of("Regla cualitativa dinámica vigente"), null);
            }
            if (elegibles.stream().anyMatch(item -> item.notaNumerica() != null)) {
                int cantidad = (int) elegibles.stream().filter(item -> item.notaNumerica() != null).count();
                boolean historicas = elegibles.stream().anyMatch(item -> item.notaNumerica() == null && item.nota().nivel() != null);
                if (historicas || escala == null) {
                    return new CalificacionDto.ConsolidadoResponse(competencia.getId(), competencia.getCodigo(),
                            competencia.getNombre(), null, null, cantidad, asignacion, regla.getNumeroVersion(),
                            List.of(historicas ? "Transición de niveles históricos a notas numéricas"
                                    : "Escala institucional vigente"), null);
                }
                var resultado = calcularNumerico(componentes, elegibles.stream().map(item ->
                        new ReglaCalificacionDto.MuestraNumerica(item.nota().tipoEvaluacion(), item.notaNumerica())).toList(), escala);
                return new CalificacionDto.ConsolidadoResponse(competencia.getId(), competencia.getCodigo(),
                        competencia.getNombre(), resultado.promedioPonderado(), resultado.nivelConsolidado(),
                        resultado.evaluacionesConsideradas(), asignacion, regla.getNumeroVersion(),
                        resultado.componentesFaltantes(), resultado.numeroVersionEscala());
            }
            var resultado = calcular(componentes, items.stream().map(NotaCompetencia::nota).toList());
            return new CalificacionDto.ConsolidadoResponse(competencia.getId(), competencia.getCodigo(),
                    competencia.getNombre(), resultado.promedioPonderado(), resultado.nivelConsolidado(),
                    resultado.evaluacionesConsideradas(), asignacion, regla.getNumeroVersion(),
                    resultado.componentesFaltantes(), null);
        }).sorted(Comparator.comparing(CalificacionDto.ConsolidadoResponse::nombreCompetencia, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    static BigDecimal pesoEfectivo(BigDecimal pesoComponente, long nivelesValidos) {
        return nivelesValidos == 0 ? null : pesoComponente.divide(BigDecimal.valueOf(nivelesValidos), 10, RoundingMode.HALF_UP);
    }

    Map<Long, BigDecimal> pesosDinamicos(com.asiscontrol.entity.Evaluacion evaluacion, ReglaCalificacion regla) {
        if (!evaluacion.isCuentaResultado() || !regla.getComponentes().containsKey(evaluacion.getTipoEvaluacion())) return Map.of();
        var periodos = em.createQuery("""
                from PeriodoAcademico p where p.activo = true and p.anioAcademico.activo = true
                  and p.anioAcademico.anio = :anio and :fecha between p.fechaInicio and p.fechaFin
                """, com.asiscontrol.entity.planificacion.PeriodoAcademico.class)
                .setParameter("anio", evaluacion.getAsignacionCurso().getSeccion().getAnioAcademico())
                .setParameter("fecha", evaluacion.getFechaEvaluacion()).getResultList();
        if (periodos.size() != 1) return Map.of();
        var periodo = periodos.getFirst();
        var filas = em.createQuery("""
                select e.id, c.id, e.tipoEvaluacion from Evaluacion e join e.competencias c
                  where e.activo = true and e.cuentaResultado = true and e.asignacionCurso.id = :curso
                    and e.fechaEvaluacion between :inicio and :fin
                    and (e.estado in (com.asiscontrol.entity.enums.EstadoEvaluacion.PUBLICADA,
                                      com.asiscontrol.entity.enums.EstadoEvaluacion.CERRADA)
                      or (e.estado = com.asiscontrol.entity.enums.EstadoEvaluacion.BORRADOR
                        and exists (select t.id from Tarea t where t.activo = true and t.evaluacionAcademica = e
                          and (t.fechaPublicacion is null or t.fechaPublicacion <= :ahora)
                          and t.estadoPublicacion in (com.asiscontrol.entity.enums.EstadoPublicacionTarea.PUBLICADA,
                              com.asiscontrol.entity.enums.EstadoPublicacionTarea.CERRADA,
                              com.asiscontrol.entity.enums.EstadoPublicacionTarea.ARCHIVADA))))
                """, Object[].class).setParameter("curso", evaluacion.getAsignacionCurso().getId())
                .setParameter("inicio", periodo.getFechaInicio()).setParameter("fin", periodo.getFechaFin())
                .setParameter("ahora", java.time.LocalDateTime.now()).getResultList();
        Map<Long, Map<TipoEvaluacion, Long>> cantidades = new HashMap<>();
        var incluye = new java.util.HashSet<Long>();
        for (var fila : filas) {
            var tipo = (TipoEvaluacion) fila[2];
            if (!regla.getComponentes().containsKey(tipo)) continue;
            Long competencia = (Long) fila[1];
            cantidades.computeIfAbsent(competencia, id -> new EnumMap<>(TipoEvaluacion.class)).merge(tipo, 1L, Long::sum);
            if (evaluacion.getId().equals(fila[0])) incluye.add(competencia);
        }
        Map<Long, BigDecimal> resultado = new HashMap<>();
        for (Long competencia : incluye) {
            var porTipo = cantidades.get(competencia);
            var total = porTipo.keySet().stream().map(tipo -> regla.getComponentes().get(tipo).getPeso())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            var tipo = evaluacion.getTipoEvaluacion();
            resultado.put(competencia, pesoNormalizado(regla.getComponentes().get(tipo).getPeso(), total, porTipo.get(tipo)));
        }
        return resultado;
    }

    static BigDecimal pesoNormalizado(BigDecimal peso, BigDecimal total, long cantidad) {
        return peso.multiply(CIEN).divide(total.multiply(BigDecimal.valueOf(cantidad)), 10, RoundingMode.HALF_UP);
    }

    static ReglaCalificacionDto.Resultado calcularCualitativo(List<ReglaCalificacionDto.Componente> componentes,
            List<ReglaCalificacionDto.Muestra> muestra) {
        Map<TipoEvaluacion, BigDecimal> pesos = new EnumMap<>(TipoEvaluacion.class);
        componentes.forEach(componente -> pesos.put(componente.tipoEvaluacion(), componente.peso()));
        Map<TipoEvaluacion, List<ReglaCalificacionDto.Muestra>> grupos = new EnumMap<>(TipoEvaluacion.class);
        muestra.stream().filter(item -> pesos.containsKey(item.tipoEvaluacion())).forEach(item ->
                grupos.computeIfAbsent(item.tipoEvaluacion(), tipo -> new ArrayList<>()).add(item));
        var pendientes = grupos.entrySet().stream().filter(grupo -> grupo.getValue().stream().anyMatch(item -> item.nivel() == null))
                .map(grupo -> grupo.getKey().name()).toList();
        int cantidad = grupos.values().stream().flatMap(List::stream).mapToInt(item -> item.nivel() == null ? 0 : 1).sum();
        if (grupos.isEmpty() || !pendientes.isEmpty()) {
            return new ReglaCalificacionDto.Resultado(null, null, cantidad, pendientes);
        }
        // Denominador común para comparar los cortes sin aproximar promedios intermedios.
        BigDecimal comun = grupos.values().stream().map(grupo -> BigDecimal.valueOf(grupo.size()))
                .reduce(BigDecimal.ONE, BigDecimal::multiply);
        BigDecimal numerador = BigDecimal.ZERO;
        BigDecimal pesoTotal = BigDecimal.ZERO;
        for (var grupo : grupos.entrySet()) {
            var peso = pesos.get(grupo.getKey());
            var valores = grupo.getValue();
            int suma = valores.stream().mapToInt(item -> item.nivel().getValor()).sum();
            numerador = numerador.add(peso.multiply(BigDecimal.valueOf(suma)).multiply(comun.divide(BigDecimal.valueOf(valores.size()))));
            pesoTotal = pesoTotal.add(peso);
        }
        BigDecimal denominador = pesoTotal.multiply(comun);
        NivelLogro nivel = numerador.compareTo(denominador.multiply(new BigDecimal("3.5"))) >= 0 ? NivelLogro.AD
                : numerador.compareTo(denominador.multiply(new BigDecimal("2.5"))) >= 0 ? NivelLogro.A
                : numerador.compareTo(denominador.multiply(new BigDecimal("1.5"))) >= 0 ? NivelLogro.B : NivelLogro.C;
        BigDecimal sumaPesos = pesoTotal;
        var detalle = grupos.entrySet().stream().map(grupo -> {
            var valores = grupo.getValue();
            int suma = valores.stream().mapToInt(item -> item.nivel().getValor()).sum();
            return new ReglaCalificacionDto.ComponenteSimulado(grupo.getKey(), valores.size(),
                    BigDecimal.valueOf(suma).divide(BigDecimal.valueOf(valores.size()), 10, RoundingMode.HALF_UP),
                    pesoNormalizado(pesos.get(grupo.getKey()), sumaPesos, 1));
        }).toList();
        return new ReglaCalificacionDto.Resultado(numerador.divide(denominador, 10, RoundingMode.HALF_UP), nivel, cantidad, List.of(), detalle);
    }

    private ReglaCalificacionDto.Resultado calcular(List<ReglaCalificacionDto.Componente> componentes,
            List<ReglaCalificacionDto.Muestra> muestra) {
        Map<TipoEvaluacion, List<NivelLogro>> niveles = new EnumMap<>(TipoEvaluacion.class);
        for (var nota : muestra) {
            if (nota.nivel() != null) niveles.computeIfAbsent(nota.tipoEvaluacion(), tipo -> new ArrayList<>()).add(nota.nivel());
        }
        var faltantes = new ArrayList<String>();
        BigDecimal total = BigDecimal.ZERO;
        int cantidad = 0;
        for (var componente : componentes) {
            var valores = niveles.get(componente.tipoEvaluacion());
            if (valores == null) {
                faltantes.add(componente.nombre());
                continue;
            }
            cantidad += valores.size();
            int suma = valores.stream().mapToInt(NivelLogro::getValor).sum();
            total = total.add(BigDecimal.valueOf(suma).multiply(componente.peso())
                    .divide(BigDecimal.valueOf(valores.size()), 10, RoundingMode.HALF_UP));
        }
        if (!faltantes.isEmpty()) return new ReglaCalificacionDto.Resultado(null, null, cantidad, faltantes);
        BigDecimal promedio = total.divide(CIEN, 2, RoundingMode.HALF_UP);
        return new ReglaCalificacionDto.Resultado(promedio, NivelLogro.desdePromedio(promedio.doubleValue()), cantidad, List.of());
    }

    private Optional<ReglaCalificacion> vigente(Long curso, Long grado) {
        return reglas.findByCursoIdAndGradoIdAndEstado(curso, grado, ReglaCalificacion.Estado.VIGENTE);
    }

    private Curso bloquearCurso(Long id) {
        var curso = em.find(Curso.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (curso == null) throw new ResourceNotFoundException("CURSO_NO_ENCONTRADO", "Curso no encontrado");
        return curso;
    }

    private GradoAcademico gradoDelCurso(Long id, Curso curso) {
        var grado = grados.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GRADO_NO_ENCONTRADO", "Grado no encontrado"));
        if (!grado.isActivo() || !grado.getAnioAcademico().isActivo() || grado.getCursosPlan().keySet().stream().noneMatch(c -> c.getId().equals(curso.getId()))) {
            throw new BusinessRuleException("CURSO_FUERA_DEL_PLAN", "El curso debe pertenecer al plan del año y grado seleccionados");
        }
        return grado;
    }

    private void validarTipos(List<ReglaCalificacionDto.Componente> componentes) {
        if (componentes.stream().map(ReglaCalificacionDto.Componente::tipoEvaluacion).distinct().count() != componentes.size()) {
            throw new BusinessRuleException("COMPONENTE_DUPLICADO", "Cada tipo de evaluación puede aparecer una sola vez");
        }
    }

    private void validarSuma(List<ReglaCalificacionDto.Componente> componentes) {
        var suma = componentes.stream().map(ReglaCalificacionDto.Componente::peso).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (suma.compareTo(CIEN) != 0) {
            throw new BusinessRuleException("PESOS_INVALIDOS", "Los pesos deben sumar 100 %. Total actual: " + suma.stripTrailingZeros().toPlainString() + " %.");
        }
    }

    private void verificarVersion(ReglaCalificacion regla, Long version) {
        if (version == null || regla.getVersion() != version) throw versionDesactualizada();
    }

    private ConflictException versionDesactualizada() {
        return new ConflictException("VERSION_DESACTUALIZADA", "La regla cambió. Actualiza los datos y vuelve a intentarlo");
    }

    private ReglaCalificacionDto.Regla respuesta(ReglaCalificacion regla) {
        return new ReglaCalificacionDto.Regla(regla.getId(), regla.getCurso().getId(), regla.getGrado().getId(),
                regla.getNumeroVersion(), regla.getEstado(), regla.getFormula(), componentes(regla), regla.getVersion(), regla.getFechaCreacion());
    }

    private List<ReglaCalificacionDto.Componente> componentes(ReglaCalificacion regla) {
        return regla.getComponentes().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(item -> new ReglaCalificacionDto.Componente(item.getKey(), item.getValue().getNombre(), item.getValue().getPeso()))
                .toList();
    }

    private String describir(ReglaCalificacion regla) {
        return "versión=" + regla.getNumeroVersion() + ", fórmula=" + regla.getFormula() + ", componentes=" + componentes(regla);
    }

    private void auditar(String accion, Long id, String motivo, String antes, String despues) {
        auditoria.registrar(accion, ModuloAuditoria.ACADEMICO, "ReglaCalificacion", id.toString(),
                ResultadoAuditoria.EXITOSO, motivo, antes, despues);
    }
}

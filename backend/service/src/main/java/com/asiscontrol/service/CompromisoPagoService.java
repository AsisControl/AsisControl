package com.asiscontrol.service;

import com.asiscontrol.dto.PagoDto;
import com.asiscontrol.entity.Comprobante;
import com.asiscontrol.entity.CompromisoPago;
import com.asiscontrol.entity.TransaccionPago;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.ConceptoCobro;
import com.asiscontrol.entity.TarifaPension;
import com.asiscontrol.entity.enums.EstadoAdministrativoCompromiso;
import com.asiscontrol.entity.enums.EstadoAnioAcademico;
import com.asiscontrol.entity.enums.EstadoCompromisoPago;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoTransaccionPago;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.ComprobanteRepository;
import com.asiscontrol.repository.CompromisoPagoRepository;
import com.asiscontrol.repository.ContadorCodigoCompromisoRepository;
import com.asiscontrol.repository.MatriculaRepository;
import com.asiscontrol.repository.TransaccionPagoRepository;
import com.asiscontrol.repository.ConceptoCobroRepository;
import com.asiscontrol.repository.OrdenPagoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.asiscontrol.repository.planificacion.AnioAcademicoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Locale;
import java.util.OptionalLong;

@Service
public class CompromisoPagoService {

    private final CompromisoPagoRepository compromisos;
    private final ContadorCodigoCompromisoRepository contadorCodigos;
    private final MatriculaRepository matriculas;
    private final AnioAcademicoRepository anios;
    private final TransaccionPagoRepository transacciones;
    private final AuditoriaService auditoria;
    private final BeneficioCalculoService beneficios;
    private final ConceptoCobroRepository conceptos;
    private final OrdenPagoRepository ordenes;
    private final EntityManager em;
    private final ComprobanteRepository comprobantes;

    /** Filtros de la consulta administrativa de compromisos (US-44). */
    public record FiltroCompromisos(
            EstadoCompromisoPago estado,
            EstadoAdministrativoCompromiso estadoAdministrativo,
            Long idMatricula,
            Long idAlumno,
            Integer anioAcademico,
            Integer grado,
            Long idSeccion,
            LocalDate vencimientoDesde,
            LocalDate vencimientoHasta,
            String busqueda) {
    }

    public CompromisoPagoService(
            CompromisoPagoRepository compromisos,
            ContadorCodigoCompromisoRepository contadorCodigos,
            MatriculaRepository matriculas,
            AnioAcademicoRepository anios,
            TransaccionPagoRepository transacciones,
            AuditoriaService auditoria, BeneficioCalculoService beneficios,
            ConceptoCobroRepository conceptos,OrdenPagoRepository ordenes,EntityManager em,
            ComprobanteRepository comprobantes) {
        this.comprobantes = comprobantes;
        this.compromisos = compromisos;
        this.contadorCodigos = contadorCodigos;
        this.matriculas = matriculas;
        this.anios = anios;
        this.transacciones = transacciones;
        this.auditoria = auditoria;
        this.beneficios=beneficios;this.conceptos=conceptos;this.ordenes=ordenes;this.em=em;
    }

    @Transactional(readOnly = true)
    public Page<PagoDto.CompromisoResponse> listar(FiltroCompromisos filtro, Long personaApoderadoId, Pageable pageable) {
        LocalDate hoy = EstadoFinancieroCompromiso.hoy();
        return buscar(filtro, personaApoderadoId, hoy, pageable).map(compromiso -> respuesta(compromiso, null, hoy));
    }

    /**
     * Cantidades y montos por estado de los compromisos dentro del filtro, junto con los
     * indicadores de morosidad. Todo se calcula con la misma regla que se muestra en el listado.
     */
    @Transactional(readOnly = true)
    public PagoDto.ResumenCompromisosResponse resumir(FiltroCompromisos filtro) {
        LocalDate hoy = EstadoFinancieroCompromiso.hoy();
        Map<EstadoCompromisoPago, Acumulado> porEstado = new EnumMap<>(EstadoCompromisoPago.class);
        for (EstadoCompromisoPago estado : EstadoCompromisoPago.values()) {
            porEstado.put(estado, new Acumulado());
        }
        Acumulado total = new Acumulado();
        Set<Long> morosos = new HashSet<>();
        for (CompromisoPago compromiso : buscar(filtro, null, hoy, Pageable.unpaged()).getContent()) {
            EstadoCompromisoPago estado = EstadoFinancieroCompromiso.visible(compromiso, hoy);
            BigDecimal pagado = dinero(compromiso.getMontoPagado());
            BigDecimal saldo = EstadoFinancieroCompromiso.saldo(compromiso);
            porEstado.get(estado).sumar(compromiso.getMontoTotal(), pagado, saldo);
            total.sumar(compromiso.getMontoTotal(), pagado, saldo);
            if (estado == EstadoCompromisoPago.VENCIDO) {
                morosos.add(compromiso.getMatricula().getAlumno().getId());
            }
        }
        List<PagoDto.ResumenEstadoResponse> estados = porEstado.entrySet().stream()
                .map(entrada -> entrada.getValue().respuesta(entrada.getKey()))
                .toList();
        Acumulado vencidos = porEstado.get(EstadoCompromisoPago.VENCIDO);
        return new PagoDto.ResumenCompromisosResponse(hoy, total.cantidad, total.montoTotal, total.montoPagado,
                total.saldo, estados,
                new PagoDto.IndicadoresMorosidadResponse(vencidos.saldo, vencidos.cantidad, morosos.size()));
    }

    /** Compromiso con la secuencia de sus transacciones y los comprobantes internos emitidos. */
    @Transactional(readOnly = true)
    public PagoDto.CompromisoDetalleResponse detallar(Long id) {
        CompromisoPago compromiso = compromisos.findByIdAndActivoTrue(id).orElseThrow(this::noEncontrado);
        Map<Long, Comprobante> comprobantesPorTransaccion = comprobantes.buscarPorCompromiso(id).stream()
                .collect(Collectors.toMap(c -> c.getTransaccionPago().getId(), Function.identity(), (a, b) -> a));
        List<PagoDto.MovimientoCompromisoResponse> movimientos = new ArrayList<>();
        BigDecimal acumulado = dinero(BigDecimal.ZERO);
        int secuencia = 0;
        for (TransaccionPago transaccion : transacciones
                .findAllByCompromisoPagoIdAndActivoTrueOrderByFechaTransaccionAscIdAsc(id)) {
            boolean aplica = transaccion.getEstado() == EstadoTransaccionPago.APROBADA;
            if (aplica) {
                acumulado = dinero(acumulado.add(transaccion.getMonto()));
            }
            Comprobante comprobante = comprobantesPorTransaccion.get(transaccion.getId());
            movimientos.add(new PagoDto.MovimientoCompromisoResponse(
                    ++secuencia,
                    PagoService.toTransaccionResponse(transaccion),
                    aplica,
                    acumulado,
                    EstadoFinancieroCompromiso.saldo(compromiso.getMontoTotal(), acumulado),
                    comprobante == null ? null : PagoService.toComprobanteResponse(comprobante)));
        }
        return new PagoDto.CompromisoDetalleResponse(respuesta(compromiso), List.copyOf(movimientos));
    }

    /** El estado financiero lo calcula el sistema; ningún usuario puede fijarlo manualmente. */
    public void rechazarCambioEstadoFinanciero() {
        throw new BusinessRuleException("ESTADO_FINANCIERO_CALCULADO",
                "El estado del compromiso se calcula con sus pagos aprobados y su vencimiento; no puede cambiarse manualmente.");
    }

    private Page<CompromisoPago> buscar(FiltroCompromisos filtro, Long personaApoderadoId, LocalDate hoy, Pageable pageable) {
        return compromisos.buscarAdministrados(
                filtro.estado() == null ? null : filtro.estado().name(), filtro.estadoAdministrativo(),
                filtro.idMatricula(), filtro.idAlumno(), filtro.anioAcademico(), filtro.grado(), filtro.idSeccion(),
                filtro.vencimientoDesde(), filtro.vencimientoHasta(),
                limpiar(filtro.busqueda()), personaApoderadoId, hoy, pageable);
    }

    private static BigDecimal dinero(BigDecimal monto) {
        return (monto == null ? BigDecimal.ZERO : monto).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static final class Acumulado {
        private long cantidad;
        private BigDecimal montoTotal = dinero(BigDecimal.ZERO);
        private BigDecimal montoPagado = dinero(BigDecimal.ZERO);
        private BigDecimal saldo = dinero(BigDecimal.ZERO);

        private void sumar(BigDecimal total, BigDecimal pagado, BigDecimal pendiente) {
            cantidad++;
            montoTotal = montoTotal.add(dinero(total));
            montoPagado = montoPagado.add(pagado);
            saldo = saldo.add(pendiente);
        }

        private PagoDto.ResumenEstadoResponse respuesta(EstadoCompromisoPago estado) {
            return new PagoDto.ResumenEstadoResponse(estado, cantidad, montoTotal, montoPagado, saldo);
        }
    }

    @Transactional(readOnly = true)
    public PagoDto.CompromisoResponse obtener(Long id) {
        return respuesta(compromisos.findByIdAndActivoTrue(id).orElseThrow(this::noEncontrado));
    }

    @Transactional(readOnly = true)
    public Page<PagoDto.MatriculaCompromisoResponse> listarMatriculas(String busqueda, Pageable pageable) {
        return matriculas.buscarParaCompromisos(limpiar(busqueda), pageable)
                .map(matricula -> new PagoDto.MatriculaCompromisoResponse(
                        matricula.getId(), matricula.getCodigoMatricula(),
                        matricula.getAlumno().getPersona().nombreCompleto(),
                        matricula.getAlumno().getCodigoAlumno(), matricula.getAlumno().getId(),
                        matricula.getAnioAcademico(), matricula.getSeccion().getGrado(),
                        matricula.getSeccion().getNombre(), matricula.getEstado().name()));
    }

    @Transactional
    public PagoDto.CompromisoResponse emitir(PagoDto.CompromisoRequest request) {
        return emitirInterno(request,false);
    }

    /** Solo se invoca después de aceptar una oferta perteneciente al apoderado autenticado. */
    @Transactional
    public PagoDto.CompromisoResponse emitirAceptado(PagoDto.CompromisoRequest request) {
        return emitirInterno(request,true);
    }

    private PagoDto.CompromisoResponse emitirInterno(PagoDto.CompromisoRequest request,boolean aceptado) {
        Matricula matricula = matriculas.bloquearPorId(request.idMatricula())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MATRICULA_NO_ENCONTRADA", "No se encontró la matrícula seleccionada."));
        validarMatricula(matricula);
        em.flush();em.refresh(matricula.getAlumno(),LockModeType.PESSIMISTIC_WRITE);
        ConceptoCobro catalogo=request.idConcepto()==null?null:conceptos.findByIdAndActivoTrue(request.idConcepto())
                .orElseThrow(()->new ResourceNotFoundException("CONCEPTO_NO_ENCONTRADO","Selecciona un concepto activo."));
        if(catalogo!=null&&catalogo.isEsPension())
            throw new BusinessRuleException("PENSION_GENERADA_POR_PLAN","Las pensiones se generan mediante el plan anual de la matrícula.");
        if(catalogo!=null&&catalogo.isRequiereAceptacion()&&!aceptado)
            throw new BusinessRuleException("ACEPTACION_REQUERIDA","Este concepto se ofrece primero al apoderado para su aceptación.");
        if(catalogo==null&&(limpiar(request.concepto())==null||request.numeroCuota()==null))
            throw new BusinessRuleException("CONCEPTO_CUOTA_REQUERIDOS","Los compromisos anteriores requieren concepto y cuota; para un cobro único selecciona un concepto del catálogo.");
        if(catalogo!=null&&request.numeroCuota()!=null)throw new BusinessRuleException("COBRO_UNICO_SIN_CUOTA","Los cobros únicos no llevan número de cuota.");
        String concepto = catalogo==null||aceptado?request.concepto().trim().replaceAll("\\s+", " "):catalogo.getNombre();
        String referencia=normalizarReferenciaCobro(request.referenciaCobro());
        if(catalogo!=null&&referencia==null)throw new BusinessRuleException("REFERENCIA_COBRO_REQUERIDA","Indica una referencia para identificar este cobro y evitar duplicados.");
        String clave = catalogo==null?claveObligacion(matricula, concepto, request.numeroCuota()):claveIdentidad("UNICO|"+matricula.getAlumno().getId()+"|"+matricula.getAnioAcademico()+"|"+catalogo.getId()+"|"+referencia);
        if ((catalogo==null&&compromisos.existeObligacionCoincidente(matricula.getAlumno().getId(),
                matricula.getAnioAcademico(), concepto.toLowerCase(Locale.ROOT), request.numeroCuota()))
                ||compromisos.findByClaveObligacion(clave).isPresent()) {
            throw new ConflictException("COMPROMISO_DUPLICADO",
                    "El estudiante ya tiene un compromiso activo para ese año, concepto y cuota.");
        }
        String codigo = resolverCodigo(request.codigoCompromiso());
        CompromisoPago compromiso = new CompromisoPago();
        compromiso.setMatricula(matricula);
        compromiso.setCodigoCompromiso(codigo);
        compromiso.setConcepto(concepto);
        compromiso.setNumeroCuota(request.numeroCuota());
        compromiso.setConceptoCobro(catalogo);
        compromiso.setReferenciaCobro(referencia);
        compromiso.setClaveObligacion(clave);
        compromiso.setDescripcion(limpiar(request.descripcion()));
        compromiso.setMontoTotal(request.montoTotal().setScale(2));
        compromiso.setMontoPagado(BigDecimal.ZERO.setScale(2));
        compromiso.setMoneda("PEN");
        compromiso.setFechaVencimiento(request.fechaVencimiento());
        var calculado=catalogo==null||request.fechaVencimiento().getYear()!=matricula.getAnioAcademico()
                ?new BeneficioCalculoService.CalculoBeneficio(request.montoTotal().setScale(2),new BigDecimal("0.00"),request.montoTotal().setScale(2),null,null,null)
                :beneficios.calcular(matricula.getAlumno().getId(),matricula.getAnioAcademico(),catalogo.getId(),request.fechaVencimiento().getMonthValue(),request.montoTotal());
        ConfiguracionPagosService.aplicarCalculo(compromiso,calculado);
        compromisos.saveAndFlush(compromiso);
        registrar("EMITIR_COMPROMISO", compromiso, "Emisión manual del compromiso de pago", null);
        return respuesta(compromiso);
    }

    @Transactional
    public CompromisoPago emitirPension(Matricula matricula,ConceptoCobro concepto,TarifaPension tarifa,int mes,
            BeneficioCalculoService.CalculoBeneficio calculado){
        String clave=clavePension(matricula.getAlumno().getId(),matricula.getAnioAcademico(),concepto.getId(),mes);
        var existente=compromisos.findByClaveObligacion(clave);if(existente.isPresent())return existente.get();
        var c=new CompromisoPago();c.setMatricula(matricula);c.setCodigoCompromiso(resolverCodigo(null));
        c.setConceptoCobro(concepto);c.setMes(mes);c.setNumeroCuota(mes-2);c.setClaveObligacion(clave);
        c.setConcepto(concepto.getNombre()+" de "+java.time.Month.of(mes).getDisplayName(java.time.format.TextStyle.FULL,Locale.forLanguageTag("es-PE"))+" de "+matricula.getAnioAcademico());
        c.setMoneda("PEN");c.setMontoPagado(new BigDecimal("0.00"));c.setFechaVencimiento(java.time.YearMonth.of(matricula.getAnioAcademico(),mes).atEndOfMonth());
        c.setIdTarifa(tarifa.getId());c.setTarifaVersion(tarifa.getVersion());ConfiguracionPagosService.aplicarCalculo(c,calculado);
        compromisos.saveAndFlush(c);registrar("EMITIR_PENSION",c,"Generación del plan anual",null);return c;
    }
    public static String clavePension(Long alumno,int anio,Long concepto,int mes){return claveIdentidad("PENSION|"+alumno+"|"+anio+"|"+concepto+"|"+mes);}
    public static String normalizarReferenciaCobro(String valor){if(valor==null||valor.isBlank())return null;
        String normal=java.text.Normalizer.normalize(valor,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+","-").replaceAll("^-|-$","");
        return normal.isBlank()?null:normal;
    }
    public static String claveIdentidad(String valor){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}

    /** El contador y el compromiso se confirman o revierten en la misma transacción. */
    private String resolverCodigo(String solicitado) {
        contadorCodigos.initializeCounter();
        var contador = contadorCodigos.lockCounter();
        // También cubre perfiles locales con ddl-auto=update y bases sin códigos numéricos previos.
        if (contador.getUltimoValor() == 0) {
            long maximoLegado = compromisos.codigosConPrefijoCorrelativo().stream()
                    .mapToLong(codigo -> numeroCorrelativo(codigo).orElse(0)).max().orElse(0);
            contador.setUltimoValor(maximoLegado);
        }
        String codigo = limpiar(solicitado);
        if (codigo != null) {
            // Compatibilidad con integraciones anteriores: también participan del mismo bloqueo.
            codigo = codigo.toUpperCase(Locale.ROOT);
            if (compromisos.findByCodigoCompromisoIgnoreCase(codigo).isPresent()) {
                throw new ConflictException("CODIGO_COMPROMISO_DUPLICADO", "Ese código de compromiso ya está registrado.");
            }
            contador.setUltimoValor(Math.max(contador.getUltimoValor(), numeroCorrelativo(codigo).orElse(0)));
        } else {
            do {
                if (contador.getUltimoValor() == Long.MAX_VALUE) {
                    throw new BusinessRuleException("CORRELATIVO_COMPROMISO_AGOTADO",
                            "No quedan referencias disponibles para emitir compromisos. Contacta a administración.");
                }
                contador.setUltimoValor(contador.getUltimoValor() + 1);
                codigo = String.format(Locale.ROOT, "CP-%06d", contador.getUltimoValor());
            } while (compromisos.findByCodigoCompromisoIgnoreCase(codigo).isPresent());
        }
        contadorCodigos.save(contador);
        return codigo;
    }

    private OptionalLong numeroCorrelativo(String codigo) {
        String normalizado = codigo.toUpperCase(Locale.ROOT);
        if (!normalizado.matches("CP-[0-9]+")) return OptionalLong.empty();
        try {
            return OptionalLong.of(Long.parseLong(normalizado.substring(3)));
        } catch (NumberFormatException exception) {
            // Los identificadores históricos fuera del rango se conservan como referencias opacas.
            return OptionalLong.empty();
        }
    }

    @Transactional
    public PagoDto.CompromisoResponse modificar(Long id, PagoDto.CompromisoActualizacionRequest request) {
        if (request.intentaCambiarEstadoFinanciero()) {
            rechazarCambioEstadoFinanciero();
        }
        CompromisoPago compromiso = bloquearEditable(id, request.version());
        if (!"PEN".equals(compromiso.getMoneda())) {
            throw new BusinessRuleException("MONEDA_HISTORICA_REQUIERE_REVISION",
                    "Este compromiso histórico está en otra moneda y requiere revisión antes de modificarlo.");
        }
        String anterior = describir(compromiso);
        if(compromiso.getMes()!=null&&compromiso.getConceptoCobro()!=null&&compromiso.getConceptoCobro().isEsPension())
            throw new BusinessRuleException("PENSION_GESTIONADA_POR_PLAN","La pensión conserva el cálculo del plan anual. Utiliza becas y sus ajustes para modificar el importe pendiente.");
        if(compromiso.getIdBeneficio()!=null)throw new BusinessRuleException("AJUSTE_CON_BENEFICIO","Este compromiso tiene un beneficio. Utiliza el ajuste de becas para conservar su cálculo.");
        compromiso.setMontoBase(request.montoTotal().setScale(2));compromiso.setMontoDescuento(new BigDecimal("0.00"));
        compromiso.setMontoTotal(request.montoTotal().setScale(2));
        compromiso.setFechaVencimiento(request.fechaVencimiento());
        compromiso.setEstado(estadoFinanciero(compromiso));
        compromisos.saveAndFlush(compromiso);
        registrar("MODIFICAR_COMPROMISO", compromiso, request.motivo().trim(), anterior);
        return respuesta(compromiso);
    }

    @Transactional
    public PagoDto.CompromisoResponse anular(Long id, PagoDto.CompromisoAnulacionRequest request) {
        CompromisoPago compromiso = bloquearEditable(id, request.version());
        String anterior = describir(compromiso);
        compromiso.setEstadoAdministrativo(EstadoAdministrativoCompromiso.ANULADO);
        compromiso.setEstado(EstadoCompromisoPago.ANULADO);
        compromiso.setClaveObligacion(null);
        compromiso.setMotivoAnulacion(request.motivo().trim());
        compromiso.setFechaAnulacion(Instant.now());
        compromisos.saveAndFlush(compromiso);
        registrar("ANULAR_COMPROMISO", compromiso, request.motivo().trim(), anterior);
        return respuesta(compromiso);
    }

    public void rechazarEliminacion() {
        throw new BusinessRuleException("ELIMINACION_COMPROMISO_NO_PERMITIDA",
                "Los compromisos conservan su historial. Para dejar uno sin efecto, anúlalo indicando el motivo.");
    }

    private CompromisoPago bloquearEditable(Long id, Long version) {
        CompromisoPago compromiso = compromisos.bloquearPorId(id).orElseThrow(this::noEncontrado);
        if (compromiso.getVersion() != version) {
            throw new ConflictException("COMPROMISO_MODIFICADO",
                    "El compromiso cambió. Actualiza sus datos antes de continuar.");
        }
        if (compromiso.getEstadoAdministrativo() == EstadoAdministrativoCompromiso.ANULADO) {
            throw new BusinessRuleException("COMPROMISO_ANULADO", "Un compromiso anulado no admite cambios.");
        }
        if (transacciones.existsByCompromisoPagoIdAndEstado(id, EstadoTransaccionPago.APROBADA)
                || compromiso.getMontoPagado().signum() > 0) {
            throw new BusinessRuleException("COMPROMISO_CON_PAGOS",
                    "El compromiso tiene pagos aprobados y no puede modificarse ni anularse.");
        }
        if(ordenes.existePagoVigente(java.util.List.of(id),Instant.now()))throw new ConflictException("PAGO_EN_CURSO","Hay un pago en curso para este compromiso. Espera su resultado o vencimiento antes de cambiarlo.");
        return compromiso;
    }

    private void validarMatricula(Matricula matricula) {
        if (matricula.getEstado() != EstadoMatricula.ACTIVA
                || !matricula.getAlumno().isActivo() || !matricula.getAlumno().getPersona().isActivo()) {
            throw new BusinessRuleException("MATRICULA_NO_VIGENTE",
                    "Selecciona una matrícula vigente de un estudiante activo.", "idMatricula");
        }
        var anio = anios.findByAnio(matricula.getAnioAcademico())
                .orElseThrow(() -> new BusinessRuleException("ANIO_NO_CONFIGURADO",
                        "El año académico de la matrícula aún no está configurado.", "idMatricula"));
        if (!anio.isActivo() || anio.getEstado() != EstadoAnioAcademico.ACTIVO
                || anio.getFechaInicio() == null || anio.getFechaFin() == null
                || anio.getFechaInicio().isAfter(anio.getFechaFin())) {
            throw new BusinessRuleException("ANIO_NO_VIGENTE",
                    "El año académico de la matrícula debe estar activo y tener sus fechas configuradas.", "idMatricula");
        }
    }

    private String claveObligacion(Matricula matricula, String concepto, Integer cuota) {
        String valor = matricula.getAlumno().getId() + "|" + matricula.getAnioAcademico()
                + "|" + concepto.toLowerCase(Locale.ROOT) + "|" + cuota;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }

    private EstadoCompromisoPago estadoFinanciero(CompromisoPago compromiso) {
        return EstadoFinancieroCompromiso.financiero(compromiso, EstadoFinancieroCompromiso.hoy());
    }

    private PagoDto.CompromisoResponse respuesta(CompromisoPago compromiso) {
        return respuesta(compromiso, null, EstadoFinancieroCompromiso.hoy());
    }

    public PagoDto.CompromisoResponse previsualizar(CompromisoPago compromiso,BeneficioCalculoService.CalculoBeneficio calculo){
        return respuesta(compromiso, calculo, EstadoFinancieroCompromiso.hoy());
    }

    private PagoDto.CompromisoResponse respuesta(CompromisoPago compromiso,BeneficioCalculoService.CalculoBeneficio calculo,LocalDate hoy) {
        Matricula matricula = compromiso.getMatricula();
        boolean anulado = EstadoFinancieroCompromiso.anulado(compromiso);
        var monto=calculo==null?compromiso.getMontoTotal():calculo.montoTotal();
        // La previsualización de un beneficio solo aplica a compromisos sin pagos, pero se calcula con la misma regla.
        EstadoCompromisoPago financiero = EstadoFinancieroCompromiso.calcular(
                monto, compromiso.getMontoPagado(), compromiso.getFechaVencimiento(), hoy);
        var seccion = matricula.getSeccion();
        return new PagoDto.CompromisoResponse(
                compromiso.getId(), matricula.getId(), compromiso.getCodigoCompromiso(),
                compromiso.getConcepto(), compromiso.getDescripcion(), monto,
                compromiso.getMontoPagado(), anulado ? BigDecimal.ZERO.setScale(2)
                        : EstadoFinancieroCompromiso.saldo(monto, compromiso.getMontoPagado()),
                compromiso.getMoneda(), compromiso.getFechaVencimiento(),
                anulado ? EstadoCompromisoPago.ANULADO : financiero,
                compromiso.getFechaCreacion(), compromiso.getFechaActualizacion(),
                compromiso.getNumeroCuota(), compromiso.getEstadoAdministrativo(), financiero,
                matricula.getAlumno().getId(), matricula.getAlumno().getPersona().nombreCompleto(),
                matricula.getAlumno().getCodigoAlumno(), matricula.getAnioAcademico(), matricula.getCodigoMatricula(),
                compromiso.getMotivoAnulacion(), compromiso.getFechaAnulacion(), compromiso.getVersion(),
                compromiso.getConceptoCobro()==null?null:compromiso.getConceptoCobro().getId(),compromiso.getMes(),compromiso.getReferenciaCobro(),
                calculo==null?(compromiso.getMontoBase()==null?compromiso.getMontoTotal():compromiso.getMontoBase()):calculo.montoBase(),
                calculo==null?(compromiso.getMontoDescuento()==null?new BigDecimal("0.00"):compromiso.getMontoDescuento()):calculo.montoDescuento(),
                calculo==null?compromiso.getBeneficioNombre():calculo.beneficioNombre(),calculo==null?compromiso.getIdBeneficio():calculo.idBeneficio(),compromiso.getIdTarifa(),compromiso.getTarifaVersion(),
                seccion == null ? null : seccion.getGrado(), seccion == null ? null : seccion.getNombre(),
                seccion == null ? null : seccion.getId());
    }

    private void registrar(String accion, CompromisoPago compromiso, String motivo, String anterior) {
        auditoria.registrar(accion, ModuloAuditoria.FINANZAS, "CompromisoPago",
                compromiso.getId().toString(), ResultadoAuditoria.EXITOSO,
                motivo, anterior, describir(compromiso));
    }

    private String describir(CompromisoPago compromiso) {
        return "codigo=" + compromiso.getCodigoCompromiso()
                + ",matriculaId=" + compromiso.getMatricula().getId()
                + ",alumnoId=" + compromiso.getMatricula().getAlumno().getId()
                + ",anio=" + compromiso.getMatricula().getAnioAcademico()
                + ",concepto=" + compromiso.getConcepto() + ",cuota=" + compromiso.getNumeroCuota()
                + ",montoTotal=" + compromiso.getMontoTotal() + ",moneda=" + compromiso.getMoneda()
                + ",vencimiento=" + compromiso.getFechaVencimiento()
                + ",estadoAdministrativo=" + compromiso.getEstadoAdministrativo();
    }

    private ResourceNotFoundException noEncontrado() {
        return new ResourceNotFoundException("COMPROMISO_PAGO_NO_ENCONTRADO", "No se encontró el compromiso de pago.");
    }

    private String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}

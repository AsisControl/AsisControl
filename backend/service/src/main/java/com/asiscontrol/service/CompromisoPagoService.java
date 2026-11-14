package com.asiscontrol.service;

import com.asiscontrol.dto.PagoDto;
import com.asiscontrol.entity.CompromisoPago;
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
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Locale;
import java.util.OptionalLong;

@Service
public class CompromisoPagoService {

    private static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Lima");
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

    public CompromisoPagoService(
            CompromisoPagoRepository compromisos,
            ContadorCodigoCompromisoRepository contadorCodigos,
            MatriculaRepository matriculas,
            AnioAcademicoRepository anios,
            TransaccionPagoRepository transacciones,
            AuditoriaService auditoria, BeneficioCalculoService beneficios,
            ConceptoCobroRepository conceptos,OrdenPagoRepository ordenes,EntityManager em) {
        this.compromisos = compromisos;
        this.contadorCodigos = contadorCodigos;
        this.matriculas = matriculas;
        this.anios = anios;
        this.transacciones = transacciones;
        this.auditoria = auditoria;
        this.beneficios=beneficios;this.conceptos=conceptos;this.ordenes=ordenes;this.em=em;
    }

    @Transactional(readOnly = true)
    public Page<PagoDto.CompromisoResponse> listar(
            EstadoCompromisoPago estado,
            EstadoAdministrativoCompromiso estadoAdministrativo,
            Long idMatricula,
            Long idAlumno,
            Integer anioAcademico,
            String busqueda,
            Long personaApoderadoId,
            Pageable pageable) {
        return compromisos.buscarAdministrados(
                estado == null ? null : estado.name(), estadoAdministrativo, idMatricula, idAlumno, anioAcademico,
                limpiar(busqueda), personaApoderadoId, LocalDate.now(ZONA_NEGOCIO), pageable)
                .map(this::respuesta);
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
        if(compromiso.getMontoTotal().signum()==0)return EstadoCompromisoPago.EXONERADO;
        if (compromiso.getMontoPagado().compareTo(compromiso.getMontoTotal()) >= 0) {
            return EstadoCompromisoPago.PAGADO;
        }
        if (compromiso.getFechaVencimiento().isBefore(LocalDate.now(ZONA_NEGOCIO))) {
            return EstadoCompromisoPago.VENCIDO;
        }
        return compromiso.getMontoPagado().signum() > 0
                ? EstadoCompromisoPago.PARCIAL : EstadoCompromisoPago.PENDIENTE;
    }

    private PagoDto.CompromisoResponse respuesta(CompromisoPago compromiso) {
        return respuesta(compromiso,null);
    }

    public PagoDto.CompromisoResponse previsualizar(CompromisoPago compromiso,BeneficioCalculoService.CalculoBeneficio calculo){return respuesta(compromiso,calculo);}

    private PagoDto.CompromisoResponse respuesta(CompromisoPago compromiso,BeneficioCalculoService.CalculoBeneficio calculo) {
        Matricula matricula = compromiso.getMatricula();
        boolean anulado = compromiso.getEstadoAdministrativo() == EstadoAdministrativoCompromiso.ANULADO;
        var monto=calculo==null?compromiso.getMontoTotal():calculo.montoTotal();
        EstadoCompromisoPago financiero = calculo==null?estadoFinanciero(compromiso):monto.signum()==0?EstadoCompromisoPago.EXONERADO:
            compromiso.getFechaVencimiento().isBefore(LocalDate.now(ZONA_NEGOCIO))?EstadoCompromisoPago.VENCIDO:EstadoCompromisoPago.PENDIENTE;
        return new PagoDto.CompromisoResponse(
                compromiso.getId(), matricula.getId(), compromiso.getCodigoCompromiso(),
                compromiso.getConcepto(), compromiso.getDescripcion(), monto,
                compromiso.getMontoPagado(), anulado ? BigDecimal.ZERO.setScale(2)
                        : monto.subtract(compromiso.getMontoPagado()).max(BigDecimal.ZERO),
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
                calculo==null?compromiso.getBeneficioNombre():calculo.beneficioNombre(),calculo==null?compromiso.getIdBeneficio():calculo.idBeneficio(),compromiso.getIdTarifa(),compromiso.getTarifaVersion());
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

package com.asiscontrol.service;

import com.asiscontrol.dto.PagoDto;
import com.asiscontrol.entity.CompromisoPago;
import com.asiscontrol.entity.Matricula;
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

    public CompromisoPagoService(
            CompromisoPagoRepository compromisos,
            ContadorCodigoCompromisoRepository contadorCodigos,
            MatriculaRepository matriculas,
            AnioAcademicoRepository anios,
            TransaccionPagoRepository transacciones,
            AuditoriaService auditoria) {
        this.compromisos = compromisos;
        this.contadorCodigos = contadorCodigos;
        this.matriculas = matriculas;
        this.anios = anios;
        this.transacciones = transacciones;
        this.auditoria = auditoria;
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
        Matricula matricula = matriculas.bloquearPorId(request.idMatricula())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MATRICULA_NO_ENCONTRADA", "No se encontró la matrícula seleccionada."));
        validarMatricula(matricula);
        String concepto = request.concepto().trim().replaceAll("\\s+", " ");
        String clave = claveObligacion(matricula, concepto, request.numeroCuota());
        if (compromisos.existeObligacionCoincidente(matricula.getAlumno().getId(),
                matricula.getAnioAcademico(), concepto.toLowerCase(Locale.ROOT), request.numeroCuota())) {
            throw new ConflictException("COMPROMISO_DUPLICADO",
                    "El estudiante ya tiene un compromiso activo para ese año, concepto y cuota.");
        }
        String codigo = resolverCodigo(request.codigoCompromiso());
        CompromisoPago compromiso = new CompromisoPago();
        compromiso.setMatricula(matricula);
        compromiso.setCodigoCompromiso(codigo);
        compromiso.setConcepto(concepto);
        compromiso.setNumeroCuota(request.numeroCuota());
        compromiso.setClaveObligacion(clave);
        compromiso.setDescripcion(limpiar(request.descripcion()));
        compromiso.setMontoTotal(request.montoTotal().setScale(2));
        compromiso.setMontoPagado(BigDecimal.ZERO.setScale(2));
        compromiso.setMoneda("PEN");
        compromiso.setFechaVencimiento(request.fechaVencimiento());
        compromiso.setEstado(estadoFinanciero(compromiso));
        compromisos.saveAndFlush(compromiso);
        registrar("EMITIR_COMPROMISO", compromiso, "Emisión manual del compromiso de pago", null);
        return respuesta(compromiso);
    }

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
        Matricula matricula = compromiso.getMatricula();
        boolean anulado = compromiso.getEstadoAdministrativo() == EstadoAdministrativoCompromiso.ANULADO;
        EstadoCompromisoPago financiero = estadoFinanciero(compromiso);
        return new PagoDto.CompromisoResponse(
                compromiso.getId(), matricula.getId(), compromiso.getCodigoCompromiso(),
                compromiso.getConcepto(), compromiso.getDescripcion(), compromiso.getMontoTotal(),
                compromiso.getMontoPagado(), anulado ? BigDecimal.ZERO.setScale(2)
                        : compromiso.getMontoTotal().subtract(compromiso.getMontoPagado()).max(BigDecimal.ZERO),
                compromiso.getMoneda(), compromiso.getFechaVencimiento(),
                anulado ? EstadoCompromisoPago.ANULADO : financiero,
                compromiso.getFechaCreacion(), compromiso.getFechaActualizacion(),
                compromiso.getNumeroCuota(), compromiso.getEstadoAdministrativo(), financiero,
                matricula.getAlumno().getId(), matricula.getAlumno().getPersona().nombreCompleto(),
                matricula.getAlumno().getCodigoAlumno(), matricula.getAnioAcademico(), matricula.getCodigoMatricula(),
                compromiso.getMotivoAnulacion(), compromiso.getFechaAnulacion(), compromiso.getVersion());
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

package com.asiscontrol.service;

import com.asiscontrol.dto.OfertaCobroDto;
import com.asiscontrol.dto.PagoDto;
import com.asiscontrol.entity.*;
import com.asiscontrol.entity.enums.*;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.ConceptoCobroRepository;
import com.asiscontrol.repository.MatriculaRepository;
import com.asiscontrol.repository.OfertaCobroRepository;
import com.asiscontrol.repository.planificacion.AnioAcademicoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Publicar una oferta no emite una deuda. Solo una aceptación autenticada la convierte en compromiso. */
@Service
@Transactional(readOnly = true)
public class OfertasCobroService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final OfertaCobroRepository ofertas;
    private final ConceptoCobroRepository conceptos;
    private final MatriculaRepository matriculas;
    private final AnioAcademicoRepository anios;
    private final ConsultaObligacionesService acceso;
    private final ConfiguracionPagosService configuracion;
    private final CompromisoPagoService compromisos;
    private final AuditoriaService auditoria;
    private final EntityManager em;
    private final SecretKey secret;

    public OfertasCobroService(OfertaCobroRepository ofertas, ConceptoCobroRepository conceptos,
            MatriculaRepository matriculas, AnioAcademicoRepository anios, ConsultaObligacionesService acceso,
            ConfiguracionPagosService configuracion, CompromisoPagoService compromisos,
            AuditoriaService auditoria, EntityManager em, SecretKey jwtSecretKey) {
        this.ofertas = ofertas; this.conceptos = conceptos; this.matriculas = matriculas;
        this.anios = anios; this.acceso = acceso; this.configuracion = configuracion;
        this.compromisos = compromisos; this.auditoria = auditoria; this.em = em; this.secret = jwtSecretKey;
    }

    public Page<OfertaCobroDto.OfertaResponse> listar(Pageable pageable) {
        return ofertas.findByActivoTrue(pageable).map(this::respuesta);
    }

    public Page<OfertaCobroDto.OfertaResponse> listarFamilia(Long alumno, Pageable pageable) {
        var apoderado = acceso.apoderadoActual();
        if (alumno != null) acceso.verificarAlumno(alumno, apoderado);
        return ofertas.buscarFamilia(apoderado.getId(), alumno, pageable).map(this::respuesta);
    }

    public OfertaCobroDto.OfertaResponse obtenerFamilia(Long id) {
        var apoderado = acceso.apoderadoActual();
        var oferta = ofertas.findByIdAndActivoTrue(id).orElseThrow(this::sinAcceso);
        acceso.verificarAlumno(oferta.getMatricula().getAlumno().getId(), apoderado);
        return respuesta(oferta);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<OfertaCobroDto.OfertaResponse> crear(OfertaCobroDto.CrearRequest request) {
        var concepto = conceptos.findByIdAndActivoTrue(request.idConcepto())
                .orElseThrow(() -> new ResourceNotFoundException("CONCEPTO_NO_ENCONTRADO", "Selecciona un concepto disponible."));
        validarConcepto(concepto);
        var ids = request.idMatriculas().stream().distinct().sorted().toList();
        if (ids.isEmpty() || ids.size() > 100 || ids.size() != request.idMatriculas().size()) {
            throw new BusinessRuleException("DESTINATARIOS_INVALIDOS", "Selecciona entre uno y cien estudiantes distintos.");
        }
        String referencia = normalizarReferencia(request.referencia());
        String titulo = limpiar(request.titulo());
        if (titulo == null) titulo = concepto.getNombre();
        LocalDate limite = request.fechaLimiteAceptacion() == null ? request.fechaVencimiento() : request.fechaLimiteAceptacion();
        LocalDate hoy = LocalDate.now(LIMA);
        if (request.fechaVencimiento().isBefore(hoy) || limite.isBefore(hoy) || limite.isAfter(request.fechaVencimiento())) {
            throw new BusinessRuleException("FECHAS_OFERTA_INVALIDAS", "Elige fechas vigentes; la aceptación debe terminar antes o el día del vencimiento.");
        }
        List<Matricula> destinatarios = new ArrayList<>();
        for (Long id : ids) {
            var matricula = matriculas.bloquearPorId(id).orElseThrow(() ->
                    new ResourceNotFoundException("MATRICULA_NO_ENCONTRADA", "Una matrícula seleccionada ya no está disponible."));
            validarMatricula(matricula);
            destinatarios.add(matricula);
        }
        if (destinatarios.stream().map(m -> m.getAlumno().getId()).distinct().count() != destinatarios.size()) {
            throw new BusinessRuleException("ESTUDIANTE_REPETIDO", "Selecciona una sola matrícula por estudiante.");
        }
        List<OfertaCobro> creadas = new ArrayList<>();
        for (var matricula : destinatarios) {
            String clave = clave(matricula, concepto, referencia);
            var existente = ofertas.findByClaveOferta(clave);
            if (existente.isPresent()) {
                var previa = existente.get();
                if (!previa.isActivo() || !Objects.equals(previa.getMatricula().getId(), matricula.getId())
                        || !Objects.equals(previa.getTitulo(), titulo)
                        || !Objects.equals(previa.getDescripcion(), limpiar(request.descripcion()))
                        || previa.getMontoBase().compareTo(request.monto()) != 0
                        || !previa.getFechaVencimiento().equals(request.fechaVencimiento())
                        || !previa.getFechaLimiteAceptacion().equals(limite)) {
                    throw new ConflictException("OFERTA_DUPLICADA", "Ya existe un cobro con esa referencia para uno de los estudiantes. Revisa el listado.");
                }
                creadas.add(previa);
                continue;
            }
            var oferta = new OfertaCobro();
            oferta.setMatricula(matricula); oferta.setConcepto(concepto); oferta.setConceptoNombre(concepto.getNombre());
            oferta.setReferencia(referencia); oferta.setClaveOferta(clave); oferta.setTitulo(titulo);
            oferta.setDescripcion(limpiar(request.descripcion())); oferta.setMontoBase(request.monto().setScale(2));
            oferta.setFechaVencimiento(request.fechaVencimiento()); oferta.setFechaLimiteAceptacion(limite);
            ofertas.saveAndFlush(oferta); creadas.add(oferta);
            auditar("PUBLICAR_COBRO_OPCIONAL", oferta, null, "Publicación de propuesta opcional");
        }
        return creadas.stream().map(this::respuesta).toList();
    }

    @Transactional
    public OfertaCobroDto.OfertaResponse aceptar(Long id, OfertaCobroDto.AceptarRequest request) {
        var apoderado = acceso.apoderadoActual();
        var oferta = bloquear(id, true);
        acceso.verificarAlumno(oferta.getMatricula().getAlumno().getId(), apoderado);
        if (oferta.getEstado() == EstadoOfertaCobro.ACEPTADA) return respuesta(oferta);
        exigirPropuesta(oferta);
        exigirVersion(oferta, request.version());
        validarMatricula(oferta.getMatricula()); validarConcepto(oferta.getConcepto());
        var cotizacion = cotizar(oferta);
        if (!MessageDigest.isEqual(firma(oferta, cotizacion).getBytes(StandardCharsets.US_ASCII),
                request.firmaCotizacion().getBytes(StandardCharsets.US_ASCII))) throw ofertaActualizada();
        var emitido = compromisos.emitirAceptado(new PagoDto.CompromisoRequest(
                oferta.getMatricula().getId(), null, oferta.getTitulo(), oferta.getDescripcion(),
                oferta.getMontoBase(), "PEN", oferta.getFechaVencimiento(), null,
                oferta.getConcepto().getId(), oferta.getReferencia()));
        // Un cambio concurrente de beneficio nunca debe alterar el importe consentido.
        if (emitido.montoTotal().compareTo(cotizacion.total()) != 0) throw ofertaActualizada();
        oferta.setCompromiso(em.getReference(CompromisoPago.class, emitido.id()));
        oferta.setDescuentoAceptado(cotizacion.descuento()); oferta.setMontoAceptado(cotizacion.total());
        oferta.setBeneficioAceptado(cotizacion.beneficioNombre()); oferta.setApoderadoRespuesta(apoderado);
        oferta.setEstado(EstadoOfertaCobro.ACEPTADA); oferta.setRespondidaEn(Instant.now());
        em.flush(); auditar("ACEPTAR_COBRO_OPCIONAL", oferta, EstadoOfertaCobro.PROPUESTA, "Aceptación del apoderado");
        return respuesta(oferta);
    }

    @Transactional
    public OfertaCobroDto.OfertaResponse rechazar(Long id, OfertaCobroDto.CambioEstadoRequest request) {
        var apoderado = acceso.apoderadoActual();
        var oferta = bloquear(id, true);
        acceso.verificarAlumno(oferta.getMatricula().getAlumno().getId(), apoderado);
        if (oferta.getEstado() == EstadoOfertaCobro.RECHAZADA) return respuesta(oferta);
        exigirPropuesta(oferta); exigirVersion(oferta, request == null ? null : request.version());
        oferta.setEstado(EstadoOfertaCobro.RECHAZADA); oferta.setApoderadoRespuesta(apoderado);
        oferta.setRespondidaEn(Instant.now()); oferta.setMotivoRespuesta(request == null ? null : limpiar(request.motivo()));
        em.flush(); auditar("RECHAZAR_COBRO_OPCIONAL", oferta, EstadoOfertaCobro.PROPUESTA, oferta.getMotivoRespuesta());
        return respuesta(oferta);
    }

    @Transactional
    public OfertaCobroDto.OfertaResponse cancelar(Long id, OfertaCobroDto.CambioEstadoRequest request) {
        var oferta = bloquear(id, false);
        if (oferta.getEstado() == EstadoOfertaCobro.CANCELADA) return respuesta(oferta);
        if (oferta.getEstado() != EstadoOfertaCobro.PROPUESTA) {
            throw new ConflictException("OFERTA_RESPONDIDA", "La propuesta ya fue respondida. Revisa el compromiso si corresponde un ajuste.");
        }
        exigirVersion(oferta, request == null ? null : request.version());
        oferta.setEstado(EstadoOfertaCobro.CANCELADA); oferta.setRespondidaEn(Instant.now());
        oferta.setMotivoRespuesta(request == null ? null : limpiar(request.motivo()));
        em.flush(); auditar("CANCELAR_COBRO_OPCIONAL", oferta, EstadoOfertaCobro.PROPUESTA, oferta.getMotivoRespuesta());
        return respuesta(oferta);
    }

    private OfertaCobro bloquear(Long id, boolean familiar) {
        var oferta = ofertas.findByIdAndActivoTrue(id).orElseThrow(() -> familiar ? sinAcceso() :
                new ResourceNotFoundException("OFERTA_NO_ENCONTRADA", "No se encontró la propuesta."));
        em.refresh(oferta, LockModeType.PESSIMISTIC_WRITE);
        if (!oferta.isActivo()) throw sinAcceso();
        return oferta;
    }

    private void exigirPropuesta(OfertaCobro oferta) {
        if (oferta.getEstado() != EstadoOfertaCobro.PROPUESTA) {
            throw new ConflictException("OFERTA_RESPONDIDA", "Esta propuesta ya tiene una respuesta. Actualiza el listado.");
        }
        if (oferta.getFechaLimiteAceptacion().isBefore(LocalDate.now(LIMA))) {
            throw new BusinessRuleException("OFERTA_VENCIDA", "El plazo para aceptar esta propuesta terminó.");
        }
    }

    private void exigirVersion(OfertaCobro oferta, Long version) {
        if (version != null && version != oferta.getVersion()) throw ofertaActualizada();
    }

    private void validarConcepto(ConceptoCobro concepto) {
        if (!concepto.isActivo() || concepto.isEsPension() || concepto.getPeriodicidad() != PeriodicidadCobro.UNICO
                || !concepto.isRequiereAceptacion()) {
            throw new BusinessRuleException("CONCEPTO_NO_OPCIONAL", "Selecciona un concepto de pago único que requiera aceptación.");
        }
    }

    private void validarMatricula(Matricula matricula) {
        if (!matricula.isActivo() || matricula.getEstado() != EstadoMatricula.ACTIVA
                || !matricula.getAlumno().isActivo() || !matricula.getAlumno().getPersona().isActivo()) {
            throw new BusinessRuleException("MATRICULA_NO_VIGENTE", "La matrícula del estudiante ya no está vigente.");
        }
        if (anios.findByAnio(matricula.getAnioAcademico()).filter(a -> a.isActivo() && a.getEstado() == EstadoAnioAcademico.ACTIVO).isEmpty()) {
            throw new BusinessRuleException("ANIO_NO_ACTIVO", "El año académico debe estar activo para publicar o aceptar un cobro.");
        }
    }

    private Cotizacion cotizar(OfertaCobro oferta) {
        if (oferta.getEstado() == EstadoOfertaCobro.ACEPTADA) {
            return new Cotizacion(oferta.getMontoBase(), oferta.getDescuentoAceptado(), oferta.getMontoAceptado(),
                    oferta.getBeneficioAceptado(), null, null);
        }
        if (oferta.getFechaVencimiento().getYear() != oferta.getMatricula().getAnioAcademico()) {
            return new Cotizacion(oferta.getMontoBase(), new BigDecimal("0.00"), oferta.getMontoBase(), null, null, null);
        }
        var c = configuracion.calcularBeneficio(oferta.getMatricula().getAlumno().getId(),
                oferta.getMatricula().getAnioAcademico(), oferta.getConcepto().getId(),
                oferta.getFechaVencimiento().getMonthValue(), oferta.getMontoBase());
        return new Cotizacion(c.montoBase(), c.montoDescuento(), c.montoTotal(), c.beneficioNombre(), c.idBeneficio(), c.beneficioVersion());
    }

    private OfertaCobroDto.OfertaResponse respuesta(OfertaCobro oferta) {
        var c = cotizar(oferta);
        var estado = oferta.getEstado() == EstadoOfertaCobro.PROPUESTA
                && oferta.getFechaLimiteAceptacion().isBefore(LocalDate.now(LIMA)) ? EstadoOfertaCobro.VENCIDA : oferta.getEstado();
        return new OfertaCobroDto.OfertaResponse(oferta.getId(), String.format(Locale.ROOT, "OC-%06d", oferta.getId()),
                oferta.getMatricula().getId(), oferta.getMatricula().getAlumno().getId(),
                oferta.getMatricula().getAlumno().getPersona().nombreCompleto(), oferta.getMatricula().getAnioAcademico(),
                oferta.getConcepto().getId(), oferta.getConceptoNombre(), oferta.getReferencia(), oferta.getTitulo(), oferta.getDescripcion(),
                c.base(), c.descuento(), c.total(), c.beneficioNombre(), oferta.getFechaVencimiento(), oferta.getFechaLimiteAceptacion(),
                estado, oferta.getCompromiso() == null ? null : oferta.getCompromiso().getId(),
                oferta.getCompromiso() == null ? null : oferta.getCompromiso().getCodigoCompromiso(),
                oferta.getFechaCreacion(), oferta.getRespondidaEn(), oferta.getVersion(),
                estado == EstadoOfertaCobro.PROPUESTA ? firma(oferta, c) : null);
    }

    private String firma(OfertaCobro oferta, Cotizacion c) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256"); hmac.init(secret);
            String dato = "asiscontrol:oferta-cotizacion:v1:" + oferta.getId() + ":" + oferta.getVersion()
                    + ":" + c.base() + ":" + c.descuento() + ":" + c.total() + ":" + c.idBeneficio() + ":" + c.versionBeneficio();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac.doFinal(dato.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) { throw new IllegalStateException("No se pudo preparar la confirmación del cobro.", error); }
    }

    private String clave(Matricula matricula, ConceptoCobro concepto, String referencia) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((matricula.getAlumno().getId()
                    + "|" + matricula.getAnioAcademico() + "|" + concepto.getId() + "|" + referencia).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) { throw new IllegalStateException(error); }
    }

    private String normalizarReferencia(String referencia) {
        String normalizada = CompromisoPagoService.normalizarReferenciaCobro(referencia);
        if (normalizada == null || normalizada.isBlank()) throw new BusinessRuleException("REFERENCIA_INVALIDA", "Escribe una referencia que permita identificar este cobro.");
        return normalizada;
    }

    private String limpiar(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ConflictException ofertaActualizada() {
        return new ConflictException("OFERTA_ACTUALIZADA", "La propuesta o su beneficio cambió. Actualiza el detalle y revisa el importe antes de aceptar.");
    }
    private AccessDeniedException sinAcceso() { return new AccessDeniedException("No tienes acceso al cobro solicitado."); }
    private void auditar(String accion, OfertaCobro oferta, EstadoOfertaCobro anterior, String motivo) {
        auditoria.registrar(accion, ModuloAuditoria.FINANZAS, "OfertaCobro", oferta.getId().toString(),
                ResultadoAuditoria.EXITOSO, motivo, anterior == null ? null : anterior.name(),
                "estado=" + oferta.getEstado() + ",referencia=" + oferta.getReferencia());
    }
    private record Cotizacion(BigDecimal base, BigDecimal descuento, BigDecimal total, String beneficioNombre,
                              Long idBeneficio, Long versionBeneficio) {}
}

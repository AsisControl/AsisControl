package com.asiscontrol.service;

import com.asiscontrol.dto.ConfiguracionPagosDto.PlanResponse;
import com.asiscontrol.dto.matricula.MatriculaDtos.CrearMatriculaRequest;
import com.asiscontrol.dto.matricula.RenovacionDtos.AprobarRequest;
import com.asiscontrol.dto.matricula.RenovacionDtos.CrearRequest;
import com.asiscontrol.dto.matricula.RenovacionDtos.Opcion;
import com.asiscontrol.dto.matricula.RenovacionDtos.PromocionRequest;
import com.asiscontrol.dto.matricula.RenovacionDtos.PromocionResponse;
import com.asiscontrol.dto.matricula.RenovacionDtos.RechazarRequest;
import com.asiscontrol.dto.matricula.RenovacionDtos.Response;
import com.asiscontrol.dto.matricula.RenovacionDtos.SeccionOpcion;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.Matricula;
import com.asiscontrol.entity.RenovacionMatricula;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.SolicitudMatricula;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoRenovacion;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;
import com.asiscontrol.entity.enums.ResultadoAuditoria;
import com.asiscontrol.entity.enums.ResultadoPromocion;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AlumnoApoderadoRepository;
import com.asiscontrol.repository.CompromisoPagoRepository;
import com.asiscontrol.repository.MatriculaRepository;
import com.asiscontrol.repository.PersonaRepository;
import com.asiscontrol.repository.RenovacionMatriculaRepository;
import com.asiscontrol.repository.SeccionRepository;
import com.asiscontrol.repository.SolicitudMatriculaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class RenovacionMatriculaService {
    private static final List<EstadoMatricula> ORIGEN_VALIDO =
            List.of(EstadoMatricula.ACTIVA, EstadoMatricula.FINALIZADA);
    private static final List<EstadoSolicitudMatricula> SOLICITUD_ABIERTA = List.of(
            EstadoSolicitudMatricula.BORRADOR, EstadoSolicitudMatricula.ENVIADA,
            EstadoSolicitudMatricula.EN_REVISION, EstadoSolicitudMatricula.OBSERVADA);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final RenovacionMatriculaRepository renovaciones;
    private final MatriculaRepository matriculas;
    private final SolicitudMatriculaRepository solicitudes;
    private final SeccionRepository secciones;
    private final PersonaRepository personas;
    private final AlumnoApoderadoRepository vinculosFamiliares;
    private final CompromisoPagoRepository compromisos;
    private final ConfiguracionAcademicaService configuracion;
    private final FamiliaService familia;
    private final ModuloAccessGuard guard;
    private final MatriculaService gestionMatricula;
    private final PlanPensionService planes;
    private final AuditoriaService auditoria;
    private final EntityManager entityManager;

    public RenovacionMatriculaService(RenovacionMatriculaRepository renovaciones, MatriculaRepository matriculas,
            SolicitudMatriculaRepository solicitudes, SeccionRepository secciones, PersonaRepository personas,
            AlumnoApoderadoRepository vinculosFamiliares, CompromisoPagoRepository compromisos,
            ConfiguracionAcademicaService configuracion, FamiliaService familia, ModuloAccessGuard guard,
            MatriculaService gestionMatricula, PlanPensionService planes, AuditoriaService auditoria,
            EntityManager entityManager) {
        this.renovaciones = renovaciones;
        this.matriculas = matriculas;
        this.solicitudes = solicitudes;
        this.secciones = secciones;
        this.personas = personas;
        this.vinculosFamiliares = vinculosFamiliares;
        this.compromisos = compromisos;
        this.configuracion = configuracion;
        this.familia = familia;
        this.guard = guard;
        this.gestionMatricula = gestionMatricula;
        this.planes = planes;
        this.auditoria = auditoria;
        this.entityManager = entityManager;
    }

    public Page<Response> listar(EstadoRenovacion estado, Pageable pagina) {
        return renovaciones.buscarVisibles(guard.restriccionApoderadoPersonaId(), estado, pagina).map(this::mapear);
    }

    public Response obtener(Long id) {
        RenovacionMatricula tramite = buscar(id);
        exigirAsociacion(tramite.getAlumno().getId());
        return mapear(tramite);
    }

    public List<Opcion> opciones() {
        Long personaApoderado = guard.restriccionApoderadoPersonaId();
        return familia.oferta().stream().flatMap(oferta -> {
            var destinos = configuracion.vacantes(oferta.anio()).stream().filter(s -> s.activo()).toList();
            return matriculas.buscarUltimasValidasDelAnio(oferta.anio() - 1, ORIGEN_VALIDO, personaApoderado)
                    .stream()
                    .filter(origen -> oferta.grados().contains(origen.getSeccion().getGrado() + 1)
                            && !matriculas.existsByAlumnoIdAndAnioAcademicoAndActivoTrueAndEstadoIn(
                                    origen.getAlumno().getId(), oferta.anio(), ORIGEN_VALIDO)
                            && !renovaciones.existsByAlumnoIdAndAnioDestinoAndClaveVigenteAndActivoTrue(
                                    origen.getAlumno().getId(), oferta.anio(), RenovacionMatricula.CLAVE_VIGENTE))
                    .map(origen -> {
                        Alumno alumno = origen.getAlumno();
                        int gradoDestino = origen.getSeccion().getGrado() + 1;
                        var opciones = destinos.stream().filter(s -> s.grado() == gradoDestino)
                                .map(s -> new SeccionOpcion(s.id(), s.nombre(), s.vacantes())).toList();
                        return new Opcion(alumno.getId(), alumno.getPersona().nombreCompleto(),
                                alumno.getCodigoAlumno(), origen.getId(), origen.getAnioAcademico(),
                                origen.getSeccion().getGrado(), oferta.anio(), gradoDestino,
                                origen.getResultadoPromocion(), opciones);
                    });
        }).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Response enviar(CrearRequest request) {
        Matricula origen = buscarMatricula(request.idMatriculaAnterior());
        exigirAsociacion(origen.getAlumno().getId());
        Seccion destino = secciones.findByIdAndActivoTrue(request.idSeccion()).orElseThrow(() ->
                new ResourceNotFoundException("SECCION_NO_ENCONTRADA", "No se encontró la sección de destino."));
        int anio = origen.getAnioAcademico() + 1;
        int grado = origen.getSeccion().getGrado() + 1;
        if (destino.getAnioAcademico() != anio || destino.getGrado() != grado || grado > 5) {
            throw new BusinessRuleException("DESTINO_RENOVACION_INVALIDO",
                    "Selecciona una sección del grado siguiente en el año consecutivo.");
        }
        configuracion.bloquear(anio);
        configuracion.exigirAdmision(anio, grado);
        bloquearPersona(origen.getAlumno());
        exigirAsociacion(origen.getAlumno().getId());
        if (!origenVigente(origen)) {
            throw new BusinessRuleException("MATRICULA_ANTERIOR_INVALIDA",
                    "La renovación requiere la última matrícula válida del año anterior.");
        }
        verificarDuplicados(origen.getAlumno().getId(), anio);
        if (renovaciones.existsByAlumnoIdAndAnioDestinoAndClaveVigenteAndActivoTrue(
                origen.getAlumno().getId(), anio, RenovacionMatricula.CLAVE_VIGENTE)) {
            throw new ConflictException("RENOVACION_DUPLICADA",
                    "Ya hay una renovación en revisión o aprobada para ese año.");
        }
        RenovacionMatricula tramite = new RenovacionMatricula();
        tramite.setMatriculaAnterior(origen);
        tramite.setAlumno(origen.getAlumno());
        tramite.setSeccion(destino);
        tramite.setAnioDestino(anio);
        tramite.setGradoDestino(grado);
        renovaciones.saveAndFlush(tramite);
        registrar(tramite, "ENVIAR_RENOVACION", ResultadoAuditoria.EXITOSO, null, null);
        return mapear(tramite);
    }

    public PlanResponse plan(Long id) {
        RenovacionMatricula tramite = buscar(id);
        return planes.previewRenovacion(tramite.getAlumno(), tramite.getAnioDestino(), tramite.getGradoDestino());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Response aprobar(Long id, AprobarRequest request) {
        RenovacionMatricula vista = buscar(id);
        configuracion.bloquear(vista.getAnioDestino());
        bloquearPersona(vista.getAlumno());
        RenovacionMatricula tramite = bloquearPendiente(id, request.version());
        Matricula origen = tramite.getMatriculaAnterior();
        entityManager.refresh(origen, LockModeType.PESSIMISTIC_WRITE);
        if (!origenVigente(origen) || origen.getResultadoPromocion() != ResultadoPromocion.PROMOVIDO) {
            String motivo = origen.getResultadoPromocion() == ResultadoPromocion.NO_PROMOVIDO
                    ? "El estudiante no aprobó el grado anterior."
                    : "No se acreditó la aprobación del grado anterior con un resultado oficial de promoción.";
            return rechazar(tramite, "GRADO_NO_APROBADO", motivo);
        }
        // Comparte el bloqueo del alumno con ajustes de beca y emisiones del plan anual.
        entityManager.refresh(tramite.getAlumno(), LockModeType.PESSIMISTIC_WRITE);
        if (tieneDeudaVencida(tramite.getAlumno().getId())) {
            return rechazar(tramite, "DEUDA_VENCIDA",
                    "El estudiante tiene obligaciones vencidas con importe pendiente.");
        }
        Seccion destino = bloquearSeccionDestino(tramite, request.idSeccion());
        if (destino == null || vacantes(destino) <= 0) {
            if (hayVacanteEnElGrado(tramite)) {
                throw new BusinessRuleException("SECCION_SIN_VACANTES",
                        "La sección no tiene vacantes. Elige otra sección del mismo grado.");
            }
            return rechazar(tramite, "SECCION_SIN_VACANTES",
                    "No hay una vacante disponible en las secciones del grado de destino.");
        }
        configuracion.exigirOferta(tramite.getAnioDestino(), tramite.getGradoDestino(), true);
        verificarDuplicados(tramite.getAlumno().getId(), tramite.getAnioDestino());
        if (request.firmaPlan() == null || request.firmaPlan().isBlank()) {
            throw new BusinessRuleException("REVISION_PLAN_REQUERIDA",
                    "Revisa el plan de pensiones antes de aprobar.");
        }

        String detalleAprobacion = asignarSeccion(tramite, destino);
        SolicitudMatricula soporte = generarSolicitudDeSoporte(tramite, destino);
        var matriculaAprobada = gestionMatricula.crearDesdeRenovacion(
                new CrearMatriculaRequest(soporte.getId(), soporte.getVersion(), request.firmaPlan()));
        tramite.aprobar(matriculas.getReferenceById(matriculaAprobada.id()), Instant.now());
        renovaciones.saveAndFlush(tramite);
        registrar(tramite, "APROBAR_RENOVACION", ResultadoAuditoria.EXITOSO,
                EstadoRenovacion.EN_REVISION.name(), detalleAprobacion);
        return mapear(tramite);
    }

    /** Devuelve null si la sección pedida ya no está disponible; una elegida por administración debe ser válida. */
    private Seccion bloquearSeccionDestino(RenovacionMatricula tramite, Long idSeccionElegida) {
        Long idSeccion = idSeccionElegida == null ? tramite.getSeccion().getId() : idSeccionElegida;
        Seccion seccion = secciones.buscarActivaParaActualizar(idSeccion)
                .filter(candidata -> candidata.getAnioAcademico() == tramite.getAnioDestino()
                        && candidata.getGrado() == tramite.getGradoDestino())
                .orElse(null);
        if (seccion == null && idSeccionElegida != null) {
            throw new BusinessRuleException("DESTINO_RENOVACION_INVALIDO",
                    "Selecciona una sección activa del mismo grado y año de la renovación.");
        }
        return seccion;
    }

    private boolean hayVacanteEnElGrado(RenovacionMatricula tramite) {
        return secciones.findByAnioAcademicoOrderByGradoAscNombreAsc(tramite.getAnioDestino()).stream()
                .anyMatch(seccion -> seccion.isActivo() && seccion.getGrado() == tramite.getGradoDestino()
                        && vacantes(seccion) > 0);
    }

    /** Asigna la sección final y devuelve el detalle que queda en la auditoría. */
    private String asignarSeccion(RenovacionMatricula tramite, Seccion destino) {
        String detalle = "Matrícula generada tras verificar promoción, deuda y vacante.";
        Seccion solicitada = tramite.getSeccion();
        if (solicitada.getId().equals(destino.getId())) {
            return detalle;
        }
        tramite.setSeccion(destino);
        return detalle + " Sección solicitada: " + solicitada.getNombre() + "; asignada: " + destino.getNombre() + ".";
    }

    private SolicitudMatricula generarSolicitudDeSoporte(RenovacionMatricula tramite, Seccion destino) {
        SolicitudMatricula soporte = new SolicitudMatricula();
        soporte.setAlumno(tramite.getAlumno());
        soporte.setSeccion(destino);
        soporte.setAnioAcademico(tramite.getAnioDestino());
        soporte.setGrado(tramite.getGradoDestino());
        soporte.setFechaEnvio(tramite.getFechaCreacion());
        soporte.setEstado(EstadoSolicitudMatricula.EN_REVISION);
        soporte.setObservaciones("Matrícula por renovación anual " + tramite.getId());
        return solicitudes.saveAndFlush(soporte);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Response rechazar(Long id, RechazarRequest request) {
        RenovacionMatricula tramite = bloquearPendiente(id, request.version());
        return rechazar(tramite, "RECHAZO_ADMINISTRATIVO", request.motivo().strip());
    }

    public Page<PromocionResponse> promociones(String busqueda, Pageable pagina) {
        String filtro = busqueda == null || busqueda.isBlank() ? "" : busqueda.strip().toLowerCase(Locale.ROOT);
        return matriculas.buscarParaRegistrarPromocion(ORIGEN_VALIDO, "%" + filtro + "%", pagina)
                .map(this::mapearPromocion);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PromocionResponse guardarPromocion(Long id, PromocionRequest request) {
        Matricula vista = buscarMatricula(id);
        bloquearPersona(vista.getAlumno());
        Matricula origen = matriculas.bloquearPorId(id)
                .orElseThrow(() -> noEncontrado("MATRICULA_ANTERIOR_NO_ENCONTRADA"));
        entityManager.refresh(origen, LockModeType.PESSIMISTIC_WRITE);
        exigirVersion(origen.getVersion(), request.version());
        if (!origenVigente(origen)) {
            throw new BusinessRuleException("MATRICULA_ANTERIOR_INVALIDA",
                    "Registra el resultado en la última matrícula válida del año.");
        }
        if (renovaciones.existsByMatriculaAnteriorIdAndEstadoAndActivoTrue(id, EstadoRenovacion.APROBADA)) {
            throw new ConflictException("PROMOCION_YA_UTILIZADA", "La promoción ya sustentó una renovación aprobada.");
        }
        ResultadoPromocion anterior = origen.getResultadoPromocion();
        origen.setResultadoPromocion(request.resultado());
        origen.setMotivoPromocion(request.motivo().strip());
        matriculas.saveAndFlush(origen);
        auditoria.registrar("REGISTRAR_PROMOCION", ModuloAuditoria.MATRICULA, "Matricula", id.toString(),
                ResultadoAuditoria.EXITOSO, origen.getMotivoPromocion(),
                anterior == null ? null : anterior.name(), request.resultado().name());
        return mapearPromocion(origen);
    }

    private boolean origenVigente(Matricula origen) {
        return matriculas.findFirstByAlumnoIdAndAnioAcademicoAndActivoTrueAndEstadoInOrderByFechaMatriculaDescIdDesc(
                origen.getAlumno().getId(), origen.getAnioAcademico(), ORIGEN_VALIDO)
                .map(Matricula::getId).filter(origen.getId()::equals).isPresent();
    }

    private boolean tieneDeudaVencida(Long alumno) {
        return !compromisos.buscarIdsDeObligacionesVencidas(alumno, LocalDate.now(LIMA), PageRequest.of(0, 1))
                .isEmpty();
    }

    private long vacantes(Seccion seccion) {
        return seccion.getCapacidadMaxima() - matriculas.countBySeccionIdAndEstadoAndActivoTrue(
                seccion.getId(), EstadoMatricula.ACTIVA);
    }

    private void verificarDuplicados(Long alumno, int anio) {
        if (matriculas.existsByAlumnoIdAndAnioAcademicoAndActivoTrueAndEstadoIn(alumno, anio, ORIGEN_VALIDO)) {
            throw new ConflictException("ALUMNO_YA_MATRICULADO", "El estudiante ya está matriculado para ese año.");
        }
        if (solicitudes.contarSolicitudesEnEstados(alumno, anio, SOLICITUD_ABIERTA) > 0) {
            throw new ConflictException("SOLICITUD_MATRICULA_DUPLICADA",
                    "Ya existe una solicitud de matrícula en trámite. Administración debe resolverla antes.");
        }
    }

    private void exigirAsociacion(Long alumno) {
        Long persona = guard.restriccionApoderadoPersonaId();
        if (persona != null && !vinculosFamiliares.existeVinculoActivo(alumno, persona)) {
            throw new AccessDeniedException("La renovación solo está disponible para estudiantes asociados.");
        }
    }

    private void bloquearPersona(Alumno alumno) {
        if (!alumno.isActivo() || personas.lockActiveById(alumno.getPersona().getId()).isEmpty()) {
            throw new BusinessRuleException("ALUMNO_INACTIVO", "El estudiante no está habilitado.");
        }
    }

    private RenovacionMatricula buscar(Long id) {
        return renovaciones.findByIdAndActivoTrue(id).orElseThrow(() -> noEncontrado("RENOVACION_NO_ENCONTRADA"));
    }

    private Matricula buscarMatricula(Long id) {
        return matriculas.findByIdAndActivoTrue(id)
                .orElseThrow(() -> noEncontrado("MATRICULA_ANTERIOR_NO_ENCONTRADA"));
    }

    private RenovacionMatricula bloquearPendiente(Long id, Long version) {
        RenovacionMatricula tramite = renovaciones.bloquear(id)
                .orElseThrow(() -> noEncontrado("RENOVACION_NO_ENCONTRADA"));
        entityManager.refresh(tramite);
        if (tramite.getEstado() != EstadoRenovacion.EN_REVISION) {
            throw new ConflictException("RENOVACION_YA_RESUELTA",
                    "La renovación ya fue resuelta. Actualiza la consulta.");
        }
        exigirVersion(tramite.getVersion(), version);
        return tramite;
    }

    private void exigirVersion(long actual, Long version) {
        if (!Objects.equals(actual, version)) {
            throw new ConflictException("VERSION_DESACTUALIZADA",
                    "El registro cambió. Vuelve a cargarlo antes de confirmar.");
        }
    }

    private Response rechazar(RenovacionMatricula tramite, String codigo, String motivo) {
        tramite.rechazar(codigo, motivo, Instant.now());
        renovaciones.saveAndFlush(tramite);
        registrar(tramite, "RECHAZAR_RENOVACION", ResultadoAuditoria.RECHAZADO,
                EstadoRenovacion.EN_REVISION.name(), motivo);
        return mapear(tramite);
    }

    private void registrar(RenovacionMatricula tramite, String accion, ResultadoAuditoria resultado,
            String antes, String motivo) {
        auditoria.registrar(accion, ModuloAuditoria.MATRICULA, "RenovacionMatricula", tramite.getId().toString(),
                resultado, motivo, antes, tramite.getEstado().name());
    }

    private Response mapear(RenovacionMatricula tramite) {
        Alumno alumno = tramite.getAlumno();
        return new Response(tramite.getId(), alumno.getId(), alumno.getPersona().nombreCompleto(), alumno.getCodigoAlumno(),
                tramite.getMatriculaAnterior().getId(), tramite.getMatriculaAnterior().getAnioAcademico(),
                tramite.getMatriculaAnterior().getSeccion().getGrado(), tramite.getAnioDestino(), tramite.getGradoDestino(),
                tramite.getSeccion().getId(), tramite.getSeccion().getNombre(), tramite.getEstado(), tramite.getCodigoMotivo(),
                tramite.getMotivo(), tramite.getMatricula() == null ? null : tramite.getMatricula().getId(),
                tramite.getFechaCreacion(), tramite.getFechaDecision(), tramite.getVersion());
    }

    private PromocionResponse mapearPromocion(Matricula matricula) {
        Alumno alumno = matricula.getAlumno();
        return new PromocionResponse(matricula.getId(), alumno.getId(), alumno.getPersona().nombreCompleto(),
                alumno.getCodigoAlumno(), matricula.getAnioAcademico(), matricula.getSeccion().getGrado(),
                matricula.getResultadoPromocion(), matricula.getMotivoPromocion(), matricula.getVersion());
    }

    private ResourceNotFoundException noEncontrado(String codigo) {
        return new ResourceNotFoundException(codigo, "No se encontró el trámite o la matrícula indicada.");
    }
}

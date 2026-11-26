package com.asiscontrol.service;

import com.asiscontrol.dto.CargaMasivaDto;
import com.asiscontrol.dto.matricula.MatriculaDtos.ActualizarSolicitudMatriculaRequest;
import com.asiscontrol.dto.matricula.MatriculaDtos.CrearMatriculaRequest;
import com.asiscontrol.dto.matricula.MatriculaDtos.CrearSolicitudMatriculaRequest;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.enums.EstadoMatricula;
import com.asiscontrol.entity.enums.EstadoSolicitudMatricula;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.MatriculaRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * OBS-05 · ASISCTRL-232: carga masiva de matrículas con sección. Cada fila recorre el mismo
 * flujo que la secretaría en pantalla: solicitud, envío, revisión, sección asignada y matrícula
 * finalizada con la firma del plan de cuotas. Se aplican las mismas reglas: año activo, grado
 * ofertado, cupo de la sección, una matrícula por alumno y año, y tarifa del grado para generar
 * las cuotas. Si un paso falla, la fila completa se deshace.
 */
@Service
public class ImportacionMatriculaService {

    private static final List<String> COLUMNAS =
            List.of("codigoAlumno", "anioAcademico", "grado", "seccion", "observaciones");
    private static final List<String> OBLIGATORIAS = COLUMNAS.subList(0, 4);
    private static final List<String> EJEMPLO =
            List.of("A20270001", "2027", "1", "A", "Documentos completos");
    private static final EnumSet<EstadoMatricula> VIGENTES =
            EnumSet.of(EstadoMatricula.ACTIVA, EstadoMatricula.FINALIZADA);

    private final ImportadorExcel importador;
    private final SolicitudMatriculaService solicitudes;
    private final MatriculaService matriculas;
    private final PlanPensionService planes;
    private final AlumnoRepository alumnos;
    private final MatriculaRepository matriculaRepository;
    private final ReferenciasImportacion referencias;
    private final Validator validator;

    public ImportacionMatriculaService(
            ImportadorExcel importador,
            SolicitudMatriculaService solicitudes,
            MatriculaService matriculas,
            PlanPensionService planes,
            AlumnoRepository alumnos,
            MatriculaRepository matriculaRepository,
            ReferenciasImportacion referencias,
            Validator validator) {
        this.importador = importador;
        this.solicitudes = solicitudes;
        this.matriculas = matriculas;
        this.planes = planes;
        this.alumnos = alumnos;
        this.matriculaRepository = matriculaRepository;
        this.referencias = referencias;
        this.validator = validator;
    }

    public CargaMasivaDto.Resultado importar(MultipartFile archivo, boolean confirmar) {
        return importador.importar(archivo, confirmar, OBLIGATORIAS, fila -> {
            Alumno alumno = alumno(fila.obligatorio("codigoAlumno"));
            var seccion = referencias.seccion(fila);
            String observaciones = fila.opcional("observaciones");
            var crear = new CrearSolicitudMatriculaRequest(
                    alumno.getId(), null, observaciones, seccion.getAnioAcademico(), seccion.getGrado());
            LectorExcel.validar(validator, crear);
            return new ImportadorExcel.Paso(
                    alumno.getId() + "|" + seccion.getAnioAcademico(),
                    "codigoAlumno",
                    matriculaRepository.existsByAlumnoIdAndAnioAcademicoAndActivoTrueAndEstadoIn(
                            alumno.getId(), seccion.getAnioAcademico(), VIGENTES),
                    () -> {
                        var solicitud = solicitudes.crear(crear);
                        solicitud = estado(solicitud.id(), solicitud.version(), EstadoSolicitudMatricula.ENVIADA);
                        solicitud = estado(solicitud.id(), solicitud.version(), EstadoSolicitudMatricula.EN_REVISION);
                        solicitud = solicitudes.actualizar(solicitud.id(), new ActualizarSolicitudMatriculaRequest(
                                seccion.getId(), null, null, null, solicitud.version()));
                        String firma = planes.previewSolicitud(solicitud.id()).firmaPlan();
                        matriculas.crear(new CrearMatriculaRequest(solicitud.id(), solicitud.version(), firma));
                    });
        });
    }

    public byte[] plantilla() {
        return PlantillaExcel.build("Matriculas", COLUMNAS, EJEMPLO, Map.of());
    }

    private com.asiscontrol.dto.matricula.MatriculaDtos.SolicitudMatriculaResponse estado(
            Long id, long version, EstadoSolicitudMatricula estado) {
        return solicitudes.actualizar(id, new ActualizarSolicitudMatriculaRequest(null, estado, null, null, version));
    }

    private Alumno alumno(String codigo) {
        return alumnos.findByCodigoAlumnoIgnoreCase(codigo)
                .filter(Alumno::isActivo)
                .orElseThrow(() -> new BusinessRuleException(
                        "ALUMNO_NO_ENCONTRADO",
                        "No hay un estudiante habilitado con el código " + codigo,
                        "codigoAlumno"));
    }
}

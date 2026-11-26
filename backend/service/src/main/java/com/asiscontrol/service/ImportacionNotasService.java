package com.asiscontrol.service;

import com.asiscontrol.dto.CalificacionDto;
import com.asiscontrol.dto.CargaMasivaDto;
import com.asiscontrol.entity.Alumno;
import com.asiscontrol.entity.Competencia;
import com.asiscontrol.entity.enums.EstadoEvaluacion;
import com.asiscontrol.entity.enums.NivelLogro;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.CalificacionRepository;
import com.asiscontrol.repository.EvaluacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * OBS-05 · ASISCTRL-234: carga masiva de notas en la planilla del docente. Hay una plantilla por
 * evaluación, ya completada con los alumnos matriculados en la sección y las competencias de la
 * evaluación; el docente solo escribe el nivel de logro. Cada fila pasa por
 * {@link CalificacionService#registrar}, con las mismas reglas que la planilla: solo el docente de
 * la asignación (o el administrador), evaluación en borrador, alumno con matrícula activa en la
 * sección y competencia de la evaluación. Como en la planilla, en borrador un nivel puede
 * cambiarse; lo publicado se corrige con motivo desde la pantalla.
 */
@Service
public class ImportacionNotasService {

    private static final List<String> COLUMNAS = List.of(
            "codigoAlumno", "alumno", "codigoCompetencia", "competencia", "nivelLogro", "observacion");
    private static final List<String> OBLIGATORIAS = List.of("codigoAlumno", "codigoCompetencia");
    private static final List<String> NIVELES = Arrays.stream(NivelLogro.values()).map(Enum::name).toList();

    /** Lo necesario de la evaluación, leído en una transacción: el resto corre sin ella. */
    private record Contexto(Long evaluacionId, Long seccionId, String titulo,
                            Map<String, Competencia> competencias) {}

    private final ImportadorExcel importador;
    private final CalificacionService calificaciones;
    private final EvaluacionRepository evaluaciones;
    private final CalificacionRepository calificacionRepository;
    private final AlumnoRepository alumnos;
    private final MatriculaService matriculas;
    private final ModuloAccessGuard accessGuard;
    private final TransactionTemplate lectura;

    public ImportacionNotasService(
            ImportadorExcel importador,
            CalificacionService calificaciones,
            EvaluacionRepository evaluaciones,
            CalificacionRepository calificacionRepository,
            AlumnoRepository alumnos,
            MatriculaService matriculas,
            ModuloAccessGuard accessGuard,
            PlatformTransactionManager transactionManager) {
        this.importador = importador;
        this.calificaciones = calificaciones;
        this.evaluaciones = evaluaciones;
        this.calificacionRepository = calificacionRepository;
        this.alumnos = alumnos;
        this.matriculas = matriculas;
        this.accessGuard = accessGuard;
        this.lectura = new TransactionTemplate(transactionManager);
        this.lectura.setReadOnly(true);
    }

    public CargaMasivaDto.Resultado importar(Long evaluacionId, MultipartFile archivo, boolean confirmar) {
        Contexto contexto = contexto(evaluacionId, true);
        return importador.importar(archivo, confirmar, OBLIGATORIAS, fila -> {
            String codigoAlumno = fila.obligatorio("codigoAlumno");
            Alumno alumno = alumnos.findByCodigoAlumnoIgnoreCase(codigoAlumno)
                    .filter(Alumno::isActivo)
                    .orElseThrow(() -> new BusinessRuleException(
                            "ALUMNO_NO_ENCONTRADO",
                            "No hay un estudiante habilitado con el código " + codigoAlumno,
                            "codigoAlumno"));
            String codigoCompetencia = fila.obligatorio("codigoCompetencia");
            Competencia competencia = contexto.competencias().get(codigoCompetencia.toUpperCase(Locale.ROOT));
            if (competencia == null) {
                throw new BusinessRuleException(
                        "COMPETENCIA_NO_EVALUADA",
                        "La competencia " + codigoCompetencia + " no forma parte de esta evaluación",
                        "codigoCompetencia");
            }
            String clave = alumno.getId() + "|" + competencia.getId();
            if (fila.opcional("nivelLogro") == null) {
                // La plantilla trae todas las celdas: las que el docente deja vacías no se registran.
                return new ImportadorExcel.Paso(clave, "nivelLogro", true, () -> {},
                        "Sin nivel de logro; no se registra");
            }
            NivelLogro nivel = fila.enumerado(NivelLogro.class, "nivelLogro", List.of(NivelLogro.values()));
            String observacion = fila.opcional("observacion");
            boolean igual = calificacionRepository
                    .findByEvaluacionIdAndAlumnoIdAndCompetenciaIdAndActivoTrue(
                            contexto.evaluacionId(), alumno.getId(), competencia.getId())
                    .filter(actual -> actual.getNivelLogro() == nivel
                            && Objects.equals(actual.getObservacion(), observacion))
                    .isPresent();
            return new ImportadorExcel.Paso(clave, "nivelLogro", igual,
                    () -> calificaciones.registrar(new CalificacionDto.Request(
                            contexto.evaluacionId(), alumno.getId(), competencia.getId(), nivel, observacion)),
                    "Ya tiene ese nivel; no se duplica");
        });
    }

    /** Planilla de la evaluación con una fila por alumno y competencia, y los niveles ya guardados. */
    public byte[] plantilla(Long evaluacionId) {
        Contexto contexto = contexto(evaluacionId, false);
        List<List<String>> filas = lectura.execute(estado -> {
            var competencias = contexto.competencias().values().stream()
                    .sorted(Comparator.comparing(Competencia::getCodigo))
                    .toList();
            Map<String, String> guardadas = calificacionRepository
                    .findAllByEvaluacionIdAndActivoTrue(contexto.evaluacionId()).stream()
                    .collect(Collectors.toMap(
                            c -> c.getAlumno().getId() + "|" + c.getCompetencia().getId(),
                            c -> c.getNivelLogro() == null ? "" : c.getNivelLogro().name(),
                            (a, b) -> a));
            List<List<String>> resultado = new ArrayList<>();
            matriculas.alumnosConMatriculaActiva(contexto.seccionId()).stream()
                    .sorted(Comparator.comparing(a -> a.getPersona().nombreCompleto()))
                    .forEach(alumno -> competencias.forEach(competencia -> resultado.add(List.of(
                            alumno.getCodigoAlumno(),
                            alumno.getPersona().nombreCompleto(),
                            competencia.getCodigo(),
                            competencia.getNombre(),
                            guardadas.getOrDefault(alumno.getId() + "|" + competencia.getId(), ""),
                            ""))));
            return resultado;
        });
        return PlantillaExcel.buildConFilas("Notas", COLUMNAS, filas, Map.of("nivelLogro", NIVELES));
    }

    private Contexto contexto(Long evaluacionId, boolean paraRegistrar) {
        return lectura.execute(estado -> {
            var evaluacion = evaluaciones.findByIdAndActivoTrue(evaluacionId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "EVALUACION_NO_ENCONTRADA", "No se encontró la evaluación " + evaluacionId));
            accessGuard.verificarAsignacionDocente(evaluacion.getAsignacionCurso());
            if (paraRegistrar && evaluacion.getEstado() != EstadoEvaluacion.BORRADOR) {
                throw new BusinessRuleException(
                        "EVALUACION_NO_ADMITE_CARGA",
                        "Solo se cargan notas en una evaluación en borrador; lo publicado se corrige con motivo en la planilla");
            }
            Map<String, Competencia> competencias = evaluacion.getCompetencias().stream()
                    .collect(Collectors.toMap(
                            c -> c.getCodigo().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));
            return new Contexto(evaluacion.getId(), evaluacion.getAsignacionCurso().getSeccion().getId(),
                    evaluacion.getTitulo(), competencias);
        });
    }
}

package com.asiscontrol.service;

import com.asiscontrol.dto.CargaMasivaDto;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearCursoRequest;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.CursoEnPlanRequest;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.GuardarPlan;
import com.asiscontrol.entity.Competencia;
import com.asiscontrol.entity.Curso;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.repository.CompetenciaRepository;
import com.asiscontrol.repository.CursoRepository;
import com.asiscontrol.repository.GradoAcademicoRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * OBS-05 · ASISCTRL-230: carga masiva de cursos y plan de estudios. Cada fila agrega un curso al
 * plan de un año y grado con {@link PlanEstudiosService#guardar}, igual que la pantalla del plan.
 * Si el código no existe en el catálogo, primero se registra el curso con {@link CursoService}.
 */
@Service
public class ImportacionPlanEstudiosService {

    private static final List<String> COLUMNAS = List.of(
            "anioAcademico", "grado", "codigoCurso", "nombreCurso", "horasSemanales",
            "codigosCompetencias", "descripcion", "motivo");
    private static final List<String> OBLIGATORIAS = COLUMNAS.subList(0, 5);
    private static final List<String> EJEMPLO = List.of(
            "2027", "1", "MAT-1", "Matemática", "6", "COM-01;COM-02",
            "Opcional: se usa solo si el curso es nuevo", "");

    private final ImportadorExcel importador;
    private final PlanEstudiosService planes;
    private final CursoService cursoService;
    private final CursoRepository cursos;
    private final CompetenciaRepository competencias;
    private final GradoAcademicoRepository grados;
    private final Validator validator;

    public ImportacionPlanEstudiosService(
            ImportadorExcel importador,
            PlanEstudiosService planes,
            CursoService cursoService,
            CursoRepository cursos,
            CompetenciaRepository competencias,
            GradoAcademicoRepository grados,
            Validator validator) {
        this.importador = importador;
        this.planes = planes;
        this.cursoService = cursoService;
        this.cursos = cursos;
        this.competencias = competencias;
        this.grados = grados;
        this.validator = validator;
    }

    public CargaMasivaDto.Resultado importar(MultipartFile archivo, boolean confirmar) {
        // Un plan ya definido exige motivo para cambiarlo. Las filas siguientes del mismo grado en
        // este archivo son parte del mismo cambio: si no traen motivo, se registra la carga.
        Set<String> gradosDelArchivo = new HashSet<>();
        String motivoCarga = "Carga masiva del plan de estudios (" + archivo.getOriginalFilename() + ")";
        return importador.importar(archivo, confirmar, OBLIGATORIAS, fila -> {
            int anio = fila.entero("anioAcademico");
            int grado = fila.entero("grado");
            String codigo = fila.obligatorio("codigoCurso").toUpperCase(Locale.ROOT);
            String nombre = fila.obligatorio("nombreCurso");
            int horas = fila.entero("horasSemanales");
            String descripcion = fila.opcional("descripcion");
            List<Long> competenciasIds = competencias(fila.opcional("codigosCompetencias"));
            LectorExcel.validar(validator, new CursoEnPlanRequest(1L, nombre, horas, competenciasIds));

            var plan = planes.consultar(anio, grado);
            boolean enPlan = plan.cursos().stream().anyMatch(c -> c.codigo().equalsIgnoreCase(codigo));
            String claveGrado = anio + "|" + grado;
            String motivo = fila.opcional("motivo") != null || !gradosDelArchivo.contains(claveGrado)
                    ? fila.opcional("motivo")
                    : motivoCarga;
            return new ImportadorExcel.Paso(
                    claveGrado + "|" + codigo,
                    "codigoCurso",
                    enPlan,
                    () -> {
                        Long cursoId = cursoDelCatalogo(codigo, nombre, descripcion, horas, anio, grado);
                        var actual = planes.consultar(anio, grado);
                        // Un plan sin definir ya muestra los cursos del catálogo de su grado, entre
                        // ellos el recién creado: se reemplaza por los datos de la fila.
                        List<CursoEnPlanRequest> lista = new ArrayList<>(actual.cursos().stream()
                                .filter(c -> !c.id().equals(cursoId))
                                .map(c -> new CursoEnPlanRequest(c.id(), c.nombre(), c.horasSemanales(), null))
                                .toList());
                        lista.add(new CursoEnPlanRequest(cursoId, nombre, horas, competenciasIds));
                        planes.guardar(anio, grado, new GuardarPlan(actual.version(), null, motivo, lista));
                        gradosDelArchivo.add(claveGrado);
                    });
        });
    }

    public byte[] plantilla() {
        return PlantillaExcel.build("Plan de estudios", COLUMNAS, EJEMPLO, Map.of());
    }

    /** Reutiliza el curso del catálogo o lo registra en el grado del plan con las reglas de CursoService. */
    private Long cursoDelCatalogo(
            String codigo, String nombre, String descripcion, int horas, int anio, int grado) {
        var existente = cursos.findByCodigoIgnoreCase(codigo);
        if (existente.isPresent()) {
            Curso curso = existente.get();
            if (!curso.isActivo()) {
                throw new BusinessRuleException(
                        "CURSO_INACTIVO", "El curso " + codigo + " está inhabilitado en el catálogo",
                        "codigoCurso");
            }
            return curso.getId();
        }
        Long gradoId = grados.findByAnioAcademicoAnioAndNumero(anio, grado)
                .map(g -> g.getId())
                .orElseThrow(() -> new BusinessRuleException(
                        "GRADO_NO_ENCONTRADO", "El grado " + grado + " no existe en " + anio, "grado"));
        return cursoService.crear(new CrearCursoRequest(codigo, nombre, descripcion, horas, gradoId)).id();
    }

    /** Códigos separados por punto y coma; vacío deja las competencias base del curso. */
    private List<Long> competencias(String codigos) {
        if (codigos == null) {
            return null;
        }
        return Arrays.stream(codigos.split("[;,]"))
                .map(String::trim)
                .filter(codigo -> !codigo.isEmpty())
                .distinct()
                .map(codigo -> competencias.findByCodigoIgnoreCaseAndActivoTrue(codigo)
                        .map(Competencia::getId)
                        .orElseThrow(() -> new BusinessRuleException(
                                "COMPETENCIA_NO_ENCONTRADA",
                                "No hay una competencia activa con el código " + codigo,
                                "codigosCompetencias")))
                .toList();
    }
}

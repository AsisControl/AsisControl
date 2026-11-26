package com.asiscontrol.service;

import com.asiscontrol.dto.CargaMasivaDto;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearAsignacionCursoRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OBS-05 · ASISCTRL-231: carga masiva de asignaciones docentes. Cada fila pasa por
 * {@link AsignacionCursoService#crear}: el curso debe estar en el plan del grado de la sección, el
 * año no puede estar cerrado y solo hay un docente activo por curso y sección.
 */
@Service
public class ImportacionAsignacionService {

    private static final List<String> COLUMNAS =
            List.of("anioAcademico", "grado", "seccion", "codigoCurso", "codigoDocente");
    private static final List<String> EJEMPLO = List.of("2027", "1", "A", "MAT-1", "DOC20270001");

    private final ImportadorExcel importador;
    private final AsignacionCursoService asignaciones;
    private final ReferenciasImportacion referencias;

    public ImportacionAsignacionService(
            ImportadorExcel importador,
            AsignacionCursoService asignaciones,
            ReferenciasImportacion referencias) {
        this.importador = importador;
        this.asignaciones = asignaciones;
        this.referencias = referencias;
    }

    public CargaMasivaDto.Resultado importar(MultipartFile archivo, boolean confirmar) {
        return importador.importar(archivo, confirmar, COLUMNAS, fila -> {
            var seccion = referencias.seccion(fila);
            var curso = referencias.curso(fila.obligatorio("codigoCurso").toUpperCase(Locale.ROOT));
            var docente = referencias.docente(fila.obligatorio("codigoDocente"));
            return new ImportadorExcel.Paso(
                    seccion.getId() + "|" + curso.getId(),
                    "codigoCurso",
                    referencias.asignado(curso, seccion),
                    () -> asignaciones.crear(new CrearAsignacionCursoRequest(
                            curso.getId(), seccion.getId(), docente.getId())));
        });
    }

    public byte[] plantilla() {
        return PlantillaExcel.build("Asignaciones docentes", COLUMNAS, EJEMPLO, Map.of());
    }
}

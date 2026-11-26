package com.asiscontrol.service;

import com.asiscontrol.dto.CargaMasivaDto;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearSeccionRequest;
import com.asiscontrol.entity.Docente;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.repository.DocenteRepository;
import com.asiscontrol.repository.SeccionRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * OBS-05 · ASISCTRL-229: carga masiva de secciones. Cada fila pasa por {@link SeccionService}:
 * grado ofertado y activo, sección única por año y grado, y motivo si el año ya está activo.
 */
@Service
public class ImportacionSeccionService {

    private static final List<String> COLUMNAS = List.of(
            "anioAcademico", "grado", "nombre", "capacidadMaxima", "codigoDocenteTutor", "motivo");
    private static final List<String> OBLIGATORIAS = COLUMNAS.subList(0, 4);
    private static final List<String> EJEMPLO =
            List.of("2027", "1", "A", "30", "", "Apertura del año 2027");

    private final ImportadorExcel importador;
    private final SeccionService secciones;
    private final SeccionRepository repositorio;
    private final DocenteRepository docentes;
    private final Validator validator;

    public ImportacionSeccionService(
            ImportadorExcel importador,
            SeccionService secciones,
            SeccionRepository repositorio,
            DocenteRepository docentes,
            Validator validator) {
        this.importador = importador;
        this.secciones = secciones;
        this.repositorio = repositorio;
        this.docentes = docentes;
        this.validator = validator;
    }

    public CargaMasivaDto.Resultado importar(MultipartFile archivo, boolean confirmar) {
        return importador.importar(archivo, confirmar, OBLIGATORIAS, fila -> {
            var solicitud = new CrearSeccionRequest(
                    fila.obligatorio("nombre").toUpperCase(java.util.Locale.ROOT),
                    fila.entero("grado"),
                    fila.entero("anioAcademico"),
                    fila.entero("capacidadMaxima"),
                    tutor(fila.opcional("codigoDocenteTutor")),
                    fila.opcional("motivo"));
            LectorExcel.validar(validator, solicitud);
            int anio = solicitud.anioAcademico();
            int grado = solicitud.grado();
            String nombre = solicitud.nombre().trim();
            return new ImportadorExcel.Paso(
                    anio + "|" + grado + "|" + nombre,
                    "nombre",
                    repositorio.findByAnioAcademicoAndGradoAndNombreIgnoreCase(anio, grado, nombre)
                            .isPresent(),
                    () -> secciones.crear(solicitud));
        });
    }

    public byte[] plantilla() {
        return PlantillaExcel.build("Secciones", COLUMNAS, EJEMPLO, Map.of());
    }

    private Long tutor(String codigo) {
        if (codigo == null) {
            return null;
        }
        return docentes.findByCodigoDocenteIgnoreCase(codigo)
                .filter(Docente::isActivo)
                .map(Docente::getId)
                .orElseThrow(() -> new BusinessRuleException(
                        "DOCENTE_NO_ENCONTRADO",
                        "No hay un docente activo con el código " + codigo,
                        "codigoDocenteTutor"));
    }
}

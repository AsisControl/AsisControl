package com.asiscontrol.service;

import com.asiscontrol.dto.academico.AcademicoDtos.CrearCompetenciaRequest;
import com.asiscontrol.dto.persona.PersonaDtos.ImportIssue;
import com.asiscontrol.exception.ConflictException;
import com.asiscontrol.repository.CompetenciaRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ImportacionCompetenciaService {
    private static final List<String> REQUERIDAS = List.of("codigo", "nombre");
    private final CompetenciaService competencias;
    private final CompetenciaRepository repositorio;
    private final Validator validator;

    public ImportacionCompetenciaService(CompetenciaService competencias, CompetenciaRepository repositorio,
            Validator validator) {
        this.competencias = competencias;
        this.repositorio = repositorio;
        this.validator = validator;
    }

    public record Resultado(int total, int validas, int creadas, List<ImportIssue> omitidas, List<ImportIssue> errores) {}

    public Resultado importar(MultipartFile archivo, boolean confirmar) {
        var codigos = new HashSet<String>();
        var omitidas = new ArrayList<ImportIssue>();
        var resultado = LectorExcel.recorrer(archivo, REQUERIDAS, fila -> {
            String codigo = fila.obligatorio("codigo").toUpperCase(Locale.ROOT);
            var solicitud = new CrearCompetenciaRequest(codigo, fila.obligatorio("nombre"), fila.opcional("descripcion"));
            LectorExcel.validar(validator, solicitud);
            if (!codigos.add(codigo) || repositorio.findByCodigoIgnoreCase(codigo).isPresent()) {
                omitidas.add(new ImportIssue(fila.numero(), "codigo", "El código ya está en el catálogo o en el archivo."));
                return;
            }
            if (confirmar) {
                try {
                    competencias.crear(solicitud);
                } catch (ConflictException conflicto) {
                    omitidas.add(new ImportIssue(fila.numero(), "codigo", conflicto.getMessage()));
                }
            }
        });
        int validas = resultado.importadas() - omitidas.size();
        return new Resultado(resultado.total(), validas, confirmar ? validas : 0,
                List.copyOf(omitidas), resultado.issues());
    }

    public byte[] plantilla() {
        return PlantillaExcel.build("Competencias", List.of("codigo", "nombre", "descripcion"),
                List.of("COM-01", "Se comunica oralmente", "Descripción opcional"), Map.of());
    }
}

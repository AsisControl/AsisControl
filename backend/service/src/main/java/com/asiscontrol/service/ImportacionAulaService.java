package com.asiscontrol.service;

import com.asiscontrol.dto.CargaMasivaDto;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearAulaRequest;
import com.asiscontrol.entity.enums.TipoAula;
import com.asiscontrol.repository.AulaRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** OBS-05 · ASISCTRL-229: carga masiva de aulas con las reglas del alta individual. */
@Service
public class ImportacionAulaService {

    private static final List<String> COLUMNAS =
            List.of("codigo", "nombre", "tipoAula", "capacidad", "ubicacion");
    private static final List<String> OBLIGATORIAS = COLUMNAS.subList(0, 4);
    private static final List<String> EJEMPLO =
            List.of("A-101", "Aula 101", "AULA_REGULAR", "35", "Pabellón A, primer piso");

    private final ImportadorExcel importador;
    private final AulaService aulas;
    private final AulaRepository repositorio;
    private final Validator validator;

    public ImportacionAulaService(
            ImportadorExcel importador,
            AulaService aulas,
            AulaRepository repositorio,
            Validator validator) {
        this.importador = importador;
        this.aulas = aulas;
        this.repositorio = repositorio;
        this.validator = validator;
    }

    public CargaMasivaDto.Resultado importar(MultipartFile archivo, boolean confirmar) {
        return importador.importar(archivo, confirmar, OBLIGATORIAS, fila -> {
            var solicitud = new CrearAulaRequest(
                    fila.obligatorio("codigo"),
                    fila.obligatorio("nombre"),
                    fila.enumerado(TipoAula.class, "tipoAula", List.of(TipoAula.values())),
                    fila.entero("capacidad"),
                    fila.opcional("ubicacion"));
            LectorExcel.validar(validator, solicitud);
            // Mismo criterio que AulaService: espacios simplificados y sin distinguir mayúsculas.
            String codigo = solicitud.codigo().trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
            return new ImportadorExcel.Paso(
                    codigo,
                    "codigo",
                    repositorio.findByCodigoIgnoreCase(codigo).isPresent(),
                    () -> aulas.crear(solicitud));
        });
    }

    public byte[] plantilla() {
        return PlantillaExcel.build("Aulas", COLUMNAS, EJEMPLO, Map.of(
                "tipoAula", Arrays.stream(TipoAula.values()).map(Enum::name).toList()));
    }
}

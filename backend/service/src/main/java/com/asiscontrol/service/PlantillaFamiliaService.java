package com.asiscontrol.service;

import com.asiscontrol.entity.enums.Parentesco;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Plantillas de apoderados y parentescos para preparar la carga de familias. Los ejemplos
 * coinciden con el de la plantilla de alumnos (EST-0001) para que las tres se lean juntas:
 * los parentescos unen el codigo del alumno con el documento del apoderado.
 */
@Service
public class PlantillaFamiliaService {

    static final List<String> GUARDIAN_HEADERS = List.of(
            "tipoDocumento", "numeroDocumento", "nombres", "apellidoPaterno",
            "apellidoMaterno", "sexo", "fechaNacimiento", "telefono", "correo", "direccion");
    private static final List<String> GUARDIAN_EXAMPLE = List.of(
            "DNI", "40000001", "Rosa", "Flores", "Vega", "FEMENINO", "1985-03-14",
            "999888777", "rosa.flores@ejemplo.com", "Lima");
    static final List<String> RELATIONSHIP_HEADERS = List.of(
            "codigoAlumno", "tipoDocumentoApoderado", "numeroDocumentoApoderado",
            "parentesco", "principal");
    private static final List<String> RELATIONSHIP_EXAMPLE = List.of(
            "EST-0001", "DNI", "40000001", "MADRE", "SI");

    public byte[] generateGuardianTemplate() {
        return PlantillaExcel.build(
                "Apoderados",
                GUARDIAN_HEADERS,
                GUARDIAN_EXAMPLE,
                Map.of("tipoDocumento", names(TipoDocumento.values()), "sexo", names(Sexo.values())));
    }

    public byte[] generateRelationshipTemplate() {
        return PlantillaExcel.build(
                "Parentescos",
                RELATIONSHIP_HEADERS,
                RELATIONSHIP_EXAMPLE,
                Map.of(
                        "tipoDocumentoApoderado", names(TipoDocumento.values()),
                        "parentesco",
                        names(ImportacionFamiliaService.PARENTESCOS_IMPORTABLES
                                .toArray(Parentesco[]::new)),
                        "principal", List.of("SI", "NO")));
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}

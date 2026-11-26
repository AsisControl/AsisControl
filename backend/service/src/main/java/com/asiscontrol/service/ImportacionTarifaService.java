package com.asiscontrol.service;

import com.asiscontrol.dto.CargaMasivaDto;
import com.asiscontrol.dto.ConfiguracionPagosDto.TarifaRequest;
import com.asiscontrol.entity.ConceptoCobro;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.repository.ConceptoCobroRepository;
import com.asiscontrol.repository.TarifaPensionRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * OBS-05 · ASISCTRL-233: carga masiva de tarifas de pensión. Cada fila pasa por
 * {@link ConfiguracionPagosService#guardarTarifa}: el concepto debe ser el de pensión, el año no
 * puede estar cerrado y hay una sola tarifa por concepto, año y grado.
 */
@Service
public class ImportacionTarifaService {

    private static final List<String> COLUMNAS =
            List.of("codigoConcepto", "anioAcademico", "grado", "montoMensual");
    private static final List<String> EJEMPLO = List.of("PENSION", "2027", "1", "450.00");

    private final ImportadorExcel importador;
    private final ConfiguracionPagosService configuracion;
    private final ConceptoCobroRepository conceptos;
    private final TarifaPensionRepository tarifas;
    private final Validator validator;

    public ImportacionTarifaService(
            ImportadorExcel importador,
            ConfiguracionPagosService configuracion,
            ConceptoCobroRepository conceptos,
            TarifaPensionRepository tarifas,
            Validator validator) {
        this.importador = importador;
        this.configuracion = configuracion;
        this.conceptos = conceptos;
        this.tarifas = tarifas;
        this.validator = validator;
    }

    public CargaMasivaDto.Resultado importar(MultipartFile archivo, boolean confirmar) {
        return importador.importar(archivo, confirmar, COLUMNAS, fila -> {
            String codigo = fila.obligatorio("codigoConcepto");
            ConceptoCobro concepto = conceptos.findByCodigoIgnoreCase(codigo)
                    .filter(ConceptoCobro::isActivo)
                    .orElseThrow(() -> new BusinessRuleException(
                            "CONCEPTO_NO_ENCONTRADO",
                            "No hay un concepto de cobro activo con el código " + codigo,
                            "codigoConcepto"));
            var solicitud = new TarifaRequest(
                    concepto.getId(),
                    fila.entero("anioAcademico"),
                    fila.entero("grado"),
                    monto(fila.obligatorio("montoMensual")),
                    null);
            LectorExcel.validar(validator, solicitud);
            return new ImportadorExcel.Paso(
                    concepto.getId() + "|" + solicitud.anioAcademico() + "|" + solicitud.grado(),
                    "grado",
                    tarifas.findByConceptoIdAndAnioAcademicoAndGrado(
                                    concepto.getId(), solicitud.anioAcademico(), solicitud.grado())
                            .isPresent(),
                    () -> configuracion.guardarTarifa(null, solicitud));
        });
    }

    public byte[] plantilla() {
        return PlantillaExcel.build("Tarifas", COLUMNAS, EJEMPLO, Map.of());
    }

    /** Acepta punto o coma decimal ("450.50" o "450,50"). */
    private static BigDecimal monto(String valor) {
        try {
            return new BigDecimal(valor.replace(',', '.'));
        } catch (NumberFormatException exception) {
            throw new BusinessRuleException(
                    "MONTO_INVALIDO", "La columna montoMensual debe ser un número, por ejemplo 450.00",
                    "montoMensual");
        }
    }
}

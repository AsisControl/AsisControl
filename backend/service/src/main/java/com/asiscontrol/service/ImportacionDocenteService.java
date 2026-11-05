package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;
import com.asiscontrol.entity.enums.Especialidad;
import com.asiscontrol.entity.enums.Sexo;
import com.asiscontrol.entity.enums.TipoDocumento;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * US-51: carga masiva de perfiles docentes con las columnas de Docente y Persona. Cada fila pasa
 * por las mismas reglas que el alta individual. Primero se analiza el archivo sin guardar; al
 * confirmar se crean las filas validas y las demas quedan rechazadas con su motivo.
 */
@Service
public class ImportacionDocenteService {

    private static final List<String> COLUMNAS = List.of(
            "codigoDocente", "tipoDocumento", "numeroDocumento", "nombres", "apellidoPaterno",
            "apellidoMaterno", "sexo", "fechaNacimiento", "fechaIngreso", "especialidad",
            "telefono", "correo", "direccion");
    private static final List<String> EJEMPLO = List.of(
            "DOC-0001", "DNI", "41000001", "Patricia", "Morales", "Rios", "FEMENINO", "1985-03-14",
            "2020-03-01", "MATEMATICA", "999888777", "patricia.morales@ejemplo.com", "Lima");
    /**
     * Desde V5 la base admite personas sin segundo apellido, documento, sexo ni correo (fichas de
     * alumnos), pero un docente necesita esos datos para identificarlo y contactarlo.
     */
    private static final List<String> COLUMNAS_OBLIGATORIAS = COLUMNAS.stream()
            .filter(columna -> !List.of("telefono", "direccion").contains(columna))
            .toList();

    private final PersonaService personaService;
    private final Validator validator;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transaccion;

    public ImportacionDocenteService(
            PersonaService personaService,
            Validator validator,
            AuditoriaService auditoria,
            PlatformTransactionManager transactionManager) {
        this.personaService = personaService;
        this.validator = validator;
        this.auditoria = auditoria;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    /**
     * Sin confirmar, procesa el archivo en una transaccion que siempre se deshace: asi tambien se
     * detectan documentos y codigos repetidos dentro del mismo archivo. Al confirmar, cada fila
     * se guarda en su propia transaccion y una fila rechazada no afecta a las demas.
     */
    public PersonaDtos.ImportResult importar(MultipartFile archivo, boolean confirmar) {
        if (confirmar) {
            return registrarFilas(archivo);
        }
        return transaccion.execute(estado -> {
            estado.setRollbackOnly();
            return registrarFilas(archivo);
        });
    }

    public byte[] generarPlantilla() {
        return PlantillaExcel.build(
                "Docentes",
                COLUMNAS,
                EJEMPLO,
                Map.of(
                        "tipoDocumento", nombres(TipoDocumento.values()),
                        "sexo", nombres(Sexo.values()),
                        "especialidad", nombres(Especialidad.values())));
    }

    private PersonaDtos.ImportResult registrarFilas(MultipartFile archivo) {
        var resumen = LectorExcel.recorrer(archivo, COLUMNAS_OBLIGATORIAS, fila -> {
            var solicitud = aSolicitud(fila);
            LectorExcel.validar(validator, solicitud);
            personaService.createDocente(solicitud);
        });
        resumen.auditar(auditoria, "IMPORTAR_DOCENTES", "Docente", archivo);
        return resumen.resultado();
    }

    private PersonaDtos.DocenteCreateRequest aSolicitud(LectorExcel.Fila fila) {
        var persona = new PersonaDtos.PersonaRequest(
                fila.obligatorio("nombres"),
                fila.obligatorio("apellidoPaterno"),
                fila.obligatorio("apellidoMaterno"),
                fila.enumerado(TipoDocumento.class, "tipoDocumento", List.of(TipoDocumento.values())),
                fila.obligatorio("numeroDocumento"),
                null,
                fila.enumerado(Sexo.class, "sexo", List.of(Sexo.values())),
                fila.fecha("fechaNacimiento"),
                fila.opcional("telefono"),
                fila.obligatorio("correo"),
                fila.opcional("direccion"));
        return new PersonaDtos.DocenteCreateRequest(
                persona,
                fila.obligatorio("codigoDocente"),
                fila.fecha("fechaIngreso"),
                fila.enumerado(Especialidad.class, "especialidad", List.of(Especialidad.values())));
    }

    private static List<String> nombres(Enum<?>[] valores) {
        return Arrays.stream(valores).map(Enum::name).toList();
    }
}

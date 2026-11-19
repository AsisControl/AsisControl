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
            "tipoDocumento", "numeroDocumento", "nombres", "apellidoPaterno",
            "apellidoMaterno", "sexo", "fechaNacimiento", "nacionalidad", "fechaIngreso",
            "especialidad", "telefono", "correo", "direccion");
    private static final List<String> EJEMPLO = List.of(
            "DNI", "41000001", "Patricia", "Morales", "Rios", "FEMENINO", "1985-03-14", "Peruana",
            "2020-03-01", "MATEMATICA", "999888777", "patricia.morales@ejemplo.com", "Lima");
    /**
     * Un docente necesita documento, nombres completos y sexo para identificarlo; nacionalidad,
     * teléfono, correo y dirección son opcionales.
     */
    private static final List<String> COLUMNAS_OBLIGATORIAS = COLUMNAS.stream()
            .filter(columna -> !List.of("nacionalidad", "telefono", "correo", "direccion")
                    .contains(columna))
            .toList();

    private final PersonaService personaService;
    private final Validator validator;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transaccion;
    private final AccesoImportacionService accesos;

    public ImportacionDocenteService(
            PersonaService personaService,
            Validator validator,
            AuditoriaService auditoria,
            PlatformTransactionManager transactionManager,
            AccesoImportacionService accesos) {
        this.personaService = personaService;
        this.validator = validator;
        this.auditoria = auditoria;
        this.transaccion = new TransactionTemplate(transactionManager);
        this.accesos = accesos;
    }

    /**
     * Sin confirmar, procesa el archivo en una transaccion que siempre se deshace: asi tambien se
     * detectan documentos y codigos repetidos dentro del mismo archivo. Al confirmar, cada fila
     * se guarda con su acceso en su propia transaccion y una fila rechazada no afecta a las demas.
     * Las contraseñas temporales solo se devuelven al confirmar.
     */
    public PersonaDtos.ImportResult importar(MultipartFile archivo, boolean confirmar) {
        if (confirmar) {
            return registrarFilas(archivo);
        }
        return transaccion.execute(estado -> {
            estado.setRollbackOnly();
            return registrarFilas(archivo).sinContrasenas();
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
        List<PersonaDtos.CredencialAcceso> credenciales = new java.util.ArrayList<>();
        var resumen = LectorExcel.recorrer(archivo, COLUMNAS_OBLIGATORIAS, fila -> {
            var solicitud = aSolicitud(fila);
            LectorExcel.validar(validator, solicitud);
            credenciales.add(accesos.docente(fila.numero(), solicitud));
        });
        resumen.auditar(auditoria, "IMPORTAR_DOCENTES", "Docente", archivo, credenciales);
        return resumen.resultado(credenciales, List.of());
    }

    private PersonaDtos.DocenteCreateRequest aSolicitud(LectorExcel.Fila fila) {
        var persona = new PersonaDtos.PersonaRequest(
                fila.obligatorio("nombres"),
                fila.obligatorio("apellidoPaterno"),
                fila.obligatorio("apellidoMaterno"),
                fila.enumerado(TipoDocumento.class, "tipoDocumento", List.of(TipoDocumento.values())),
                fila.obligatorio("numeroDocumento"),
                fila.opcional("nacionalidad") == null
                        ? null
                        : personaService.nacionalidadPorNombre(fila.opcional("nacionalidad")),
                fila.enumerado(Sexo.class, "sexo", List.of(Sexo.values())),
                fila.fecha("fechaNacimiento"),
                fila.opcional("telefono"),
                fila.opcional("correo"),
                fila.opcional("direccion"));
        // El código del docente lo genera el sistema; una columna codigoDocente se ignora.
        return new PersonaDtos.DocenteCreateRequest(
                persona,
                fila.fecha("fechaIngreso"),
                fila.enumerado(Especialidad.class, "especialidad", List.of(Especialidad.values())));
    }

    private static List<String> nombres(Enum<?>[] valores) {
        return Arrays.stream(valores).map(Enum::name).toList();
    }
}

package com.asiscontrol.service;

import com.asiscontrol.dto.CargaMasivaDto;
import com.asiscontrol.dto.academico.AcademicoDtos.CrearHorarioRequest;
import com.asiscontrol.entity.enums.DiaSemana;
import com.asiscontrol.repository.HorarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OBS-05 · ASISCTRL-231: carga masiva de bloques de horario. Cada fila pasa por
 * {@link HorarioService#crear}: jornada de 07:00 a 20:00 en pasos de 30 minutos, aula con
 * capacidad para la sección y sin cruces de aula, docente ni sección.
 */
@Service
public class ImportacionHorarioService {

    private static final List<String> COLUMNAS = List.of(
            "anioAcademico", "grado", "seccion", "codigoCurso", "codigoAula",
            "diaSemana", "horaInicio", "horaFin");
    private static final List<String> EJEMPLO =
            List.of("2027", "1", "A", "MAT-1", "A-101", "LUNES", "08:00", "09:30");

    private final ImportadorExcel importador;
    private final HorarioService horarios;
    private final HorarioRepository repositorio;
    private final ReferenciasImportacion referencias;

    public ImportacionHorarioService(
            ImportadorExcel importador,
            HorarioService horarios,
            HorarioRepository repositorio,
            ReferenciasImportacion referencias) {
        this.importador = importador;
        this.horarios = horarios;
        this.repositorio = repositorio;
        this.referencias = referencias;
    }

    public CargaMasivaDto.Resultado importar(MultipartFile archivo, boolean confirmar) {
        return importador.importar(archivo, confirmar, COLUMNAS, fila -> {
            var seccion = referencias.seccion(fila);
            var curso = referencias.curso(fila.obligatorio("codigoCurso").toUpperCase(Locale.ROOT));
            var aula = referencias.aula(fila.obligatorio("codigoAula"));
            var dia = fila.enumerado(DiaSemana.class, "diaSemana", List.of(DiaSemana.values()));
            var inicio = fila.hora("horaInicio");
            var fin = fila.hora("horaFin");
            var asignacion = referencias.asignacion(curso, seccion);
            boolean existe = repositorio.findAllByAsignacionCursoIdAndActivoTrue(asignacion.getId()).stream()
                    .anyMatch(h -> h.getDiaSemana() == dia && h.getHoraInicio().equals(inicio));
            return new ImportadorExcel.Paso(
                    asignacion.getId() + "|" + dia + "|" + inicio,
                    "horaInicio",
                    existe,
                    () -> horarios.crear(new CrearHorarioRequest(
                            asignacion.getId(), aula.getId(), dia, inicio, fin)));
        });
    }

    public byte[] plantilla() {
        return PlantillaExcel.build("Horarios", COLUMNAS, EJEMPLO, Map.of(
                "diaSemana", Arrays.stream(DiaSemana.values()).map(Enum::name).toList()));
    }
}

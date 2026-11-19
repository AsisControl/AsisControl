package com.asiscontrol.service;

import com.asiscontrol.entity.ContadorCodigoInstitucional;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.repository.AdministradorRepository;
import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.ApoderadoRepository;
import com.asiscontrol.repository.ContadorCodigoInstitucionalRepository;
import com.asiscontrol.repository.DocenteRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Códigos institucionales de los perfiles: {PREFIJO}{AÑO}{####}, por ejemplo EST20260001. La
 * secuencia es propia de cada prefijo y se reinicia cada año (zona America/Lima). El código no
 * cambia después de asignarse.
 *
 * <p>El contador se bloquea dentro de la transacción del alta, así dos altas simultáneas nunca
 * reciben el mismo número, y un alta que se deshace no deja consumido su número.
 */
@Service
public class CodigoInstitucionalService {

    static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final int MAXIMO_POR_ANIO = 9999;

    public enum Prefijo {
        EST,
        DOC,
        APO,
        ADM
    }

    private final ContadorCodigoInstitucionalRepository contadores;
    private final AlumnoRepository alumnos;
    private final DocenteRepository docentes;
    private final ApoderadoRepository apoderados;
    private final AdministradorRepository administradores;
    private final Clock reloj;

    public CodigoInstitucionalService(
            ContadorCodigoInstitucionalRepository contadores,
            AlumnoRepository alumnos,
            DocenteRepository docentes,
            ApoderadoRepository apoderados,
            AdministradorRepository administradores) {
        this.contadores = contadores;
        this.alumnos = alumnos;
        this.docentes = docentes;
        this.apoderados = apoderados;
        this.administradores = administradores;
        this.reloj = Clock.system(ZONA);
    }

    /** Siguiente código del año en curso. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String siguiente(Prefijo prefijo) {
        return siguiente(prefijo, LocalDate.now(reloj).getYear());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String siguiente(Prefijo prefijo, int anio) {
        contadores.inicializar(prefijo.name(), anio);
        ContadorCodigoInstitucional contador = contadores.bloquear(prefijo.name(), anio);
        Predicate<String> enUso = enUso(prefijo);
        String codigo;
        do {
            if (contador.getUltimoValor() >= MAXIMO_POR_ANIO) {
                throw new BusinessRuleException(
                        "CODIGOS_AGOTADOS",
                        "Se agotaron los códigos " + prefijo + " del año " + anio);
            }
            contador.setUltimoValor(contador.getUltimoValor() + 1);
            codigo = formatear(prefijo, anio, contador.getUltimoValor());
            // Un código igual registrado antes (por ejemplo, a mano) se salta en vez de repetirse.
        } while (enUso.test(codigo));
        contadores.save(contador);
        return codigo;
    }

    static String formatear(Prefijo prefijo, int anio, long numero) {
        return String.format(Locale.ROOT, "%s%04d%04d", prefijo.name(), anio, numero);
    }

    private Predicate<String> enUso(Prefijo prefijo) {
        return switch (prefijo) {
            case EST -> codigo -> alumnos.findByCodigoAlumnoIgnoreCase(codigo).isPresent();
            case DOC -> codigo -> docentes.findByCodigoDocenteIgnoreCase(codigo).isPresent();
            case APO -> apoderados::existsByCodigoApoderadoIgnoreCase;
            case ADM -> administradores::existsByCodigoAdministradorIgnoreCase;
        };
    }
}

package com.asiscontrol.service;

import com.asiscontrol.dto.asistencia.ReporteAsistenciaDtos.*;
import com.asiscontrol.entity.Seccion;
import com.asiscontrol.entity.planificacion.PeriodoAcademico;
import com.asiscontrol.exception.BusinessRuleException;
import com.asiscontrol.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReporteAsistenciaService {
    private final EntityManager em;
    private final Clock reloj;
    public ReporteAsistenciaService(EntityManager em, Clock reloj) { this.em = em; this.reloj = reloj; }

    public Filtros filtros() {
        var periodos = em.createQuery("from PeriodoAcademico p where p.activo = true order by p.fechaInicio desc", PeriodoAcademico.class)
                .getResultList().stream().map(p -> new Opcion(p.getId(), p.getAnioAcademico().getAnio() + " · " + p.getNombre())).toList();
        var secciones = em.createQuery("from Seccion s where s.activo = true order by s.anioAcademico desc, s.grado, s.nombre", Seccion.class)
                .getResultList().stream().map(s -> new Opcion(s.getId(), s.getAnioAcademico() + " · " + s.getGrado() + "° " + s.getNombre())).toList();
        return new Filtros(periodos, secciones);
    }

    public Informe consultar(Tipo tipo, Long periodoId, Long seccionId, Long personaId, String busqueda,
                              LocalDate desde, LocalDate hasta, Estado estado, int pagina, int tamano) {
        if (pagina < 0 || tamano < 1 || tamano > 100 || (long) pagina * tamano > Integer.MAX_VALUE)
            throw new BusinessRuleException("PAGINACION_INVALIDA", "La página debe ser válida y el tamaño entre 1 y 100");
        if (desde != null && hasta != null && desde.isAfter(hasta))
            throw new BusinessRuleException("RANGO_FECHAS_INVALIDO", "La fecha inicial no puede superar la final");
        if (busqueda != null && busqueda.length() > 100)
            throw new BusinessRuleException("BUSQUEDA_INVALIDA", "La búsqueda admite hasta 100 caracteres");
        Map<String, Object> params = new HashMap<>();
        String where = " where 1 = 1";
        if (periodoId != null) {
            var periodo = em.find(PeriodoAcademico.class, periodoId);
            if (periodo == null || !periodo.isActivo()) throw new ResourceNotFoundException("PERIODO_NO_ENCONTRADO", "No existe el período seleccionado");
            where += " and r.fecha between :periodoInicio and :periodoFin";
            params.put("periodoInicio", periodo.getFechaInicio()); params.put("periodoFin", periodo.getFechaFin());
        }
        if (seccionId != null) {
            if (em.find(Seccion.class, seccionId) == null) throw new ResourceNotFoundException("SECCION_NO_ENCONTRADA", "No existe la sección seleccionada");
            params.put("seccion", seccionId);
            where += tipo == Tipo.ESTUDIANTES ? " and r.seccion_id = :seccion" : """
                    and exists (select 1 from SesionClase sx join AsignacionCurso ax on ax.id_asignacion_curso = sx.id_asignacion_curso
                      where sx.activo = true and sx.fecha = r.fecha and ax.id_docente = r.docente_id and ax.id_seccion = :seccion)
                    """;
        }
        if (personaId != null) { where += " and r.persona_id = :persona"; params.put("persona", personaId); }
        if (desde != null) { where += " and r.fecha >= :desde"; params.put("desde", desde); }
        if (hasta != null) { where += " and r.fecha <= :hasta"; params.put("hasta", hasta); }
        if (busqueda != null && !busqueda.isBlank()) {
            where += " and lower(r.persona) like :busqueda escape '!'";
            params.put("busqueda", "%" + busqueda.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        String base = " from (" + (tipo == Tipo.ESTUDIANTES ? ESTUDIANTES : DOCENTES) + ") r" + where;
        if (tipo == Tipo.DOCENTES) params.put("ahora", LocalDateTime.now(reloj));
        Map<Estado, Long> cantidades = new EnumMap<>(Estado.class);
        for (Object obj : query("select r.categoria, count(*)" + base + " group by r.categoria", params).getResultList()) {
            Object[] row = (Object[]) obj;
            cantidades.put(Estado.valueOf(row[0].toString()), ((Number) row[1]).longValue());
        }
        long total = cantidades.values().stream().mapToLong(Long::longValue).sum();
        List<Indicador> indicadores = total == 0 ? List.of() : Arrays.stream(Estado.values()).map(e -> {
            long n = cantidades.getOrDefault(e, 0L);
            return new Indicador(e, n, BigDecimal.valueOf(n).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP));
        }).toList();
        long detalle = estado == null ? total : cantidades.getOrDefault(estado, 0L);
        if (estado != null) { base += " and r.categoria = :estado"; params.put("estado", estado.name()); }
        List<?> filas = query("select r.id, r.persona_id, r.persona, r.fecha, r.hora, r.curso, r.seccion, r.estado, r.origen, r.sesion_id" + base
                + " order by r.fecha desc, r.persona, r.origen, r.id desc", params)
                .setFirstResult(pagina * tamano).setMaxResults(tamano).getResultList();
        List<Registro> registros = filas.stream().map(obj -> registro((Object[]) obj)).toList();
        return new Informe(total, indicadores, registros, pagina, (int) ((detalle + tamano - 1) / tamano), detalle);
    }

    private Query query(String sql, Map<String, Object> params) {
        Query query = em.createNativeQuery(sql); params.forEach(query::setParameter); return query;
    }
    private Registro registro(Object[] r) {
        LocalDate fecha = r[3] instanceof LocalDate d ? d : ((java.sql.Date) r[3]).toLocalDate();
        LocalTime hora = r[4] == null ? null : r[4] instanceof LocalTime t ? t : ((java.sql.Time) r[4]).toLocalTime();
        return new Registro(((Number) r[0]).longValue(), ((Number) r[1]).longValue(), r[2].toString(), fecha, hora,
                (String) r[5], (String) r[6], r[7].toString(), r[8].toString(), r[9] == null ? null : ((Number) r[9]).longValue());
    }

    private static final String NOMBRE = "concat(p.nombres, ' ', p.apellido_paterno, ' ', coalesce(p.apellido_materno, ''))";
    private static final String ESTUDIANTES = """
            select a.id_asistencia_alumno id, p.id_persona persona_id, %s persona,
              a.fecha_registro fecha, a.hora_registro hora, coalesce(plan.nombre_plan, c.nombre) curso,
              concat(s.grado, '° ', s.nombre) seccion, s.id_seccion seccion_id, ac.id_docente docente_id,
              a.estado_asistencia estado,
              case a.estado_asistencia when 'TARDANZA_JUSTIFICADA' then 'TARDANZA'
                when 'INASISTENCIA_JUSTIFICADA' then 'INASISTENCIA' else a.estado_asistencia end categoria,
              'REGISTRO' origen, sc.id_sesion_clase sesion_id
            from AsistenciaAlumno a join Alumno al on al.id_alumno = a.id_alumno
            join Persona p on p.id_persona = al.id_persona
            join SesionClase sc on sc.id_sesion_clase = a.id_sesion_clase
            join AsignacionCurso ac on ac.id_asignacion_curso = a.id_asignacion_curso
            join Curso c on c.id_curso = ac.id_curso join Seccion s on s.id_seccion = ac.id_seccion
            left join AnioAcademico anio on anio.anio = s.anio_academico
            left join GradoAcademico grado on grado.id_anio = anio.id_anio_academico and grado.numero = s.grado
            left join PlanEstudiosCurso plan on plan.id_grado_academico = grado.id_grado_academico and plan.id_curso = ac.id_curso
            where a.activo = true and sc.estado = 'TOMADA' and sc.activo = true
            """.formatted(NOMBRE);
    private static final String DOCENTES = """
            select a.id_asistencia_docente id, p.id_persona persona_id, %s persona,
              a.fecha_jornada fecha, cast(a.fecha_hora_marcada as time) hora,
              cast(null as char) curso, cast(null as char) seccion, 0 seccion_id, d.id_docente docente_id,
              a.estado_asistencia estado,
              case a.estado_asistencia when 'TARDANZA_JUSTIFICADA' then 'TARDANZA'
                when 'INASISTENCIA_JUSTIFICADA' then 'INASISTENCIA' else a.estado_asistencia end categoria,
              'REGISTRO' origen, a.id_sesion_clase sesion_id
            from AsistenciaDocente a join Docente d on d.id_docente = a.id_docente
            join Persona p on p.id_persona = d.id_persona where a.activo = true
            union all
            select j.id_jornada_marcacion, p.id_persona, %s, j.fecha, cast(null as time),
              cast(null as char), cast(null as char), 0, d.id_docente,
              'SIN_MARCACION', 'SIN_MARCACION', 'JORNADA', null
            from JornadaMarcacionDocente j join Docente d on d.id_docente = j.id_docente
            join Persona p on p.id_persona = d.id_persona
            where j.activo = true and j.cierre <= :ahora and not exists
              (select 1 from AsistenciaDocente a where a.id_docente = j.id_docente and a.fecha_jornada = j.fecha and a.activo = true)
            """.formatted(NOMBRE, NOMBRE);
}

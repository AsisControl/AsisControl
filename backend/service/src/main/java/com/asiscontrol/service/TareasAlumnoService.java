package com.asiscontrol.service;

import com.asiscontrol.dto.tarea.TareaDtos.TareaAlumnoResponse;
import com.asiscontrol.entity.EntregaTarea;
import com.asiscontrol.exception.ResourceNotFoundException;
import com.asiscontrol.repository.AlumnoRepository;
import com.asiscontrol.repository.EntregaTareaRepository;
import com.asiscontrol.repository.TareaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class TareasAlumnoService {
    private final AlumnoRepository alumnos;
    private final TareaRepository tareas;
    private final EntregaTareaRepository entregas;
    private final TareaService tareaService;
    private final EntregaTareaService entregaService;
    private final ModuloAccessGuard guard;

    public TareasAlumnoService(AlumnoRepository alumnos, TareaRepository tareas,
            EntregaTareaRepository entregas, TareaService tareaService,
            EntregaTareaService entregaService, ModuloAccessGuard guard) {
        this.alumnos = alumnos;
        this.tareas = tareas;
        this.entregas = entregas;
        this.tareaService = tareaService;
        this.entregaService = entregaService;
        this.guard = guard;
    }

    public Page<TareaAlumnoResponse> listar(String busqueda, Boolean entregada, Pageable pageable) {
        // La identidad viene de la sesión, nunca de un parámetro del navegador.
        Long personaId = guard.restriccionAlumnoPersonaId();
        if (personaId == null) throw new AccessDeniedException("Se requiere una sesión de estudiante");
        var alumno = alumnos.findByPersonaIdAndActivoTrue(personaId)
                .filter(a -> a.getPersona().isActivo())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ALUMNO_NO_ENCONTRADO", "No existe un perfil de estudiante activo para esta cuenta"));
        var pagina = tareas.buscarParaAlumno(alumno.getId(), busqueda.trim(), entregada,
                java.time.LocalDateTime.now(), pageable);
        var porTarea = entregas.findAllByAlumnoIdAndTareaIdInAndActivoTrue(alumno.getId(),
                        pagina.getContent().stream().map(t -> t.getId()).toList())
                .stream().collect(Collectors.toMap(e -> e.getTarea().getId(), e -> e));
        return pagina.map(t -> {
            var asignacion = t.getAsignacionCurso();
            var persona = asignacion.getDocente().getPersona();
            var seccion = asignacion.getSeccion();
            EntregaTarea entrega = porTarea.get(t.getId());
            String nombre = java.util.stream.Stream.of(persona.getNombres(), persona.getApellidoPaterno(),
                    persona.getApellidoMaterno()).filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(" "));
            return new TareaAlumnoResponse(tareaService.mapResponse(t), alumno.getId(),
                    asignacion.getCurso().getNombre(), nombre,
                    seccion.getGrado() + "° Grado " + seccion.getNombre() + " · " + seccion.getAnioAcademico(),
                    entrega == null ? null : entregaService.mapResponse(entrega));
        });
    }
}

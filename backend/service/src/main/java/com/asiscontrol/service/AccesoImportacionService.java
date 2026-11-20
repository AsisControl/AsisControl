package com.asiscontrol.service;

import com.asiscontrol.dto.persona.PersonaDtos;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Altas de la carga masiva con su acceso al sistema. Cada método registra el perfil y su cuenta
 * en la misma transacción: si no se puede crear la cuenta, tampoco queda la persona. Una persona
 * que ya tiene acceso conserva esa cuenta y su contraseña.
 */
@Service
public class AccesoImportacionService {

    private final PersonaService personas;
    public AccesoImportacionService(PersonaService personas) {
        this.personas = personas;
    }

    @Transactional
    public PersonaDtos.CredencialAcceso alumno(int fila, PersonaDtos.AlumnoCreateRequest request) {
        return personas.createAlumno(request).credencialAcceso().enFila(fila);
    }

    @Transactional
    public PersonaDtos.CredencialAcceso docente(int fila, PersonaDtos.DocenteCreateRequest request) {
        return personas.createDocente(request).credencialAcceso().enFila(fila);
    }

    @Transactional
    public PersonaDtos.CredencialAcceso apoderado(
            int fila, PersonaDtos.ApoderadoCreateRequest request) {
        return personas.registrarOReutilizarApoderado(request).credencialAcceso().enFila(fila);
    }
}

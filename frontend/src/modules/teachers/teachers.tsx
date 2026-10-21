"use client";
/**
 * US-37: perfil institucional de docentes (FE 4 + BE 4).
 * Endpoints disponibles en el backend: GET/POST /api/docentes y
 * GET/PUT/DELETE /api/docentes/{id}. Ver tipos en ./types.ts.
 * Tomar como referencia el módulo de estudiantes (listado, ficha y edición).
 */
export function Teachers() {
  return (
    <section>
      <div className="section-heading">
        <h3>Docentes</h3>
      </div>
      <p className="empty">
        La gestión de perfiles docentes está pendiente de implementación.
      </p>
    </section>
  );
}

"use client";
import { Feedback, Pager } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { roleLabel } from "@/shared/lib/formatters";
import type { Audit } from "./types";
export function AuditLog() {
  const list = usePaged<Audit>("/api/auditoria/registros");
  return (
    <>
      <Feedback error={list.error} />
      {list.loading ? (
        <p>Cargando auditoría…</p>
      ) : list.data?.content.length ? (
        <div className="audit-list">
          {list.data.content.map((a) => (
            <article key={a.id}>
              <div>
                <strong>{a.accion.replaceAll("_", " ")}</strong>
                <p>
                  {a.actorIdentificador || "Sistema"} · {roleLabel(a.rolActor)}{" "}
                  · {a.entidad} #{a.registroId}
                </p>
                {a.motivo && <p>Motivo: {a.motivo}</p>}
              </div>
              <div>
                <span>
                  {new Date(a.fechaHora).toLocaleString("es-PE", {
                    timeZone: "America/Lima",
                  })}
                </span>
                <small>{a.resultado}</small>
              </div>
            </article>
          ))}
        </div>
      ) : (
        !list.error && (
          <p className="empty">Todavía no hay operaciones registradas.</p>
        )
      )}
      <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
    </>
  );
}

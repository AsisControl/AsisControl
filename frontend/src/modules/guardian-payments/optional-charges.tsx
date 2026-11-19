"use client";
import { useEffect, useState } from "react";
import { api, ApiError, message } from "@/core/api/client";
import { usePaged } from "@/shared/hooks/use-paged";
import { Dialog, Feedback, Pager, PanelHeading, StatusPill } from "@/shared/ui";
import { dueDateLabel, moneyLabel } from "./payment-format";
import styles from "./guardian-payments.module.css";

export type OptionalCharge = {
  id: number; codigo: string; idMatricula: number; idAlumno: number; alumno: string;
  anioAcademico: number; idConcepto: number; concepto: string; referencia: string;
  titulo: string; descripcion: string | null; montoBase: number; montoDescuento: number;
  monto: number; beneficioNombre: string | null; fechaVencimiento: string;
  fechaLimiteAceptacion: string; estado: "PROPUESTA" | "ACEPTADA" | "RECHAZADA" | "CANCELADA" | "VENCIDA";
  idCompromiso: number | null; codigoCompromiso: string | null; fechaCreacion: string;
  respondidaEn: string | null; version: number; firmaCotizacion: string;
};
const PATH = "/api/apoderado/ofertas";
const labels: Record<OptionalCharge["estado"], string> = {
  PROPUESTA: "Disponible", ACEPTADA: "Aceptado", RECHAZADA: "No aceptado",
  CANCELADA: "Cancelado", VENCIDA: "Plazo finalizado",
};

export function OptionalCharges({ onChanged, onObligation }: {
  onChanged: () => void;
  onObligation: (id: number) => void;
}) {
  const list = usePaged<OptionalCharge>(PATH, "", "", 10);
  const [selected, setSelected] = useState<number | null>(null);
  function changed() { list.refresh(); onChanged(); }
  return <section className="panel">
    <PanelHeading title="Actividades y servicios opcionales"
      summary="Revisa las opciones para tus estudiantes. Aceptar registra la obligación; el pago se realiza después."
      actions={<button type="button" className="secondary-action" disabled={list.loading} onClick={list.refresh}>Actualizar opciones</button>} />
    <Feedback error={list.error} />
    {list.loading ? <p role="status">Consultando actividades y servicios…</p>
      : !list.error && !list.data?.content.length ? <p className="empty">No tienes actividades ni servicios opcionales registrados.</p>
      : list.data && <div className={styles.tableWrap}><table className={`user-table ${styles.historyTable}`} aria-label="Opciones de mis estudiantes">
        <thead><tr><th>Estudiante</th><th>Actividad o servicio</th><th>Monto</th><th>Estado</th><th>Acciones</th></tr></thead>
        <tbody>{list.data.content.map((item) => <tr key={item.id}>
          <td>{item.alumno}</td><td><strong>{item.titulo}</strong><small>{item.concepto} · {item.referencia}</small></td>
          <td>{item.monto === 0 ? "Exonerado" : moneyLabel(item.monto)}</td>
          <td><StatusPill state={item.estado} label={labels[item.estado]} tone={item.estado === "ACEPTADA" ? "success" : item.estado === "PROPUESTA" ? "info" : "neutral"} /></td>
          <td><button type="button" className="secondary-action" aria-label={`Revisar ${item.titulo} de ${item.alumno}`} onClick={() => setSelected(item.id)}>Ver detalle</button></td>
        </tr>)}</tbody>
      </table></div>}
    <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
    {selected != null && <OptionalChargeDetail key={selected} id={selected} onClose={() => setSelected(null)} onChanged={changed}
      onObligation={(id) => { setSelected(null); onObligation(id); }} />}
  </section>;
}

function OptionalChargeDetail({ id, onClose, onChanged, onObligation }: {
  id: number; onClose: () => void; onChanged: () => void; onObligation: (id: number) => void;
}) {
  const [item, setItem] = useState<OptionalCharge | null>(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [revision, setRevision] = useState(0);
  const [accepted, setAccepted] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    api<OptionalCharge>(`${PATH}/${id}`).then((value) => { if (active) setItem(value); })
      .catch((caught) => { if (active) setError(message(caught)); });
    return () => { active = false; };
  }, [id, revision]);
  async function respond(action: "aceptar" | "rechazar") {
    if (!item || busy || (action === "aceptar" && !accepted)) return;
    setBusy(true); setError(""); setSuccess("");
    try {
      const next = await api<OptionalCharge>(`${PATH}/${id}/${action}`, {
        method: "POST", body: JSON.stringify({ version: item.version, firmaCotizacion: item.firmaCotizacion }),
      });
      setItem(next); setAccepted(false); onChanged();
      setSuccess(action === "aceptar" ? next.monto === 0
        ? "La opción quedó aceptada y exonerada. No necesitas realizar un pago."
        : "La opción quedó aceptada. Puedes realizar su pago desde Obligaciones."
        : "Registramos que no deseas aceptar esta opción.");
    } catch (caught) {
      setError(message(caught));
      if (caught instanceof ApiError && ["OFERTA_ACTUALIZADA", "CONFLICTO_VERSION"].includes(caught.code)) {
        setItem(null); setAccepted(false); setRevision((value) => value + 1);
      }
    } finally { setBusy(false); }
  }
  return <Dialog title="Detalle de la actividad o servicio" description={item ? `${item.titulo} · ${item.alumno}` : "Consulta las condiciones antes de decidir."}
    size="lg" busy={busy} onClose={onClose}>
    <Feedback error={error} success={success} />
    {!item && !error && <p role="status">Consultando el detalle…</p>}
    {!item && error && <button type="button" className="secondary-action" onClick={() => { setError(""); setRevision((value) => value + 1); }}>Reintentar consulta</button>}
    {item && <>
      <dl className={styles.summary}>
        <div><dt>Estudiante</dt><dd>{item.alumno}</dd></div><div><dt>Concepto</dt><dd>{item.concepto}</dd></div>
        <div><dt>Referencia</dt><dd>{item.referencia}</dd></div><div><dt>Estado</dt><dd>{labels[item.estado]}</dd></div>
        {item.montoDescuento > 0 && <><div><dt>Importe regular</dt><dd>{moneyLabel(item.montoBase)}</dd></div><div><dt>Beneficio aplicado</dt><dd>{item.beneficioNombre}<small>−{moneyLabel(item.montoDescuento)}</small></dd></div></>}
        <div><dt>Monto a pagar</dt><dd>{item.monto === 0 ? "Exonerado" : moneyLabel(item.monto)}</dd></div>
        <div><dt>Vencimiento del pago</dt><dd>{dueDateLabel(item.fechaVencimiento)}</dd></div>
        <div><dt>Plazo para aceptar</dt><dd>{dueDateLabel(item.fechaLimiteAceptacion)}</dd></div>
      </dl>
      {item.descripcion && <p>{item.descripcion}</p>}
      {item.estado === "PROPUESTA" && <>
        <label className={styles.acceptance}><input type="checkbox" checked={accepted} disabled={busy} onChange={(event) => setAccepted(event.target.checked)} />
          He revisado el importe y las condiciones y deseo aceptar esta opción para mi estudiante.
        </label>
        <p className={styles.hint}>Al aceptar se registrará una obligación individual. Podrás elegir el medio de pago después.</p>
        <div className={styles.actions}>
          <button type="button" className="primary-action" disabled={busy || !accepted || !item.firmaCotizacion} onClick={() => respond("aceptar")}>Confirmar aceptación</button>
          <button type="button" className="secondary-action" disabled={busy} onClick={() => respond("rechazar")}>No aceptar</button>
        </div>
      </>}
      {item.estado === "ACEPTADA" && item.idCompromiso != null && <button type="button" className="secondary-action" onClick={() => onObligation(item.idCompromiso!)}>Ver obligación</button>}
    </>}
    <div className={styles.actions}><button type="button" className={`secondary-action ${styles.closeButton}`} disabled={busy} onClick={onClose}>Cerrar</button></div>
  </Dialog>;
}

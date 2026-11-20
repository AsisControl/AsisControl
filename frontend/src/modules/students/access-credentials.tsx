"use client";
import { countLabel, roleLabel, shortDateLabel } from "@/shared/lib/formatters";
import { Dialog } from "@/shared/ui";
import type { AccessCredential } from "./types";
import styles from "./import-dialog.module.css";

const escapeHtml = (value: string) =>
  value.replace(
    /[&<>"']/g,
    (char) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        char
      ] ?? char,
  );

/**
 * Hoja imprimible con las credenciales iniciales. Se arma en una ventana aparte para que el
 * navegador la imprima o la guarde como PDF; no se envía ni se guarda en ningún servidor.
 */
export function printCredentials(credentials: AccessCredential[]) {
  const rows = credentials
    .filter((c) => c.temporaryPassword)
    .map(
      (c) => `<tr><td>${escapeHtml(c.fullName)}</td><td>${escapeHtml(roleLabel(c.role))}</td>
<td>${escapeHtml(c.code ?? "—")}</td><td><code>${escapeHtml(c.accessCode ?? "—")}</code></td>
<td><code>${escapeHtml(c.username)}</code></td>
<td><code>${escapeHtml(c.temporaryPassword ?? "")}</code></td>
<td>${escapeHtml(shortDateLabel(c.expiresAt))}</td></tr>`,
    )
    .join("");
  const sheet = window.open("", "_blank");
  if (!sheet) return false;
  sheet.document.write(`<!doctype html><html lang="es"><head><meta charset="utf-8">
<title>Credenciales iniciales · AsisControl</title>
<style>body{font-family:Inter,Arial,sans-serif;margin:24px;color:#111}
h1{font-size:18px}p{font-size:12px;color:#444}table{border-collapse:collapse;width:100%;font-size:12px}
th,td{border:1px solid #bbb;padding:6px 8px;text-align:left}th{background:#f1f5f9}
code{font-family:Consolas,monospace;font-size:13px}</style></head><body>
<h1>Credenciales iniciales de acceso</h1>
<p>Entrega a cada titular su usuario y contraseña temporal. Deberá cambiar la contraseña en su
primer ingreso, antes de la fecha de vencimiento. Guarda esta hoja en un lugar seguro.</p>
<table><thead><tr><th>Nombre</th><th>Rol</th><th>Código</th><th>Código de acceso</th><th>Usuario</th>
<th>Contraseña temporal</th><th>Vence</th></tr></thead><tbody>${rows}</tbody></table>
</body></html>`);
  sheet.document.close();
  sheet.focus();
  sheet.print();
  return true;
}

export function NewAccountCredentials({
  credential,
  onClose,
}: {
  credential: AccessCredential;
  onClose: () => void;
}) {
  return (
    <Dialog title="Credenciales iniciales" onClose={onClose}>
      <p>Entrega estos datos únicamente a {credential.fullName}.</p>
      <dl className="detail-grid">
        <div><dt>Código de acceso</dt><dd>{credential.accessCode ?? "—"}</dd></div>
        <div><dt>Usuario</dt><dd>{credential.username}</dd></div>
        <div><dt>Correo</dt><dd>{credential.email}</dd></div>
        <div><dt>Contraseña temporal</dt><dd><code>{credential.temporaryPassword}</code></dd></div>
        <div><dt>Vencimiento</dt><dd>{shortDateLabel(credential.expiresAt)}</dd></div>
      </dl>
      <p>La contraseña solo se muestra ahora y debe cambiarse en el primer ingreso.</p>
      <div className="form-actions">
        <button type="button" onClick={() => printCredentials([credential])}>
          Imprimir o guardar PDF
        </button>
        <button className="primary-action" type="button" onClick={onClose}>Entendido</button>
      </div>
    </Dialog>
  );
}

/**
 * Accesos de una importación. Antes de guardar muestra el usuario que recibirá cada persona;
 * después de guardar muestra una sola vez las contraseñas temporales para imprimirlas.
 */
export function AccessCredentials({
  credentials,
  saved,
}: {
  credentials: AccessCredential[];
  saved: boolean;
}) {
  if (!credentials.length) return null;
  const created = credentials.filter((c) => !c.existing).length;
  const existing = credentials.length - created;
  const printable = saved && credentials.some((c) => c.temporaryPassword);
  return (
    <section
      className={styles.credentials}
      aria-labelledby="access-credentials-title"
    >
      <h4 id="access-credentials-title" className={styles.sectionTitle}>
        {saved ? "Credenciales iniciales" : "Accesos al sistema"}
      </h4>
      <p className={styles.slotText}>
        {saved
          ? `Se ${created === 1 ? "creó" : "crearon"} ${countLabel(created, "acceso", "accesos")}`
          : `Se ${created === 1 ? "creará" : "crearán"} ${countLabel(created, "acceso", "accesos")}`}
        {existing > 0 &&
          ` y se ${saved ? "conservaron" : "conservarán"} ${countLabel(existing, "acceso existente", "accesos existentes")}`}
        . Cada cuenta conserva su código de acceso y su usuario al agregarle otro rol.
      </p>
      {printable && (
        <div className={styles.credentialsWarning} role="note">
          <strong>Las contraseñas temporales solo se muestran ahora.</strong>{" "}
          Imprímelas o guárdalas como PDF antes de cerrar y entrega a cada
          titular únicamente la suya. Si se pierden, restablécelas desde Usuarios.
          <div>
            <button
              type="button"
              className="primary-action"
              onClick={() => printCredentials(credentials)}
            >
              Imprimir o guardar PDF
            </button>
          </div>
        </div>
      )}
      <div className="user-table-wrap">
        <table className="user-table" aria-label="Accesos de la importación">
          <thead>
            <tr>
              <th scope="col">Fila</th>
              <th scope="col">Nombre</th>
              <th scope="col">Rol</th>
              <th scope="col">Código</th>
              <th scope="col">Código de acceso</th>
              <th scope="col">Usuario</th>
              <th scope="col">{saved ? "Contraseña temporal" : "Acceso"}</th>
            </tr>
          </thead>
          <tbody>
            {credentials.map((c) => (
              <tr key={`${c.role}-${c.row}-${c.accessCode ?? c.username}`}>
                <td data-label="Fila">{c.row}</td>
                <td data-label="Nombre">{c.fullName}</td>
                <td data-label="Rol">{roleLabel(c.role)}</td>
                <td data-label="Código">{c.code ?? "—"}</td>
                <td data-label="Código de acceso">{c.accessCode ?? "—"}</td>
                <td data-label="Usuario">{c.username}</td>
                <td data-label={saved ? "Contraseña temporal" : "Acceso"}>
                  {c.existing ? (
                    "Ya tenía acceso"
                  ) : saved && c.temporaryPassword ? (
                    <code>{c.temporaryPassword}</code>
                  ) : (
                    "Nuevo"
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

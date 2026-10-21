/* eslint-disable @next/next/no-img-element -- SVG assets are served from public/. */
export function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <div className="brand">
      <span className="brand-icon">
        <img src="/school-logo.svg" alt="" />
      </span>
      <span>
        <b>AsisControl</b>
        <small>
          {compact ? "Sistema de Gestión" : "Sistema de Gestión Académica"}
        </small>
      </span>
    </div>
  );
}

/* eslint-disable @next/next/no-img-element -- SVG assets are served from public/. */
import Image from "next/image";
import type { ReactNode } from "react";

export function AsisControlLogo({ inverse = false }: { inverse?: boolean }) {
  const filename = inverse ? "asiscontrol-invertido.svg" : "asiscontrol-principal.svg";
  return <Image src={`/brand/${filename}`} alt="AsisControl" width={208} height={53} unoptimized loading="eager" />;
}

export function Brand({ compact = false, name = "Colegio PUCP", subtitle }: {
  compact?: boolean;
  name?: string;
  subtitle?: ReactNode;
}) {
  return (
    <div className="brand">
      <span className="brand-icon">
        <img src="/school-logo.svg" alt="" />
      </span>
      <span>
        <b>{name}</b>
        <small>
          {subtitle ?? (compact ? "Sistema de Gestión" : "Sistema de Gestión Académica")}
        </small>
      </span>
    </div>
  );
}

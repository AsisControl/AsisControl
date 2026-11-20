import Image from "next/image";
import styles from "./access.module.css";

export function AccessBrand({ className }: { className?: string }) {
  return (
    <header className={`${styles.brand} ${className ?? ""}`}>
      <Image src="/brand/asiscontrol-principal.svg" alt="AsisControl" width={208} height={53} unoptimized loading="eager" />
      <p>Colegio PUCP</p>
    </header>
  );
}

export function AccessArrow() {
  return (
    <svg viewBox="0 0 24 24" width={18} height={18} fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M4 12h15m-6-6 6 6-6 6" />
    </svg>
  );
}

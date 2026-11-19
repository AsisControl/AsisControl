"use client";
import { useState } from "react";
import { ConceptsPanel } from "./concepts-panel";
import { RatesPanel } from "./rates-panel";
import { BenefitsPanel } from "./benefits-panel";
import styles from "./payment-settings.module.css";

export function PaymentSettings({ canWrite }: { canWrite: boolean }) {
  const [section, setSection] = useState("conceptos");
  return <div className={styles.settings}>
    <nav className={styles.tabs} aria-label="Configuración de pagos">{[{ id: "conceptos", label: "Conceptos" }, { id: "tarifas", label: "Tarifas por año y grado" }, { id: "beneficios", label: "Becas y descuentos" }].map((item) => <button type="button" key={item.id} className={section === item.id ? "primary-action" : "secondary-action"} aria-current={section === item.id ? "page" : undefined} onClick={() => setSection(item.id)}>{item.label}</button>)}</nav>
    {section === "conceptos" ? <ConceptsPanel canWrite={canWrite} /> : section === "tarifas" ? <RatesPanel canWrite={canWrite} /> : <BenefitsPanel canWrite={canWrite} />}
  </div>;
}

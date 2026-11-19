"use client";
import { useId, type ReactNode } from "react";
import styles from "./guardian-payments.module.css";

export function PaymentTabs({ label, items, selected, onSelect, disabled = false, children }: {
  label: string;
  items: Array<{ id: string; label: string }>;
  selected: string;
  onSelect: (id: string) => void;
  disabled?: boolean;
  children: ReactNode;
}) {
  const id = useId();
  return <>
    <div className={styles.tabs} role="tablist" aria-label={label}>
      {items.map((item, index) => <button key={item.id} id={`${id}-${item.id}`} type="button" role="tab"
        aria-selected={selected === item.id} aria-controls={`${id}-panel`} tabIndex={selected === item.id ? 0 : -1}
        className={selected === item.id ? "primary-action" : "secondary-action"} disabled={disabled}
        onClick={() => onSelect(item.id)} onKeyDown={(event) => {
          const next = event.key === "ArrowRight" ? (index + 1) % items.length
            : event.key === "ArrowLeft" ? (index + items.length - 1) % items.length
            : event.key === "Home" ? 0 : event.key === "End" ? items.length - 1 : null;
          if (next == null) return;
          event.preventDefault(); onSelect(items[next].id);
          (event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next])?.focus();
        }}>{item.label}</button>)}
    </div>
    <div id={`${id}-panel`} role="tabpanel" aria-labelledby={`${id}-${selected}`}>{children}</div>
  </>;
}

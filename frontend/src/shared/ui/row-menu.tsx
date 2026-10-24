"use client";
import { useEffect, useRef, useState } from "react";
import styles from "./row-menu.module.css";
export type MenuOption = {
  label: string;
  onSelect: () => void;
  danger?: boolean;
};
type MenuPosition = { top: number; right: number };
/** Menú "⋮" de cada fila. Se posiciona fijo para no recortarse con el scroll de la tabla. */
export function RowMenu({
  itemName,
  options,
}: {
  /** Nombre del registro de la fila, para el nombre accesible del botón. */
  itemName: string;
  options: MenuOption[];
}) {
  const [position, setPosition] = useState<MenuPosition | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!position) return;
    const close = () => setPosition(null);
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) close();
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") close();
    };
    document.addEventListener("mousedown", closeOnOutsideClick);
    document.addEventListener("keydown", closeOnEscape);
    window.addEventListener("scroll", close, true);
    window.addEventListener("resize", close);
    return () => {
      document.removeEventListener("mousedown", closeOnOutsideClick);
      document.removeEventListener("keydown", closeOnEscape);
      window.removeEventListener("scroll", close, true);
      window.removeEventListener("resize", close);
    };
  }, [position]);
  function toggle(button: HTMLButtonElement) {
    if (position) {
      setPosition(null);
      return;
    }
    const bounds = button.getBoundingClientRect();
    setPosition({
      top: bounds.bottom + 4,
      right: window.innerWidth - bounds.right,
    });
  }
  function choose(option: MenuOption) {
    setPosition(null);
    option.onSelect();
  }
  return (
    <div ref={containerRef} className={styles.menu}>
      <button
        type="button"
        className={styles.trigger}
        aria-label={`Más acciones para ${itemName}`}
        aria-haspopup="menu"
        aria-expanded={Boolean(position)}
        onClick={(event) => toggle(event.currentTarget)}
      >
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
          <circle cx="12" cy="5" r="1.6" fill="currentColor" />
          <circle cx="12" cy="12" r="1.6" fill="currentColor" />
          <circle cx="12" cy="19" r="1.6" fill="currentColor" />
        </svg>
      </button>
      {position && (
        <div className={styles.menuList} role="menu" style={position}>
          {options.map((option) => (
            <button
              key={option.label}
              type="button"
              role="menuitem"
              className={option.danger ? styles.dangerItem : undefined}
              onClick={() => choose(option)}
            >
              {option.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

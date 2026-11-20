"use client";
import { useEffect, useState } from "react";
import styles from "./new-user.module.css";
const SEARCH_DELAY_MS = 300;
export function CheckIcon({ size }: { size: number }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      stroke="currentColor"
      strokeWidth="3"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M20 6 9 17l-5-5" />
    </svg>
  );
}
/** Buscador sin envío de formulario: Enter no debe crear el usuario. */
export function SearchInput({
  label,
  placeholder,
  value,
  onChange,
}: {
  label: string;
  placeholder: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className={styles.searchBox}>
      <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
        <circle
          cx="11"
          cy="11"
          r="7"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
        />
        <path d="m20 20-3.5-3.5" stroke="currentColor" strokeWidth="2" />
      </svg>
      <input
        type="search"
        aria-label={label}
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter") e.preventDefault();
        }}
      />
    </label>
  );
}
/** Texto del buscador y consulta que se aplica tras una pausa al escribir. */
export function useDebouncedSearch() {
  const [text, setText] = useState("");
  const [query, setQuery] = useState("");
  useEffect(() => {
    const timer = setTimeout(() => setQuery(text.trim()), SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [text]);
  return { text, setText, query };
}

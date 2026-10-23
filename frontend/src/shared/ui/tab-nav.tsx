import styles from "./tab-nav.module.css";
export type TabItem = { id: string; label: string };
/** Pestañas de una pantalla; la pestaña activa la decide quien la usa (normalmente la URL). */
export function TabNav({
  label,
  tabs,
  current,
  onSelect,
}: {
  label: string;
  tabs: TabItem[];
  current: string;
  onSelect: (id: string) => void;
}) {
  return (
    <nav className={styles.tabs} aria-label={label}>
      {tabs.map(({ id, label: text }) => (
        <button
          key={id}
          type="button"
          aria-current={current === id ? "page" : undefined}
          onClick={() => onSelect(id)}
        >
          {text}
        </button>
      ))}
    </nav>
  );
}

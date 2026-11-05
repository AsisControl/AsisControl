"use client";
import {
  useEffect,
  useEffectEvent,
  useId,
  useRef,
  type ReactNode,
  type RefObject,
} from "react";
/** md para formularios y detalles; lg para ventanas con listas o historial. */
export type DialogSize = "md" | "lg";
export function Dialog({
  title,
  description,
  size = "md",
  children,
  onClose,
  busy = false,
  active = true,
  role = "dialog",
  initialFocusRef,
}: {
  title: string;
  /** Texto breve bajo el título: qué registro se está viendo o editando. */
  description?: ReactNode;
  size?: DialogSize;
  children: ReactNode;
  onClose: () => void;
  busy?: boolean;
  /** Una ventana de fondo conserva sus datos y cede el teclado a la confirmación. */
  active?: boolean;
  role?: "dialog" | "alertdialog";
  /** Control que recibe el foco al abrir; por defecto se enfoca la ventana. */
  initialFocusRef?: RefObject<HTMLElement | null>;
}) {
  const id = useId();
  const ref = useRef<HTMLDivElement>(null);
  const close = useEffectEvent(() => {
    if (active && !busy) onClose();
  });
  const focusInitial = useEffectEvent(() => {
    if (active) (initialFocusRef?.current ?? ref.current)?.focus();
  });
  const handleKey = useEffectEvent((event: KeyboardEvent) => {
    const node = ref.current;
    if (!active || !node) return;
    if (event.key === "Escape") {
      close();
      return;
    }
    if (event.key !== "Tab") return;
    const controls = Array.from(
      node.querySelectorAll<HTMLElement>(
        'button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]',
      ),
    );
    if (controls.length === 0) {
      event.preventDefault();
      node.focus();
      return;
    }
    const first = controls[0],
      last = controls.at(-1);
    if (
      event.shiftKey &&
      (document.activeElement === first || document.activeElement === node)
    ) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  });
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    focusInitial();
    const listener = (event: KeyboardEvent) => handleKey(event);
    document.addEventListener("keydown", listener);
    return () => {
      document.removeEventListener("keydown", listener);
      previous?.focus();
    };
  }, []);
  return (
    <div className="modal-backdrop" inert={!active} aria-hidden={!active || undefined}>
      <div
        ref={ref}
        className={`dialog dialog-${size}`}
        role={role}
        aria-modal={active || undefined}
        aria-labelledby={`${id}-title`}
        aria-describedby={description ? `${id}-description` : undefined}
        tabIndex={-1}
      >
        <header className="dialog-header">
          <div>
            <h3 id={`${id}-title`}>{title}</h3>
            {description && <p id={`${id}-description`}>{description}</p>}
          </div>
          <button
            type="button"
            className="dialog-close"
            disabled={busy || !active}
            onClick={onClose}
            aria-label="Cerrar ventana"
            title="Cerrar"
          >
            <svg
              viewBox="0 0 24 24"
              width="18"
              height="18"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              aria-hidden="true"
            >
              <path d="M6 6l12 12M18 6 6 18" />
            </svg>
          </button>
        </header>
        <div className="dialog-body">{children}</div>
      </div>
    </div>
  );
}

"use client";
import {
  useEffect,
  useEffectEvent,
  useId,
  useRef,
  type ReactNode,
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
}: {
  title: string;
  /** Texto breve bajo el título: qué registro se está viendo o editando. */
  description?: ReactNode;
  size?: DialogSize;
  children: ReactNode;
  onClose: () => void;
  busy?: boolean;
}) {
  const id = useId();
  const ref = useRef<HTMLDivElement>(null);
  const close = useEffectEvent(() => {
    if (!busy) onClose();
  });
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const node = ref.current;
    node?.focus();
    const listener = (event: KeyboardEvent) => {
      if (event.key === "Escape") close();
      if (event.key !== "Tab" || !node) return;
      const controls = Array.from(
        node.querySelectorAll<HTMLElement>(
          'button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]',
        ),
      );
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
    };
    document.addEventListener("keydown", listener);
    return () => {
      document.removeEventListener("keydown", listener);
      previous?.focus();
    };
  }, []);
  return (
    <div className="modal-backdrop">
      <div
        ref={ref}
        className={`dialog dialog-${size}`}
        role="dialog"
        aria-modal="true"
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
            disabled={busy}
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

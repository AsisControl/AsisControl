"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useRouter } from "next/navigation";
import { message } from "@/core/api/client";
import { usePaged } from "@/shared/hooks/use-paged";
import { dateLabel } from "@/shared/lib/formatters";
import { Dialog, Feedback, Loading, Pager, StatusPill } from "@/shared/ui";
import {
  NOTIFICATIONS_CHANGED, NOTIFICATIONS_PATH, markNotificationsRead, notificationDestination,
  unreadNotifications, type Notification,
} from "./notification-service";
import styles from "./notifications.module.css";

const SUMMARY_REFRESH_MS = 30_000;

export function NotificationBell({ onOpen }: { onOpen?: () => void }) {
  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState<number | null>(null);
  const [summaryError, setSummaryError] = useState("");
  const requestNumber = useRef(0);
  const invalidateSummary = useCallback(() => {
    requestNumber.current++;
  }, []);
  const refreshSummary = useCallback(async () => {
    const request = ++requestNumber.current;
    try {
      const summary = await unreadNotifications();
      if (request !== requestNumber.current) return;
      setUnread(summary.noLeidas);
      setSummaryError("");
    } catch {
      if (request === requestNumber.current) {
        setSummaryError("No se pudo actualizar el contador de notificaciones.");
      }
    }
  }, []);

  useEffect(() => {
    function refreshIfVisible() {
      if (document.visibilityState === "visible") void refreshSummary();
    }
    refreshIfVisible();
    const timer = window.setInterval(refreshIfVisible, SUMMARY_REFRESH_MS);
    window.addEventListener("focus", refreshIfVisible);
    window.addEventListener(NOTIFICATIONS_CHANGED, refreshIfVisible);
    document.addEventListener("visibilitychange", refreshIfVisible);
    return () => {
      invalidateSummary();
      window.clearInterval(timer);
      window.removeEventListener("focus", refreshIfVisible);
      window.removeEventListener(NOTIFICATIONS_CHANGED, refreshIfVisible);
      document.removeEventListener("visibilitychange", refreshIfVisible);
    };
  }, [invalidateSummary, refreshSummary]);

  const label = summaryError ? "Notificaciones, contador sin actualizar"
    : unread ? `Notificaciones, ${unread} sin leer` : "Notificaciones";
  return <>
    <button type="button" className={styles.bell} aria-haspopup="dialog"
      aria-label={label} title={summaryError || undefined}
      onClick={() => {
        onOpen?.();
        void refreshSummary();
        setOpen(true);
      }}>
      <svg aria-hidden="true" width="21" height="21" viewBox="0 0 24 24" fill="none"
        stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" />
      </svg>
      {summaryError ? <span className={`${styles.count} ${styles.countWarning}`} aria-hidden="true">!</span>
        : unread !== null && unread > 0 && <span className={styles.count} aria-hidden="true">{unread > 99 ? "99+" : unread}</span>}
    </button>
    {open && createPortal(
      <NotificationInbox unread={unread} summaryError={summaryError}
        onRetrySummary={refreshSummary} onClose={() => setOpen(false)} />,
      document.body,
    )}
  </>;
}

function NotificationInbox({ unread, summaryError, onRetrySummary, onClose }: {
  unread: number | null;
  summaryError: string;
  onRetrySummary: () => Promise<void>;
  onClose: () => void;
}) {
  const router = useRouter();
  const list = usePaged<Notification>(NOTIFICATIONS_PATH, "", "", 10);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function read(id?: number, destination?: string) {
    setBusy(true);
    setError("");
    try {
      await markNotificationsRead(id);
      if (destination) {
        router.push(destination);
        onClose();
      } else list.refresh();
    } catch (cause) {
      setError(message(cause));
    } finally { setBusy(false); }
  }

  return <Dialog title="Notificaciones" size="lg" onClose={onClose} busy={busy}>
    {summaryError && <div className={styles.toolbar}>
      <Feedback error={summaryError} />
      <button type="button" onClick={() => void onRetrySummary()}>Reintentar contador</button>
    </div>}
    <Feedback error={error || list.error} />
    {list.error && <button type="button" onClick={list.refresh}>Reintentar carga de avisos</button>}
    {list.loading ? <Loading>Cargando notificaciones…</Loading> : !list.error && (
      list.data?.content.length ? <>
        <div className={styles.toolbar}>
          {unread !== null && !summaryError &&
            <span>{unread === 1 ? "1 aviso sin leer" : `${unread} avisos sin leer`}</span>}
          <button type="button" disabled={busy || (unread === 0 && !summaryError)}
            onClick={() => void read()}>Marcar todas como leídas</button>
        </div>
        <ul className={styles.inbox}>
          {list.data.content.map((notice) => {
            const target = notificationDestination(notice);
            return <li key={notice.id} className={styles.notice}>
              <div className={styles.noticeTitle}>
                <strong>{notice.titulo}</strong>
                {!notice.leida && <StatusPill state="PENDIENTE" label="Sin leer" />}
              </div>
              <p>{notice.mensaje}</p>
              <small>{dateLabel(notice.fechaCreacion)}</small>
              <div className={styles.noticeActions}>
                {!notice.leida && <button type="button" disabled={busy} onClick={() => void read(notice.id)}>
                  Marcar como leída
                </button>}
                {target && <button type="button" className="primary-action" disabled={busy} onClick={() => {
                  if (!notice.leida && target.readOnOpen) void read(notice.id, target.path);
                  else { router.push(target.path); onClose(); }
                }}>Abrir</button>}
              </div>
            </li>;
          })}
        </ul>
        <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
      </> : <p className="empty">Todavía no tienes notificaciones.</p>
    )}
  </Dialog>;
}

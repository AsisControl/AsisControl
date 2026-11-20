const MAX_TIMEOUT_MS = 2 ** 31 - 1;

/** Revisa nuevamente el vencimiento cuando una sola espera no alcanza. */
export function scheduleRenewal(at: number, renew: () => void): () => void {
  let timer: number | undefined;
  function schedule() {
    const remaining = at - Date.now();
    timer = window.setTimeout(() => {
      if (at > Date.now()) schedule();
      else renew();
    }, Math.min(MAX_TIMEOUT_MS, Math.max(0, remaining)));
  }
  schedule();
  return () => window.clearTimeout(timer);
}

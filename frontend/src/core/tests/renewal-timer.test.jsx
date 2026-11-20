import { afterEach, expect, test, vi } from "vitest";
import { scheduleRenewal } from "../auth/renewal-timer";

afterEach(() => { vi.clearAllTimers(); vi.useRealTimers(); });

test("Un vencimiento de 30 días espera por tramos sin renovar antes de tiempo", () => {
  vi.useFakeTimers();
  const renew = vi.fn();
  const delay = 30 * 24 * 60 * 60 * 1000;
  scheduleRenewal(Date.now() + delay, renew);
  vi.advanceTimersByTime(2 ** 31 - 1);
  expect(renew).not.toHaveBeenCalled();
  vi.advanceTimersByTime(delay - (2 ** 31 - 1) - 1);
  expect(renew).not.toHaveBeenCalled();
  vi.advanceTimersByTime(1);
  expect(renew).toHaveBeenCalledTimes(1);
});

test("Conserva la espera normal y permite cancelar un tramo reprogramado", () => {
  vi.useFakeTimers();
  const renew = vi.fn();
  scheduleRenewal(Date.now() + 14 * 60 * 1000, renew);
  vi.advanceTimersByTime(14 * 60 * 1000);
  expect(renew).toHaveBeenCalledTimes(1);
  const cancel = scheduleRenewal(Date.now() + 30 * 24 * 60 * 60 * 1000, renew);
  vi.advanceTimersByTime(2 ** 31 - 1);
  cancel();
  vi.advanceTimersByTime(30 * 24 * 60 * 60 * 1000);
  expect(renew).toHaveBeenCalledTimes(1);
});

test("Un vencimiento pasado se procesa sin una espera negativa", () => {
  vi.useFakeTimers();
  const renew = vi.fn();
  scheduleRenewal(Date.now() - 1000, renew);
  vi.advanceTimersByTime(0);
  expect(renew).toHaveBeenCalledTimes(1);
});

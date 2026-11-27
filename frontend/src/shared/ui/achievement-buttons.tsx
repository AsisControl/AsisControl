"use client";
import styles from "./achievement-buttons.module.css";

export const achievementLevels = ["AD", "A", "B", "C"] as const;
export type AchievementChoice = typeof achievementLevels[number];

export function AchievementButtons({ label, selected, onChange, disabled = false, correction = false }: {
  label: string; selected?: AchievementChoice; onChange: (value: AchievementChoice) => void; disabled?: boolean;
  correction?: boolean;
}) {
  return <div role="group" aria-label={label} className={styles.group}>
    {achievementLevels.map(level => <button key={level} type="button"
      className={styles.button}
      data-correction={correction && selected === level ? "true" : undefined}
      aria-pressed={selected === level} aria-label={`${level}: ${label}`} disabled={disabled}
      onClick={() => onChange(level)}>{level}</button>)}
  </div>;
}

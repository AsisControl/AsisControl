import { RecoveryScreen } from "@/core/auth/recovery";
import type { Metadata } from "next";
export const metadata: Metadata = {
  title: "Recuperar contraseña | AsisControl",
  referrer: "no-referrer",
  robots: { index: false, follow: false },
};
export default function Page() {
  return <RecoveryScreen />;
}

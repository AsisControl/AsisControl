import type { Metadata } from "next";
import { QrWallet } from "@/modules/guardian-payments";

export const metadata: Metadata = {
  title: "Billetera digital | AsisControl",
  referrer: "no-referrer",
  robots: { index: false, follow: false },
};

export default async function QrPaymentPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return <QrWallet token={token} />;
}

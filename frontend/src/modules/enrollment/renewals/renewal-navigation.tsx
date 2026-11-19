"use client";

import { useRouter } from "next/navigation";
import { PageHeader, TabNav } from "@/shared/ui";
import { GuardianEnrollment } from "../guardian-enrollment";
import { RenewalList } from "./renewal-list";
import { PromotionPanel } from "./promotion-panel";

const GUARDIAN_TABS = [
  { id: "solicitudes", label: "Ingreso nuevo" },
  { id: "renovaciones", label: "Renovación anual" },
];

const ADMIN_TABS = [
  { id: "tramites", label: "Trámites de renovación" },
  { id: "promociones", label: "Resultados de promoción" },
];

export function GuardianEnrollmentPortal({ tab, canWrite }: { tab?: string; canWrite: boolean }) {
  const router = useRouter();
  const renewal = tab === "renovaciones";
  const navigation = (
    <TabNav
      label="Trámites de matrícula"
      tabs={GUARDIAN_TABS}
      current={renewal ? "renovaciones" : "solicitudes"}
      onSelect={(id) => router.push(`/matricula/${id}`)}
    />
  );
  if (!renewal) return <GuardianEnrollment navigation={navigation} />;
  return (
    <section>
      <PageHeader title="Renovación anual" description="Solicita la matrícula del próximo año para tus estudiantes y consulta la decisión de la institución." />
      {navigation}
      <RenewalList canWrite={canWrite} role="APODERADO" />
    </section>
  );
}

export function AdminRenewals({ view, canWrite }: { view?: string; canWrite: boolean }) {
  const router = useRouter();
  const promotions = view === "promociones";
  return (
    <>
      <TabNav
        label="Gestión de renovaciones"
        tabs={ADMIN_TABS}
        current={promotions ? "promociones" : "tramites"}
        onSelect={(id) => router.push(id === "promociones" ? "/matricula/renovaciones/promociones" : "/matricula/renovaciones")}
      />
      {promotions ? <PromotionPanel canWrite={canWrite} /> : <RenewalList canWrite={canWrite} role="ADMINISTRADOR" />}
    </>
  );
}

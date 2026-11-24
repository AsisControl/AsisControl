import { NavIcon } from "@/core/navigation/nav-icons";
import type { Indicador } from "@/core/api/dashboard";
import { HomeIndicator, HomeIndicators } from "./home";

export type DestinoIndicador = { icono: string; enlace?: string };

export function DashboardIndicators({ indicadores, destino }: {
  indicadores: Indicador[];
  destino: (codigo: string) => DestinoIndicador;
}) {
  return (
    <HomeIndicators>
      {indicadores.map(({ codigo, tono, valor, etiqueta }) => {
        const { icono, enlace } = destino(codigo);
        return (
          <HomeIndicator key={codigo} icon={<NavIcon label={icono} />} value={valor} label={etiqueta}
                         tone={tono === "neutral" ? "primary" : tono} href={enlace} />
        );
      })}
    </HomeIndicators>
  );
}

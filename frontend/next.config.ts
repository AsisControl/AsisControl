import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  // Oculta el indicador "N" de desarrollo; los errores de compilación se siguen mostrando.
  devIndicators: false,
};

export default nextConfig;

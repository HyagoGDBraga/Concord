import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Gera um servidor Node minimo em .next/standalone, usado pela imagem de
  // producao. Sem isso a imagem carregaria node_modules inteiro.
  output: "standalone",
  reactStrictMode: true,
  // Nao anunciar a tecnologia do servidor.
  poweredByHeader: false,
};

export default nextConfig;

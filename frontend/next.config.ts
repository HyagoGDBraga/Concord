import type { NextConfig } from "next";

/**
 * Cabecalhos de seguranca da aplicacao.
 *
 * O CSP do backend cobre a API, que so devolve JSON. Este aqui e o que importa
 * de fato: ele governa o HTML, e e a diferenca entre um XSS que executa e um que
 * o navegador bloqueia.
 */
const securityHeaders = [
  {
    key: "Content-Security-Policy",
    value: [
      "default-src 'self'",
      // 'unsafe-inline' em script-src e uma concessao conhecida: o Next injeta
      // o payload de hidratacao inline. Eliminar exige nonce por requisicao,
      // que por sua vez exige renderizacao dinamica em todas as paginas.
      // Registrado como divida; o ganho de fechar isso e real e o custo tambem.
      "script-src 'self' 'unsafe-inline'",
      // O Next injeta estilos inline para otimizacao de fonte e para o CSS
      // critico. Sem 'unsafe-inline' aqui, a pagina renderiza sem estilo.
      "style-src 'self' 'unsafe-inline'",
      "img-src 'self' data: blob:",
      "font-src 'self' data:",
      // 'self' cobre a API na mesma origem; ws e wss cobrem o WebSocket.
      "connect-src 'self' ws: wss:",
      // blob: e necessario para os fluxos de midia do WebRTC nos elementos
      // <video> e <audio>.
      "media-src 'self' blob:",
      "object-src 'none'",
      "base-uri 'self'",
      "form-action 'self'",
      // Impede que o Concord seja embutido em um iframe de outro site —
      // defesa contra clickjacking, que num app de chamadas significaria
      // enganar alguem para clicar em "atender".
      "frame-ancestors 'none'",
      "upgrade-insecure-requests",
    ].join("; "),
  },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "no-referrer" },
  {
    // Restringe as permissoes do navegador ao minimo. Microfone, camera e
    // captura de tela sao liberados apenas para a propria origem; o resto e
    // negado a todos, inclusive a si.
    key: "Permissions-Policy",
    value: [
      "camera=(self)",
      "microphone=(self)",
      "display-capture=(self)",
      "geolocation=()",
      "payment=()",
      "usb=()",
      "magnetometer=()",
      "accelerometer=()",
      "gyroscope=()",
      "interest-cohort=()",
    ].join(", "),
  },
  // Isola o contexto de navegacao: impede que outra aba obtenha referencia a
  // esta janela e reduz a superficie para ataques de canal lateral.
  { key: "Cross-Origin-Opener-Policy", value: "same-origin" },
  { key: "Cross-Origin-Resource-Policy", value: "same-origin" },
];

const nextConfig: NextConfig = {
  // Gera um servidor Node minimo em .next/standalone, usado pela imagem de
  // producao. Sem isso a imagem carregaria node_modules inteiro.
  output: "standalone",
  reactStrictMode: true,
  // Nao anunciar a tecnologia do servidor.
  poweredByHeader: false,

  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;

import type { Metadata, Viewport } from "next";
import { SessionProvider } from "@/lib/session";
import { ThemeProvider } from "@/lib/theme";
import "./globals.css";

export const metadata: Metadata = {
  title: "Concord",
  description: "Comunicacao privada: mensagens, voz, video e tela.",
  robots: {
    // Aplicacao privada: nao deve ser indexada por buscadores.
    index: false,
    follow: false,
  },
};

export const viewport: Viewport = {
  themeColor: "#0b1220",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR" data-theme="classic">
      <body>
        <ThemeProvider>
          <SessionProvider>{children}</SessionProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}

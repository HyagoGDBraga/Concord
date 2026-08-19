"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { useSession } from "@/lib/session";
import { RealtimeProvider } from "@/lib/realtime";
import { CallProvider } from "@/lib/callContext";
import { CallPanel } from "@/components/CallPanel";
import { ConsentBanner } from "@/components/ConsentBanner";
import { Button, Spinner } from "@/components/ui";

/**
 * Moldura da area autenticada.
 *
 * A guarda aqui e de experiencia, nao de seguranca: ela evita que o usuario veja
 * uma tela vazia. A autorizacao real acontece no backend, que recusa qualquer
 * requisicao sem sessao valida. Nenhuma decisao de acesso depende deste codigo.
 */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthenticatedShell>
      <RealtimeProvider>
        <CallProvider>
          {children}
          {/* Montado fora das paginas: uma chamada continua enquanto o usuario
              navega, e o convite precisa aparecer esteja ele onde estiver. */}
          <CallPanel />
        </CallProvider>
      </RealtimeProvider>
    </AuthenticatedShell>
  );
}

/**
 * Casca autenticada. O RealtimeProvider fica DENTRO dela de proposito: a
 * conexao WebSocket so e aberta depois que a sessao foi confirmada, evitando um
 * handshake que o servidor recusaria de qualquer forma.
 */
function AuthenticatedShell({ children }: { children: React.ReactNode }) {
  const { user, loading, logout } = useSession();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (!loading && !user) {
      router.replace("/login");
    }
  }, [loading, user, router]);

  if (loading || !user) {
    return (
      <main className="flex min-h-screen items-center justify-center">
        <Spinner label="Verificando sessao" />
      </main>
    );
  }

  const links = [
    { href: "/", label: "Inicio" },
    { href: "/conversations", label: "Conversas" },
    { href: "/contacts", label: "Contatos" },
    { href: "/settings", label: "Conta" },
    { href: "/diagnostics", label: "Diagnostico" },
    ...(user.role === "ADMIN"
      ? [
          { href: "/admin/users", label: "Usuarios" },
          { href: "/admin/audit", label: "Auditoria" },
        ]
      : []),
  ];

  return (
    <div className="min-h-screen">
      <ConsentBanner />
      <header className="border-b border-line bg-panel">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-x-6 gap-y-3 px-6 py-4">
          <Link href="/" className="font-mono text-xs uppercase tracking-[0.3em] text-amber">
            Concord
          </Link>
          <nav className="flex flex-wrap gap-4 text-sm">
            {links.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                className={
                  pathname === link.href
                    ? "text-paper"
                    : "text-muted hover:text-paper"
                }
              >
                {link.label}
              </Link>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-3">
            <span className="font-mono text-xs text-muted">
              {user.username}
            </span>
            <Button
              variant="ghost"
              onClick={async () => {
                await logout();
                router.replace("/login");
              }}
            >
              Sair
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-5xl px-6 py-10">{children}</main>
    </div>
  );
}


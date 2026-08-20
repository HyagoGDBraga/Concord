"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useSession } from "@/lib/session";
import { RealtimeProvider } from "@/lib/realtime";
import { CallProvider } from "@/lib/callContext";
import { CallPanel } from "@/components/CallPanel";
import { ConsentBanner } from "@/components/ConsentBanner";
import { CommunityShell } from "@/components/CommunityShell";
import { Spinner } from "@/components/ui";

/**
 * Moldura da area autenticada.
 *
 * A guarda aqui e de experiencia, nao de seguranca: ela evita que o usuario veja
 * uma tela vazia. A autorizacao real acontece no backend, que recusa qualquer
 * requisicao sem sessao valida. Nenhuma decisao de acesso depende deste codigo.
 */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthenticatedShell>{children}</AuthenticatedShell>
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

  return (
    <div className="min-h-screen">
      <ConsentBanner />
      <RealtimeProvider>
        <CallProvider>
          <CommunityShell
            username={user.username}
            onLogout={async () => {
              await logout();
              router.replace("/login");
            }}
          >
            {children}
          </CommunityShell>
          {/* Montado fora das paginas: uma chamada continua enquanto o usuario
              navega, e o convite precisa aparecer esteja ele onde estiver. */}
          <CallPanel />
        </CallProvider>
      </RealtimeProvider>
    </div>
  );
}


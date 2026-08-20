"use client";

import { useSession } from "@/lib/session";
import Link from "next/link";
import { Badge } from "@/components/ui";

/**
 * Tela inicial da area autenticada.
 *
 * A Fase 2 entrega a fundacao de identidade; conversas e chamadas chegam nas
 * fases 3 a 5. Esta pagina mostra o estado da conta e aponta o que ja funciona,
 * em vez de fingir uma interface de chat que ainda nao existe.
 */
export default function HomePage() {
  const { user } = useSession();
  if (!user) {
    return null;
  }

  return (
    <div className="space-y-8">
      <section className="home-intro">
        <div>
          <p className="eyebrow">Seu espaço de comunicação</p>
          <h2>Olá, {user.displayName}</h2>
          <p>Converse, compartilhe tela e mantenha seu time no mesmo lugar.</p>
        </div>
        <div className="home-status">
          <span className="status-dot" /> online
          <Badge tone={user.role === "ADMIN" ? "warn" : "neutral"}>{user.role}</Badge>
        </div>
      </section>

      <section className="home-grid">
        <Link href="/conversations" className="home-tile home-tile-main">
          <span className="tile-kicker">ATIVIDADE</span>
          <strong>Conversas</strong>
          <span>Retome uma conversa privada ou inicie uma chamada.</span>
          <span className="tile-arrow">→</span>
        </Link>
        <Link href="/contacts" className="home-tile">
          <span className="tile-kicker">REDE</span>
          <strong>Contatos</strong>
          <span>Encontre pessoas e aceite novas conexões.</span>
          <span className="tile-arrow">→</span>
        </Link>
        <Link href="/settings" className="home-tile">
          <span className="tile-kicker">PERFIL</span>
          <strong>Conta</strong>
          <span>Preferências, segurança e sessões ativas.</span>
          <span className="tile-arrow">→</span>
        </Link>
      </section>

      <section className="home-bottom">
        <div>
          <p className="eyebrow">Agora</p>
          <h3>Seu espaço está pronto.</h3>
          <p className="text-muted">Escolha um canal na barra lateral para começar a construir sua comunidade.</p>
        </div>
        <Link href="/diagnostics" className="home-text-link">Ver diagnóstico →</Link>
      </section>
    </div>
  );
}

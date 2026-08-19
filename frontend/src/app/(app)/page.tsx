"use client";

import Link from "next/link";
import { useSession } from "@/lib/session";
import { Badge, Card } from "@/components/ui";

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
    <div className="space-y-6">
      <Card
        title={`Ola, ${user.displayName}`}
        description="Conversas por texto ja funcionam. Voz, video e tela chegam nas fases 5 e 6."
      >
        <dl className="grid gap-4 sm:grid-cols-3">
          <div>
            <dt className="font-mono text-xs uppercase tracking-widest text-muted">
              Usuario
            </dt>
            <dd className="mt-1 text-sm">{user.username}</dd>
          </div>
          <div>
            <dt className="font-mono text-xs uppercase tracking-widest text-muted">
              Estado
            </dt>
            <dd className="mt-1">
              <Badge tone={user.status === "ACTIVE" ? "good" : "warn"}>
                {user.status}
              </Badge>
            </dd>
          </div>
          <div>
            <dt className="font-mono text-xs uppercase tracking-widest text-muted">
              Papel
            </dt>
            <dd className="mt-1">
              <Badge tone={user.role === "ADMIN" ? "warn" : "neutral"}>
                {user.role}
              </Badge>
            </dd>
          </div>
        </dl>
      </Card>

      <Card title="O que ja funciona">
        <ul className="space-y-2 text-sm text-muted">
          <li>
            <Link href="/contacts" className="text-paper hover:text-amber">
              Contatos
            </Link>{" "}
            — adicionar por nome de usuario, aceitar pedidos, bloquear.
          </li>
          <li>
            <Link href="/conversations" className="text-paper hover:text-amber">
              Conversas
            </Link>{" "}
            — mensagens de texto com quem ja e seu contato.
          </li>
          <li>
            <Link href="/settings" className="text-paper hover:text-amber">
              Conta
            </Link>{" "}
            — perfil, senha, e-mail, dispositivos conectados e exclusao.
          </li>
          <li>
            <Link href="/diagnostics" className="text-paper hover:text-amber">
              Diagnostico
            </Link>{" "}
            — estado da cadeia navegador, Caddy, backend e banco.
          </li>
        </ul>
      </Card>
    </div>
  );
}

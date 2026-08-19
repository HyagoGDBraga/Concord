export const metadata = { title: "Termos de Uso — Concord" };

/** Mesma ressalva da politica de privacidade: rascunho tecnico. */
export default function TermosPage() {
  return (
    <>
      <div className="rounded border border-amber/50 bg-ink/60 p-4 text-amber">
        <strong>Rascunho tecnico.</strong> Este texto precisa de revisao
        juridica antes de valer como termo de uso.
      </div>

      <h1 className="text-2xl font-semibold text-paper">Termos de Uso</h1>
      <p className="font-mono text-xs text-muted">Versao 2026-01</p>

      <h2 className="pt-4 text-lg font-semibold text-paper">O que e</h2>
      <p className="text-muted">
        O Concord e um sistema privado de comunicacao entre pessoas que se
        aceitaram como contatos. Nao e um servico publico e nao ha garantia de
        disponibilidade.
      </p>

      <h2 className="pt-4 text-lg font-semibold text-paper">Sua conta</h2>
      <ul className="list-disc space-y-1 pl-5 text-muted">
        <li>Voce e responsavel por manter sua senha em sigilo.</li>
        <li>
          Voce so recebe mensagens de quem aceitou como contato, e pode
          bloquear qualquer pessoa a qualquer momento.
        </li>
        <li>
          Uma conta pode ser desativada por um administrador em caso de abuso, e
          voce sera avisado por e-mail com o motivo.
        </li>
      </ul>

      <h2 className="pt-4 text-lg font-semibold text-paper">Limites</h2>
      <p className="text-muted">
        Nao use o Concord para assediar pessoas, distribuir conteudo ilegal ou
        tentar obter acesso a contas alheias.
      </p>
    </>
  );
}

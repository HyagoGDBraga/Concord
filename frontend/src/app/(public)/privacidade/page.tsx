export const metadata = { title: "Politica de Privacidade — Concord" };

/**
 * ATENCAO: texto de referencia tecnica, nao documento juridico.
 *
 * Ele descreve com precisao o que o sistema faz — e isso tem valor, porque a
 * maior fonte de politica de privacidade errada e alguem descrevendo um
 * tratamento de dados que o software nao pratica. Mas descrever corretamente
 * nao e o mesmo que redigir um documento valido, e este ainda precisa passar
 * por um advogado antes de ir ao ar.
 */
export default function PrivacidadePage() {
  return (
    <>
      <div className="rounded border border-amber/50 bg-ink/60 p-4 text-amber">
        <strong>Rascunho tecnico.</strong> Este texto descreve o que o sistema
        faz. Ele ainda precisa de revisao juridica antes de valer como politica
        de privacidade.
      </div>

      <h1 className="text-2xl font-semibold text-paper">
        Politica de Privacidade
      </h1>
      <p className="font-mono text-xs text-muted">Versao 2026-01</p>

      <h2 className="pt-4 text-lg font-semibold text-paper">
        Que dados sao tratados
      </h2>
      <ul className="list-disc space-y-1 pl-5 text-muted">
        <li>
          <strong className="text-paper">Cadastro:</strong> nome de usuario,
          e-mail, nome de exibicao, bio e senha (armazenada apenas como hash
          Argon2id, nunca em texto).
        </li>
        <li>
          <strong className="text-paper">Uso:</strong> mensagens trocadas,
          contatos, registro de chamadas (quem, quando, duracao) e sessoes
          ativas com IP e dispositivo.
        </li>
        <li>
          <strong className="text-paper">Seguranca:</strong> registros de
          autenticacao e de acoes administrativas, com IP.
        </li>
      </ul>

      <h2 className="pt-4 text-lg font-semibold text-paper">
        Que dados NAO sao tratados
      </h2>
      <ul className="list-disc space-y-1 pl-5 text-muted">
        <li>
          O conteudo de audio, video e compartilhamento de tela nao passa pelo
          servidor: ele vai direto entre os participantes. Quando a rede exige
          retransmissao, o servidor encaminha bytes cifrados que nao consegue
          ler.
        </li>
        <li>
          Dados de negociacao WebRTC (SDP e candidatos ICE) nao sao gravados em
          lugar nenhum.
        </li>
        <li>
          Nao ha rastreadores, analytics de terceiros nem publicidade.
        </li>
        <li>
          Administradores nao tem acesso ao conteudo de mensagens. Isso nao e
          uma configuracao desligada: nao existe caminho no software que
          entregue mensagem a um administrador.
        </li>
      </ul>

      <h2 className="pt-4 text-lg font-semibold text-paper">
        Por quanto tempo
      </h2>
      <ul className="list-disc space-y-1 pl-5 text-muted">
        <li>Mensagens: enquanto a conversa existir.</li>
        <li>Registros de chamada: 180 dias.</li>
        <li>Registros de seguranca: 6 meses (IP anulado no mesmo prazo).</li>
        <li>Registros de acao administrativa: 24 meses.</li>
        <li>Registros de exercicio de direitos: 60 meses, ja sem IP.</li>
        <li>Contas nunca confirmadas: apagadas em 7 dias.</li>
      </ul>

      <h2 className="pt-4 text-lg font-semibold text-paper">Seus direitos</h2>
      <p className="text-muted">
        Voce pode, a qualquer momento e sem pedir a ninguem:{" "}
        <strong className="text-paper">baixar todos os seus dados</strong> em
        JSON, <strong className="text-paper">corrigir</strong> seu perfil,{" "}
        <strong className="text-paper">encerrar sessoes</strong> de qualquer
        dispositivo e <strong className="text-paper">excluir sua conta</strong>.
        Tudo isso esta em Conta, dentro do aplicativo.
      </p>
      <p className="text-muted">
        Ao excluir a conta, seus dados pessoais sao removidos e o cadastro deixa
        de identificar voce. As mensagens que voce enviou permanecem nas
        conversas de quem as recebeu, exibidas como &quot;Usuario removido&quot;
        — elas tambem sao o historico dessas pessoas.
      </p>
    </>
  );
}

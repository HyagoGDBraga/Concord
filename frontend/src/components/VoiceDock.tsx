"use client";

/**
 * Painel de voz encaixado.
 *
 * Renderizado na casca do aplicativo, acima das rotas. E o que permite
 * continuar na chamada enquanto se navega — antes a sala vivia dentro da
 * pagina do canal, e sair dela derrubava a conexao.
 *
 * A `key` amarrada ao canal e deliberada: trocar de canal REMONTA o componente,
 * e a limpeza do anterior fecha as conexoes antigas. Sem isso as duas salas
 * conviveriam.
 */

import { useVoiceChannel } from "@/lib/voiceChannel";
import { VoiceRoom } from "@/components/VoiceRoom";

export function VoiceDock() {
  const { ativo, sair } = useVoiceChannel();

  if (!ativo) {
    return null;
  }

  return (
    <div className="voice-dock">
      <header className="voice-dock-header">
        <span className="eyebrow">{ativo.serverName}</span>
        <strong>{ativo.channelName}</strong>
      </header>

      <VoiceRoom
        key={ativo.channelId}
        serverId={ativo.serverId}
        channelId={ativo.channelId}
        autoJoin
        onLeave={sair}
      />
    </div>
  );
}

"use client";

/**
 * Cartao do canal de voz, dentro da pagina do canal.
 *
 * Ele NAO hospeda a conexao — apenas ativa o canal no contexto. A sala em si e
 * renderizada pelo VoiceDock, na casca do aplicativo, para sobreviver a
 * navegacao. Antes a sala vivia aqui, e sair da pagina derrubava a chamada.
 */

import { useVoiceChannel } from "@/lib/voiceChannel";
import { useRealtime } from "@/lib/realtime";
import { SpeakerIcon } from "@/components/icons";

export function VoiceChannelCard({
  serverId,
  serverName,
  channelId,
  channelName,
}: {
  serverId: string;
  serverName: string;
  channelId: string;
  channelName: string;
}) {
  // Este cartao so e renderizado quando o usuario NAO esta na sala: assim que
  // entra, o palco assume. Por isso nao ha mais estado "dentro" aqui.
  const { entrar } = useVoiceChannel();
  const { voiceParticipantsByChannel } = useRealtime();

  const participantes = voiceParticipantsByChannel.get(channelId)?.size ?? 0;

  return (
    <section className="rounded-lg border border-line bg-panel p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="eyebrow">Sala de voz</p>
          <strong className="display text-sm">{channelName}</strong>
          <p className="mt-1 text-xs text-muted">
            {participantes === 0
              ? "Ninguém aqui ainda."
              : `${participantes} ${participantes === 1 ? "pessoa conectada" : "pessoas conectadas"}`}
          </p>
        </div>

        {(
          <button
            type="button"
            onClick={() => entrar({ serverId, serverName, channelId, channelName })}
            className="flex items-center gap-2 rounded bg-mint px-4 py-2 text-sm font-semibold text-ink transition hover:brightness-110"
          >
            <SpeakerIcon size={16} />
            Entrar na voz
          </button>
        )}
      </div>
    </section>
  );
}

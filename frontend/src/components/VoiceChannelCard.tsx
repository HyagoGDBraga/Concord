"use client";

/**
 * Cartao do canal de voz, dentro da pagina do canal.
 *
 * Ele NAO hospeda a conexao — apenas ativa o canal no contexto. A sala em si e
 * renderizada pelo VoiceDock, na casca do aplicativo, para sobreviver a
 * navegacao. Antes a sala vivia aqui, e sair da pagina derrubava a chamada.
 */

import { useEffect, useState } from "react";
import { useVoiceChannel } from "@/lib/voiceChannel";
import { useRealtime } from "@/lib/realtime";
import { serversApi, type ServerMember } from "@/lib/chatApi";
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
  const [membros, setMembros] = useState<Map<string, ServerMember>>(new Map());

  // Nomes e fotos de quem ja esta na sala.
  //
  // Antes o cartao dizia apenas "2 pessoas conectadas" — e so entrando dava
  // para descobrir QUEM. Entrar numa sala as cegas e desconfortavel: pode ser
  // uma conversa que voce nao quer interromper, ou pode ser exatamente quem
  // voce procurava.
  useEffect(() => {
    serversApi
      .members(serverId)
      .then((lista) => setMembros(new Map(lista.map((m) => [m.user.id, m]))))
      .catch(() => {});
  }, [serverId]);

  const idsNaSala = Array.from(voiceParticipantsByChannel.get(channelId) ?? []);
  const participantes = idsNaSala.length;

  function nomeDe(userId: string) {
    const membro = membros.get(userId);
    return (
      membro?.nickname ??
      membro?.user.displayName ??
      membro?.user.username ??
      "Alguém"
    );
  }

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

      {/* Quem esta na sala, visivel ANTES de entrar. */}
      {participantes > 0 && (
        <ul className="voice-preview-people">
          {idsNaSala.map((userId) => {
            const foto = membros.get(userId)?.user.avatarUrl;
            const nome = nomeDe(userId);
            return (
              <li key={userId} title={nome}>
                <span className="voice-preview-avatar">
                  {foto ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={foto} alt="" />
                  ) : (
                    nome.slice(0, 2).toUpperCase()
                  )}
                </span>
                <span className="voice-preview-name">{nome}</span>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

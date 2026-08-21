"use client";

/**
 * Barra compacta de voz, no rodape da lista de canais.
 *
 * Mostra apenas onde voce esta e o essencial para agir sem procurar: mudo e
 * sair. Tudo o mais — participantes, transmissoes, camera — fica no palco, no
 * centro da pagina do canal.
 *
 * A divisao existe porque empilhar a sala inteira num canto fica ilegivel
 * assim que ha mais de uma pessoa transmitindo.
 */

import Link from "next/link";
import { useVoiceChannel } from "@/lib/voiceChannel";
import { useVoiceSession } from "@/lib/voiceSession";
import { MicIcon, MicOffIcon, PhoneOffIcon, SpeakerIcon } from "@/components/icons";

export function VoiceMiniBar() {
  const { ativo } = useVoiceChannel();
  const sessao = useVoiceSession();

  if (!ativo) {
    return null;
  }

  return (
    <div className="voice-minibar">
      <Link
        href={`/servers/${ativo.serverId}/channels/${ativo.channelId}`}
        className="voice-minibar-where"
        title="Abrir a sala"
      >
        <SpeakerIcon size={15} />
        <span>
          <strong>{ativo.channelName}</strong>
          <small>
            {sessao.joined
              ? `${sessao.participants.length + 1} conectado(s)`
              : "conectando…"}
          </small>
        </span>
      </Link>

      <div className="voice-minibar-actions">
        <button
          type="button"
          onClick={sessao.toggleMic}
          disabled={sessao.pushToTalk}
          className={sessao.micEnabled ? "" : "is-off"}
          aria-label={sessao.micEnabled ? "Silenciar" : "Ativar microfone"}
          title={sessao.micEnabled ? "Silenciar" : "Ativar microfone"}
        >
          {sessao.micEnabled ? <MicIcon size={17} /> : <MicOffIcon size={17} />}
        </button>

        <button
          type="button"
          onClick={sessao.sair}
          className="is-danger"
          aria-label="Sair da sala"
          title="Sair da sala"
        >
          <PhoneOffIcon size={17} />
        </button>
      </div>
    </div>
  );
}

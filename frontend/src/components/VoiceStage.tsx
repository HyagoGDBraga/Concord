"use client";

/**
 * Palco da sala de voz, no centro da página do canal.
 *
 * A conexão não vive aqui — ela está no VoiceSessionProvider, acima das rotas.
 * Este componente só desenha. Por isso navegar para outro canal e voltar não
 * derruba nada.
 *
 * Decisão de interface: transmissões NÃO tocam sozinhas. Cada uma aparece como
 * um cartão com "Assistir transmissão". Com três pessoas compartilhando, três
 * vídeos decodificando ao mesmo tempo consomem CPU e banda de quem talvez não
 * queira ver nenhum — e o áudio da conversa é o que importa.
 */

import { useEffect, useRef, useState } from "react";
import { useVoiceSession } from "@/lib/voiceSession";
import {
  CameraIcon,
  CameraOffIcon,
  CloseIcon,
  MaximizeIcon,
  MicIcon,
  MicOffIcon,
  MinimizeIcon,
  PhoneOffIcon,
  PushToTalkIcon,
  ScreenShareIcon,
  ScreenShareOffIcon,
} from "@/components/icons";

/** Nome legível de uma tecla física. */
function nomeDaTecla(code: string): string {
  if (code === "Space") return "Espaço";
  if (code.startsWith("Key")) return code.slice(3);
  if (code.startsWith("Digit")) return code.slice(5);
  if (code.startsWith("Numpad")) return "Num " + code.slice(6);
  if (code.endsWith("Left")) return code.replace("Left", " esq.");
  if (code.endsWith("Right")) return code.replace("Right", " dir.");
  return code;
}

export function VoiceStage() {
  const sessao = useVoiceSession();
  const palcoRef = useRef<HTMLDivElement>(null);

  /** Quem o usuário escolheu assistir. Vazio = ninguém tocando. */
  const [assistindo, setAssistindo] = useState<Set<string>>(new Set());

  // Ao sair da sala, esquece o que estava assistindo.
  useEffect(() => {
    if (!sessao.joined) {
      setAssistindo(new Set());
    }
  }, [sessao.joined]);

  if (!sessao.ativo) {
    return null;
  }

  function alternarAssistir(userId: string) {
    setAssistindo((atual) => {
      const proximo = new Set(atual);
      if (proximo.has(userId)) {
        proximo.delete(userId);
      } else {
        proximo.add(userId);
      }
      return proximo;
    });
  }

  async function telaCheia(elemento: HTMLElement | null) {
    if (!elemento) {
      return;
    }
    try {
      if (document.fullscreenElement) {
        await document.exitFullscreen();
      } else {
        await elemento.requestFullscreen();
      }
    } catch {
      sessao.setError("O navegador não permitiu a tela cheia.");
    }
  }

  const transmitindo = sessao.participants.filter(
    (id) => sessao.peerStates.get(id)?.screen || sessao.peerStates.get(id)?.camera,
  );

  const eu = "eu";

  return (
    <section className="voice-stage" ref={palcoRef}>
      <header className="voice-stage-header">
        <div>
          <p className="eyebrow">Sala de voz</p>
          <strong className="display text-sm">
            {sessao.participants.length + 1} na sala
          </strong>
        </div>
        <button type="button" onClick={sessao.sair} className="voice-leave">
          <PhoneOffIcon size={16} />
          Sair da sala
        </button>
      </header>

      {/* ------------------------------------------------- transmissões */}
      {transmitindo.length > 0 && (
        <div className="voice-streams">
          {transmitindo.map((userId) => {
            const estado = sessao.peerStates.get(userId);
            const stream = sessao.remoteStreams.get(userId);
            const trilhas =
              stream?.getVideoTracks().filter((t) => t.readyState === "live" && !t.muted) ?? [];
            const aberto = assistindo.has(userId);

            return (
              <article key={userId} className="voice-stream-card">
                <header>
                  <span className="voice-stream-name">
                    {sessao.nomeDe(userId)}
                  </span>
                  <span className="voice-stream-kind">
                    {estado?.screen ? "compartilhando a tela" : "câmera ligada"}
                  </span>
                </header>

                {aberto && trilhas.length > 0 ? (
                  <div className="voice-stream-player">
                    <video
                      autoPlay
                      playsInline
                      // muted obrigatório: o áudio sai pelo elemento próprio de
                      // cada participante. Com áudio aqui, o autoplay é
                      // bloqueado e o vídeo fica preto.
                      muted
                      onDoubleClick={(e) =>
                        void telaCheia(e.currentTarget.parentElement)
                      }
                      ref={(el) => {
                        const alvo = new MediaStream(trilhas);
                        if (el && el.srcObject !== alvo) {
                          el.srcObject = alvo;
                          el.play().catch(() => {});
                        }
                      }}
                    />
                    <div className="voice-stream-actions">
                      <button
                        type="button"
                        onClick={(e) =>
                          void telaCheia(
                            e.currentTarget.closest(".voice-stream-player"),
                          )
                        }
                        title="Tela cheia (ou dois cliques no vídeo)"
                      >
                        <MaximizeIcon size={16} />
                        Tela cheia
                      </button>
                      <button
                        type="button"
                        onClick={() => alternarAssistir(userId)}
                        title="Parar de assistir"
                      >
                        <CloseIcon size={16} />
                        Fechar
                      </button>
                    </div>
                  </div>
                ) : (
                  <button
                    type="button"
                    onClick={() => alternarAssistir(userId)}
                    className="voice-stream-watch"
                    disabled={trilhas.length === 0}
                  >
                    {trilhas.length === 0
                      ? "Aguardando o vídeo…"
                      : "Assistir transmissão"}
                  </button>
                )}
              </article>
            );
          })}
        </div>
      )}

      {/* ------------------------------------------------- participantes */}
      <ul className="voice-people">
        {[eu, ...sessao.participants].map((id) => {
          const souEu = id === eu;
          const userId = souEu ? "" : id;
          const estado = souEu
            ? {
                muted: !sessao.micEnabled,
                camera: sessao.cameraEnabled,
                screen: sessao.sharingScreen,
              }
            : (sessao.peerStates.get(userId) ?? {
                muted: false,
                camera: false,
                screen: false,
              });
          const falando = souEu
            ? false
            : sessao.speaking.has(userId) && !estado.muted;

          return (
            <li key={id} className="voice-person">
              <span
                className={`voice-person-avatar ${
                  falando ? "speaking-ring" : "speaking-ring-idle"
                }`}
              >
                {souEu ? "EU" : sessao.iniciaisDe(userId)}
              </span>
              <span className="voice-person-name">
                {souEu ? "Você" : sessao.nomeDe(userId)}
              </span>
              <span className="voice-person-tags">
                {estado.muted && <span>mudo</span>}
                {estado.camera && <span>câmera</span>}
                {estado.screen && <span className="is-live">● tela</span>}
              </span>
            </li>
          );
        })}
      </ul>

      {/* ------------------------------------------------------ controles */}
      <div className="voice-controls">
        <button
          type="button"
          onClick={sessao.toggleMic}
          disabled={sessao.pushToTalk}
          className={`icon-button ${sessao.micEnabled ? "" : "is-off"}`}
          title={sessao.micEnabled ? "Silenciar" : "Ativar microfone"}
        >
          {sessao.micEnabled ? <MicIcon /> : <MicOffIcon />}
        </button>

        <button
          type="button"
          onClick={() => void sessao.toggleCamera()}
          className={`icon-button ${sessao.cameraEnabled ? "is-on" : ""}`}
          title={sessao.cameraEnabled ? "Desligar câmera" : "Ligar câmera"}
        >
          {sessao.cameraEnabled ? <CameraIcon /> : <CameraOffIcon />}
        </button>

        <button
          type="button"
          onClick={() => void sessao.toggleScreenShare()}
          className={`icon-button ${sessao.sharingScreen ? "is-on" : ""}`}
          title={sessao.sharingScreen ? "Parar de compartilhar" : "Compartilhar tela"}
        >
          {sessao.sharingScreen ? <ScreenShareOffIcon /> : <ScreenShareIcon />}
        </button>

        <button
          type="button"
          onClick={() => sessao.setPushToTalk((atual) => !atual)}
          className={`icon-button ${sessao.pushToTalk ? "is-on" : ""} ${
            sessao.pttHeld ? "ptt-active" : ""
          }`}
          title="Push-to-talk"
        >
          <PushToTalkIcon />
        </button>
      </div>

      {sessao.pushToTalk && (
        <p className="voice-ptt-hint">
          Segure <kbd>{nomeDaTecla(sessao.pttKey)}</kbd> para falar.
          <button type="button" onClick={() => sessao.setCapturandoTecla(true)}>
            {sessao.capturandoTecla ? "pressione a tecla…" : "trocar tecla"}
          </button>
          {sessao.pttHeld && <span className="is-live">transmitindo</span>}
        </p>
      )}

      {sessao.sharingScreen && (
        <p className="voice-sharing-note">
          ● Você está compartilhando. Prefira uma <strong>janela</strong>: o
          monitor inteiro, com o Concord nele, gera o espelho infinito.
        </p>
      )}

      {sessao.error && (
        <p className="voice-error" role="alert">
          {sessao.error}
        </p>
      )}
    </section>
  );
}

"use client";

/**
 * Painel de chamada.
 *
 * Fica montado o tempo todo na area autenticada e so aparece quando ha chamada:
 * um convite precisa surgir esteja o usuario em que tela estiver.
 */

import { useEffect, useRef, useState } from "react";
import { useCall } from "@/lib/callContext";
import { MaximizeIcon, MinimizeIcon } from "@/components/icons";
import { Alert, Badge, Button } from "@/components/ui";

export function CallPanel() {
  const {
    phase,
    call,
    localStream,
    remoteStream,
    remoteVideoAvailable,
    micEnabled,
    cameraEnabled,
    sharingScreen,
    peerSharingScreen,
    error,
    accept,
    reject,
    hangUp,
    toggleMic,
    toggleCamera,
    toggleScreenShare,
    dismissError,
  } = useCall();

  const localVideoRef = useRef<HTMLVideoElement>(null);
  const remoteVideoRef = useRef<HTMLVideoElement>(null);
  const remoteAudioRef = useRef<HTMLAudioElement>(null);
  const [remoteFullscreen, setRemoteFullscreen] = useState(false);

  // srcObject nao e um atributo: precisa ser atribuido pelo DOM.
  useEffect(() => {
    if (localVideoRef.current) {
      localVideoRef.current.srcObject = localStream;
    }
  }, [localStream]);

  // O mesmo fluxo remoto alimenta os dois elementos: o <video> quando ha
  // camera ou compartilhamento, o <audio> quando a chamada e so de voz. O
  // estado de video entra nas dependencias porque o elemento pode ser montado
  // depois que uma tela e anunciada numa chamada que comecou com audio.
  useEffect(() => {
    if (remoteVideoRef.current) {
      remoteVideoRef.current.srcObject = remoteStream;
    }
    if (remoteAudioRef.current) {
      remoteAudioRef.current.srcObject = remoteStream;
    }
    if (remoteVideoRef.current) {
      void remoteVideoRef.current.play().catch(() => {});
    }
  }, [remoteStream, remoteVideoAvailable, cameraEnabled, call?.type, peerSharingScreen, sharingScreen]);

  useEffect(() => {
    const updateFullscreenState = () => {
      setRemoteFullscreen(document.fullscreenElement === remoteVideoRef.current);
    };
    document.addEventListener("fullscreenchange", updateFullscreenState);
    return () => document.removeEventListener("fullscreenchange", updateFullscreenState);
  }, []);

  async function toggleRemoteFullscreen() {
    const video = remoteVideoRef.current;
    if (!video) {
      return;
    }
    if (document.fullscreenElement) {
      await document.exitFullscreen();
      return;
    }
    await video.requestFullscreen();
  }

  if (error && phase === "idle") {
    return (
      <div className="fixed bottom-4 right-4 z-50 w-80">
        <Alert tone="error">
          {error}{" "}
          <button onClick={dismissError} className="underline">
            fechar
          </button>
        </Alert>
      </div>
    );
  }

  if (phase === "idle" || !call) {
    return null;
  }

  const peerName = call.peer?.displayName ?? "Contato";
  const peerAvatar = call.peer?.avatarUrl ?? null;

  /**
   * Foto de quem esta do outro lado.
   *
   * Numa chamada de voz nao ha nada para olhar — sem rosto, a interface e um
   * retangulo com um nome. A foto e o que faz parecer uma conversa com alguem.
   */
  function AvatarDoContato({ size = 44 }: { size?: number }) {
    return (
      <span
        className="flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-elevated font-semibold"
        style={{ width: size, height: size, fontSize: size * 0.34 }}
      >
        {peerAvatar ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={peerAvatar}
            alt=""
            className="h-full w-full object-cover"
          />
        ) : (
          peerName.slice(0, 2).toUpperCase()
        )}
      </span>
    );
  }
  const comVideo =
    call.type === "VIDEO" || cameraEnabled || sharingScreen || peerSharingScreen || remoteVideoAvailable;

  /* ----------------------------------------------------- convite recebido */

  if (phase === "incoming") {
    return (
      <aside
        role="dialog"
        aria-label="Chamada recebida"
        className="fixed bottom-4 right-4 z-50 w-80 rounded-md border border-amber/50 bg-panel p-5 shadow-lg"
      >
        <p className="font-mono text-xs uppercase tracking-widest text-amber">
          Chamada recebida
        </p>
        <div className="mt-3 flex items-center gap-3">
          <AvatarDoContato size={56} />
          <p className="text-lg font-semibold">{peerName}</p>
        </div>
        <p className="mt-1 text-sm text-muted">
          {call.type === "VIDEO" ? "Video" : "Voz"}
        </p>
        <div className="mt-5 flex gap-3">
          <Button onClick={() => void accept()}>Atender</Button>
          <Button variant="danger" onClick={() => void reject()}>
            Recusar
          </Button>
        </div>
      </aside>
    );
  }

  /* -------------------------------------------------- chamando ou em curso */

  return (
    <aside
      role="dialog"
      aria-label="Chamada em andamento"
      className="fixed bottom-4 right-4 z-50 w-[22rem] rounded-md border border-line bg-panel p-5 shadow-lg"
    >
      <div className="flex items-center justify-between">
        <span className="flex items-center gap-2">
          <AvatarDoContato size={32} />
          <span className="text-sm font-semibold">{peerName}</span>
        </span>
        <Badge tone={phase === "active" ? "good" : "warn"}>
          {phase === "dialing"
            ? "Chamando"
            : phase === "connecting"
              ? "Conectando"
              : "Em chamada"}
        </Badge>
      </div>

      {peerSharingScreen && (
        <p className="mt-3 font-mono text-xs uppercase tracking-widest text-amber">
          {peerName} esta compartilhando a tela
        </p>
      )}

      {/* Chamada so de voz: a foto ocupa o lugar do video. Um painel sem nada
          para ver nao comunica que existe alguem do outro lado. */}
      {!comVideo && phase === "active" && (
        <div className="mt-4 flex flex-col items-center gap-2 rounded border border-line bg-ink/50 py-6">
          <AvatarDoContato size={88} />
          <p className="font-mono text-xs text-muted">chamada de voz</p>
        </div>
      )}

      {comVideo && (
        <div className="relative mt-4 overflow-hidden rounded border border-line bg-ink">
          {/* object-contain quando e tela: recortar as bordas de uma janela
              esconde justamente barra de menu, abas e o que a pessoa quer
              mostrar. Rosto pode ser recortado; texto nao. */}
          <video
            ref={remoteVideoRef}
            autoPlay
            playsInline
            // Duplo clique alterna a tela cheia: e o gesto que as pessoas ja
            // conhecem de qualquer player, e nao depende de acertar um botao
            // pequeno sobre o video.
            onDoubleClick={() => void toggleRemoteFullscreen()}
            className={`aspect-video w-full cursor-pointer bg-ink ${
              peerSharingScreen ? "object-contain" : "object-cover"
            }`}
          />
          {(peerSharingScreen || remoteVideoAvailable) && (
            <button
              type="button"
              onClick={() => void toggleRemoteFullscreen()}
              aria-label={remoteFullscreen ? "Sair da tela cheia" : "Ver em tela cheia"}
              title={
                remoteFullscreen
                  ? "Sair da tela cheia (Esc)"
                  : "Ver em tela cheia (ou dê dois cliques no vídeo)"
              }
              // Alvo de 44px, o piso das diretrizes de toque. O botao anterior
              // tinha metade disso e ficava sobre a imagem, entao acertar
              // dependia de sorte.
              // Rotulo ao lado do icone: um icone sozinho num canto escuro
              // sobre video passa despercebido, que era a queixa de "nao tem um
              // botao aparente pra dar tela cheia".
              className="absolute right-2 top-2 flex h-11 items-center gap-2 rounded-lg border border-white/30 bg-ink/90 px-3 text-sm font-medium text-white shadow-lg backdrop-blur transition hover:border-white hover:bg-ink"
            >
              {remoteFullscreen ? <MinimizeIcon size={18} /> : <MaximizeIcon size={18} />}
              <span className="hidden sm:inline">
                {remoteFullscreen ? "Sair" : "Tela cheia"}
              </span>
            </button>
          )}
          <video
            ref={localVideoRef}
            autoPlay
            playsInline
            muted
            className="absolute bottom-2 right-2 w-24 rounded border border-line object-cover"
          />
        </div>
      )}

      {sharingScreen && (
        <div className="mt-3">
          <p className="mb-1 font-mono text-[11px] uppercase tracking-widest text-mint">
            Voce esta compartilhando
          </p>
        </div>
      )}

      {/* Sempre presente: em chamada de voz e a unica saida de audio, e em
          chamada de video garante o som se o <video> for pausado pelo
          navegador. */}
      <audio ref={remoteAudioRef} autoPlay className="hidden" />

      {error && (
        <div className="mt-4">
          <Alert tone="error">{error}</Alert>
        </div>
      )}

      <div className="mt-5 flex flex-wrap gap-2">
        <Button variant="secondary" onClick={toggleMic}>
          {micEnabled ? "Silenciar" : "Ativar microfone"}
        </Button>
        <Button variant="secondary" onClick={() => void toggleCamera()}>
          {cameraEnabled ? "Desligar camera" : "Ligar camera"}
        </Button>
        <Button
          variant="secondary"
          disabled={phase !== "active"}
          onClick={() => void toggleScreenShare()}
        >
          {sharingScreen ? "Parar de compartilhar" : "Compartilhar tela"}
        </Button>
        <Button variant="danger" onClick={() => void hangUp()}>
          Desligar
        </Button>
      </div>
    </aside>
  );
}

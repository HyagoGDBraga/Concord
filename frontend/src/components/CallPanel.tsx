"use client";

/**
 * Painel de chamada.
 *
 * Fica montado o tempo todo na area autenticada e so aparece quando ha chamada:
 * um convite precisa surgir esteja o usuario em que tela estiver.
 */

import { useEffect, useRef } from "react";
import { useCall } from "@/lib/callContext";
import { Alert, Badge, Button } from "@/components/ui";

export function CallPanel() {
  const {
    phase,
    call,
    localStream,
    remoteStream,
    micEnabled,
    cameraEnabled,
    sharingScreen,
    peerSharingScreen,
    screenStream,
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
  const screenPreviewRef = useRef<HTMLVideoElement>(null);

  // srcObject nao e um atributo: precisa ser atribuido pelo DOM.
  useEffect(() => {
    if (localVideoRef.current) {
      localVideoRef.current.srcObject = localStream;
    }
  }, [localStream]);

  useEffect(() => {
    if (screenPreviewRef.current) {
      screenPreviewRef.current.srcObject = screenStream;
    }
  }, [screenStream]);

  // O mesmo fluxo remoto alimenta os dois elementos: o <video> quando ha
  // camera, o <audio> quando a chamada e so de voz. Sem o <audio>, uma chamada
  // de voz nao produziria som algum.
  useEffect(() => {
    if (remoteVideoRef.current) {
      remoteVideoRef.current.srcObject = remoteStream;
    }
    if (remoteAudioRef.current) {
      remoteAudioRef.current.srcObject = remoteStream;
    }
  }, [remoteStream]);

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
  const comVideo =
    call.type === "VIDEO" || cameraEnabled || sharingScreen || peerSharingScreen;

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
        <p className="mt-2 text-lg font-semibold">{peerName}</p>
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
        <p className="text-sm font-semibold">{peerName}</p>
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

      {comVideo && (
        <div className="relative mt-4 overflow-hidden rounded border border-line bg-ink">
          {/* object-contain quando e tela: recortar as bordas de uma janela
              esconde justamente barra de menu, abas e o que a pessoa quer
              mostrar. Rosto pode ser recortado; texto nao. */}
          <video
            ref={remoteVideoRef}
            autoPlay
            playsInline
            className={`aspect-video w-full bg-ink ${
              peerSharingScreen ? "object-contain" : "object-cover"
            }`}
          />
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
          <video
            ref={screenPreviewRef}
            autoPlay
            playsInline
            muted
            className="w-full rounded border border-mint/40 bg-ink object-contain"
          />
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

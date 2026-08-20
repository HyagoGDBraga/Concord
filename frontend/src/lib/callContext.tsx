"use client";

/**
 * Estado da chamada em curso.
 *
 * Vive acima das telas porque uma chamada nao pertence a uma pagina: ela
 * continua enquanto o usuario navega entre conversas, e o convite precisa
 * aparecer esteja ele onde estiver.
 *
 * Divisao de responsabilidade: o ciclo de vida (convidar, aceitar, recusar,
 * desligar) vai por REST, porque muda estado no servidor; SDP e candidatos ICE
 * vao pelo WebSocket, porque sao efemeros e nao sao gravados.
 */

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { api, errorMessage } from "./apiClient";
import { clearCallNotification, notifyIncomingCall } from "./desktop";
import {
  useRealtime,
  useRealtimeEvent,
  type CallInfo,
  type CallSignal,
} from "./realtime";
import { useSession } from "./session";
import { PeerConnection, fetchIceConfig, mediaErrorMessage } from "./webrtc";

export type CallPhase = "idle" | "incoming" | "dialing" | "connecting" | "active";

interface CallState {
  phase: CallPhase;
  call: CallInfo | null;
  localStream: MediaStream | null;
  remoteStream: MediaStream | null;
  micEnabled: boolean;
  cameraEnabled: boolean;
  /** Este lado esta compartilhando a tela. */
  sharingScreen: boolean;
  /** O outro lado esta compartilhando a tela. */
  peerSharingScreen: boolean;
  /** Previa local do que esta sendo compartilhado. */
  screenStream: MediaStream | null;
  error: string | null;
  start: (conversationId: string, withVideo: boolean) => Promise<void>;
  accept: () => Promise<void>;
  reject: () => Promise<void>;
  hangUp: () => Promise<void>;
  toggleMic: () => void;
  toggleCamera: () => Promise<void>;
  toggleScreenShare: () => Promise<void>;
  dismissError: () => void;
}

const CallContext = createContext<CallState | null>(null);

export function CallProvider({ children }: { children: React.ReactNode }) {
  const { user } = useSession();
  const { sendCallSignal } = useRealtime();

  const [phase, setPhase] = useState<CallPhase>("idle");
  const [call, setCall] = useState<CallInfo | null>(null);
  const [localStream, setLocalStream] = useState<MediaStream | null>(null);
  const [remoteStream, setRemoteStream] = useState<MediaStream | null>(null);
  const [micEnabled, setMicEnabled] = useState(true);
  const [cameraEnabled, setCameraEnabled] = useState(false);
  const [sharingScreen, setSharingScreen] = useState(false);
  const [peerSharingScreen, setPeerSharingScreen] = useState(false);
  const [screenStream, setScreenStream] = useState<MediaStream | null>(null);
  const [error, setError] = useState<string | null>(null);

  const peerRef = useRef<PeerConnection | null>(null);
  const callRef = useRef<CallInfo | null>(null);
  callRef.current = call;

  /* ------------------------------------------------------------ limpeza */

  const teardown = useCallback(() => {
    clearCallNotification();
    peerRef.current?.close();
    peerRef.current = null;
    setLocalStream(null);
    setRemoteStream(null);
    setPhase("idle");
    setCall(null);
    setMicEnabled(true);
    setCameraEnabled(false);
    setSharingScreen(false);
    setPeerSharingScreen(false);
    setScreenStream(null);
  }, []);

  /* ------------------------------------------------- criacao do peer */

  const createPeer = useCallback(
    async (callId: string, withVideo: boolean) => {
      const iceServers = await fetchIceConfig();

      const peer = new PeerConnection(iceServers, {
        onIceCandidate: (candidate) =>
          sendCallSignal(callId, "ICE_CANDIDATE", candidate),
        onRemoteStream: (stream) => {
          setRemoteStream(stream);
          setPhase("active");
        },
        onScreenShareEnded: () => {
          // O usuario parou pela barra do proprio navegador. A interface precisa
          // acompanhar, e o outro lado precisa saber.
          setSharingScreen(false);
          setScreenStream(null);
          sendCallSignal(callId, "SCREEN_SHARE", { active: false });
        },
        onStateChange: (state) => {
          if (state === "failed" || state === "closed") {
            // A conexao morreu sem ninguem desligar. Encerra no servidor para
            // que o outro lado nao fique preso em "em chamada".
            void api.post(`/calls/${callId}/end`).catch(() => {});
            teardown();
          }
        },
      });

      peerRef.current = peer;
      const stream = await peer.startLocalMedia(withVideo);
      setLocalStream(stream);
      setCameraEnabled(withVideo);
      return peer;
    },
    [sendCallSignal, teardown],
  );

  /* ------------------------------------------------------------- acoes */

  const start = useCallback(
    async (conversationId: string, withVideo: boolean) => {
      setError(null);
      try {
        const created = await api.post<CallInfo>("/calls", {
          conversationId,
          type: withVideo ? "VIDEO" : "AUDIO",
        });
        setCall(created);
        setPhase("dialing");

        // A midia e capturada agora, antes do aceite: se a permissao for
        // negada, e melhor descobrir antes de fazer o telefone do outro tocar.
        await createPeer(created.id, withVideo);
      } catch (err) {
        setError(err instanceof DOMException ? mediaErrorMessage(err) : errorMessage(err));
        const current = callRef.current;
        if (current) {
          void api.post(`/calls/${current.id}/end`).catch(() => {});
        }
        teardown();
      }
    },
    [createPeer, teardown],
  );

  const accept = useCallback(async () => {
    const current = callRef.current;
    if (!current) {
      return;
    }
    setError(null);
    try {
      setPhase("connecting");
      // O peer do lado que atende e criado antes do aceite chegar ao servidor,
      // para estar pronto quando a oferta SDP chegar em seguida.
      await createPeer(current.id, current.type === "VIDEO");
      const accepted = await api.post<CallInfo>(`/calls/${current.id}/accept`);
      setCall(accepted);
    } catch (err) {
      setError(err instanceof DOMException ? mediaErrorMessage(err) : errorMessage(err));
      void api.post(`/calls/${current.id}/end`).catch(() => {});
      teardown();
    }
  }, [createPeer, teardown]);

  const reject = useCallback(async () => {
    const current = callRef.current;
    if (current) {
      await api.post(`/calls/${current.id}/reject`).catch(() => {});
    }
    teardown();
  }, [teardown]);

  const hangUp = useCallback(async () => {
    const current = callRef.current;
    if (current) {
      await api.post(`/calls/${current.id}/end`).catch(() => {});
    }
    teardown();
  }, [teardown]);

  const toggleMic = useCallback(() => {
    setMicEnabled((current) => {
      const next = !current;
      peerRef.current?.setAudioEnabled(next);
      return next;
    });
  }, []);

  /**
   * Liga ou desliga o compartilhamento de tela.
   *
   * A troca em si e local — `replaceTrack` na conexao que ja existe. O sinal
   * enviado ao outro lado serve so para a interface dele saber que aquele video
   * e uma tela, e nao um rosto.
   */
  const toggleScreenShare = useCallback(async () => {
    const peer = peerRef.current;
    const current = callRef.current;
    if (!peer || !current) {
      return;
    }
    try {
      if (peer.isSharingScreen()) {
        await peer.stopScreenShare();
        setSharingScreen(false);
        setScreenStream(null);
        sendCallSignal(current.id, "SCREEN_SHARE", { active: false });
        return;
      }

      const { stream, needsRenegotiation } = await peer.startScreenShare();
      setScreenStream(stream);
      setSharingScreen(true);
      sendCallSignal(current.id, "SCREEN_SHARE", { active: true });

      // So renegocia quando a chamada era de voz e nao havia trilha de video
      // para substituir.
      if (needsRenegotiation) {
        const offer = await peer.createOffer();
        sendCallSignal(current.id, "OFFER", offer);
      }
    } catch (err) {
      // Cancelar a janela de selecao do navegador nao e erro que mereca alarde.
      if (err instanceof DOMException && err.name === "AbortError") {
        return;
      }
      setError(mediaErrorMessage(err));
    }
  }, [sendCallSignal]);

  const toggleCamera = useCallback(async () => {
    const peer = peerRef.current;
    const current = callRef.current;
    if (!peer || !current) {
      return;
    }
    try {
      if (!peer.hasVideoTrack()) {
        // Chamada comecou so com audio: acrescentar video exige renegociar.
        const stream = await peer.addVideoTrack();
        setLocalStream(stream);
        setCameraEnabled(true);

        const offer = await peer.createOffer();
        sendCallSignal(current.id, "OFFER", offer);
        return;
      }
      setCameraEnabled((enabled) => {
        const next = !enabled;
        peer.setVideoEnabled(next);
        return next;
      });
    } catch (err) {
      setError(mediaErrorMessage(err));
    }
  }, [sendCallSignal]);

  /* ------------------------------------------------------------ eventos */

  useRealtimeEvent<CallInfo>("CALL_INVITE", (incoming) => {
    // Ja ocupado: o servidor tambem recusa, mas a interface nao deve piscar um
    // convite que nao pode ser atendido.
    if (callRef.current) {
      return;
    }
    setCall(incoming);
    setPhase("incoming");

    // No desktop, destaca na barra de tarefas e emite notificacao do sistema.
    // No navegador e no-op.
    notifyIncomingCall(incoming.peer?.displayName ?? "Alguem");
  });

  useRealtimeEvent<CallInfo>("CALL_ACCEPTED", async (accepted) => {
    setCall(accepted);
    setPhase("connecting");

    // Quem ligou e quem oferta. Fixado no servidor, elimina glare.
    const peer = peerRef.current;
    if (!peer || !user || accepted.callerId !== user.id) {
      return;
    }
    try {
      const offer = await peer.createOffer();
      sendCallSignal(accepted.id, "OFFER", offer);
    } catch (err) {
      setError(mediaErrorMessage(err));
      await api.post(`/calls/${accepted.id}/end`).catch(() => {});
      teardown();
    }
  });

  useRealtimeEvent<CallInfo>("CALL_ENDED", (ended) => {
    if (callRef.current?.id === ended.id || !callRef.current) {
      teardown();
    }
  });

  useRealtimeEvent<CallSignal>("CALL_SIGNAL", async (signal) => {
    const peer = peerRef.current;
    if (!peer || callRef.current?.id !== signal.callId) {
      return;
    }
    try {
      switch (signal.type) {
        case "OFFER": {
          await peer.setRemoteDescription(signal.payload as RTCSessionDescriptionInit);
          const answer = await peer.createAnswer();
          sendCallSignal(signal.callId, "ANSWER", answer);
          break;
        }
        case "ANSWER":
          await peer.setRemoteDescription(signal.payload as RTCSessionDescriptionInit);
          break;
        case "ICE_CANDIDATE":
          await peer.addIceCandidate(signal.payload as RTCIceCandidateInit);
          break;
        case "SCREEN_SHARE": {
          const { active } = signal.payload as { active: boolean };
          setPeerSharingScreen(active);
          break;
        }
        default:
          break;
      }
    } catch {
      setError("Falha na negociacao da chamada.");
    }
  });

  /* ------------------------------------- recuperacao apos recarregar */

  useEffect(() => {
    if (!user) {
      return;
    }
    // Se o usuario recarregou a pagina no meio de uma chamada, a midia se
    // perdeu com a pagina. Encerrar e mais honesto do que exibir uma chamada
    // que nao existe mais deste lado.
    api
      .get<CallInfo | null>("/calls/current")
      .then((current) => {
        if (current) {
          void api.post(`/calls/${current.id}/end`).catch(() => {});
        }
      })
      .catch(() => {});
  }, [user]);

  useEffect(() => () => peerRef.current?.close(), []);

  const value = useMemo<CallState>(
    () => ({
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
      start,
      accept,
      reject,
      hangUp,
      toggleMic,
      toggleCamera,
      toggleScreenShare,
      dismissError: () => setError(null),
    }),
    [phase, call, localStream, remoteStream, micEnabled, cameraEnabled,
      sharingScreen, peerSharingScreen, screenStream, error,
      start, accept, reject, hangUp, toggleMic, toggleCamera, toggleScreenShare],
  );

  return <CallContext.Provider value={value}>{children}</CallContext.Provider>;
}

export function useCall(): CallState {
  const context = useContext(CallContext);
  if (!context) {
    throw new Error("useCall precisa estar dentro de CallProvider");
  }
  return context;
}

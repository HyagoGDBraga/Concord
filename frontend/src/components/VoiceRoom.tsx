"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRealtime, useRealtimeEvent, type CallSignal } from "@/lib/realtime";
import { useSession } from "@/lib/session";
import { serversApi, type ServerMember } from "@/lib/chatApi";
import { detectSpeaking, type SpeakingDetector } from "@/lib/audioLevel";
import { fetchIceConfig, PeerConnection } from "@/lib/webrtc";
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
  SpeakerIcon,
} from "@/components/icons";

type RoomState = { channelId: string; participantIds: string[] };
type RoomPresence = { channelId: string; userId: string };
type RoomSignal = {
  channelId: string;
  fromUserId: string;
  type: CallSignal["type"];
  payload: unknown;
};
type RoomUserState = {
  channelId: string;
  userId: string;
  state: { muted: boolean; camera: boolean; screen: boolean };
};

/** Tecla de push-to-talk. */
const PTT_KEY = "Space";

interface PeerState {
  muted: boolean;
  camera: boolean;
  screen: boolean;
}

const ESTADO_PADRAO: PeerState = { muted: false, camera: false, screen: false };

export function VoiceRoom({
  serverId,
  channelId,
}: {
  serverId: string;
  channelId: string;
}) {
  const { user } = useSession();
  const { connected, sendVoicePresence, sendVoiceSignal, sendVoiceState } =
    useRealtime();

  const [joined, setJoined] = useState(false);
  const [participants, setParticipants] = useState<string[]>([]);
  const [members, setMembers] = useState<Map<string, ServerMember>>(new Map());
  const [remoteStreams, setRemoteStreams] = useState<Map<string, MediaStream>>(
    new Map(),
  );
  const [peerStates, setPeerStates] = useState<Map<string, PeerState>>(new Map());
  const [speaking, setSpeaking] = useState<Set<string>>(new Set());

  const [micEnabled, setMicEnabled] = useState(true);
  const [cameraEnabled, setCameraEnabled] = useState(false);
  const [sharingScreen, setSharingScreen] = useState(false);
  const [pushToTalk, setPushToTalk] = useState(false);
  const [pttHeld, setPttHeld] = useState(false);
  const [showSelfPreview, setShowSelfPreview] = useState(false);
  const [focusedUserId, setFocusedUserId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const peersRef = useRef<Map<string, PeerConnection>>(new Map());
  const audioRef = useRef<Map<string, HTMLAudioElement>>(new Map());
  const detectorsRef = useRef<Map<string, SpeakingDetector>>(new Map());
  const localStreamRef = useRef<MediaStream | null>(null);
  const cameraTrackRef = useRef<MediaStreamTrack | null>(null);
  const screenTrackRef = useRef<MediaStreamTrack | null>(null);
  const selfPreviewRef = useRef<HTMLVideoElement>(null);
  const palcoRef = useRef<HTMLDivElement>(null);
  const [fullscreen, setFullscreen] = useState(false);

  /* ------------------------------------------------------------- membros */

  useEffect(() => {
    serversApi
      .members(serverId)
      .then((lista) =>
        setMembers(new Map(lista.map((membro) => [membro.user.id, membro]))),
      )
      .catch(() => {
        // Sem a lista, a sala cai para exibir o inicio do id. Feio, mas
        // funcional — nao vale derrubar a sala por causa do rotulo.
      });
  }, [serverId]);

  const iniciaisDe = useCallback(
    (userId: string) => {
      const nome = members.get(userId);
      const base =
        nome?.nickname ?? nome?.user.displayName ?? nome?.user.username ?? "?";
      // Duas iniciais de palavras diferentes quando houver: "Ana Silva" -> AS.
      const partes = base.trim().split(/\s+/).filter(Boolean);
      const primeira = partes[0]?.[0] ?? "";
      const segunda = partes[1]?.[0] ?? "";
      // Duas palavras -> iniciais de cada uma; uma palavra -> duas letras dela.
      return (segunda ? primeira + segunda : base.slice(0, 2)).toUpperCase();
    },
    [members],
  );

  const nomeDe = useCallback(
    (userId: string) => {
      const membro = members.get(userId);
      // Apelido do servidor > nome de exibicao > @username. O id so aparece se
      // a lista de membros nao tiver carregado.
      return (
        membro?.nickname ??
        membro?.user.displayName ??
        membro?.user.username ??
        `Membro ${userId.slice(0, 6)}`
      );
    },
    [members],
  );

  /* -------------------------------------------------- estado transmitido */

  const publishState = useCallback(
    (estado: Partial<PeerState>) => {
      sendVoiceState(serverId, channelId, {
        muted: estado.muted ?? !micEnabled,
        camera: estado.camera ?? cameraEnabled,
        screen: estado.screen ?? sharingScreen,
      });
    },
    [
      sendVoiceState,
      serverId,
      channelId,
      micEnabled,
      cameraEnabled,
      sharingScreen,
    ],
  );

  /* ---------------------------------------------------------- detectores */

  const watchSpeaking = useCallback((userId: string, stream: MediaStream) => {
    detectorsRef.current.get(userId)?.stop();
    const detector = detectSpeaking(stream, (falando) => {
      setSpeaking((atual) => {
        const proximo = new Set(atual);
        if (falando) {
          proximo.add(userId);
        } else {
          proximo.delete(userId);
        }
        return proximo;
      });
    });
    detectorsRef.current.set(userId, detector);
  }, []);

  const removePeer = useCallback((userId: string) => {
    peersRef.current.get(userId)?.close();
    peersRef.current.delete(userId);
    audioRef.current.get(userId)?.pause();
    audioRef.current.delete(userId);
    detectorsRef.current.get(userId)?.stop();
    detectorsRef.current.delete(userId);

    setRemoteStreams((atual) => {
      const proximo = new Map(atual);
      proximo.delete(userId);
      return proximo;
    });
    setPeerStates((atual) => {
      const proximo = new Map(atual);
      proximo.delete(userId);
      return proximo;
    });
    setSpeaking((atual) => {
      const proximo = new Set(atual);
      proximo.delete(userId);
      return proximo;
    });
    setParticipants((atual) => atual.filter((id) => id !== userId));
    setFocusedUserId((atual) => (atual === userId ? null : atual));
  }, []);

  async function createPeer(userId: string, makeOffer: boolean) {
    if (!user || peersRef.current.has(userId)) {
      return;
    }
    // Educado = id menor. Deterministico e simetrico: os dois lados calculam a
    // mesma resposta, entao exatamente um cede em caso de colisao de ofertas.
    const educado = user.id < userId;

    const peer = new PeerConnection(await fetchIceConfig(), {
      onIceCandidate: (candidate) =>
        sendVoiceSignal(serverId, channelId, userId, "ICE_CANDIDATE", candidate),

      // O navegador decide quando renegociar; aqui so transportamos.
      onDescription: (description) =>
        sendVoiceSignal(
          serverId,
          channelId,
          userId,
          description.type === "offer" ? "OFFER" : "ANSWER",
          description,
        ),

      onRemoteStream: (stream) => {
        console.info(
          "[voz] trilhas recebidas de", userId.slice(0, 8),
          "— áudio:", stream.getAudioTracks().length,
          "vídeo:", stream.getVideoTracks().length,
        );
        setRemoteStreams((atual) => new Map(atual).set(userId, stream));

        // O audio sai por um elemento proprio, separado do <video>. Se
        // dependesse do video, quem so manda audio ficaria mudo — e quem para
        // de compartilhar a tela derrubaria o proprio som junto.
        let audio = audioRef.current.get(userId);
        if (!audio) {
          audio = new Audio();
          audio.autoplay = true;
          audioRef.current.set(userId, audio);
        }
        // So as trilhas de audio: um <audio> com trilha de video anexada
        // mantem o decodificador de video ligado sem exibir nada.
        const somente = new MediaStream(stream.getAudioTracks());
        audio.srcObject = somente;
        audio.play().catch((erro) =>
          console.warn("[voz] play() do áudio falhou", erro),
        );
        watchSpeaking(userId, stream);
      },

      onStateChange: (state) => {
        // Console em vez de tela: e informacao de diagnostico, nao de usuario.
        // Sem isto, "o video nao aparece" nao tem como ser investigado.
        console.info("[voz] conexão com", userId.slice(0, 8), "→", state);
        if (state === "failed" || state === "closed") {
          removePeer(userId);
        }
      },
    }, educado);

    peersRef.current.set(userId, peer);

    if (!localStreamRef.current) {
      localStreamRef.current = await navigator.mediaDevices.getUserMedia({
        audio: true,
        video: false,
      });
    }
    peer.attachLocalStream(localStreamRef.current);

    const videoTrack = screenTrackRef.current ?? cameraTrackRef.current;
    if (videoTrack) {
      peer.attachVideoTrack(videoTrack, localStreamRef.current);
    }

    setParticipants((atual) =>
      atual.includes(userId) ? atual : [...atual, userId],
    );

    // Nao ha createOffer manual: anexar as trilhas acima ja dispara
    // negotiationneeded, e o proprio navegador emite a oferta no momento certo.
    // O parametro makeOffer continua existindo por compatibilidade da
    // assinatura, mas quem oferta agora e sempre o navegador.
    void makeOffer;
    // Quem acabou de entrar precisa saber o que ja esta acontecendo. O atraso
    // curto garante que o outro lado ja processou o JOIN antes de receber o
    // estado — sem ele, o evento chega para uma sala que ainda nao registrou
    // este participante e e descartado.
    window.setTimeout(() => publishState({}), 400);
  }

  /* ------------------------------------------------------------- eventos */

  useRealtimeEvent<RoomState>("VOICE_ROOM_STATE", (event) => {
    if (event.channelId !== channelId || !joined) {
      return;
    }
    for (const participantId of event.participantIds) {
      void createPeer(participantId, true);
    }
  });

  useRealtimeEvent<RoomPresence>("VOICE_USER_JOINED", (event) => {
    if (event.channelId === channelId && event.userId !== user?.id && joined) {
      setParticipants((atual) =>
        atual.includes(event.userId) ? atual : [...atual, event.userId],
      );
    }
  });

  useRealtimeEvent<RoomPresence>("VOICE_USER_LEFT", (event) => {
    if (event.channelId === channelId) {
      removePeer(event.userId);
    }
  });

  useRealtimeEvent<RoomUserState>("VOICE_USER_STATE", (event) => {
    if (event.channelId !== channelId) {
      return;
    }
    setPeerStates((atual) =>
      new Map(atual).set(event.userId, { ...ESTADO_PADRAO, ...event.state }),
    );
  });

  useRealtimeEvent<RoomSignal>("VOICE_SIGNAL", (event) => {
    if (event.channelId !== channelId || !joined) {
      return;
    }
    void (async () => {
      await createPeer(event.fromUserId, false);
      const peer = peersRef.current.get(event.fromUserId);
      if (!peer) {
        return;
      }
      if (event.type === "OFFER" || event.type === "ANSWER") {
        // Um caminho so: applyRemoteDescription resolve colisao e devolve a
        // resposta quando houver o que responder.
        const resposta = await peer.applyRemoteDescription(
          event.payload as RTCSessionDescriptionInit,
        );
        if (resposta) {
          sendVoiceSignal(
            serverId,
            channelId,
            event.fromUserId,
            "ANSWER",
            resposta,
          );
        }
      } else if (event.type === "ICE_CANDIDATE") {
        await peer.addIceCandidate(event.payload as RTCIceCandidateInit);
      }
    })().catch((erro) => {
      // A mensagem generica escondia a causa. Falha de negociacao ("Called in
      // wrong state", SDP incompativel) so aparece aqui.
      console.error("[voz] falha ao processar sinal de", event.fromUserId, erro);
      setError("Não foi possível conectar o áudio da sala.");
    });
  });

  /* ------------------------------------------------------ entrada e saída */

  useEffect(() => {
    if (!joined || !connected) {
      return;
    }
    sendVoicePresence(serverId, channelId, true);

    // Copias locais das refs. Na limpeza, `ref.current` pode ja apontar para
    // outra coisa — o React avisa sobre isso justamente porque leva a fechar a
    // conexao errada quando o usuario troca de canal rapido.
    const peers = peersRef.current;
    const detectors = detectorsRef.current;
    const audios = audioRef.current;

    return () => {
      sendVoicePresence(serverId, channelId, false);
      peers.forEach((peer) => peer.close());
      peers.clear();
      detectors.forEach((detector) => detector.stop());
      detectors.clear();
      localStreamRef.current?.getTracks().forEach((track) => track.stop());
      localStreamRef.current = null;
      cameraTrackRef.current = null;
      screenTrackRef.current = null;
      audios.forEach((audio) => audio.pause());
      audios.clear();
      setRemoteStreams(new Map());
      setPeerStates(new Map());
      setSpeaking(new Set());
      setParticipants([]);
      setFocusedUserId(null);
    };
  }, [joined, connected, serverId, channelId, sendVoicePresence]);

  /* ------------------------------------------------------- push to talk */

  const applyMicState = useCallback((ativo: boolean) => {
    localStreamRef.current?.getAudioTracks().forEach((track) => {
      track.enabled = ativo;
    });
  }, []);

  useEffect(() => {
    if (!joined || !pushToTalk) {
      return;
    }
    // Ao ligar o modo, o microfone comeca fechado.
    applyMicState(false);
    setMicEnabled(false);
    publishState({ muted: true });

    function pressionou(evento: KeyboardEvent) {
      // Ignora quando o foco esta num campo de texto: senao a barra de espaco
      // no meio de uma mensagem abriria o microfone.
      const alvo = evento.target as HTMLElement | null;
      if (
        alvo &&
        (alvo.tagName === "INPUT" ||
          alvo.tagName === "TEXTAREA" ||
          alvo.isContentEditable)
      ) {
        return;
      }
      if (evento.code !== PTT_KEY || evento.repeat) {
        return;
      }
      evento.preventDefault();
      setPttHeld(true);
      applyMicState(true);
      setMicEnabled(true);
      publishState({ muted: false });
    }

    function soltou(evento: KeyboardEvent) {
      if (evento.code !== PTT_KEY) {
        return;
      }
      evento.preventDefault();
      setPttHeld(false);
      applyMicState(false);
      setMicEnabled(false);
      publishState({ muted: true });
    }

    // A janela pode perder o foco com a tecla pressionada — sem isto, o
    // microfone ficaria aberto sem ninguem perceber.
    function perdeuFoco() {
      setPttHeld(false);
      applyMicState(false);
      setMicEnabled(false);
    }

    window.addEventListener("keydown", pressionou);
    window.addEventListener("keyup", soltou);
    window.addEventListener("blur", perdeuFoco);

    return () => {
      window.removeEventListener("keydown", pressionou);
      window.removeEventListener("keyup", soltou);
      window.removeEventListener("blur", perdeuFoco);
      applyMicState(true);
      setMicEnabled(true);
      setPttHeld(false);
    };
  }, [joined, pushToTalk, applyMicState, publishState]);

  /* ---------------------------------------- fala do proprio microfone */

  useEffect(() => {
    if (!joined || !user) {
      return;
    }
    const stream = localStreamRef.current;
    if (!stream) {
      return;
    }
    const meuId = user.id;
    const detectors = detectorsRef.current;
    watchSpeaking(meuId, stream);
    return () => {
      detectors.get(meuId)?.stop();
      detectors.delete(meuId);
    };
  }, [joined, user, watchSpeaking]);

  /* -------------------------------------------------------- previa local */

  useEffect(() => {
    if (!selfPreviewRef.current) {
      return;
    }
    const trilha = screenTrackRef.current ?? cameraTrackRef.current;
    selfPreviewRef.current.srcObject =
      showSelfPreview && trilha ? new MediaStream([trilha]) : null;
  }, [showSelfPreview, sharingScreen, cameraEnabled]);

  /* ------------------------------------------------------- tela cheia */

  useEffect(() => {
    // O usuario pode sair da tela cheia pelo Esc ou pelo botao do navegador —
    // sem escutar o evento, o botao continuaria dizendo "sair" para sempre.
    function sincronizar() {
      setFullscreen(document.fullscreenElement === palcoRef.current);
    }
    document.addEventListener("fullscreenchange", sincronizar);
    return () => document.removeEventListener("fullscreenchange", sincronizar);
  }, []);

  async function alternarTelaCheia() {
    const palco = palcoRef.current;
    if (!palco) {
      return;
    }
    try {
      if (document.fullscreenElement) {
        await document.exitFullscreen();
      } else {
        await palco.requestFullscreen();
      }
    } catch {
      setError("O navegador não permitiu a tela cheia.");
    }
  }

  /* ------------------------------------------------------------ controles */

  async function toggle() {
    setError(null);
    if (!joined) {
      try {
        localStreamRef.current = await navigator.mediaDevices.getUserMedia({
          audio: true,
          video: false,
        });
        setJoined(true);
      } catch {
        setError("Permita o microfone para entrar na sala de voz.");
      }
    } else {
      setJoined(false);
    }
  }

  /**
   * Renegociacao manual foi removida.
   *
   * Acrescentar ou trocar trilha dispara `negotiationneeded` no navegador, que
   * emite a oferta no momento certo e no estado certo. Chamar createOffer na
   * mao — como antes — podia acontecer com uma negociacao em andamento e falhar
   * silenciosamente, que era a causa de "compartilhei e nao apareceu nada".
   *
   * A funcao continua como no-op para nao espalhar a mudanca por todas as
   * chamadas.
   */
  function renegotiate() {
    // O navegador cuida. Ver PeerConnection#onnegotiationneeded.
  }

  function toggleMic() {
    if (pushToTalk) {
      return;
    }
    const proximo = !micEnabled;
    applyMicState(proximo);
    setMicEnabled(proximo);
    publishState({ muted: !proximo });
  }

  async function toggleCamera() {
    if (!joined) {
      return;
    }
    try {
      if (!cameraEnabled) {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: true,
          audio: false,
        });
        const track = stream.getVideoTracks()[0];
        if (!track || !localStreamRef.current) {
          return;
        }
        cameraTrackRef.current = track;
        localStreamRef.current.addTrack(track);
        for (const peer of peersRef.current.values()) {
          if (!peer.replaceVideoTrack(track)) {
            peer.attachVideoTrack(track, localStreamRef.current);
          }
        }
        setCameraEnabled(true);
        publishState({ camera: true });
        renegotiate();
        return;
      }
      cameraTrackRef.current?.stop();
      cameraTrackRef.current = null;
      for (const peer of peersRef.current.values()) {
        peer.replaceVideoTrack(screenTrackRef.current);
      }
      setCameraEnabled(false);
      publishState({ camera: false });
      renegotiate();
    } catch {
      setError("Não foi possível acessar a câmera.");
    }
  }

  const pararTela = useCallback(() => {
    screenTrackRef.current?.stop();
    screenTrackRef.current = null;
    for (const peer of peersRef.current.values()) {
      peer.replaceVideoTrack(cameraTrackRef.current);
    }
    setSharingScreen(false);
    setShowSelfPreview(false);
    publishState({ screen: false });
    renegotiate();
  }, [publishState]);

  async function toggleScreenShare() {
    if (!joined) {
      return;
    }
    if (sharingScreen) {
      pararTela();
      return;
    }
    try {
      const display = await capturarTela();
      const track = display.getVideoTracks()[0];
      if (!track || !localStreamRef.current) {
        return;
      }
      if ("contentHint" in track) {
        track.contentHint = "detail";
      }
      screenTrackRef.current = track;
      track.addEventListener("ended", () => pararTela());

      localStreamRef.current.addTrack(track);
      for (const peer of peersRef.current.values()) {
        if (!peer.replaceVideoTrack(track)) {
          peer.attachVideoTrack(track, localStreamRef.current);
        }
      }
      setSharingScreen(true);
      publishState({ screen: true });
      renegotiate();
    } catch (erro) {
      // Cancelar o seletor do navegador nao e falha.
      if (
        erro instanceof DOMException &&
        (erro.name === "AbortError" || erro.name === "NotAllowedError")
      ) {
        return;
      }
      // Mensagem generica escondia a causa. Agora o nome do erro aparece: e a
      // diferenca entre "o navegador negou", "nao ha tela" e "restricao
      // impossivel".
      const detalhe = erro instanceof DOMException ? ` (${erro.name})` : "";
      setError(`Não foi possível compartilhar a tela${detalhe}.`);
    }
  }

  /**
   * Captura a tela com degradacao progressiva.
   *
   * As opcoes que evitam o espelho infinito — `selfBrowserSurface` e
   * `surfaceSwitching` — sao do Chromium. O Firefox nao as conhece, e trata
   * `displaySurface` como restricao obrigatoria em vez de preferencia: pedir
   * "window" ali faz a chamada FALHAR em vez de apenas sugerir a aba.
   *
   * Por isso a tentativa vai do mais especifico ao mais simples, em vez de
   * detectar navegador — detectar navegador envelhece mal.
   */
  async function capturarTela(): Promise<MediaStream> {
    const tentativas: DisplayMediaStreamOptions[] = [
      {
        video: { frameRate: { ideal: 15, max: 30 }, displaySurface: "window" },
        audio: false,
        selfBrowserSurface: "exclude",
        surfaceSwitching: "include",
      } as DisplayMediaStreamOptions,
      { video: { frameRate: { ideal: 15, max: 30 } }, audio: false },
      { video: true, audio: false },
    ];

    let ultimoErro: unknown = null;
    for (const opcoes of tentativas) {
      try {
        return await navigator.mediaDevices.getDisplayMedia(opcoes);
      } catch (erro) {
        // Desistencia do usuario nao deve escalar para a proxima tentativa:
        // abriria o seletor de novo, o que pareceria um bug.
        if (
          erro instanceof DOMException &&
          (erro.name === "AbortError" || erro.name === "NotAllowedError")
        ) {
          throw erro;
        }
        ultimoErro = erro;
      }
    }
    throw ultimoErro ?? new Error("getDisplayMedia indisponível");
  }

  /* ------------------------------------------------------------------ UI */

  const eu = user?.id;

  /**
   * Stream contendo APENAS video, para o elemento <video>.
   *
   * O stream recebido do WebRTC traz audio e video juntos. Entregar isso a um
   * <video> nao silenciado faz o navegador bloquear o autoplay: play() e
   * rejeitado, nenhum quadro e pintado, e o resultado e um retangulo preto com
   * a trilha de video presente — exatamente o que o diagnostico mostrava
   * ("video 1" e tela preta).
   *
   * O audio continua saindo pelo elemento <audio> dedicado de cada
   * participante, que ja existia.
   */
  const videoOnly = useMemo(() => {
    const mapa = new Map<string, MediaStream>();
    for (const [userId, stream] of remoteStreams) {
      const trilhas = stream.getVideoTracks();
      if (trilhas.length > 0) {
        mapa.set(userId, new MediaStream(trilhas));
      }
    }
    return mapa;
  }, [remoteStreams]);

  const emDestaque = focusedUserId ? videoOnly.get(focusedUserId) : null;

  const comVideo = useMemo(() => Array.from(videoOnly.entries()), [videoOnly]);

  return (
    <section className="rounded-lg border border-line bg-panel p-5">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="eyebrow">Sala de voz</p>
          <strong className="display text-sm">
            {joined ? "Você está na sala" : "Áudio entre membros"}
          </strong>
          {joined && (
            <span className="ml-2 font-mono text-xs text-muted">
              {participants.length + 1} conectado(s)
            </span>
          )}
        </div>

        {!joined && (
          <button
            type="button"
            onClick={() => void toggle()}
            className="flex items-center gap-2 rounded bg-mint px-4 py-2 text-sm font-semibold text-ink transition hover:brightness-110"
          >
            <SpeakerIcon size={16} />
            Entrar na voz
          </button>
        )}
      </header>

      {/* --------------------------------------------------- palco de vídeo */}
      {comVideo.length > 0 && (
        <div className="mt-5 space-y-3">
          {emDestaque && focusedUserId && (
            <div
              ref={palcoRef}
              className={`relative overflow-hidden rounded border border-amber/50 bg-ink ${
                fullscreen ? "flex h-screen w-screen items-center justify-center" : ""
              }`}
            >
              <video
                autoPlay
                playsInline
                // muted e obrigatorio: sem ele o navegador bloqueia o autoplay
                // e o video fica preto. O som vem do <audio> do participante.
                muted
                ref={(el) => {
                  if (el && el.srcObject !== emDestaque) {
                    el.srcObject = emDestaque;
                    el.play().catch((erro) =>
                      console.warn("[voz] play() do vídeo em destaque falhou", erro),
                    );
                  }
                }}
                // object-contain: tela compartilhada é texto. Recortar as
                // bordas esconde barra de menu e abas, que é o que a pessoa
                // quer mostrar.
                className={
                  fullscreen
                    ? "h-full w-full bg-ink object-contain"
                    : "max-h-[65vh] w-full bg-ink object-contain"
                }
              />
              <div className="absolute left-2 top-2 rounded bg-ink/85 px-2 py-1 font-mono text-xs">
                {nomeDe(focusedUserId)}
                {peerStates.get(focusedUserId)?.screen && " · tela"}
              </div>
              <div className="absolute right-2 top-2 flex gap-1">
                <button
                  type="button"
                  onClick={() => void alternarTelaCheia()}
                  className="rounded bg-ink/85 p-2 hover:text-amber"
                  title={fullscreen ? "Sair da tela cheia" : "Tela cheia"}
                  aria-label={fullscreen ? "Sair da tela cheia" : "Tela cheia"}
                >
                  {fullscreen ? <MinimizeIcon /> : <MaximizeIcon />}
                </button>
                <button
                  type="button"
                  onClick={() => setFocusedUserId(null)}
                  className="rounded bg-ink/85 p-2 hover:text-amber"
                  title="Fechar destaque"
                  aria-label="Fechar destaque"
                >
                  <CloseIcon />
                </button>
              </div>
            </div>
          )}

          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {comVideo
              .filter(([userId]) => userId !== focusedUserId)
              .map(([userId, stream]) => (
                <button
                  key={userId}
                  type="button"
                  onClick={() => setFocusedUserId(userId)}
                  className="relative overflow-hidden rounded border border-line bg-ink"
                  title={`Ampliar ${nomeDe(userId)}`}
                >
                  <video
                    autoPlay
                    playsInline
                    muted
                    ref={(el) => {
                      if (el && el.srcObject !== stream) {
                        el.srcObject = stream;
                        el.play().catch((erro) =>
                          console.warn("[voz] play() da miniatura falhou", erro),
                        );
                      }
                    }}
                    className="aspect-video w-full bg-ink object-contain"
                  />
                  <span className="absolute bottom-1 left-1 rounded bg-ink/85 px-1.5 py-0.5 font-mono text-[11px]">
                    {nomeDe(userId)}
                    {peerStates.get(userId)?.screen && " · tela"}
                  </span>
                </button>
              ))}
          </div>
        </div>
      )}

      {/* ----------------------------------------------------- participantes */}
      {joined && (
        <ul className="mt-5 space-y-2">
          {[eu, ...participants].filter(Boolean).map((userId) => {
            const id = userId as string;
            const estado =
              id === eu
                ? {
                    muted: !micEnabled,
                    camera: cameraEnabled,
                    screen: sharingScreen,
                  }
                : (peerStates.get(id) ?? ESTADO_PADRAO);
            const falando = speaking.has(id) && !estado.muted;

            return (
              <li key={id} className="flex items-center gap-3">
                {/* Anel de fala em volta do avatar — o sinal que faltava. */}
                <span
                  className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-elevated font-mono text-sm ${
                    falando ? "speaking-ring" : "speaking-ring-idle"
                  }`}
                >
                  {iniciaisDe(id)}
                </span>

                <span className="min-w-0 flex-1 truncate text-sm">
                  {nomeDe(id)}
                  {id === eu && <span className="ml-1 text-muted">(você)</span>}
                </span>

                <span className="flex items-center gap-2 font-mono text-[11px] text-muted">
                  {estado.muted && <span title="Microfone desligado">mudo</span>}
                  {estado.camera && <span title="Câmera ligada">cam</span>}
                  {estado.screen && (
                    <span className="text-amber" title="Compartilhando a tela">
                      ● tela
                    </span>
                  )}
                </span>
              </li>
            );
          })}
        </ul>
      )}

      {/* --------------------------------------------------------- controles */}
      {joined && (
        <div className="mt-5 space-y-3 border-t border-line pt-4">
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={toggleMic}
              disabled={pushToTalk}
              className={`icon-button ${micEnabled ? "" : "is-off"}`}
              title={
                pushToTalk
                  ? "Push-to-talk ativo: segure a barra de espaço"
                  : micEnabled
                    ? "Silenciar microfone"
                    : "Ativar microfone"
              }
              aria-label={micEnabled ? "Silenciar microfone" : "Ativar microfone"}
              aria-pressed={!micEnabled}
            >
              {micEnabled ? <MicIcon /> : <MicOffIcon />}
            </button>

            <button
              type="button"
              onClick={() => void toggleCamera()}
              className={`icon-button ${cameraEnabled ? "is-on" : ""}`}
              title={cameraEnabled ? "Desligar a câmera" : "Ligar a câmera"}
              aria-label={cameraEnabled ? "Desligar a câmera" : "Ligar a câmera"}
              aria-pressed={cameraEnabled}
            >
              {cameraEnabled ? <CameraIcon /> : <CameraOffIcon />}
            </button>

            <button
              type="button"
              onClick={() => void toggleScreenShare()}
              className={`icon-button ${sharingScreen ? "is-on" : ""}`}
              title={sharingScreen ? "Parar de compartilhar" : "Compartilhar tela"}
              aria-label={
                sharingScreen ? "Parar de compartilhar" : "Compartilhar tela"
              }
              aria-pressed={sharingScreen}
            >
              {sharingScreen ? <ScreenShareOffIcon /> : <ScreenShareIcon />}
            </button>

            <button
              type="button"
              onClick={() => setPushToTalk((atual) => !atual)}
              className={`icon-button ${pushToTalk ? "is-on" : ""} ${
                pttHeld ? "ptt-active" : ""
              }`}
              title="Push-to-talk: fala só enquanto a barra de espaço estiver pressionada"
              aria-label="Alternar push-to-talk"
              aria-pressed={pushToTalk}
            >
              <PushToTalkIcon />
            </button>

            <span className="flex-1" />

            <button
              type="button"
              onClick={() => void toggle()}
              className="icon-button is-danger"
              title="Sair da sala de voz"
              aria-label="Sair da sala de voz"
            >
              <PhoneOffIcon />
            </button>
          </div>

          {pushToTalk && (
            <p className="font-mono text-xs text-muted">
              Push-to-talk ligado — segure{" "}
              <kbd className="rounded border border-line px-1">espaço</kbd> para
              falar.
              {pttHeld && <span className="ml-2 text-mint">transmitindo</span>}
            </p>
          )}

          {sharingScreen && (
            <div className="rounded border border-amber/40 bg-ink/60 p-3">
              <p className="font-mono text-xs text-amber">
                ● Você está compartilhando a tela
              </p>
              <p className="mt-1 text-xs text-muted">
                Prefira compartilhar uma <strong>janela</strong> específica. Se
                escolher o monitor inteiro e o Concord estiver nele, quem assiste
                vê o espelho infinito — tela preta com dezenas de cursores. Não é
                falha da conexão: é a tela se filmando.
              </p>
              <button
                type="button"
                onClick={() => setShowSelfPreview((atual) => !atual)}
                className="mt-2 font-mono text-xs text-muted underline hover:text-paper"
              >
                {showSelfPreview ? "Ocultar prévia" : "Ver prévia mesmo assim"}
              </button>
              {showSelfPreview && (
                <video
                  ref={selfPreviewRef}
                  autoPlay
                  playsInline
                  muted
                  className="mt-2 aspect-video w-40 rounded border border-line bg-ink object-contain"
                />
              )}
            </div>
          )}
        </div>
      )}

      {error && (
        <p className="mt-4 text-sm text-coral" role="alert">
          {error}
        </p>
      )}

      {/* Linha de diagnostico. Mostra o que a interface REALMENTE recebeu, que
          e diferente do que o outro lado diz estar enviando. Quando alguem
          aparece como "● tela" mas o video nao surge, a diferenca entre as duas
          informacoes e a pista. */}
      {joined && participants.length > 0 && (
        <details className="mt-4">
          <summary className="cursor-pointer font-mono text-[11px] text-muted">
            diagnóstico da sala
          </summary>
          <ul className="mt-2 space-y-1 font-mono text-[11px] text-muted">
            {participants.map((id) => {
              const stream = remoteStreams.get(id);
              const estado = peerStates.get(id) ?? ESTADO_PADRAO;
              return (
                <li key={id}>
                  {nomeDe(id)} — recebendo: áudio{" "}
                  {stream?.getAudioTracks().length ?? 0}, vídeo{" "}
                  {stream?.getVideoTracks().length ?? 0}
                  {(() => {
                    const trilha = stream?.getVideoTracks()[0];
                    if (!trilha) {
                      return null;
                    }
                    // "muted" numa trilha remota significa que ela existe mas
                    // nao esta entregando quadros — a pista mais util quando o
                    // video aparece preto.
                    return ` (${trilha.readyState}${
                      trilha.muted ? ", sem quadros" : ""
                    })`;
                  })()}{" "}
                  · anunciado:{" "}
                  {estado.screen ? "tela" : estado.camera ? "câmera" : "só áudio"}
                </li>
              );
            })}
          </ul>
        </details>
      )}
    </section>
  );
}

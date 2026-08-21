"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useRealtime, useRealtimeEvent, type CallSignal } from "@/lib/realtime";
import { useSession } from "@/lib/session";
import { serversApi, type ServerMember } from "@/lib/chatApi";
import { detectSpeaking, type SpeakingDetector } from "@/lib/audioLevel";
import { fetchIceConfig, PeerConnection } from "@/lib/webrtc";
import { useVoiceChannel } from "@/lib/voiceChannel";
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


/** O que as telas de voz podem ler e comandar. */
interface VoiceSessionState {
  ativo: boolean;
  serverId: string;
  channelId: string;
  joined: boolean;
  participants: string[];
  members: Map<string, ServerMember>;
  remoteStreams: Map<string, MediaStream>;
  peerStates: Map<string, PeerState>;
  speaking: Set<string>;
  micEnabled: boolean;
  cameraEnabled: boolean;
  sharingScreen: boolean;
  pushToTalk: boolean;
  pttHeld: boolean;
  pttKey: string;
  capturandoTecla: boolean;
  focusedUserId: string | null;
  error: string | null;
  nomeDe: (userId: string) => string;
  iniciaisDe: (userId: string) => string;
  toggleMic: () => void;
  toggleCamera: () => Promise<void>;
  toggleScreenShare: () => Promise<void>;
  setPushToTalk: (valor: boolean | ((atual: boolean) => boolean)) => void;
  setCapturandoTecla: (valor: boolean) => void;
  setFocusedUserId: (userId: string | null) => void;
  setError: (mensagem: string | null) => void;
  sair: () => void;
}

const VoiceSessionContext = createContext<VoiceSessionState | null>(null);

/** Tecla padrao do push-to-talk, usada ate o usuario escolher outra. */
const PTT_KEY_PADRAO = "Space";

const PTT_STORAGE_KEY = "concord.ptt.key";

/**
 * Nome legivel de um KeyboardEvent.code.
 *
 * O `code` identifica a POSICAO fisica da tecla, nao o caractere — em teclado
 * ABNT2 e US a mesma tecla produz o mesmo code. Por isso ele e guardado, e nao
 * o `key`. Mas "ControlLeft" nao serve para exibir.
 */
function nomeDaTecla(code: string): string {
  if (code === "Space") return "Espaço";
  if (code.startsWith("Key")) return code.slice(3);
  if (code.startsWith("Digit")) return code.slice(5);
  if (code.startsWith("Numpad")) return "Num " + code.slice(6);
  if (code === "ControlLeft") return "Ctrl esq.";
  if (code === "ControlRight") return "Ctrl dir.";
  if (code === "ShiftLeft") return "Shift esq.";
  if (code === "ShiftRight") return "Shift dir.";
  if (code === "AltLeft") return "Alt esq.";
  if (code === "AltRight") return "Alt dir.";
  return code;
}

interface PeerState {
  muted: boolean;
  camera: boolean;
  screen: boolean;
}

const ESTADO_PADRAO: PeerState = { muted: false, camera: false, screen: false };

/**
 * Sessao de voz do aplicativo.
 *
 * Aqui vive a CONEXAO — pares, trilhas, sinalizacao, deteccao de fala. Nenhuma
 * tela hospeda isso, e por dois motivos:
 *
 *   1. navegar nao pode derrubar a chamada, e um componente de pagina e
 *      desmontado ao sair dela;
 *   2. a mesma sessao precisa aparecer em DOIS lugares ao mesmo tempo — o
 *      palco, no centro do canal, e a barra compacta na lateral. Duas telas,
 *      uma conexao.
 */
export function VoiceSessionProvider({ children }: { children: React.ReactNode }) {
  const { ativo, sair: sairDoCanal } = useVoiceChannel();
  const serverId = ativo?.serverId ?? "";
  const channelId = ativo?.channelId ?? "";
  const autoJoin = ativo !== null;
  const onLeave = sairDoCanal;
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
  const [pttKey, setPttKey] = useState(PTT_KEY_PADRAO);
  /** Verdadeiro enquanto espera o usuario pressionar a nova tecla. */
  const [capturandoTecla, setCapturandoTecla] = useState(false);
  const [focusedUserId, setFocusedUserId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const peersRef = useRef<Map<string, PeerConnection>>(new Map());
  /**
   * Criacoes em andamento.
   *
   * A guarda antiga (`peersRef.has(userId)`) rodava ANTES do await que busca as
   * credenciais de ICE. Dois eventos quase simultaneos — VOICE_ROOM_STATE e
   * VOICE_SIGNAL — passavam os dois pela guarda, criavam DUAS conexoes para a
   * mesma pessoa, e a segunda sobrescrevia a primeira no mapa. A conexao orfa
   * ficava com as trilhas, e os sinais seguintes iam para o objeto errado: audio
   * que as vezes sai e na maioria das vezes nao.
   *
   * Guardando a promessa de forma sincrona, a segunda chamada espera a mesma
   * criacao em vez de comecar outra.
   */
  const criandoRef = useRef<Map<string, Promise<void>>>(new Map());
  /** Credenciais de ICE reaproveitadas entre pares da mesma sala. */
  const iceRef = useRef<RTCIceServer[] | null>(null);
  /** Sinais que chegaram antes de a conexao existir. */
  const filaDeSinaisRef = useRef<Map<string, RoomSignal[]>>(new Map());
  const audioRef = useRef<Map<string, HTMLAudioElement>>(new Map());
  const detectorsRef = useRef<Map<string, SpeakingDetector>>(new Map());
  const localStreamRef = useRef<MediaStream | null>(null);
  const cameraTrackRef = useRef<MediaStreamTrack | null>(null);
  const screenTrackRef = useRef<MediaStreamTrack | null>(null);
  const [fullscreen, setFullscreen] = useState(false);

  /* --------------------------------------------------- tecla do push-to-talk */

  useEffect(() => {
    const salva = window.localStorage.getItem(PTT_STORAGE_KEY);
    if (salva) {
      setPttKey(salva);
    }
  }, []);

  // Captura a proxima tecla pressionada e a adota como atalho.
  useEffect(() => {
    if (!capturandoTecla) {
      return;
    }
    function capturar(evento: KeyboardEvent) {
      evento.preventDefault();
      // Escape cancela sem trocar nada.
      if (evento.code !== "Escape") {
        setPttKey(evento.code);
        window.localStorage.setItem(PTT_STORAGE_KEY, evento.code);
      }
      setCapturandoTecla(false);
    }
    window.addEventListener("keydown", capturar, { capture: true });
    return () => window.removeEventListener("keydown", capturar, { capture: true });
  }, [capturandoTecla]);

  /* --------------------------------------------------- entrada automatica */

  useEffect(() => {
    if (!autoJoin || joined) {
      return;
    }
    // Pede o microfone e entra. Se a permissao for negada, a mensagem aparece
    // e o painel continua aberto para nova tentativa.
    navigator.mediaDevices
      .getUserMedia({ audio: true, video: false })
      .then((stream) => {
        localStreamRef.current = stream;
        setJoined(true);
      })
      .catch(() =>
        setError("Permita o microfone para entrar na sala de voz."),
      );
    // Roda uma vez por canal: as dependencias mudam quando o canal muda, e ai
    // entrar de novo e o comportamento correto.
  }, [autoJoin, channelId, joined]);

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
    criandoRef.current.delete(userId);
    filaDeSinaisRef.current.delete(userId);
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

  /**
   * Garante UMA conexao por participante, mesmo com chamadas concorrentes.
   */
  function createPeer(userId: string, makeOffer: boolean): Promise<void> {
    const jaExiste = peersRef.current.get(userId);
    if (jaExiste) {
      return Promise.resolve();
    }
    const emAndamento = criandoRef.current.get(userId);
    if (emAndamento) {
      return emAndamento;
    }
    const promessa = criarConexao(userId, makeOffer).finally(() => {
      criandoRef.current.delete(userId);
    });
    // Registro SINCRONO: qualquer chamada posterior encontra a promessa antes
    // de qualquer await acontecer.
    criandoRef.current.set(userId, promessa);
    return promessa;
  }

  async function criarConexao(userId: string, makeOffer: boolean) {
    if (!user) {
      return;
    }
    // Educado = id menor. Deterministico e simetrico: os dois lados calculam a
    // mesma resposta, entao exatamente um cede em caso de colisao de ofertas.
    const educado = user.id < userId;

    // As credenciais valem uma hora e servem para todos os pares da sala; uma
    // requisicao por participante era desperdicio e aumentava a janela de corrida.
    if (!iceRef.current) {
      iceRef.current = await fetchIceConfig();
    }

    const peer = new PeerConnection(iceRef.current, {
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

    // Sinais que chegaram enquanto a conexao era montada.
    const pendentes = filaDeSinaisRef.current.get(userId);
    if (pendentes?.length) {
      filaDeSinaisRef.current.delete(userId);
      for (const sinal of pendentes) {
        await aplicarSinal(peer, userId, sinal).catch((erro) =>
          console.error("[voz] sinal pendente falhou", erro),
        );
      }
    }

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

  // O servidor agora diz por que recusou, em vez de descartar em silencio.
  useRealtimeEvent<{ channelId: string; reason: string; message: string }>(
    "VOICE_ERROR",
    (event) => {
      if (event.channelId !== channelId) {
        return;
      }
      console.error("[voz] recusado pelo servidor:", event.reason);
      setError(event.message);
      // Sai do estado "entrando": o botao volta a "Entrar na voz" em vez de
      // ficar preso em "Sair da sala" sem conexao nenhuma.
      setJoined(false);
    },
  );

  useRealtimeEvent<RoomUserState>("VOICE_USER_STATE", (event) => {
    if (event.channelId !== channelId) {
      return;
    }
    setPeerStates((atual) =>
      new Map(atual).set(event.userId, { ...ESTADO_PADRAO, ...event.state }),
    );
  });

  /** Aplica um sinal a uma conexao ja existente. */
  async function aplicarSinal(
    peer: PeerConnection,
    userId: string,
    sinal: RoomSignal,
  ) {
    if (sinal.type === "OFFER" || sinal.type === "ANSWER") {
      const resposta = await peer.applyRemoteDescription(
        sinal.payload as RTCSessionDescriptionInit,
      );
      if (resposta) {
        sendVoiceSignal(serverId, channelId, userId, "ANSWER", resposta);
      }
    } else if (sinal.type === "ICE_CANDIDATE") {
      await peer.addIceCandidate(sinal.payload as RTCIceCandidateInit);
    }
  }

  useRealtimeEvent<RoomSignal>("VOICE_SIGNAL", (event) => {
    if (event.channelId !== channelId || !joined) {
      return;
    }

    const existente = peersRef.current.get(event.fromUserId);
    if (existente) {
      void aplicarSinal(existente, event.fromUserId, event).catch((erro) => {
        console.error("[voz] falha ao aplicar sinal de", event.fromUserId, erro);
        // Falha de um sinal isolado nao derruba a sala: um candidato ICE
        // recusado e comum e inofensivo. Antes, qualquer erro aqui pintava
        // "Nao foi possivel conectar o audio" na tela, mesmo com a conexao
        // funcionando.
      });
      return;
    }

    // A conexao ainda nao existe. Enfileira o sinal em vez de descarta-lo — o
    // primeiro OFFER costuma chegar exatamente nessa janela, e perde-lo
    // significava uma sala muda ate alguem sair e voltar.
    const fila = filaDeSinaisRef.current.get(event.fromUserId) ?? [];
    fila.push(event);
    filaDeSinaisRef.current.set(event.fromUserId, fila);

    void createPeer(event.fromUserId, false).catch((erro) => {
      console.error("[voz] falha ao criar conexão com", event.fromUserId, erro);
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
      if (evento.code !== pttKey || evento.repeat) {
        return;
      }
      evento.preventDefault();
      setPttHeld(true);
      applyMicState(true);
      setMicEnabled(true);
      publishState({ muted: false });
    }

    function soltou(evento: KeyboardEvent) {
      if (evento.code !== pttKey) {
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
  }, [joined, pushToTalk, pttKey, applyMicState, publishState]);

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
      onLeave?.();
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
      const camera = cameraTrackRef.current;
      cameraTrackRef.current = null;
      if (camera) {
        camera.stop();
        localStreamRef.current?.removeTrack(camera);
      }
      for (const peer of peersRef.current.values()) {
        if (screenTrackRef.current) {
          peer.replaceVideoTrack(screenTrackRef.current);
        } else {
          peer.stopVideo();
        }
      }
      setCameraEnabled(false);
      publishState({ camera: false });
      renegotiate();
    } catch {
      setError("Não foi possível acessar a câmera.");
    }
  }

  const pararTela = useCallback(() => {
    const trilha = screenTrackRef.current;
    screenTrackRef.current = null;

    if (trilha) {
      trilha.stop();
      // A trilha morta continuaria no stream local, e a proxima captura
      // acumularia mais uma em cima.
      localStreamRef.current?.removeTrack(trilha);
    }

    for (const peer of peersRef.current.values()) {
      if (cameraTrackRef.current) {
        // Ha camera: substitui, sem renegociar.
        peer.replaceVideoTrack(cameraTrackRef.current);
      } else {
        // Nao ha nada para enviar. removeTrack encerra a trilha do outro lado,
        // que e o que faz o retangulo preto sumir da tela dele.
        peer.stopVideo();
      }
    }
    setSharingScreen(false);
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
      // Trilha "muted" existe mas nao entrega quadros — e o que sobra quando o
      // outro lado faz replaceTrack(null). Exibi-la produz o retangulo preto.
      const trilhas = stream
        .getVideoTracks()
        .filter((trilha) => trilha.readyState === "live" && !trilha.muted);
      if (trilhas.length > 0) {
        mapa.set(userId, new MediaStream(trilhas));
      }
    }
    return mapa;
  }, [remoteStreams]);

  const emDestaque = focusedUserId ? videoOnly.get(focusedUserId) : null;

  const comVideo = useMemo(() => Array.from(videoOnly.entries()), [videoOnly]);


  /* --------------------------------------------------------- publicacao */

  const valor = useMemo<VoiceSessionState>(
    () => ({
      ativo: ativo !== null,
      serverId,
      channelId,
      joined,
      participants,
      members,
      remoteStreams,
      peerStates,
      speaking,
      micEnabled,
      cameraEnabled,
      sharingScreen,
      pushToTalk,
      pttHeld,
      pttKey,
      capturandoTecla,
      focusedUserId,
      error,
      nomeDe,
      iniciaisDe,
      toggleMic,
      toggleCamera,
      toggleScreenShare,
      setPushToTalk,
      setCapturandoTecla,
      setFocusedUserId,
      setError,
      sair: () => {
        setJoined(false);
        onLeave?.();
      },
    }),
    [
      ativo, serverId, channelId, joined, participants, members, remoteStreams,
      peerStates, speaking, micEnabled, cameraEnabled, sharingScreen, pushToTalk,
      pttHeld, pttKey, capturandoTecla, focusedUserId, error, nomeDe, iniciaisDe,
      onLeave,
    ],
  );

  return (
    <VoiceSessionContext.Provider value={valor}>
      {children}
    </VoiceSessionContext.Provider>
  );
}

export function useVoiceSession(): VoiceSessionState {
  const context = useContext(VoiceSessionContext);
  if (!context) {
    throw new Error("useVoiceSession precisa estar dentro de VoiceSessionProvider");
  }
  return context;
}

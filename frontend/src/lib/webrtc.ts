/**
 * Conexao par a par.
 *
 * O servidor nao ve midia: audio e video vao direto entre os navegadores, ou
 * pelo TURN quando o NAT nao permite caminho direto — e o TURN so encaminha
 * bytes ja cifrados por DTLS-SRTP, que ele nao consegue ler.
 *
 * Quem liga e sempre quem faz a oferta. Isso e fixado pelo servidor e elimina a
 * colisao de negociacao (glare), em que os dois lados ofertam ao mesmo tempo e a
 * conexao nunca se estabelece.
 */

import { api } from "./apiClient";

export interface IceServerConfig {
  urls: string[];
  username: string | null;
  credential: string | null;
}

export interface IceConfig {
  iceServers: IceServerConfig[];
  expiresAt: string;
}

/** Busca credenciais frescas. Elas expiram, entao nao sao guardadas em cache. */
export async function fetchIceConfig(): Promise<RTCIceServer[]> {
  const config = await api.get<IceConfig>("/webrtc/ice");
  return config.iceServers.map((server) => ({
    urls: server.urls,
    ...(server.username ? { username: server.username } : {}),
    ...(server.credential ? { credential: server.credential } : {}),
  }));
}

export interface PeerCallbacks {
  onIceCandidate: (candidate: RTCIceCandidateInit) => void;
  /**
   * Emite uma descricao (oferta ou resposta) gerada pelo proprio navegador.
   *
   * Com negociacao perfeita, quem decide QUANDO renegociar e o navegador, pelo
   * evento `negotiationneeded`. A aplicacao so transporta o que sair daqui.
   */
  onDescription?: (description: RTCSessionDescriptionInit) => void;
  onRemoteStream: (stream: MediaStream) => void;
  onStateChange: (state: RTCPeerConnectionState) => void;
  onNegotiationNeeded?: () => void;
  /**
   * Disparado quando o compartilhamento termina por fora da aplicacao.
   *
   * O navegador exibe a propria barra "Parar de compartilhar", e nao ha como
   * suprimi-la. Sem tratar isso, o botao da interface continuaria dizendo
   * "Parar de compartilhar" depois de o compartilhamento ja ter parado.
   */
  onScreenShareEnded?: () => void;
}

/**
 * Envolve o RTCPeerConnection com o que a aplicacao precisa.
 *
 * Cuida de um detalhe facil de errar: candidatos ICE que chegam antes da
 * descricao remota. Aplicar um deles nessa ordem lanca excecao, entao eles ficam
 * em fila ate haver descricao remota.
 */
export class PeerConnection {
  private readonly pc: RTCPeerConnection;
  private readonly pendingCandidates: RTCIceCandidateInit[] = [];
  private remoteDescriptionSet = false;
  private remoteTracks: MediaStreamTrack[] = [];
  private localStream: MediaStream | null = null;
  private screenTrack: MediaStreamTrack | null = null;
  /** Trilha da camera guardada durante o compartilhamento, para ser restaurada. */
  private cameraTrack: MediaStreamTrack | null = null;
  private onScreenShareEnded: (() => void) | null = null;

  /* ------------------------------------------------ negociacao perfeita */

  /**
   * Quem cede em caso de colisao.
   *
   * Quando os dois lados ofertam ao mesmo tempo (o que acontece quando duas
   * pessoas ligam a camera no mesmo instante), alguem precisa desistir da
   * propria oferta e aceitar a do outro. Sem isso a conexao trava em
   * "have-local-offer" dos dois lados e nenhuma midia passa — que e
   * exatamente o sintoma de "compartilhei e nao apareceu nada".
   *
   * A escolha e deterministica: quem tem o id menor e o educado. Nao pode ser
   * aleatoria, senao os dois lados poderiam se considerar educados.
   */
  private readonly polite: boolean;

  /** Verdadeiro enquanto este lado esta montando uma oferta. */
  private makingOffer = false;

  /** Oferta descartada por colisao; os candidatos ICE dela devem ser ignorados. */
  private ignoreOffer = false;

  private readonly callbacks: PeerCallbacks;

  constructor(iceServers: RTCIceServer[], callbacks: PeerCallbacks, polite = true) {
    this.polite = polite;
    this.callbacks = callbacks;
    this.pc = new RTCPeerConnection({
      iceServers,
      // "all" permite caminho direto quando possivel e so cai no relay se
      // precisar. "relay" forcaria tudo pelo TURN — mais privado quanto ao IP,
      // muito mais caro em banda. Compromisso registrado como decisao.
      iceTransportPolicy: "all",
      bundlePolicy: "max-bundle",
    });

    this.pc.onicecandidate = (event) => {
      if (event.candidate) {
        callbacks.onIceCandidate(event.candidate.toJSON());
      }
    };

    this.pc.ontrack = (event) => {
      if (!this.remoteTracks.some((track) => track.id === event.track.id)) {
        this.remoteTracks.push(event.track);
      }

      // Um MediaStream NOVO a cada trilha, de proposito.
      //
      // Reaproveitar o mesmo objeto e o que fazia o compartilhamento de tela
      // nao aparecer: o <video> ja tinha aquele stream em srcObject, entao o
      // React nao reatribuia nada e o elemento continuava exibindo so o audio
      // que existia quando ele foi montado. Trocando a identidade do objeto, a
      // atribuicao acontece e o video aparece.
      //
      // Trilhas que terminaram sao descartadas: quem para de compartilhar deixa
      // uma trilha morta que manteria o elemento de video na tela, congelado.
      this.remoteTracks = this.remoteTracks.filter(
        (track) => track.readyState !== "ended",
      );
      callbacks.onRemoteStream(new MediaStream(this.remoteTracks));
    };

    // Trilha remota encerrada (o outro lado parou de compartilhar): republica o
    // stream sem ela, para o elemento de video sair da tela em vez de congelar
    // no ultimo quadro.
    this.pc.addEventListener("track", (event) => {
      event.track.addEventListener("ended", () => {
        this.remoteTracks = this.remoteTracks.filter(
          (track) => track.id !== event.track.id,
        );
        callbacks.onRemoteStream(new MediaStream(this.remoteTracks));
      });
    });

    this.pc.onconnectionstatechange = () => {
      callbacks.onStateChange(this.pc.connectionState);
    };

    // O navegador avisa sozinho quando a sessao precisa ser renegociada —
    // ao acrescentar uma trilha, por exemplo. Chamar createOffer na mao, como
    // antes, corria o risco de faze-lo no estado errado; aqui isso nao
    // acontece porque o evento so dispara quando ha o que negociar.
    this.pc.onnegotiationneeded = async () => {
      try {
        this.makingOffer = true;
        await this.pc.setLocalDescription();
        if (this.pc.localDescription) {
          this.callbacks.onDescription?.(this.pc.localDescription.toJSON());
        }
      } catch (erro) {
        console.error("[webrtc] falha ao criar oferta", erro);
      } finally {
        this.makingOffer = false;
      }
      callbacks.onNegotiationNeeded?.();
    };

    this.onScreenShareEnded = callbacks.onScreenShareEnded ?? null;
  }

  /** Captura microfone e, se pedido, camera. */
  async startLocalMedia(withVideo: boolean): Promise<MediaStream> {
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
      video: withVideo ? { width: { ideal: 1280 }, height: { ideal: 720 } } : false,
    });

    this.attachLocalStream(stream);
    return stream;
  }

  /** Reusa o mesmo microfone em várias conexoes de uma sala multiparte. */
  attachLocalStream(stream: MediaStream): void {
    this.localStream = stream;
    for (const track of stream.getTracks()) {
      this.pc.addTrack(track, stream);
    }
  }

  attachVideoTrack(track: MediaStreamTrack, stream: MediaStream): void {
    this.localStream = stream;
    this.pc.addTrack(track, stream);
  }

  replaceVideoTrack(track: MediaStreamTrack | null): boolean {
    const sender = this.pc.getSenders().find((candidate) => candidate.track?.kind === "video");
    if (!sender) {
      return false;
    }
    void sender.replaceTrack(track);
    return true;
  }

  async createOffer(): Promise<RTCSessionDescriptionInit> {
    await this.pc.setLocalDescription();
    if (!this.pc.localDescription) {
      throw new Error("O navegador nao criou a oferta WebRTC");
    }
    return this.pc.localDescription.toJSON();
  }

  async createAnswer(): Promise<RTCSessionDescriptionInit> {
    await this.pc.setLocalDescription();
    if (!this.pc.localDescription) {
      throw new Error("O navegador nao criou a resposta WebRTC");
    }
    return this.pc.localDescription.toJSON();
  }

  /**
   * Aplica uma descricao recebida, resolvendo colisao quando houver.
   *
   * Substitui o par createOffer/createAnswer manual. A aplicacao entrega o que
   * chegou e este metodo decide o que fazer:
   *
   *  - oferta recebida em estado limpo    -> responde
   *  - oferta recebida durante a propria  -> se educado, cede e responde;
   *    oferta (colisao)                      se nao, ignora a do outro
   *  - resposta                           -> aplica
   *
   * Devolve a resposta a ser enviada, ou null quando nao ha o que responder.
   */
  async applyRemoteDescription(
    description: RTCSessionDescriptionInit,
  ): Promise<RTCSessionDescriptionInit | null> {
    const ehOferta = description.type === "offer";

    // "Pronto para receber oferta" cobre o caso de estarmos montando a nossa:
    // o estado ainda seria "stable", mas ha uma oferta em voo.
    const prontoParaOferta =
      !this.makingOffer && this.pc.signalingState === "stable";

    const colisao = ehOferta && !prontoParaOferta;

    // O lado impaciente descarta a oferta do outro e mantem a sua. O educado
    // faz o contrario. Com a regra deterministica, exatamente um dos dois cede.
    this.ignoreOffer = !this.polite && colisao;
    if (this.ignoreOffer) {
      console.info("[webrtc] oferta ignorada por colisão (lado impaciente)");
      return null;
    }

    await this.pc.setRemoteDescription(new RTCSessionDescription(description));
    this.remoteDescriptionSet = true;

    // Candidatos que chegaram antes da descricao agora podem ser aplicados.
    for (const candidate of this.pendingCandidates.splice(0)) {
      await this.pc.addIceCandidate(new RTCIceCandidate(candidate)).catch(() => {
        // Candidato invalido nao derruba a chamada: os outros ainda servem.
      });
    }

    if (!ehOferta) {
      return null;
    }
    // setLocalDescription sem argumento cria a resposta apropriada ao estado.
    await this.pc.setLocalDescription();
    return this.pc.localDescription?.toJSON() ?? null;
  }

  /** Mantido para o fluxo de chamada privada, que negocia manualmente. */
  async setRemoteDescription(description: RTCSessionDescriptionInit): Promise<void> {
    await this.applyRemoteDescription(description);
  }

  async addIceCandidate(candidate: RTCIceCandidateInit): Promise<void> {
    if (!this.remoteDescriptionSet) {
      this.pendingCandidates.push(candidate);
      return;
    }
    await this.pc
      .addIceCandidate(new RTCIceCandidate(candidate))
      .catch((erro) => {
        // Candidatos da oferta descartada na colisao chegam depois e falham.
        // Ignorar so nesse caso; nos demais, o erro interessa.
        if (!this.ignoreOffer) {
          console.warn("[webrtc] candidato ICE recusado", erro);
        }
      });
  }

  /** Liga ou desliga o microfone sem derrubar a negociacao. */
  setAudioEnabled(enabled: boolean): void {
    this.localStream?.getAudioTracks().forEach((track) => {
      track.enabled = enabled;
    });
  }

  setVideoEnabled(enabled: boolean): void {
    this.localStream?.getVideoTracks().forEach((track) => {
      track.enabled = enabled;
    });
  }

  hasVideoTrack(): boolean {
    return (this.localStream?.getVideoTracks().length ?? 0) > 0;
  }

  /**
   * Acrescenta video a uma chamada que comecou so com audio.
   *
   * Dispara renegociacao — o `onnegotiationneeded` do lado que chamou isto
   * cuida de enviar a nova oferta.
   */
  async addVideoTrack(): Promise<MediaStream | null> {
    if (this.hasVideoTrack()) {
      return this.localStream;
    }
    const videoStream = await navigator.mediaDevices.getUserMedia({
      video: { width: { ideal: 1280 }, height: { ideal: 720 } },
    });
    const [videoTrack] = videoStream.getVideoTracks();
    if (!videoTrack || !this.localStream) {
      return this.localStream;
    }
    this.localStream.addTrack(videoTrack);
    this.pc.addTrack(videoTrack, this.localStream);
    return this.localStream;
  }

  /* --------------------------------------------- compartilhamento de tela */

  /**
   * Substitui o video enviado pela tela.
   *
   * `replaceTrack` troca a midia dentro do transceiver que ja existe: mesmo
   * codec, mesmo SSRC, mesmo transporte. Nao ha renegociacao, e por isso a troca
   * e instantanea (decisao D-06).
   *
   * A excecao e a chamada que comecou so com voz: nao ha trilha de video para
   * substituir, entao a trilha e acrescentada e a negociacao acontece.
   *
   * @returns o fluxo da tela, para exibir na previa local, e se houve
   *          necessidade de renegociar
   */
  async startScreenShare(): Promise<{ stream: MediaStream; needsRenegotiation: boolean }> {
    const display = await navigator.mediaDevices.getDisplayMedia({
      video: { frameRate: { ideal: 15, max: 30 } },
      // Audio do sistema fica de fora deliberadamente: capturar a saida de som
      // da maquina inteira e uma forma facil de transmitir sem querer uma
      // notificacao, uma outra conversa ou o que estiver tocando ao fundo.
      audio: false,
    });

    const [screenTrack] = display.getVideoTracks();
    if (!screenTrack) {
      throw new Error("Nenhuma trilha de video no compartilhamento");
    }

    // Quadro parado ganha nitidez; movimento ganha fluidez. Tela costuma ser
    // texto, entao a escolha e por detalhe.
    if ("contentHint" in screenTrack) {
      screenTrack.contentHint = "detail";
    }

    const sender = this.pc
      .getSenders()
      .find((candidate) => candidate.track?.kind === "video");

    let needsRenegotiation = false;

    if (sender) {
      this.cameraTrack = sender.track;
      await sender.replaceTrack(screenTrack);
    } else {
      // Chamada de voz: nao ha o que substituir.
      if (!this.localStream) {
        this.localStream = new MediaStream();
      }
      this.pc.addTrack(screenTrack, this.localStream);
      needsRenegotiation = true;
    }

    screenTrack.addEventListener("ended", () => {
      // O usuario parou pela barra do navegador, nao pelo botao da aplicacao.
      void this.stopScreenShare();
      this.onScreenShareEnded?.();
    });

    this.screenTrack = screenTrack;
    return { stream: display, needsRenegotiation };
  }

  /** Volta a enviar a camera, ou nada, se a chamada era so de voz. */
  async stopScreenShare(): Promise<void> {
    if (!this.screenTrack) {
      return;
    }
    const sender = this.pc
      .getSenders()
      .find((candidate) => candidate.track === this.screenTrack);

    if (sender) {
      // replaceTrack(null) apenas para de enviar video; a conexao e o audio
      // continuam intactos.
      await sender.replaceTrack(this.cameraTrack).catch(() => {});
    }

    this.screenTrack.stop();
    this.screenTrack = null;
    this.cameraTrack = null;
  }

  isSharingScreen(): boolean {
    return this.screenTrack !== null;
  }

  /** Encerra tudo e libera camera e microfone. */
  close(): void {
    this.screenTrack?.stop();
    this.screenTrack = null;
    this.cameraTrack = null;
    this.localStream?.getTracks().forEach((track) => track.stop());
    this.localStream = null;
    this.pc.onicecandidate = null;
    this.pc.ontrack = null;
    this.pc.onconnectionstatechange = null;
    this.pc.onnegotiationneeded = null;
    this.pc.close();
  }

  get connectionState(): RTCPeerConnectionState {
    return this.pc.connectionState;
  }
}

/** Mensagem de erro util para as falhas comuns de captura de midia. */
export function mediaErrorMessage(error: unknown): string {
  if (error instanceof DOMException) {
    switch (error.name) {
      case "NotAllowedError":
        return "Permissao de microfone ou camera negada pelo navegador.";
      case "NotFoundError":
        return "Nenhum microfone ou camera encontrado.";
      case "NotReadableError":
        return "O dispositivo esta em uso por outro programa.";
      case "AbortError":
        return "Compartilhamento cancelado.";
      default:
        return "Nao foi possivel acessar microfone ou camera.";
    }
  }
  return "Falha ao iniciar a midia.";
}

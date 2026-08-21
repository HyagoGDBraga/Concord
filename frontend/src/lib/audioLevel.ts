/**
 * Deteccao de "esta falando" a partir de um MediaStream.
 *
 * Usa a Web Audio API para medir o volume em tempo real. Nao ha outro jeito: o
 * WebRTC nao avisa quem esta falando — ele so entrega o audio.
 *
 * O detector roda no navegador de quem OUVE, sobre o stream recebido. Isso
 * significa que o servidor nunca sabe quem esta falando, e nao ha um evento por
 * pessoa a cada 100ms atravessando o WebSocket.
 */

/** Acima disto consideramos que ha voz. Abaixo, e ruido de fundo. */
const THRESHOLD = 0.045;

/**
 * Tempo que o indicador permanece aceso depois que o volume cai.
 *
 * Sem isso, a borda piscaria em cada pausa entre palavras — a fala humana tem
 * silencios curtos o tempo todo.
 */
const RELEASE_MS = 350;

export interface SpeakingDetector {
  stop: () => void;
}

export function detectSpeaking(
  stream: MediaStream,
  onChange: (speaking: boolean) => void,
): SpeakingDetector {
  if (stream.getAudioTracks().length === 0) {
    return { stop: () => {} };
  }

  const AudioContextClass =
    window.AudioContext ??
    (window as unknown as { webkitAudioContext?: typeof AudioContext })
      .webkitAudioContext;

  if (!AudioContextClass) {
    return { stop: () => {} };
  }

  const context = new AudioContextClass();

  // O navegador cria o AudioContext SUSPENSO quando nao ha gesto do usuario
  // associado, e um contexto suspenso nao processa nada — o analisador leria
  // silencio para sempre. Era por isso que o anel de fala nunca acendia.
  //
  // resume() e uma promessa; nao da para esperar aqui sem tornar a funcao
  // assincrona, entao o retorno e ignorado de proposito: o loop de analise ja
  // esta rodando e passa a receber dados assim que o contexto acorda.
  if (context.state === "suspended") {
    void context.resume().catch(() => {});
  }

  const source = context.createMediaStreamSource(stream);
  const analyser = context.createAnalyser();

  // Janela pequena: queremos reagir rapido, nao analisar espectro.
  analyser.fftSize = 512;
  analyser.smoothingTimeConstant = 0.4;
  source.connect(analyser);

  // O elemento de audio precisa existir para que o Chrome processe o stream
  // remoto. Sem uma saida ativa, createMediaStreamSource de um stream WebRTC
  // devolve silencio — quirk conhecido do Chromium. O VoiceRoom ja cria um
  // <audio> por participante; este destino mudo cobre o caso de o detector
  // rodar antes disso.
  const mudo = context.createGain();
  mudo.gain.value = 0;
  analyser.connect(mudo);
  mudo.connect(context.destination);

  const samples = new Uint8Array(analyser.frequencyBinCount);
  let speaking = false;
  let silenceSince = 0;
  let frame = 0;
  let stopped = false;

  function tick() {
    if (stopped) {
      return;
    }
    analyser.getByteTimeDomainData(samples);

    // RMS sobre a forma de onda. Cada amostra vem de 0 a 255 com 128 no
    // silencio, entao o desvio em relacao a 128 e a amplitude.
    let sum = 0;
    for (const sample of samples) {
      const deviation = (sample - 128) / 128;
      sum += deviation * deviation;
    }
    const level = Math.sqrt(sum / samples.length);
    const now = performance.now();

    if (level > THRESHOLD) {
      silenceSince = 0;
      if (!speaking) {
        speaking = true;
        onChange(true);
      }
    } else if (speaking) {
      if (silenceSince === 0) {
        silenceSince = now;
      } else if (now - silenceSince > RELEASE_MS) {
        speaking = false;
        onChange(false);
      }
    }
    frame = requestAnimationFrame(tick);
  }

  frame = requestAnimationFrame(tick);

  return {
    stop: () => {
      stopped = true;
      cancelAnimationFrame(frame);
      source.disconnect();
      analyser.disconnect();
      mudo.disconnect();
      void context.close().catch(() => {});
    },
  };
}

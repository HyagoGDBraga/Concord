"use client";

import { useEffect, useRef, useState } from "react";
import { useRealtime, useRealtimeEvent, type CallSignal } from "@/lib/realtime";
import { useSession } from "@/lib/session";
import { fetchIceConfig, PeerConnection } from "@/lib/webrtc";

type RoomState = { channelId: string; participantIds: string[] };
type RoomPresence = { channelId: string; userId: string };
type RoomSignal = { channelId: string; fromUserId: string; type: CallSignal["type"]; payload: unknown };

export function VoiceRoom({ serverId, channelId }: { serverId: string; channelId: string }) {
  const { user } = useSession();
  const { connected, sendVoicePresence, sendVoiceSignal } = useRealtime();
  const [joined, setJoined] = useState(false);
  const [participants, setParticipants] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const peersRef = useRef<Map<string, PeerConnection>>(new Map());
  const audioRef = useRef<Map<string, HTMLAudioElement>>(new Map());
  const localStreamRef = useRef<MediaStream | null>(null);

  function removePeer(userId: string) {
    peersRef.current.get(userId)?.close();
    peersRef.current.delete(userId);
    audioRef.current.get(userId)?.pause();
    audioRef.current.delete(userId);
    setParticipants((current) => current.filter((id) => id !== userId));
  }

  async function createPeer(userId: string, makeOffer: boolean) {
    if (!user || peersRef.current.has(userId)) {
      return;
    }
    const peer = new PeerConnection(await fetchIceConfig(), {
      onIceCandidate: (candidate) => sendVoiceSignal(serverId, channelId, userId, "ICE_CANDIDATE", candidate),
      onRemoteStream: (stream) => {
        let audio = audioRef.current.get(userId);
        if (!audio) {
          audio = new Audio();
          audio.autoplay = true;
          audioRef.current.set(userId, audio);
        }
        audio.srcObject = stream;
        void audio.play().catch(() => {});
      },
      onStateChange: (state) => {
        if (state === "failed" || state === "closed") {
          removePeer(userId);
        }
      },
    });
    peersRef.current.set(userId, peer);
    if (!localStreamRef.current) {
      localStreamRef.current = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
    }
    peer.attachLocalStream(localStreamRef.current);
    setParticipants((current) => current.includes(userId) ? current : [...current, userId]);
    if (makeOffer) {
      sendVoiceSignal(serverId, channelId, userId, "OFFER", await peer.createOffer());
    }
  }

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
      setParticipants((current) => current.includes(event.userId) ? current : [...current, event.userId]);
    }
  });

  useRealtimeEvent<RoomPresence>("VOICE_USER_LEFT", (event) => {
    if (event.channelId === channelId) {
      removePeer(event.userId);
    }
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
      if (event.type === "OFFER") {
        await peer.setRemoteDescription(event.payload as RTCSessionDescriptionInit);
        sendVoiceSignal(serverId, channelId, event.fromUserId, "ANSWER", await peer.createAnswer());
      } else if (event.type === "ANSWER") {
        await peer.setRemoteDescription(event.payload as RTCSessionDescriptionInit);
      } else if (event.type === "ICE_CANDIDATE") {
        await peer.addIceCandidate(event.payload as RTCIceCandidateInit);
      }
    })().catch(() => setError("Não foi possível conectar o áudio da sala."));
  });

  useEffect(() => {
    if (!joined || !connected) {
      return;
    }
    sendVoicePresence(serverId, channelId, true);
    return () => {
      sendVoicePresence(serverId, channelId, false);
      peersRef.current.forEach((peer) => peer.close());
      peersRef.current.clear();
      localStreamRef.current?.getTracks().forEach((track) => track.stop());
      localStreamRef.current = null;
      audioRef.current.forEach((audio) => audio.pause());
      audioRef.current.clear();
      setParticipants([]);
    };
  }, [joined, connected, serverId, channelId, sendVoicePresence]);

  async function toggle() {
    setError(null);
    if (!joined) {
      try {
        localStreamRef.current = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
        setJoined(true);
      } catch {
        setError("Permita o microfone para entrar na sala de voz.");
      }
    } else {
      setJoined(false);
    }
  }

  return (
    <div className={`voice-room ${joined ? "is-joined" : ""}`}>
      <div>
        <p className="eyebrow">Sala de voz</p>
        <strong>{joined ? "Você está na sala" : "Áudio entre membros"}</strong>
        {joined && <span>{participants.length + 1} participante(s) conectado(s)</span>}
      </div>
      <button type="button" onClick={() => void toggle()} className="voice-room-button">
        {joined ? "Sair da sala" : "Entrar na voz"}
      </button>
      {error && <small>{error}</small>}
    </div>
  );
}
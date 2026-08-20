"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/apiClient";
import { useRealtime } from "@/lib/realtime";

type Channel = { id?: string; name: string; kind: "text" | "voice" };
type Community = { id?: string; name: string; channels: Channel[] };

type ServerResponse = {
  id: string;
  name: string;
  channels: { id: string; name: string; type: string }[];
};
type MemberResponse = { user: { id: string; username: string; displayName: string; avatarUrl: string | null }; role: string };

const DEFAULT_COMMUNITIES: Community[] = [
  {
    name: "Concord",
    channels: [
      { name: "geral", kind: "text" },
      { name: "avisos", kind: "text" },
      { name: "Sala de voz", kind: "voice" },
    ],
  },
  {
    name: "Projeto Concord",
    channels: [
      { name: "desenvolvimento", kind: "text" },
      { name: "design", kind: "text" },
    ],
  },
];

const STORAGE_KEY = "concord.communities";

function initials(name: string): string {
  return name
    .split(/\s+/)
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
}

export function CommunityShell({
  username,
  onLogout,
  children,
}: {
  username: string;
  onLogout: () => void;
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const [communities, setCommunities] = useState(DEFAULT_COMMUNITIES);
  const [activeCommunity, setActiveCommunity] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);
  const [members, setMembers] = useState<MemberResponse[]>([]);
  const [modal, setModal] = useState<"server" | "channel" | "member" | "invite" | null>(null);
  const [modalValue, setModalValue] = useState("");
  const [modalChannelType, setModalChannelType] = useState<"TEXT" | "VOICE">("TEXT");
  const [toast, setToast] = useState<string | null>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const { voiceParticipantsByChannel } = useRealtime();

  useEffect(() => {
    let mounted = true;
    void api.get<ServerResponse[]>("/servers").then((servers) => {
      if (!mounted || servers.length === 0) {
        return;
      }
      const next = servers.map((server) => ({
        id: server.id,
        name: server.name,
        channels: server.channels.map((channel) => ({
          id: channel.id,
          name: channel.name,
          kind: channel.type.toLowerCase() === "voice" ? "voice" as const : "text" as const,
        })),
      }));
      setCommunities(next);
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    }).catch(() => {
      const saved = window.localStorage.getItem(STORAGE_KEY);
      if (!saved) {
        return;
      }
      try {
        const parsed = JSON.parse(saved) as Community[];
        if (mounted && Array.isArray(parsed) && parsed.length > 0) {
          setCommunities(parsed);
        }
      } catch {
        window.localStorage.removeItem(STORAGE_KEY);
      }
    });
    return () => {
      mounted = false;
    };
  }, []);

  async function createCommunity(name: string) {
    if (!name.trim()) {
      return;
    }
    let next: Community[];
    try {
      const created = await api.post<ServerResponse>("/servers", { name: name.trim() });
      next = [...communities, {
        id: created.id,
        name: created.name,
        channels: created.channels.map((channel) => ({ id: channel.id, name: channel.name, kind: "text" as const })),
      }];
    } catch {
      next = [...communities, {
        name: name.trim(),
        channels: [{ name: "geral", kind: "text" as const }],
      }];
    }
    setCommunities(next);
    setActiveCommunity(next.length - 1);
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  }

  async function createChannel(name: string, type: "TEXT" | "VOICE" = "TEXT") {
    if (!name.trim() || !community) {
      return;
    }
    const channelName = name.trim();
    try {
      if (community.id) {
        const created = await api.post<{ id: string; name: string; type: string }>(
          `/servers/${community.id}/channels`, { name: channelName, type });
        const next = communities.map((item, index) =>
          index === activeCommunity
            ? { ...item, channels: [...item.channels, { id: created.id, name: created.name, kind: type === "VOICE" ? "voice" as const : "text" as const }] }
            : item,
        );
        setCommunities(next);
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
        return;
      }
    } catch {
      // O fallback local mantém a interface utilizável durante a atualização da API.
    }
    const next = communities.map((item, index) =>
      index === activeCommunity
        ? { ...item, channels: [...item.channels, { name: channelName, kind: type === "VOICE" ? "voice" as const : "text" as const }] }
        : item,
    );
    setCommunities(next);
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  }

  const community = communities[activeCommunity] ?? communities[0];

  useEffect(() => {
    function closeOnOutside(event: PointerEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setMenuOpen(false);
      }
    }
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setMenuOpen(false);
        setModal(null);
      }
    }
    document.addEventListener("pointerdown", closeOnOutside);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("pointerdown", closeOnOutside);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, []);

  useEffect(() => {
    if (!community?.id) {
      setMembers([]);
      return;
    }
    void api.get<MemberResponse[]>(`/servers/${community.id}/members`)
      .then(setMembers)
      .catch(() => setMembers([]));
  }, [community?.id]);

  async function addMember(username: string) {
    if (!community?.id) {
      return;
    }
    if (!username.trim()) {
      return;
    }
    try {
      const member = await api.post<MemberResponse>(`/servers/${community.id}/members`, {
        username: username.trim(),
      });
      setMembers((current) => [...current, member]);
    } catch (error) {
      setToast(error instanceof Error ? error.message : "Não foi possível adicionar o membro");
    }
  }

  async function createInvite(username: string) {
    if (!community?.id) {
      return;
    }
    if (!username.trim()) {
      return;
    }
    try {
      const invite = await api.post<{ token: string; expiresAt: string }>(
        `/servers/${community.id}/invites`, { username: username.trim() });
      if (navigator.clipboard) {
        await navigator.clipboard.writeText(invite.token).catch(() => {});
      }
      setToast(`Convite copiado. Expira em ${new Date(invite.expiresAt).toLocaleDateString("pt-BR")}.`);
    } catch (error) {
      setToast(error instanceof Error ? error.message : "Não foi possível criar o convite");
    }
  }

  return (
    <div className="community-frame">
      <aside className="community-rail" aria-label="Servidores">
        <Link href="/" className="community-mark" aria-label="Ir para o inicio">
          C
        </Link>
        <div className="rail-rule" />
        {communities.map((item, index) => (
          <button
            key={`${item.name}-${index}`}
            type="button"
            className={`community-orb ${index === activeCommunity ? "is-active" : ""}`}
            onClick={() => setActiveCommunity(index)}
            title={item.name}
            aria-label={item.name}
          >
            {initials(item.name)}
          </button>
        ))}
      <button type="button" className="community-orb add-orb" onClick={() => { setModal("server"); setModalValue(""); }} title="Criar servidor">
          +
        </button>
      </aside>

      <aside className="channel-sidebar" aria-label="Canais do servidor">
        <div className="server-heading" ref={menuRef}>
          <div>
            <p className="eyebrow">Servidor</p>
            <h1>{community.name}</h1>
          </div>
          <button type="button" className="server-menu" onClick={() => setMenuOpen((open) => !open)} aria-label="Abrir menu do servidor">
            ...
          </button>
          {menuOpen && (
            <div className="server-popover">
              <strong>{community.name}</strong>
              <span>Espaço compartilhado</span>
              <button type="button" className="popover-action" onClick={() => { setModal("member"); setModalValue(""); }}>
                + adicionar membro
              </button>
              <button type="button" className="popover-action" onClick={() => { setModal("invite"); setModalValue(""); }}>
                ↗ convidar membro
              </button>
              {members.length > 0 && (
                <div className="popover-members">
                  {members.map((member) => (
                    <span key={member.user.username}>● {member.user.displayName}</span>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>

        <div className="channel-group">
          <div className="channel-group-title">
            <span>CANAIS</span>
            <button type="button" onClick={() => { setModal("channel"); setModalValue(""); }} aria-label="Criar canal">+</button>
          </div>
          {community.channels.map((channel) => (
            <div key={channel.name}>
              <Link
                href={community.id && channel.id
                  ? `/servers/${community.id}/channels/${channel.id}`
                  : "/conversations"}
                className={`channel-link ${pathname.includes(channel.id ?? "__missing__") ? "is-current" : ""}`}
              >
                <span className="channel-symbol">{channel.kind === "voice" ? "◖" : "#"}</span>
                <span className="channel-name">{channel.name}</span>
              </Link>
              {channel.kind === "voice" && channel.id && (
                <div className="voice-channel-participants" aria-label="Pessoas na sala de voz">
                  {Array.from(voiceParticipantsByChannel.get(channel.id) ?? []).map((participantId) => {
                    const member = members.find((item) => item.user.id === participantId);
                    const label = member?.user.displayName ?? member?.user.username ?? participantId.slice(0, 6);
                    return (
                      <span key={participantId} className="voice-channel-participant" title={label}>
                        {member?.user.avatarUrl ? <img src={member.user.avatarUrl} alt="" /> : initials(label)}
                      </span>
                    );
                  })}
                </div>
              )}
            </div>
          ))}
        </div>

        <div className="sidebar-spacer" />
        <div className="profile-strip">
          <span className="profile-avatar">{initials(username)}</span>
          <span className="profile-copy"><strong>{username}</strong><small>online</small></span>
          <Link href="/settings" className="profile-action" aria-label="Abrir conta">⚙</Link>
          <button type="button" className="profile-action" onClick={onLogout} aria-label="Sair">↪</button>
        </div>
      </aside>

      <main className="community-content">{children}</main>

      {modal && (
        <div className="community-modal-backdrop" onMouseDown={() => setModal(null)}>
          <form
            className="community-modal"
            onSubmit={(event) => {
              event.preventDefault();
              const action = modal === "server" ? (value: string) => createCommunity(value)
                : modal === "channel" ? (value: string) => createChannel(value, modalChannelType)
                  : modal === "member" ? addMember : createInvite;
              void action(modalValue).then(() => {
                setModal(null);
                setModalValue("");
              });
            }}
            onMouseDown={(event) => event.stopPropagation()}
          >
            <p className="eyebrow">Concord / {modal === "server" ? "novo servidor" : modal === "channel" ? "novo canal" : modal === "invite" ? "novo convite" : "novo membro"}</p>
            <h2>{modal === "server" ? "Criar servidor" : modal === "channel" ? "Criar canal" : modal === "invite" ? "Convidar alguém" : "Adicionar membro"}</h2>
            <p className="modal-copy">{modal === "channel" ? "Dê um nome curto para a sala da comunidade." : "Tudo começa com uma boa sala para as pessoas se encontrarem."}</p>
            <input
              autoFocus
              value={modalValue}
              onChange={(event) => setModalValue(event.target.value)}
              placeholder={modal === "server" ? "Nome do servidor" : modal === "channel" ? "ex.: papo-livre" : "username"}
              maxLength={80}
            />
            {modal === "channel" && (
              <select value={modalChannelType} onChange={(event) => setModalChannelType(event.target.value as "TEXT" | "VOICE")}>
                <option value="TEXT">Canal de texto</option>
                <option value="VOICE">Sala de voz</option>
              </select>
            )}
            <div className="modal-actions">
              <button type="button" onClick={() => setModal(null)}>Cancelar</button>
              <button type="submit" className="modal-submit">Confirmar</button>
            </div>
          </form>
        </div>
      )}
      {toast && (
        <button type="button" className="community-toast" onClick={() => setToast(null)}>
          {toast}
        </button>
      )}
    </div>
  );
}
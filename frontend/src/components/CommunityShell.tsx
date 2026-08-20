"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui";

type Channel = { name: string; kind: "text" | "voice" };
type Community = { name: string; channels: Channel[] };

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

  useEffect(() => {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    if (!saved) {
      return;
    }
    try {
      const parsed = JSON.parse(saved) as Community[];
      if (Array.isArray(parsed) && parsed.length > 0) {
        setCommunities(parsed);
      }
    } catch {
      window.localStorage.removeItem(STORAGE_KEY);
    }
  }, []);

  function createCommunity() {
    const name = window.prompt("Nome do servidor");
    if (!name?.trim()) {
      return;
    }
    const next = [
      ...communities,
      { name: name.trim(), channels: [{ name: "geral", kind: "text" as const }] },
    ];
    setCommunities(next);
    setActiveCommunity(next.length - 1);
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  }

  function createChannel() {
    const name = window.prompt("Nome do canal");
    if (!name?.trim() || !community) {
      return;
    }
    const next = communities.map((item, index) =>
      index === activeCommunity
        ? { ...item, channels: [...item.channels, { name: name.trim(), kind: "text" as const }] }
        : item,
    );
    setCommunities(next);
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  }

  const community = communities[activeCommunity] ?? communities[0];

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
        <button type="button" className="community-orb add-orb" onClick={createCommunity} title="Criar servidor">
          +
        </button>
      </aside>

      <aside className="channel-sidebar" aria-label="Canais do servidor">
        <div className="server-heading">
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
            </div>
          )}
        </div>

        <div className="channel-group">
          <div className="channel-group-title">
            <span>CANAIS</span>
            <button type="button" onClick={createChannel} aria-label="Criar canal">+</button>
          </div>
          {community.channels.map((channel) => (
            <Link
              key={channel.name}
              href={channel.kind === "voice" ? "/conversations" : "/conversations"}
              className={`channel-link ${pathname === "/conversations" && channel.name === "geral" ? "is-current" : ""}`}
            >
              <span className="channel-symbol">{channel.kind === "voice" ? "◖" : "#"}</span>
              {channel.name}
            </Link>
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
    </div>
  );
}
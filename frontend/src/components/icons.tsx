/**
 * Ícones da interface.
 *
 * SVG inline em vez de biblioteca. Três razões: nenhuma dependência nova para
 * catorze desenhos; `currentColor` faz cada ícone herdar a cor do contexto, o
 * que resolve os dois temas sem código extra; e o traço vetorial evita o
 * problema dos emojis, que mudam de desenho conforme o sistema operacional — o
 * mesmo botão apareceria diferente no Windows, no macOS e no Android.
 *
 * Todos usam a mesma grade de 24 e a mesma espessura de traço, para que fiquem
 * visualmente consistentes lado a lado.
 */

interface IconProps {
  className?: string;
  size?: number;
}

function Svg({
  children,
  className = "",
  size = 20,
}: IconProps & { children: React.ReactNode }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      // Decorativo: o rótulo acessível vem do botão que o contém, não daqui.
      aria-hidden="true"
      focusable="false"
    >
      {children}
    </svg>
  );
}

export function MicIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z" />
      <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
      <line x1="12" y1="19" x2="12" y2="22" />
    </Svg>
  );
}

export function MicOffIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <line x1="2" y1="2" x2="22" y2="22" />
      <path d="M9 9v3a3 3 0 0 0 5.12 2.12" />
      <path d="M15 9.34V5a3 3 0 0 0-5.94-.6" />
      <path d="M19 10v2a7 7 0 0 1-.11 1.23" />
      <path d="M5 10v2a7 7 0 0 0 12 5" />
      <line x1="12" y1="19" x2="12" y2="22" />
    </Svg>
  );
}

export function CameraIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="m23 7-7 5 7 5V7Z" />
      <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
    </Svg>
  );
}

export function CameraOffIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <line x1="2" y1="2" x2="22" y2="22" />
      <path d="M10.66 5H14a2 2 0 0 1 2 2v3.34l1 1L23 7v10" />
      <path d="M16 16a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h2" />
    </Svg>
  );
}

export function ScreenShareIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="2" y="3" width="20" height="14" rx="2" />
      <line x1="8" y1="21" x2="16" y2="21" />
      <line x1="12" y1="17" x2="12" y2="21" />
      <path d="m9 10 3-3 3 3" />
      <line x1="12" y1="7" x2="12" y2="13" />
    </Svg>
  );
}

export function ScreenShareOffIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <line x1="2" y1="2" x2="22" y2="22" />
      <path d="M22 15V5a2 2 0 0 0-2-2H7" />
      <path d="M2 6v9a2 2 0 0 0 2 2h13" />
      <line x1="8" y1="21" x2="16" y2="21" />
      <line x1="12" y1="17" x2="12" y2="21" />
    </Svg>
  );
}

/** Push-to-talk. O raio indica ação momentânea, não estado permanente. */
export function PushToTalkIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M13 2 4.5 13.5H11l-1 8.5 8.5-11.5H12l1-8.5Z" />
    </Svg>
  );
}

export function MaximizeIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M8 3H5a2 2 0 0 0-2 2v3" />
      <path d="M16 3h3a2 2 0 0 1 2 2v3" />
      <path d="M8 21H5a2 2 0 0 1-2-2v-3" />
      <path d="M16 21h3a2 2 0 0 0 2-2v-3" />
    </Svg>
  );
}

export function MinimizeIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M8 3v3a2 2 0 0 1-2 2H3" />
      <path d="M21 8h-3a2 2 0 0 1-2-2V3" />
      <path d="M3 16h3a2 2 0 0 1 2 2v3" />
      <path d="M16 21v-3a2 2 0 0 1 2-2h3" />
    </Svg>
  );
}

export function CloseIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <line x1="18" y1="6" x2="6" y2="18" />
      <line x1="6" y1="6" x2="18" y2="18" />
    </Svg>
  );
}

export function PhoneOffIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M10.68 13.31a16 16 0 0 0 3.41 2.6l1.27-1.27a2 2 0 0 1 2.11-.45 12.8 12.8 0 0 0 2.53.51A2 2 0 0 1 22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-3.33-2.67" />
      <path d="M5.09 9.41A19.5 19.5 0 0 1 2.12 4.18 2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91" />
      <line x1="2" y1="2" x2="22" y2="22" />
    </Svg>
  );
}

/** Dono do servidor. */
export function CrownIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M3 7l4.5 3.5L12 4l4.5 6.5L21 7l-2 11H5L3 7Z" />
    </Svg>
  );
}

/** Moderador. */
export function ShieldIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M12 2 4 5.5v6c0 5 3.4 9.4 8 10.5 4.6-1.1 8-5.5 8-10.5v-6L12 2Z" />
    </Svg>
  );
}

export function HashIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <line x1="4" y1="9" x2="20" y2="9" />
      <line x1="4" y1="15" x2="20" y2="15" />
      <line x1="10" y1="3" x2="8" y2="21" />
      <line x1="16" y1="3" x2="14" y2="21" />
    </Svg>
  );
}

export function SpeakerIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M11 5 6 9H2v6h4l5 4V5Z" />
      <path d="M15.54 8.46a5 5 0 0 1 0 7.07" />
      <path d="M19.07 4.93a10 10 0 0 1 0 14.14" />
    </Svg>
  );
}

export function PlusIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <line x1="12" y1="5" x2="12" y2="19" />
      <line x1="5" y1="12" x2="19" y2="12" />
    </Svg>
  );
}

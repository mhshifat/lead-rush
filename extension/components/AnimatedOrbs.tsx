/**
 * Tiny SVG orb background — same motif as the web app but scaled for the popup.
 * No JS animation, all CSS keyframes; respects prefers-reduced-motion.
 */
import type { CSSProperties } from 'react'

export function AnimatedOrbs({ style }: { style?: CSSProperties }) {
  return (
    <div
      aria-hidden
      style={{
        position: 'absolute',
        inset: 0,
        overflow: 'hidden',
        pointerEvents: 'none',
        zIndex: 0,
        ...style,
      }}
    >
      <style>{`
        @keyframes lr-drift-a { 0%,100% { transform: translate(0,0) scale(1); } 50% { transform: translate(8%,-4%) scale(1.08); } }
        @keyframes lr-drift-b { 0%,100% { transform: translate(0,0) scale(1); } 50% { transform: translate(-6%,5%) scale(0.94); } }
        @keyframes lr-drift-c { 0%,100% { transform: translate(0,0) scale(1); } 50% { transform: translate(4%,6%) scale(1.12); } }
        .lr-orb-a { animation: lr-drift-a 22s ease-in-out infinite; transform-origin: center; }
        .lr-orb-b { animation: lr-drift-b 28s ease-in-out infinite; transform-origin: center; }
        .lr-orb-c { animation: lr-drift-c 25s ease-in-out infinite; transform-origin: center; }
        @media (prefers-reduced-motion: reduce) { .lr-orb-a, .lr-orb-b, .lr-orb-c { animation: none; } }
      `}</style>
      <svg
        viewBox="0 0 400 400"
        preserveAspectRatio="xMidYMid slice"
        style={{ position: 'absolute', inset: 0, width: '100%', height: '100%' }}
      >
        <defs>
          <radialGradient id="lr-orb-a" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="hsl(243 80% 65%)" stopOpacity="0.5" />
            <stop offset="70%" stopColor="hsl(243 80% 65%)" stopOpacity="0" />
          </radialGradient>
          <radialGradient id="lr-orb-b" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="hsl(280 75% 60%)" stopOpacity="0.4" />
            <stop offset="70%" stopColor="hsl(280 75% 60%)" stopOpacity="0" />
          </radialGradient>
          <radialGradient id="lr-orb-c" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="hsl(210 80% 55%)" stopOpacity="0.35" />
            <stop offset="70%" stopColor="hsl(210 80% 55%)" stopOpacity="0" />
          </radialGradient>
          <filter id="lr-blur"><feGaussianBlur stdDeviation="40" /></filter>
        </defs>
        <g filter="url(#lr-blur)">
          <circle className="lr-orb-a" cx="80"  cy="60"  r="120" fill="url(#lr-orb-a)" />
          <circle className="lr-orb-b" cx="330" cy="100" r="140" fill="url(#lr-orb-b)" />
          <circle className="lr-orb-c" cx="200" cy="340" r="130" fill="url(#lr-orb-c)" />
        </g>
      </svg>
    </div>
  )
}

/**
 * Lead Rush brand mark — a stacked upward chevron inside a gradient rounded
 * square. Mirrors the frontend's SharedAppLogo component so the extension
 * feels like part of the same product.
 *
 * Inline SVG (no asset reference) so it renders identically in the popup,
 * the LinkedIn Shadow DOM, and any future surface — no CSP shim needed.
 */
interface Props {
  size?: number
}

export function BrandMark({ size = 22 }: Props) {
  const inner = Math.round(size * 0.625)
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: size,
        height: size,
        borderRadius: Math.round(size * 0.27),
        background: 'linear-gradient(135deg, hsl(243 80% 65%), hsl(280 75% 60%))',
        boxShadow: '0 4px 14px -4px hsl(243 80% 60% / 0.45)',
      }}
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        width={inner}
        height={inner}
        aria-hidden="true"
      >
        <path
          d="M5 17.5 L12 8 L19 17.5"
          stroke="white"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path
          d="M8 14.5 L12 11.5 L16 14.5"
          stroke="white"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          opacity="0.45"
        />
      </svg>
    </span>
  )
}

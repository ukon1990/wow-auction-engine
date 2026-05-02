---
name: Ethereal Arcana
colors:
  surface: '#17130a'
  surface-dim: '#17130a'
  surface-bright: '#3e392d'
  surface-container-lowest: '#120e05'
  surface-container-low: '#1f1b11'
  surface-container: '#241f15'
  surface-container-high: '#2e291f'
  surface-container-highest: '#393429'
  on-surface: '#ebe1d1'
  on-surface-variant: '#d2c5ac'
  inverse-surface: '#ebe1d1'
  inverse-on-surface: '#353025'
  outline: '#9b9079'
  outline-variant: '#4e4633'
  surface-tint: '#f3c01e'
  primary: '#ffd773'
  on-primary: '#3e2e00'
  primary-container: '#ecb913'
  on-primary-container: '#624b00'
  inverse-primary: '#765b00'
  secondary: '#e0b6ff'
  on-secondary: '#4c007d'
  secondary-container: '#6d11ad'
  on-secondary-container: '#d7a4ff'
  tertiary: '#b3e2ff'
  on-tertiary: '#003549'
  tertiary-container: '#63cbff'
  on-tertiary-container: '#005473'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#ffdf94'
  primary-fixed-dim: '#f3c01e'
  on-primary-fixed: '#241a00'
  on-primary-fixed-variant: '#594400'
  secondary-fixed: '#f2daff'
  secondary-fixed-dim: '#e0b6ff'
  on-secondary-fixed: '#2e004e'
  on-secondary-fixed-variant: '#6a0baa'
  tertiary-fixed: '#c3e8ff'
  tertiary-fixed-dim: '#79d1ff'
  on-tertiary-fixed: '#001e2c'
  on-tertiary-fixed-variant: '#004c68'
  background: '#17130a'
  on-background: '#ebe1d1'
  surface-variant: '#393429'
typography:
  nav-logo:
    fontFamily: Cinzel
    fontSize: 24px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: 0.05em
  section-heading:
    fontFamily: Cinzel
    fontSize: 18px
    fontWeight: '700'
    lineHeight: '1.4'
  body-main:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: '1.5'
  data-mono:
    fontFamily: Space Mono
    fontSize: 13px
    fontWeight: '400'
    lineHeight: '1'
  label-caps:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    letterSpacing: 0.1em
  profit-lg:
    fontFamily: Space Mono
    fontSize: 30px
    fontWeight: '700'
    lineHeight: '1'
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  container-padding: 1.5rem
  element-gap: 1rem
  section-margin: 1.5rem
  inner-padding: 1rem
  row-padding: 0.75rem
---

## Brand & Style

The brand is "Ethereal Arcana," a high-fantasy, data-driven experience designed for enthusiasts of complex crafting economies. The personality is mysterious, premium, and authoritative—blending the aesthetic of a wizard’s sanctum with the precision of a financial trading terminal.

The visual style is **Glassmorphism with a Dark Fantasy twist**. It utilizes deep violet and obsidian backgrounds layered with translucent "glass" panels that feature high backdrop blurs (16px+). This creates a sense of depth and magical immersion. Accents are neon-sharp—electric gold for primary actions and vibrant purples for flair—evoking a world powered by arcane energy. The target audience seeks a sophisticated tool that feels like part of the game world yet provides professional-grade market analysis.

## Colors

The palette is built on a "Void and Glow" philosophy.

- **Primary Gold (#ecb913):** Used for interactive states, navigation highlights, and high-tier branding. It represents value and rarity.
- **Surface & Background:** The background is a near-black obsidian (#090514), while surfaces use a tinted, semi-transparent violet (rgba(30, 20, 50, 0.65)).
- **Semantic Colors:** Profit and Loss are handled with saturated, "glowing" variants of green and red to ensure high legibility against the dark backdrop.
- **Metallic Gradients:** Special tokens are reserved for currency (Gold, Silver, Copper) to mimic physical coins with 135-degree linear gradients.

## Typography

The typography system uses a tri-font approach to balance flavor with functionality:

1.  **Display/Heading (Cinzel):** A serif font used for titles, navigation, and section headers to provide the "fantasy" and "ancient codex" feel.
2.  **Body/UI (Inter):** A clean, highly legible sans-serif for all functional UI elements, labels, and standard text blocks.
3.  **Data (Space Mono):** A monospaced font reserved exclusively for currency, percentages, timestamps, and ROI calculations, emphasizing the "terminal" aspect of the exchange.

## Layout & Spacing

The layout follows a **Fixed-Width Split-Pane model** designed for 1440px displays. It utilizes a three-tier vertical structure:

- **Global Navigation:** Fixed height, glass-textured header.
- **Context Bar:** A secondary bar for global filters (Expansions, Search, Profession selection).
- **Main Viewport:** A split-pane layout (1/3 list view, 2/3 detail view) that remains contained within the viewport to prevent page scrolling, utilizing internal scroll areas for lists and tables.

Spacing is governed by a 4px baseline, but defaults to 16px (1rem) for most internal component padding and 24px (1.5rem) for major layout gaps.

## Elevation & Depth

Hierarchy is established through **transparency and glow** rather than traditional drop shadows:

- **Base Layer:** A fixed, full-bleed background image with a dark overlay.
- **Mid Layer (Glass Panels):** Elements use `backdrop-filter: blur(16px)` and a thin 1px white border at 8% opacity. This creates a "frosted" effect that separates content from the background.
- **Top Layer (Active States):** Active items (like selected profession buttons or recipe rows) use "Arcane Glow"—a `box-shadow` or `border` using the primary gold color with a soft, diffused spread (e.g., `0 0 15px rgba(236,185,19,0.5)`).
- **In-Set Depth:** Tables and input fields use a darker, 40-50% opaque black fill to appear recessed into the glass panels.

## Shapes

The shape language is modern and refined:

- **Panels:** Use a standard 0.5rem (8px) radius for a soft but professional feel.
- **Buttons & Toggles:** Profession icons and action buttons often use fully circular (pill) shapes to distinguish them from structural panels.
- **Recipe/List Items:** Use the base 0.5rem radius to maintain consistency with the panel container.
- **Inputs:** Search bars and selectors use pill-shaped or 8px rounded corners to emphasize interactivity.

## Components

- **Glass Cards:** The fundamental container. Always includes a 1px `border-white/10` and `backdrop-blur-md`.
- **Profession Orbs:** Circular buttons with a 2px border. Active state features a primary gold border and an outer glow; inactive states are grayscaled at 60% opacity.
- **Interactive List Rows:** Use `transition-colors`. Hover states should use a subtle white/5 overlay, while selected states use a primary/20 background with a primary/50 border.
- **Data Tables:** Headers are sticky with a background blur. Rows feature a `divide-white/5` separator and high-contrast monospaced text for numerical values.
- **Currency Tokens:** Custom components that pair a numeric value with a small circular icon (`.coin-icon`) using the metallic gradients defined in the Color section.
- **Pill Toggles:** Used for mode switching (e.g., "Min Buyout" vs "Market Value"). The active state is a solid primary color with a dark background-dark text for maximum contrast.

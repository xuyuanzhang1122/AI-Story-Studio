import { ThemeProvider } from '@lobehub/ui'
import { Hero } from '@lobehub/ui/awesome'
import type { HeroProps } from '@lobehub/ui/awesome'

export default function HeroIsland(props: HeroProps) {
  return (
    <ThemeProvider themeMode="light">
      <div className="red-parrot-hero">
        <Hero {...props} />
      </div>
    </ThemeProvider>
  )
}

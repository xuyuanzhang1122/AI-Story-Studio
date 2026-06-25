/**
 * 剧本文本高亮工具
 *
 * 把分镜剧本里的关键内容渲染成带颜色的 HTML，
 * 模仿 MochiAni 风格：
 *  - 角色 / 场景 / 道具：根据绑定实体上色
 *  - 运镜 N[...]：青蓝标签
 *  - 画面构图 / 人物动作 / 对口型台词 / 光影 / 运镜细节：标签灰
 *  - [声纹特征:...]：灰色斜体
 *
 * 输出的 HTML 已经过 escape，可以安全地用 v-html 注入。
 */

export const HL_COLORS = {
  character: '#22D3EE', // cyan-400
  scene:     '#FB923C', // orange-400
  prop:      '#C084FC', // purple-400
  shotLabel: '#38BDF8', // sky-400
  fieldKey:  '#64748B', // slate-500
  voice:     '#94A3B8', // slate-400
} as const

const LABEL_KEYS = ['画面构图', '人物动作', '对口型台词', '光影', '运镜细节']

const escapeHtml = (s: string): string =>
  s.replace(/[&<>"']/g, c => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] as string
  ))

const escapeRegex = (s: string): string => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

/**
 * 渲染高亮 HTML
 *
 * @param scriptText  原始剧本字符串
 * @param characters  绑定到分镜的角色名列表
 * @param scenes      绑定到分镜的场景名列表
 * @param props       绑定到分镜的道具名列表
 */
export function highlightScript(
  scriptText: string,
  characters: string[] = [],
  scenes: string[] = [],
  props: string[] = [],
): string {
  if (!scriptText) return ''

  let html = escapeHtml(scriptText)

  // 1. [声纹特征:...] —— 灰色斜体
  html = html.replace(
    /\[声纹特征[:：][^\]]*\]/g,
    m => `<span style="color:${HL_COLORS.voice};font-style:italic;">${m}</span>`,
  )

  // 2. 运镜 N[...] —— 高亮蓝
  html = html.replace(
    /运镜\d+\[[^\]]*\]/g,
    m => `<span style="color:${HL_COLORS.shotLabel};font-weight:500;">${m}</span>`,
  )

  // 3. 字段关键字 画面构图: 等 —— 标签灰
  for (const key of LABEL_KEYS) {
    const re = new RegExp(escapeRegex(key) + '[:：]', 'g')
    html = html.replace(re, m => `<span style="color:${HL_COLORS.fieldKey};">${m}</span>`)
  }

  // 4. 角色 / 场景 / 道具 —— 一次性 union 匹配，长串优先避免子串覆盖
  type Token = { name: string; color: string }
  const tokens: Token[] = []
  const seen = new Set<string>()
  const push = (name: string, color: string) => {
    const n = name?.trim()
    if (!n || seen.has(n)) return
    seen.add(n)
    tokens.push({ name: n, color })
  }
  characters.forEach(n => push(n, HL_COLORS.character))
  scenes.forEach(n => push(n, HL_COLORS.scene))
  props.forEach(n => push(n, HL_COLORS.prop))

  if (tokens.length === 0) return html

  // 长 token 优先
  tokens.sort((a, b) => b.name.length - a.name.length)
  const colorMap = new Map(tokens.map(t => [escapeHtml(t.name), t.color]))
  const pattern = tokens
    .map(t => escapeRegex(escapeHtml(t.name)))
    .join('|')
  const re = new RegExp(pattern, 'g')
  html = html.replace(re, m => {
    const color = colorMap.get(m)
    return color ? `<span style="color:${color};font-weight:500;">${m}</span>` : m
  })

  return html
}

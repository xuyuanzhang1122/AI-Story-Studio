export const BRANDING = {
  appName: '红鹦鹉漫剧',
  appNameEn: 'Red Parrot Story Studio',
  currency: '积分',
  // Use SparklesIcon component instead of emoji
}

export const API_CONFIG = {
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  timeout: 30000,
}

export const ASPECT_RATIOS = [
  { label: '1:1', value: '1:1' },
  { label: '16:9', value: '16:9' },
  { label: '9:16', value: '9:16' },
  { label: '21:9', value: '21:9' },
]

export const STYLE_CODES = {
  'disney-3d': '迪士尼3D',
  cinematic: '赛璐璐',
  'korean-manga': '韩漫',
  pixar: '皮克斯',
  'miyazaki': '宫崎骏',
  'japanese-anime': '日漫',
}

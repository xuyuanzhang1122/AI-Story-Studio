import { MotionProvider, ThemeProvider } from '@lobehub/ui'
import { TypewriterEffect } from '@lobehub/ui/awesome'
import { ChatInputArea } from '@lobehub/ui/chat'
import { forwardRef, useEffect, useRef, useState, type HTMLAttributes } from 'react'

interface Props {
  canSend: boolean
  loading: boolean
  onInput: (value: string) => void
  onSend: () => void
  value: string
}

const prompts = [
  '释放你的创意！',
  '开始你的第一步！',
  '准备好搞些什么了吗？',
]

const StaticMotion = {
  span: forwardRef<HTMLSpanElement, HTMLAttributes<HTMLSpanElement> & Record<string, unknown>>(
    ({ animate, initial, transition, whileHover, whileTap, ...props }, ref) => (
      <span ref={ref} {...props} />
    )
  ),
}

export default function LobeChatInputIsland({ value, loading, canSend, onInput, onSend }: Props) {
  // 用 React 本地 state 持有输入值，textarea 受控于它（每次 onChange 同步更新，
  // 包括 IME 组词中）。Vue 的 value prop 经过异步 emit→store→watch→render 回来，
  // 若直接驱动 textarea 会在组词中途回灌覆盖、打断输入法，所以只在外部变化
  // （如发送后清空）时同步进本地 state。
  const [local, setLocal] = useState(value)
  const lastEmitted = useRef(value)

  useEffect(() => {
    if (value !== lastEmitted.current) {
      setLocal(value)
      lastEmitted.current = value
    }
  }, [value])

  const handleInput = (next: string) => {
    setLocal(next)
    lastEmitted.current = next
    onInput(next)
  }

  const isEmpty = local.trim().length === 0

  return (
    <ThemeProvider themeMode="dark">
      <MotionProvider motion={StaticMotion as any}>
        <div className="lobe-toolbox-input-shell">
          {isEmpty && (
            <TypewriterEffect
              className="lobe-toolbox-typewriter"
              color="#f8fafc"
              cursorStyle="pipe"
              deletingSpeed={36}
              pauseDuration={1500}
              sentences={prompts}
              showCursor
              textColors={['#a78bfa', '#38bdf8', '#f59e0b']}
              typingSpeed={72}
            />
          )}
          <ChatInputArea
            className="lobe-toolbox-chat-input"
            expand={false}
            heights={{
              inputHeight: 128,
              maxHeight: 180,
              minHeight: 128,
            }}
            loading={loading}
            placeholder=""
            value={local}
            onInput={handleInput}
            onSend={() => {
              if (canSend) onSend()
            }}
            bottomAddons={
              <div className="lobe-toolbox-input-footer">
                <span className="lobe-toolbox-input-hint">Enter 发送 · Shift+Enter 换行</span>
                <button
                  className="lobe-toolbox-send-button"
                  disabled={!canSend}
                  type="button"
                  onClick={() => {
                    if (canSend) onSend()
                  }}
                >
                  {loading ? '生成中' : '发送'}
                </button>
              </div>
            }
          />
        </div>
      </MotionProvider>
    </ThemeProvider>
  )
}

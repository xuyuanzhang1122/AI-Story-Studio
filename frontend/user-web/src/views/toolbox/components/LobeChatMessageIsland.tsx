import { ThemeProvider } from '@lobehub/ui'
import { ChatItem, LoadingDots } from '@lobehub/ui/chat'
import { Fragment, type ReactNode } from 'react'

type ContentType = 'TEXT' | 'IMAGE' | 'VIDEO'
type ConversationRole = 'user' | 'ai'
type AIStatus = 'GENERATING' | 'READY' | 'FAILED'

interface ConversationView {
  id: string
  timestamp: number
  role: ConversationRole
  contentType: ContentType
  userInput?: {
    type: ContentType
    model: string
    aspectRatio?: string
    prompt: string
    estimatedCost: number
    duration?: number
  }
  aiResponse?: {
    status: AIStatus
    text?: string
    resultUrl?: string
    allImageUrls?: string[]
    costPoints: number
    generationTime: number
    errorMessage?: string
    historyId?: number
  }
}

export type LobeChatMessageAction =
  | 'copy'
  | 'download'
  | 'download-image'
  | 'open'
  | 'save-character'
  | 'save-scene'

interface Props {
  conversation: ConversationView
  userAvatar?: string
  userName?: string
  onAction?: (action: LobeChatMessageAction, payload?: string | number) => void
}

const typeNames: Record<ContentType, string> = {
  IMAGE: '图片',
  TEXT: '文字',
  VIDEO: '视频',
}

const ActionButton = ({
  children,
  onClick,
}: {
  children: ReactNode
  onClick: () => void
}) => (
  <button
    className="lobe-toolbox-action"
    type="button"
    onClick={(event) => {
      event.stopPropagation()
      onClick()
    }}
  >
    {children}
  </button>
)

const renderUserMessage = (conversation: ConversationView) => {
  const input = conversation.userInput
  if (!input) return '(无提示词)'

  return (
    <div className="lobe-toolbox-user-message">
      <div className="lobe-toolbox-message-meta">
        <span>{typeNames[input.type]}</span>
        <span>{input.model}</span>
        {input.aspectRatio && <span>{input.aspectRatio}</span>}
        {input.duration && <span>{input.duration}秒</span>}
      </div>
      <div className="lobe-toolbox-message-text">{input.prompt || '(无提示词)'}</div>
    </div>
  )
}

const renderGenerating = () => (
  <div className="lobe-toolbox-loading">
    <LoadingDots color="#8b5cf6" size={7} variant="typing" />
    <span>正在生成中</span>
  </div>
)

const renderFailed = (conversation: ConversationView) => (
  <div className="lobe-toolbox-error">
    {conversation.aiResponse?.errorMessage || '生成失败'}
  </div>
)

const renderImages = (conversation: ConversationView, onAction?: Props['onAction']) => {
  const response = conversation.aiResponse
  const urls = response?.allImageUrls?.length ? response.allImageUrls : response?.resultUrl ? [response.resultUrl] : []
  if (!urls.length) return <div className="lobe-toolbox-empty-result">图片生成完成，但结果地址为空</div>

  return (
    <div className={urls.length > 1 ? 'lobe-toolbox-image-grid' : 'lobe-toolbox-image-single'}>
      {urls.map((url, index) => (
        <div className="lobe-toolbox-image-shell" key={`${url}-${index}`}>
          <img
            alt={`生成图片 ${index + 1}`}
            src={url}
            onClick={() => onAction?.('open', url)}
          />
          <div className="lobe-toolbox-media-actions">
            <ActionButton onClick={() => onAction?.('download-image', index)}>下载</ActionButton>
            <ActionButton onClick={() => onAction?.('save-character')}>角色库</ActionButton>
            <ActionButton onClick={() => onAction?.('save-scene')}>场景库</ActionButton>
          </div>
        </div>
      ))}
    </div>
  )
}

const renderVideo = (conversation: ConversationView, onAction?: Props['onAction']) => {
  const url = conversation.aiResponse?.resultUrl
  if (!url) return <div className="lobe-toolbox-empty-result">视频生成完成，但结果地址为空</div>

  return (
    <div className="lobe-toolbox-video-shell">
      <video controls preload="metadata" src={url} />
      <div className="lobe-toolbox-media-actions">
        <ActionButton onClick={() => onAction?.('download')}>下载</ActionButton>
        <ActionButton onClick={() => onAction?.('save-scene')}>保存</ActionButton>
      </div>
    </div>
  )
}

const renderAIMessage = (conversation: ConversationView, onAction?: Props['onAction']) => {
  const response = conversation.aiResponse

  if (response?.status === 'GENERATING') return renderGenerating()
  if (response?.status === 'FAILED') return renderFailed(conversation)
  if (conversation.contentType === 'IMAGE') return renderImages(conversation, onAction)
  if (conversation.contentType === 'VIDEO') return renderVideo(conversation, onAction)
  if (response?.text) return response.text

  return <div className="lobe-toolbox-empty-result">暂无生成内容</div>
}

const belowMessage = (conversation: ConversationView) => {
  if (conversation.role !== 'ai' || conversation.aiResponse?.status !== 'READY') return null

  return (
    <div className="lobe-toolbox-message-footer">
      耗时 {conversation.aiResponse.generationTime || 0}s
    </div>
  )
}

export default function LobeChatMessageIsland({
  conversation,
  userAvatar,
  userName = '用户',
  onAction,
}: Props) {
  const isUser = conversation.role === 'user'
  const aiMessage = !isUser ? renderAIMessage(conversation, onAction) : undefined
  const isRichAIMessage = !isUser && (conversation.contentType !== 'TEXT' || conversation.aiResponse?.status !== 'READY')

  return (
    <ThemeProvider themeMode="dark">
      <div className="lobe-toolbox-chat-item">
        <ChatItem
          avatar={
            isUser
              ? { avatar: userAvatar, title: userName }
              : { backgroundColor: '#8b5cf6', title: '红鹦鹉' }
          }
          avatarProps={{ size: 36 }}
          belowMessage={belowMessage(conversation)}
          fontSize={14}
          loading={conversation.aiResponse?.status === 'GENERATING'}
          message={isUser ? ' ' : isRichAIMessage ? ' ' : (aiMessage as string)}
          placement={isUser ? 'right' : 'left'}
          primary={isUser}
          renderMessage={isUser ? () => renderUserMessage(conversation) : isRichAIMessage ? () => <Fragment>{aiMessage}</Fragment> : undefined}
          showTitle
          time={conversation.timestamp}
          variant="bubble"
          actions={
            !isUser && conversation.contentType === 'TEXT' && conversation.aiResponse?.text ? (
              <ActionButton onClick={() => onAction?.('copy')}>复制</ActionButton>
            ) : null
          }
        />
      </div>
    </ThemeProvider>
  )
}

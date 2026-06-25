// ============== Common Types ==============

export interface Result<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

// ============== User & Auth ==============

export interface UserVO {
  userId: number
  nickname: string
  avatarUrl: string | null
  status: number
  balance: number
  createdAt: string
  phone?: string  // 登录时返回，profile接口不返回
}

export interface UpdateProfileRequest {
  nickname?: string
  avatarUrl?: string
}

// 登录响应（后端返回扁平结构）
export interface LoginResponse {
  token: string
  userId: number
  nickname: string
  avatarUrl: string | null
  balance: number
}

export interface SendCodeRequest {
  phone: string
}

export interface LoginRequest {
  phone: string
  code: string
  inviteCode?: string
}

// ============== Project & Folder ==============

export interface ProjectVO {
  id: number
  name: string
  folderId: number | null
  aspectRatio: string
  styleCode: string
  eraSetting: string | null
  createdAt: string
  updatedAt: string
  // Additional fields that may be returned by backend
  description?: string
  coverUrl?: string
  shotCount?: number
  title?: string  // Alias for name for compatibility
}

export interface ProjectCreateRequest {
  name: string
  folderId?: number
  aspectRatio: '16:9' | '9:16' | '21:9'
  styleCode: string
  eraSetting?: string
  rawText?: string
}

// Aliases for convenience
export type CreateProjectDTO = ProjectCreateRequest
export type UpdateProjectDTO = Partial<ProjectCreateRequest>
export type CreateFolderDTO = { name: string }
export type UpdateFolderDTO = { name: string }

export interface FolderVO {
  id: number
  name: string
  createdAt: string
  coverUrl?: string
  projectCount?: number
}

// ============== Style Preset ==============

export interface StylePresetVO {
  id: number
  code: string
  name: string
  thumbnailUrl: string | null
  promptPrefix: string | null
  sortOrder: number
  enabled: number  // 1=enabled, 0=disabled
  createdAt: string
}

// ============== Storyboard ==============

export interface StoryboardShotVO {
  id: number
  shotNo: number
  scriptText: string
  characters: BoundCharacterVO[]
  scene: BoundSceneVO | null
  props: BoundPropVO[]
  shotImage: AssetStatusVO
  video: AssetStatusVO
  createdAt: string
  updatedAt: string
}

export interface BoundCharacterVO {
  bindingId: number
  characterId: number
  characterName: string
  thumbnailUrl: string | null
}

export interface BoundSceneVO {
  bindingId: number
  sceneId: number
  sceneName: string
  thumbnailUrl: string | null
}

export interface AssetStatusVO {
  assetId: number | null
  currentVersionId: number | null
  currentUrl: string | null
  status: 'NONE' | 'GENERATING' | 'READY' | 'FAILED'
  totalVersions: number
}

// ============== Character ==============

export interface CharacterLibraryVO {
  id: number
  categoryId: number | null
  name: string
  description: string
  thumbnailUrl: string | null
  createdAt: string
}

export interface ProjectCharacterVO {
  id: number
  projectId?: number
  libraryCharacterId: number | null
  libraryCharacterName?: string
  displayName: string
  name?: string  // 别名，兼容旧代码
  finalDescription?: string
  description?: string  // 别名，兼容旧代码
  overrideDescription?: string
  thumbnailUrl: string | null
  isActive?: boolean
  createdAt: string
}

export interface CharacterCategoryVO {
  id: number
  name: string
  createdAt: string
}

// Character Library CRUD DTOs
export interface CreateCharacterLibraryDTO {
  name: string
  categoryId?: number
  description?: string
}

export interface UpdateCharacterLibraryDTO {
  name?: string
  categoryId?: number | null
  description?: string
  thumbnailUrl?: string | null
}

export interface CreateCharacterCategoryDTO {
  name: string
}

export interface UpdateCharacterCategoryDTO {
  name: string
}

// Project Character DTOs
export interface CreateCharacterDTO {
  name: string
  description: string
  characterLibraryId?: number
}

export interface UpdateCharacterDTO {
  name?: string
  description?: string
  isActive?: boolean
  thumbnailUrl?: string | null
}

export interface GenerateCharacterDTO {
  prompt?: string
  aspectRatio?: '1:1' | '16:9' | '9:16' | '21:9'
  model?: string
}

// ============== Scene ==============

export interface SceneLibraryVO {
  id: number
  categoryId: number | null
  name: string
  description: string
  thumbnailUrl: string | null
  createdAt: string
}

export interface ProjectSceneVO {
  id: number
  projectId: number
  librarySceneId: number | null
  librarySceneName: string
  displayName: string
  finalDescription: string | null
  overrideDescription: string | null
  thumbnailUrl: string | null
  createdAt: string
}

export interface SceneCategoryVO {
  id: number
  name: string
  createdAt: string
}

// Scene Library CRUD DTOs
export interface CreateSceneLibraryDTO {
  categoryId?: number
  name: string
  description?: string
  thumbnailUrl?: string | null
}

export interface UpdateSceneLibraryDTO {
  categoryId?: number | null
  name?: string
  description?: string
  thumbnailUrl?: string | null
}

export interface CreateSceneCategoryDTO {
  name: string
}

export interface UpdateSceneCategoryDTO {
  name: string
}

// Project Scene DTOs
export interface CreateSceneDTO {
  name: string
  description: string
  sceneLibraryId?: number
}

export interface UpdateSceneDTO {
  name?: string
  description?: string
  isActive?: boolean
  thumbnailUrl?: string | null
}

export interface GenerateSceneDTO {
  prompt?: string
  aspectRatio?: '1:1' | '16:9' | '9:16' | '21:9'
  model?: string
}

// ============== Prop (道具) ==============

export interface PropLibraryVO {
  id: number
  categoryId: number | null
  categoryName: string | null
  name: string
  description: string | null
  thumbnailUrl: string | null
  referenceCount: number
  createdAt: string
  updatedAt: string
}

export interface ProjectPropVO {
  id: number
  libraryPropId: number
  displayName: string | null
  name: string
  description: string | null
  thumbnailUrl: string | null
  createdAt: string
}

export interface PropCategoryVO {
  id: number
  name: string
  sortOrder: number
  propCount: number
  createdAt: string
  updatedAt: string
}

export interface BoundPropVO {
  bindingId: number
  propId: number
  propName: string
  thumbnailUrl: string | null
}

// Prop Library CRUD DTOs
export interface CreatePropLibraryDTO {
  categoryId?: number
  name: string
  description?: string
  thumbnailUrl?: string | null
}

export interface UpdatePropLibraryDTO {
  categoryId?: number | null
  name?: string
  description?: string
  thumbnailUrl?: string | null
}

export interface CreatePropCategoryDTO {
  name: string
}

export interface UpdatePropCategoryDTO {
  name: string
}

// Project Prop DTOs
export interface CreatePropDTO {
  name: string
  description: string
  propLibraryId?: number
}

export interface UpdatePropDTO {
  name?: string
  description?: string
  thumbnailUrl?: string | null
}

export interface AddPropToProjectDTO {
  libraryPropId?: number | null
  displayName?: string
  overrideDescription?: string
  thumbnailUrl?: string | null
}

export interface GeneratePropDTO {
  prompt?: string
  aspectRatio?: '1:1' | '16:9' | '9:16' | '21:9'
  model?: string
}

// ============== Asset ==============

export interface AssetVersionVO {
  id: number
  assetId: number
  versionNo: number
  source: 'AI' | 'UPLOAD' | 'IMPORT'
  provider: 'OSS' | 'MINIO'
  url: string
  objectKey: string | null
  prompt: string | null
  paramsJson: string | null
  status: 'READY' | 'FAILED'
  isCurrent: boolean
  createdBy: number | null
  createdAt: string
}

export interface SetCurrentVersionRequest {
  versionId: number
}

// ============== Job ==============

export interface JobVO {
  id: number
  projectId?: number
  projectName?: string
  jobType: string
  status: 'PENDING' | 'RUNNING' | 'GENERATING' | 'COMPLETED' | 'SUCCEEDED' | 'FAILED' | 'CANCELED'
  progress: number
  totalItems: number
  doneItems: number
  elapsedSeconds?: number
  startedAt?: string
  finishedAt?: string
  errorMessage: string | null
  resultUrl: string | null
  allImageUrls: string[] | null
  costPoints: number | null
  createdAt: string
}

// ============== Generation ==============

export interface BatchGenerateRequest {
  targetIds: number[]
  mode: 'ALL' | 'MISSING'
  countPerItem: number
  aspectRatio?: '1:1' | '16:9' | '9:16' | '21:9'
  model?: string
}

export interface BatchGenerateResponse {
  jobId: number
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  totalItems: number
  message: string
}

// ============== Toolbox ==============

export interface ToolboxGenerateRequest {
  type: 'TEXT' | 'IMAGE' | 'VIDEO'
  prompt: string
  model?: string
  aspectRatio?: '1:1' | '16:9' | '9:16' | '21:9'
  duration?: number
  referenceImageUrl?: string
}

export interface ToolboxGenerateResponse {
  jobId: number | null
  status: string
  type: string
  model: string
  text: string | null
  resultUrl: string | null
  aspectRatio: string | null
  costPoints: number | null
}

export interface ToolboxHistoryVO {
  id: number
  type: string
  model: string
  prompt: string
  aspectRatio: string | null
  text: string | null  // For TEXT type results
  resultUrl: string | null
  status: string
  costPoints: number
  createdAt: string
  expireAt: string
}

// ============== Invite ==============

export interface InviteCodeVO {
  id: number
  code: string
  rewardPoints: number
  inviterRewardPoints: number
  usedCount: number
  maxUses: number | null
  expireAt: string | null
  enabled: number
  createdAt: string
}

export interface InviteInfoVO {
  code: string
  inviteLink: string
  rewardPoints: number
  inviterRewardPoints: number
  usedCount: number
  maxUses: number | null
  totalInvited: number
  totalRewardsEarned: number
}

export interface InviteRecordVO {
  id: number
  inviteePhone: string
  inviteeNickname: string
  registeredAt: string
  rewardPoints: number
}

export interface InviteStatsVO {
  totalInvited: number
  totalRewardsEarned: number
}

// ============== Wallet ==============

export interface WalletVO {
  balance: number
  frozenBalance: number
}

export interface TransactionVO {
  id: number
  type: string
  amount: number
  balance: number
  description: string
  createdAt: string
}

// ============== Recharge & Payment ==============

export interface RechargeProductVO {
  id: number
  name: string
  points: number
  priceCents: number
  enabled: number
  sortOrder: number
  description?: string
}

export interface ExchangeRuleVO {
  id: number
  name: string
  pointsPerYuan: number  // 每1元兑换多少积分
  enabled: number
}

export interface CreateOrderRequest {
  productId?: number  // 充值商品ID (与amountCents二选一)
  amountCents?: number  // 自定义金额-分 (与productId二选一)
  clientIp?: string
}

export interface NativeOrderVO {
  orderNo: string
  codeUrl: string  // 二维码URL
  amountCents: number
  points: number
  expireAt: string
}

export interface OrderStatusVO {
  orderNo: string
  status: 'CREATED' | 'SUCCEEDED' | 'FAILED' | 'CANCELED'
  amountCents: number
  points: number
  paidAt: string | null
  createdAt: string
}

// ============== Prompt Template ==============

export interface PromptTemplateVO {
  id: number
  name: string
  content: string
  category: string
  createdAt: string
}

// ============== Export ==============

export interface ExportRequest {
  exportCharacters: boolean
  exportScenes: boolean
  exportShotImages: boolean
  exportVideos: boolean
  mode: 'CURRENT' | 'ALL'
}

export interface ExportResponse {
  jobId: number
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  message: string
}

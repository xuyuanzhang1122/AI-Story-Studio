<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import api from '@/api/index'
import { useEditorStore } from '@/stores/editor'
import { characterApi, generationApi, jobApi, sceneApi, shotApi, propApi, uploadApi } from '@/api/apis'
import type { AssetVersionVO } from '@/types/api'

const editorStore = useEditorStore()

// Props定义
const props = defineProps<{
  assetId?: number
  assetType?: 'character' | 'scene' | 'prop' | 'shot' | 'video'
  prefillDescription?: string  // 预填充的描述
  characterName?: string  // 角色名称
  sceneName?: string  // 场景名称
  propName?: string  // 道具名称
  shotId?: number  // 关联的分镜ID
  existingThumbnailUrl?: string | null  // 已有的缩略图URL
  existingDescription?: string | null  // 已有的描述
}>()

// Emits定义
const emit = defineEmits<{
  close: []
}>()

// 计算标题文本
const titleText = computed(() => {
  if (props.characterName) {
    return `角色: ${props.characterName}`
  }
  if (props.sceneName) {
    return `场景: ${props.sceneName}`
  }
  if (props.propName) {
    return `道具: ${props.propName}`
  }
  const typeMap = {
    'character': '角色列表',
    'scene': '场景列表',
    'prop': '道具列表',
    'shot': '分镜列表',
    'video': '视频列表'
  }
  return typeMap[props.assetType || 'character']
})

// 计算资产类型文本
const assetTypeText = computed(() => {
  const typeMap = {
    'character': '角色',
    'scene': '场景',
    'prop': '道具',
    'shot': '分镜',
    'video': '视频'
  }
  return typeMap[props.assetType || 'character']
})

// 当前资产类型
const assetType = computed(() => props.assetType || 'character')

// 标签页状态
const activeTab = ref<'custom' | 'local' | 'library'>('custom')

// AI描述
const aiDescription = ref('')
const isParsingCharacter = ref(false)  // 角色解析中状态
const isParsingScene = ref(false)  // 场景解析中状态
const parseError = ref(false)  // 解析失败状态
const currentCharacterName = ref('')  // 当前解析的角色名
const currentSceneName = ref('')  // 当前解析的场景名
const currentPropName = ref('')  // 当前解析的道具名
const currentScriptText = ref('')  // 当前解析的文本
const currentSceneId = ref<number | undefined>(props.assetId)  // 当前操作的场景ID
const currentPropId = ref<number | undefined>(props.assetId)  // 当前操作的道具ID

const isParsingProp = ref(false)  // 道具解析中状态

// 是否正在解析
const isParsing = computed(() => isParsingCharacter.value || isParsingScene.value || isParsingProp.value)

// AI解析人物形象（带重试机制）
const parseCharacterDescription = async (scriptText: string, characterName: string, retryCount = 0) => {
  if (!editorStore.projectId) {
    // 没有projectId，给一个默认描述
    aiDescription.value = `${characterName}，一个角色`
    return
  }
  
  // 保存当前解析信息，用于重试
  currentCharacterName.value = characterName
  currentScriptText.value = scriptText
  
  // 检查缓存，如果已解析过该角色，直接使用缓存
  const cachedDescription = editorStore.characterDescriptionCache.get(characterName)
  if (cachedDescription) {
    console.log('[AssetEditPanel] 使用缓存的角色描述:', characterName)
    aiDescription.value = cachedDescription
    parseError.value = false
    return
  }
  
  isParsingCharacter.value = true
  parseError.value = false
  
  try {
    const prompt = `请根据以下文本内容，提取角色「${characterName}」的外貌形象描述，用于AI绘图。

要求：
1. 只描述该角色的外貌形象（如性别、年龄段、身材、发型发色、五官特征、服装穿着等）
2. 不要描述表情、动作、性格、物品、道具
3. 用简洁的描述语，适合AI绘图理解
4. 如果文本中没有该角色的外貌描述，请根据角色名称和场景合理推断
5. 最后要添加姿势描述：正面站立或半身正面照，面向镜头
6. 直接输出描述文本，不要其他说明

文本内容：
${scriptText}`

    const response = await api.post('/generate/text', {
      prompt,
      temperature: 0.7,
      topP: 0.9,
      projectId: editorStore.projectId
    }, {
      timeout: 120000  // AI文本生成需要更长时间，120秒超时
    })
    
    if (response?.text) {
      const description = response.text.trim()
      aiDescription.value = description
      // 保存到缓存
      editorStore.characterDescriptionCache.set(characterName, description)
      console.log('[AssetEditPanel] 角色描述已缓存:', characterName)
    } else {
      // 给一个默认描述
      aiDescription.value = `${characterName}，一个角色`
    }
  } catch (error) {
    console.error('[AssetEditPanel] 解析人物形象失败:', error)
    
    // 重试机制：最多重试2次
    if (retryCount < 2) {
      console.log(`[AssetEditPanel] 重试解析 (${retryCount + 1}/2)...`)
      isParsingCharacter.value = false
      await new Promise(resolve => setTimeout(resolve, 1000))  // 等待1秒后重试
      return parseCharacterDescription(scriptText, characterName, retryCount + 1)
    }
    
    // 重试失败，给默认描述并标记错误
    aiDescription.value = `${characterName}，一个角色`
    parseError.value = true
    window.$message?.warning('AI解析失败，请手动填写描述或点击重试')
  } finally {
    isParsingCharacter.value = false
  }
}

// 手动重试解析
const handleRetryParse = () => {
  if (props.assetType === 'scene') {
    if (currentSceneName.value && currentScriptText.value) {
      parseSceneDescription(currentScriptText.value, currentSceneName.value)
    }
  } else if (props.assetType === 'prop') {
    if (currentPropName.value && currentScriptText.value) {
      parsePropDescription(currentScriptText.value, currentPropName.value)
    }
  } else {
    if (currentCharacterName.value && currentScriptText.value) {
      parseCharacterDescription(currentScriptText.value, currentCharacterName.value)
    }
  }
}

// AI解析道具描述（带重试机制）
const parsePropDescription = async (scriptText: string, propName: string, retryCount = 0) => {
  if (!editorStore.projectId) {
    // 没有projectId，给一个默认描述
    aiDescription.value = `${propName}，一个道具`
    return
  }
  
  // 保存当前解析信息，用于重试
  currentPropName.value = propName
  currentScriptText.value = scriptText
  
  isParsingProp.value = true
  parseError.value = false
  
  try {
    const prompt = `请根据以下文本内容，提取道具「${propName}」的外观描述，用于AI绘图。

要求：
1. 只描述道具的外观特征（如材质、颜色、形状、大小、装饰细节等）
2. 不要描述任何人物或场景
3. 用简洁的描述语，适合AI绘图理解
4. 如果文本中没有该道具的描述，请根据道具名称合理推断
5. 不要输出任何风格指令
6. 直接输出道具描述文本，不要其他说明

文本内容：
${scriptText}`

    const response = await api.post('/generate/text', {
      prompt,
      temperature: 0.7,
      topP: 0.9,
      projectId: editorStore.projectId
    }, {
      timeout: 120000
    })
    
    if (response?.text) {
      const description = response.text.trim()
      aiDescription.value = description
      console.log('[AssetEditPanel] 道具描述解析完成:', propName)
    } else {
      aiDescription.value = `${propName}，一个道具`
    }
  } catch (error) {
    console.error('[AssetEditPanel] 解析道具失败:', error)
    
    if (retryCount < 2) {
      console.log(`[AssetEditPanel] 重试解析道具 (${retryCount + 1}/2)...`)
      isParsingProp.value = false
      await new Promise(resolve => setTimeout(resolve, 1000))
      return parsePropDescription(scriptText, propName, retryCount + 1)
    }
    
    aiDescription.value = `${propName}，一个道具`
    parseError.value = true
    window.$message?.warning('AI解析失败，请手动填写描述或点击重试')
  } finally {
    isParsingProp.value = false
  }
}

// AI解析场景描述（带重试机制）
// 注意：场景描述不使用缓存，因为每条分镜的剧本内容不同，需要重新解析
const parseSceneDescription = async (scriptText: string, sceneName: string, retryCount = 0) => {
  if (!editorStore.projectId) {
    // 没有projectId，给一个默认描述
    aiDescription.value = `${sceneName}，一个场景`
    return
  }
  
  // 保存当前解析信息，用于重试
  currentSceneName.value = sceneName
  currentScriptText.value = scriptText
  
  isParsingScene.value = true
  parseError.value = false
  
  try {
    const prompt = `请根据以下文本内容，提取场景「${sceneName}」的环境描述，用于AI绘图。

要求：
1. 只描述场景的环境特征（如地点、天气、光线、建筑、植物、氛围、室内陈设等）
2. 不要描述任何人物或动物
3. 用简洁的描述语，适合AI绘图理解
4. 如果文本中没有该场景的描述，请根据场景名称合理推断
5. 不要输出任何风格指令（如"2D动漫风格"、"高质量"、"细腻画质"等）
6. 直接输出场景描述文本，不要其他说明

文本内容：
${scriptText}`

    const response = await api.post('/generate/text', {
      prompt,
      temperature: 0.7,
      topP: 0.9,
      projectId: editorStore.projectId
    }, {
      timeout: 120000  // AI文本生成需要更长时间，120秒超时
    })
    
    if (response?.text) {
      const description = response.text.trim()
      aiDescription.value = description
      // 场景描述不缓存，因为每条分镜的内容不同
      console.log('[AssetEditPanel] 场景描述解析完成:', sceneName)
    } else {
      // 给一个默认描述
      aiDescription.value = `${sceneName}，一个场景`
    }
  } catch (error) {
    console.error('[AssetEditPanel] 解析场景失败:', error)
    
    // 重试机制：最多重试2次
    if (retryCount < 2) {
      console.log(`[AssetEditPanel] 重试解析场景 (${retryCount + 1}/2)...`)
      isParsingScene.value = false
      await new Promise(resolve => setTimeout(resolve, 1000))
      return parseSceneDescription(scriptText, sceneName, retryCount + 1)
    }
    
    // 重试失败，给默认描述并标记错误
    aiDescription.value = `${sceneName}，一个场景`
    parseError.value = true
    window.$message?.warning('AI解析失败，请手动填写描述或点击重试')
  } finally {
    isParsingScene.value = false
  }
}

// 组件挂载时自动解析
onMounted(async () => {
  console.log('[AssetEditPanel] Mounted with props:', {
    assetType: props.assetType,
    prefillDescription: props.prefillDescription,
    characterName: props.characterName,
    sceneName: props.sceneName,
    propName: props.propName,
    projectId: editorStore.projectId,
    existingThumbnailUrl: props.existingThumbnailUrl,
    existingDescription: props.existingDescription
  })
  
  // 如果有已存在的缩略图，检查是否为有效URL后再显示（只接受 HTTP/HTTPS）
  if (props.existingThumbnailUrl) {
    const isValidUrl = props.existingThumbnailUrl.startsWith('http://') || 
                       props.existingThumbnailUrl.startsWith('https://')
    if (isValidUrl) {
      generatedImageUrl.value = props.existingThumbnailUrl
    }
  }
  
  // 如果有已存在的描述，直接使用
  if (props.existingDescription) {
    aiDescription.value = props.existingDescription
    if (props.assetType === 'scene') {
      currentSceneName.value = props.sceneName || ''
    } else if (props.assetType === 'prop') {
      currentPropName.value = props.propName || ''
    } else {
      currentCharacterName.value = props.characterName || ''
    }
  } else if (props.prefillDescription) {
    // 根据资产类型调用不同的解析函数
    if (props.assetType === 'scene' && props.sceneName) {
      await parseSceneDescription(props.prefillDescription, props.sceneName)
    } else if (props.assetType === 'prop' && props.propName) {
      await parsePropDescription(props.prefillDescription, props.propName)
    } else if (props.characterName) {
      await parseCharacterDescription(props.prefillDescription, props.characterName)
    } else {
      aiDescription.value = props.prefillDescription
    }
  }
  
  // 加载历史记录
  await loadGenerationHistory()
})

// 监听 assetId 变化时同步更新 currentCharacterId
watch(() => props.assetId, (newId) => {
  if (newId) {
    currentCharacterId.value = newId
  }
})

// 监听标签页切换，自动加载库资产
watch(() => activeTab.value, async (newTab) => {
  if (newTab === 'library' && libraryAssets.value.length === 0) {
    await loadLibraryAssets()
  }
})



// 监听 store 数据变化，同步更新 generatedImageUrl
watch(() => [editorStore.props, editorStore.scenes, editorStore.characters], () => {
  let newUrl: string | null | undefined = null
  
  if (props.assetType === 'prop') {
    const p = (editorStore.props || []).find((x: any) => x.id === (currentPropId.value || props.assetId))
    newUrl = p?.thumbnailUrl
  } else if (props.assetType === 'scene') {
    const s = editorStore.scenes.find(x => x.id === (currentSceneId.value || props.assetId))
    newUrl = s?.thumbnailUrl
  } else {
    const c = editorStore.characters.find(x => x.id === (currentCharacterId.value || props.assetId))
    newUrl = c?.thumbnailUrl
  }

  // 只有当新URL有效且不同于当前URL时才更新
  // 注意：如果是本地blob预览中，则不覆盖，除非store更新的是远程URL
  if (newUrl && (newUrl.startsWith('http') || newUrl.startsWith('blob:'))) {
    // 防止覆盖用户刚上传但还没保存的图（如果是同一个URL则无所谓）
    if (newUrl !== generatedImageUrl.value) {
       generatedImageUrl.value = newUrl
    }
  }
}, { deep: true })

// 参考图文件
const referenceImage = ref<File | null>(null)
const referenceImageUrl = ref<string>('')

// 生成的图片预览
const generatedImageUrl = ref<string>('')

// 当前操作的角色ID（AI生成后会更新为新角色ID）
const currentCharacterId = ref<number | undefined>(props.assetId)

// 比例选择
const aspectRatio = ref('16:9')
const aspectRatioOptions = [
  { label: '16:9', value: '16:9' },
  { label: '1:1', value: '1:1' },
  { label: '9:16', value: '9:16' },
  { label: '4:3', value: '4:3' }
]

// 历史记录（后端）
const generationHistory = ref<AssetVersionVO[]>([])
const loadingHistory = ref(false)

// 库资产列表
const loadingLibraryAssets = ref(false)
const libraryAssets = ref<any[]>([])

// 加载库资产
const loadLibraryAssets = async () => {
  loadingLibraryAssets.value = true
  try {
    if (props.assetType === 'character') {
      const assets = await characterApi.getLibraryCharacters()
      libraryAssets.value = assets.filter(a => a.thumbnailUrl)
    } else if (props.assetType === 'scene') {
      const assets = await sceneApi.getLibraryScenes()
      libraryAssets.value = assets.filter(a => a.thumbnailUrl)
    } else if (props.assetType === 'prop') {
      const assets = await propApi.getLibraryProps()
      libraryAssets.value = assets.filter(a => a.thumbnailUrl)
    }
    console.log('[AssetEditPanel] 已加载库资产:', libraryAssets.value.length)
  } catch (error) {
    console.error('[AssetEditPanel] 加载库资产失败:', error)
    libraryAssets.value = []
  } finally {
    loadingLibraryAssets.value = false
  }
}

// 点击库资产图片
const handleLibraryAssetClick = async (asset: any) => {
  console.log('[AssetEditPanel] 选择库资产:', asset)
  
  // 如果是道具，先查找或创建项目道具
  if (props.assetType === 'prop' && editorStore.projectId) {
    try {
      // 检查是否已经引用到项目
      const existingProp = editorStore.props.find((p: any) => p.libraryPropId === asset.id)
      
      if (existingProp) {
        // 已存在，直接使用
        currentPropId.value = existingProp.id
        console.log('[AssetEditPanel] 使用已存在的项目道具:', existingProp.id)
      } else {
        // 不存在，创建新的项目道具引用
        const projectProp = await propApi.addPropToProject(editorStore.projectId, {
          libraryPropId: asset.id,
          displayName: asset.name
        })
        currentPropId.value = projectProp.id
        console.log('[AssetEditPanel] 创建新的项目道具:', projectProp.id)
        
        // 如果有关联的分镜ID，自动绑定道具到分镜
        if (props.shotId) {
          try {
            await shotApi.createBinding(editorStore.projectId, props.shotId, 'PPROP', projectProp.id)
            console.log('[AssetEditPanel] 道具已绑定到分镜:', props.shotId)
          } catch (error) {
            console.error('[AssetEditPanel] 绑定道具到分镜失败:', error)
          }
        }
        
        // 刷新道具列表和分镜数据
        await Promise.all([
          editorStore.fetchProps(),
          editorStore.fetchShots()
        ])
      }
    } catch (error) {
      console.error('[AssetEditPanel] 处理库道具失败:', error)
    }
  } else if (props.assetType === 'scene' && editorStore.projectId) {
    // 场景处理逻辑
    try {
      const existingScene = editorStore.scenes.find((s: any) => s.librarySceneId === asset.id)
      
      if (existingScene) {
        currentSceneId.value = existingScene.id
        console.log('[AssetEditPanel] 使用已存在的项目场景:', existingScene.id)
      } else {
        const projectScene = await api.post(`/projects/${editorStore.projectId}/scenes`, {
          librarySceneId: asset.id,
          displayName: asset.name
        })
        currentSceneId.value = projectScene.id
        console.log('[AssetEditPanel] 创建新的项目场景:', projectScene.id)
        
        if (props.shotId) {
          try {
            await shotApi.createBinding(editorStore.projectId, props.shotId, 'PSCENE', projectScene.id)
            console.log('[AssetEditPanel] 场景已绑定到分镜:', props.shotId)
          } catch (error) {
            console.error('[AssetEditPanel] 绑定场景到分镜失败:', error)
          }
        }
        
        await Promise.all([
          editorStore.fetchScenes(),
          editorStore.fetchShots()
        ])
      }
    } catch (error) {
      console.error('[AssetEditPanel] 处理库场景失败:', error)
    }
  } else if (props.assetType === 'character' && editorStore.projectId) {
    // 角色处理逻辑
    try {
      const existingChar = editorStore.characters.find((c: any) => c.libraryCharacterId === asset.id)
      
      if (existingChar) {
        currentCharacterId.value = existingChar.id
        console.log('[AssetEditPanel] 使用已存在的项目角色:', existingChar.id)
      } else {
        const projectChar = await api.post(`/projects/${editorStore.projectId}/characters`, {
          libraryCharacterId: asset.id,
          displayName: asset.name
        })
        currentCharacterId.value = projectChar.id
        console.log('[AssetEditPanel] 创建新的项目角色:', projectChar.id)
        
        await Promise.all([
          editorStore.fetchCharacters(),
          editorStore.fetchShots()
        ])
      }
    } catch (error) {
      console.error('[AssetEditPanel] 处理库角色失败:', error)
    }
  }
  
  // 更新大图预览
  generatedImageUrl.value = asset.thumbnailUrl
  
  // 更新参考图
  referenceImageUrl.value = asset.thumbnailUrl
  
  // 更新描述
  if (asset.description) {
    aiDescription.value = asset.description
  }
  
  // 同步到服务器，使用 handleHistoryImageClick 的逻辑
  await handleHistoryImageClick({
    url: asset.thumbnailUrl,
    prompt: asset.description || null
  })
  
  // 重新加载历史记录，使用新的 assetId
  await loadGenerationHistory()
  
  window.$message?.success(`已选择${assetTypeText.value}：${asset.name}`)
}

// 获取同名角色的所有ID（用于合并历史记录）
const getSameNameCharacterIds = computed(() => {
  const charName = props.characterName || currentCharacterName.value
  if (!charName) {
    const charId = currentCharacterId.value || props.assetId
    return charId ? [charId] : []
  }
  // 找出所有同名角色的ID
  const sameNameChars = editorStore.characters.filter(c => {
    const name = (c as any).displayName || c.name
    return name === charName
  })
  return sameNameChars.map(c => c.id)
})

// 获取同名场景的所有ID（用于合并历史记录）
const getSameNameSceneIds = computed(() => {
  const scnName = props.sceneName || currentSceneName.value
  console.log('[AssetEditPanel] getSameNameSceneIds:', {
    propsSceneName: props.sceneName,
    currentSceneName: currentSceneName.value,
    currentSceneId: currentSceneId.value,
    propsAssetId: props.assetId
  })
  if (!scnName) {
    const scnId = currentSceneId.value || props.assetId
    console.log('[AssetEditPanel] 无场景名，使用ID:', scnId)
    return scnId ? [scnId] : []
  }
  // 找出所有同名场景的ID
  const sameNameScenes = editorStore.scenes.filter(s => {
    const name = (s as any).displayName || s.name
    return name === scnName
  })
  const ids = sameNameScenes.map(s => s.id)
  console.log('[AssetEditPanel] 同名场景结果:', scnName, ids)
  return ids
})

// 获取同名道具的所有ID（用于合并历史记录）
const getSameNamePropIds = computed(() => {
  const prpName = props.propName || currentPropName.value
  if (!prpName) {
    const prpId = currentPropId.value || props.assetId
    return prpId ? [prpId] : []
  }
  // 找出所有同名道具的ID
  const sameNameProps = (editorStore.props || []).filter((p: any) => {
    const name = p.displayName || p.name
    return name === prpName
  })
  return sameNameProps.map((p: any) => p.id)
})

// 检查是否为有效的图片URL（接受 HTTP/HTTPS 和本地 blob URL）
const isValidImageUrl = (url: string | null | undefined): boolean => {
  if (!url) return false
  return url.startsWith('http://') || url.startsWith('https://') || url.startsWith('blob:')
}

// 检查是否为可上传到服务器的URL（只接受 HTTP/HTTPS）
const isServerValidUrl = (url: string | null | undefined): boolean => {
  if (!url) return false
  return url.startsWith('http://') || url.startsWith('https://')
}

// 合并历史记录（后端 + store中的本地记录，同名资产共享）
const allHistory = computed(() => {
  // 根据资产类型获取对应的ID列表
  let assetIds: number[] = []
  let assetTypeKey = 'character'
  
  if (props.assetType === 'scene') {
    assetIds = getSameNameSceneIds.value
    assetTypeKey = 'scene'
  } else if (props.assetType === 'prop') {
    assetIds = getSameNamePropIds.value
    assetTypeKey = 'prop'
  } else {
    assetIds = getSameNameCharacterIds.value
    assetTypeKey = 'character'
  }
  
  console.log('[AssetEditPanel] allHistory 计算:', {
    assetType: props.assetType,
    assetIds,
    generationHistoryCount: generationHistory.value.length,
    shotId: props.shotId
  })
  
  // 合并所有同名资产的本地历史记录，只保留有效URL
  const allLocalRecords: { id: number; url: string; prompt: string | null; versionNo: number; createdAt: string }[] = []
  assetIds.forEach(assetId => {
    // 传递 shotId 以获取隔离的历史记录，同时也会获取通用的历史记录
    // 对于 props.assetType 是 prop 的情况，shotId 是必须的隔离条件
    // 对于其他类型，shotId 也是推荐的隔离条件
    const records = editorStore.getLocalImageHistory(
      assetTypeKey as 'character' | 'scene' | 'prop', 
      assetId,
      props.shotId
    )
    // 过滤出有效的图片URL
    const validRecords = records.filter(r => isValidImageUrl(r.url))
    allLocalRecords.push(...validRecords)
  })
  
  const combined = [
    ...generationHistory.value.map(v => ({
      id: v.id,
      url: v.url,
      prompt: v.prompt,
      versionNo: v.versionNo,
      createdAt: v.createdAt,
      source: 'server' as const
    })),
    ...allLocalRecords.map(v => ({
      ...v,
      source: 'local' as const
    }))
  ]
  // 按创建时间倒序排列，并去重（根据 URL）
  const unique = combined.reduce((acc, curr) => {
    if (!acc.find(item => item.url === curr.url)) {
      acc.push(curr)
    }
    return acc
  }, [] as typeof combined)
  
  const result = unique.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
  console.log('[AssetEditPanel] allHistory 结果:', result.length)
  return result
})

// 加载历史记录（角色/场景/道具的历史记录主要来自本地历史）
const loadGenerationHistory = async () => {
  if (!editorStore.projectId) {
    generationHistory.value = []
    return
  }
  
  loadingHistory.value = true
  try {
    // 确保场景/角色/道具数据已加载
    if (props.assetType === 'scene' && editorStore.scenes.length === 0) {
      await editorStore.fetchScenes()
    } else if (props.assetType === 'prop' && (!editorStore.props || editorStore.props.length === 0)) {
      await editorStore.fetchProps()
    } else if (props.assetType !== 'scene' && props.assetType !== 'prop' && editorStore.characters.length === 0) {
      await editorStore.fetchCharacters()
    }
    
    const existingRecords: AssetVersionVO[] = []
    
    if (props.assetType === 'scene') {
      // 场景历史记录
      const sceneIds = getSameNameSceneIds.value
      
      for (const sceneId of sceneIds) {
        const scene = editorStore.scenes.find(s => s.id === sceneId)
        if (scene?.thumbnailUrl && isValidImageUrl(scene.thumbnailUrl)) {
          // 检查是否已经在本地历史中
          const localHistory = editorStore.getLocalImageHistory('scene', sceneId, props.shotId)
          const alreadyInHistory = localHistory.some(r => r.url === scene.thumbnailUrl)
          
          if (!alreadyInHistory) {
            existingRecords.push({
              id: sceneId,
              url: scene.thumbnailUrl,
              prompt: (scene as any).finalDescription || (scene as any).description || null,
              versionNo: 1,
              status: 'READY',
              createdAt: scene.createdAt
            } as AssetVersionVO)
          }
        }
      }
    } else if (props.assetType === 'prop') {
      // 道具历史记录
      const propIds = getSameNamePropIds.value
      
      for (const propId of propIds) {
        const prop = (editorStore.props || []).find((p: any) => p.id === propId)
        if (prop?.thumbnailUrl && isValidImageUrl(prop.thumbnailUrl)) {
          const localHistory = editorStore.getLocalImageHistory('prop', propId, props.shotId)
          const alreadyInHistory = localHistory.some(r => r.url === prop.thumbnailUrl)
          
          if (!alreadyInHistory) {
            existingRecords.push({
              id: propId,
              url: prop.thumbnailUrl,
              prompt: (prop as any).description || null,
              versionNo: 1,
              status: 'READY',
              createdAt: prop.createdAt
            } as AssetVersionVO)
          }
        }
      }
    } else {
      // 角色历史记录
      const charIds = getSameNameCharacterIds.value
      
      for (const charId of charIds) {
        const character = editorStore.characters.find(c => c.id === charId)
        if (character?.thumbnailUrl && isValidImageUrl(character.thumbnailUrl)) {
          // 检查是否已经在本地历史中
          const localHistory = editorStore.getLocalImageHistory('character', charId, props.shotId)
          const alreadyInHistory = localHistory.some(r => r.url === character.thumbnailUrl)
          
          if (!alreadyInHistory) {
            existingRecords.push({
              id: charId,
              url: character.thumbnailUrl,
              prompt: (character as any).finalDescription || (character as any).description || null,
              versionNo: 1,
              status: 'READY',
              createdAt: character.createdAt
            } as AssetVersionVO)
          }
        }
      }
    }
    
    generationHistory.value = existingRecords
    console.log('[AssetEditPanel] 已加载历史记录:', generationHistory.value.length)
  } catch (error) {
    console.error('[AssetEditPanel] 加载历史记录失败:', error)
    generationHistory.value = []
  } finally {
    loadingHistory.value = false
  }
}

// ... (code omitted)

// 在 updateAssetWithImage 方法被调用处，添加历史记录也需要传递 shotId
// 但目前我们只看这里显示的 diff block，需要找到 uploadImage 相关的部分

// 下面是上传图片后的逻辑部分


// 点击历史记录图片 - 选择并更新角色/场景缩略图
const isUpdatingThumbnail = ref(false)
const handleHistoryImageClick = async (record: { url: string; prompt: string | null }) => {
  console.log('[AssetEditPanel] 点击历史记录:', record)
  console.log('[AssetEditPanel] 更新前 generatedImageUrl:', generatedImageUrl.value)
  
  // 先清空再设置，强制触发响应式更新
  generatedImageUrl.value = ''
  referenceImageUrl.value = ''
  
  // 使用 nextTick 确保 DOM 更新
  await new Promise(resolve => setTimeout(resolve, 0))
  
  generatedImageUrl.value = record.url
  referenceImageUrl.value = record.url
  
  console.log('[AssetEditPanel] 更新后 generatedImageUrl:', generatedImageUrl.value)
  console.log('[AssetEditPanel] 参考图已更新:', referenceImageUrl.value)
  
  // 如果有 prompt，也更新到描述框
  if (record.prompt && record.prompt !== '本地上传') {
    aiDescription.value = record.prompt
  }
  
  // 如果是 blob URL（本地上传的图片），只在本地预览，不同步到服务器
  if (record.url.startsWith('blob:')) {
    window.$message?.success('已选择本地图片')
    return
  }
  
  if (props.assetType === 'scene') {
    // 场景处理
    const sceneId = currentSceneId.value || props.assetId
    if (sceneId && editorStore.projectId) {
      const scene = editorStore.scenes.find(s => s.id === sceneId)
      let librarySceneId = scene?.librarySceneId
      
      console.log('[AssetEditPanel] 更新场景缩略图:', {
        sceneId,
        librarySceneId,
        sceneName: (scene as any)?.displayName,
        newUrl: record.url
      })
      
      isUpdatingThumbnail.value = true
      try {
        const sceneName = (scene as any)?.displayName || '未命名场景'
        const sceneDesc = (scene as any)?.finalDescription || ''
        
        if (librarySceneId) {
          try {
            // 调用场景库 API 更新缩略图
            await sceneApi.updateLibraryScene(librarySceneId, {
              name: sceneName,
              description: sceneDesc,
              thumbnailUrl: record.url
            })
          } catch (error: any) {
            // 如果场景库场景不存在，尝试重新创建
            if (error.message?.includes('场景不存在') || error.message?.includes('not found')) {
              console.log('[AssetEditPanel] 场景库场景不存在，尝试重新创建')
              const newLibraryScene = await sceneApi.createLibraryScene({
                name: sceneName,
                description: sceneDesc,
                thumbnailUrl: record.url
              })
              librarySceneId = newLibraryScene.id
              // 更新项目场景的关联
              await api.put(`/projects/${editorStore.projectId}/scenes/${sceneId}`, {
                librarySceneId: newLibraryScene.id
              })
            } else {
              throw error
            }
          }
        } else {
          // 没有场景库ID，创建一个新的场景库场景
          console.log('[AssetEditPanel] 没有场景库ID，创建新场景')
          const newLibraryScene = await sceneApi.createLibraryScene({
            name: sceneName,
            description: sceneDesc,
            thumbnailUrl: record.url
          })
          librarySceneId = newLibraryScene.id
          // 更新项目场景的关联
          await api.put(`/projects/${editorStore.projectId}/scenes/${sceneId}`, {
            librarySceneId: newLibraryScene.id
          })
        }
        
        // 刷新场景列表和分镜数据
        await Promise.all([
          editorStore.fetchScenes(),
          editorStore.fetchShots()
        ])
        
        // 重新加载历史记录，使用新的 sceneId
        await loadGenerationHistory()
        
        window.$message?.success('已更新场景图片')
      } catch (error: any) {
        console.error('[AssetEditPanel] 更新场景缩略图失败:', error)
        window.$message?.warning('图片已选择，但更新场景图标失败: ' + (error.message || ''))
      } finally {
        isUpdatingThumbnail.value = false
      }
    } else {
      window.$message?.success('已选择该图片')
    }
  } else if (props.assetType === 'prop') {
    // 道具处理
    const propId = currentPropId.value || props.assetId
    if (propId && editorStore.projectId) {
      const prop = (editorStore.props || []).find((p: any) => p.id === propId)
      let libraryPropId = prop?.libraryPropId
      
      console.log('[AssetEditPanel] 更新道具缩略图:', {
        propId,
        libraryPropId,
        propName: (prop as any)?.displayName,
        newUrl: record.url
      })
      
      isUpdatingThumbnail.value = true
      try {
        const propName = (prop as any)?.displayName || '未命名道具'
        const propDesc = (prop as any)?.finalDescription || (prop as any)?.description || ''
        
        if (libraryPropId) {
          try {
            // 调用道具库 API 更新缩略图
            await propApi.updateLibraryProp(libraryPropId, {
              name: propName,
              description: propDesc,
              thumbnailUrl: record.url
            })
          } catch (error: any) {
            // 如果道具库道具不存在，尝试重新创建
            if (error.message?.includes('道具不存在') || error.message?.includes('not found')) {
              console.log('[AssetEditPanel] 道具库道具不存在，尝试重新创建')
              const newLibraryProp = await propApi.createLibraryProp({
                name: propName,
                description: propDesc,
                thumbnailUrl: record.url
              })
              libraryPropId = newLibraryProp.id
              // 更新项目道具的关联
              await api.put(`/projects/${editorStore.projectId}/props/${propId}`, {
                libraryPropId: newLibraryProp.id
              })
            } else {
              throw error
            }
          }
        } else {
          // 没有道具库ID，创建一个新的道具库道具
          console.log('[AssetEditPanel] 没有道具库ID，创建新道具')
          const newLibraryProp = await propApi.createLibraryProp({
            name: propName,
            description: propDesc,
            thumbnailUrl: record.url
          })
          libraryPropId = newLibraryProp.id
          // 更新项目道具的关联
          await api.put(`/projects/${editorStore.projectId}/props/${propId}`, {
            libraryPropId: newLibraryProp.id
          })
        }
        
        // 刷新道具列表和分镜数据
        await Promise.all([
          editorStore.fetchProps(),
          editorStore.fetchShots()
        ])
        
        // 重新加载历史记录，使用新的 propId
        await loadGenerationHistory()
        
        window.$message?.success('已更新道具图片')
      } catch (error: any) {
        console.error('[AssetEditPanel] 更新道具缩略图失败:', error)
        window.$message?.warning('图片已选择，但更新道具图标失败: ' + (error.message || ''))
      } finally {
        isUpdatingThumbnail.value = false
      }
    } else {
      window.$message?.success('已选择该图片')
    }
  } else {
    // 角色处理
    const charId = currentCharacterId.value || props.assetId
    if (charId && editorStore.projectId) {
      // 获取角色的角色库ID
      const character = editorStore.characters.find(c => c.id === charId)
      let libraryCharacterId = character?.libraryCharacterId
      
      console.log('[AssetEditPanel] 更新角色缩略图:', {
        charId,
        libraryCharacterId,
        characterName: (character as any)?.displayName || character?.name,
        newUrl: record.url
      })
      
      isUpdatingThumbnail.value = true
      try {
        const characterName = (character as any)?.libraryCharacterName || (character as any)?.displayName || character?.name || '未命名角色'
        const characterDesc = (character as any)?.finalDescription || (character as any)?.description || ''
        
        if (libraryCharacterId) {
          try {
            // 调用角色库 API 更新缩略图
            await characterApi.updateLibraryCharacter(libraryCharacterId, {
              name: characterName,
              description: characterDesc,
              thumbnailUrl: record.url
            })
          } catch (error: any) {
            // 如果角色库角色不存在，尝试重新创建
            if (error.message?.includes('角色不存在') || error.message?.includes('not found')) {
              console.log('[AssetEditPanel] 角色库角色不存在，尝试重新创建')
              // 创建新的角色库角色
              const newLibraryChar = await characterApi.createLibraryCharacter({
                name: characterName,
                description: characterDesc,
                thumbnailUrl: record.url
              })
              libraryCharacterId = newLibraryChar.id
              // 更新项目角色的关联
              await api.put(`/projects/${editorStore.projectId}/characters/${charId}`, {
                libraryCharacterId: newLibraryChar.id
              })
              console.log('[AssetEditPanel] 已重新创建角色库角色:', newLibraryChar.id)
            } else {
              throw error
            }
          }
        } else {
          // 没有角色库ID，创建一个新的角色库角色
          console.log('[AssetEditPanel] 没有角色库ID，创建新角色')
          const newLibraryChar = await characterApi.createLibraryCharacter({
            name: characterName,
            description: characterDesc,
            thumbnailUrl: record.url
          })
          libraryCharacterId = newLibraryChar.id
          // 更新项目角色的关联
          await api.put(`/projects/${editorStore.projectId}/characters/${charId}`, {
            libraryCharacterId: newLibraryChar.id
          })
          console.log('[AssetEditPanel] 已创建并关联角色库角色:', newLibraryChar.id)
        }
        
        // 刷新角色列表和分镜数据
        console.log('[AssetEditPanel] 开始刷新角色和分镜数据...')
        await Promise.all([
          editorStore.fetchCharacters(),
          editorStore.fetchShots()
        ])
        console.log('[AssetEditPanel] 数据刷新完成, 角色列表:', editorStore.characters.map(c => ({ id: c.id, name: c.name, thumbnailUrl: c.thumbnailUrl })))
        
        // 重新加载历史记录，使用新的 charId
        await loadGenerationHistory()
        
        window.$message?.success('已更新角色图片')
      } catch (error: any) {
        console.error('[AssetEditPanel] 更新角色缩略图失败:', error)
        window.$message?.warning('图片已选择，但更新角色图标失败: ' + (error.message || ''))
      } finally {
        isUpdatingThumbnail.value = false
      }
    } else {
      window.$message?.success('已选择该图片')
    }
  }
}

// 删除历史记录
const handleDeleteHistory = async (record: any) => {
  try {
    console.log('[AssetEditPanel] 删除历史记录:', record)
    
    // 根据资产类型获取所有同名资产的ID
    let assetIds: number[] = []
    let assetTypeKey: 'character' | 'scene' | 'prop'
    
    if (props.assetType === 'scene') {
      assetIds = getSameNameSceneIds.value
      assetTypeKey = 'scene'
    } else if (props.assetType === 'prop') {
      assetIds = getSameNamePropIds.value
      assetTypeKey = 'prop'
    } else {
      assetIds = getSameNameCharacterIds.value
      assetTypeKey = 'character'
    }
    
    // 遍历所有同名资产，查找并删除匹配的历史记录
    let deleted = false
    for (const assetId of assetIds) {
      const localHistory = editorStore.getLocalImageHistory(assetTypeKey, assetId, props.shotId)
      // 通过 URL 查找匹配的记录
      const matchingRecord = localHistory.find(r => r.url === record.url)
      
      if (matchingRecord) {
        // 找到匹配的记录，删除它
        editorStore.deleteLocalImageHistory(assetTypeKey, assetId, matchingRecord.id, props.shotId)
        console.log('[AssetEditPanel] 已从资产 ID', assetId, '删除历史记录:', matchingRecord.id)
        deleted = true
        break  // 只需要删除一次
      }
    }
    
    if (deleted) {
      window.$message?.success('已删除历史记录')
    } else {
      console.warn('[AssetEditPanel] 未找到匹配的历史记录:', record.url)
      window.$message?.warning('未找到该历史记录')
    }
  } catch (error: any) {
    console.error('[AssetEditPanel] 删除历史记录失败:', error)
    window.$message?.error('删除失败: ' + (error.message || ''))
  }
}

// 上传参考图
const handleReferenceImageUpload = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    referenceImage.value = file
    referenceImageUrl.value = URL.createObjectURL(file)
  }
}

// 触发文件选择
const triggerFileInput = () => {
  const input = document.getElementById('reference-image-input') as HTMLInputElement
  input?.click()
}

// 清除参考图
const clearReferenceImage = () => {
  referenceImage.value = null
  referenceImageUrl.value = ''
  const input = document.getElementById('reference-image-input') as HTMLInputElement
  if (input) {
    input.value = ''
  }
}

// AI生成状态
const isGenerating = ref(false)

// AI生成
const handleAIGenerate = async () => {
  if (!aiDescription.value.trim()) {
    window.$message?.warning('请填写描述后再生成')
    return
  }
  
  if (!editorStore.projectId) {
    window.$message?.error('项目未加载')
    return
  }
  
  isGenerating.value = true
  
  try {
    // 根据资产类型分发
    if (props.assetType === 'scene') {
      await handleSceneGenerate()
    } else if (props.assetType === 'prop') {
      await handlePropGenerate()
    } else {
      await handleCharacterGenerate()
    }
  } catch (error: any) {
    console.error('[AssetEditPanel] AI生成失败:', error)
    window.$message?.error(error.message || 'AI生成失败')
  } finally {
    isGenerating.value = false
  }
}

// 角色图片生成
const handleCharacterGenerate = async () => {
  let targetCharacterId: number
  
  // 判断是为现有角色生成还是创建新角色
  if (props.assetId) {
    // 为现有角色生成新图片
    targetCharacterId = props.assetId
    console.log('[AssetEditPanel] 为现有角色生成图片:', targetCharacterId)
    
    // ✅ 先更新角色描述（AI分析后的内容）
    await api.put(`/projects/${editorStore.projectId}/characters/${targetCharacterId}`, {
      overrideDescription: aiDescription.value
    })
    console.log('[AssetEditPanel] 角色描述已更新:', aiDescription.value.substring(0, 50) + '...')
  } else {
    // 创建新角色
    const characterName = currentCharacterName.value || '新角色'
    console.log('[AssetEditPanel] 开始创建角色:', characterName)
    
    // 1. 先在角色库创建角色
    const libraryCharacter = await characterApi.createLibraryCharacter({
      name: characterName,
      description: aiDescription.value
    })
    console.log('[AssetEditPanel] 角色库角色创建成功:', libraryCharacter)
    
    // 2. 将角色引用到项目
    const projectCharacter = await api.post(`/projects/${editorStore.projectId}/characters`, {
      libraryCharacterId: libraryCharacter.id,
      displayName: characterName
    })
    console.log('[AssetEditPanel] 角色引用到项目成功:', projectCharacter)
    
    targetCharacterId = projectCharacter.id
  }
  
  // 3. 调用图片生成API
  const response = await generationApi.generateSingleCharacter(
    editorStore.projectId!,
    targetCharacterId,
    {
      aspectRatio: aspectRatio.value as '1:1' | '16:9' | '9:16' | '21:9'
    }
  )
  
  console.log('[AssetEditPanel] 生成任务已提交:', response)
  window.$message?.success('图片生成任务已提交')
  
  // 4. 轮询任务状态
  if (response.jobId) {
    await pollCharacterJobUntilComplete(response.jobId, targetCharacterId)
  }
  
  // 5. 刷新角色列表
  await editorStore.fetchCharacters()
}

// 场景图片生成
const handleSceneGenerate = async () => {
  let targetSceneId: number
  
  // 判断是为现有场景生成还是创建新场景
  if (props.assetId) {
    // 为现有场景生成新图片
    targetSceneId = props.assetId
    console.log('[AssetEditPanel] 为现有场景生成图片:', targetSceneId)
    
    // 更新场景描述
    await api.put(`/projects/${editorStore.projectId}/scenes/${targetSceneId}`, {
      description: aiDescription.value
    })
  } else {
    // 创建新场景
    const sceneName = currentSceneName.value || '新场景'
    console.log('[AssetEditPanel] 开始创建场景:', sceneName)
    
    // 1. 先在场景库创建场景
    const libraryScene = await sceneApi.createLibraryScene({
      name: sceneName,
      description: aiDescription.value
    })
    console.log('[AssetEditPanel] 场景库场景创建成功:', libraryScene)
    
    // 2. 将场景引用到项目
    const projectScene = await api.post(`/projects/${editorStore.projectId}/scenes`, {
      librarySceneId: libraryScene.id,
      displayName: sceneName
    })
    console.log('[AssetEditPanel] 场景引用到项目成功:', projectScene)
    
    targetSceneId = projectScene.id
    currentSceneId.value = targetSceneId
    
    // 2.5 如果有关联的分镜ID，自动绑定场景到分镜
    if (props.shotId && editorStore.projectId) {
      try {
        await shotApi.createBinding(editorStore.projectId, props.shotId, 'PSCENE', targetSceneId)
        console.log('[AssetEditPanel] 场景已绑定到分镜:', props.shotId)
      } catch (error) {
        console.error('[AssetEditPanel] 绑定场景到分镜失败:', error)
      }
    }
  }
  
  // 3. 调用场景图片生成API
  const response = await generationApi.generateSingleScene(
    editorStore.projectId!,
    targetSceneId,
    {
      aspectRatio: aspectRatio.value as '1:1' | '16:9' | '9:16' | '21:9'
    }
  )
  
  console.log('[AssetEditPanel] 场景生成任务已提交:', response)
  window.$message?.success('场景图片生成任务已提交')
  
  // 4. 轮询任务状态
  if (response.jobId) {
    await pollSceneJobUntilComplete(response.jobId, targetSceneId)
  }
  
  // 5. 刷新场景列表和分镜数据
  await Promise.all([
    editorStore.fetchScenes(),
    editorStore.fetchShots()  // 同时刷新分镜，以更新场景缩略图
  ])
}

// 道具图片生成
const handlePropGenerate = async () => {
  let targetPropId: number
  
  // 判断是为现有道具生成还是创建新道具
  if (props.assetId) {
    // 为现有道具生成新图片
    targetPropId = props.assetId
    console.log('[AssetEditPanel] 为现有道具生成图片:', targetPropId)
    
    // 更新道具描述
    await api.put(`/projects/${editorStore.projectId}/props/${targetPropId}`, {
      description: aiDescription.value
    })
  } else {
    // 创建新道具
    const propName = currentPropName.value || '新道具'
    console.log('[AssetEditPanel] 开始创建道具:', propName)
    
    // 1. 先在道具库创建道具
    const libraryProp = await propApi.createLibraryProp({
      name: propName,
      description: aiDescription.value
    })
    console.log('[AssetEditPanel] 道具库道具创建成功:', libraryProp)
    
    // 2. 将道具引用到项目
    const projectProp = await propApi.addPropToProject(editorStore.projectId!, {
      libraryPropId: libraryProp.id,
      displayName: propName
    })
    console.log('[AssetEditPanel] 道具引用到项目成功:', projectProp)
    
    targetPropId = projectProp.id
    currentPropId.value = targetPropId
    
    // 2.5 如果有关联的分镜ID，自动绑定道具到分镜
    if (props.shotId && editorStore.projectId) {
      try {
        await shotApi.createBinding(editorStore.projectId, props.shotId, 'PPROP', targetPropId)
        console.log('[AssetEditPanel] 道具已绑定到分镜:', props.shotId)
      } catch (error) {
        console.error('[AssetEditPanel] 绑定道具到分镜失败:', error)
      }
    }
  }
  
  // 3. 调用道具图片生成API
  const response = await generationApi.generateSingleProp(
    editorStore.projectId!,
    targetPropId,
    {
      aspectRatio: aspectRatio.value as '1:1' | '16:9' | '9:16' | '21:9'
    }
  )
  
  console.log('[AssetEditPanel] 道具生成任务已提交:', response)
  window.$message?.success('道具图片生成任务已提交')
  
  // 4. 轮询任务状态
  if (response.jobId) {
    await pollPropJobUntilComplete(response.jobId, targetPropId)
  }
  
  // 5. 刷新道具列表和分镜数据
  await Promise.all([
    editorStore.fetchProps(),
    editorStore.fetchShots()
  ])
}

// 轮询角色任务状态直到完成
const pollCharacterJobUntilComplete = async (jobId: number, characterId: number) => {
  const maxAttempts = 60
  let attempts = 0
  
  while (attempts < maxAttempts) {
    await new Promise(resolve => setTimeout(resolve, 3000))
    attempts++
    
    try {
      const jobStatus = await jobApi.getJobStatus(jobId)
      console.log('[AssetEditPanel] 角色任务状态:', jobStatus)
      
      if (jobStatus.status === 'COMPLETED' || jobStatus.status === 'SUCCEEDED') {
        window.$message?.success('图片生成成功!')
        currentCharacterId.value = characterId
        if (editorStore.projectId) {
          await editorStore.fetchCharacters()
          const updatedCharacter = editorStore.characters.find(c => c.id === characterId)
          
          // 优先使用 Job 返回的 allImageUrls，否则使用角色的 thumbnailUrl
          const allImageUrls = jobStatus.allImageUrls || []
          const primaryUrl = allImageUrls.length > 0 ? allImageUrls[0] : updatedCharacter?.thumbnailUrl
          
          if (primaryUrl && isValidImageUrl(primaryUrl)) {
            generatedImageUrl.value = primaryUrl
            
            // 将所有图片添加到本地历史记录
            const localRecords = editorStore.getLocalImageHistory('character', characterId)
            const groupId = Date.now()  // 使用统一的groupId关联同一批次生成的图片
            const baseVersionNo = localRecords.length + 1
            
            // 如果有多张图片，添加所有图片到历史记录
            if (allImageUrls.length > 0) {
              console.log('[AssetEditPanel] 添加多张图片到历史记录:', allImageUrls.length)
              allImageUrls.forEach((url: string, index: number) => {
                if (isValidImageUrl(url)) {
                  const newRecord = {
                    id: groupId + index,
                    url: url,
                    prompt: aiDescription.value,
                    versionNo: baseVersionNo,
                    createdAt: new Date().toISOString(),
                    groupId: groupId,  // 标记同一批次生成的图片
                    groupIndex: index  // 组内索引
                  }
                  editorStore.addLocalImageHistory('character', characterId, newRecord, props.shotId)
                }
              })
            } else if (primaryUrl) {
              // 只有一张图片的情况
              const newRecord = {
                id: groupId,
                url: primaryUrl,
                prompt: aiDescription.value,
                versionNo: baseVersionNo,
                createdAt: new Date().toISOString()
              }
              editorStore.addLocalImageHistory('character', characterId, newRecord, props.shotId)
            }
            
            // 重新加载历史记录以确保显示
            await loadGenerationHistory()
          }
        }
        return
      } else if (jobStatus.status === 'FAILED') {
        window.$message?.error('图片生成失败')
        return
      }
    } catch (error) {
      console.error('[AssetEditPanel] 轮询任务状态失败:', error)
    }
  }
  
  window.$message?.warning('生成超时，请稍后刷新查看')
}

// 轮询场景任务状态直到完成
const pollSceneJobUntilComplete = async (jobId: number, sceneId: number) => {
  const maxAttempts = 60
  let attempts = 0
  
  while (attempts < maxAttempts) {
    await new Promise(resolve => setTimeout(resolve, 3000))
    attempts++
    
    try {
      const jobStatus = await jobApi.getJobStatus(jobId)
      console.log('[AssetEditPanel] 场景任务状态:', jobStatus)
      
      if (jobStatus.status === 'COMPLETED' || jobStatus.status === 'SUCCEEDED') {
        window.$message?.success('场景图片生成成功!')
        currentSceneId.value = sceneId
        if (editorStore.projectId) {
          await editorStore.fetchScenes()
          const updatedScene = editorStore.scenes.find(s => s.id === sceneId)
          
          // 优先使用 Job 返回的 allImageUrls，否则使用场景的 thumbnailUrl
          const allImageUrls = jobStatus.allImageUrls || []
          const primaryUrl = allImageUrls.length > 0 ? allImageUrls[0] : updatedScene?.thumbnailUrl
          
          if (primaryUrl && isValidImageUrl(primaryUrl)) {
            generatedImageUrl.value = primaryUrl
            
            // 将所有图片添加到本地历史记录
            const localRecords = editorStore.getLocalImageHistory('scene', sceneId)
            const groupId = Date.now()
            const baseVersionNo = localRecords.length + 1
            
            if (allImageUrls.length > 0) {
              console.log('[AssetEditPanel] 添加多张场景图片到历史记录:', allImageUrls.length)
              allImageUrls.forEach((url: string, index: number) => {
                if (isValidImageUrl(url)) {
                  const newRecord = {
                    id: groupId + index,
                    url: url,
                    prompt: aiDescription.value,
                    versionNo: baseVersionNo,
                    createdAt: new Date().toISOString(),
                    groupId: groupId,
                    groupIndex: index
                  }
                  editorStore.addLocalImageHistory('scene', sceneId, newRecord, props.shotId)
                }
              })
            } else if (primaryUrl) {
              const newRecord = {
                id: groupId,
                url: primaryUrl,
                prompt: aiDescription.value,
                versionNo: baseVersionNo,
                createdAt: new Date().toISOString()
              }
              editorStore.addLocalImageHistory('scene', sceneId, newRecord, props.shotId)
            }
            
            // 重新加载历史记录以确保显示
            await loadGenerationHistory()
          }
        }
        return
      } else if (jobStatus.status === 'FAILED') {
        window.$message?.error('场景图片生成失败')
        return
      }
    } catch (error) {
      console.error('[AssetEditPanel] 轮询场景任务状态失败:', error)
    }
  }
  
  window.$message?.warning('生成超时，请稍后刷新查看')
}

// 轮询道具任务状态直到完成
const pollPropJobUntilComplete = async (jobId: number, propId: number) => {
  const maxAttempts = 60
  let attempts = 0
  
  while (attempts < maxAttempts) {
    await new Promise(resolve => setTimeout(resolve, 3000))
    attempts++
    
    try {
      const jobStatus = await jobApi.getJobStatus(jobId)
      console.log('[AssetEditPanel] 道具任务状态:', jobStatus)
      
      if (jobStatus.status === 'COMPLETED' || jobStatus.status === 'SUCCEEDED') {
        window.$message?.success('道具图片生成成功!')
        currentPropId.value = propId
        if (editorStore.projectId) {
          await editorStore.fetchProps()
          const updatedProp = (editorStore.props || []).find((p: any) => p.id === propId)
          
          // 优先使用 Job 返回的 allImageUrls，否则使用道具的 thumbnailUrl
          const allImageUrls = jobStatus.allImageUrls || []
          const primaryUrl = allImageUrls.length > 0 ? allImageUrls[0] : updatedProp?.thumbnailUrl
          
          if (primaryUrl && isValidImageUrl(primaryUrl)) {
            generatedImageUrl.value = primaryUrl
            
            // 将所有图片添加到本地历史记录
            const localRecords = editorStore.getLocalImageHistory('prop', propId)
            const groupId = Date.now()
            const baseVersionNo = localRecords.length + 1
            
            if (allImageUrls.length > 0) {
              console.log('[AssetEditPanel] 添加多张道具图片到历史记录:', allImageUrls.length)
              allImageUrls.forEach((url: string, index: number) => {
                if (isValidImageUrl(url)) {
                  const newRecord = {
                    id: groupId + index,
                    url: url,
                    prompt: aiDescription.value,
                    versionNo: baseVersionNo,
                    createdAt: new Date().toISOString(),
                    groupId: groupId,
                    groupIndex: index
                  }
                  editorStore.addLocalImageHistory('prop', propId, newRecord, props.shotId)
                }
              })
            } else if (primaryUrl) {
              const newRecord = {
                id: groupId,
                url: primaryUrl,
                prompt: aiDescription.value,
                versionNo: baseVersionNo,
                createdAt: new Date().toISOString()
              }
              editorStore.addLocalImageHistory('prop', propId, newRecord, props.shotId)
            }
            
            // 重新加载历史记录以确保显示
            await loadGenerationHistory()
          }
        }
        return
      } else if (jobStatus.status === 'FAILED') {
        window.$message?.error('道具图片生成失败')
        return
      }
    } catch (error) {
      console.error('[AssetEditPanel] 轮询道具任务状态失败:', error)
    }
  }
  
  window.$message?.warning('生成超时，请稍后刷新查看')
}

// 本地上传图片
const isUploadingLocal = ref(false)
const handleLocalImageUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  
  // 根据资产类型获取ID
  let assetId: number | undefined
  let assetTypeKey: string
  
  if (props.assetType === 'scene') {
    assetId = currentSceneId.value || props.assetId
    assetTypeKey = 'scene'
  } else if (props.assetType === 'prop') {
    assetId = currentPropId.value || props.assetId
    assetTypeKey = 'prop'
  } else {
    assetId = currentCharacterId.value || props.assetId
    assetTypeKey = 'character'
  }
  
  // 先显示本地预览
  const blobUrl = URL.createObjectURL(file)
  generatedImageUrl.value = blobUrl
  
  // 上传到OSS
  isUploadingLocal.value = true
  try {
    window.$message?.info('正在上传图片到云端...')
    const response = await uploadApi.upload(file, 'image')
    const ossUrl = response.url
    
    console.log('[AssetEditPanel] 图片上传成功:', ossUrl)
    
    // 更新显示的图片URL
    generatedImageUrl.value = ossUrl
    
    // 如果没有资产ID，需要先创建资产
    if (!assetId && editorStore.projectId) {
      console.log('[AssetEditPanel] 没有资产ID，先创建资产')
      
      if (props.assetType === 'scene') {
        const sceneName = currentSceneName.value || props.sceneName || '新场景'
        const sceneDesc = aiDescription.value || ''
        
        // 创建场景库场景
        const libraryScene = await sceneApi.createLibraryScene({
          name: sceneName,
          description: sceneDesc,
          thumbnailUrl: ossUrl
        })
        
        // 引用到项目
        const projectScene = await api.post(`/projects/${editorStore.projectId}/scenes`, {
          librarySceneId: libraryScene.id,
          displayName: sceneName
        })
        
        assetId = projectScene.id
        currentSceneId.value = assetId
        
        // 如果有关联的分镜ID，自动绑定
        if (props.shotId) {
          try {
            await shotApi.createBinding(editorStore.projectId, props.shotId, 'PSCENE', assetId)
            console.log('[AssetEditPanel] 场景已绑定到分镜:', props.shotId)
          } catch (error) {
            console.error('[AssetEditPanel] 绑定场景到分镜失败:', error)
          }
        }
        
        await Promise.all([
          editorStore.fetchScenes(),
          editorStore.fetchShots()
        ])
      } else if (props.assetType === 'prop') {
        const propName = currentPropName.value || props.propName || '新道具'
        const propDesc = aiDescription.value || ''
        
        // 创建道具库道具
        const libraryProp = await propApi.createLibraryProp({
          name: propName,
          description: propDesc,
          thumbnailUrl: ossUrl
        })
        
        // 引用到项目
        const projectProp = await propApi.addPropToProject(editorStore.projectId, {
          libraryPropId: libraryProp.id,
          displayName: propName
        })
        
        assetId = projectProp.id
        currentPropId.value = assetId
        
        // 如果有关联的分镜ID，自动绑定
        if (props.shotId) {
          try {
            await shotApi.createBinding(editorStore.projectId, props.shotId, 'PPROP', assetId)
            console.log('[AssetEditPanel] 道具已绑定到分镜:', props.shotId)
          } catch (error) {
            console.error('[AssetEditPanel] 绑定道具到分镜失败:', error)
          }
        }
        
        await Promise.all([
          editorStore.fetchProps(),
          editorStore.fetchShots()
        ])
      } else {
        const characterName = currentCharacterName.value || props.characterName || '新角色'
        const characterDesc = aiDescription.value || ''
        
        // 创建角色库角色
        const libraryChar = await characterApi.createLibraryCharacter({
          name: characterName,
          description: characterDesc,
          thumbnailUrl: ossUrl
        })
        
        // 引用到项目
        const projectChar = await api.post(`/projects/${editorStore.projectId}/characters`, {
          libraryCharacterId: libraryChar.id,
          displayName: characterName
        })
        
        assetId = projectChar.id
        currentCharacterId.value = assetId
        
        await Promise.all([
          editorStore.fetchCharacters(),
          editorStore.fetchShots()
        ])
      }
    }
    
    // 如果有资产ID，更新资产库和历史记录
    if (assetId && editorStore.projectId) {
      // 更新资产库缩略图
      if (props.assetType === 'scene') {
        const scene = editorStore.scenes.find(s => s.id === assetId)
        let librarySceneId = scene?.librarySceneId
        const sceneName = (scene as any)?.displayName || '未命名场景'
        const sceneDesc = (scene as any)?.finalDescription || ''
        
        if (librarySceneId) {
          await sceneApi.updateLibraryScene(librarySceneId, {
            name: sceneName,
            description: sceneDesc,
            thumbnailUrl: ossUrl
          })
        } else {
          const newLibraryScene = await sceneApi.createLibraryScene({
            name: sceneName,
            description: sceneDesc,
            thumbnailUrl: ossUrl
          })
          await api.put(`/projects/${editorStore.projectId}/scenes/${assetId}`, {
            librarySceneId: newLibraryScene.id,
            thumbnailUrl: ossUrl
          })
        }
        await editorStore.fetchScenes()
        await editorStore.fetchShots()
      } else if (props.assetType === 'prop') {
        const prop = editorStore.props.find((p: any) => p.id === assetId)
        let libraryPropId = prop?.libraryPropId
        const propName = (prop as any)?.displayName || '未命名道具'
        const propDesc = (prop as any)?.finalDescription || ''
        
        if (libraryPropId) {
          await propApi.updateLibraryProp(libraryPropId, {
            name: propName,
            description: propDesc,
            thumbnailUrl: ossUrl
          })
        } else {
          const newLibraryProp = await propApi.createLibraryProp({
            name: propName,
            description: propDesc,
            thumbnailUrl: ossUrl
          })
          await api.put(`/projects/${editorStore.projectId}/props/${assetId}`, {
            libraryPropId: newLibraryProp.id,
            thumbnailUrl: ossUrl
          })
        }
        await editorStore.fetchProps()
        await editorStore.fetchShots()
      } else {
        const character = editorStore.characters.find(c => c.id === assetId)
        let libraryCharacterId = character?.libraryCharacterId
        const characterName = (character as any)?.displayName || character?.name || '未命名角色'
        const characterDesc = (character as any)?.finalDescription || ''
        
        if (libraryCharacterId) {
          await characterApi.updateLibraryCharacter(libraryCharacterId, {
            name: characterName,
            description: characterDesc,
            thumbnailUrl: ossUrl
          })
        } else {
          const newLibraryChar = await characterApi.createLibraryCharacter({
            name: characterName,
            description: characterDesc,
            thumbnailUrl: ossUrl
          })
          await api.put(`/projects/${editorStore.projectId}/characters/${assetId}`, {
            libraryCharacterId: newLibraryChar.id,
            thumbnailUrl: ossUrl
          })
        }
        await editorStore.fetchCharacters()
        await editorStore.fetchShots()
      }
      
      // 添加到本地历史记录（使用OSS URL）
      const localRecords = editorStore.getLocalImageHistory(assetTypeKey as 'character' | 'scene' | 'prop', assetId, props.shotId)
      const newRecord = {
        id: Date.now(),
        url: ossUrl,
        prompt: '本地上传',
        versionNo: localRecords.length + 1,
        createdAt: new Date().toISOString(),
        source: 'local'
      }
      editorStore.addLocalImageHistory(assetTypeKey as 'character' | 'scene' | 'prop', assetId, newRecord, props.shotId)
      
      // 重新加载历史记录
      await loadGenerationHistory()
      
      window.$message?.success('图片上传成功并已更新')
    } else {
      window.$message?.success('图片上传成功')
    }
    
    // 清理blob URL
    URL.revokeObjectURL(blobUrl)
  } catch (error: any) {
    console.error('[AssetEditPanel] 图片上传失败:', error)
    window.$message?.error('图片上传失败: ' + (error.message || ''))
    // 恢复blob URL显示
    generatedImageUrl.value = blobUrl
  } finally {
    isUploadingLocal.value = false
    // 清空input，允许重复上传同一文件
    target.value = ''
  }
}

// 触发本地图片上传
const triggerLocalImageInput = () => {
  const input = document.getElementById('local-image-input') as HTMLInputElement
  input?.click()
}

// 删除生成的图片
const handleDeleteGeneratedImage = () => {
  generatedImageUrl.value = ''
  window.$message?.info('已删除图片')
}

// 下载图片（使用a标签直接下载，避免CORS）
const handleDownloadImage = () => {
  if (!generatedImageUrl.value) return
  try {
    const link = document.createElement('a')
    link.href = generatedImageUrl.value
    link.download = `${assetTypeText.value}_${Date.now()}.jpg`
    link.target = '_blank'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.$message?.success('开始下载')
  } catch (error) {
    console.error('下载失败:', error)
    window.$message?.error('下载失败')
  }
}

// 复制图片到剪贴板（通过后端代理避免CORS）
const handleCopyImage = async () => {
  if (!generatedImageUrl.value) return
  try {
    window.$message?.info('正在复制...')
    
    // 通过后端下载图片
    const response = await fetch('/api/asset/download-from-url', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: JSON.stringify({ url: generatedImageUrl.value })
    })
    
    if (!response.ok) {
      throw new Error('下载图片失败')
    }
    
    const blob = await response.blob()
    
    // 复制到剪贴板
    await navigator.clipboard.write([
      new ClipboardItem({
        [blob.type]: blob
      })
    ])
    
    window.$message?.success('已复制到剪贴板')
  } catch (error) {
    console.error('复制失败:', error)
    window.$message?.error('复制失败，请尝试右键保存')
  }
}
</script>

<template>
  <div class="flex flex-col h-full bg-bg-elevated">
    <!-- 顶部导航 -->
    <div class="flex items-center gap-3 border-b border-border-default px-4 py-3">
      <button
        @click="$emit('close')"
        class="p-1.5 rounded hover:bg-bg-hover transition-colors"
        title="返回"
      >
        <svg class="w-5 h-5 text-text-secondary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path>
        </svg>
      </button>
      <h3 class="text-text-primary text-base font-medium">返回 {{ titleText }}</h3>
    </div>

    <!-- 主内容区 -->
    <div class="flex-1 overflow-y-auto px-6 py-6">
      <!-- 图片预览区（大虚线框） -->
      <div
        class="group relative w-full aspect-video rounded border-2 border-dashed border-border-default bg-bg-subtle flex flex-col items-center justify-center mb-6 overflow-hidden"
      >
        <template v-if="generatedImageUrl">
          <!-- 已生成的图片 -->
          <img
            :src="generatedImageUrl"
            :alt="`${assetTypeText}图片`"
            class="w-full h-full object-cover"
          >
          <!-- 操作按钮组（悬浮显示） -->
          <div class="absolute top-3 right-3 flex gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
            <!-- 下载按钮 -->
            <button
              @click="handleDownloadImage"
              class="p-2 rounded-lg bg-gray-800 hover:bg-gray-700 transition-colors"
              title="下载图片"
            >
              <svg class="w-5 h-5 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
              </svg>
            </button>
            <!-- 复制按钮 -->
            <button
              @click="handleCopyImage"
              class="p-2 rounded-lg bg-gray-800 hover:bg-gray-700 transition-colors"
              title="复制图片"
            >
              <svg class="w-5 h-5 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
              </svg>
            </button>
            <!-- 删除按钮 -->
            <button
              @click="handleDeleteGeneratedImage"
              class="p-2 rounded-lg bg-gray-800 hover:bg-gray-700 transition-colors"
              title="删除图片"
            >
              <svg class="w-5 h-5 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
              </svg>
            </button>
          </div>
        </template>
        <template v-else>
          <!-- 占位符 - 根据类型显示不同图标 -->
          <!-- 场景图标 -->
          <svg v-if="assetType === 'scene'" class="w-16 h-16 text-white/30 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path>
          </svg>
          <!-- 道具图标 -->
          <svg v-else-if="assetType === 'prop'" class="w-16 h-16 text-white/30 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z"></path>
          </svg>
          <!-- 角色图标 -->
          <svg v-else class="w-16 h-16 text-white/30 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
            <circle cx="12" cy="7" r="4"></circle>
          </svg>
          <p class="text-text-tertiary text-sm font-medium mb-1">暂无{{ assetTypeText }}图片</p>
          <p class="text-text-tertiary text-xs">编辑下方描述后点击生成</p>
        </template>
      </div>

      <!-- 标签页 -->
      <div class="flex gap-3 mb-4">
        <button
          @click="activeTab = 'custom'"
          :class="[
            'px-6 py-2.5 rounded text-sm font-medium transition-all',
            activeTab === 'custom'
              ? 'bg-bg-hover text-text-primary border border-border-default'
              : 'bg-transparent text-text-tertiary border border-transparent hover:bg-bg-subtle'
          ]"
        >
          新{{ assetTypeText }}
        </button>
        <button
          @click="activeTab = 'local'"
          :class="[
            'px-6 py-2.5 rounded text-sm font-medium transition-all',
            activeTab === 'local'
              ? 'bg-bg-hover text-text-primary border border-border-default'
              : 'bg-transparent text-text-tertiary border border-transparent hover:bg-bg-subtle'
          ]"
        >
          本地图片
        </button>
        <button
          @click="activeTab = 'library'"
          :class="[
            'px-6 py-2.5 rounded text-sm font-medium transition-all',
            activeTab === 'library'
              ? 'bg-bg-hover text-text-primary border border-border-default'
              : 'bg-transparent text-text-tertiary border border-transparent hover:bg-bg-subtle'
          ]"
        >
          从库选择
        </button>
      </div>

      <!-- 自定义生成内容 -->
      <div v-show="activeTab === 'custom'" class="flex gap-4 mb-6">
        <!-- 左侧：上传AI参考图 -->
        <div class="flex-shrink-0">
          <div
            v-if="!referenceImageUrl"
            @click="triggerFileInput"
            class="w-32 h-32 rounded border-2 border-dashed border-border-default bg-bg-subtle flex flex-col items-center justify-center cursor-pointer hover:bg-bg-subtle hover:border-border-default transition-all"
          >
            <svg class="w-8 h-8 text-text-tertiary mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path>
            </svg>
            <p class="text-text-tertiary text-xs">上传AI参考图</p>
          </div>
          <div v-else class="relative w-32 h-32 rounded overflow-hidden group">
            <img :src="referenceImageUrl" alt="参考图" class="w-full h-full object-cover">
            <!-- 删除按钮 -->
            <button
              @click="clearReferenceImage"
              class="absolute top-1 right-1 p-1.5 rounded-lg bg-red-500/80 hover:bg-red-500 transition-all opacity-0 group-hover:opacity-100"
            >
              <svg class="w-4 h-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
              </svg>
            </button>
          </div>
          <input
            id="reference-image-input"
            type="file"
            accept="image/*"
            @change="handleReferenceImageUpload"
            class="hidden"
          >
        </div>

        <!-- 右侧：描述输入框 -->
        <div class="flex-1 relative">
          <!-- 解析中覆盖层 -->
          <div v-if="isParsing" class="absolute inset-0 bg-bg-subtle rounded flex items-center justify-center z-10">
            <div class="flex items-center gap-2 text-gray-600">
              <svg class="w-5 h-5 animate-spin" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              <span class="text-sm font-medium">AI正在解析{{ assetType === 'scene' ? '场景环境' : assetType === 'prop' ? '道具外观' : '人物形象' }}...</span>
            </div>
          </div>
          <textarea
            v-model="aiDescription"
            :placeholder="assetType === 'scene' ? '填写用于AI生图的场景描述' : assetType === 'prop' ? '填写用于AI生图的道具描述' : '填写用于AI生图的角色描述'"
            class="w-full h-32 px-4 py-3 bg-bg-subtle border border-border-default rounded text-text-primary placeholder-text-tertiary focus:outline-none focus:border-gray-900/50 resize-none text-sm"
            :disabled="isParsing"
          ></textarea>
          <!-- 解析失败重试按钮 -->
          <button
            v-if="parseError"
            @click="handleRetryParse"
            class="absolute bottom-3 right-3 px-3 py-1 bg-bg-subtle border border-border-default rounded-lg text-text-secondary text-xs hover:bg-bg-hover transition-colors flex items-center gap-1"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path>
            </svg>
            重试解析
          </button>
        </div>
      </div>

      <!-- 本地上传内容 -->
      <div v-show="activeTab === 'local'" class="mb-6">
        <div
          @click="triggerLocalImageInput"
          class="w-full h-40 rounded border-2 border-dashed border-border-default bg-bg-subtle flex flex-col items-center justify-center cursor-pointer hover:bg-bg-subtle hover:border-border-default transition-all"
        >
          <svg class="w-12 h-12 text-text-tertiary mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path>
          </svg>
          <p class="text-text-tertiary text-sm font-medium mb-1">上传本地图片</p>
          <p class="text-text-tertiary text-xs">支持 JPG、PNG 格式</p>
        </div>
        <input
          id="local-image-input"
          type="file"
          accept="image/*"
          @change="handleLocalImageUpload"
          class="hidden"
        >
      </div>

      <!-- 从库选择内容 -->
      <div v-show="activeTab === 'library'" class="mb-6">
        <div v-if="loadingLibraryAssets" class="text-center py-8">
          <div class="inline-block w-6 h-6 border-2 border-gray-900 border-t-transparent rounded animate-spin"></div>
          <p class="text-text-tertiary text-sm mt-3">正在加载{{ assetTypeText }}库...</p>
        </div>

        <div v-else-if="libraryAssets.length === 0" class="text-center py-8">
          <svg class="w-16 h-16 text-text-disabled mx-auto mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4"></path>
          </svg>
          <p class="text-text-tertiary text-sm">库中暂无{{ assetTypeText }}</p>
        </div>

        <div v-else class="grid grid-cols-4 gap-3 max-h-96 overflow-y-auto">
          <div
            v-for="asset in libraryAssets"
            :key="asset.id"
            class="group aspect-square rounded-lg overflow-hidden cursor-pointer hover:ring-2 hover:ring-[#00FFCC]/50 transition-all relative"
            @click="handleLibraryAssetClick(asset)"
          >
            <img :src="asset.thumbnailUrl" :alt="asset.name" class="w-full h-full object-cover">
            <!-- 底部名称标签 -->
            <div class="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/70 to-transparent px-2 py-1.5">
              <span class="text-white text-xs font-medium truncate block">{{ asset.name }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部控制区 -->
      <div v-show="activeTab === 'custom'" class="flex items-center justify-between mb-6">
        <!-- 比例选择 -->
        <div class="flex items-center gap-2">
          <div class="p-2 bg-bg-subtle rounded-lg">
            <svg class="w-5 h-5 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 5a1 1 0 011-1h4a1 1 0 011 1v7a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM14 5a1 1 0 011-1h4a1 1 0 011 1v7a1 1 0 01-1 1h-4a1 1 0 01-1-1V5zM4 16a1 1 0 011-1h4a1 1 0 011 1v3a1 1 0 01-1 1H5a1 1 0 01-1-1v-3zM14 16a1 1 0 011-1h4a1 1 0 011 1v3a1 1 0 01-1 1h-4a1 1 0 01-1-1v-3z"></path>
            </svg>
          </div>
          <select
            v-model="aspectRatio"
            class="px-3 py-2 bg-bg-subtle border border-border-default rounded-lg text-text-primary text-sm focus:outline-none focus:border-gray-900/50 cursor-pointer"
          >
            <option v-for="option in aspectRatioOptions" :key="option.value" :value="option.value" class="bg-bg-elevated">
              {{ option.label }}
            </option>
          </select>
        </div>

        <!-- AI生成按钮 -->
        <button
          @click="handleAIGenerate"
          :disabled="isGenerating || isParsingCharacter"
          :class="[
            'px-8 py-3 rounded font-medium text-sm transition-all flex items-center gap-2',
            isGenerating || isParsingCharacter
              ? 'bg-gray-500 cursor-not-allowed opacity-60'
              : 'bg-gray-800 text-white hover:opacity-90'
          ]"
        >
          <template v-if="isGenerating">
            <svg class="w-5 h-5 animate-spin" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            生成中...
          </template>
          <template v-else>
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
            </svg>
            AI生成
          </template>
        </button>
      </div>

      <!-- 历史记录 -->
      <div class="border-t border-border-default pt-6">
        <h4 class="text-text-primary text-sm font-medium mb-4">历史记录</h4>

        <div v-if="loadingHistory" class="text-center py-8">
          <div class="inline-block w-6 h-6 border-2 border-gray-900 border-t-transparent rounded animate-spin"></div>
        </div>

        <div v-else-if="allHistory.length === 0" class="text-center py-4">
          <p class="text-text-tertiary text-sm">暂无历史生成记录</p>
        </div>

        <div v-else class="grid grid-cols-4 gap-3 mb-4">
          <div
            v-for="record in allHistory"
            :key="record.id"
            class="group aspect-square rounded-lg overflow-hidden cursor-pointer hover:ring-2 hover:ring-[#00FFCC]/50 transition-all relative"
          >
            <img :src="record.url" :alt="record.prompt || '历史记录'" class="w-full h-full object-cover" @click="handleHistoryImageClick(record)">
            <!-- 底部版本标签 -->
            <div class="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/70 to-transparent px-2 py-1.5">
              <span class="text-white text-xs font-medium">{{ record.source === 'local' ? '本地上传' : `v${record.versionNo}` }}</span>
            </div>
            <!-- 本地上传标记 -->
            <div v-if="record.source === 'local'" class="absolute top-1 right-1 px-1.5 py-0.5 bg-purple-500/80 rounded text-text-primary text-[10px] pointer-events-none">本地</div>
            <!-- 删除按钮（悬浮显示） -->
            <button
              @click.stop="handleDeleteHistory(record)"
              class="absolute top-1 left-1 w-6 h-6 rounded bg-red-500/80 flex items-center justify-center hover:bg-red-500 transition-all opacity-0 group-hover:opacity-100 z-10"
              title="删除历史记录"
            >
              <svg class="w-3.5 h-3.5 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M6 18L18 6M6 6l12 12"></path>
              </svg>
            </button>
          </div>
        </div>
        
        <!-- 提示信息始终显示 -->
        <p class="text-[#FF6B9D] text-xs text-center">未被使用的生成记录仅保留7天，请及时下载文件</p>
      </div>
    </div>
  </div>
</template>

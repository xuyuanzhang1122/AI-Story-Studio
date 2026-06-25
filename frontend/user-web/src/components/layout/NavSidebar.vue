<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api/auth'
import { NModal, NInput, NButton, NUpload, type UploadFileInfo } from 'naive-ui'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 用户信息编辑弹窗
const showProfileModal = ref(false)
const editNickname = ref('')
const editAvatarUrl = ref('')
const saving = ref(false)
const saveError = ref('')
const uploading = ref(false)
const MAX_AVATAR_SIZE = 5 * 1024 * 1024 // 5MB

// 敬请期待弹窗
const showComingSoonModal = ref(false)

// 素材库展开状态
const isAssetLibraryExpanded = ref(true)

interface NavItem {
  name: string
  path: string
  icon: string
  badge?: number
  comingSoon?: boolean
}

interface NavGroup {
  name: string
  icon: string
  children: NavItem[]
}

// 素材库子菜单
const assetLibraryItems: NavItem[] = [
  {
    name: '角色库',
    path: '/character-library',
    icon: `<svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
    </svg>`,
  },
  {
    name: '场景库',
    path: '/scene-library',
    icon: `<svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
    </svg>`,
  },
  {
    name: '道具库',
    path: '/prop-library',
    icon: `<svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
    </svg>`,
  },
]

const navItems = computed<NavItem[]>(() => [
  {
    name: '首页',
    path: '/home',
    icon: `<svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
    </svg>`,
  },
  {
    name: '工具箱',
    path: '/toolbox',
    icon: `<svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
    </svg>`,
  },
  {
    name: '邀请好友',
    path: '/invite',
    icon: `<svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
    </svg>`,
    comingSoon: true,
  },
])

// 检查素材库是否有子项被选中
const isAssetLibraryActive = computed(() => {
  return assetLibraryItems.some(item => route.path === item.path)
})

const isActive = (path: string) => {
  return route.path === path
}

const handleNavigate = (item: NavItem) => {
  if (item.comingSoon) {
    showComingSoonModal.value = true
    return
  }
  router.push(item.path)
}

const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}

const openProfileModal = () => {
  editNickname.value = userStore.user?.nickname || ''
  editAvatarUrl.value = userStore.user?.avatarUrl || ''
  showProfileModal.value = true
}

const handleAvatarUpload = async (options: { file: UploadFileInfo }) => {
  const file = options.file.file
  if (!file) return
  
  // 校验文件大小
  if (file.size > MAX_AVATAR_SIZE) {
    saveError.value = '头像文件大小不能超过5MB'
    return
  }
  
  uploading.value = true
  saveError.value = ''
  try {
    const url = await authApi.uploadAvatar(file)
    editAvatarUrl.value = url
  } catch {
    saveError.value = '头像上传失败'
  } finally {
    uploading.value = false
  }
}

const handleSaveProfile = async () => {
  saveError.value = ''
  if (!editNickname.value.trim()) {
    saveError.value = '昵称不能为空'
    return
  }
  if (editNickname.value.length < 2 || editNickname.value.length > 20) {
    saveError.value = '昵称长度需在2-20个字符之间'
    return
  }
  
  saving.value = true
  try {
    await userStore.updateProfile({
      nickname: editNickname.value.trim(),
      avatarUrl: editAvatarUrl.value.trim() || undefined,
    })
    showProfileModal.value = false
  } catch {
    saveError.value = '保存失败，请重试'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <aside class="w-60 flex flex-col bg-bg-sidebar border-r border-border-default">
    <!-- Logo 区域 -->
    <div class="px-5 py-5 border-b border-border-subtle">
      <div class="flex items-center gap-3">
        <!-- Logo 图标 -->
        <div class="w-9 h-9 rounded-lg overflow-hidden">
          <img src="@/assets/images/9724166f3c32d4dac68c7615c5e5de2f.jpg" alt="Logo" class="w-full h-full object-cover" />
        </div>
        <!-- 标题 -->
        <div>
          <h1 class="text-base font-semibold text-text-primary">红鹦鹉漫剧</h1>
          <p class="text-xs text-text-tertiary mt-0.5">AI Story Studio</p>
        </div>
      </div>
    </div>

    <!-- 分组标签 -->
    <div class="px-5 pt-4 pb-2">
      <span class="text-xs text-text-tertiary font-medium">创作</span>
    </div>

    <!-- 导航区域 -->
    <nav class="flex-1 px-3 pb-4 overflow-y-auto">
      <div class="space-y-1">
        <!-- 首页 -->
        <button
          class="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all duration-150 text-sm font-medium group"
          :class="isActive('/home')
            ? 'bg-[#8B5CF6] text-white shadow-[0_0_20px_rgba(139,92,246,0.3)]'
            : 'text-text-secondary hover:bg-bg-hover hover:text-text-primary'"
          @click="router.push('/home')"
        >
          <div 
            :class="['w-5 h-5 flex items-center justify-center', isActive('/home') ? 'text-white' : 'text-text-tertiary group-hover:text-text-secondary']"
          >
            <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
            </svg>
          </div>
          <span class="flex-1 text-left">首页</span>
        </button>

        <!-- 素材库组 -->
        <div>
          <!-- 素材库主按钮 -->
          <button
            class="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all duration-150 text-sm font-medium group"
            :class="isAssetLibraryActive && !isAssetLibraryExpanded
              ? 'bg-[#8B5CF6] text-white shadow-[0_0_20px_rgba(139,92,246,0.3)]'
              : 'text-text-secondary hover:bg-bg-hover hover:text-text-primary'"
            @click="isAssetLibraryExpanded = !isAssetLibraryExpanded"
          >
            <div 
              :class="['w-5 h-5 flex items-center justify-center', isAssetLibraryActive && !isAssetLibraryExpanded ? 'text-white' : 'text-text-tertiary group-hover:text-text-secondary']"
            >
              <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
              </svg>
            </div>
            <span class="flex-1 text-left">素材库</span>
            <!-- 展开箭头 -->
            <svg 
              class="w-4 h-4 transition-transform duration-200"
              :class="{ 'rotate-180': isAssetLibraryExpanded }"
              fill="none" 
              viewBox="0 0 24 24" 
              stroke="currentColor"
            >
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
            </svg>
          </button>

          <!-- 素材库子菜单 -->
          <div 
            v-show="isAssetLibraryExpanded" 
            class="mt-1 ml-4 pl-4 border-l border-border-subtle space-y-1"
          >
            <button
              v-for="item in assetLibraryItems"
              :key="item.path"
              class="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg transition-all duration-150 text-sm font-medium group"
              :class="isActive(item.path)
                ? 'bg-[#8B5CF6] text-white shadow-[0_0_20px_rgba(139,92,246,0.3)]'
                : 'text-text-secondary hover:bg-bg-hover hover:text-text-primary'"
              @click="router.push(item.path)"
            >
              <div 
                :class="['w-4 h-4 flex items-center justify-center', isActive(item.path) ? 'text-white' : 'text-text-tertiary group-hover:text-text-secondary']"
                v-html="item.icon"
              />
              <span class="flex-1 text-left">{{ item.name }}</span>
            </button>
          </div>
        </div>

        <!-- 其他菜单项 -->
        <button
          v-for="item in navItems.slice(1)"
          :key="item.path"
          :class="[
            'w-full flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all duration-150 text-sm font-medium group',
            isActive(item.path)
              ? 'bg-[#8B5CF6] text-white shadow-[0_0_20px_rgba(139,92,246,0.3)]'
              : 'text-text-secondary hover:bg-bg-hover hover:text-text-primary'
          ]"
          @click="handleNavigate(item)"
        >
          <!-- 图标 -->
          <div 
            :class="[
              'w-5 h-5 flex items-center justify-center',
              isActive(item.path) ? 'text-white' : 'text-text-tertiary group-hover:text-text-secondary'
            ]" 
            v-html="item.icon" 
          />

          <!-- 文字 -->
          <span class="flex-1 text-left">{{ item.name }}</span>

          <!-- 即将上线标签 -->
          <span
            v-if="item.comingSoon"
            class="px-1.5 py-0.5 rounded text-xs bg-bg-subtle text-text-tertiary"
          >
            Soon
          </span>

          <!-- 徽章 -->
          <span
            v-if="item.badge"
            class="px-1.5 py-0.5 rounded bg-[#8B5CF6] text-white text-xs font-semibold"
          >
            {{ item.badge }}
          </span>
        </button>
      </div>
    </nav>

    <!-- 底部用户区域 -->
    <div class="px-3 pb-4 border-t border-border-subtle pt-3">
      <!-- 用户卡片 -->
      <div
        class="group p-2.5 rounded-lg bg-bg-elevated hover:bg-bg-hover border border-border-default hover:border-border-strong transition-all duration-150 cursor-pointer"
        @click="openProfileModal"
      >
        <div class="flex items-center gap-2.5">
          <!-- 头像 -->
          <div class="relative">
            <div class="w-9 h-9 rounded-lg bg-bg-hover flex items-center justify-center overflow-hidden">
              <img
                v-if="userStore.userAvatar"
                :src="userStore.userAvatar"
                class="w-full h-full object-cover"
                alt="avatar"
              />
              <span v-else class="text-sm font-semibold text-text-primary">
                {{ userStore.userName.charAt(0) }}
              </span>
            </div>
            <!-- 在线状态 -->
            <div class="absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 bg-success rounded-full border-2 border-bg-sidebar"></div>
          </div>
          
          <!-- 用户信息 -->
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium text-text-primary truncate">{{ userStore.userName }}</p>
            <p class="text-xs text-text-tertiary">点击编辑</p>
          </div>
          
          <!-- 箭头 -->
          <div class="w-5 h-5 flex items-center justify-center">
            <svg class="w-4 h-4 text-text-tertiary group-hover:text-text-secondary transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
            </svg>
          </div>
        </div>
      </div>

      <!-- 退出按钮 -->
      <button
        class="w-full mt-2 px-3 py-2 rounded-lg text-text-tertiary hover:text-text-primary hover:bg-bg-hover transition-all duration-150 text-sm flex items-center justify-center gap-2"
        @click="handleLogout"
      >
        <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
        </svg>
        退出登录
      </button>
    </div>

    <!-- 用户信息编辑弹窗 -->
    <NModal
      v-model:show="showProfileModal"
      preset="card"
      title="编辑个人资料"
      :style="{ width: '460px', background: '#1a1a1a', borderColor: '#2a2a2a' }"
      :bordered="false"
      class="profile-modal"
    >
      <div class="space-y-6 py-2">
        <!-- 头像区域 -->
        <div class="flex flex-col items-center gap-4">
          <!-- 头像预览 -->
          <div class="relative group">
            <div class="w-24 h-24 rounded-2xl bg-gradient-to-br from-purple-500/20 to-blue-500/20 flex items-center justify-center overflow-hidden border-2 border-border-subtle">
              <img
                v-if="editAvatarUrl"
                :src="editAvatarUrl"
                class="w-full h-full object-cover"
                alt="avatar preview"
              />
              <span v-else class="text-3xl font-bold text-text-primary">
                {{ editNickname.charAt(0) || '?' }}
              </span>
            </div>
            <!-- 删除头像按钮 -->
            <button
              v-if="editAvatarUrl"
              class="absolute -top-1 -right-1 w-6 h-6 rounded-full bg-red-500 hover:bg-red-600 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all shadow-lg"
              @click="editAvatarUrl = ''"
            >
              <svg class="w-3.5 h-3.5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <!-- 上传按钮 -->
          <NUpload
            accept="image/*"
            :show-file-list="false"
            :custom-request="handleAvatarUpload"
          >
            <button
              class="px-6 py-2 rounded-lg bg-bg-elevated hover:bg-bg-hover border border-border-default hover:border-[#8B5CF6] text-text-secondary hover:text-[#8B5CF6] text-sm font-medium transition-all"
              :disabled="uploading"
            >
              <span v-if="uploading" class="flex items-center gap-2">
                <svg class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                上传中...
              </span>
              <span v-else>更换头像</span>
            </button>
          </NUpload>
          <p class="text-text-tertiary text-xs">支持 JPG、PNG 格式，最大 5MB</p>
        </div>

        <!-- 昵称输入 -->
        <div class="space-y-2">
          <label class="block text-text-secondary text-sm font-medium">昵称</label>
          <NInput
            v-model:value="editNickname"
            placeholder="请输入昵称（2-20个字符）"
            maxlength="20"
            show-count
            size="large"
          />
        </div>

        <!-- 错误提示 -->
        <div v-if="saveError" class="flex items-center gap-2 px-3 py-2.5 rounded-lg bg-red-500/10 border border-red-500/30">
          <svg class="w-4 h-4 text-red-500 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span class="text-red-500 text-sm">{{ saveError }}</span>
        </div>

        <!-- 操作按钮 -->
        <div class="flex gap-3 pt-2">
          <button
            class="flex-1 px-4 py-2.5 rounded-lg bg-bg-elevated hover:bg-bg-hover border border-border-default text-text-secondary hover:text-text-primary text-sm font-medium transition-all"
            @click="showProfileModal = false"
          >
            取消
          </button>
          <button
            class="flex-1 px-4 py-2.5 rounded-lg bg-[#8B5CF6] hover:bg-[#A78BFA] text-white text-sm font-medium transition-all shadow-[0_0_20px_rgba(139,92,246,0.3)] hover:shadow-[0_0_25px_rgba(139,92,246,0.4)] disabled:opacity-50 disabled:cursor-not-allowed"
            :disabled="saving"
            @click="handleSaveProfile"
          >
            <span v-if="saving" class="flex items-center justify-center gap-2">
              <svg class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              保存中...
            </span>
            <span v-else>保存</span>
          </button>
        </div>
      </div>
    </NModal>

    <!-- 敬请期待弹窗 -->
    <NModal
      v-model:show="showComingSoonModal"
      preset="card"
      :style="{ width: '360px', background: '#232323', borderColor: '#333333' }"
      :bordered="false"
      class="coming-soon-modal"
    >
      <div class="flex flex-col items-center py-6">
        <div class="w-16 h-16 rounded-lg bg-bg-hover flex items-center justify-center mb-4">
          <svg class="w-8 h-8 text-[#8B5CF6]" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>
        <h3 class="text-text-primary text-lg font-semibold mb-2">敬请期待</h3>
        <p class="text-text-secondary text-sm text-center leading-relaxed">
          该功能正在加紧开发中<br/>即将上线，敬请期待！
        </p>
        <button
          class="mt-6 px-8 py-2.5 rounded-lg bg-[#8B5CF6] text-white font-medium text-sm hover:bg-[#A78BFA] transition-colors"
          @click="showComingSoonModal = false"
        >
          我知道了
        </button>
      </div>
    </NModal>
  </aside>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api/auth'
import GlassCard from '@/components/base/GlassCard.vue'
import PillButton from '@/components/base/PillButton.vue'

const props = defineProps<{ code?: string }>()

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const phone = ref('')
const verifyCode = ref('')
const inviteCode = ref('')
const loading = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
const errorMessage = ref('')

onMounted(() => {
  // 从路由参数获取邀请码
  const codeFromRoute = props.code || (route.params.code as string)
  if (codeFromRoute) {
    inviteCode.value = codeFromRoute
  }
})

let countdownTimer: number | null = null

const isPhoneValid = computed(() => {
  return /^1[3-9]\d{9}$/.test(phone.value)
})

const canSendCode = computed(() => {
  return isPhoneValid.value && countdown.value === 0 && !sendingCode.value
})

const canLogin = computed(() => {
  return isPhoneValid.value && verifyCode.value.length === 6
})

const sendCodeButtonText = computed(() => {
  if (countdown.value > 0) return `${countdown.value}s 后重试`
  return '发送验证码'
})

const startCountdown = () => {
  countdown.value = 60
  countdownTimer = window.setInterval(() => {
    countdown.value--
    if (countdown.value <= 0 && countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

const handleSendCode = async () => {
  if (!canSendCode.value) return

  errorMessage.value = ''
  sendingCode.value = true

  try {
    await authApi.sendCode({ phone: phone.value })
    startCountdown()
  } catch (error: any) {
    errorMessage.value = error.message || '发送验证码失败，请稍后重试'
  } finally {
    sendingCode.value = false
  }
}

const handleLogin = async () => {
  if (!canLogin.value || loading.value) return

  errorMessage.value = ''
  loading.value = true

  try {
    await userStore.login(phone.value, verifyCode.value, inviteCode.value || undefined)
    router.push('/home')
  } catch (error: any) {
    errorMessage.value = error.message || '登录失败，请检查验证码'
  } finally {
    loading.value = false
  }
}

const handlePhoneInput = (e: Event) => {
  const target = e.target as HTMLInputElement
  // Only allow digits
  target.value = target.value.replace(/\D/g, '').slice(0, 11)
  phone.value = target.value
}

const handleCodeInput = (e: Event) => {
  const target = e.target as HTMLInputElement
  // Only allow digits
  target.value = target.value.replace(/\D/g, '').slice(0, 6)
  verifyCode.value = target.value
}

const handleKeydown = (e: KeyboardEvent, action: 'sendCode' | 'login') => {
  if (e.key === 'Enter') {
    if (action === 'sendCode' && canSendCode.value) {
      handleSendCode()
    } else if (action === 'login' && canLogin.value) {
      handleLogin()
    }
  }
}
</script>

<template>
  <div class="min-h-screen bg-bg-base flex items-center justify-center p-4">
    <div class="relative w-full max-w-md">
      <!-- Logo and title -->
      <div class="text-center mb-8">
        <div class="inline-block mb-4">
          <div class="w-16 h-16 rounded-lg bg-[#8B5CF6] flex items-center justify-center">
            <span class="text-lg font-bold leading-none text-white">红鹦鹉</span>
          </div>
        </div>
        <h1 class="text-3xl font-bold text-text-primary mb-2">红鹦鹉漫剧</h1>
        <p class="text-text-tertiary text-sm">AI 驱动的漫剧创作平台</p>
      </div>

      <!-- Login form -->
      <div class="card p-8">
        <h2 class="text-xl font-semibold text-text-primary mb-6">手机号登录</h2>

        <!-- Phone input -->
        <div class="mb-4">
          <label class="block text-text-secondary text-sm mb-2">手机号</label>
          <input
            :value="phone"
            type="tel"
            inputmode="numeric"
            placeholder="请输入手机号"
            maxlength="11"
            class="input"
            @input="handlePhoneInput"
            @keydown="(e) => handleKeydown(e, 'sendCode')"
          >
        </div>

        <!-- Verification code input with send button -->
        <div class="mb-4">
          <label class="block text-text-secondary text-sm mb-2">验证码</label>
          <div class="flex gap-2">
            <input
              :value="verifyCode"
              type="text"
              inputmode="numeric"
              placeholder="请输入验证码"
              maxlength="6"
              class="input flex-1"
              @input="handleCodeInput"
              @keydown="(e) => handleKeydown(e, 'login')"
            >
            <button
              class="btn btn-secondary whitespace-nowrap"
              :disabled="!canSendCode"
              @click="handleSendCode"
            >
              <span v-if="sendingCode">发送中...</span>
              <span v-else>{{ sendCodeButtonText }}</span>
            </button>
          </div>
        </div>

        <!-- Invite code input (optional) -->
        <div class="mb-6">
          <label class="block text-text-secondary text-sm mb-2">邀请码 <span class="text-text-tertiary">(选填)</span></label>
          <input
            v-model="inviteCode"
            type="text"
            placeholder="请输入邀请码"
            maxlength="20"
            class="input"
          >
        </div>

        <!-- Error message -->
        <div v-if="errorMessage" class="mb-4 p-3 rounded-lg bg-error/20 border border-error/30">
          <p class="text-error text-sm">{{ errorMessage }}</p>
        </div>

        <!-- Login button -->
        <button
          class="btn btn-primary w-full"
          :disabled="!canLogin || loading"
          @click="handleLogin"
        >
          <span v-if="loading">登录中...</span>
          <span v-else>登录</span>
        </button>

        <!-- Terms -->
        <p class="text-text-tertiary text-xs text-center mt-6">
          登录即表示同意
          <a href="#" class="text-[#8B5CF6] hover:underline">用户协议</a>
          和
          <a href="#" class="text-[#8B5CF6] hover:underline">隐私政策</a>
        </p>
      </div>

      <!-- Footer text -->
      <p class="text-text-tertiary text-xs text-center mt-6">
        © 2024 红鹦鹉漫剧 · AI Story Studio
      </p>
    </div>
  </div>
</template>

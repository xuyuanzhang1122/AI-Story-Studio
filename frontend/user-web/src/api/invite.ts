import api from './index'
import type { InviteInfoVO, InviteRecordVO, InviteStatsVO, PageResult } from '@/types/api'

export const inviteApi = {
  /**
   * Get current user's invite code (my-code returns single InviteCodeVO)
   */
  async getMyInviteInfo(): Promise<InviteInfoVO> {
    const codeData = await api.get('/invite/my-code')

    // Transform InviteCodeVO to InviteInfoVO
    const baseUrl = import.meta.env.VITE_APP_BASE_URL || window.location.origin
    const inviteLink = `${baseUrl}/invite/${codeData.code}`

    return {
      code: codeData.code,
      inviteLink,
      rewardPoints: codeData.rewardPoints,
      inviterRewardPoints: codeData.inviterRewardPoints,
      usedCount: codeData.usedCount,
      maxUses: codeData.maxUses,
      // These will be fetched separately from /invite/stats
      totalInvited: 0,
      totalRewardsEarned: 0,
    }
  },

  /**
   * Get invite records with pagination
   */
  async getInviteRecords(params: {
    page: number
    size: number
  }): Promise<PageResult<InviteRecordVO>> {
    return api.get('/invite/records', { params })
  },

  /**
   * Get invite statistics
   */
  async getInviteStats(): Promise<InviteStatsVO> {
    return api.get('/invite/stats')
  },

  /**
   * Generate invite link text
   * @param inviteCode - User's unique invite code
   * @returns Formatted invite text
   */
  generateInviteText(inviteCode: string): string {
    const baseUrl = import.meta.env.VITE_APP_BASE_URL || window.location.origin
    const inviteLink = `${baseUrl}/invite/${inviteCode}`
    return `您的好友邀请你加入红鹦鹉漫剧免费创作AI漫剧:${inviteLink}`
  },

  /**
   * Copy invite link to clipboard
   * @param inviteCode - User's unique invite code
   * @returns Promise that resolves when copied
   */
  async copyInviteLink(inviteCode: string): Promise<void> {
    const inviteText = this.generateInviteText(inviteCode)

    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(inviteText)
    } else {
      // Fallback for older browsers
      const textArea = document.createElement('textarea')
      textArea.value = inviteText
      textArea.style.position = 'fixed'
      textArea.style.left = '-999999px'
      document.body.appendChild(textArea)
      textArea.focus()
      textArea.select()
      try {
        document.execCommand('copy')
      } finally {
        document.body.removeChild(textArea)
      }
    }
  },
}

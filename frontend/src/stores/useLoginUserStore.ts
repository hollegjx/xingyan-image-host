import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getLoginUserUsingGet } from '@/service/api/userController'

export interface LoginUser {
  id?: string | number
  userName?: string
  userAccount?: string
  userAvatar?: string
  userProfile?: string
  userRole?: number  // 0 = 普通用户, 1 = 管理员
  createTime?: string
}

export const useLoginUserStore = defineStore('loginUser', () => {
  const loginUser = ref<LoginUser>({})
  // 是否已尝试获取过登录信息（避免重复请求）
  const hasFetched = ref(false)
  let fetchingPromise: Promise<void> | undefined

  const setLoginUser = (user: LoginUser) => {
    loginUser.value = user
  }

  const clearLoginUser = () => {
    loginUser.value = {}
    hasFetched.value = false
  }

  // 从后端获取当前登录用户信息
  const fetchLoginUser = async () => {
    if (fetchingPromise) {
      return fetchingPromise
    }
    hasFetched.value = true
    fetchingPromise = (async () => {
      try {
        const res = await getLoginUserUsingGet()
        loginUser.value = res.code === 0 && res.data ? res.data : {}
      } catch {
        // 公共页面不应因为后端暂时不可用或未登录而中断路由。
        loginUser.value = {}
      }
    })()
    try {
      await fetchingPromise
    } finally {
      fetchingPromise = undefined
    }
  }

  return {
    loginUser,
    hasFetched,
    setLoginUser,
    clearLoginUser,
    fetchLoginUser,
  }
})

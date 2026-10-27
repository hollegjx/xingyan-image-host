import router from '@/router'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { message } from 'ant-design-vue'

// 是否为首次获取登录用户
let firstFetchLoginUser = true

/**
 * 全局权限校验
 */
router.beforeEach(async (to, from, next) => {
  const loginUserStore = useLoginUserStore()
  let loginUser = loginUserStore.loginUser

  // 确保页面刷新时、首次加载时，能等待后端返回用户信息后再校验权限
  if (firstFetchLoginUser) {
    await loginUserStore.fetchLoginUser()
    loginUser = loginUserStore.loginUser
    firstFetchLoginUser = false
  }

  const toUrl = to.fullPath

  if (to.meta.requiresLogin && !loginUser.id) {
    message.warning('请先登录')
    next({ path: '/user/login', query: { redirect: to.fullPath } })
    return
  }

  // 管理员才能访问 /admin 开头的页面（1 = 管理员）
  if (toUrl.startsWith('/admin')) {
    if (!loginUser.id || loginUser.userRole !== 1) {
      message.error('没有权限')
      next({ path: '/user/login', query: { redirect: to.fullPath } })
      return
    }
  }

  next()
})

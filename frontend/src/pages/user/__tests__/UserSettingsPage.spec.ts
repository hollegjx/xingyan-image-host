import { shallowMount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, expect, it, vi } from 'vitest'
import UserSettingsPage from '../UserSettingsPage.vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
const api = vi.hoisted(() => ({ get: vi.fn(), profile: vi.fn(), password: vi.fn() }))
vi.mock('@/service/api/userController', () => ({
  getLoginUserUsingGet: api.get,
  updateMyProfileUsingPost: api.profile,
  updateMyPasswordUsingPost: api.password,
  userLogoutUsingPost: vi.fn(),
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ replace: vi.fn() }) }))
vi.mock('ant-design-vue', () => ({ message: { success: vi.fn() } }))
beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  api.get.mockResolvedValue({ code: 0, data: { id: 5, userName: 'before', userProfile: 'bio' } })
  api.profile.mockResolvedValue({ code: 0, data: { id: 5, userName: 'after', userProfile: '' } })
  api.password.mockResolvedValue({ code: 0, data: true })
})
async function page() {
  const wrapper = shallowMount(UserSettingsPage, { global: { plugins: [createPinia()] } })
  await flushPromises()
  const vm = wrapper.vm as unknown as {
    profileForm: API.UserProfileUpdateRequest
    passwordForm: API.UserPasswordUpdateRequest
    saveProfile: () => Promise<void>
    savePassword: () => Promise<void>
    profileError: string
    passwordError: string
  }
  return { wrapper, vm }
}
it('saves only nickname and bio and refreshes login store', async () => {
  const { wrapper, vm } = await page()
  vm.profileForm.userName = 'after'
  vm.profileForm.userProfile = ''
  await vm.saveProfile()
  expect(api.profile).toHaveBeenCalledWith({ userName: 'after', userProfile: '' })
  expect(useLoginUserStore().loginUser.userName).toBe('after')
  wrapper.unmount()
})
it('rejects blank nickname locally', async () => {
  const { wrapper, vm } = await page()
  vm.profileForm.userName = ' '
  await vm.saveProfile()
  expect(api.profile).not.toHaveBeenCalled()
  expect(vm.profileError).not.toBe('')
  wrapper.unmount()
})
it('does not claim success when saving profile fails', async () => {
  const { wrapper, vm } = await page()
  api.profile.mockResolvedValue({ code: 50001, message: 'save failed' })
  await vm.saveProfile()
  expect(vm.profileError).toBe('save failed')
  wrapper.unmount()
})
it('rejects mismatched confirmation without sending credentials', async () => {
  const { wrapper, vm } = await page()
  Object.assign(vm.passwordForm, {
    oldPassword: 'old-fixture',
    newPassword: 'new-fixture',
    checkPassword: 'different',
  })
  await vm.savePassword()
  expect(api.password).not.toHaveBeenCalled()
  wrapper.unmount()
})
it('clears password fields after successful change', async () => {
  const { wrapper, vm } = await page()
  Object.assign(vm.passwordForm, {
    oldPassword: 'old-fixture',
    newPassword: 'new-fixture',
    checkPassword: 'new-fixture',
  })
  await vm.savePassword()
  expect(api.password).toHaveBeenCalledTimes(1)
  expect(Object.values(vm.passwordForm)).toEqual(['', '', ''])
  wrapper.unmount()
})
it('shows server rejection when old password is wrong', async () => {
  const { wrapper, vm } = await page()
  Object.assign(vm.passwordForm, {
    oldPassword: 'old-fixture',
    newPassword: 'new-fixture',
    checkPassword: 'new-fixture',
  })
  api.password.mockResolvedValue({ code: 40101, message: 'rejected' })
  await vm.savePassword()
  expect(vm.passwordError).toBe('rejected')
  wrapper.unmount()
})

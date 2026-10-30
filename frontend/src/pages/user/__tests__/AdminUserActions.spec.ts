import { shallowMount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, expect, it, vi } from 'vitest'
import UserManagePage from '../../admin/UserManagePage.vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
const api = vi.hoisted(() => ({ list: vi.fn(), update: vi.fn(), reset: vi.fn(), confirm: vi.fn() }))
vi.mock('@/service/api/userController', () => ({ listUserVoByPageUsingPost: api.list, updateUserUsingPost: api.update,
  resetUserPasswordUsingPost: api.reset, addUserUsingPost: vi.fn(), deleteUserUsingPost: vi.fn() }))
vi.mock('ant-design-vue', () => ({ Modal: { confirm: api.confirm }, message: { error: vi.fn(), success: vi.fn() } }))
beforeEach(() => {
  setActivePinia(createPinia()); vi.clearAllMocks(); useLoginUserStore().setLoginUser({ id: '1', userRole: 1 })
  api.list.mockResolvedValue({ code: 0, data: { records: [] } }); api.update.mockResolvedValue({ code: 0 })
})
function page() {
  const wrapper = shallowMount(UserManagePage)
  const vm = wrapper.vm as unknown as { changeRole: (u: API.AdminUserVO) => void; resetPassword: (u: API.AdminUserVO) => void;
    temporaryPassword: string; passwordOpen: boolean; clearTemporaryPassword: () => void; copyTemporaryPassword: () => Promise<void> }
  return { wrapper, vm }
}
it('does not offer an action to change own role', () => {
  const { wrapper, vm } = page(); vm.changeRole({ id: '1', userRole: 1 }); expect(api.confirm).not.toHaveBeenCalled(); wrapper.unmount()
})
it('confirms and submits only the selected user role', async () => {
  const { wrapper, vm } = page(); vm.changeRole({ id: '2', userRole: 0 });
  await api.confirm.mock.calls[0]![0].onOk(); expect(api.update).toHaveBeenCalledWith({ id: '2', userRole: 1 }); wrapper.unmount()
})
it('shows the generated credential once and clears it on close', async () => {
  const { wrapper, vm } = page(); const generated = crypto.randomUUID();
  api.reset.mockResolvedValue({ code: 0, data: { temporaryPassword: generated } });
  vm.resetPassword({ id: '2' }); await api.confirm.mock.calls[0]![0].onOk();
  expect(vm.temporaryPassword === generated).toBe(true); expect(vm.passwordOpen).toBe(true);
  vm.clearTemporaryPassword(); expect(vm.temporaryPassword.length).toBe(0); expect(vm.passwordOpen).toBe(false);
  await flushPromises(); wrapper.unmount()
})
it('copies only the currently visible credential', async () => {
  const { wrapper, vm } = page(); const writeText = vi.fn().mockResolvedValue(undefined)
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
  vm.temporaryPassword = crypto.randomUUID(); await vm.copyTemporaryPassword(); expect(writeText).toHaveBeenCalledTimes(1);
  vm.clearTemporaryPassword(); await vm.copyTemporaryPassword(); expect(writeText).toHaveBeenCalledTimes(1); wrapper.unmount()
})

import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import UserManagePage from '../../admin/UserManagePage.vue'
const api = vi.hoisted(() => ({ add: vi.fn(), list: vi.fn() }))
vi.mock('@/service/api/userController', () => ({
  addUserUsingPost: api.add, listUserVoByPageUsingPost: api.list, deleteUserUsingPost: vi.fn(),
}))
vi.mock('ant-design-vue', () => ({ message: { error: vi.fn(), success: vi.fn() }, Modal: { confirm: vi.fn() } }))
beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks(); api.list.mockResolvedValue({ code: 0, data: { records: [] } }); api.add.mockResolvedValue({ code: 0 }) })
function form() {
  const wrapper = shallowMount(UserManagePage)
  const vm = wrapper.vm as unknown as { createForm: API.UserAddRequest; createUser: () => Promise<void>; clearCreate: () => void }
  vm.createForm.userAccount = 'fixture-account'
  return { wrapper, vm }
}
it('requires an explicit initial password before calling admin creation', async () => {
  const { wrapper, vm } = form(); await vm.createUser(); expect(api.add).not.toHaveBeenCalled(); wrapper.unmount()
})
it('submits an explicit password and clears it after successful creation', async () => {
  const { wrapper, vm } = form(); vm.createForm.userPassword = 'fixture-password'; await vm.createUser();
  expect(api.add).toHaveBeenCalledWith(expect.objectContaining({ userPassword: expect.any(String) }));
  expect(vm.createForm.userPassword).toBe(''); await flushPromises(); wrapper.unmount()
})
it('rejects passwords above the UTF-8 byte limit', async () => {
  const { wrapper, vm } = form(); vm.createForm.userPassword = '密'.repeat(25); await vm.createUser();
  expect(api.add).not.toHaveBeenCalled(); wrapper.unmount()
})
it('clears the password when creation is cancelled', () => {
  const { wrapper, vm } = form(); vm.createForm.userPassword = 'fixture-password'; vm.clearCreate();
  expect(vm.createForm.userPassword).toBe(''); wrapper.unmount()
})

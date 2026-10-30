import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import TeamInvitePage from '../../space/TeamInvitePage.vue'
import SpaceMembersPage from '../../space/SpaceMembersPage.vue'
import UserLoginPage from '../UserLoginPage.vue'
const api = vi.hoisted(() => ({ preview: vi.fn(), join: vi.fn(), create: vi.fn(), list: vi.fn(), revoke: vi.fn(), push: vi.fn(), replace: vi.fn(), login: vi.fn(), me: vi.fn(), route: { hash: '', query: {} as Record<string,string> } }))
vi.mock('vue-router', () => ({ useRoute: () => api.route, useRouter: () => ({ push: api.push, replace: api.replace }) }))
vi.mock('@/service/api/teamInvitationController', () => ({ previewInvitation: api.preview, joinInvitation: api.join, createInvitation: api.create, listInvitations: api.list, revokeInvitation: api.revoke }))
vi.mock('@/service/api/userController', () => ({ userLoginUsingPost: api.login, getLoginUserUsingGet: api.me }))
vi.mock('@/service/api/spaceController', () => ({ getSpaceVoUsingGet: vi.fn().mockResolvedValue({ code:0, data:{id:'10',spaceType:1,permissionList:['spaceUser:manage']} }) }))
vi.mock('@/service/api/spaceUserController', () => ({ listSpaceUserUsingPost:vi.fn().mockResolvedValue({code:0,data:[]}),addSpaceUserUsingPost:vi.fn(),editSpaceUserUsingPost:vi.fn(),deleteSpaceUserUsingPost:vi.fn() }))
vi.mock('ant-design-vue', () => ({ message:{ success:vi.fn(),error:vi.fn() }, Modal:{ destroyAll:vi.fn() } }))
function token() { return Buffer.from(crypto.getRandomValues(new Uint8Array(32))).toString('base64url') }
beforeEach(() => {
  setActivePinia(createPinia()); vi.clearAllMocks(); api.route.hash = '#token='+token(); api.route.query = {}
  api.preview.mockResolvedValue({ code:0, data:{id:'1',spaceId:'10',spaceName:'团队',spaceRole:'viewer',expiresAt:new Date().toISOString(),usedCount:0} })
  api.join.mockResolvedValue({code:0,data:'10'});api.list.mockResolvedValue({code:0,data:[]})
})
it('anonymous visitor does not load private invitation data and keeps token out of login query', async () => {
  const wrapper=shallowMount(TeamInvitePage); await flushPromises()
  const vm=wrapper.vm as unknown as {login:()=>void};vm.login()
  expect(api.preview).not.toHaveBeenCalled();const destination=api.push.mock.calls[0]![0]
  expect(destination.query.redirect).toBe('/space/invite');expect(destination.hash===api.route.hash).toBe(true)
  expect(JSON.stringify(destination.query).includes(api.route.hash.slice(7))).toBe(false);wrapper.unmount()
})
it('preview does not join until user confirms', async () => {
  useLoginUserStore().setLoginUser({id:'3'});const wrapper=shallowMount(TeamInvitePage);await flushPromises()
  expect(api.preview).toHaveBeenCalledTimes(1);expect(api.join).not.toHaveBeenCalled()
  await (wrapper.vm as unknown as {join:()=>Promise<void>}).join();expect(api.join).toHaveBeenCalledTimes(1)
  expect(api.replace).toHaveBeenCalledWith('/space/10');wrapper.unmount()
})
it('expired or revoked invitation displays the server reason and cannot join', async () => {
  useLoginUserStore().setLoginUser({id:'3'});api.preview.mockResolvedValue({code:50001,message:'邀请已过期'})
  const wrapper=shallowMount(TeamInvitePage);await flushPromises();const vm=wrapper.vm as unknown as {error:string;join:()=>Promise<void>}
  expect(vm.error).toBe('邀请已过期');await vm.join();expect(api.join).not.toHaveBeenCalled();wrapper.unmount()
})
it('creates a fragment link and clears it when revoked', async () => {
  const generated=token();api.create.mockResolvedValue({code:0,data:{id:'8',token:generated}});api.revoke.mockResolvedValue({code:0,data:true})
  const wrapper=shallowMount(SpaceMembersPage,{props:{id:'10'}});await flushPromises()
  const vm=wrapper.vm as unknown as { createLink:()=>Promise<void>;revokeLink:(id:string)=>Promise<void>;inviteLink:string }
  await vm.createLink();const link=new URL(vm.inviteLink);expect(link.search).toBe('');expect(link.hash.includes(generated)).toBe(true)
  await vm.revokeLink('8');expect(vm.inviteLink).toBe('');wrapper.unmount()
})
it('successful login restores invitation fragment without adding it to redirect query', async () => {
  api.route.query={redirect:'/space/invite'};api.login.mockResolvedValue({code:0});api.me.mockResolvedValue({code:0,data:{id:'3'}})
  const wrapper=shallowMount(UserLoginPage)
  await (wrapper.vm as unknown as {handleSubmit:(v:API.UserLoginRequest)=>Promise<void>}).handleSubmit({userAccount:'fixture',userPassword:crypto.randomUUID()})
  const destination=api.replace.mock.calls[0]![0];expect(destination.path).toBe('/space/invite');expect(destination.hash===api.route.hash).toBe(true)
  expect(destination.query).toBeUndefined();wrapper.unmount()
})

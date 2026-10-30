import request from '@/request'
export type Invitation = { id: string | number; spaceId: string | number; spaceName?: string; spaceRole: string; expiresAt: string; maxUses?: number; usedCount: number }
type Result<T> = { code?: number; message?: string; data?: T }
export const createInvitation = (data: { spaceId: string; spaceRole: string; validDays: number; maxUses?: number }) =>
  request<Result<{ id: string | number; token: string }>>('/api/spaceUser/invite/create', { method: 'POST', data })
export const listInvitations = (spaceId: string) => request<Result<Invitation[]>>('/api/spaceUser/invite/list', { method: 'POST', data: { spaceId } })
export const revokeInvitation = (spaceId: string, id: string | number) => request<Result<boolean>>('/api/spaceUser/invite/revoke', { method: 'POST', data: { spaceId, id } })
export const previewInvitation = (token: string) => request<Result<Invitation>>('/api/spaceUser/invite/preview', { method: 'POST', data: { token } })
export const joinInvitation = (token: string) => request<Result<string | number>>('/api/spaceUser/invite/join', { method: 'POST', data: { token } })

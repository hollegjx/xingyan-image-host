import { describe, expect, it } from 'vitest'
import { parseId, sameId, usagePercent, formatBytes, hasPermission, permissions, roleName } from '../space'

describe('space IDs and usage', () => {
  it('fails closed without server permissions and never treats admin or ownership as membership', () => {
    expect(hasPermission(undefined, permissions.edit)).toBe(false)
    expect(hasPermission({}, permissions.view)).toBe(false)
    expect(hasPermission({ permissionList: ['picture:view'] }, permissions.edit)).toBe(false)
    expect(hasPermission({ permissionList: ['picture:view'] }, permissions.view)).toBe(true)
    expect(roleName('admin')).toBe('管理员')
    expect(roleName('root')).toBe('未知角色')
  })
  it('keeps full Long precision and compares numeric fixtures safely', () => {
    expect(parseId('9223372036854775807')).toBe('9223372036854775807')
    expect(sameId('12', 12)).toBe(true)
    expect(sameId('9007199254740992', '9007199254740993')).toBe(false)
  })
  it.each([
    undefined,
    null,
    0,
    -1,
    1.2,
    Number.MAX_SAFE_INTEGER + 1,
    '',
    '01',
    '1e2',
    '9223372036854775808',
    ['12'],
    ' 12',
  ])('rejects invalid or ambiguous ID %j', (value) => {
    expect(parseId(value)).toBeUndefined()
    expect(sameId(value, value)).toBe(false)
  })
  it('formats server string byte counts and bounds progress', () => {
    expect(formatBytes('104857600')).toBe('100.0 MiB')
    expect(usagePercent('5', '100')).toBe(5)
    expect(usagePercent(200, 100)).toBe(100)
    expect(usagePercent(0, 0)).toBe(0)
  })
})

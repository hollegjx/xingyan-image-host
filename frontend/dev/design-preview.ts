import type { Plugin } from 'vite'

// Original colorful artwork and synthetic records, served only by the opt-in Vite dev mode.
// The interface is neutral; image content deliberately retains its own colors.
const artwork = [
  {
    name: '山野之间 · 留一片宁静',
    category: '自然',
    tags: ['自然', '绿色'],
    bg: '#d9f0ed',
    ink: '#238a6b',
    kind: 0,
  },
  {
    name: '光与拱门的对话',
    category: '建筑',
    tags: ['建筑', '极简'],
    bg: '#ffe4cf',
    ink: '#d77949',
    kind: 1,
  },
  {
    name: '向上生长的日常',
    category: '插画',
    tags: ['自然', '绿色'],
    bg: '#ebf4d7',
    ink: '#438735',
    kind: 2,
  },
  {
    name: 'LESS / 少即是多',
    category: '海报',
    tags: ['创意', '极简'],
    bg: '#e8e1ff',
    ink: '#6741d9',
    kind: 3,
  },
  {
    name: '海岸线上的漫游',
    category: '自然',
    tags: ['自然', '蓝色'],
    bg: '#d5efff',
    ink: '#2084c5',
    kind: 0,
  },
  {
    name: 'FORM — 形状实验',
    category: '海报',
    tags: ['极简', '创意'],
    bg: '#ffe4d8',
    ink: '#e85f39',
    kind: 4,
  },
  {
    name: '留白，是另一种表达',
    category: '建筑',
    tags: ['建筑', '极简'],
    bg: '#e1eaff',
    ink: '#5b77cc',
    kind: 1,
  },
  {
    name: '慢一点，也很好',
    category: '插画',
    tags: ['生活', '暖色'],
    bg: '#fff0cd',
    ink: '#b77b35',
    kind: 5,
  },
  {
    name: '夜色里的山',
    category: '自然',
    tags: ['自然', '蓝色'],
    bg: '#7785c8',
    ink: '#293f88',
    kind: 0,
  },
  {
    name: '小小的植物图鉴',
    category: '插画',
    tags: ['绿色', '生活'],
    bg: '#e0f3d3',
    ink: '#529532',
    kind: 2,
  },
  {
    name: 'LIGHT / 光的形状',
    category: '海报',
    tags: ['创意', '暖色'],
    bg: '#ffe7ac',
    ink: '#dc7a12',
    kind: 3,
  },
  {
    name: '日常几何',
    category: '海报',
    tags: ['极简', '创意'],
    bg: '#dcedff',
    ink: '#3886d6',
    kind: 4,
  },
]

export function coverSvg(index: number) {
  const art = artwork[index % artwork.length]!
  const shapes = [
    '<circle cx="322" cy="93" r="39" fill="#fafafa"/><path d="m-50 350 200-236 135 149 75-90 160 177" fill="currentColor" opacity=".5"/><path d="m-10 350 170-145 147 129 86-78 107 94" fill="currentColor"/>',
    '<path d="M108 350V155a132 132 0 0 1 264 0v195" fill="currentColor"/><path d="M145 350V155a95 95 0 0 1 190 0v195" fill="#f5f5f7"/><path d="M176 350V155a64 64 0 0 1 128 0v195" fill="currentColor" opacity=".55"/><path d="M205 350V155a35 35 0 0 1 70 0v195" fill="currentColor"/><path d="M350 0 480 0 480 350 210 350" fill="#fff" opacity=".1"/>',
    '<path d="M228 350c5-100 21-194 71-274" stroke="currentColor" stroke-width="4"/><path d="M247 238C143 217 134 143 141 115c76 2 121 62 106 123ZM269 174C245 86 303 36 337 33c25 60 2 108-68 141ZM233 309c35-66 107-76 142-58-26 58-77 82-142 58Z" fill="currentColor"/><path d="M254 207C191 175 180 94 193 69c60 24 78 80 61 138Z" fill="currentColor" opacity=".5"/>',
    '<text x="240" y="82" text-anchor="middle" fill="currentColor" font-size="64" font-family="Arial,sans-serif" font-weight="bold" letter-spacing="-3">LESS.</text><circle cx="240" cy="210" r="87" stroke="currentColor" stroke-width="1.5"/><circle cx="240" cy="210" r="62" stroke="currentColor" stroke-width="1.5" opacity=".5"/><circle cx="240" cy="210" r="37" fill="currentColor"/><text x="240" y="322" text-anchor="middle" fill="currentColor" font-size="10" letter-spacing="3" font-family="sans-serif">SPACE TO CREATE.</text>',
    '<text x="240" y="72" text-anchor="middle" fill="currentColor" font-size="52" font-family="Arial,sans-serif" font-weight="bold">FORM.</text><rect x="40" y="106" width="170" height="170" rx="85" fill="currentColor"/><path d="M240 106h170v170H240z" fill="currentColor" opacity=".42"/><circle cx="325" cy="191" r="52" fill="#f5f5f7"/><text x="240" y="326" text-anchor="middle" fill="currentColor" font-size="10" letter-spacing="3" font-family="sans-serif">SIMPLICITY IN FORM.</text>',
    '<ellipse cx="247" cy="284" rx="143" ry="14" fill="currentColor" opacity=".1"/><path d="M135 132h175v91a58 58 0 0 1-58 58h-59a58 58 0 0 1-58-58Z" fill="currentColor"/><path d="M309 153h22a35 35 0 0 1 0 70h-22" stroke="currentColor" stroke-width="17"/><path d="M187 105c-30-31 29-31 0-62m53 62c-30-31 29-31 0-62" stroke="currentColor" stroke-width="3" opacity=".5"/><text x="35" y="325" fill="currentColor" font-size="11" letter-spacing="4" font-family="sans-serif">A MOMENT FOR YOURSELF</text>',
  ]
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 480 350" width="480" height="350" fill="none" style="color:${art.ink}"><rect width="480" height="350" fill="${art.bg}"/>${shapes[art.kind]}</svg>`
}

const user = {
  id: '9007199254740901',
  userName: '预览用户',
  userRole: 0,
  userAccount: 'design-preview',
  userProfile: '收藏日常的光，也分享偶然的灵感。',
  createTime: '2026-10-01T09:00:00+08:00',
}
const pictures = artwork.map((item, index) => ({
  ...item,
  id: String(900100 + index),
  url: `/__design/cover/${index}.svg`,
  thumbnailUrl: `/__design/cover/${index}.svg`,
  introduction: '本地设计预览使用的原创矢量示例，不是线上图片。',
  userId: user.id,
  user,
  picWidth: 480,
  picHeight: 350,
  picFormat: 'svg',
  picSize: 2048,
  picScale: 480 / 350,
  picColor: item.ink,
  uploadStatus: 1,
  reviewStatus: 1,
}))
const space = {
  id: '900201',
  spaceName: '我的灵感库',
  userId: user.id,
  spaceType: 0,
  spaceLevel: 0,
  totalCount: 12,
  maxCount: 100,
  totalSize: 24576,
  maxSize: 104857600,
  canManageSpace: true,
  permissionList: [
    'picture:view',
    'picture:upload',
    'picture:edit',
    'picture:delete',
    'space:analyze',
  ],
}
// Separate synthetic submissions: never imply that drafts are public gallery records.
const submissions = pictures.map((picture, index) => ({
  ...picture,
  id: String(900500 + index),
  createTime: `2026-10-${String(25 - index).padStart(2, '0')}T09:00:00+08:00`,
  editTime: `2026-10-${String(25 - index).padStart(2, '0')}T10:00:00+08:00`,
  uploadStatus: index % 4 === 1 ? 0 : 1,
  reviewStatus: index % 4 === 2 ? 0 : index % 4 === 3 ? 2 : 1,
  reviewMessage: index % 4 === 3 ? '请补充作品的来源说明，完善后可重新提交审核。' : '',
}))
const team = {
  ...space,
  id: '900202',
  spaceName: '设计共创小组',
  spaceType: 1,
  permissionList: [...space.permissionList, 'spaceUser:manage'],
}
const readPosts = new Set([
  '/api/picture/list/page/vo',
  '/api/picture/my/list/page/vo',
  '/api/space/my/list/page/vo',
  '/api/spaceUser/my/list/page',
])

export function designPreviewPlugin(): Plugin {
  return {
    name: 'local-read-only-design-preview',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        const url = new URL(req.url || '/', 'http://localhost')
        const cover = /^\/__design\/cover\/(\d+)\.svg$/.exec(url.pathname)
        if (cover) {
          res.setHeader('Content-Type', 'image/svg+xml')
          res.setHeader('Cache-Control', 'no-store')
          res.end(coverSvg(Number(cover[1])))
          return
        }
        if (!url.pathname.startsWith('/api/') && !url.pathname.startsWith('/tuku/api/')) {
          next()
          return
        }
        res.setHeader('Content-Type', 'application/json; charset=utf-8')
        res.setHeader('Cache-Control', 'no-store')
        const reply = (data: unknown, code = 0, message = '') =>
          res.end(JSON.stringify({ code, data, message }))
        // Fail closed: no fallback proxy, no upload, login, AI or real server writes.
        if (req.method !== 'GET' && !(req.method === 'POST' && readPosts.has(url.pathname))) {
          reply(null, 50000, '本地设计预览为只读模式，请勿输入真实账号；写入不会发送至任何服务器。')
          return
        }
        let body: Record<string, unknown> = {}
        if (req.method === 'POST') {
          try {
            let raw = ''
            for await (const chunk of req) {
              raw += chunk
              if (raw.length > 8192) throw new Error('body limit')
            }
            body = raw ? JSON.parse(raw) : {}
          } catch {
            reply(null, 40000, '预览请求参数无效')
            return
          }
        }
        if (url.pathname === '/api/user/get/login') {
          reply(user)
          return
        }
        if (url.pathname === '/api/picture/tag_category') {
          reply({
            categoryList: ['自然', '建筑', '插画', '海报'],
            tagList: ['自然', '绿色', '极简', '创意', '暖色', '蓝色', '生活'],
          })
          return
        }
        if (url.pathname === '/api/picture/get/vo') {
          const row = [...pictures, ...submissions].find((p) => p.id === url.searchParams.get('id'))
          reply(row || null, row ? 0 : 40400, row ? '' : '示例图片不存在')
          return
        }
        if (
          url.pathname === '/api/picture/list/page/vo' ||
          url.pathname === '/api/picture/my/list/page/vo'
        ) {
          const source = url.pathname === '/api/picture/my/list/page/vo' ? submissions : pictures
          const found = source.filter(
            (p) =>
              (body.uploadStatus === undefined || p.uploadStatus === body.uploadStatus) &&
              (body.reviewStatus === undefined || p.reviewStatus === body.reviewStatus) &&
              (!body.category || p.category === body.category) &&
              (!body.searchText ||
                `${p.name} ${p.introduction}`.includes(String(body.searchText))) &&
              (!Array.isArray(body.tags) || body.tags.every((tag) => p.tags.includes(String(tag)))),
          )
          const pageSize = Math.max(1, Math.min(20, Number(body.pageSize) || 12)),
            current = Math.max(1, Number(body.current) || 1)
          reply({
            records: found
              .slice((current - 1) * pageSize, current * pageSize)
              .map((p) => (body.spaceId ? { ...p, spaceId: String(body.spaceId) } : p)),
            total: found.length,
          })
          return
        }
        if (url.pathname === '/api/space/my/list/page/vo') {
          reply({ records: [space], total: 1 })
          return
        }
        if (url.pathname === '/api/space/get/vo') {
          reply(url.searchParams.get('id') === team.id ? team : space)
          return
        }
        if (url.pathname === '/api/spaceUser/my/list/page') {
          reply({
            records: [
              {
                id: '900301',
                spaceId: team.id,
                userId: user.id,
                spaceRole: 'admin',
                owner: true,
                space: team,
              },
            ],
            total: 1,
          })
          return
        }
        if (url.pathname === '/api/space/list/level') {
          reply([{ value: 0, text: '普通版', maxCount: 100, maxSize: 104857600 }])
          return
        }
        reply(null, 50000, '此接口未提供设计示例，请使用本地后端联调模式。')
      })
    },
  }
}

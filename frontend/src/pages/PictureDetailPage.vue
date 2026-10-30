<template>
  <div id="pictureDetailPage" :aria-busy="loading">
    <!-- 返回按钮 -->
    <a-button type="text" @click="goBack" class="back-btn">
      <LeftOutlined />
      返回列表
    </a-button>

    <a-alert v-if="error" type="error" :message="error" show-icon />
    <div v-else-if="loading" class="detail-layout" aria-label="正在加载图片">
      <div class="detail-stage detail-stage--loading" />
      <a-skeleton active :paragraph="{ rows: 6 }" />
    </div>
    <div v-else class="detail-layout">
      <!-- 左侧：图片预览 -->
      <figure class="detail-stage">
        <img v-if="picture.url" :src="picture.url" :alt="picture.name" class="detail-image" />
      </figure>

      <!-- 右侧：图片信息 -->
      <aside class="detail-info" aria-label="图片信息">
        <span class="eyebrow">{{ picture.category || '默认' }}</span>
        <h1 class="detail-title">{{ picture.name || '未命名图片' }}</h1>

        <div class="detail-author">
          <img
            v-if="picture.user?.userAvatar"
            :src="picture.user.userAvatar"
            alt=""
            class="author-avatar"
          />
          <span v-else class="author-avatar author-avatar--initial" aria-hidden="true">{{
            authorInitial
          }}</span>
          <span>{{ picture.user?.userName || '匿名用户' }}</span>
        </div>

        <p v-if="picture.introduction" class="detail-intro">{{ picture.introduction }}</p>

        <div v-if="picture.tags?.length" class="detail-tags" aria-label="标签">
          <span v-for="tag in picture.tags" :key="tag" class="detail-tag">{{ tag }}</span>
        </div>

        <dl class="detail-meta">
          <div>
            <dt>尺寸</dt>
            <dd>{{ dimensions }}</dd>
          </div>
          <div>
            <dt>格式</dt>
            <dd>{{ picture.picFormat?.toUpperCase() || '-' }}</dd>
          </div>
          <div>
            <dt>大小</dt>
            <dd>{{ formatSize(picture.picSize) }}</dd>
          </div>
          <div>
            <dt>主色调</dt>
            <dd>
              <template v-if="picColor">
                <span class="color-swatch" :style="{ backgroundColor: picColor }" />{{ picColor }}
              </template>
              <template v-else>未提取</template>
            </dd>
          </div>
        </dl>

        <div class="detail-actions">
          <a-button v-if="picture.id" aria-label="分享" @click="shareOpen = true">
            <template #icon><ShareAltOutlined /></template>
            分享
          </a-button>
          <a-button v-if="canFavorite" :loading="favoriteBusy" :aria-pressed="!!picture.favorited" @click="toggleFavorite">
            <template #icon><HeartFilled v-if="picture.favorited" aria-hidden="true" /><HeartOutlined v-else aria-hidden="true" /></template>
            {{ picture.favorited ? '已收藏' : '收藏' }} · {{ picture.favoriteCount ?? 0 }}
          </a-button>
          <PictureSearchButton :picture="picture" />
          <a-dropdown v-if="canPublish" :trigger="['click']">
            <a-button>更多 ···</a-button>
            <template #overlay
              ><a-menu
                ><a-menu-item key="submit" @click="submitOpen = true"
                  >投稿到公共图库</a-menu-item
                ></a-menu
              ></template
            >
          </a-dropdown>
        </div>
        <!-- 操作按钮（仅作者或管理员可见） -->
        <div v-if="canEdit" class="detail-actions detail-actions--owner">
          <a-button
            v-if="authorizedSpace?.spaceType === 1"
            type="primary"
            @click="router.push(`/picture/${props.id}/collaborate`)"
          >
            协同编辑
          </a-button>
          <a-button @click="doEdit">
            <template #icon><EditOutlined /></template>
            编辑
          </a-button>
          <a-button v-if="canDelete" danger @click="doDelete">
            <template #icon><DeleteOutlined /></template>
            删除
          </a-button>
        </div>
      </aside>
    </div>
    <PictureShareModal
      v-model:open="shareOpen"
      :picture="picture"
      :can-generate-private-link="canPublish"
    />
    <PictureSubmitModal v-model:open="submitOpen" :picture="picture" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount, computed } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { EditOutlined, DeleteOutlined, LeftOutlined, ShareAltOutlined, HeartOutlined, HeartFilled } from '@ant-design/icons-vue'
import { getPictureVoByIdUsingGet, deletePictureUsingPost } from '@/service/api/pictureController'
import { setPictureFavorite } from '@/service/api/pictureFavoriteController'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { getSpaceVoUsingGet } from '@/service/api/spaceController'
import {
  parseId,
  sameId,
  formatBytes as formatSize,
  hasPermission,
  permissions,
} from '@/utils/space'
import PictureSearchButton from '@/components/PictureSearchButton.vue'
import PictureShareModal from '@/components/PictureShareModal.vue'
import PictureSubmitModal from '@/components/PictureSubmitModal.vue'
import { normalizePictureColor } from '@/utils/pictureExtensions'
import { returnToGallery } from '@/utils/galleryNavigation'
import { useNavigationStore } from '@/stores/useNavigationStore'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const navigation = useNavigationStore()

// 接收路由参数
const props = defineProps<{
  id: string
}>()

const picture = ref<API.PictureDetailVO>({})
const loading = ref(false)
const error = ref('')
const authorizedSpace = ref<API.SpaceVO>()
const shareOpen = ref(false)
const submitOpen = ref(false)
const canPublish = computed(
  () =>
    picture.value.spaceId != null &&
    picture.value.uploadStatus === 1 &&
    (authorizedSpace.value?.spaceType === 1
      ? hasPermission(authorizedSpace.value, permissions.members)
      : sameId(authorizedSpace.value?.userId, loginUserStore.loginUser.id)),
)
const picColor = computed(() => normalizePictureColor(picture.value.picColor))
const authorInitial = computed(() => (picture.value.user?.userName || '匿').slice(0, 1))
// 宽高合并展示，宽高比保留两位小数，避免出现长浮点数
const dimensions = computed(() => {
  const { picWidth: width, picHeight: height } = picture.value
  if (!width || !height) return '-'
  return `${width} × ${height} · ${(width / height).toFixed(2)} : 1`
})
let sequence = 0
const favoriteBusy = ref(false)
const canFavorite = computed(() => picture.value.spaceId == null && picture.value.uploadStatus === 1 && picture.value.reviewStatus === 1)
const toggleFavorite = async () => {
  if (!canFavorite.value || favoriteBusy.value || !picture.value.id) return
  if (!loginUserStore.loginUser.id) {
    await router.push({ path: '/user/login', query: { redirect: `/picture/${props.id}` } })
    return
  }
  const ticket = sequence
  const id = picture.value.id
  favoriteBusy.value = true
  try {
    const result = await setPictureFavorite(id, !picture.value.favorited)
    if (ticket !== sequence) return
    if (result.code !== 0 || !result.data) throw new Error(result.message || '收藏操作失败')
    picture.value.favoriteCount = result.data.favoriteCount
    picture.value.favorited = result.data.favorited
  } catch (e) {
    if (ticket === sequence) message.error(e instanceof Error ? e.message : '收藏操作失败，请重试')
  } finally {
    if (ticket === sequence) favoriteBusy.value = false
  }
}

// 判断是否有编辑/删除权限（作者本人或管理员）
const canEdit = computed(() => {
  const loginUser = loginUserStore.loginUser
  if (!loginUser?.id) return false
  if (picture.value.spaceId != null) return hasPermission(authorizedSpace.value, permissions.edit)
  // 管理员
  if (loginUser.userRole === 1) return true
  // 作者本人
  return sameId(picture.value.userId, loginUser.id)
})
const canDelete = computed(() =>
  picture.value.spaceId != null
    ? hasPermission(authorizedSpace.value, permissions.delete)
    : loginUserStore.loginUser.userRole === 1,
)

// 返回列表
const goBack = () => {
  returnToGallery(router, picture.value.spaceId ? `/space/${picture.value.spaceId}` : '/')
}

// 获取图片详情
const fetchPictureDetail = async () => {
  const ticket = ++sequence
  favoriteBusy.value = false
  picture.value = {}
  shareOpen.value = false
  submitOpen.value = false
  authorizedSpace.value = undefined
  error.value = ''
  loading.value = true
  try {
    const id = parseId(props.id)
    if (!id) throw new Error('图片 ID 无效')
    const res = await getPictureVoByIdUsingGet({ id })
    if (ticket !== sequence) return
    if (res.code === 0 && res.data) {
      if (res.data.spaceId != null) {
        const info = await getSpaceVoUsingGet({ id: res.data.spaceId })
        if (ticket !== sequence) return
        if (info.code !== 0 || !hasPermission(info.data, permissions.view))
          throw new Error('无权访问此私有图片')
        authorizedSpace.value = info.data
        if (info.data) navigation.rememberSpace(info.data)
      }
      picture.value = res.data
      navigation.rememberPicture(res.data)
    } else {
      throw new Error(res.message || '获取图片详情失败')
    }
  } catch (e) {
    if (ticket === sequence) error.value = e instanceof Error ? e.message : '获取图片详情失败'
  } finally {
    if (ticket === sequence) loading.value = false
  }
}

// 编辑图片
const doEdit = () => {
  router.push({
    path: '/picture/add',
    query: {
      id: props.id,
      ...(picture.value.spaceId ? { spaceId: String(picture.value.spaceId) } : {}),
    },
  })
}

// 删除图片
const doDelete = () => {
  const id = parseId(props.id)
  const targetSpace = parseId(picture.value.spaceId)
  if (!id || !canDelete.value) return
  Modal.confirm({
    title: '确认删除',
    content: '确定要删除这张图片吗？删除后无法恢复。',
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await deletePictureUsingPost({ id })
        if (res.code === 0) {
          message.success('删除成功')
          returnToGallery(router, targetSpace ? `/space/${targetSpace}` : '/')
        } else {
          message.error('删除失败：' + res.message)
        }
      } catch {
        message.error('删除失败')
      }
    },
  })
}

watch(() => props.id, fetchPictureDetail, { immediate: true })
onBeforeUnmount(() => {
  sequence++
})
</script>

<style scoped>
#pictureDetailPage {
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 8px 0 48px;
}

.back-btn {
  margin: 0 0 20px -12px;
  color: var(--muted);
}
.back-btn:hover {
  color: var(--ink);
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 48px;
  align-items: start;
}

/* 浅灰画布承托图片，图片本身保留原色 */
.detail-stage {
  margin: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  padding: clamp(16px, 3vw, 40px);
  border-radius: 20px;
  background: var(--surface-soft);
}
.detail-stage--loading {
  min-height: 480px;
}
.detail-image {
  display: block;
  max-width: 100%;
  max-height: 72vh;
  object-fit: contain;
  border-radius: 6px;
}

.detail-info {
  position: sticky;
  top: 24px;
  min-width: 0;
}
.detail-info .eyebrow {
  color: var(--muted);
  letter-spacing: 0.15em;
}
.detail-title {
  margin: 10px 0 18px;
  font-size: clamp(24px, 2.4vw, 32px);
  font-weight: 650;
  line-height: 1.25;
  word-break: break-word;
}
.detail-author {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--ink);
  font-size: 14px;
}
.author-avatar {
  flex: none;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
}
.author-avatar--initial {
  display: inline-grid;
  place-items: center;
  background: #ececf0;
  color: var(--muted);
  font-size: 12px;
}
.detail-intro {
  margin: 20px 0 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-line;
  /* 简介里常带来源网址，允许在任意位置换行，避免撑破信息栏 */
  overflow-wrap: anywhere;
}
.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 20px;
}
.detail-tag {
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--surface-soft);
  color: var(--muted);
  font-size: 12px;
}
.detail-tag::before {
  content: '#';
  margin-right: 1px;
  opacity: 0.6;
}

.detail-meta {
  margin: 28px 0 0;
  border-top: 1px solid var(--line);
}
.detail-meta > div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--line);
  font-size: 13px;
}
.detail-meta dt {
  color: var(--muted);
}
.detail-meta dd {
  display: inline-flex;
  align-items: center;
  margin: 0;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
  text-align: right;
}
.color-swatch {
  display: inline-block;
  width: 14px;
  height: 14px;
  margin-right: 8px;
  border-radius: 4px;
  box-shadow: inset 0 0 0 1px rgb(0 0 0 / 12%);
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
}
.detail-actions--owner {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
}

@media (max-width: 960px) {
  .detail-layout {
    grid-template-columns: minmax(0, 1fr);
    gap: 28px;
  }
  .detail-info {
    position: static;
  }
  .detail-stage {
    min-height: 240px;
    border-radius: 16px;
  }
  .detail-stage--loading {
    min-height: 280px;
  }
}
</style>

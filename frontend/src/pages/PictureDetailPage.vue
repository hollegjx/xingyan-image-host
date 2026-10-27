<template>
  <div id="pictureDetailPage" :aria-busy="loading">
    <!-- 返回按钮 -->
    <a-button type="link" @click="goBack" class="back-btn">
      <LeftOutlined />
      返回列表
    </a-button>

    <a-alert v-if="error" type="error" :message="error" show-icon />
    <a-row v-else :gutter="24">
      <!-- 左侧：图片预览 -->
      <a-col :xs="24" :md="16">
        <a-card title="图片预览" :loading="loading">
          <img v-if="picture.url" :src="picture.url" :alt="picture.name" class="detail-image" />
        </a-card>
      </a-col>

      <!-- 右侧：图片信息 -->
      <a-col :xs="24" :md="8">
        <a-card title="图片信息" :loading="loading">
          <a-descriptions :column="1">
            <a-descriptions-item label="作者">
              <a-space v-if="picture.user">
                <a-avatar :src="picture.user.userAvatar" :size="24" />
                <span>{{ picture.user.userName || '匿名用户' }}</span>
              </a-space>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item label="名称">
              {{ picture.name || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="简介">
              {{ picture.introduction || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="分类">
              {{ picture.category || '默认' }}
            </a-descriptions-item>
            <a-descriptions-item label="标签">
              <a-tag v-for="tag in picture.tags" :key="tag">
                {{ tag }}
              </a-tag>
              <span v-if="!picture.tags?.length">-</span>
            </a-descriptions-item>
            <a-descriptions-item label="格式">
              {{ picture.picFormat?.toUpperCase() || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="主色调">
              <a-space v-if="picColor"
                ><span class="color-swatch" :style="{ backgroundColor: picColor }" />{{
                  picColor
                }}</a-space
              >
              <span v-else>未提取</span>
            </a-descriptions-item>
            <a-descriptions-item label="宽度">
              {{ picture.picWidth || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="高度">
              {{ picture.picHeight || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="宽高比">
              {{ picture.picScale || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="大小">
              {{ formatSize(picture.picSize) }}
            </a-descriptions-item>
          </a-descriptions>

          <div class="action-buttons">
            <PictureSearchButton :picture="picture" /><a-button
              v-if="picture.id"
              aria-label="分享"
              @click="shareOpen = true"
              >分享</a-button
            >
          </div>
          <!-- 操作按钮（仅作者或管理员可见） -->
          <a-space v-if="canEdit" class="action-buttons">
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
          </a-space>
        </a-card>
      </a-col>
    </a-row>
    <PictureShareModal v-model:open="shareOpen" :picture="picture" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount, computed } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { EditOutlined, DeleteOutlined, LeftOutlined } from '@ant-design/icons-vue'
import { getPictureVoByIdUsingGet, deletePictureUsingPost } from '@/service/api/pictureController'
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

const picture = ref<API.PictureVO>({})
const loading = ref(false)
const error = ref('')
const authorizedSpace = ref<API.SpaceVO>()
const shareOpen = ref(false)
const picColor = computed(() => normalizePictureColor(picture.value.picColor))
let sequence = 0

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
  picture.value = {}
  shareOpen.value = false
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
  padding: 24px;
}

.back-btn {
  margin-bottom: 16px;
  padding-left: 0;
}

.detail-image {
  width: 100%;
  max-height: 70vh;
  object-fit: contain;
}

.action-buttons {
  margin-top: 16px;
}
.color-swatch {
  display: inline-block;
  width: 18px;
  height: 18px;
  border: 1px solid #999;
  border-radius: 3px;
}
</style>

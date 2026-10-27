<template>
  <div id="addPicturePage" :aria-busy="initializing">
    <header class="creation-header">
      <span class="eyebrow">创作工作台</span>
      <h2>{{ picture ? '编辑 / 替换图片' : '创建图片' }}</h2>
      <p>{{ picture ? '给好作品，一点新的可能。' : '为想留下的画面，找一处安静的屋檐。' }}</p>
    </header>
    <a-spin v-if="initializing" />
    <a-alert v-else-if="error" type="error" :message="error" show-icon>
      <template #action><a-button @click="loadContext">重试</a-button></template>
    </a-alert>
    <template v-else>
      <ol class="creation-steps" aria-label="创建图片步骤">
        <li :class="{ complete: picture }" :aria-current="!picture ? 'step' : undefined">
          <span>01</span> 上传图片
        </li>
        <li class="step-line" aria-hidden="true" />
        <li :class="{ current: picture }" :aria-current="picture ? 'step' : undefined">
          <span>02</span> 完善信息
        </li>
      </ol>
      <a-alert
        :message="
          spaceId
            ? `${space?.spaceType === 1 ? '团队空间' : '私有空间'}：${space?.spaceName || ''}（仅授权成员可见）`
            : '公共图库：提交后进入公开审核流程'
        "
        type="info"
      />
      <SpaceUsage v-if="space" :space="space" />

      <!-- 图片上传组件 -->
      <PictureUpload
        :key="contextKey"
        :picture="picture"
        :space-id="spaceId"
        :disabled="submitting || editing"
        :onSuccess="onSuccess"
        @busy="uploading = $event"
      />

      <div v-if="picture" class="edit-actions">
        <a-space wrap>
          <a-button :disabled="uploading || submitting || editing" @click="cropper?.openModal()"
            >编辑图片</a-button
          >
          <a-button :disabled="uploading || submitting || editing" @click="outpainting?.openModal()"
            >AI 扩图</a-button
          >
        </a-space>
      </div>
      <ImageCropper
        v-if="picture"
        :key="`crop-${contextKey}`"
        ref="cropper"
        :picture="picture"
        :on-success="onEditSuccess"
        @busy="editing = $event"
      />
      <ImageOutPainting
        v-if="picture"
        :key="`ai-${contextKey}`"
        ref="outpainting"
        :picture="picture"
        :on-success="onEditSuccess"
        @busy="editing = $event"
      />

      <!-- 图片信息表单 -->
      <a-form
        v-if="picture"
        class="picture-metadata"
        layout="vertical"
        :model="pictureForm"
        @finish="handleSubmit"
      >
        <h3>让这张图片，更容易被找到。</h3>
        <a-form-item label="名称">
          <a-input
            v-model:value="pictureForm.name"
            placeholder="请输入名称"
            :disabled="busy"
            allow-clear
          />
        </a-form-item>

        <a-form-item label="简介">
          <a-textarea
            v-model:value="pictureForm.introduction"
            :disabled="busy"
            placeholder="请输入简介"
            :auto-size="{ minRows: 2, maxRows: 5 }"
            allow-clear
          />
        </a-form-item>

        <a-form-item label="分类">
          <a-auto-complete
            v-model:value="pictureForm.category"
            :disabled="busy"
            placeholder="请输入分类"
            :options="categoryOptions"
            allow-clear
          />
        </a-form-item>

        <a-form-item label="标签">
          <a-select
            v-model:value="pictureForm.tags"
            :disabled="busy"
            mode="tags"
            placeholder="请输入标签"
            :options="tagOptions"
            allow-clear
          />
        </a-form-item>

        <a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            :loading="submitting"
            :disabled="uploading || editing"
            >保存图片信息</a-button
          >
        </a-form-item>
      </a-form>
    </template>
  </div>
</template>

<script setup lang="ts">
import PictureUpload from '@/components/PictureUpload.vue'
import { computed, ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  editPictureUsingPost,
  listPictureTagCategoryUsingGet,
  getPictureVoByIdUsingGet,
} from '@/service/api/pictureController'
import { useRouter, useRoute } from 'vue-router'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'
import { useNavigationStore } from '@/stores/useNavigationStore'
import { returnToGallery } from '@/utils/galleryNavigation'
import { getSpaceVoUsingGet } from '@/service/api/spaceController'
import { parseId, sameId, hasPermission, permissions } from '@/utils/space'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import SpaceUsage from '@/components/SpaceUsage.vue'
import ImageCropper from '@/components/ImageCropper.vue'
import ImageOutPainting from '@/components/ImageOutPainting.vue'

const router = useRouter()
const route = useRoute()
const picture = ref<API.PictureVO>()
const submitting = ref(false)
const uploading = ref(false)
const editing = ref(false)
const cropper = ref<InstanceType<typeof ImageCropper>>()
const outpainting = ref<InstanceType<typeof ImageOutPainting>>()
const initializing = ref(true)
const error = ref('')
const space = ref<API.SpaceVO>()
const spaceId = ref<string>()
const contextKey = ref(0)
const user = useLoginUserStore()
const navigation = useNavigationStore()
let sequence = 0
onBeforeUnmount(() => {
  sequence++
})

// 表单数据
const pictureForm = ref({
  name: '',
  introduction: '',
  category: '',
  tags: [] as string[],
})
const savedForm = ref(JSON.stringify(pictureForm.value))
const dirty = computed(
  () => !!picture.value && JSON.stringify(pictureForm.value) !== savedForm.value,
)
const busy = computed(() => uploading.value || submitting.value || editing.value)
useUnsavedChanges(dirty, busy)

// 分类和标签选项（从后端获取）
const categoryOptions = ref<{ value: string }[]>([])
const tagOptions = ref<{ value: string }[]>([])

// 获取标签和分类列表
const fetchTagCategory = async () => {
  try {
    const res = await listPictureTagCategoryUsingGet()
    if (res.code === 0 && res.data) {
      // 转换为 options 格式
      categoryOptions.value = (res.data.categoryList || []).map((item) => ({ value: item }))
      tagOptions.value = (res.data.tagList || []).map((item) => ({ value: item }))
    }
  } catch (error) {
    console.error('获取标签分类失败', error)
  }
}

// 获取旧图片数据（编辑模式）
const fillForm = (item: API.PictureVO) => {
  pictureForm.value = {
    name: item.name || '',
    introduction: item.introduction || '',
    category: item.category || '',
    tags: item.tags || [],
  }
  savedForm.value = JSON.stringify(pictureForm.value)
}
const loadContext = async () => {
  const ticket = ++sequence
  initializing.value = true
  error.value = ''
  picture.value = undefined
  space.value = undefined
  spaceId.value = undefined
  contextKey.value++
  fillForm({})
  try {
    const id = parseId(route.query.id)
    let target = parseId(route.query.spaceId)
    if (route.query.id !== undefined && !id) throw new Error('图片 ID 无效，已停止上传')
    if (route.query.spaceId !== undefined && !target) throw new Error('空间 ID 无效，已停止上传')
    let old: API.PictureVO | undefined
    if (id) {
      const res = await getPictureVoByIdUsingGet({ id })
      if (ticket !== sequence) return
      if (res.code !== 0 || !res.data) throw new Error(res.message || '无法读取原图片，已停止上传')
      old = res.data
      if (target && !sameId(target, old.spaceId))
        throw new Error('图片与目标空间不匹配，不能跨空间替换')
      target = parseId(old.spaceId)
      if (old.spaceId != null && !target) throw new Error('图片空间信息异常')
      if (!target && user.loginUser.userRole !== 1 && !sameId(old.userId, user.loginUser.id))
        throw new Error('无权编辑此图片')
    }
    if (target) {
      const res = await getSpaceVoUsingGet({ id: target })
      if (ticket !== sequence) return
      if (res.code !== 0 || !res.data) throw new Error(res.message || '获取空间失败')
      if (!hasPermission(res.data, id ? permissions.edit : permissions.upload))
        throw new Error('没有此空间的上传或编辑权限')
      space.value = res.data
      navigation.rememberSpace(res.data)
    }
    spaceId.value = target
    picture.value = old
    if (old) {
      fillForm(old)
      navigation.rememberPicture(old)
    }
  } catch (e) {
    if (ticket === sequence) error.value = e instanceof Error ? e.message : '初始化失败，请重试'
  } finally {
    if (ticket === sequence) initializing.value = false
  }
}

// 页面加载时获取标签分类
onMounted(() => {
  fetchTagCategory()
})
watch(
  () => [route.query.id, route.query.spaceId],
  () => {
    // 上传成功后补齐 URL，仅同步地址，不重新加载/丢弃当前表单。
    if (
      sameId(route.query.id, picture.value?.id) &&
      (spaceId.value
        ? sameId(route.query.spaceId, spaceId.value)
        : route.query.spaceId === undefined)
    )
      return
    loadContext()
  },
  { immediate: true },
)
watch(uploading, (busy) => {
  const id = parseId(picture.value?.id)
  if (!busy && id && !error.value && !sameId(route.query.id, id)) {
    router.replace({
      path: route.path,
      query: { id, ...(spaceId.value ? { spaceId: spaceId.value } : {}) },
    })
  }
})

// 图片上传成功回调
const onEditSuccess = async (newPicture: API.PictureVO) => {
  const draft = { ...pictureForm.value }
  await onSuccess(newPicture)
  pictureForm.value = draft
}
const onSuccess = async (newPicture: API.PictureVO) => {
  if (
    !parseId(newPicture.id) ||
    (spaceId.value ? !sameId(newPicture.spaceId, spaceId.value) : newPicture.spaceId != null)
  ) {
    error.value = '上传结果归属异常，请刷新后检查'
    return
  }
  picture.value = newPicture
  fillForm(newPicture)
  // 等子组件解除忙碌状态后由 watch 补齐 URL，避免与离开守卫冲突。
  if (spaceId.value) {
    const ticket = sequence
    try {
      const res = await getSpaceVoUsingGet({ id: spaceId.value })
      if (ticket === sequence && res.code === 0 && res.data) space.value = res.data
    } catch {
      message.warning('图片已上传，用量刷新失败，可返回空间刷新')
    }
  }
}

// 提交表单
const handleSubmit = async () => {
  if (submitting.value || uploading.value || initializing.value || error.value) return
  if (!picture.value?.id) {
    message.error('请先上传图片')
    return
  }

  submitting.value = true
  try {
    const res = await editPictureUsingPost({
      id: picture.value.id,
      name: pictureForm.value.name,
      introduction: pictureForm.value.introduction,
      category: pictureForm.value.category,
      tags: pictureForm.value.tags,
    })

    if (res.code === 0) {
      message.success('图片信息已提交')
      savedForm.value = JSON.stringify(pictureForm.value)
      submitting.value = false
      returnToGallery(router, spaceId.value ? `/space/${spaceId.value}` : '/')
    } else {
      message.error('创建失败：' + res.message)
    }
  } catch (error) {
    message.error('创建失败：' + (error as Error).message)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
#addPicturePage {
  width: 100%;
}
.creation-header {
  margin-bottom: 28px;
}
.creation-header .eyebrow {
  color: var(--muted);
  font-size: 11px;
}
.creation-header h2 {
  margin: 10px 0 8px;
  font-size: 32px;
  font-weight: 650;
}
.creation-header p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}
.creation-steps {
  display: flex;
  align-items: center;
  gap: 16px;
  list-style: none;
  padding: 0;
  margin: 0 0 28px;
}
.creation-steps li:not(.step-line) {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 12px;
  color: var(--muted);
  white-space: nowrap;
}
.creation-steps li > span {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--surface-soft);
  font-size: 10px;
}
.creation-steps li[aria-current='step'] {
  color: var(--ink);
  font-weight: 600;
}
.creation-steps li[aria-current='step'] > span {
  color: white;
  background: var(--ink);
}
.step-line {
  width: 56px;
  height: 1px;
  background: var(--line);
}
.edit-actions {
  margin: 20px 0 28px;
}
.picture-metadata {
  padding-top: 24px;
  border-top: 1px solid var(--line);
}
.picture-metadata h3 {
  margin: 0 0 24px;
  font-size: 18px;
  font-weight: 600;
}
.picture-metadata :deep(.ant-btn-primary) {
  border-radius: 100px;
  padding-inline: 24px;
}
#addPicturePage :deep(.ant-alert-info) {
  background: var(--surface-soft);
  border-color: var(--line);
  border-radius: 12px;
  font-size: 12px;
}
@media (max-width: 640px) {
  .creation-header h2 {
    font-size: 27px;
  }
  .creation-steps {
    gap: 10px;
  }
  .step-line {
    width: 28px;
  }
}
</style>

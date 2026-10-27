<template>
  <a-modal
    :open="open"
    title="批量导入图片"
    ok-text="开始导入"
    cancel-text="取消"
    :confirm-loading="uploading"
    @ok="submit"
    @cancel="close"
  >
    <a-form layout="vertical" :model="form">
      <a-form-item label="搜索关键词" required>
        <a-input v-model:value="form.searchText" placeholder="请输入搜索关键词" allow-clear :maxlength="50" />
      </a-form-item>
      <a-form-item label="导入数量" required>
        <a-input-number v-model:value="form.count" :min="1" :max="30" :precision="0" style="width: 100%" />
      </a-form-item>
      <a-form-item label="偏移量">
        <a-input-number v-model:value="form.offset" :min="0" :precision="0" style="width: 100%" />
      </a-form-item>
      <a-form-item label="名称前缀">
        <a-input v-model:value="form.namePrefix" placeholder="默认使用搜索关键词" allow-clear :maxlength="50" />
      </a-form-item>
      <a-form-item label="分类">
        <a-auto-complete
          v-model:value="form.category"
          :options="categoryOptions"
          placeholder="请输入或选择分类"
          allow-clear
          style="width: 100%"
        />
      </a-form-item>
      <a-form-item label="标签">
        <a-select
          v-model:value="form.tags"
          mode="tags"
          placeholder="请选择或输入标签"
          :options="tagOptions"
          allow-clear
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { uploadPictureByBatchUsingPost } from '@/service/api/pictureController'

defineProps<{
  open: boolean
  categoryOptions: { value: string }[]
  tagOptions: { value: string }[]
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  success: [count: number]
}>()

const uploading = ref(false)
const form = reactive<API.PictureUploadByBatchRequest>({
  searchText: '',
  count: 10,
  offset: 0,
  namePrefix: undefined,
  category: undefined,
  tags: [],
})

const reset = () => {
  form.searchText = ''
  form.count = 10
  form.offset = 0
  form.namePrefix = undefined
  form.category = undefined
  form.tags = []
}

const close = () => {
  if (uploading.value) return
  reset()
  emit('update:open', false)
}

const submit = async () => {
  if (uploading.value) return
  const searchText = form.searchText?.trim()
  const count = form.count ?? 10
  const offset = form.offset ?? 0
  if (!searchText) {
    message.warning('请输入搜索关键词')
    return
  }
  if (count < 1 || count > 30 || offset < 0) {
    message.warning('请检查导入数量和偏移量')
    return
  }

  uploading.value = true
  try {
    const tags = form.tags?.map((tag) => tag.trim()).filter(Boolean)
    const res = await uploadPictureByBatchUsingPost({
      searchText,
      count,
      offset,
      namePrefix: form.namePrefix?.trim() || undefined,
      category: form.category?.trim() || undefined,
      tags: tags?.length ? tags : undefined,
    })
    if (res.code !== 0) {
      message.error('批量导入失败：' + res.message)
      return
    }
    const successCount = res.data ?? 0
    message.success(`成功导入 ${successCount} 张图片`)
    reset()
    emit('update:open', false)
    emit('success', successCount)
  } catch {
    message.error('批量导入失败')
  } finally {
    uploading.value = false
  }
}
</script>

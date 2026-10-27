<template>
  <a-modal
    :open="open"
    title="批量编辑图片"
    :confirm-loading="saving"
    :mask-closable="!saving"
    :closable="!saving"
    :keyboard="!saving"
    :cancel-button-props="{ disabled: saving }"
    ok-text="确认修改"
    @ok="submit"
    @cancel="$emit('update:open', false)"
  >
    <p>
      仅修改当前页选中的
      {{ pictures.length }} 张图片，不包含其他页。任一图片校验或更新失败，整批回滚。
    </p>
    <a-form layout="vertical" :model="values">
      <a-form-item>
        <a-checkbox v-model:checked="values.changeCategory" :disabled="saving">修改分类</a-checkbox>
        <a-input
          v-if="values.changeCategory"
          v-model:value="values.category"
          :disabled="saving"
          :maxlength="64"
          placeholder="留空表示清空分类"
          aria-label="批量分类"
        />
      </a-form-item>
      <a-form-item>
        <a-checkbox v-model:checked="values.changeTags" :disabled="saving">替换标签</a-checkbox>
        <a-select
          v-if="values.changeTags"
          v-model:value="values.tags"
          :disabled="saving"
          mode="tags"
          placeholder="留空表示清空标签"
          aria-label="批量标签"
        />
      </a-form-item>
      <a-form-item>
        <a-checkbox v-model:checked="values.rename" :disabled="saving">批量重命名</a-checkbox>
        <a-input
          v-if="values.rename"
          v-model:value="values.nameRule"
          :disabled="saving"
          :maxlength="128"
          placeholder="例如：旅行_{序号}"
          aria-label="命名规则"
        />
      </a-form-item>
    </a-form>
    <template v-if="values.rename">
      <p>
        按当前列表顺序从 1 编号，仅支持 {序号}，不执行表达式。不填占位符时，所选图片会使用同一名称。
      </p>
      <a-alert v-if="preview.error" type="warning" :message="preview.error" />
      <ul v-else class="rename-preview" aria-label="重命名预览">
        <li v-for="(name, index) in preview.names" :key="String(pictures[index]?.id)">
          {{ pictures[index]?.name || '未命名图片' }} → {{ name }}
        </li>
      </ul>
    </template>
    <a-alert v-if="error" type="error" :message="error" show-icon />
  </a-modal>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { editPictureByBatchUsingPost } from '@/service/api/pictureExtensionController'
import {
  buildBatchPictureRequest,
  renamePreview,
  type BatchPictureValues,
} from '@/utils/pictureExtensions'
const props = defineProps<{ open: boolean; spaceId: string; pictures: API.PictureVO[] }>()
const emit = defineEmits<{ 'update:open': [value: boolean]; success: [count: number] }>()
const values = reactive<BatchPictureValues>({
  changeCategory: false,
  category: '',
  changeTags: false,
  tags: [],
  rename: false,
  nameRule: '',
})
const saving = ref(false)
const error = ref('')
let alive = true
const abort = new AbortController()
const preview = computed(() => {
  try {
    return { names: renamePreview(values.nameRule, props.pictures.length), error: '' }
  } catch (e) {
    return { names: [], error: e instanceof Error ? e.message : '命名规则无效' }
  }
})
async function submit() {
  if (saving.value) return
  error.value = ''
  try {
    const body = buildBatchPictureRequest(props.spaceId, props.pictures, values)
    saving.value = true
    const res = await editPictureByBatchUsingPost(body, { signal: abort.signal })
    if (!alive) return
    if (res.code !== 0 || res.data !== props.pictures.length)
      throw new Error(res.message || '批量修改未完成，请刷新核验后再试')
    message.success(`已修改 ${res.data} 张图片`)
    emit('success', res.data)
    emit('update:open', false)
  } catch (e) {
    if (alive) error.value = e instanceof Error ? e.message : '修改失败，请刷新核验后重试'
  } finally {
    if (alive) saving.value = false
  }
}
onBeforeUnmount(() => {
  alive = false
  abort.abort()
})
</script>

<style scoped>
.rename-preview {
  max-height: 200px;
  overflow: auto;
  padding-left: 20px;
  overflow-wrap: anywhere;
}
</style>

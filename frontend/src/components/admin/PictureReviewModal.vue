<template>
  <a-modal
    :open="open"
    :title="`审核图片：${statusText}`"
    ok-text="确认审核"
    cancel-text="取消"
    :confirm-loading="confirmLoading"
    @ok="emit('submit', reviewMessage.trim())"
    @cancel="close"
  >
    <a-form layout="vertical">
      <a-form-item label="图片">
        <span>{{ picture?.name || picture?.id || '-' }}</span>
      </a-form-item>
      <a-form-item label="审核原因">
        <a-textarea
          v-model:value="reviewMessage"
          placeholder="请输入审核通过或拒绝的原因"
          :maxlength="512"
          :rows="4"
          show-count
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { PICTURE_REVIEW_STATUS, PICTURE_REVIEW_STATUS_TEXT } from '@/constants/picture'

const props = defineProps<{
  open: boolean
  picture?: API.PictureVO
  reviewStatus: number
  confirmLoading?: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  submit: [reviewMessage: string]
}>()

const reviewMessage = ref('')
const statusText = computed(() => PICTURE_REVIEW_STATUS_TEXT[props.reviewStatus] || '未知状态')

watch(
  () => [props.open, props.reviewStatus] as const,
  ([open, status]) => {
    if (open) {
      reviewMessage.value = status === PICTURE_REVIEW_STATUS.PASS ? '管理员审核通过' : '管理员审核拒绝'
    }
  },
)

const close = () => emit('update:open', false)
</script>

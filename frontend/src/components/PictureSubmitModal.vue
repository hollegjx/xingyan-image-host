<template>
  <a-modal :open="open" title="投稿到公共图库" :footer="null" @cancel="$emit('update:open', false)">
    <a-alert
      type="warning"
      show-icon
      message="审核通过后所有人可见"
      description="会复制一份图片到公共图库并进入待审核，空间里的原图保持不变。请确认你有权公开这张图片。"
    />
    <template v-if="submitted">
      <p role="status">投稿成功，等待审核</p>
      <RouterLink to="/picture/mine">查看我的投稿</RouterLink>
    </template>
    <template v-else>
      <p v-if="error" role="alert">{{ error }}</p>
      <RouterLink v-if="error" to="/picture/mine">查看我的投稿</RouterLink>
      <a-button type="primary" :loading="busy" :disabled="!picture?.id" @click="submit"
        >确认投稿</a-button
      >
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { submitPublicPicture } from '@/service/api/picturePublicationController'
const props = defineProps<{ open: boolean; picture?: API.PictureVO }>()
defineEmits<{ 'update:open': [value: boolean] }>()
const busy = ref(false)
const submitted = ref(false)
const error = ref('')
let sequence = 0
watch(
  () => [props.open, props.picture?.id],
  () => {
    sequence++
    busy.value = false
    submitted.value = false
    error.value = ''
  },
)
async function submit() {
  if (!props.open || !props.picture?.id || busy.value || submitted.value) return
  const ticket = sequence
  busy.value = true
  error.value = ''
  try {
    const result = await submitPublicPicture(props.picture.id)
    if (ticket !== sequence) return
    if (result.code !== 0 || !result.data) throw new Error(result.message || '投稿失败，请重试')
    submitted.value = true
  } catch (e) {
    if (ticket === sequence) error.value = e instanceof Error ? e.message : '投稿失败，请重试'
  } finally {
    if (ticket === sequence) busy.value = false
  }
}
</script>

<style scoped>
.ant-btn {
  margin-top: 16px;
}
</style>

<template>
  <section id="mySpacePage">
    <header class="spaces-header">
      <span class="eyebrow"><LockOutlined /> 只属于你的收藏</span>
      <h2>{{ !loading && !total && !error ? '给灵感，一个自己的空间。' : '我的空间' }}</h2>
      <p>
        {{
          total > 1
            ? '选择一个空间，继续整理你的图片。'
            : '收藏、整理、随时找回。这里的图片仅你可见。'
        }}
      </p>
    </header>
    <a-spin :spinning="loading">
      <a-alert v-if="error" type="error" :message="error" show-icon>
        <template #action><a-button size="small" @click="load">重试</a-button></template>
      </a-alert>
      <div class="spaces-grid" v-show="!error">
        <router-link
          v-for="space in spaces"
          :key="String(space.id)"
          :to="`/space/${space.id}`"
          class="space-card"
        >
          <div class="space-folder"><FolderOpenOutlined /></div>
          <div class="space-card-title">
            <h3>{{ space.spaceName }}</h3>
            <ArrowRightOutlined />
          </div>
          <p>
            {{ space.totalCount ?? 0 }} 张图片 <span>·</span> {{ formatBytes(space.totalSize) }}
          </p>
          <small>{{ spaceLevelName(space.spaceLevel) }} · 私有空间</small>
        </router-link>
      </div>
      <a-pagination
        v-if="total > 12 && !error"
        :current="current"
        :page-size="12"
        :total="total"
        :show-size-changer="false"
        @change="changePage"
      />
      <a-card
        v-if="!loading && !error && (!total || user.loginUser.userRole === 1)"
        title="创建我的私有空间"
        class="create-space-card"
      >
        <a-form layout="vertical" :model="{ name, level }" @finish="create">
          <a-form-item label="空间名称">
            <a-input v-model:value="name" placeholder="默认空间" :maxlength="30" allow-clear />
          </a-form-item>
          <a-form-item label="空间等级">
            <a-select v-model:value="level" :options="levelOptions" />
          </a-form-item>
          <p>
            {{
              user.loginUser.userRole === 1
                ? '你可以创建多个空间，分别整理不同主题的图片。'
                : '普通版即可开始收藏，之后可在空间内查看存储用量。'
            }}
          </p>
          <a-button
            type="primary"
            html-type="submit"
            :loading="creating"
            :disabled="!levelOptions.length"
            >创建空间</a-button
          >
        </a-form>
      </a-card>
    </a-spin>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LockOutlined, FolderOpenOutlined, ArrowRightOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  addSpaceUsingPost,
  listMySpacesUsingPost,
  listSpaceLevelsUsingGet,
} from '@/service/api/spaceController'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { formatBytes, parseId, spaceLevelName } from '@/utils/space'

const router = useRouter()
const route = useRoute()
const user = useLoginUserStore()
const loading = ref(true)
const creating = ref(false)
const error = ref('')
const spaces = ref<API.SpaceVO[]>([])
const current = ref(1),
  total = ref(0)
const name = ref('')
const level = ref(0)
const levels = ref<API.SpaceLevelVO[]>([])
let sequence = 0
const levelOptions = computed(() =>
  levels.value
    .filter((item) => user.loginUser.userRole === 1 || item.value === 0)
    .map((item) => ({
      value: item.value,
      label: `${item.text} · ${item.maxCount} 张 / ${formatBytes(item.maxSize)}`,
    })),
)

async function load() {
  const ticket = ++sequence
  loading.value = true
  error.value = ''
  spaces.value = []
  try {
    const res = await listMySpacesUsingPost({ current: current.value, pageSize: 12, spaceType: 0 })
    if (ticket !== sequence) return
    if (res.code !== 0 || !res.data) throw new Error(res.message || '无法获取空间')
    spaces.value = res.data.records || []
    total.value = Number(res.data.total || 0)
    // The common single-space case opens the gallery directly. Explicit list navigation
    // remains available for switching/managing spaces, without redirect loops.
    if (total.value === 1 && spaces.value.length === 1 && route.query.view !== 'list') {
      const id = parseId(spaces.value[0]?.id)
      if (!id) throw new Error('空间 ID 无效')
      await router.replace(`/space/${id}`)
      return
    }
    if (!total.value || user.loginUser.userRole === 1) {
      const result = await listSpaceLevelsUsingGet()
      if (ticket !== sequence) return
      if (result.code !== 0 || !result.data) throw new Error(result.message || '无法获取空间等级')
      levels.value = result.data
    }
  } catch (e) {
    if (ticket === sequence) error.value = e instanceof Error ? e.message : '获取空间失败，请重试'
  } finally {
    if (ticket === sequence) loading.value = false
  }
}

async function create() {
  if (creating.value) return
  creating.value = true
  try {
    const res = await addSpaceUsingPost({
      spaceName: name.value.trim(),
      spaceLevel: level.value,
      spaceType: 0,
    })
    const id = parseId(res.data)
    if (res.code === 0 && id) {
      message.success('空间创建成功')
      await router.push(`/space/${id}`)
    } else {
      message.error(res.message || '创建失败')
      // 另一个页面可能已创建成功，刷新实际空间列表。
      await load()
    }
  } catch {
    message.error('创建空间失败，请重试')
  } finally {
    creating.value = false
  }
}
function changePage(page: number) {
  current.value = page
  void load()
}
onMounted(load)
watch(
  () => route.query.view,
  () => {
    if (route.name !== 'my-space') return
    current.value = 1
    void load()
  },
)
onBeforeUnmount(() => {
  sequence++
})
</script>

<style scoped>
#mySpacePage {
  padding: 8px 0 32px;
  max-width: 880px;
  margin: 0 auto;
}
.spaces-header {
  margin-bottom: 30px;
}
.spaces-header .eyebrow {
  display: flex;
  align-items: center;
  gap: 7px;
}
.spaces-header h2 {
  font-size: 32px;
  margin: 12px 0;
  font-weight: 650;
}
.spaces-header p {
  color: var(--muted);
  font-size: 13px;
}
.spaces-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 20px;
}
.spaces-grid .space-card {
  display: block;
  margin: 0 0 24px;
  padding: 26px;
  border: 1px solid var(--line);
  border-radius: 20px;
  background: var(--surface);
  transition:
    border-color 200ms,
    box-shadow 200ms;
}
.space-card:hover {
  border-color: #c2c2c9;
  box-shadow: 0 8px 24px #00000006;
}
.space-folder {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  font-size: 25px;
  background: var(--surface-soft);
  border-radius: 14px;
  margin-bottom: 24px;
}
.space-card-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.space-card-title h3 {
  margin: 0;
  font-size: 19px;
  overflow-wrap: anywhere;
}
.space-card p {
  margin: 10px 0 20px;
  color: var(--muted);
  font-size: 12px;
}
.space-card p span {
  margin: 0 5px;
}
.space-card small {
  color: var(--muted);
  font-size: 11px;
}
.create-space-card {
  max-width: 620px;
  border-radius: 20px;
}
.create-space-card p {
  color: var(--muted);
  font-size: 12px;
}
@media (max-width: 640px) {
  .spaces-header h2 {
    font-size: 26px;
  }
  .spaces-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

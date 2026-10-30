<template>
  <section id="spaceAnalyzePage">
    <PageHeader
      :eyebrow="adminView ? '管理 · 分析' : '空间 · 分析'"
      title="图库分析"
      description="了解图库的容量、内容分布与上传变化。"
    >
      <template v-if="!adminView" #actions>
        <a-button @click="backToSpace">返回空间</a-button>
      </template>
    </PageHeader>
    <div class="filters">
      <label v-if="adminView"
        >统计范围
        <select v-model="scope" aria-label="统计范围" @change="changeScope">
          <option value="PUBLIC">公共图库</option>
          <option value="ALL">全站概览</option>
        </select>
      </label>
      <label
        >趋势维度
        <select v-model="dimension" aria-label="趋势维度" @change="load">
          <option value="day">按日</option>
          <option value="week">按周</option>
          <option value="month">按月</option>
        </select>
      </label>
      <label>开始日期<input v-model="startDate" type="date" aria-label="开始日期" /></label>
      <label>结束日期<input v-model="endDate" type="date" aria-label="结束日期" /></label>
      <label v-if="adminView && scope === 'PUBLIC'"
        >趋势用户 ID
        <input
          v-model="userId"
          type="text"
          inputmode="numeric"
          placeholder="全部用户"
          aria-label="趋势用户 ID"
        />
      </label>
      <a-space
        ><a-button type="primary" :loading="loading" @click="load">查询 / 刷新</a-button>
        <a-button @click="resetDates">默认时间窗口</a-button></a-space
      >
    </div>
    <p class="scope-note">
      日期和用户筛选只影响上传趋势；日期留空时显示最近 30 天 / 12 周 / 12 月。
    </p>
    <a-alert v-if="error" type="error" :message="error" show-icon>
      <template #action><a-button size="small" @click="load">重试</a-button></template>
    </a-alert>
    <a-spin :spinning="loading">
      <div v-if="data && !error" class="dashboard" data-testid="analysis-dashboard">
        <div class="snapshot">
          <h2>{{ data.spaceName }}</h2>
          <span>统计时间：{{ snapshotTime }}</span>
        </div>
        <div class="stats-grid">
          <a-card title="已用容量"
            ><strong>{{ formatBytes(data.usage?.usedSize) }}</strong>
            <p v-if="data.usage?.maxSize != null">配额 {{ formatBytes(data.usage.maxSize) }}</p>
            <p v-else>当前有效图片的业务容量</p>
            <a-progress
              v-if="data.usage?.sizeUsageRatio != null"
              :percent="Math.min(100, data.usage.sizeUsageRatio)"
              :show-info="false"
            />
            <small v-if="data.usage?.sizeUsageRatio != null"
              >使用率 {{ data.usage.sizeUsageRatio }}%</small
            >
            <small v-else-if="data.usage?.maxSize != null">配额为零，使用率不适用</small>
          </a-card>
          <a-card title="占用图片数"
            ><strong>{{ data.usage?.usedCount ?? 0 }} <small>张</small></strong>
            <p>
              {{
                data.usage?.maxCount != null ? `配额 ${data.usage.maxCount} 张` : '包含已上传草稿'
              }}
            </p>
            <a-progress
              v-if="data.usage?.countUsageRatio != null"
              :percent="Math.min(100, data.usage.countUsageRatio)"
              :show-info="false"
            />
            <small v-if="data.usage?.countUsageRatio != null"
              >使用率 {{ data.usage.countUsageRatio }}%</small
            >
          </a-card>
          <a-card title="已完成图片"
            ><strong>{{ data.usage?.completedCount ?? 0 }} <small>张</small></strong>
            <p>进入下方分布与趋势统计</p></a-card
          >
          <a-card title="上传草稿"
            ><strong>{{ data.usage?.draftCount ?? 0 }} <small>张</small></strong>
            <p>已占用容量，尚未完成信息保存</p></a-card
          >
        </div>
        <a-alert
          type="info"
          show-icon
          :message="
            scope === 'ALL'
              ? '全站只展示数值聚合和空间容量排行，不披露他人的私有分类、标签或图片地址。'
              : '统计排除已删除图片；公共图库包含待审核及拒绝图片，不等于首页公开列表。'
          "
        />
        <div class="charts-grid">
          <a-card v-if="scope !== 'ALL'" title="分类分布">
            <AnalysisChart title="分类分布" kind="pie" :data="data.categories || []" />
            <p class="caption">按图片数量；前 20 类以外合并为其他，空分类显示为未分类。</p>
          </a-card>
          <a-card title="图片大小分布">
            <AnalysisChart title="图片大小分布" :data="data.sizes || []" />
            <p class="caption">区间含下限、不含上限；按原图大小统计，不含缩略图等衍生文件占用的存储。</p>
          </a-card>
          <a-card v-if="scope !== 'ALL'" title="热门标签 Top 30" class="wide">
            <AnalysisChart title="热门标签" :data="data.tags || []" value-name="使用图片数" />
            <p class="caption">
              同一图片的重复标签只计一次；一图可有多个标签，因此各标签数量之和可能大于图片总数。
            </p>
          </a-card>
          <a-card title="上传趋势" class="wide">
            <p>
              {{ data.trend?.startDate }} 至 {{ data.trend?.endDate }} · 上海时间 · 周一为一周开始
            </p>
            <AnalysisChart
              title="上传趋势"
              kind="line"
              :data="data.trend?.points || []"
              label-name="日期 / 周起始日 / 月份"
            />
            <p class="caption">
              按创建日期统计当前仍存在且已完成的图片；删除图片会改变历史点，不是永久上传流水。
            </p>
          </a-card>
          <a-card v-if="scope === 'ALL'" title="空间容量排行 Top 10" class="wide">
            <a-empty v-if="!data.rankings?.length" description="暂无空间" />
            <div v-else class="table-scroll">
              <table class="ranking-table">
                <thead>
                  <tr>
                    <th scope="col">排名</th>
                    <th scope="col">空间</th>
                    <th scope="col">已用容量</th>
                    <th scope="col">图片数</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(space, index) in data.rankings" :key="String(space.spaceId)">
                    <td>{{ index + 1 }}</td>
                    <th scope="row">
                      {{ space.spaceName }}<small>ID {{ space.spaceId }}</small>
                    </th>
                    <td>{{ formatBytes(space.usedSize) }}</td>
                    <td>{{ space.usedCount }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p class="caption">按空间配额账本排序，包含草稿。不提供他人私有图片的跳转入口。</p>
          </a-card>
        </div>
      </div>
    </a-spin>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AnalysisChart from '@/components/AnalysisChart.vue'
import PageHeader from '@/components/PageHeader.vue'
import { dashboardUsingPost } from '@/service/api/spaceAnalyzeController'
import { analysisRequest } from '@/utils/analysis'
import { formatBytes, parseId, sameId } from '@/utils/space'
import { useLoginUserStore } from '@/stores/useLoginUserStore'

const props = withDefaults(defineProps<{ adminView?: boolean }>(), { adminView: false })
const route = useRoute()
const router = useRouter()
const user = useLoginUserStore()
const scope = ref<'SPACE' | 'PUBLIC' | 'ALL'>(props.adminView ? 'PUBLIC' : 'SPACE')
const dimension = ref<'day' | 'week' | 'month'>('day')
const startDate = ref('')
const endDate = ref('')
const userId = ref('')
const data = ref<API.SpaceAnalysisVO>()
const loading = ref(false)
const error = ref('')
let sequence = 0
let controller: AbortController | undefined
const snapshotTime = computed(() =>
  data.value?.generatedAt
    ? new Date(data.value.generatedAt).toLocaleString('zh-CN', {
        timeZone: 'Asia/Shanghai',
        hour12: false,
      })
    : '—',
)

async function load() {
  const current = ++sequence
  controller?.abort()
  controller = new AbortController()
  data.value = undefined
  error.value = ''
  loading.value = true
  try {
    if (props.adminView && user.loginUser.userRole !== 1)
      throw new Error('仅管理员可查看公共和全局分析')
    if (!user.loginUser.id) throw new Error('请先登录')
    const body = analysisRequest({
      scope: scope.value,
      spaceId: route.params.id,
      timeDimension: dimension.value,
      startDate: startDate.value,
      endDate: endDate.value,
      userId: userId.value,
    })
    const response = await dashboardUsingPost(body, { signal: controller.signal })
    if (current !== sequence) return
    if (response.code !== 0 || !response.data) throw new Error(response.message || '获取统计失败')
    if (
      response.data.scope !== body.scope ||
      (body.scope === 'SPACE' && !sameId(response.data.spaceId, body.spaceId))
    )
      throw new Error('统计返回的范围不匹配，请重试')
    data.value = response.data
  } catch (e) {
    if (current === sequence)
      error.value = e instanceof Error ? e.message : '统计暂时不可用，请重试'
  } finally {
    if (current === sequence) loading.value = false
  }
}
function changeScope() {
  userId.value = ''
  void load()
}
function resetDates() {
  startDate.value = ''
  endDate.value = ''
  userId.value = ''
  void load()
}
function backToSpace() {
  const id = parseId(route.params.id)
  void router.push(id ? `/space/${id}` : '/space/mine')
}
watch(
  () => [route.params.id, props.adminView, user.loginUser.id],
  () => {
    scope.value = props.adminView ? 'PUBLIC' : 'SPACE'
    void load()
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  sequence++
  controller?.abort()
})
</script>

<style scoped>
#spaceAnalyzePage {
  max-width: 1280px;
  margin: 0 auto;
  padding: 12px 4px 32px;
}
.snapshot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
h2 {
  margin: 0;
  font-size: 19px;
  font-weight: 600;
}
.snapshot span,
.scope-note,
.caption {
  color: var(--muted);
}
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 12px 16px;
  padding: 18px 20px;
  background: var(--surface-soft);
  border-radius: 16px;
  margin: 0 0 8px;
}
.filters label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--muted);
  font-size: 12px;
}
/* 原生日期与下拉控件，对齐 Ant Design 输入框的尺寸与边框 */
.filters input,
.filters select {
  height: 40px;
  border: 1px solid #dedee3;
  border-radius: 10px;
  padding: 4px 12px;
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  max-width: 180px;
  transition: border-color 0.2s ease;
}
.filters input:hover,
.filters select:hover,
.filters input:focus,
.filters select:focus {
  border-color: var(--ink);
  outline: none;
}
.scope-note,
.caption {
  font-size: 12px;
  line-height: 1.7;
}
.snapshot {
  margin: 20px 0;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}
.stats-grid :deep(.ant-card) {
  border-radius: 18px;
}
.stats-grid :deep(.ant-card-head) {
  min-height: 0;
  padding: 18px 22px 0;
  border-bottom: 0;
  color: var(--muted);
  font-size: 13px;
  font-weight: 500;
}
.stats-grid :deep(.ant-card-body) {
  padding: 10px 22px 20px;
}
.stats-grid strong {
  font-size: 30px;
  font-weight: 650;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
}
.stats-grid p {
  margin: 8px 0;
  color: var(--muted);
  font-size: 12px;
}
.stats-grid small {
  font-size: 12px;
  font-weight: normal;
}
.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 18px;
}
.charts-grid > * {
  min-width: 0;
}
.wide {
  grid-column: 1 / -1;
}
.table-scroll {
  overflow-x: auto;
}
.ranking-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
}
.ranking-table th,
.ranking-table td {
  border-bottom: 1px solid var(--line);
  padding: 12px 8px;
  overflow-wrap: anywhere;
}
.ranking-table small {
  display: block;
  color: var(--muted);
  font-weight: normal;
}
@media (max-width: 900px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }
  .filters {
    padding: 12px;
  }
  .filters input {
    max-width: 145px;
  }
  .stats-grid {
    gap: 8px;
  }
  .stats-grid :deep(.ant-card-body) {
    padding: 12px;
  }
  .stats-grid strong {
    font-size: 22px;
  }
}
</style>

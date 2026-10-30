<template>
  <div class="analysis-chart">
    <a-empty v-if="!hasData" description="暂无统计数据" />
    <div
      v-else
      ref="container"
      class="plot"
      role="img"
      :aria-label="`${title}，详细数值见下方数据表`"
    />
    <details v-if="hasData">
      <summary>{{ title }}数据表</summary>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th scope="col">{{ labelName }}</th>
              <th scope="col">{{ valueName }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in data" :key="`${index}-${item.name}`">
              <th scope="row">{{ item.name }}</th>
              <td>{{ item.count ?? 0 }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </details>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use, type EChartsType, type EChartsCoreOption } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import { chartNumber } from '@/utils/analysis'

use([BarChart, LineChart, PieChart, GridComponent, TooltipComponent, SVGRenderer])
const props = withDefaults(
  defineProps<{
    title: string
    data: API.Distribution[]
    kind?: 'bar' | 'line' | 'pie'
    labelName?: string
    valueName?: string
  }>(),
  { kind: 'bar', labelName: '名称', valueName: '图片数' },
)
const container = ref<HTMLDivElement>()
const hasData = computed(() => props.data.some((item) => chartNumber(item.count) > 0))
let chart: EChartsType | undefined
let observer: ResizeObserver | undefined
let disposed = false
let renderVersion = 0

function dispose() {
  observer?.disconnect()
  observer = undefined
  chart?.dispose()
  chart = undefined
}

async function render() {
  const version = ++renderVersion
  await nextTick()
  if (disposed || version !== renderVersion) return
  if (!container.value || !hasData.value) {
    dispose()
    return
  }
  if (chart && chart.getDom() !== container.value) dispose()
  if (!chart) {
    chart = init(container.value, undefined, { renderer: 'svg' })
    observer = new ResizeObserver(() => {
      chart?.resize()
      if (props.kind === 'pie')
        chart?.setOption({
          series: [
            {
              label: {
                show: props.data.length <= 8 && (container.value?.clientWidth || 0) >= 400,
              },
            },
          ],
        })
    })
    observer.observe(container.value)
  }
  const options: EChartsCoreOption = {
    animation: false,
    // 与界面一致的克制配色：墨色为主，辅以低饱和的沙、灰蓝、灰绿
    color: ['#1d1d1f', '#86868b', '#c7c7cc', '#c4a77d', '#7f9cb5', '#9db39a'],
    // 标签由用户输入，禁用 HTML tooltip，避免把标签当作 HTML 渲染。
    tooltip: {
      trigger: props.kind === 'pie' ? 'item' : 'axis',
      renderMode: 'richText',
      confine: true,
    },
    series:
      props.kind === 'pie'
        ? [
            {
              type: 'pie',
              radius: ['35%', '67%'],
              label: {
                show: props.data.length <= 8 && container.value.clientWidth >= 400,
                width: 80,
                overflow: 'truncate',
              },
              data: props.data.map((item) => ({ name: item.name, value: chartNumber(item.count) })),
            },
          ]
        : [
            {
              type: props.kind,
              data: props.data.map((item) => chartNumber(item.count)),
              ...(props.kind === 'line'
                ? {
                    smooth: false,
                    areaStyle: { opacity: 0.12 },
                    showSymbol: props.data.length < 45,
                  }
                : { barMaxWidth: 36 }),
            },
          ],
    ...(props.kind === 'pie'
      ? {}
      : {
          grid: { left: 12, right: 15, top: 24, bottom: 20, containLabel: true },
          xAxis: {
            type: 'category',
            data: props.data.map((item) => item.name),
            axisLabel: { width: 85, overflow: 'truncate', hideOverlap: true },
          },
          yAxis: { type: 'value', minInterval: 1 },
        }),
  }
  chart.setOption(options, true)
}
onMounted(render)
watch(() => [props.data, props.kind], render, { deep: true })
onBeforeUnmount(() => {
  disposed = true
  renderVersion++
  dispose()
})
</script>

<style scoped>
.plot {
  height: 290px;
  width: 100%;
  overflow: hidden;
}
.analysis-chart {
  min-width: 0;
}
summary {
  cursor: pointer;
  color: var(--ink);
  margin: 8px 0;
}
.table-scroll {
  overflow: auto;
  max-height: 280px;
}
table {
  border-collapse: collapse;
  width: 100%;
  font-size: 13px;
}
th,
td {
  padding: 8px;
  border-bottom: 1px solid #eee;
  text-align: left;
  overflow-wrap: anywhere;
}
</style>

<script setup lang="ts">
/** 全国牵线热力分布（抽象点 + 飞线）— 数据源：screen.region */
import { computed, inject, ref } from 'vue'
import ScreenPanel from './ScreenPanel.vue'

/** 静态地理经纬度查表（非业务数据），用于把后端返回的地区名落到画布坐标 */
const COORDS: Record<string, [number, number]> = {
  北京: [116.4, 39.9], 上海: [121.47, 31.23], 天津: [117.2, 39.13], 重庆: [106.55, 29.56],
  广州: [113.26, 23.13], 深圳: [114.06, 22.55], 成都: [104.07, 30.57], 杭州: [120.15, 30.28],
  武汉: [114.3, 30.6], 西安: [108.94, 34.34], 南京: [118.78, 32.04], 苏州: [120.58, 31.3],
  长沙: [112.94, 28.23], 郑州: [113.62, 34.75], 青岛: [120.38, 36.07], 沈阳: [123.43, 41.8],
  合肥: [117.27, 31.86], 福州: [119.3, 26.08], 厦门: [118.09, 24.48], 昆明: [102.71, 25.04],
  济南: [117.0, 36.65], 哈尔滨: [126.53, 45.8], 大连: [121.62, 38.92], 宁波: [121.55, 29.88],
  南昌: [115.89, 28.68], 贵阳: [106.63, 26.65], 太原: [112.55, 37.87], 石家庄: [114.48, 38.03],
  南宁: [108.33, 22.84], 兰州: [103.82, 36.06], 长春: [125.32, 43.82], 无锡: [120.3, 31.57],
  温州: [120.7, 28.0], 佛山: [113.11, 23.02], 东莞: [113.75, 23.04], 珠海: [113.55, 22.27],
  广东: [113.26, 23.13], 江苏: [118.78, 32.04], 浙江: [120.15, 30.28], 山东: [117.0, 36.65],
  四川: [104.07, 30.57], 湖北: [114.3, 30.6], 湖南: [112.94, 28.23], 河南: [113.62, 34.75],
  河北: [114.48, 38.03], 福建: [119.3, 26.08], 安徽: [117.27, 31.86], 江西: [115.89, 28.68],
  陕西: [108.94, 34.34], 山西: [112.55, 37.87], 辽宁: [123.43, 41.8], 吉林: [125.32, 43.82],
  黑龙江: [126.53, 45.8], 云南: [102.71, 25.04], 贵州: [106.63, 26.65], 广西: [108.33, 22.84],
  甘肃: [103.82, 36.06], 海南: [110.35, 20.02], 内蒙古: [111.73, 40.83], 新疆: [87.62, 43.79],
  宁夏: [106.27, 38.47], 青海: [101.78, 36.62], 西藏: [91.11, 29.97], 香港: [114.17, 22.32],
}

/** 去掉行政后缀再查表，未命中的地区不参与绘制 */
const lookup = (name: string): [number, number] | null => {
  const raw = String(name ?? '')
  return COORDS[raw] ?? COORDS[raw.replace(/(省|市|自治区|特别行政区|维吾尔|壮族|回族)/g, '')] ?? null
}

const screen = inject<any>('screen', ref(null))

const points = computed<any[]>(() =>
  ((screen.value?.region ?? []) as any[])
    .map((r: any) => ({ name: r?.name, pairs: Number(r?.value || 0), coord: lookup(r?.name) }))
    .filter((r: any) => !!r.coord)
    .sort((a: any, b: any) => b.pairs - a.pairs),
)

const maxPairs = computed(() => Math.max(1, ...points.value.map((p: any) => p.pairs)))
const totalPairs = computed(() => points.value.reduce((a: number, p: any) => a + p.pairs, 0))

const mapOption = computed(() => {
  const list = points.value
  const hub = list[0]
  return {
    grid: { left: 0, right: 0, top: 0, bottom: 0 },
    xAxis: { show: false, min: 95, max: 125 },
    yAxis: { show: false, min: 18, max: 45 },
    series: [
      {
        type: 'lines', coordinateSystem: 'cartesian2d', z: 2,
        effect: { show: true, period: 4, trailLength: 0.4, symbol: 'arrow', symbolSize: 4, color: '#e8c878' },
        lineStyle: { color: 'rgba(232,200,120,0.4)', width: 1, curveness: 0.3 },
        data: hub ? list.slice(1).map((c: any) => ({ coords: [hub.coord, c.coord] })) : [],
      },
      {
        type: 'effectScatter', coordinateSystem: 'cartesian2d', z: 3,
        rippleEffect: { brushType: 'stroke', scale: 3 },
        symbolSize: (_val: any, p: any) => 6 + (list[p.dataIndex]?.pairs ?? 0) / maxPairs.value * 12,
        itemStyle: { color: '#e8c878', shadowBlur: 8, shadowColor: 'rgba(232,200,120,0.6)' },
        label: { show: true, position: 'right', color: '#f0e6d2', fontSize: 10, formatter: (p: any) => `${list[p.dataIndex]?.name ?? ''} ${list[p.dataIndex]?.pairs ?? ''}` },
        data: list.map((c: any) => c.coord),
      },
    ],
  }
})
</script>

<template>
  <ScreenPanel class="map-panel" title="全国牵线热力分布">
    <div v-if="points.length" class="cross-tag">覆盖 {{ points.length }} 地 · 合计 {{ totalPairs }} 对</div>
    <div style="height: 340px"><v-chart :option="mapOption" /></div>
  </ScreenPanel>
</template>

<style scoped>
.map-panel { position: relative; }
.cross-tag { position: absolute; top: 40px; left: 14px; z-index: 3; font-size: 0.75rem; color: #e8c878; background: rgba(232,200,120,0.1); border: 1px solid rgba(232,200,120,0.25); border-radius: 8px; padding: 4px 10px; }
</style>

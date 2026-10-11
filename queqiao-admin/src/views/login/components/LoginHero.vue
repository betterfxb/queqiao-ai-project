<script setup lang="ts">
/** 登录页左侧品牌面板（纯展示） */
</script>

<template>
  <div class="brand-panel">
    <div class="brand-logo">
      <div class="logo-mark">牵</div>
      <div>
        <div class="brand-name">牵线运营中心</div>
        <div class="brand-name-en">Matchmaker Operations Center</div>
      </div>
    </div>

    <div class="brand-mid">
      <div class="brand-body">
        <div class="brand-kicker">红娘工作台</div>
        <h2 class="brand-statement">把每一位会员的<br />婚恋进程管理清楚</h2>
        <p class="brand-desc">
          会员档案、牵线匹配、沟通记录、活动运营与财务结算，<br />
          在一处完成日常工作的全部流转。
        </p>
        <ul class="brand-points">
          <li>统一会员档案与核验状态</li>
          <li>牵引记录与匹配进度可视化</li>
          <li>运营数据与结算明细可追溯</li>
        </ul>
      </div>

      <!-- 品牌展示：囍字徽记 + 同心圆环（原设计的中心装饰） -->
      <div class="brand-hero" aria-hidden="true">
        <span class="hero-ring outer" />
        <span class="hero-ring inner" />
        <span class="hero-xi">囍</span>
      </div>
    </div>

    <div class="brand-foot">
      <span>v2.0.1</span>
      <span class="dot" />
      <span>内部运营系统</span>
    </div>
  </div>
</template>

<style scoped>
.brand-panel {
  position: relative; flex: 1.1;
  background: #3d1420;
  display: flex; flex-direction: column; justify-content: space-between;
  padding: 44px 56px; color: #f5f1ef; overflow: hidden;
}
/* 中区：文案 + 囍字徽记，两端对齐、垂直居中 */
.brand-mid {
  display: flex; align-items: center; justify-content: space-between;
  gap: 32px; min-width: 0;
}
.brand-body { max-width: 460px; min-width: 0; }
.brand-logo { display: flex; align-items: center; gap: 12px; }
.logo-mark {
  width: 40px; height: 40px; border-radius: var(--dt-radius-md);
  background: #f5f1ef; color: #3d1420;
  font-size: 1.25rem; font-weight: 600; display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.brand-name { font-size: 1.0625rem; font-weight: 600; letter-spacing: 0.01em; }
.brand-name-en { font-size: 0.6875rem; color: rgba(245, 241, 239, 0.5); letter-spacing: 0.04em; margin-top: 1px; }
/* 囍字徽记：黄铜描边图形 + 同心圆环（原设计稿的中心装饰） */
.brand-hero {
  position: relative; flex-shrink: 0;
  width: 260px; height: 260px;
  display: flex; align-items: center; justify-content: center;
  user-select: none; pointer-events: none;
}
.hero-ring { position: absolute; border-radius: 50%; }
.hero-ring.outer { width: 100%; height: 100%; border: 1px solid rgba(245, 241, 239, 0.14); }
.hero-ring.inner { width: 78%; height: 78%; border: 1px solid rgba(168, 134, 63, 0.3); }
.hero-xi {
  font-size: 8.75rem; line-height: 1; font-weight: 500;
  color: transparent;
  -webkit-text-stroke: 1.5px rgba(168, 134, 63, 0.52);
}
.brand-kicker {
  display: inline-block; font-size: 0.75rem; font-weight: 500; letter-spacing: 0.08em;
  color: rgba(245, 241, 239, 0.62);
  border: 1px solid rgba(245, 241, 239, 0.2);
  padding: 3px 8px; border-radius: var(--dt-radius-sm);
}
.brand-statement {
  font-size: 2.375rem; line-height: 1.28; font-weight: 600; letter-spacing: -0.015em;
  margin: 24px 0 18px;
}
.brand-desc { font-size: 0.9375rem; line-height: 1.9; color: rgba(245, 241, 239, 0.68); margin: 0; }
.brand-points {
  list-style: none; margin: 28px 0 0; padding: 22px 0 0;
  border-top: 1px solid rgba(255, 255, 255, 0.12);
  display: flex; flex-direction: column; gap: 10px;
}
.brand-points li {
  position: relative; padding-left: 16px;
  font-size: 0.875rem; color: rgba(245, 241, 239, 0.8);
}
.brand-points li::before {
  content: ''; position: absolute; left: 0; top: 0.65em;
  width: 6px; height: 1px; background: rgba(245, 241, 239, 0.55);
}
.brand-foot {
  display: flex; align-items: center; gap: 8px;
  font-size: 0.75rem; color: rgba(245, 241, 239, 0.42);
}
.brand-foot .dot { width: 3px; height: 3px; border-radius: 50%; background: currentColor; }

/* 中宽屏：徽记适度收缩，保证文案列宽 */
@media (max-width: 1500px) {
  .brand-hero { width: 160px; height: 160px; }
  .hero-xi { font-size: 5.5rem; }
}
/* 窄屏：让位给文案，仅保留文字版式 */
@media (max-width: 1180px) {
  .brand-hero { display: none; }
}
/* 更窄：品牌面板整体收缩（≤900px 由页面隐藏整块面板） */
@media (max-width: 1100px) {
  .brand-panel { padding: 32px 36px; }
  .brand-statement { font-size: 1.875rem; }
}
</style>
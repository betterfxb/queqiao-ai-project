<script setup lang="ts">
/** 登录页左侧品牌装饰面板（纯展示） */
</script>

<template>
  <div class="brand-panel">
    <div class="brand-corner tl" /><div class="brand-corner tr" />
    <div class="brand-corner bl" /><div class="brand-corner br" />
    <div class="brand-logo">
      <div class="logo-mark">牵</div>
      <div>
        <div class="brand-name">牵线运营中心</div>
        <div class="brand-name-en">Matchmaker Operations Center</div>
      </div>
    </div>
    <div class="brand-hero">
      <div class="xi-ring r1" /><div class="xi-ring r2" />
      <div class="xi-char">囍</div>
    </div>
    <div class="brand-slogan">
      <div class="slogan-main">成就美好姻缘</div>
      <div class="slogan-sub">专业的婚恋服务管理平台</div>
      <div class="slogan-ver">v2.0.1</div>
    </div>
  </div>
</template>

<style scoped>
.brand-panel {
  position: relative; flex: 1.1;
  background: radial-gradient(circle at 30% 30%, #7a2135 0%, #5a1525 45%, #3a0e18 100%);
  display: flex; flex-direction: column; justify-content: space-between;
  padding: 48px 56px; color: #f0e6d2; overflow: hidden;
}
.brand-corner { position: absolute; width: 40px; height: 40px; border: 2px solid rgba(232, 200, 120, 0.5); }
.brand-corner.tl { top: 24px; left: 24px; border-right: none; border-bottom: none; }
.brand-corner.tr { top: 24px; right: 24px; border-left: none; border-bottom: none; }
.brand-corner.bl { bottom: 24px; left: 24px; border-right: none; border-top: none; }
.brand-corner.br { bottom: 24px; right: 24px; border-left: none; border-top: none; }
.brand-logo { display: flex; align-items: center; gap: 14px; }
.logo-mark {
  width: 52px; height: 52px; border-radius: 14px; background: var(--dt-gold-gradient);
  color: #7a2135; font-size: 1.625rem; font-weight: 800; display: flex; align-items: center; justify-content: center;
}
.brand-name { font-size: 1.375rem; font-weight: 800; letter-spacing: 0.05em; }
.brand-name-en { font-size: 0.6875rem; color: rgba(240, 230, 210, 0.6); letter-spacing: 0.08em; }
.brand-hero { position: relative; display: flex; align-items: center; justify-content: center; }
.xi-char {
  font-size: 16.25rem; font-weight: 800; color: transparent;
  -webkit-text-stroke: 2px rgba(232, 200, 120, 0.35); line-height: 1; user-select: none;
}
.xi-ring { position: absolute; border-radius: 50%; border: 1px solid rgba(232, 200, 120, 0.25); }
.xi-ring.r1 { width: 320px; height: 320px; }
.xi-ring.r2 { width: 420px; height: 420px; border-style: dashed; }
.brand-slogan { text-align: center; }
.slogan-main { font-size: 1.625rem; font-weight: 700; letter-spacing: 0.1em; }
.slogan-sub { font-size: 0.875rem; color: rgba(240, 230, 210, 0.7); margin-top: 8px; }
.slogan-ver { font-size: 0.75rem; color: rgba(240, 230, 210, 0.45); margin-top: 16px; }
</style>

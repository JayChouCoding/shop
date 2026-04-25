<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { orderApi, payApi } from '../api/services';
import { submitAlipayHtml } from '../utils/alipay';

const POLL_INTERVAL = 2000;
const PAY_FALLBACK_DELAY = 2000;
const PAY_PENDING_STATUS = 0;
const PAY_SUCCESS_STATUS = 1;

const route = useRoute();
const router = useRouter();

const orderId = computed(() => String(route.params.id || ''));
const orderStatus = ref(0);
const visualProgress = ref(0);
const networkBusy = ref(false);
const payLaunching = ref(false);
const payCtaVisible = ref(false);
const navigationLikely = ref(false);
const pollAttempts = ref(0);

let pollTimer = null;
let payFallbackTimer = null;
let progressFrame = null;

const copy = computed(() => {
  if (orderStatus.value === 10) {
    return {
      title: '抢占成功，马上为你打开付款页',
      description: '系统正在尝试自动拉起支付宝，如果没有跳转，中央按钮会立即给你继续付款。'
    };
  }
  if (orderStatus.value === 20) {
    return {
      title: '支付结果已同步',
      description: '订单已经进入后续处理，马上带你回到订单列表。'
    };
  }
  if (orderStatus.value === 50) {
    return {
      title: '本次订单未继续付款',
      description: '你可以回到订单列表查看详情，或返回首页继续选购。'
    };
  }
  if (networkBusy.value) {
    return {
      title: '网络有点忙，正在继续刷新结果',
      description: '不用重复操作，页面会继续帮你盯住当前订单状态。'
    };
  }
  return {
    title: '正在为你生成交易订单...',
    description: '订单处理中，请稍候，页面会自动推进到下一步。'
  };
});

const progressTarget = computed(() => {
  if (payLaunching.value) {
    return 96;
  }
  if (orderStatus.value === 20 || orderStatus.value === 50) {
    return 100;
  }
  if (orderStatus.value === 10) {
    return 88;
  }
  if (networkBusy.value) {
    return Math.max(60, 48 + pollAttempts.value * 4);
  }
  return Math.min(82, 34 + pollAttempts.value * 6);
});

const statusText = computed(() => {
  if (orderStatus.value === 10) return '待付款';
  if (orderStatus.value === 20) return '已支付';
  if (orderStatus.value === 50) return '已关闭';
  if (networkBusy.value) return '正在重连';
  return '处理中';
});

watch(
  progressTarget,
  (value) => {
    if (progressFrame) {
      cancelAnimationFrame(progressFrame);
    }
    const start = visualProgress.value;
    const distance = value - start;
    const duration = Math.max(360, Math.min(1100, Math.abs(distance) * 16));
    const startedAt = performance.now();

    const step = (now) => {
      const ratio = Math.min(1, (now - startedAt) / duration);
      visualProgress.value = Number((start + distance * ratio).toFixed(1));
      if (ratio < 1) {
        progressFrame = requestAnimationFrame(step);
      }
    };

    progressFrame = requestAnimationFrame(step);
  },
  { immediate: true }
);

function clearPayFallbackTimer() {
  if (payFallbackTimer) {
    clearTimeout(payFallbackTimer);
    payFallbackTimer = null;
  }
}

function markNavigationLikely() {
  navigationLikely.value = true;
  clearPayFallbackTimer();
}

function handleVisibilityChange() {
  if (document.visibilityState === 'hidden') {
    markNavigationLikely();
  }
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

function showManualPay(message) {
  payLaunching.value = false;
  payCtaVisible.value = true;
  if (message) {
    ElMessage.warning(message);
  }
}

function armPayFallback() {
  clearPayFallbackTimer();
  payFallbackTimer = window.setTimeout(() => {
    if (!navigationLikely.value) {
      showManualPay('付款入口已经准备好，请点击中央按钮继续付款');
    }
  }, PAY_FALLBACK_DELAY);
}

async function launchPayPage() {
  if (payLaunching.value) {
    return;
  }

  navigationLikely.value = false;
  payLaunching.value = true;
  payCtaVisible.value = false;

  try {
    const formHtml = await payApi.createPage(orderId.value);
    armPayFallback();
    submitAlipayHtml(formHtml);
  } catch (error) {
    clearPayFallbackTimer();
    showManualPay(error.message || '自动打开支付宝失败，请点击按钮继续付款');
    ElMessage.error(error.message || '自动打开支付宝失败，请点击按钮继续付款');
  }
}

async function trySyncPayState() {
  try {
    const pay = await payApi.query(orderId.value);
    const payStatus = Number(pay?.status);

    if (payStatus === PAY_SUCCESS_STATUS) {
      orderStatus.value = 20;
      clearPayFallbackTimer();
      stopPolling();
      window.setTimeout(() => router.replace('/account'), 800);
      return true;
    }

    if (payStatus === PAY_PENDING_STATUS) {
      orderStatus.value = 10;
      stopPolling();
      await launchPayPage();
      return true;
    }
  } catch (error) {
    return false;
  }

  return false;
}

async function pollOrderStatus() {
  if (payLaunching.value) {
    return;
  }

  try {
    const status = await orderApi.status(orderId.value);
    networkBusy.value = false;
    orderStatus.value = Number(status || 0);

    if (orderStatus.value === 10) {
      stopPolling();
      await launchPayPage();
      return;
    }
    if (orderStatus.value === 20) {
      clearPayFallbackTimer();
      stopPolling();
      window.setTimeout(() => router.replace('/account'), 800);
      return;
    }
    if (orderStatus.value === 50) {
      clearPayFallbackTimer();
      stopPolling();
      return;
    }

    const payReady = await trySyncPayState();
    if (payReady) {
      return;
    }

    pollAttempts.value += 1;
  } catch (error) {
    const payReady = await trySyncPayState();
    if (payReady) {
      return;
    }
    networkBusy.value = true;
  }
}

onMounted(async () => {
  await pollOrderStatus();
  pollTimer = setInterval(pollOrderStatus, POLL_INTERVAL);
  document.addEventListener('visibilitychange', handleVisibilityChange);
  window.addEventListener('pagehide', markNavigationLikely);
  window.addEventListener('beforeunload', markNavigationLikely);
});

onBeforeUnmount(() => {
  stopPolling();
  clearPayFallbackTimer();
  if (progressFrame) {
    cancelAnimationFrame(progressFrame);
  }
  document.removeEventListener('visibilitychange', handleVisibilityChange);
  window.removeEventListener('pagehide', markNavigationLikely);
  window.removeEventListener('beforeunload', markNavigationLikely);
});
</script>

<template>
  <section class="processing-shell">
    <div class="processing-card">
      <span class="processing-pill">订单处理中</span>
      <h1>{{ copy.title }}</h1>
      <p class="processing-desc">{{ copy.description }}</p>

      <div class="progress-panel">
        <div class="progress-track">
          <div class="progress-value" :style="{ width: `${visualProgress}%` }" />
        </div>
        <strong>{{ Math.round(visualProgress) }}%</strong>
      </div>

      <div class="status-grid">
        <div class="status-card">
          <span>订单编号</span>
          <strong>{{ orderId }}</strong>
        </div>
        <div class="status-card">
          <span>当前阶段</span>
          <strong>{{ statusText }}</strong>
        </div>
        <div class="status-card">
          <span>页面动作</span>
          <strong>{{ payCtaVisible ? '等待你点击付款' : payLaunching ? '正在打开付款页' : '自动刷新中' }}</strong>
        </div>
      </div>

      <div v-if="payCtaVisible" class="pay-cta-card">
        <span>付款入口已就绪</span>
        <button class="pay-cta-button" :disabled="payLaunching" @click="launchPayPage">
          {{ payLaunching ? '正在打开支付宝...' : '立即前往支付宝付款' }}
        </button>
        <p>如果刚才没有自动跳转，不用返回查找，直接点击上面的按钮继续完成付款。</p>
      </div>

      <div v-else class="waiting-card">
        <span>{{ networkBusy ? '正在继续刷新' : '请保持当前页面' }}</span>
        <p>页面会自动帮助你推进状态，一旦付款页准备好就会立即尝试拉起。</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.processing-shell {
  min-height: calc(100vh - 75px);
  display: grid;
  place-items: center;
  padding: 24px 16px;
  background:
    radial-gradient(circle at top left, rgba(255, 196, 152, 0.32), transparent 26%),
    linear-gradient(180deg, #fffaf5 0%, #fff1e6 100%);
}

.processing-card {
  width: min(760px, 100%);
  padding: 34px;
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 24px 60px rgba(154, 72, 28, 0.12);
  text-align: center;
}

.processing-pill,
.waiting-card span,
.pay-cta-card span {
  display: inline-flex;
  align-items: center;
  padding: 8px 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #b3471d;
  background: rgba(235, 101, 49, 0.1);
}

.processing-card h1 {
  margin: 18px 0 12px;
  font-size: clamp(30px, 4vw, 42px);
  color: #31160e;
}

.processing-desc,
.waiting-card p,
.pay-cta-card p {
  margin: 0;
  line-height: 1.7;
  color: #735041;
}

.progress-panel {
  margin-top: 28px;
}

.progress-track {
  overflow: hidden;
  height: 14px;
  border-radius: 999px;
  background: #f5ded1;
}

.progress-value {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #ff9b61 0%, #ed4b20 100%);
  box-shadow: 0 0 18px rgba(237, 75, 32, 0.35);
}

.progress-panel strong {
  display: inline-block;
  margin-top: 12px;
  font-size: 26px;
  color: #da481a;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 22px;
}

.status-card,
.waiting-card,
.pay-cta-card {
  padding: 20px;
  border-radius: 22px;
  background: linear-gradient(180deg, #fff7ef 0%, #fff1e6 100%);
}

.status-card {
  text-align: left;
}

.status-card span {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  color: #a86a45;
}

.status-card strong {
  color: #3f1b12;
}

.waiting-card,
.pay-cta-card {
  margin-top: 22px;
}

.pay-cta-card {
  border: 2px solid rgba(239, 77, 32, 0.18);
  box-shadow: 0 18px 36px rgba(230, 61, 24, 0.14);
}

.pay-cta-button {
  width: 100%;
  margin: 12px 0;
  padding: 24px;
  border: none;
  border-radius: 22px;
  font-size: clamp(24px, 4vw, 34px);
  font-weight: 800;
  color: #fff;
  background: linear-gradient(135deg, #ff7b45 0%, #e54017 100%);
  box-shadow: 0 24px 40px rgba(229, 64, 23, 0.26);
  cursor: pointer;
}

.pay-cta-button:disabled {
  opacity: 0.75;
  cursor: wait;
}

@media (max-width: 720px) {
  .processing-card {
    padding: 24px 18px;
  }

  .status-grid {
    grid-template-columns: 1fr;
  }

  .pay-cta-button {
    padding: 20px;
    font-size: 26px;
  }
}
</style>

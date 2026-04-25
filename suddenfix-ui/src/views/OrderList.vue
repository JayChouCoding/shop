<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { orderApi, payApi } from '../api/services';
import { submitAlipayHtml } from '../utils/alipay';
import { formatCurrency, orderStatusMeta } from '../utils/format';

const PAY_FALLBACK_DELAY = 2000;

const router = useRouter();

const loading = ref(false);
const orders = ref([]);

const payAssist = reactive({
  visible: false,
  orderId: '',
  orderNo: '',
  payLaunching: false,
  navigationLikely: false,
  message: '正在帮你打开支付宝收银台...',
  manualVisible: false
});

let payFallbackTimer = null;

const summary = computed(() => {
  const total = orders.value.length;
  const pending = orders.value.filter((item) => Number(item.order?.status) === 10).length;
  const paid = orders.value.filter((item) => Number(item.order?.status) >= 20).length;
  return { total, pending, paid };
});

function formatTime(value) {
  if (!value) {
    return '刚刚创建';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date);
}

async function loadOrders() {
  loading.value = true;
  try {
    const result = await orderApi.list();
    orders.value = Array.isArray(result) ? result : [];
  } catch (error) {
    ElMessage.error(error.message || '订单列表加载失败');
  } finally {
    loading.value = false;
  }
}

function clearPayFallbackTimer() {
  if (payFallbackTimer) {
    clearTimeout(payFallbackTimer);
    payFallbackTimer = null;
  }
}

function markNavigationLikely() {
  payAssist.navigationLikely = true;
  clearPayFallbackTimer();
}

function handleVisibilityChange() {
  if (document.visibilityState === 'hidden') {
    markNavigationLikely();
  }
}

function openManualPay(order, message) {
  payAssist.visible = true;
  payAssist.orderId = String(order.orderId);
  payAssist.orderNo = order.orderNo || String(order.orderId);
  payAssist.payLaunching = false;
  payAssist.manualVisible = true;
  payAssist.message =
    message || '收银台已经准备好，如果刚才没有自动跳转，请点击中央按钮继续付款。';
}

function armManualPayFallback(order) {
  clearPayFallbackTimer();
  payFallbackTimer = window.setTimeout(() => {
    if (!payAssist.navigationLikely) {
      openManualPay(order);
      ElMessage.warning('付款入口已经准备好，请点击中央按钮继续付款');
    }
  }, PAY_FALLBACK_DELAY);
}

async function launchPay(order) {
  if (!order?.orderId || payAssist.payLaunching) {
    return;
  }

  payAssist.visible = true;
  payAssist.orderId = String(order.orderId);
  payAssist.orderNo = order.orderNo || String(order.orderId);
  payAssist.payLaunching = true;
  payAssist.manualVisible = false;
  payAssist.navigationLikely = false;
  payAssist.message = '正在帮你打开支付宝收银台...';

  try {
    const formHtml = await payApi.createPage(order.orderId);
    armManualPayFallback(order);
    submitAlipayHtml(formHtml);
  } catch (error) {
    clearPayFallbackTimer();
    openManualPay(order, error.message || '自动打开支付宝失败，请点击按钮继续付款');
    ElMessage.error(error.message || '自动打开支付宝失败，请点击按钮继续付款');
  }
}

function closePayAssist() {
  if (payAssist.payLaunching && !payAssist.manualVisible) {
    return;
  }
  payAssist.visible = false;
  payAssist.orderId = '';
  payAssist.orderNo = '';
  payAssist.payLaunching = false;
  payAssist.manualVisible = false;
}

onMounted(async () => {
  await loadOrders();
  document.addEventListener('visibilitychange', handleVisibilityChange);
  window.addEventListener('pagehide', markNavigationLikely);
  window.addEventListener('beforeunload', markNavigationLikely);
});

onBeforeUnmount(() => {
  clearPayFallbackTimer();
  document.removeEventListener('visibilitychange', handleVisibilityChange);
  window.removeEventListener('pagehide', markNavigationLikely);
  window.removeEventListener('beforeunload', markNavigationLikely);
});
</script>

<template>
  <section class="orders-shell">
    <header class="orders-hero">
      <div>
        <span class="hero-pill">My Orders</span>
        <h1>支付中断不用慌，待支付订单会稳定留在这里，随时一键继续付款。</h1>
        <p>列表布局选了偏编辑式订单面板，重要动作始终贴着订单卡片本身，避免用户找不到支付入口。</p>
      </div>

      <div class="summary-grid">
        <div class="summary-card">
          <span>订单总数</span>
          <strong>{{ summary.total }}</strong>
        </div>
        <div class="summary-card warm">
          <span>待支付</span>
          <strong>{{ summary.pending }}</strong>
        </div>
        <div class="summary-card">
          <span>已支付及后续</span>
          <strong>{{ summary.paid }}</strong>
        </div>
      </div>
    </header>

    <div v-if="loading" class="loading-state">正在加载订单列表...</div>

    <div v-else-if="!orders.length" class="empty-panel">
      <span>还没有订单</span>
      <p>去秒杀会场逛逛吧，抢到的订单会第一时间出现在这里。</p>
    </div>

    <div v-else class="order-grid">
      <article v-for="entry in orders" :key="entry.order?.orderId" class="order-card">
        <div class="order-head">
          <div>
            <span class="label">订单编号</span>
            <strong>{{ entry.order?.orderNo || entry.order?.orderId }}</strong>
          </div>
          <span class="status-pill" :data-type="orderStatusMeta(entry.order?.status).type">
            {{ orderStatusMeta(entry.order?.status).text }}
          </span>
        </div>

        <div class="order-topline">
          <div class="meta-box">
            <span class="label">下单时间</span>
            <strong>{{ formatTime(entry.order?.createTime) }}</strong>
          </div>
          <div class="meta-box">
            <span class="label">支付金额</span>
            <strong>{{ formatCurrency(entry.order?.payAmount) }}</strong>
          </div>
          <div class="meta-box">
            <span class="label">收货信息</span>
            <strong>{{ entry.order?.receiverName || '未填写' }}</strong>
            <small>{{ entry.order?.receiverPhone || '暂无手机号' }}</small>
          </div>
        </div>

        <div class="item-list">
          <div v-for="item in entry.items || []" :key="item.itemId || `${entry.order?.orderId}-${item.productId}`" class="item-row">
            <div>
              <strong>{{ item.productName }}</strong>
              <small>x{{ item.quantity }}</small>
            </div>
            <span>{{ formatCurrency(item.realPayAmount || item.totalAmount || item.price) }}</span>
          </div>
        </div>

        <div class="address-bar">
          <span class="label">收货地址</span>
          <strong>{{ entry.order?.receiverAddress || '暂无地址信息' }}</strong>
        </div>

        <div class="card-actions">
          <button
            v-if="Number(entry.order?.status) === 10"
            class="pay-now-button"
            @click="launchPay(entry.order)"
          >
            去支付
          </button>
          <button v-else class="ghost-button" @click="router.push('/account')">查看订单详情</button>
        </div>
      </article>
    </div>

    <transition name="assist-fade">
      <div v-if="payAssist.visible" class="assist-overlay">
        <div class="assist-backdrop" />
        <div class="assist-card">
          <span class="hero-pill">Payment Assist</span>
          <h3>{{ payAssist.orderNo ? `订单 ${payAssist.orderNo}` : '待支付订单' }}</h3>
          <p>{{ payAssist.message }}</p>

          <button
            v-if="payAssist.manualVisible"
            class="assist-pay-button"
            :disabled="payAssist.payLaunching"
            @click="launchPay({ orderId: payAssist.orderId, orderNo: payAssist.orderNo })"
          >
            {{ payAssist.payLaunching ? '正在打开支付宝...' : '立即前往支付宝付款' }}
          </button>

          <div v-else class="assist-waiting">收银台自动拉起中，请稍候...</div>

          <div class="assist-actions">
            <button class="ghost-button" @click="closePayAssist">稍后再付</button>
            <button class="ghost-button" @click="loadOrders">刷新订单状态</button>
          </div>
        </div>
      </div>
    </transition>
  </section>
</template>

<style scoped>
.orders-shell {
  --bg: #faf4ed;
  --surface: rgba(255, 255, 255, 0.92);
  --ink: #2f1912;
  --muted: #6e564b;
  --accent: #ef6230;
  --accent-deep: #b5441b;
  min-height: 100vh;
  padding: 28px;
  background:
    radial-gradient(circle at top left, rgba(255, 188, 145, 0.34), transparent 28%),
    linear-gradient(180deg, #fffaf5 0%, var(--bg) 100%);
}

.orders-hero,
.order-card,
.empty-panel,
.assist-card {
  border-radius: 28px;
  background: var(--surface);
  box-shadow: 0 24px 60px rgba(150, 76, 31, 0.1);
  backdrop-filter: blur(12px);
}

.orders-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(320px, 0.8fr);
  gap: 20px;
  padding: 28px;
}

.hero-pill,
.label,
.status-pill,
.assist-waiting {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.hero-pill {
  padding: 7px 12px;
  letter-spacing: 0.08em;
  color: var(--accent-deep);
  background: rgba(239, 98, 48, 0.1);
}

.orders-hero h1 {
  margin: 18px 0 14px;
  font-size: clamp(32px, 4vw, 52px);
  line-height: 1.04;
  color: var(--ink);
}

.orders-hero p,
.empty-panel p,
.assist-card p,
.meta-box small {
  margin: 0;
  color: var(--muted);
  line-height: 1.7;
}

.summary-grid {
  display: grid;
  gap: 14px;
}

.summary-card {
  padding: 18px;
  border-radius: 22px;
  background: linear-gradient(180deg, #fff8f3 0%, #f6eee7 100%);
}

.summary-card.warm {
  background: linear-gradient(180deg, #fff1e6 0%, #ffe1ce 100%);
}

.summary-card span,
.label {
  color: var(--muted);
}

.summary-card strong {
  display: block;
  margin-top: 8px;
  font-size: 30px;
  color: var(--ink);
}

.loading-state,
.empty-panel {
  margin-top: 24px;
  padding: 28px;
  text-align: center;
}

.empty-panel span {
  display: block;
  margin-bottom: 10px;
  font-size: 22px;
  font-weight: 800;
}

.order-grid {
  display: grid;
  gap: 18px;
  margin-top: 24px;
}

.order-card {
  padding: 24px;
}

.order-head,
.order-topline,
.item-row,
.card-actions {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.order-head {
  align-items: center;
}

.order-head strong {
  display: block;
  margin-top: 8px;
  font-size: 22px;
  color: var(--ink);
}

.status-pill {
  padding: 8px 14px;
}

.status-pill[data-type='warning'] {
  color: #9a4e0f;
  background: rgba(255, 182, 77, 0.2);
}

.status-pill[data-type='success'] {
  color: #1c6d47;
  background: rgba(86, 201, 139, 0.16);
}

.status-pill[data-type='primary'],
.status-pill[data-type='info'] {
  color: #8a451a;
  background: rgba(239, 98, 48, 0.1);
}

.status-pill[data-type='danger'] {
  color: #8a2020;
  background: rgba(219, 75, 75, 0.14);
}

.order-topline {
  margin-top: 18px;
  align-items: stretch;
}

.meta-box,
.address-bar,
.assist-waiting {
  padding: 18px;
  border-radius: 20px;
  background: linear-gradient(180deg, #fff8f3 0%, #f7eee7 100%);
}

.meta-box {
  flex: 1;
}

.meta-box strong,
.address-bar strong {
  display: block;
  margin-top: 8px;
  color: var(--ink);
}

.item-list {
  display: grid;
  gap: 10px;
  margin-top: 18px;
}

.item-row {
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid rgba(47, 25, 18, 0.08);
}

.item-row:last-child {
  border-bottom: none;
}

.item-row strong {
  display: block;
  color: var(--ink);
}

.item-row small {
  color: var(--muted);
}

.address-bar {
  margin-top: 18px;
}

.card-actions {
  margin-top: 20px;
  justify-content: flex-end;
}

.pay-now-button,
.assist-pay-button,
.ghost-button {
  border: none;
  border-radius: 18px;
  cursor: pointer;
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.pay-now-button,
.assist-pay-button {
  color: #fff;
  background: linear-gradient(135deg, #ff7c45 0%, #e34118 100%);
  box-shadow: 0 18px 32px rgba(227, 65, 24, 0.22);
}

.pay-now-button {
  padding: 16px 24px;
  font-size: 16px;
  font-weight: 800;
}

.assist-pay-button {
  width: 100%;
  margin-top: 18px;
  padding: 24px;
  font-size: clamp(26px, 4vw, 36px);
  font-weight: 900;
}

.ghost-button {
  padding: 14px 18px;
  color: var(--ink);
  background: rgba(47, 25, 18, 0.06);
}

.pay-now-button:hover,
.assist-pay-button:hover,
.ghost-button:hover {
  transform: translateY(-1px);
}

.assist-overlay {
  position: fixed;
  inset: 0;
  z-index: 70;
  display: grid;
  place-items: center;
  padding: 20px;
}

.assist-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(44, 20, 14, 0.52);
  backdrop-filter: blur(10px);
}

.assist-card {
  position: relative;
  z-index: 1;
  width: min(720px, calc(100vw - 32px));
  padding: 28px;
  text-align: center;
}

.assist-card h3 {
  margin: 18px 0 12px;
  font-size: clamp(28px, 4vw, 40px);
  color: var(--ink);
}

.assist-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 18px;
}

.assist-fade-enter-active,
.assist-fade-leave-active {
  transition: opacity 0.24s ease;
}

.assist-fade-enter-from,
.assist-fade-leave-to {
  opacity: 0;
}

@media (max-width: 900px) {
  .orders-hero,
  .order-topline {
    grid-template-columns: 1fr;
    display: grid;
  }

  .order-head,
  .item-row,
  .card-actions,
  .assist-actions {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 640px) {
  .orders-shell {
    padding: 16px;
  }

  .orders-hero,
  .order-card,
  .assist-card {
    padding: 20px;
  }

  .assist-pay-button {
    padding: 20px;
    font-size: 28px;
  }
}
</style>

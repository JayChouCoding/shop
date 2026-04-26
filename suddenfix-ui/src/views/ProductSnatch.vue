<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { couponApi, orderApi, payApi, productApi, userApi } from '../api/services';
import { hasSession } from '../stores/session';
import { submitAlipayHtml } from '../utils/alipay';
import { formatCurrency, joinAddress } from '../utils/format';

const POLL_INTERVAL = 1800;
const PAY_FALLBACK_DELAY = 2000;
const PAY_PENDING_STATUS = 0;
const PAY_SUCCESS_STATUS = 1;

const props = defineProps({
  productId: {
    type: [String, Number],
    default: null
  },
  initialProduct: {
    type: Object,
    default: null
  }
});

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const submitting = ref(false);
const couponLoading = ref(false);
const productPayload = ref(null);
const addresses = ref([]);
const userCoupons = ref([]);
const selectedCouponToken = ref('');
const quantity = ref(1);
const now = ref(Date.now());
const visualProgress = ref(0);

const form = reactive({
  receiverName: '',
  receiverPhone: '',
  receiverAddress: '',
  remark: ''
});

const queue = reactive({
  visible: false,
  orderId: '',
  title: '准备进入抢购通道',
  description: '开抢后系统会自动帮你盯住库存和支付入口。',
  statusText: '等待开始',
  progress: 0,
  payLaunching: false,
  payCtaVisible: false,
  navigationLikely: false,
  allowClose: false,
  pollAttempts: 0
});

const resolvedProductId = computed(() => props.productId || route.params.id);
const product = computed(() => props.initialProduct || productPayload.value?.product || productPayload.value || null);
const totalAmount = computed(() => Number(product.value?.price || 0) * Number(quantity.value || 1));
const selectedCoupon = computed(() =>
  userCoupons.value.find((item) => item.couponToken === selectedCouponToken.value) || null
);
const selectedCouponDiscount = computed(() => {
  if (!selectedCoupon.value || !selectedCoupon.value.available) {
    return 0;
  }
  return Number(selectedCoupon.value.estimatedDiscountAmount || 0);
});
const payableAmount = computed(() => Math.max(0, totalAmount.value - selectedCouponDiscount.value));

const saleStart = computed(() => {
  const time = new Date(product.value?.startTime || 0).getTime();
  return Number.isNaN(time) ? 0 : time;
});

const saleEnd = computed(() => {
  const time = new Date(product.value?.endTime || 0).getTime();
  return Number.isNaN(time) ? 0 : time;
});

const countdown = computed(() => {
  const ms = Math.max(0, saleStart.value - now.value);
  const totalSeconds = Math.ceil(ms / 1000);
  const hours = String(Math.floor(totalSeconds / 3600)).padStart(2, '0');
  const minutes = String(Math.floor((totalSeconds % 3600) / 60)).padStart(2, '0');
  const seconds = String(totalSeconds % 60).padStart(2, '0');
  return [
    { label: '时', value: hours },
    { label: '分', value: minutes },
    { label: '秒', value: seconds }
  ];
});

const saleEnded = computed(() => saleEnd.value > 0 && now.value >= saleEnd.value);
const canSnatch = computed(() => Boolean(product.value?.id) && now.value >= saleStart.value && !saleEnded.value);
const primaryActionLabel = computed(() => {
  if (saleEnded.value) {
    return '本场已结束';
  }
  if (!canSnatch.value) {
    return '倒计时进行中';
  }
  if (submitting.value || queue.visible) {
    return '正在为你锁定名额';
  }
  return '立即抢购';
});

let clockTimer = null;
let pollTimer = null;
let payFallbackTimer = null;
let progressFrame = null;

function updateQueueStage(stage, overrides = {}) {
  const presets = {
    idle: {
      title: '准备进入抢购通道',
      description: '开抢后系统会自动帮你盯住库存和支付入口。',
      statusText: '等待开始',
      progress: 0,
      allowClose: false
    },
    submitting: {
      title: '请求已发出，正在帮你挤进热场',
      description: '我们已经收到下单动作，正在为你极速锁定本次抢购资格。',
      statusText: '正在提交',
      progress: 16
    },
    queueing: {
      title: '排队中，正在为你极速抢占库存...',
      description: '名额已经在处理链路里，马上为你生成交易单据和付款入口，请保持当前页面。',
      statusText: '排队处理中',
      progress: 48
    },
    retrying: {
      title: '网络稍有拥挤，正在继续为你盯结果',
      description: '不用重复点击，我们会继续自动刷新订单状态。',
      statusText: '自动重试中',
      progress: 62
    },
    pendingPayment: {
      title: '抢占成功！正在为你打开支付宝收银台',
      description: '如果 2 秒内没有自动跳转，中央按钮会立即出现，你不会找不到付款入口。',
      statusText: '待支付',
      progress: 88
    },
    launchingPayment: {
      title: '收银台准备就绪，正在发起跳转',
      description: '请稍候，当前页面会直接把你送到支付宝。',
      statusText: '打开支付中',
      progress: 96
    },
    paid: {
      title: '支付结果已回传',
      description: '订单已经进入后续履约流程，马上带你回到订单页。',
      statusText: '已支付',
      progress: 100,
      allowClose: true
    },
    closed: {
      title: '本次订单未继续进入支付',
      description: '你可以返回订单列表查看详情，或者等待下一轮活动开启。',
      statusText: '已关闭',
      progress: 100,
      allowClose: true
    }
  };

  Object.assign(queue, presets[stage] || presets.idle, overrides);
}

function animateProgress(target) {
  if (progressFrame) {
    cancelAnimationFrame(progressFrame);
  }

  const start = visualProgress.value;
  const distance = target - start;
  if (Math.abs(distance) < 0.2) {
    visualProgress.value = target;
    return;
  }

  const duration = Math.max(360, Math.min(1200, Math.abs(distance) * 16));
  const startedAt = performance.now();

  const step = (time) => {
    const ratio = Math.min(1, (time - startedAt) / duration);
    visualProgress.value = Number((start + distance * ratio).toFixed(1));
    if (ratio < 1) {
      progressFrame = requestAnimationFrame(step);
    }
  };

  progressFrame = requestAnimationFrame(step);
}

watch(
  () => queue.progress,
  (value) => animateProgress(value),
  { immediate: true }
);

function formatTime(value) {
  if (!value) {
    return '待商家设置';
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

function applyAddress(address) {
  form.receiverName = address.consignee || '';
  form.receiverPhone = address.phone || '';
  form.receiverAddress = joinAddress(address);
}

async function loadAddresses() {
  if (!hasSession()) {
    addresses.value = [];
    return;
  }

  try {
    const result = await userApi.listAddresses();
    addresses.value = Array.isArray(result) ? result : [];
    const defaultAddress = addresses.value.find((item) => item.isDefault === 1) || addresses.value[0];
    if (defaultAddress) {
      applyAddress(defaultAddress);
    }
  } catch (error) {
    addresses.value = [];
  }
}

async function loadProduct() {
  if (props.initialProduct || !resolvedProductId.value) {
    return;
  }
  loading.value = true;
  try {
    productPayload.value = await productApi.detail(resolvedProductId.value);
  } catch (error) {
    ElMessage.error(error.message || '商品加载失败');
  } finally {
    loading.value = false;
  }
}

async function loadCheckoutCoupons(orderAmount = totalAmount.value) {
  if (!hasSession() || !orderAmount) {
    userCoupons.value = [];
    selectedCouponToken.value = '';
    return;
  }

  couponLoading.value = true;
  try {
    userCoupons.value = (await couponApi.checkoutAvailable(orderAmount)) || [];
    if (selectedCouponToken.value) {
      const stillExists = userCoupons.value.some(
        (item) => item.couponToken === selectedCouponToken.value && item.available
      );
      if (!stillExists) {
        selectedCouponToken.value = '';
      }
    }
  } catch {
    userCoupons.value = [];
  } finally {
    couponLoading.value = false;
  }
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

function clearPayFallbackTimer() {
  if (payFallbackTimer) {
    clearTimeout(payFallbackTimer);
    payFallbackTimer = null;
  }
}

function markNavigationLikely() {
  queue.navigationLikely = true;
  clearPayFallbackTimer();
}

function handleVisibilityChange() {
  if (document.visibilityState === 'hidden') {
    markNavigationLikely();
  }
}

function showManualPay(message) {
  queue.payLaunching = false;
  queue.payCtaVisible = true;
  queue.allowClose = true;
  updateQueueStage('pendingPayment', {
    payCtaVisible: true,
    allowClose: true,
    description:
      message ||
      '收银台已经准备好，如果刚才没有自动跳转，请直接点击中央按钮继续付款。'
  });
}

function armManualPayFallback() {
  clearPayFallbackTimer();
  payFallbackTimer = window.setTimeout(() => {
    if (!queue.navigationLikely) {
      showManualPay();
      ElMessage.warning('付款入口已经准备好，请点击中央按钮继续付款');
    }
  }, PAY_FALLBACK_DELAY);
}

async function openCashier() {
  if (!queue.orderId || queue.payLaunching) {
    return;
  }

  queue.navigationLikely = false;
  queue.payLaunching = true;
  queue.payCtaVisible = false;
  updateQueueStage('launchingPayment');

  try {
    const formHtml = await payApi.createPage(queue.orderId);
    armManualPayFallback();
    submitAlipayHtml(formHtml);
  } catch (error) {
    clearPayFallbackTimer();
    showManualPay(error.message || '自动打开支付宝失败，请点击按钮继续付款');
    ElMessage.error(error.message || '自动打开支付宝失败，请点击按钮继续付款');
  }
}

async function trySyncQueueFromPay() {
  try {
    const pay = await payApi.query(queue.orderId);
    const payStatus = Number(pay?.status);

    if (payStatus === PAY_SUCCESS_STATUS) {
      clearPayFallbackTimer();
      updateQueueStage('paid', { payLaunching: false, payCtaVisible: false });
      stopPolling();
      window.setTimeout(() => router.replace(`/account/${queue.orderId}`), 900);
      return true;
    }

    if (payStatus === PAY_PENDING_STATUS) {
      stopPolling();
      updateQueueStage('pendingPayment');
      await openCashier();
      return true;
    }
  } catch (error) {
    return false;
  }

  return false;
}

async function handleOrderStatus(status) {
  const numericStatus = Number(status || 0);

  if (numericStatus === 10) {
    stopPolling();
    updateQueueStage('pendingPayment');
    await openCashier();
    return;
  }

  if (numericStatus === 20) {
    clearPayFallbackTimer();
    updateQueueStage('paid', { payLaunching: false, payCtaVisible: false });
    stopPolling();
    window.setTimeout(() => router.replace(`/account/${queue.orderId}`), 900);
    return;
  }

  if (numericStatus === 50) {
    clearPayFallbackTimer();
    updateQueueStage('closed', { payLaunching: false, payCtaVisible: false });
    stopPolling();
    return;
  }

  const payReady = await trySyncQueueFromPay();
  if (payReady) {
    return;
  }

  queue.pollAttempts += 1;
  updateQueueStage('queueing', {
    progress: Math.min(82, 46 + queue.pollAttempts * 5),
    description:
      queue.pollAttempts > 1
        ? '链路还在高速推进，正在为你生成交易单据并等待付款通道返回。'
        : '排队中，正在为你极速抢占库存，马上进入支付环节。'
  });
}

async function pollOrderStatus() {
  if (!queue.orderId || queue.payLaunching) {
    return;
  }

  try {
    const status = await orderApi.status(queue.orderId);
    await handleOrderStatus(status);
  } catch (error) {
    const payReady = await trySyncQueueFromPay();
    if (payReady) {
      return;
    }
    updateQueueStage('retrying', {
      progress: Math.max(queue.progress, 60)
    });
  }
}

async function startQueue(orderId) {
  queue.visible = true;
  queue.orderId = String(orderId);
  queue.pollAttempts = 0;
  queue.payLaunching = false;
  queue.payCtaVisible = false;
  queue.allowClose = false;
  updateQueueStage('queueing', { progress: 34 });

  await pollOrderStatus();
  if (!queue.payLaunching && queue.visible) {
    stopPolling();
    pollTimer = setInterval(pollOrderStatus, POLL_INTERVAL);
  }
}

async function submitOrder() {
  if (!canSnatch.value) {
    ElMessage.warning(saleEnded.value ? '本场抢购已经结束' : '还没到开抢时间');
    return;
  }

  if (!hasSession()) {
    router.push(`/auth?redirect=${encodeURIComponent(route.fullPath || `/product/${resolvedProductId.value}`)}`);
    return;
  }

  if (!form.receiverName || !form.receiverPhone || !form.receiverAddress) {
    ElMessage.warning('请先确认收货信息');
    return;
  }

  if (!product.value?.id || submitting.value) {
    return;
  }

  submitting.value = true;
  queue.visible = true;
  queue.orderId = '';
  updateQueueStage('submitting');

  try {
    const payload = {
      idempotentKey: globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`,
      products: {
        [product.value.id]: [Number(quantity.value || 1), Number(product.value.price || 0)]
      },
      productNames: {
        [product.value.id]: product.value.name
      },
      freight: 0,
      discountAmount: selectedCouponDiscount.value,
      receiverName: form.receiverName,
      receiverPhone: form.receiverPhone,
      receiverAddress: form.receiverAddress,
      remark: form.remark,
      payChannel: 1,
      couponId: selectedCoupon.value?.couponId ?? null,
      couponSegment: selectedCoupon.value?.segment ?? null,
      couponToken: selectedCoupon.value?.couponToken || ''
    };

    const orderId = await orderApi.create(payload);
    ElMessage.success('请求已进入秒杀通道，正在为你拉起支付');
    await startQueue(orderId);
  } catch (error) {
    queue.visible = false;
    updateQueueStage('idle');
    ElMessage.error(error.message || '抢购失败，请稍后重试');
  } finally {
    submitting.value = false;
  }
}

function closeQueue() {
  if (!queue.allowClose || queue.payLaunching) {
    return;
  }
  queue.visible = false;
  queue.orderId = '';
  queue.payCtaVisible = false;
  queue.payLaunching = false;
  updateQueueStage('idle');
}

onMounted(async () => {
  await Promise.all([loadProduct(), loadAddresses()]);
  clockTimer = setInterval(() => {
    now.value = Date.now();
  }, 1000);
  document.addEventListener('visibilitychange', handleVisibilityChange);
  window.addEventListener('pagehide', markNavigationLikely);
  window.addEventListener('beforeunload', markNavigationLikely);
});

watch(totalAmount, (amount) => {
  loadCheckoutCoupons(amount).catch(() => {});
}, { immediate: true });

onBeforeUnmount(() => {
  if (clockTimer) {
    clearInterval(clockTimer);
  }
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
  <section class="snatch-shell">
    <div v-if="loading" class="loading-panel">正在加载秒杀会场...</div>

    <div v-else-if="product" class="snatch-layout">
      <header class="hero-grid">
        <div class="hero-copy">
          <span class="section-pill">Flash Deal Direct</span>
          <h1>点击一次就够，排队、生成订单、拉起支付宝，全程在当前页连贯推进。</h1>
          <p>
            视觉方向选择了偏电商编辑风的暖色系陈列，大字标题负责拉起情绪，倒计时与支付引导则尽量直接，不把底层链路暴露给用户。
          </p>
        </div>

        <div class="countdown-panel">
          <span class="panel-label">{{ canSnatch ? '活动已开抢' : saleEnded ? '活动已结束' : '距离开抢' }}</span>
          <div class="countdown-grid">
            <div v-for="item in countdown" :key="item.label" class="countdown-item">
              <strong>{{ item.value }}</strong>
              <span>{{ item.label }}</span>
            </div>
          </div>
          <p>抢购时间：{{ formatTime(product.startTime) }} 至 {{ formatTime(product.endTime) }}</p>
        </div>
      </header>

      <div class="content-grid">
        <article class="product-panel">
          <img :src="product.mainImage" :alt="product.name" class="product-image" />
          <div class="product-copy">
            <span class="section-pill soft">秒杀直达支付</span>
            <h2>{{ product.name }}</h2>
            <p>{{ product.description || '系统会在名额锁定后直接为你打通支付入口，减少等待感和跳转断层。' }}</p>

            <div class="price-strip">
              <div>
                <span>原始金额</span>
                <strong>{{ formatCurrency(totalAmount) }}</strong>
              </div>
              <label class="qty-box">
                <span>购买数量</span>
                <input v-model.number="quantity" type="number" min="1" max="5" />
              </label>
            </div>

            <div class="price-strip summary-strip">
              <div>
                <span>优惠抵扣</span>
                <strong>{{ formatCurrency(selectedCouponDiscount) }}</strong>
              </div>
              <div>
                <span>实付金额</span>
                <strong>{{ formatCurrency(payableAmount) }}</strong>
              </div>
            </div>

            <div class="coupon-panel">
              <div class="panel-heading compact">
                <strong>本单可用优惠券</strong>
                <p>下单前先选券，后端会按当前订单金额重新验券并计算实付。</p>
              </div>

              <div v-if="couponLoading" class="coupon-empty">正在刷新优惠券...</div>
              <div v-else-if="!userCoupons.length" class="coupon-empty">当前没有可选优惠券</div>
              <label
                v-for="coupon in userCoupons"
                :key="coupon.couponToken"
                class="coupon-option"
                :class="{ active: selectedCouponToken === coupon.couponToken, disabled: !coupon.available }"
              >
                <input
                  v-model="selectedCouponToken"
                  type="radio"
                  :value="coupon.couponToken"
                  :disabled="!coupon.available"
                />
                <div>
                  <strong>{{ coupon.name }}</strong>
                  <p>
                    立减 {{ formatCurrency(Math.round(Number(coupon.amount || 0) * 100)) }}
                    <span>· {{ Number(coupon.minPoint || 0) > 0 ? `满 ${formatCurrency(Math.round(Number(coupon.minPoint || 0) * 100))} 可用` : '无门槛' }}</span>
                  </p>
                  <small v-if="!coupon.available">{{ coupon.unavailableReason }}</small>
                </div>
              </label>

              <button v-if="selectedCouponToken" class="coupon-reset" type="button" @click="selectedCouponToken = ''">
                本单不使用优惠券
              </button>
            </div>

            <button class="snatch-button" :disabled="!canSnatch || submitting || queue.visible" @click="submitOrder">
              <span>{{ primaryActionLabel }}</span>
              <small>{{ canSnatch ? '原地排队，成功后自动拉起支付宝' : '开抢后按钮会立即切换可点击状态' }}</small>
            </button>
          </div>
        </article>

        <aside class="address-panel">
          <div class="panel-heading">
            <strong>先确认收货信息</strong>
            <p>提前选好地址，抢购成功后系统就不需要你再手动补资料。</p>
          </div>

          <div v-if="addresses.length" class="address-list">
            <button v-for="address in addresses" :key="address.id" class="address-chip" @click="applyAddress(address)">
              <strong>{{ address.consignee }}</strong>
              <span>{{ address.phone }}</span>
              <small>{{ joinAddress(address) }}</small>
            </button>
          </div>

          <label class="field">
            <span>收货人</span>
            <input v-model="form.receiverName" type="text" placeholder="请输入收货人姓名" />
          </label>
          <label class="field">
            <span>手机号</span>
            <input v-model="form.receiverPhone" type="text" placeholder="请输入手机号" />
          </label>
          <label class="field">
            <span>收货地址</span>
            <textarea v-model="form.receiverAddress" rows="3" placeholder="请输入完整收货地址" />
          </label>
          <label class="field">
            <span>订单备注</span>
            <input v-model="form.remark" type="text" placeholder="例如：工作日白天可签收" />
          </label>
        </aside>
      </div>
    </div>

    <transition name="overlay-fade">
      <div v-if="queue.visible" class="queue-overlay">
        <div class="overlay-backdrop" />
        <div class="queue-card">
          <span class="section-pill queue-pill">抢购处理中</span>
          <h3>{{ queue.title }}</h3>
          <p class="queue-desc">{{ queue.description }}</p>

          <div class="queue-progress">
            <div class="queue-progress-track">
              <div class="queue-progress-value" :style="{ width: `${visualProgress}%` }" />
            </div>
            <strong>{{ Math.round(visualProgress) }}%</strong>
          </div>

          <div class="queue-meta">
            <div class="meta-card">
              <span>当前状态</span>
              <strong>{{ queue.statusText }}</strong>
            </div>
            <div class="meta-card">
              <span>订单编号</span>
              <strong>{{ queue.orderId || '正在生成中' }}</strong>
            </div>
            <div class="meta-card">
              <span>抢购商品</span>
              <strong>{{ product?.name || '秒杀商品' }}</strong>
            </div>
          </div>

          <div v-if="queue.payCtaVisible" class="pay-cta-panel">
            <span>付款入口已就绪</span>
            <button class="pay-cta-button" :disabled="queue.payLaunching" @click="openCashier">
              {{ queue.payLaunching ? '正在打开支付宝...' : '立即前往支付宝付款' }}
            </button>
            <p>如果刚才没有自动跳转，不用再找入口，直接点击上面的大按钮继续完成支付。</p>
          </div>

          <div v-else class="waiting-panel">
            <span>请保持当前页面</span>
            <p>系统会继续自动推进到下一步，一旦支付通道准备好就会立刻尝试拉起。</p>
          </div>

          <div class="overlay-actions">
            <button v-if="queue.allowClose" class="ghost-button" @click="closeQueue">先留在当前页</button>
            <button v-if="queue.allowClose" class="ghost-button" @click="router.push(queue.orderId ? `/account/${queue.orderId}` : '/account')">去订单详情</button>
          </div>
        </div>
      </div>
    </transition>
  </section>
</template>

<style scoped>
.snatch-shell {
  --bg: #fff7ef;
  --surface: rgba(255, 255, 255, 0.9);
  --ink: #301610;
  --muted: #765342;
  --accent: #ef6230;
  --accent-soft: #ffd8c2;
  --accent-deep: #b4431a;
  min-height: 100vh;
  padding: 28px;
  background:
    radial-gradient(circle at top left, rgba(255, 177, 117, 0.42), transparent 34%),
    radial-gradient(circle at top right, rgba(239, 98, 48, 0.12), transparent 24%),
    linear-gradient(180deg, #fffaf4 0%, var(--bg) 100%);
}

.loading-panel,
.hero-copy,
.countdown-panel,
.product-panel,
.address-panel,
.queue-card {
  border-radius: 28px;
  background: var(--surface);
  box-shadow: 0 24px 60px rgba(154, 73, 24, 0.12);
  backdrop-filter: blur(12px);
}

.snatch-layout {
  display: grid;
  gap: 22px;
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.8fr);
  gap: 18px;
}

.hero-copy,
.countdown-panel,
.address-panel,
.queue-card {
  padding: 28px;
}

.section-pill,
.panel-label,
.meta-card span,
.waiting-panel span,
.pay-cta-panel span,
.field span {
  display: inline-flex;
  align-items: center;
  padding: 7px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--accent-deep);
  background: rgba(239, 98, 48, 0.1);
}

.section-pill.soft {
  background: rgba(255, 194, 156, 0.2);
}

.hero-copy h1 {
  margin: 18px 0 14px;
  font-size: clamp(34px, 4vw, 54px);
  line-height: 1.02;
  color: var(--ink);
}

.hero-copy p,
.countdown-panel p,
.product-copy p,
.queue-desc,
.waiting-panel p,
.pay-cta-panel p,
.panel-heading p {
  margin: 0;
  color: var(--muted);
  line-height: 1.7;
}

.panel-heading.compact {
  margin-bottom: 14px;
}

.countdown-panel {
  display: grid;
  gap: 18px;
  align-content: center;
  background: linear-gradient(180deg, #fff1e4 0%, #ffe6d4 100%);
}

.countdown-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.countdown-item {
  display: grid;
  place-items: center;
  min-height: 108px;
  border-radius: 24px;
  color: #fff;
  background: linear-gradient(180deg, #ff824f 0%, #ea4a1d 100%);
}

.countdown-item strong {
  font-size: clamp(36px, 6vw, 54px);
}

.summary-strip {
  margin-top: 12px;
}

.coupon-panel {
  display: grid;
  gap: 10px;
  margin-top: 18px;
  padding: 18px;
  border-radius: 22px;
  background: rgba(255, 247, 239, 0.88);
}

.coupon-option {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(230, 120, 52, 0.16);
  background: rgba(255, 255, 255, 0.8);
  cursor: pointer;
}

.coupon-option.active {
  border-color: rgba(216, 96, 31, 0.58);
  box-shadow: 0 14px 28px rgba(216, 96, 31, 0.12);
}

.coupon-option.disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.coupon-option p {
  margin: 6px 0 0;
}

.coupon-option small,
.coupon-empty {
  color: #a96945;
}

.coupon-reset {
  justify-self: start;
  border: none;
  background: transparent;
  color: #cf5d24;
  font-weight: 700;
  cursor: pointer;
}

.countdown-item span {
  padding: 0;
  background: transparent;
  color: rgba(255, 255, 255, 0.82);
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 18px;
}

.product-panel {
  display: grid;
  grid-template-columns: minmax(260px, 0.9fr) minmax(0, 1.1fr);
  gap: 18px;
  padding: 18px;
}

.product-image {
  width: 100%;
  min-height: 380px;
  border-radius: 24px;
  object-fit: cover;
  background: #fff1e4;
}

.product-copy {
  display: grid;
  gap: 18px;
  align-content: start;
  padding: 12px 6px 12px 4px;
}

.product-copy h2 {
  margin: 0;
  font-size: 34px;
  line-height: 1.08;
}

.price-strip {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 20px;
  border-radius: 24px;
  background: linear-gradient(180deg, #fff7f1 0%, #fff1e6 100%);
}

.price-strip span,
.qty-box span {
  display: block;
  margin-bottom: 8px;
  color: var(--muted);
}

.price-strip strong {
  font-size: 38px;
  color: var(--accent-deep);
}

.qty-box {
  min-width: 140px;
}

.qty-box input,
.field input,
.field textarea {
  width: 100%;
  padding: 14px 16px;
  border: 1px solid rgba(176, 90, 46, 0.12);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.94);
  font: inherit;
  color: var(--ink);
  outline: none;
}

.snatch-button,
.pay-cta-button,
.ghost-button,
.address-chip {
  border: none;
  cursor: pointer;
  transition: transform 0.2s ease, opacity 0.2s ease, box-shadow 0.2s ease;
}

.snatch-button {
  width: 100%;
  padding: 22px;
  border-radius: 24px;
  color: #fff;
  background: linear-gradient(135deg, #ff7d46 0%, #e34017 100%);
  box-shadow: 0 20px 36px rgba(227, 64, 23, 0.24);
}

.snatch-button span {
  display: block;
  font-size: 28px;
  font-weight: 800;
}

.snatch-button small {
  display: block;
  margin-top: 6px;
  opacity: 0.9;
}

.snatch-button:disabled {
  opacity: 0.72;
  cursor: not-allowed;
  box-shadow: none;
}

.address-panel {
  display: grid;
  gap: 16px;
}

.panel-heading strong {
  display: block;
  margin-bottom: 6px;
  font-size: 26px;
}

.address-list {
  display: grid;
  gap: 10px;
}

.address-chip {
  width: 100%;
  padding: 16px;
  text-align: left;
  border-radius: 18px;
  background: linear-gradient(180deg, #fff8f3 0%, #fff1e8 100%);
}

.address-chip strong,
.address-chip span,
.address-chip small {
  display: block;
}

.address-chip span,
.address-chip small {
  color: var(--muted);
}

.field {
  display: grid;
  gap: 10px;
}

.field span,
.panel-label,
.meta-card span,
.waiting-panel span,
.pay-cta-panel span {
  width: fit-content;
}

.field textarea {
  resize: vertical;
}

.queue-overlay {
  position: fixed;
  inset: 0;
  z-index: 70;
  display: grid;
  place-items: center;
  padding: 20px;
}

.overlay-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(39, 17, 11, 0.52);
  backdrop-filter: blur(10px);
}

.queue-card {
  position: relative;
  z-index: 1;
  width: min(760px, calc(100vw - 32px));
  text-align: center;
}

.queue-pill {
  margin-bottom: 14px;
}

.queue-card h3 {
  margin: 0;
  font-size: clamp(30px, 4vw, 42px);
  color: var(--ink);
}

.queue-progress {
  margin-top: 24px;
}

.queue-progress-track {
  overflow: hidden;
  height: 14px;
  border-radius: 999px;
  background: #f7dbc9;
}

.queue-progress-value {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #ffa469 0%, #eb4d1d 100%);
  box-shadow: 0 0 18px rgba(235, 77, 29, 0.3);
}

.queue-progress strong {
  display: inline-block;
  margin-top: 12px;
  font-size: 26px;
  color: var(--accent-deep);
}

.queue-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 22px;
  text-align: left;
}

.meta-card,
.waiting-panel,
.pay-cta-panel {
  padding: 20px;
  border-radius: 22px;
  background: linear-gradient(180deg, #fff8f3 0%, #fff0e4 100%);
}

.meta-card strong {
  display: block;
  margin-top: 10px;
  font-size: 18px;
}

.waiting-panel,
.pay-cta-panel {
  margin-top: 22px;
}

.pay-cta-panel {
  border: 2px solid rgba(235, 77, 29, 0.14);
  box-shadow: 0 18px 36px rgba(227, 64, 23, 0.14);
}

.pay-cta-button {
  width: 100%;
  margin: 14px 0 12px;
  padding: 24px;
  border-radius: 24px;
  font-size: clamp(26px, 4vw, 36px);
  font-weight: 900;
  color: #fff;
  background: linear-gradient(135deg, #ff7d46 0%, #e34017 100%);
  box-shadow: 0 24px 40px rgba(227, 64, 23, 0.26);
}

.pay-cta-button:disabled,
.ghost-button:disabled,
.address-chip:disabled {
  opacity: 0.75;
  cursor: wait;
}

.overlay-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
}

.ghost-button {
  padding: 14px 18px;
  border-radius: 16px;
  background: rgba(48, 22, 16, 0.06);
  color: var(--ink);
}

.overlay-fade-enter-active,
.overlay-fade-leave-active {
  transition: opacity 0.24s ease;
}

.overlay-fade-enter-from,
.overlay-fade-leave-to {
  opacity: 0;
}

@media (max-width: 980px) {
  .hero-grid,
  .content-grid,
  .product-panel,
  .queue-meta {
    grid-template-columns: 1fr;
  }

  .product-image {
    min-height: 280px;
  }
}

@media (max-width: 640px) {
  .snatch-shell {
    padding: 16px;
  }

  .hero-copy,
  .countdown-panel,
  .address-panel,
  .queue-card {
    padding: 20px;
  }

  .product-panel {
    padding: 14px;
  }

  .countdown-grid {
    grid-template-columns: 1fr;
  }

  .price-strip,
  .overlay-actions {
    flex-direction: column;
  }

  .pay-cta-button {
    padding: 20px;
    font-size: 28px;
  }
}
</style>

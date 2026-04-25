<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { couponApi } from '../api/services';
import { formatCurrency } from '../utils/format';

const loading = ref(false);
const refreshingMine = ref(false);
const claimingMap = reactive({});
const availableCoupons = ref([]);
const myCoupons = ref([]);

const availableCount = computed(() =>
  availableCoupons.value.filter((item) => item.canClaim).length
);

async function loadCoupons() {
  loading.value = true;
  try {
    const [available, mine] = await Promise.all([couponApi.available(), couponApi.my()]);
    availableCoupons.value = available || [];
    myCoupons.value = mine || [];
  } catch (error) {
    ElMessage.error(error.message || '优惠券列表加载失败，请稍后再试。');
  } finally {
    loading.value = false;
  }
}

async function refreshMineOnly() {
  refreshingMine.value = true;
  try {
    myCoupons.value = (await couponApi.my()) || [];
  } catch (error) {
    myCoupons.value = [];
  } finally {
    refreshingMine.value = false;
  }
}

function couponConditionText(coupon) {
  const minPoint = Number(coupon.minPoint || 0);
  return minPoint > 0 ? `满 ${formatCurrency(Math.round(minPoint * 100))} 可用` : '无门槛可用';
}

function couponStockText(coupon) {
  const remaining = Number(coupon.remainingStock || 0);
  if (remaining <= 0) {
    return '已领完';
  }
  if (remaining <= 20) {
    return `仅剩 ${remaining} 张`;
  }
  return `剩余 ${remaining} 张`;
}

async function claimCoupon(coupon) {
  if (!coupon?.couponId || claimingMap[coupon.couponId]) {
    return;
  }
  claimingMap[coupon.couponId] = true;
  try {
    await couponApi.claim(coupon.couponId);
    ElMessage.success(`已领取 ${coupon.name}`);
    await Promise.all([loadCoupons(), refreshMineOnly()]);
  } catch (error) {
    ElMessage.error(error.message || '领取失败，请稍后再试。');
  } finally {
    claimingMap[coupon.couponId] = false;
  }
}

onMounted(loadCoupons);
</script>

<template>
  <section v-loading="loading" class="coupon-shell">
    <header class="coupon-hero">
      <div>
        <span class="hero-pill">Coupon Center</span>
        <h1>先领券，再去结算，优惠会直接带到下单环节。</h1>
        <p>把活动券和我的券分成两列展示，减少来回跳转，适合抢购前快速做准备。</p>
      </div>
      <div class="hero-metrics">
        <div>
          <strong>{{ availableCount }}</strong>
          <span>当前可抢</span>
        </div>
        <div>
          <strong>{{ myCoupons.length }}</strong>
          <span>我的待用券</span>
        </div>
      </div>
    </header>

    <div class="coupon-grid">
      <article class="panel-card">
        <div class="panel-head">
          <strong>活动优惠券</strong>
          <span>现在可直接领取的券都会出现在这里</span>
        </div>

        <el-empty v-if="!availableCoupons.length" description="当前没有可领取的优惠券" />

        <div v-else class="coupon-stack">
          <div v-for="coupon in availableCoupons" :key="coupon.couponId" class="coupon-card">
            <div class="coupon-main">
              <div>
                <span class="coupon-amount">{{ formatCurrency(Math.round(Number(coupon.amount || 0) * 100)) }}</span>
                <h3>{{ coupon.name }}</h3>
              </div>
              <div class="coupon-meta">
                <span>{{ couponConditionText(coupon) }}</span>
                <span>{{ couponStockText(coupon) }}</span>
              </div>
            </div>

            <div class="coupon-foot">
              <small>{{ coupon.startTime }} - {{ coupon.endTime }}</small>
              <el-button
                type="primary"
                :disabled="!coupon.canClaim || coupon.claimed"
                :loading="Boolean(claimingMap[coupon.couponId])"
                @click="claimCoupon(coupon)"
              >
                {{ coupon.claimed ? '已领取' : coupon.canClaim ? '立即领取' : '暂不可领' }}
              </el-button>
            </div>
          </div>
        </div>
      </article>

      <article class="panel-card dark-panel">
        <div class="panel-head">
          <strong>我的待用券</strong>
          <span>结算页会自动读取这里的可用券</span>
        </div>

        <el-empty v-if="!myCoupons.length && !refreshingMine" description="还没有可用优惠券，先去左侧领取一张" />

        <div v-else class="coupon-stack">
          <div v-for="coupon in myCoupons" :key="coupon.couponToken" class="coupon-card owned-card">
            <div class="coupon-main">
              <div>
                <span class="coupon-amount">{{ formatCurrency(Math.round(Number(coupon.amount || 0) * 100)) }}</span>
                <h3>{{ coupon.name }}</h3>
              </div>
              <div class="coupon-meta">
                <span>{{ couponConditionText(coupon) }}</span>
                <span>领券码 {{ coupon.couponToken }}</span>
              </div>
            </div>
            <div class="coupon-foot">
              <small>{{ coupon.startTime }} - {{ coupon.endTime }}</small>
              <span class="ready-badge">结算可用</span>
            </div>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.coupon-shell {
  min-height: calc(100vh - 75px);
  padding: 24px;
  background:
    radial-gradient(circle at top left, rgba(211, 106, 45, 0.18), transparent 24%),
    linear-gradient(160deg, #faf3ea 0%, #f6ece1 100%);
}

.coupon-hero,
.panel-card {
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 24px 60px rgba(27, 23, 19, 0.08);
  backdrop-filter: blur(12px);
}

.coupon-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
  margin-bottom: 24px;
}

.hero-pill {
  display: inline-flex;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(185, 92, 33, 0.12);
  color: #9b4a17;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.coupon-hero h1 {
  margin: 12px 0 10px;
  font-size: clamp(28px, 4vw, 42px);
  line-height: 1.15;
  color: #2f1a10;
}

.coupon-hero p {
  margin: 0;
  max-width: 680px;
  color: #7e6657;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(120px, 1fr));
  gap: 14px;
  min-width: 280px;
}

.hero-metrics div {
  display: grid;
  gap: 6px;
  align-content: center;
  padding: 18px;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(237, 117, 45, 0.14), rgba(61, 39, 22, 0.06));
}

.hero-metrics strong {
  font-size: 32px;
  color: #30150a;
}

.hero-metrics span {
  color: #835f48;
}

.coupon-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.panel-card {
  padding: 24px;
}

.dark-panel {
  background: linear-gradient(180deg, rgba(51, 32, 20, 0.95), rgba(34, 23, 17, 0.92));
  color: #fff5eb;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: baseline;
  margin-bottom: 20px;
}

.panel-head strong {
  font-size: 22px;
}

.panel-head span {
  color: inherit;
  opacity: 0.72;
}

.coupon-stack {
  display: grid;
  gap: 16px;
}

.coupon-card {
  display: grid;
  gap: 16px;
  padding: 18px;
  border-radius: 22px;
  background: linear-gradient(135deg, rgba(255, 242, 230, 0.98), rgba(255, 255, 255, 0.95));
  color: #2b1d14;
}

.owned-card {
  background: linear-gradient(135deg, rgba(255, 239, 220, 0.15), rgba(255, 255, 255, 0.08));
  color: #fff5eb;
  border: 1px solid rgba(255, 231, 207, 0.1);
}

.coupon-main {
  display: flex;
  justify-content: space-between;
  gap: 18px;
}

.coupon-main h3 {
  margin: 8px 0 0;
  font-size: 20px;
}

.coupon-amount {
  font-size: 30px;
  font-weight: 800;
  color: #bf4f18;
}

.owned-card .coupon-amount {
  color: #ffb17a;
}

.coupon-meta {
  display: grid;
  gap: 8px;
  justify-items: end;
  color: inherit;
  opacity: 0.75;
  text-align: right;
}

.coupon-foot {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.coupon-foot small {
  color: inherit;
  opacity: 0.68;
}

.ready-badge {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 177, 122, 0.18);
  color: #ffd5b0;
  font-size: 13px;
  font-weight: 700;
}

@media (max-width: 980px) {
  .coupon-hero,
  .coupon-grid {
    grid-template-columns: 1fr;
  }

  .coupon-hero {
    flex-direction: column;
  }

  .hero-metrics {
    min-width: 0;
  }
}
</style>

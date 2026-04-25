<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { shippingApi } from '../api/services';
import { formatCurrency } from '../utils/format';

const loading = ref(false);
const refreshing = ref(false);
const deliveringOrderId = ref('');
const orders = ref([]);
const deliveryDrafts = reactive({});

const pendingCount = computed(() => orders.value.length);

function formatTime(value) {
  if (!value) {
    return '刚刚完成支付';
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

function ensureDraft(order) {
  const key = String(order.orderId);
  if (!deliveryDrafts[key]) {
    deliveryDrafts[key] = {
      logisticsNo: '',
      expressCompany: 'SUDDENFIX-EXPRESS',
      remark: '商家已确认发货，包裹正在交接承运商。'
    };
  }
  return deliveryDrafts[key];
}

async function loadPendingOrders({ silent = false } = {}) {
  if (silent) {
    refreshing.value = true;
  } else {
    loading.value = true;
  }

  try {
    const result = await shippingApi.pending(50);
    orders.value = Array.isArray(result) ? result : [];
    orders.value.forEach((item) => ensureDraft(item));
  } catch (error) {
    ElMessage.error(error.message || '待发货订单加载失败');
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

async function submitDelivery(order) {
  const draft = ensureDraft(order);
  if (!draft.logisticsNo.trim()) {
    ElMessage.warning('请先填写物流单号');
    return;
  }

  deliveringOrderId.value = String(order.orderId);
  try {
    await shippingApi.deliver({
      orderId: order.orderId,
      logisticsNo: draft.logisticsNo.trim(),
      expressCompany: draft.expressCompany?.trim() || 'SUDDENFIX-EXPRESS',
      remark: draft.remark?.trim() || '商家已发货'
    });
    ElMessage.success(`订单 ${order.orderNo || order.orderId} 已发货`);
    await loadPendingOrders({ silent: true });
  } catch (error) {
    ElMessage.error(error.message || '发货失败，请稍后重试');
  } finally {
    deliveringOrderId.value = '';
  }
}
</script>

<template>
  <section class="ops-shell">
    <header class="hero-panel">
      <div class="hero-copy">
        <span class="eyebrow">Merchant Dispatch Board</span>
        <h1>已支付订单在这里集中待发，填好物流单号就能一键扭转到已发货。</h1>
        <p>
          这个界面走的是偏工业调度的视觉方向：重点信息先给到，操作动作贴着订单本身，适合商家连续处理一批秒杀订单。
        </p>
      </div>

      <div class="hero-metrics">
        <div class="metric-card">
          <span>待发货订单</span>
          <strong>{{ pendingCount }}</strong>
        </div>
        <div class="metric-card">
          <span>当前模式</span>
          <strong>支付后即时建单</strong>
        </div>
        <button class="refresh-button" :disabled="refreshing" @click="loadPendingOrders({ silent: true })">
          {{ refreshing ? '刷新中...' : '刷新待发货队列' }}
        </button>
      </div>
    </header>

    <div v-if="loading" class="loading-state">正在同步待发货订单...</div>

    <div v-else-if="!orders.length" class="empty-panel">
      <span>队列已清空</span>
      <p>当前没有待发货订单，新的已支付订单到达后会出现在这里。</p>
    </div>

    <div v-else class="dispatch-grid">
      <article v-for="order in orders" :key="order.orderId" class="dispatch-card">
        <div class="card-topline">
          <div>
            <span class="card-label">订单编号</span>
            <strong>{{ order.orderNo || order.orderId }}</strong>
          </div>
          <div class="amount-block">
            <span class="card-label">应收金额</span>
            <strong>{{ formatCurrency(order.payAmount) }}</strong>
          </div>
        </div>

        <div class="card-grid">
          <div class="info-block">
            <span class="card-label">收货人</span>
            <strong>{{ order.receiverName || '未填写' }}</strong>
            <small>{{ order.receiverPhone || '暂无手机号' }}</small>
          </div>
          <div class="info-block">
            <span class="card-label">支付完成</span>
            <strong>{{ formatTime(order.createTime) }}</strong>
            <small>订单 ID：{{ order.orderId }}</small>
          </div>
          <div class="address-block">
            <span class="card-label">配送地址</span>
            <strong>{{ order.receiverAddress || '暂无地址信息' }}</strong>
          </div>
        </div>

        <div class="form-grid">
          <label class="field">
            <span>物流单号</span>
            <input v-model="ensureDraft(order).logisticsNo" type="text" placeholder="例如：SF1234567890" />
          </label>
          <label class="field">
            <span>承运公司</span>
            <input
              v-model="ensureDraft(order).expressCompany"
              type="text"
              placeholder="例如：顺丰 / 京东物流"
            />
          </label>
        </div>

        <label class="field field-full">
          <span>商家备注</span>
          <textarea
            v-model="ensureDraft(order).remark"
            rows="3"
            placeholder="可以告诉用户预计揽收时间或发货说明"
          />
        </label>

        <div class="card-actions">
          <button
            class="deliver-button"
            :disabled="deliveringOrderId === String(order.orderId)"
            @click="submitDelivery(order)"
          >
            {{ deliveringOrderId === String(order.orderId) ? '正在提交发货...' : '确认发货并回写状态' }}
          </button>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.ops-shell {
  --bg: #f4efe8;
  --panel: rgba(255, 255, 255, 0.88);
  --ink: #1f242b;
  --muted: #66707b;
  --accent: #d96a28;
  --accent-deep: #8e3f16;
  --border: rgba(31, 36, 43, 0.08);
  min-height: 100vh;
  padding: 28px;
  background:
    linear-gradient(135deg, rgba(217, 106, 40, 0.12), transparent 34%),
    radial-gradient(circle at top right, rgba(47, 64, 87, 0.12), transparent 28%),
    var(--bg);
  color: var(--ink);
}

.hero-panel,
.dispatch-card,
.empty-panel {
  background: var(--panel);
  border: 1px solid var(--border);
  backdrop-filter: blur(10px);
  box-shadow: 0 24px 60px rgba(20, 24, 30, 0.08);
}

.hero-panel {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(280px, 0.8fr);
  gap: 20px;
  padding: 28px;
  border-radius: 28px;
}

.eyebrow,
.card-label {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--accent-deep);
}

.hero-copy h1 {
  margin: 0 0 14px;
  font-size: clamp(30px, 4vw, 48px);
  line-height: 1.02;
}

.hero-copy p,
.empty-panel p,
.info-block small {
  margin: 0;
  color: var(--muted);
  line-height: 1.7;
}

.hero-metrics {
  display: grid;
  gap: 14px;
  align-content: start;
}

.metric-card {
  padding: 18px;
  border-radius: 22px;
  background: linear-gradient(180deg, #fffaf6 0%, #f7efe6 100%);
}

.metric-card span {
  display: block;
  margin-bottom: 8px;
  color: var(--muted);
}

.metric-card strong {
  font-size: 28px;
}

.refresh-button,
.deliver-button {
  border: none;
  border-radius: 18px;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, opacity 0.2s ease;
}

.refresh-button {
  padding: 16px 18px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #202730 0%, #3d4d61 100%);
}

.deliver-button {
  width: 100%;
  padding: 18px 22px;
  font-size: 16px;
  font-weight: 800;
  color: #fff;
  background: linear-gradient(135deg, #eb7a2f 0%, #c54c18 100%);
  box-shadow: 0 18px 30px rgba(197, 76, 24, 0.22);
}

.refresh-button:hover,
.deliver-button:hover {
  transform: translateY(-1px);
}

.refresh-button:disabled,
.deliver-button:disabled {
  opacity: 0.72;
  cursor: wait;
  transform: none;
}

.loading-state,
.empty-panel {
  margin-top: 24px;
  padding: 28px;
  border-radius: 28px;
  text-align: center;
}

.empty-panel span {
  display: block;
  margin-bottom: 10px;
  font-size: 22px;
  font-weight: 800;
}

.dispatch-grid {
  display: grid;
  gap: 18px;
  margin-top: 24px;
}

.dispatch-card {
  padding: 24px;
  border-radius: 26px;
  animation: reveal 0.45s ease both;
}

.card-topline,
.card-grid,
.form-grid {
  display: grid;
  gap: 16px;
}

.card-topline {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
}

.card-topline strong,
.info-block strong,
.address-block strong {
  font-size: 22px;
}

.amount-block {
  text-align: right;
}

.amount-block strong {
  color: var(--accent-deep);
}

.card-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 18px;
}

.info-block,
.address-block {
  padding: 18px;
  border-radius: 20px;
  background: linear-gradient(180deg, #fffaf7 0%, #f5ede6 100%);
}

.address-block {
  grid-column: span 1;
}

.form-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 18px;
}

.field {
  display: grid;
  gap: 10px;
}

.field span {
  font-size: 13px;
  font-weight: 700;
  color: var(--muted);
}

.field input,
.field textarea {
  width: 100%;
  padding: 14px 16px;
  border: 1px solid rgba(31, 36, 43, 0.12);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.94);
  font: inherit;
  color: var(--ink);
  outline: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.field input:focus,
.field textarea:focus {
  border-color: rgba(217, 106, 40, 0.48);
  box-shadow: 0 0 0 4px rgba(217, 106, 40, 0.12);
}

.field-full {
  margin-top: 18px;
}

.card-actions {
  margin-top: 22px;
}

@keyframes reveal {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 900px) {
  .hero-panel,
  .card-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .amount-block {
    text-align: left;
  }
}

@media (max-width: 640px) {
  .ops-shell {
    padding: 16px;
  }

  .hero-panel,
  .dispatch-card,
  .empty-panel {
    padding: 20px;
  }
}
</style>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { orderApi, shippingApi } from '../api/services';
import { formatCurrency, orderStatusMeta, shippingStatusMeta } from '../utils/format';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const actionLoading = ref(false);
const detail = ref(null);
const shipping = ref(null);

const order = computed(() => detail.value?.order || null);
const items = computed(() => detail.value?.items || []);

function canCancel() {
  return Number(order.value?.status) === 10;
}

function canRefund() {
  return [20, 30, 40].includes(Number(order.value?.status));
}

async function loadDetail() {
  loading.value = true;
  try {
    detail.value = await orderApi.detail(route.params.id);
    try {
      shipping.value = await shippingApi.detail(route.params.id);
    } catch {
      shipping.value = null;
    }
  } catch (error) {
    ElMessage.error(error.message || '订单详情加载失败');
  } finally {
    loading.value = false;
  }
}

async function cancelOrder() {
  if (!order.value?.orderId || actionLoading.value) {
    return;
  }
  actionLoading.value = true;
  try {
    await orderApi.cancel(order.value.orderId);
    ElMessage.success('订单已取消');
    await loadDetail();
  } catch (error) {
    ElMessage.error(error.message || '取消订单失败');
  } finally {
    actionLoading.value = false;
  }
}

async function refundOrder() {
  if (!order.value?.orderId || actionLoading.value) {
    return;
  }
  actionLoading.value = true;
  try {
    await orderApi.refund(order.value.orderId);
    ElMessage.success('退货退款申请已提交');
    await loadDetail();
  } catch (error) {
    ElMessage.error(error.message || '退货退款申请失败');
  } finally {
    actionLoading.value = false;
  }
}

onMounted(loadDetail);
</script>

<template>
  <section v-loading="loading" class="page-section detail-page">
    <div class="detail-header">
      <div>
        <span class="eyebrow">订单详情</span>
        <h2>订单 {{ order?.orderNo || order?.orderId || '--' }}</h2>
      </div>
      <div class="header-actions">
        <el-button @click="router.push('/account')">返回订单列表</el-button>
        <el-button
          v-if="canCancel()"
          type="danger"
          plain
          :loading="actionLoading"
          @click="cancelOrder"
        >
          取消订单
        </el-button>
        <el-button
          v-if="canRefund()"
          type="warning"
          plain
          :loading="actionLoading"
          @click="refundOrder"
        >
          申请退货退款
        </el-button>
      </div>
    </div>

    <div v-if="order" class="detail-grid">
      <el-card shadow="never" class="detail-card">
        <template #header>
          <div class="card-head">
            <span>基础信息</span>
            <el-tag :type="orderStatusMeta(order.status).type">
              {{ orderStatusMeta(order.status).text }}
            </el-tag>
          </div>
        </template>

        <div class="info-grid">
          <div><span>订单编号</span><strong>{{ order.orderNo || order.orderId }}</strong></div>
          <div><span>下单时间</span><strong>{{ order.createTime || '--' }}</strong></div>
          <div><span>支付金额</span><strong>{{ formatCurrency(order.payAmount) }}</strong></div>
          <div><span>支付渠道</span><strong>{{ order.payChannel === 1 ? '支付宝' : '其他' }}</strong></div>
          <div><span>收货人</span><strong>{{ order.receiverName || '--' }}</strong></div>
          <div><span>手机号</span><strong>{{ order.receiverPhone || '--' }}</strong></div>
          <div class="full"><span>收货地址</span><strong>{{ order.receiverAddress || '--' }}</strong></div>
          <div class="full"><span>备注</span><strong>{{ order.remark || '无' }}</strong></div>
        </div>
      </el-card>

      <el-card shadow="never" class="detail-card">
        <template #header>商品清单</template>
        <div class="item-stack">
          <div v-for="item in items" :key="item.itemId" class="item-row">
            <div>
              <strong>{{ item.productName }}</strong>
              <p>数量 x{{ item.quantity }}</p>
            </div>
            <div class="money-col">
              <span>小计 {{ formatCurrency(item.totalAmount) }}</span>
              <strong>{{ formatCurrency(item.realPayAmount || item.totalAmount) }}</strong>
            </div>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="detail-card">
        <template #header>物流信息</template>
        <div v-if="shipping" class="info-grid">
          <div><span>物流状态</span><strong>{{ shippingStatusMeta(shipping.shippingStatus) }}</strong></div>
          <div><span>快递公司</span><strong>{{ shipping.expressCompany || '待分配' }}</strong></div>
          <div><span>物流单号</span><strong>{{ shipping.logisticsNo || '待生成' }}</strong></div>
          <div><span>发货时间</span><strong>{{ shipping.shipTime || '待发货' }}</strong></div>
          <div><span>签收时间</span><strong>{{ shipping.signTime || '未签收' }}</strong></div>
        </div>
        <el-empty v-else description="物流信息暂未生成" />
      </el-card>
    </div>
  </section>
</template>

<style scoped>
.detail-page {
  padding: 24px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-bottom: 20px;
}

.header-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.detail-grid {
  display: grid;
  gap: 20px;
}

.detail-card {
  border-radius: 24px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.info-grid div,
.item-row {
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(248, 243, 238, 0.9);
}

.info-grid span,
.item-row p,
.money-col span {
  display: block;
  color: #856758;
  margin-bottom: 6px;
}

.info-grid strong,
.item-row strong,
.money-col strong {
  color: #2c1c14;
}

.full {
  grid-column: 1 / -1;
}

.item-stack {
  display: grid;
  gap: 12px;
}

.item-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.money-col {
  text-align: right;
}

@media (max-width: 900px) {
  .detail-header,
  .item-row {
    flex-direction: column;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .full {
    grid-column: auto;
  }
}
</style>

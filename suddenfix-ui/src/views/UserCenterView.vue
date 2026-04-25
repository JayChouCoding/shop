<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { orderApi, shippingApi, userApi } from '../api/services';
import { formatCurrency, joinAddress, orderStatusMeta, shippingStatusMeta } from '../utils/format';

const loading = ref(false);
const orders = ref([]);
const trackingMap = reactive({});
const addresses = ref([]);

async function loadOrders() {
  loading.value = true;
  try {
    orders.value = await orderApi.list();
  } catch (error) {
    ElMessage.error('订单列表加载失败，请稍后重试。');
  } finally {
    loading.value = false;
  }
}

async function loadAddresses() {
  try {
    addresses.value = await userApi.listAddresses();
  } catch (error) {
    addresses.value = [];
  }
}

async function loadTracking(orderId) {
  try {
    trackingMap[orderId] = await shippingApi.detail(orderId);
  } catch (error) {
    ElMessage.warning('物流信息暂时不可用，请稍后再试。');
  }
}

onMounted(async () => {
  await Promise.all([loadOrders(), loadAddresses()]);
});
</script>

<template>
  <section class="page-section account-grid">
    <div class="orders-panel">
      <div class="page-heading">
        <div>
          <span class="eyebrow">用户中心</span>
          <h2>我的订单与物流进度</h2>
        </div>
        <el-button @click="loadOrders">刷新订单</el-button>
      </div>

      <div v-loading="loading" class="order-stack">
        <el-empty v-if="!orders.length" description="还没有订单，去首页下第一单吧" />

        <el-card v-for="orderView in orders" :key="orderView.order.orderId" shadow="never" class="order-card">
          <div class="order-head">
            <div>
              <strong>#{{ orderView.order.orderId }}</strong>
              <p>{{ orderView.order.receiverName }} · {{ orderView.order.receiverPhone }}</p>
            </div>
            <el-tag :type="orderStatusMeta(orderView.order.status).type">
              {{ orderStatusMeta(orderView.order.status).text }}
            </el-tag>
          </div>

          <div class="order-items">
            <div v-for="item in orderView.items" :key="item.itemId" class="order-item-row">
              <span>{{ item.productName }} × {{ item.quantity }}</span>
              <strong>{{ formatCurrency(item.realPayAmount) }}</strong>
            </div>
          </div>

          <div class="summary-line">
            <span>订单金额</span>
            <strong>{{ formatCurrency(orderView.order.payAmount) }}</strong>
          </div>

          <div class="toolbar-inline order-tools">
            <el-button @click="loadTracking(orderView.order.orderId)">查看物流</el-button>
          </div>

          <el-alert
            v-if="trackingMap[orderView.order.orderId]"
            class="tracking-box"
            type="info"
            :closable="false"
            show-icon
          >
            <template #title>
              物流状态：{{ shippingStatusMeta(trackingMap[orderView.order.orderId].shippingStatus) }}
            </template>
            <div>快递公司：{{ trackingMap[orderView.order.orderId].expressCompany || '待分配' }}</div>
            <div>物流单号：{{ trackingMap[orderView.order.orderId].logisticsNo || '待生成' }}</div>
            <div>发货时间：{{ trackingMap[orderView.order.orderId].shipTime || '待发货' }}</div>
            <div>签收时间：{{ trackingMap[orderView.order.orderId].signTime || '未签收' }}</div>
          </el-alert>
        </el-card>
      </div>
    </div>

    <div class="profile-panel">
      <el-card shadow="never" class="checkout-card">
        <template #header>默认收货地址</template>
        <div v-if="addresses.length" class="address-list compact">
          <div v-for="address in addresses" :key="address.id" class="address-card static">
            <strong>{{ address.consignee }}</strong>
            <span>{{ address.phone }}</span>
            <small>{{ joinAddress(address) }}</small>
          </div>
        </div>
        <el-empty v-else description="暂未配置收货地址，可直接在结算页手动填写" />
      </el-card>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { productApi } from '../api/services';
import { formatCurrency } from '../utils/format';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const preheating = ref(false);
const preheated = ref(false);
const usingMock = ref(false);
const detail = ref(null);
const recommendations = ref([]);

const currentProduct = computed(() => detail.value?.product || detail.value);
const productPrice = computed(() => formatCurrency(currentProduct.value?.price || 0));
const recommendationList = computed(() => recommendations.value.slice(0, 3));
const detailHighlights = computed(() => {
  const product = currentProduct.value;
  if (!product) {
    return [];
  }
  return [
    getStockLabel(product),
    getDeliveryLabel(product),
    usingMock.value ? '可先体验预热与下单路径' : '支持预热后快速进入结算'
  ];
});

function getStockLabel(product) {
  const stock = Number(product.stock || 0);
  if (!stock) {
    return '库存状态实时刷新';
  }
  if (stock <= 20) {
    return '库存偏紧，建议先完成预热';
  }
  if (stock <= 80) {
    return '限量发售，建议尽快锁定';
  }
  return '库存相对充足，可放心购买';
}

function getDeliveryLabel(product) {
  const stock = Number(product.stock || 0);
  if (!stock) {
    return '下单后将尽快确认';
  }
  if (stock <= 20) {
    return '优先安排出库';
  }
  if (stock <= 80) {
    return '预计 48 小时内发货';
  }
  return '预计 24 小时内发货';
}

async function loadDetail() {
  loading.value = true;
  preheated.value = false;
  usingMock.value = false;
  try {
    const result = await productApi.detail(route.params.id);
    detail.value = result;
    recommendations.value = result?.recommendProduct || [];
  } catch (error) {
    detail.value = null;
    recommendations.value = [];
    ElMessage.error(error.message || '商品详情暂时加载失败，请稍后重试。');
  } finally {
    loading.value = false;
  }
}

function goLaunch() {
  if (!currentProduct.value?.id) {
    return;
  }
  router.push(`/products/${currentProduct.value.id}/launch`);
}

async function preheatCurrentProduct(options = {}) {
  const { redirect = true } = options;
  if (!currentProduct.value?.id || preheating.value) {
    return false;
  }
  preheating.value = true;
  try {
    if (usingMock.value) {
      preheated.value = true;
      ElMessage.success('预热已完成，现在可以直接进入抢购。');
      if (redirect) {
        goLaunch();
      }
      return true;
    }
    const stock = await productApi.preheat(currentProduct.value.id);
    preheated.value = true;
    const stockText =
      Number.isFinite(Number(stock)) && Number(stock) > 0 ? ` 当前可抢约 ${stock} 件。` : ' 现在可以直接抢购。';
    ElMessage.success(`预热完成。${stockText}`);
    if (redirect) {
      goLaunch();
    }
    return true;
  } catch (error) {
    ElMessage.error('预热失败，请稍后刷新后再试。');
    return false;
  } finally {
    preheating.value = false;
  }
}

async function rushNow() {
  await preheatCurrentProduct({ redirect: true });
}

watch(
  () => route.params.id,
  () => {
    loadDetail();
  },
  { immediate: true }
);
</script>

<template>
  <section v-loading="loading" class="page-section detail-shell">
    <template v-if="currentProduct">
      <div class="detail-layout">
        <div class="detail-media">
          <div class="detail-media-frame">
            <img :src="currentProduct.mainImage" :alt="currentProduct.name" class="detail-image" />
            <div class="floating-pill">{{ usingMock ? '体验样品' : '限时发售' }}</div>
          </div>

          <div class="detail-fact-grid">
            <div class="fact-card">
              <span>建议动作</span>
              <strong>{{ preheated ? '直接抢购' : '先完成预热' }}</strong>
            </div>
            <div class="fact-card">
              <span>到手节奏</span>
              <strong>{{ getDeliveryLabel(currentProduct) }}</strong>
            </div>
            <div class="fact-card">
              <span>当前状态</span>
              <strong>{{ getStockLabel(currentProduct) }}</strong>
            </div>
          </div>
        </div>

        <div class="detail-copy">
          <span class="eyebrow">商品详情</span>
          <h2>{{ currentProduct.name }}</h2>
          <p class="detail-desc">
            {{ currentProduct.description || '这是一件值得提前预热、避免错过购买节奏的精选商品。' }}
          </p>

          <div class="detail-meta">
            <span class="detail-chip" v-for="item in detailHighlights" :key="item">{{ item }}</span>
          </div>

          <div class="detail-price-panel">
            <div>
              <div class="detail-price">{{ productPrice }}</div>
              <p class="detail-note">完成预热后，抢购时可更快进入下单流程。</p>
            </div>
            <div class="detail-price-side">
              <span>购买建议</span>
              <strong>{{ preheated ? '现在下单更顺手' : '先预热更稳妥' }}</strong>
            </div>
          </div>

          <div class="detail-actions">
            <el-button
              type="warning"
              plain
              size="large"
              :loading="preheating"
              @click="preheatCurrentProduct"
            >
              {{ preheated ? '重新进入等待页' : '先做抢购预热' }}
            </el-button>
            <el-button type="primary" size="large" @click="rushNow">
              {{ preheated ? '进入抢购页' : '预热后抢购' }}
            </el-button>
            <el-button size="large" @click="goLaunch()">进入等待页</el-button>
          </div>

          <div class="service-strip">
            <div class="service-item">
              <strong>下单流程更短</strong>
              <span>完成预热后可快速进入结算</span>
            </div>
            <div class="service-item">
              <strong>状态更清晰</strong>
              <span>库存、发货节奏和推荐都集中展示</span>
            </div>
            <div class="service-item">
              <strong>同屏继续挑选</strong>
              <span>可直接切换到更多推荐商品</span>
            </div>
          </div>
        </div>
      </div>

      <div class="detail-lower">
        <div class="detail-section">
          <div class="section-head">
            <strong>购买提醒</strong>
            <span>先预热会更稳，尤其适合限量商品。</span>
          </div>
          <div class="detail-bullet-list">
            <div class="detail-bullet">价格已直接展示，方便快速判断是否下单。</div>
            <div class="detail-bullet">库存较少时建议先预热，再进入抢购。</div>
            <div class="detail-bullet">加入购物车后可直接在结算页继续支付。</div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-head">
            <strong>你可能还想看</strong>
            <span>同屏切换，不打断当前浏览节奏。</span>
          </div>

          <div class="recommend-grid">
            <button
              v-for="item in recommendationList"
              :key="item.id"
              class="recommend-card"
              @click="router.push(`/products/${item.id}`)"
            >
              <span>{{ item.name }}</span>
              <strong>{{ formatCurrency(item.price) }}</strong>
            </button>
          </div>

          <p v-if="!recommendationList.length" class="detail-note">
            暂时没有更多推荐，你可以先完成当前商品的预热与购买。
          </p>
        </div>
      </div>
    </template>
  </section>
</template>

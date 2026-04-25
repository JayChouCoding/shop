<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { productApi } from '../api/services';
import { mockProducts } from '../data/mock';
import { formatCurrency } from '../utils/format';

const router = useRouter();
const loading = ref(false);
const preheatingMap = reactive({});
const preheatedMap = reactive({});
const state = reactive({
  keyword: '',
  minPrice: '',
  maxPrice: '',
  sortType: 'LATEST',
  page: 1,
  size: 8,
  total: 0,
  records: [],
  usingMock: false
});

const visibleProducts = computed(() => state.records);
const preheatedCount = computed(() => Object.values(preheatedMap).filter(Boolean).length);

function isPreheated(productId) {
  return Boolean(preheatedMap[productId]);
}

function getSalesLabel(product) {
  const sales = Number(product.sales || 0);
  if (sales >= 300) {
    return `${sales}+ 人正在关注`;
  }
  if (sales >= 100) {
    return '热度持续攀升';
  }
  return '新品抢先锁定';
}

function getStockLabel(product) {
  const stock = Number(product.stock || 0);
  if (!stock) {
    return '库存动态更新中';
  }
  if (stock <= 20) {
    return '库存紧张，建议先预热';
  }
  if (stock <= 80) {
    return '限量发售，适合尽快下单';
  }
  return '现货充足，可放心抢购';
}

function getDeliveryLabel(product) {
  const stock = Number(product.stock || 0);
  if (!stock) {
    return '下单后尽快确认';
  }
  if (stock <= 20) {
    return '优先安排出库';
  }
  if (stock <= 80) {
    return '预计 48 小时内发货';
  }
  return '预计 24 小时内发货';
}

async function loadProducts(nextPage = state.page) {
  state.page = nextPage;
  loading.value = true;
  try {
    const result = await productApi.searchAdvanced({
      keyword: state.keyword,
      minPrice: state.minPrice ? Number(state.minPrice) * 100 : null,
      maxPrice: state.maxPrice ? Number(state.maxPrice) * 100 : null,
      page: state.page,
      size: state.size,
      sortType: state.sortType
    });
    state.records = result?.records || [];
    state.total = Number(result?.total || 0);
    state.usingMock = false;
  } catch (error) {
    state.records = mockProducts;
    state.total = mockProducts.length;
    state.usingMock = true;
    ElMessage.warning('商品区稍有拥挤，已先为你展示精选商品。');
  } finally {
    loading.value = false;
  }
}

function resetFilters() {
  state.keyword = '';
  state.minPrice = '';
  state.maxPrice = '';
  state.sortType = 'LATEST';
  loadProducts(1);
}

function goLaunch(product) {
  router.push(`/products/${product.id}/launch`);
}

async function preheatProduct(product, options = {}) {
  const { redirect = true } = options;
  if (preheatingMap[product.id]) {
    return false;
  }
  preheatingMap[product.id] = true;
  try {
    if (state.usingMock) {
      preheatedMap[product.id] = true;
      ElMessage.success(`已为 ${product.name} 完成预热，开抢时可直接下单。`);
      if (redirect) {
        goLaunch(product);
      }
      return true;
    }
    const stock = await productApi.preheat(product.id);
    preheatedMap[product.id] = true;
    const stockText =
      Number.isFinite(Number(stock)) && Number(stock) > 0 ? ` 当前可抢约 ${stock} 件。` : ' 现在可以直接抢购。';
    ElMessage.success(`预热完成。${stockText}`);
    if (redirect) {
      goLaunch(product);
    }
    return true;
  } catch (error) {
    ElMessage.error('预热失败，请稍后刷新后再试。');
    return false;
  } finally {
    preheatingMap[product.id] = false;
  }
}

async function rushToBuy(product) {
  await preheatProduct(product, { redirect: true });
}

onMounted(loadProducts);
</script>

<template>
  <section class="page-section catalog-page">
    <div class="launch-hero">
      <div class="launch-copy">
        <span class="eyebrow">限时精选</span>
        <h2>先预热，再出手，抢购节奏一眼就清楚。</h2>
        <p class="catalog-intro">
          这里聚合了当前可关注的商品。先完成预热，开抢时就能更快进入下单流程，避免在关键一步里反复操作。
        </p>
        <div class="launch-tags">
          <span class="soft-badge">先预热后抢购</span>
          <span class="soft-badge">价格实时展示</span>
          <span class="soft-badge">移动端同步适配</span>
        </div>
        <div class="hero-actions">
          <el-button type="primary" plain @click="router.push('/coupons')">去领券中心</el-button>
          <el-button @click="router.push('/account')">查看我的订单</el-button>
        </div>
      </div>

      <div class="launch-panel">
        <p>今日状态</p>
        <strong>{{ state.total }}</strong>
        <span>可浏览商品</span>
        <div class="launch-panel-metrics">
          <div>
            <em>{{ preheatedCount }}</em>
            <span>已预热商品</span>
          </div>
          <div>
            <em>{{ state.sortType === 'LATEST' ? '最新' : '热销' }}</em>
            <span>排序策略</span>
          </div>
        </div>
      </div>
    </div>

    <el-card shadow="never" class="search-panel premium-panel">
      <div class="search-panel-head">
        <div>
          <strong>挑选你要抢的商品</strong>
          <p>搜索、筛价、排序后可直接做预热。</p>
        </div>
      </div>
      <div class="filter-grid">
        <el-input
          v-model="state.keyword"
          placeholder="搜索商品关键字"
          clearable
          @keyup.enter="loadProducts(1)"
        />
        <el-input v-model="state.minPrice" placeholder="最低价（元）" clearable />
        <el-input v-model="state.maxPrice" placeholder="最高价（元）" clearable />
        <el-select v-model="state.sortType">
          <el-option label="最新上架" value="LATEST" />
          <el-option label="热度优先" value="SALES" />
        </el-select>
        <el-button type="primary" @click="loadProducts(1)">开始筛选</el-button>
        <el-button @click="resetFilters">恢复默认</el-button>
      </div>
    </el-card>

    <el-alert
      v-if="state.usingMock"
      class="soft-alert"
      type="info"
      show-icon
      :closable="false"
      title="当前展示的是精选样品，你依然可以完整体验预热与购买流程。"
    />

    <el-empty
      v-else-if="!loading && !visibleProducts.length"
      description="当前没有符合条件的商品，稍后刷新或调整筛选条件再试"
    />

    <div v-else v-loading="loading" class="product-grid">
      <el-card
        v-for="product in visibleProducts"
        :key="product.id"
        class="product-card product-card-premium"
        shadow="hover"
      >
        <div class="product-visual">
          <img :src="product.mainImage" :alt="product.name" class="product-image" />
          <div class="product-badge-row">
            <span class="soft-badge accent">{{ getSalesLabel(product) }}</span>
            <span v-if="isPreheated(product.id)" class="soft-badge success">预热完成</span>
          </div>
        </div>

        <div class="product-copy">
          <div class="product-title-row">
            <h3>{{ product.name }}</h3>
            <span class="price">{{ formatCurrency(product.price) }}</span>
          </div>
          <p class="product-description">{{ product.description || '为你准备的限时精选商品。' }}</p>

          <div class="product-highlights">
            <span>{{ getStockLabel(product) }}</span>
            <span>{{ getDeliveryLabel(product) }}</span>
          </div>

          <div class="product-foot">
            <el-button link type="primary" @click="router.push(`/products/${product.id}`)">
              查看详情
            </el-button>

            <div class="product-actions product-actions-strong">
              <el-button
                type="warning"
                plain
                :loading="Boolean(preheatingMap[product.id])"
                @click="preheatProduct(product)"
              >
                {{ isPreheated(product.id) ? '重新进入等待页' : '抢购预热' }}
              </el-button>
              <el-button type="primary" @click="rushToBuy(product)">
                {{ isPreheated(product.id) ? '进入抢购页' : '预热后抢购' }}
              </el-button>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <div class="result-pager" v-if="!state.usingMock && state.total > state.size">
      <el-pagination
        background
        layout="prev, pager, next"
        :current-page="state.page"
        :page-size="state.size"
        :total="state.total"
        @current-change="loadProducts"
      />
    </div>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { couponApi, productApi } from '../api/services';
import AdminShipping from './AdminShipping.vue';

const publishing = ref(false);
const preheatingCoupon = ref(false);
const uploadingImage = ref(false);

const productForm = reactive({
  name: '',
  categoryId: '',
  mainImage: '',
  price: '',
  stock: '',
  description: '',
  startTime: '',
  endTime: ''
});

const couponForm = reactive({
  name: '',
  amount: '',
  minPoint: '',
  totalStock: '',
  segmentCount: '10',
  startTime: '',
  endTime: ''
});

function toDateTimeString(value) {
  if (!value) {
    return '';
  }
  return `${value.replace('T', ' ')}:00`;
}

async function handleImageUpload(event) {
  const [file] = event?.target?.files || [];
  if (!file) {
    return;
  }
  uploadingImage.value = true;
  try {
    const result = await productApi.adminUploadImage(file);
    productForm.mainImage = result.imageUrl;
    ElMessage.success('商品图片上传成功');
  } catch (error) {
    ElMessage.error(error.message || '商品图片上传失败');
  } finally {
    uploadingImage.value = false;
    if (event?.target) {
      event.target.value = '';
    }
  }
}

async function submitProduct() {
  publishing.value = true;
  try {
    const product = await productApi.adminUpload({
      name: productForm.name,
      categoryId: Number(productForm.categoryId),
      mainImage: productForm.mainImage,
      price: Math.round(Number(productForm.price || 0) * 100),
      stock: Number(productForm.stock),
      description: productForm.description,
      status: 1,
      startTime: toDateTimeString(productForm.startTime),
      endTime: toDateTimeString(productForm.endTime)
    });
    ElMessage.success(`限时商品已发布，商品 ID：${product.id}，库存：${product.stock}`);
  } catch (error) {
    ElMessage.error(error.message || '商品发布失败');
  } finally {
    publishing.value = false;
  }
}

async function submitCoupon() {
  preheatingCoupon.value = true;
  try {
    await couponApi.adminPreheat({
      name: couponForm.name,
      amount: Number(couponForm.amount || 0),
      minPoint: Number(couponForm.minPoint || 0),
      totalStock: Number(couponForm.totalStock),
      segmentCount: Number(couponForm.segmentCount || 10),
      startTime: toDateTimeString(couponForm.startTime),
      endTime: toDateTimeString(couponForm.endTime)
    });
    ElMessage.success('优惠券已完成预热');
  } catch (error) {
    ElMessage.error(error.message || '优惠券预热失败');
  } finally {
    preheatingCoupon.value = false;
  }
}
</script>

<template>
  <section class="admin-shell">
    <header class="admin-hero">
      <div>
        <span class="hero-pill">Merchant Console</span>
        <h1>发布限时商品、预热优惠券、填报订单发货，都在同一个后台控制台完成。</h1>
        <p>视觉方向选了偏工业运营面板：信息密度更高，但关键操作永远停留在第一屏。</p>
      </div>
    </header>

    <div class="admin-grid">
      <article class="panel-card">
        <div class="panel-head">
          <strong>发布限时商品</strong>
          <span>创建后会同步预热到抢购链路</span>
        </div>
        <div class="form-grid">
          <label class="field">
            <span>商品名称</span>
            <input v-model="productForm.name" type="text" placeholder="例如：秒杀款蓝牙耳机" />
          </label>
          <label class="field">
            <span>分类 ID</span>
            <input v-model="productForm.categoryId" type="number" placeholder="例如：1001" />
          </label>
          <label class="field">
            <span>主图链接</span>
            <input v-model="productForm.mainImage" type="text" placeholder="https://..." />
          </label>
          <label class="field">
            <span>上传主图</span>
            <input type="file" accept="image/*" :disabled="uploadingImage" @change="handleImageUpload" />
          </label>
          <label class="field">
            <span>秒杀价（元）</span>
            <input v-model="productForm.price" type="number" min="0" step="0.01" placeholder="99.90" />
          </label>
          <label class="field">
            <span>库存</span>
            <input v-model="productForm.stock" type="number" min="1" placeholder="100" />
          </label>
          <label class="field">
            <span>开始时间</span>
            <input v-model="productForm.startTime" type="datetime-local" />
          </label>
          <label class="field">
            <span>结束时间</span>
            <input v-model="productForm.endTime" type="datetime-local" />
          </label>
        </div>
        <label class="field field-full">
          <span>商品描述</span>
          <textarea v-model="productForm.description" rows="4" placeholder="输入卖点和活动说明" />
        </label>
        <div v-if="productForm.mainImage" class="field field-full">
          <span>主图预览</span>
          <img :src="productForm.mainImage" alt="商品主图预览" style="width: 132px; height: 132px; border-radius: 18px; object-fit: cover;" />
        </div>
        <button class="primary-button" :disabled="publishing" @click="submitProduct">
          {{ publishing ? '发布中...' : '发布限时商品' }}
        </button>
      </article>

      <article class="panel-card">
        <div class="panel-head">
          <strong>预热优惠券</strong>
          <span>支持新建并立即预热到 Redis</span>
        </div>
        <div class="form-grid">
          <label class="field">
            <span>优惠券名称</span>
            <input v-model="couponForm.name" type="text" placeholder="例如：满199减30" />
          </label>
          <label class="field">
            <span>面额</span>
            <input v-model="couponForm.amount" type="number" min="0" step="0.01" placeholder="30" />
          </label>
          <label class="field">
            <span>使用门槛</span>
            <input v-model="couponForm.minPoint" type="number" min="0" step="0.01" placeholder="199" />
          </label>
          <label class="field">
            <span>总量</span>
            <input v-model="couponForm.totalStock" type="number" min="1" placeholder="5000" />
          </label>
          <label class="field">
            <span>分段数</span>
            <input v-model="couponForm.segmentCount" type="number" min="1" placeholder="10" />
          </label>
          <label class="field">
            <span>开始时间</span>
            <input v-model="couponForm.startTime" type="datetime-local" />
          </label>
          <label class="field">
            <span>结束时间</span>
            <input v-model="couponForm.endTime" type="datetime-local" />
          </label>
        </div>
        <button class="primary-button dark" :disabled="preheatingCoupon" @click="submitCoupon">
          {{ preheatingCoupon ? '预热中...' : '一键预热优惠券' }}
        </button>
      </article>
    </div>

    <AdminShipping />
  </section>
</template>

<style scoped>
.admin-shell {
  --bg: #f2eee8;
  --surface: rgba(255, 255, 255, 0.9);
  --ink: #20252d;
  --muted: #65707b;
  --accent: #d86a29;
  --accent-deep: #8d4117;
  min-height: calc(100vh - 75px);
  padding: 24px;
  background:
    linear-gradient(135deg, rgba(216, 106, 41, 0.12), transparent 32%),
    radial-gradient(circle at top right, rgba(39, 53, 73, 0.14), transparent 26%),
    var(--bg);
  color: var(--ink);
}

.admin-hero,
.panel-card {
  border-radius: 28px;
  background: var(--surface);
  box-shadow: 0 24px 60px rgba(20, 24, 30, 0.08);
  backdrop-filter: blur(10px);
}

.admin-hero {
  padding: 28px;
}

.hero-pill {
  display: inline-flex;
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--accent-deep);
  background: rgba(216, 106, 41, 0.1);
}

.admin-hero h1 {
  margin: 18px 0 12px;
  font-size: clamp(30px, 4vw, 48px);
  line-height: 1.04;
}

.admin-hero p,
.panel-head span {
  margin: 0;
  color: var(--muted);
  line-height: 1.7;
}

.admin-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin: 22px 0;
}

.panel-card {
  padding: 22px;
}

.panel-head {
  margin-bottom: 18px;
}

.panel-head strong {
  display: block;
  margin-bottom: 6px;
  font-size: 24px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.field {
  display: grid;
  gap: 10px;
}

.field-full {
  margin-top: 14px;
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
  border: 1px solid rgba(32, 37, 45, 0.12);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.96);
  font: inherit;
  color: var(--ink);
  outline: none;
}

.primary-button {
  width: 100%;
  margin-top: 18px;
  padding: 16px 18px;
  border: none;
  border-radius: 18px;
  font-size: 16px;
  font-weight: 800;
  color: #fff;
  background: linear-gradient(135deg, #ef7a34 0%, #c54c18 100%);
  box-shadow: 0 18px 30px rgba(197, 76, 24, 0.22);
  cursor: pointer;
}

.primary-button.dark {
  background: linear-gradient(135deg, #243040 0%, #40556d 100%);
  box-shadow: 0 18px 30px rgba(36, 48, 64, 0.18);
}

.primary-button:disabled {
  opacity: 0.72;
  cursor: wait;
  box-shadow: none;
}

@media (max-width: 980px) {
  .admin-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .admin-shell {
    padding: 16px;
  }

  .admin-hero,
  .panel-card {
    padding: 18px;
  }
}
</style>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { cartApi, couponApi, orderApi, payApi, userApi } from '../api/services';
import { fallbackImage } from '../data/mock';
import { submitAlipayHtml } from '../utils/alipay';
import { formatCurrency, joinAddress } from '../utils/format';

const router = useRouter();
const PAY_PAGE_RETRY_TIMES = 8;
const PAY_PAGE_RETRY_DELAY = 400;
const loading = ref(false);
const submitting = ref(false);
const couponLoading = ref(false);
const cart = ref({
  items: [],
  totalKinds: 0,
  selectedKinds: 0,
  totalAmount: 0,
  selectedAmount: 0
});
const addresses = ref([]);
const userCoupons = ref([]);
const selectedCouponToken = ref('');

const checkoutForm = reactive({
  receiverName: '',
  receiverPhone: '',
  receiverAddress: '',
  payChannel: 1,
  remark: ''
});

const selectedItems = computed(() =>
  (cart.value.items || []).filter((item) => item.selected && item.available)
);

const freightAmount = computed(() => (selectedItems.value.length ? 1200 : 0));
const selectedCoupon = computed(() =>
  userCoupons.value.find((item) => item.couponToken === selectedCouponToken.value) || null
);

function toMinorUnits(value) {
  const amount = Number(value || 0);
  return Number.isFinite(amount) ? Math.round(amount * 100) : 0;
}

const selectedCouponThreshold = computed(() => toMinorUnits(selectedCoupon.value?.minPoint));
const selectedCouponDiscount = computed(() => {
  if (!selectedCoupon.value) {
    return 0;
  }
  if (cart.value.selectedAmount < selectedCouponThreshold.value) {
    return 0;
  }
  return toMinorUnits(selectedCoupon.value.amount);
});
const payableAmount = computed(() =>
  Math.max(0, cart.value.selectedAmount + freightAmount.value - selectedCouponDiscount.value)
);

async function loadCart() {
  loading.value = true;
  try {
    cart.value = await cartApi.detail();
  } catch (error) {
    ElMessage.error('购物车加载失败，请稍后刷新重试。');
  } finally {
    loading.value = false;
  }
}

async function loadAddresses() {
  try {
    addresses.value = await userApi.listAddresses();
    const defaultAddress =
      addresses.value.find((item) => item.isDefault === 1) || addresses.value[0];
    if (defaultAddress) {
      applyAddress(defaultAddress);
    }
  } catch (error) {
    addresses.value = [];
  }
}

function applyAddress(address) {
  checkoutForm.receiverName = address.consignee || '';
  checkoutForm.receiverPhone = address.phone || '';
  checkoutForm.receiverAddress = joinAddress(address);
}

async function changeQuantity(item, quantity) {
  try {
    await cartApi.updateItem({
      productId: item.productId,
      quantity
    });
    await loadCart();
  } catch (error) {
    ElMessage.error('数量更新失败，请稍后再试。');
  }
}

async function toggleSelected(item) {
  try {
    await cartApi.switchItemSelected({
      productId: item.productId,
      selected: item.selected
    });
    await loadCart();
  } catch (error) {
    ElMessage.error('选择状态更新失败，请稍后重试。');
  }
}

async function toggleAll(value) {
  try {
    await cartApi.switchAllSelected(value);
    await loadCart();
  } catch (error) {
    ElMessage.error('全选状态更新失败，请稍后重试。');
  }
}

async function clearSelected() {
  if (!selectedItems.value.length) {
    ElMessage.warning('当前没有已选商品');
    return;
  }
  try {
    await cartApi.clearChecked(selectedItems.value.map((item) => item.productId));
    await loadCart();
    ElMessage.success('已清空已选商品');
  } catch (error) {
    ElMessage.error('清空失败，请稍后重试。');
  }
}

async function loadUserCoupons() {
  couponLoading.value = true;
  try {
    userCoupons.value = (await couponApi.my()) || [];
    if (selectedCouponToken.value) {
      const stillExists = userCoupons.value.some((item) => item.couponToken === selectedCouponToken.value);
      if (!stillExists) {
        selectedCouponToken.value = '';
      }
    }
  } catch (error) {
    userCoupons.value = [];
  } finally {
    couponLoading.value = false;
  }
}

function sleep(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

async function waitForAlipayForm(orderId) {
  let lastError = null;
  for (let attempt = 1; attempt <= PAY_PAGE_RETRY_TIMES; attempt += 1) {
    try {
      return await payApi.createPage(orderId);
    } catch (error) {
      lastError = error;
      if (attempt < PAY_PAGE_RETRY_TIMES) {
        await sleep(PAY_PAGE_RETRY_DELAY);
      }
    }
  }
  throw lastError || new Error('支付单仍在生成中');
}

async function submitOrder() {
  if (!selectedItems.value.length) {
    ElMessage.warning('请选择至少一件商品');
    return;
  }
  if (!checkoutForm.receiverName || !checkoutForm.receiverPhone || !checkoutForm.receiverAddress) {
    ElMessage.warning('请先填写收货信息');
    return;
  }
  if (selectedCoupon.value && selectedCouponDiscount.value <= 0) {
    ElMessage.warning('当前已选优惠券还未达到使用门槛，请先取消或补充商品后再提交。');
    return;
  }

  submitting.value = true;
  let handedOff = false;
  try {
    const couponPayload = selectedCoupon.value
      ? {
          discountAmount: selectedCouponDiscount.value,
          couponId: selectedCoupon.value.couponId,
          couponSegment: selectedCoupon.value.segment,
          couponToken: selectedCoupon.value.couponToken
        }
      : {
          discountAmount: 0,
          couponId: null,
          couponSegment: null,
          couponToken: ''
        };
    const payload = {
      idempotentKey:
        globalThis.crypto?.randomUUID?.() ||
        `${Date.now()}_${Math.random().toString(36).substring(2)}`,
      products: Object.fromEntries(
        selectedItems.value.map((item) => [item.productId, [item.quantity, item.price]])
      ),
      productNames: Object.fromEntries(
        selectedItems.value.map((item) => [item.productId, item.productName])
      ),
      freight: freightAmount.value,
      discountAmount: couponPayload.discountAmount,
      receiverName: checkoutForm.receiverName,
      receiverPhone: checkoutForm.receiverPhone,
      receiverAddress: checkoutForm.receiverAddress,
      remark: checkoutForm.remark,
      payChannel: checkoutForm.payChannel,
      couponId: couponPayload.couponId,
      couponSegment: couponPayload.couponSegment,
      couponToken: couponPayload.couponToken
    };
    const orderId = await orderApi.create(payload);
    ElMessage.success('订单创建成功，正在前往支付宝...');

    cartApi.clearChecked(selectedItems.value.map((item) => item.productId)).catch(() => {});
    if (selectedCoupon.value) {
      loadUserCoupons().catch(() => {});
      selectedCouponToken.value = '';
    }

    try {
      const alipayFormHtml = await waitForAlipayForm(orderId);
      handedOff = true;
      submitAlipayHtml(alipayFormHtml);
      return;
    } catch (payError) {
      handedOff = true;
      ElMessage.warning(payError.message || '支付单仍在生成中，已切换到处理中页面继续拉起支付。');
      router.push(`/orders/${orderId}/processing`);
      return;
    }
  } catch (error) {
    ElMessage.error(error.message || '订单提交失败，请稍后再试。');
  } finally {
    if (!handedOff) {
      submitting.value = false;
    }
  }
}

onMounted(async () => {
  await Promise.all([loadCart(), loadAddresses(), loadUserCoupons()]);
});
</script>

<template>
  <section class="page-section checkout-grid">
    <div class="cart-panel">
      <div class="page-heading">
        <div>
          <span class="eyebrow">购物车 / 收银台</span>
          <h2>确认商品信息，提交后优先直达支付宝，只有必要时才进入处理中页</h2>
        </div>
        <div class="toolbar-inline">
          <el-switch
            :model-value="cart.selectedKinds === cart.totalKinds && cart.totalKinds > 0"
            inline-prompt
            active-text="全选"
            inactive-text="全选"
            @change="toggleAll"
          />
          <el-button link type="danger" @click="clearSelected">清空已选</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="cart.items" empty-text="购物车还是空的，先去挑件商品吧">
        <el-table-column label="选择" width="82">
          <template #default="{ row }">
            <el-checkbox v-model="row.selected" :disabled="!row.available" @change="toggleSelected(row)" />
          </template>
        </el-table-column>
        <el-table-column label="商品">
          <template #default="{ row }">
            <div class="cart-product">
              <img :src="row.mainImage || fallbackImage" class="cart-thumb" />
              <div>
                <strong>{{ row.productName }}</strong>
                <p>{{ row.available ? '库存可用' : row.unavailableReason }}</p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="120">
          <template #default="{ row }">{{ formatCurrency(row.price) }}</template>
        </el-table-column>
        <el-table-column label="数量" width="160">
          <template #default="{ row }">
            <el-input-number
              :model-value="row.quantity"
              :min="0"
              :max="99"
              @change="(value) => changeQuantity(row, value)"
            />
          </template>
        </el-table-column>
        <el-table-column label="小计" width="120">
          <template #default="{ row }">{{ formatCurrency(row.subtotalAmount) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <div class="checkout-panel">
      <el-card shadow="never" class="checkout-card">
        <template #header>收货信息</template>
        <div class="address-list" v-if="addresses.length">
          <button
            v-for="address in addresses"
            :key="address.id"
            class="address-card"
            @click="applyAddress(address)"
          >
            <strong>{{ address.consignee }}</strong>
            <span>{{ address.phone }}</span>
            <small>{{ joinAddress(address) }}</small>
          </button>
        </div>

        <el-form label-position="top">
          <el-form-item label="收货人">
            <el-input v-model="checkoutForm.receiverName" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="checkoutForm.receiverPhone" />
          </el-form-item>
          <el-form-item label="收货地址">
            <el-input v-model="checkoutForm.receiverAddress" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="checkoutForm.remark" placeholder="例如：工作日白天可签收" />
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never" class="checkout-card">
        <template #header>优惠券</template>
        <div v-loading="couponLoading" class="coupon-picker">
          <el-empty v-if="!userCoupons.length" description="暂无可用优惠券，可去领券中心领取" />

          <label
            v-for="coupon in userCoupons"
            :key="coupon.couponToken"
            class="coupon-option"
            :class="{ active: selectedCouponToken === coupon.couponToken }"
          >
            <input v-model="selectedCouponToken" type="radio" :value="coupon.couponToken" />
            <div>
              <strong>{{ coupon.name }}</strong>
              <p>
                立减 {{ formatCurrency(toMinorUnits(coupon.amount)) }}
                <span>· {{ Number(coupon.minPoint || 0) > 0 ? `满 ${formatCurrency(toMinorUnits(coupon.minPoint))} 可用` : '无门槛' }}</span>
              </p>
            </div>
          </label>

          <el-button v-if="selectedCouponToken" link type="warning" @click="selectedCouponToken = ''">
            不使用优惠券
          </el-button>
        </div>
      </el-card>

      <el-card shadow="never" class="checkout-card">
        <template #header>结算摘要</template>
        <div class="summary-line">
          <span>已选商品</span>
          <strong>{{ cart.selectedKinds }} 种</strong>
        </div>
        <div class="summary-line">
          <span>商品金额</span>
          <strong>{{ formatCurrency(cart.selectedAmount) }}</strong>
        </div>
        <div class="summary-line">
          <span>运费</span>
          <strong>{{ formatCurrency(freightAmount) }}</strong>
        </div>
        <div class="summary-line">
          <span>优惠券抵扣</span>
          <strong>{{ formatCurrency(selectedCouponDiscount) }}</strong>
        </div>
        <div class="summary-total">
          <span>应付合计</span>
          <strong>{{ formatCurrency(payableAmount) }}</strong>
        </div>

        <el-alert
          v-if="selectedCoupon && selectedCouponDiscount <= 0"
          type="warning"
          :closable="false"
          show-icon
          title="已选优惠券暂未满足使用门槛，补充商品或取消该券后再提交。"
        />

        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="提交后会优先直接拉起支付宝；只有支付单还没准备好时，才会自动切到处理中页继续推进。"
        />

        <el-button type="primary" class="full-width" :loading="submitting" @click="submitOrder">
          提交订单并前往支付宝
        </el-button>
      </el-card>
    </div>
  </section>
</template>

<style scoped>
.coupon-picker {
  display: grid;
  gap: 12px;
}

.coupon-option {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(226, 120, 50, 0.18);
  background: rgba(255, 247, 239, 0.84);
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.coupon-option.active {
  border-color: rgba(218, 97, 30, 0.6);
  box-shadow: 0 16px 32px rgba(208, 101, 39, 0.14);
  transform: translateY(-1px);
}

.coupon-option input {
  margin-top: 5px;
}

.coupon-option strong {
  color: #412113;
}

.coupon-option p {
  margin: 6px 0 0;
  color: #8b624b;
}
</style>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { cartApi, orderApi, userApi } from '../api/services';
import { fallbackImage } from '../data/mock';
import { formatCurrency, joinAddress } from '../utils/format';

const router = useRouter();
const loading = ref(false);
const submitting = ref(false);
const cart = ref({
  items: [],
  totalKinds: 0,
  selectedKinds: 0,
  totalAmount: 0,
  selectedAmount: 0
});
const addresses = ref([]);

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
const payableAmount = computed(() => cart.value.selectedAmount + freightAmount.value);

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

async function submitOrder() {
  if (!selectedItems.value.length) {
    ElMessage.warning('请选择至少一件商品');
    return;
  }
  if (!checkoutForm.receiverName || !checkoutForm.receiverPhone || !checkoutForm.receiverAddress) {
    ElMessage.warning('请先填写收货信息');
    return;
  }

  submitting.value = true;
  try {
    const payload = {
      idempotentKey: globalThis.crypto?.randomUUID?.() || `${Date.now()}`,
      products: Object.fromEntries(
        selectedItems.value.map((item) => [item.productId, [item.quantity, item.price]])
      ),
      productNames: Object.fromEntries(
        selectedItems.value.map((item) => [item.productId, item.productName])
      ),
      freight: freightAmount.value,
      discountAmount: 0,
      receiverName: checkoutForm.receiverName,
      receiverPhone: checkoutForm.receiverPhone,
      receiverAddress: checkoutForm.receiverAddress,
      remark: checkoutForm.remark,
      payChannel: checkoutForm.payChannel
    };
    const orderId = await orderApi.create(payload);
    await cartApi.clearChecked(selectedItems.value.map((item) => item.productId));
    await loadCart();
    ElMessage.success('订单已提交，正在为你锁定支付通道。');
    router.push(`/orders/${orderId}/processing`);
  } catch (error) {
    ElMessage.error(error.message || '订单提交失败，请稍后再试。');
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadCart(), loadAddresses()]);
});
</script>

<template>
  <section class="page-section checkout-grid">
    <div class="cart-panel">
      <div class="page-heading">
        <div>
          <span class="eyebrow">购物车 / 收银台</span>
          <h2>确认商品信息，提交后进入抢购通道</h2>
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
        <div class="summary-total">
          <span>应付合计</span>
          <strong>{{ formatCurrency(payableAmount) }}</strong>
        </div>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="提交订单后会先进入抢购处理中页面，待订单状态进入待支付后再跳转支付。"
        />

        <el-button type="primary" class="full-width" :loading="submitting" @click="submitOrder">
          提交订单并进入抢购
        </el-button>
      </el-card>
    </div>
  </section>
</template>

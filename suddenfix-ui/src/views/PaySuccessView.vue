<script setup>
import { computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { hasSession } from '../stores/session';

const route = useRoute();
const router = useRouter();

const orderId = computed(() => String(route.query.orderId || '').trim());
const targetPath = computed(() => (orderId.value ? `/account/${orderId.value}` : '/account'));

onMounted(() => {
  if (!hasSession()) {
    router.replace({
      path: '/auth',
      query: { redirect: targetPath.value }
    });
    return;
  }
  router.replace(targetPath.value);
});
</script>

<template>
  <section class="pay-success-shell">
    <div class="pay-success-card">
      <span class="eyebrow">Payment Return</span>
      <h1>支付结果已返回，正在打开订单详情</h1>
      <p>如果页面没有自动跳转，你也可以回到订单列表继续查看。</p>
    </div>
  </section>
</template>

<style scoped>
.pay-success-shell {
  min-height: calc(100vh - 75px);
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at top left, rgba(255, 183, 131, 0.28), transparent 26%),
    linear-gradient(180deg, #fffaf4 0%, #fff2e8 100%);
}

.pay-success-card {
  width: min(600px, 100%);
  padding: 36px;
  border-radius: 28px;
  text-align: center;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 24px 60px rgba(154, 76, 26, 0.14);
}

.pay-success-card h1 {
  margin: 16px 0 12px;
  color: #321710;
}

.pay-success-card p {
  margin: 0;
  color: #7b5948;
  line-height: 1.7;
}
</style>

<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { authApi } from './api/services';
import { clearSession, hasSession, isMerchant, routeForRole, sessionState } from './stores/session';

const route = useRoute();
const router = useRouter();

const isAuthed = computed(() => hasSession());
const navItems = computed(() => {
  if (!isAuthed.value) {
    return [{ label: '首页', to: '/home' }];
  }
  if (isMerchant()) {
    return [{ label: '商家控制台', to: '/admin' }];
  }
  return [
    { label: '商品会场', to: '/home' },
    { label: '购物车', to: '/cart' },
    { label: '我的订单', to: '/account' }
  ];
});

async function handleLogout() {
  try {
    if (isAuthed.value) {
      await authApi.logout();
    }
  } catch (error) {
    ElMessage.warning('登录状态已刷新，如需继续请重新登录');
  } finally {
    clearSession();
    ElMessage.success('已退出登录');
    router.push('/auth');
  }
}
</script>

<template>
  <div class="app-shell">
    <header class="app-topbar">
      <div class="brand-block" @click="router.push(routeForRole())">
        <span class="brand-badge">SF</span>
        <div>
          <div class="brand-title">SuddenFix</div>
          <div class="brand-subtitle">{{ isMerchant() ? '商家运营控制台' : '高并发秒杀与连贯支付体验' }}</div>
        </div>
      </div>

      <nav class="top-nav">
        <button
          v-for="item in navItems"
          :key="item.to"
          class="nav-link"
          :class="{ active: route.path.startsWith(item.to) }"
          @click="router.push(item.to)"
        >
          {{ item.label }}
        </button>
      </nav>

      <div class="top-actions">
        <el-tag v-if="isAuthed" class="account-pill" effect="dark" :type="isMerchant() ? 'warning' : 'success'">
          {{ sessionState.account || '已登录用户' }} · {{ isMerchant() ? '商家' : '买家' }}
        </el-tag>
        <el-button v-if="isAuthed" plain @click="handleLogout">退出</el-button>
        <el-button v-else type="primary" @click="router.push('/auth')">登录 / 注册</el-button>
      </div>
    </header>

    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(255, 198, 152, 0.26), transparent 22%),
    linear-gradient(180deg, #fffaf5 0%, #fff5eb 100%);
}

.app-topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 18px;
  padding: 16px 24px;
  backdrop-filter: blur(12px);
  background: rgba(255, 250, 245, 0.82);
  border-bottom: 1px solid rgba(79, 40, 23, 0.08);
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.brand-badge {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  font-weight: 800;
  color: #fff;
  background: linear-gradient(135deg, #ff7c45 0%, #e34118 100%);
}

.brand-title {
  font-size: 18px;
  font-weight: 800;
  color: #34160d;
}

.brand-subtitle {
  font-size: 12px;
  color: #8c5a43;
}

.top-nav {
  display: flex;
  justify-content: center;
  gap: 10px;
}

.nav-link {
  padding: 10px 14px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: #6b4d3f;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.nav-link.active,
.nav-link:hover {
  color: #ae431c;
  background: rgba(239, 98, 48, 0.1);
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.account-pill {
  border-radius: 999px;
}

.app-main {
  min-height: calc(100vh - 75px);
}

@media (max-width: 860px) {
  .app-topbar {
    grid-template-columns: 1fr;
  }

  .top-nav,
  .top-actions {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}
</style>

<script setup>
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { authApi } from '../api/services';
import { routeForRole, setSession } from '../stores/session';

const route = useRoute();
const router = useRouter();
const activeTab = ref('login');
const loading = ref(false);

const loginForm = reactive({
  account: '',
  password: ''
});

const registerForm = reactive({
  username: '',
  password: '',
  email: '',
  phone: '',
  nickname: '',
  role: 0
});

async function handleLogin() {
  loading.value = true;
  try {
    const token = await authApi.login(loginForm);
    setSession(token, loginForm.account);
    ElMessage.success('登录成功');
    router.push(route.query.redirect || routeForRole());
  } catch (error) {
    ElMessage.error(error.message || '登录失败，请检查账号信息后重试');
  } finally {
    loading.value = false;
  }
}

async function handleRegister() {
  loading.value = true;
  try {
    await authApi.register(registerForm);
    ElMessage.success(registerForm.role === 1 ? '商家账号创建成功，请登录进入控制台' : '账号创建成功，请登录开始选购');
    activeTab.value = 'login';
    loginForm.account = registerForm.username;
    loginForm.password = registerForm.password;
  } catch (error) {
    ElMessage.error(error.message || '注册失败，请检查填写信息后重试');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="auth-shell">
    <div class="hero-copy">
      <span class="eyebrow">SuddenFix Identity</span>
      <h1>买家进入商城，商家进入控制台，从登录开始就分成两条清晰路径。</h1>
      <p>
        这页走的是克制的电商入口风格：大标题给方向感，表单只保留必要动作，账号类型选择放在注册环节，避免登录后才让人迷路。
      </p>
      <div class="hero-notes">
        <div class="note-card">
          <span>普通买家</span>
          <strong>进入 /home</strong>
          <small>浏览商品、参与抢购、查看订单与物流</small>
        </div>
        <div class="note-card merchant">
          <span>入驻商家</span>
          <strong>进入 /admin</strong>
          <small>发布限时商品、预热优惠券、填报订单发货</small>
        </div>
      </div>
    </div>

    <div class="auth-panel">
      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form label-position="top" @submit.prevent="handleLogin">
            <el-form-item label="账号">
              <el-input v-model="loginForm.account" placeholder="用户名 / 手机号 / 邮箱" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="loginForm.password" show-password placeholder="请输入密码" />
            </el-form-item>
            <button class="primary-button" type="button" :disabled="loading" @click="handleLogin">
              {{ loading ? '登录中...' : '立即登录' }}
            </button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form label-position="top" @submit.prevent="handleRegister">
            <el-form-item label="账号类型">
              <el-radio-group v-model="registerForm.role" class="role-group">
                <el-radio-button :label="0">普通买家</el-radio-button>
                <el-radio-button :label="1">入驻商家</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="用户名">
              <el-input v-model="registerForm.username" placeholder="请输入用户名" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="registerForm.password" show-password placeholder="请输入密码" />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="registerForm.nickname" placeholder="页面展示名称，可选" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="registerForm.email" placeholder="可选" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="registerForm.phone" placeholder="可选" />
            </el-form-item>
            <button class="primary-button" type="button" :disabled="loading" @click="handleRegister">
              {{ loading ? '创建中...' : '创建账号' }}
            </button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </section>
</template>

<style scoped>
.auth-shell {
  --bg: #fff7ef;
  --surface: rgba(255, 255, 255, 0.9);
  --ink: #2f1710;
  --muted: #6f5347;
  --accent: #eb6531;
  --accent-deep: #b5461b;
  min-height: calc(100vh - 75px);
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(380px, 0.85fr);
  gap: 24px;
  padding: 28px;
  background:
    radial-gradient(circle at top left, rgba(255, 184, 130, 0.28), transparent 32%),
    linear-gradient(180deg, #fffaf5 0%, var(--bg) 100%);
}

.hero-copy,
.auth-panel {
  border-radius: 30px;
  background: var(--surface);
  box-shadow: 0 24px 60px rgba(147, 72, 27, 0.12);
  backdrop-filter: blur(12px);
}

.hero-copy {
  display: grid;
  gap: 20px;
  align-content: center;
  padding: 34px;
}

.eyebrow {
  display: inline-flex;
  width: fit-content;
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--accent-deep);
  background: rgba(235, 101, 49, 0.1);
}

.hero-copy h1 {
  margin: 0;
  font-size: clamp(34px, 4vw, 56px);
  line-height: 1.02;
  color: var(--ink);
}

.hero-copy p,
.note-card small {
  margin: 0;
  color: var(--muted);
  line-height: 1.75;
}

.hero-notes {
  display: grid;
  gap: 14px;
}

.note-card {
  padding: 18px 20px;
  border-radius: 22px;
  background: linear-gradient(180deg, #fff8f3 0%, #f7ede5 100%);
}

.note-card.merchant {
  background: linear-gradient(180deg, #fff2e8 0%, #ffe2d1 100%);
}

.note-card span,
.note-card strong,
.note-card small {
  display: block;
}

.note-card span {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--muted);
}

.note-card strong {
  margin-bottom: 6px;
  font-size: 24px;
  color: var(--ink);
}

.auth-panel {
  padding: 28px;
}

.role-group {
  display: flex;
  width: 100%;
}

.primary-button {
  width: 100%;
  padding: 16px 18px;
  border: none;
  border-radius: 18px;
  font-size: 16px;
  font-weight: 800;
  color: #fff;
  background: linear-gradient(135deg, #ff7e46 0%, #e34218 100%);
  box-shadow: 0 18px 36px rgba(227, 66, 24, 0.24);
  cursor: pointer;
}

.primary-button:disabled {
  opacity: 0.75;
  cursor: wait;
  box-shadow: none;
}

@media (max-width: 960px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .auth-shell {
    padding: 16px;
  }

  .hero-copy,
  .auth-panel {
    padding: 20px;
  }
}
</style>

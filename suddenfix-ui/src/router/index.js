import { createRouter, createWebHistory } from 'vue-router';
import { hasSession, routeForRole, sessionState } from '../stores/session';
import AuthView from '../views/AuthView.vue';
import ProductListView from '../views/ProductListView.vue';
import ProductDetailView from '../views/ProductDetailView.vue';
import ProductLaunchView from '../views/ProductLaunchView.vue';
import CartCheckoutView from '../views/CartCheckoutView.vue';
import OrderProcessingView from '../views/OrderProcessingView.vue';
import OrderListView from '../views/OrderList.vue';
import AdminDashboardView from '../views/AdminDashboardView.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: () => (hasSession() ? routeForRole() : '/home')
    },
    {
      path: '/auth',
      name: 'auth',
      component: AuthView
    },
    {
      path: '/home',
      alias: '/products',
      name: 'home',
      component: ProductListView
    },
    {
      path: '/home/:id',
      alias: '/products/:id',
      name: 'product-detail',
      component: ProductDetailView
    },
    {
      path: '/home/:id/launch',
      alias: '/products/:id/launch',
      name: 'product-launch',
      component: ProductLaunchView,
      meta: { requiresAuth: true, requiresRole: 0 }
    },
    {
      path: '/cart',
      name: 'cart',
      component: CartCheckoutView,
      meta: { requiresAuth: true, requiresRole: 0 }
    },
    {
      path: '/orders/:id/processing',
      name: 'order-processing',
      component: OrderProcessingView,
      meta: { requiresAuth: true, requiresRole: 0 }
    },
    {
      path: '/account',
      name: 'account',
      component: OrderListView,
      meta: { requiresAuth: true, requiresRole: 0 }
    },
    {
      path: '/admin',
      name: 'admin',
      component: AdminDashboardView,
      meta: { requiresAuth: true, requiresRole: 1 }
    }
  ],
  scrollBehavior() {
    return { top: 0 };
  }
});

router.beforeEach((to) => {
  if (to.path === '/auth' && hasSession()) {
    return routeForRole();
  }

  if (to.meta.requiresAuth && !hasSession()) {
    return {
      path: '/auth',
      query: { redirect: to.fullPath }
    };
  }

  if (typeof to.meta.requiresRole !== 'undefined' && hasSession()) {
    if (Number(sessionState.role) !== Number(to.meta.requiresRole)) {
      return routeForRole();
    }
  }

  return true;
});

export default router;

import client from './client';

export const authApi = {
  register(payload) {
    return client.post('/user/register', payload);
  },
  login(payload) {
    return client.post('/user/login', payload);
  },
  logout() {
    return client.post('/user/logout');
  },
  deregister() {
    return client.post('/user/deregister');
  }
};

export const productApi = {
  list(params) {
    return client.get('/product/list', { params });
  },
  searchAdvanced(payload) {
    return client.post('/product/search/advanced', payload);
  },
  detail(id) {
    return client.get(`/product/detail/${id}`);
  },
  preheat(id) {
    return client.post(`/product/preheat/${id}`);
  },
  adminUploadImage(file) {
    const formData = new FormData();
    formData.append('file', file);
    return client.post('/admin/product/image', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    });
  },
  adminUpload(payload) {
    return client.post('/admin/product/upload', payload);
  }
};

export const couponApi = {
  adminPreheat(payload) {
    return client.post('/admin/coupon/preheat', payload);
  },
  preheat(payload) {
    return client.post('/coupon/preheat', payload);
  }
};

export const cartApi = {
  detail() {
    return client.get('/order/cart');
  },
  addItem(payload) {
    return client.post('/order/cart/item', payload);
  },
  updateItem(payload) {
    return client.put('/order/cart/item', payload);
  },
  switchItemSelected(payload) {
    return client.put('/order/cart/item/selected', payload);
  },
  switchAllSelected(selected) {
    return client.put('/order/cart/select-all', { selected });
  },
  clearChecked(productIds) {
    return client.delete('/order/cart/checked', { data: { productIds } });
  }
};

export const orderApi = {
  create(payload) {
    return client.post('/order/buy', payload);
  },
  status(orderId) {
    return client.get(`/order/status/${orderId}`);
  },
  list() {
    return client.get('/order/list');
  },
  detail(orderId) {
    return client.get(`/order/${orderId}`);
  }
};

export const payApi = {
  query(orderId) {
    return client.get(`/pay/order/${orderId}`);
  },
  createPage(orderId) {
    return client.get(`/pay/createAlipayPage/${orderId}`, {
      responseType: 'text',
      headers: {
        Accept: 'text/html, text/plain, */*'
      }
    });
  },
  createMockPay(orderId) {
    return client.post('/pay/mock/create', null, {
      params: { orderId }
    });
  },
  notifyMockPay(outTradeNo) {
    const form = new URLSearchParams();
    form.set('outTradeNo', outTradeNo);
    return client.post('/pay/mock/notify', form, {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded'
      }
    });
  }
};

export const shippingApi = {
  detail(orderId) {
    return client.get(`/shipping/order/${orderId}`);
  },
  pending(limit = 20) {
    return client.get('/admin/shipping/pending', { params: { limit } });
  },
  deliver(payload) {
    return client.post('/admin/shipping/deliver', payload);
  },
  ship(payload) {
    return client.post('/shipping/admin/ship', payload);
  },
  complete(payload) {
    return client.post('/shipping/admin/complete', payload);
  }
};

export const userApi = {
  listAddresses() {
    return client.get('/user/address/list');
  },
  addAddress(payload) {
    return client.post('/user/address/add', payload);
  },
  updateAddress(payload) {
    return client.put('/user/address/update', payload);
  },
  deleteAddress(id) {
    return client.delete(`/user/address/delete/${id}`);
  }
};

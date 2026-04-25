export function formatCurrency(value) {
  const amount = Number(value || 0) / 100;
  return `￥${amount.toFixed(2)}`;
}

export function orderStatusMeta(status) {
  const mapping = {
    0: { text: '排队处理中', type: 'info' },
    10: { text: '待支付', type: 'warning' },
    20: { text: '已支付', type: 'success' },
    30: { text: '已发货', type: 'primary' },
    40: { text: '已完成', type: 'success' },
    50: { text: '已关闭', type: 'danger' }
  };
  return mapping[status] || { text: '状态更新中', type: 'info' };
}

export function shippingStatusMeta(status) {
  const mapping = {
    0: '等待商家打包',
    1: '已发货',
    2: '运输中',
    3: '已签收',
    4: '已完成',
    5: '物流异常'
  };
  return mapping[status] || '状态更新中';
}

export function joinAddress(address) {
  if (!address) {
    return '';
  }
  if (typeof address === 'string') {
    return address;
  }
  return [address.province, address.city, address.district, address.detailAddress]
    .filter(Boolean)
    .join(' ');
}

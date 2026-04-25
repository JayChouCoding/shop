const ALIPAY_HOST_ID = '__suddenfix_alipay_host__';

function ensureHost() {
  let host = document.getElementById(ALIPAY_HOST_ID);
  if (host) {
    return host;
  }

  host = document.createElement('div');
  host.id = ALIPAY_HOST_ID;
  host.setAttribute('aria-hidden', 'true');
  host.style.position = 'fixed';
  host.style.width = '0';
  host.style.height = '0';
  host.style.overflow = 'hidden';
  host.style.pointerEvents = 'none';
  host.style.opacity = '0';
  host.style.inset = 'auto';
  document.body.appendChild(host);
  return host;
}

export function submitAlipayHtml(formHtml) {
  if (typeof formHtml !== 'string' || !formHtml.trim()) {
    throw new Error('支付页面内容为空，暂时无法继续付款');
  }

  const host = ensureHost();
  host.innerHTML = '';
  host.insertAdjacentHTML('beforeend', formHtml);

  const form = host.querySelector('form');
  if (!form || typeof form.submit !== 'function') {
    host.innerHTML = '';
    throw new Error('支付入口生成失败，请稍后重试');
  }

  if (!form.getAttribute('accept-charset')) {
    form.setAttribute('accept-charset', 'UTF-8');
  }
  if (!form.getAttribute('target')) {
    form.setAttribute('target', '_self');
  }

  window.requestAnimationFrame(() => form.submit());
}

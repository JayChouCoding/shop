import axios from 'axios';
import { clearSession, sessionState } from '../stores/session';

function parseJsonSafely(data) {
  if (!data || typeof data !== 'string') {
    return data;
  }
  const trimmed = data.trim();
  if (!trimmed || trimmed.startsWith('<')) {
    return data;
  }

  try {
    const safeJson = trimmed
      .replace(/(:\s*)(-?\d{16,})(?=\s*[,}\]])/g, '$1"$2"')
      .replace(/([\[,]\s*)(-?\d{16,})(?=\s*[,}\]])/g, '$1"$2"');
    return JSON.parse(safeJson);
  } catch (error) {
    return data;
  }
}

const client = axios.create({
  baseURL: '/api',
  timeout: 10000,
  transformResponse: [parseJsonSafely]
});

client.interceptors.request.use((config) => {
  if (sessionState.token) {
    config.headers.Authorization = `Bearer ${sessionState.token}`;
  }
  return config;
});

client.interceptors.response.use(
  (response) => {
    const payload = response.data;
    if (payload && typeof payload === 'object' && 'code' in payload) {
      if (payload.code === 200) {
        return payload.data;
      }
      throw new Error(payload.message || '请求失败');
    }
    return payload;
  },
  (error) => {
    if (error.response?.status === 401) {
      clearSession();
      throw new Error('登录状态已失效，请重新登录');
    }
    throw new Error(error.response?.data?.message || error.message || '网络异常');
  }
);

export default client;

import { reactive } from 'vue';

const STORAGE_KEY = 'suddenfix.session';

function decodeBase64Url(segment) {
  const normalized = segment.replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalized + '='.repeat((4 - (normalized.length % 4 || 4)) % 4);
  const binary = window.atob(padded);
  return decodeURIComponent(
    Array.from(binary)
      .map((char) => `%${char.charCodeAt(0).toString(16).padStart(2, '0')}`)
      .join('')
  );
}

export function parseTokenClaims(token) {
  try {
    if (!token) {
      return null;
    }
    const segments = token.split('.');
    if (segments.length < 2) {
      return null;
    }
    return JSON.parse(decodeBase64Url(segments[1]));
  } catch (error) {
    return null;
  }
}

function normalizeRole(role) {
  return Number(role) === 1 ? 1 : 0;
}

function readSession() {
  try {
    return JSON.parse(window.localStorage.getItem(STORAGE_KEY) || '{}');
  } catch (error) {
    return {};
  }
}

const saved = readSession();
const savedClaims = parseTokenClaims(saved.token);

export const sessionState = reactive({
  token: saved.token || '',
  account: saved.account || savedClaims?.username || '',
  role: normalizeRole(saved.role ?? savedClaims?.role)
});

function persist() {
  window.localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      token: sessionState.token,
      account: sessionState.account,
      role: sessionState.role
    })
  );
}

export function setSession(token, fallbackAccount) {
  const claims = parseTokenClaims(token);
  sessionState.token = token || '';
  sessionState.account = claims?.username || fallbackAccount || '';
  sessionState.role = normalizeRole(claims?.role);
  persist();
}

export function clearSession() {
  sessionState.token = '';
  sessionState.account = '';
  sessionState.role = 0;
  persist();
}

export function hasSession() {
  return Boolean(sessionState.token);
}

export function isMerchant() {
  return sessionState.role === 1;
}

export function routeForRole(role = sessionState.role) {
  return normalizeRole(role) === 1 ? '/admin' : '/home';
}

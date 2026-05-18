const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

function resolveUserId(explicitUserId) {
  if (explicitUserId) return explicitUserId;

  try {
    const raw = localStorage.getItem('policy_user');
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed?.userId) return parsed.userId;
    }
  } catch (error) {
    // Fall back to default user if localStorage is unavailable or malformed.
  }

  return 1;
}

async function request(path, options = {}) {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
    ...options,
  });

  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch (error) {
      data = null;
    }
  }

  if (!response.ok) {
    const message = data?.message || `Request failed: ${response.status}`;
    const error = new Error(message);
    error.payload = data;
    throw error;
  }

  if (data && typeof data === 'object') {
    const hasIsSuccess = Object.prototype.hasOwnProperty.call(data, 'isSuccess');
    const hasSuccess = Object.prototype.hasOwnProperty.call(data, 'success');
    if (hasIsSuccess || hasSuccess) {
      const ok = hasIsSuccess ? data.isSuccess : data.success;
      if (ok === false) {
        const error = new Error(data.message || '요청 처리에 실패했습니다.');
        error.payload = data;
        throw error;
      }
      return data.result;
    }
  }

  return data;
}

export function getHome(userId) {
  return request(`/api/users/${resolveUserId(userId)}/home`);
}

export function getEvents(dateSegment, category, userId) {
  const query = new URLSearchParams({
    dateSegment: dateSegment,
    category,
  });
  return request(`/api/users/${resolveUserId(userId)}/events?${query.toString()}`);
}

export function updateEventAlert(eventId, enabled, userId) {
  return request(`/api/users/${resolveUserId(userId)}/events/${eventId}/alerts`, {
    method: 'POST',
    body: JSON.stringify({ enabled }),
  });
}

export function getHeatmap(country = 'all') {
  const query = new URLSearchParams({
    marketScope: 'all',
    country,
  });
  return request(`/api/insights/heatmap?${query.toString()}`);
}

export function getMe(userId) {
  return request(`/api/users/${resolveUserId(userId)}`);
}

export function getSettings(userId) {
  return request(`/api/users/${resolveUserId(userId)}/settings`);
}

export function updateSettings(payload, userId) {
  return request(`/api/users/${resolveUserId(userId)}/settings`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

export function triggerAIEngine(userId) {
  return request(`/api/ai/users/${resolveUserId(userId)}/sync`, {
    method: 'POST',
  });
}

export function getWatchAssetOptions() {
  return request('/api/assets/options');
}

export function updateWatchAssets(assetNames, userId) {
  return request(`/api/users/${resolveUserId(userId)}/watchlist`, {
    method: 'POST',
    body: JSON.stringify({ assetNames }),
  });
}

export function getPolicyFeed(payload = {}) {
  const params = new URLSearchParams();
  if (payload.limit !== undefined) params.append('limit', String(payload.limit));
  if (payload.category !== undefined) params.append('category', payload.category);
  if (payload.dateFrom !== undefined) params.append('dateFrom', payload.dateFrom);
  if (payload.dateTo !== undefined) params.append('dateTo', payload.dateTo);

  const qs = params.toString();
  const path = qs ? `/api/feeds/policy?${qs}` : `/api/feeds/policy`;
  return request(path);
}


export function trainRegression() {
  return request('/api/ai/models/regression/training', {
    method: 'POST',
  });
}

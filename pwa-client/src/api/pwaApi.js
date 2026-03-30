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
  return request(`/api/home?user_id=${resolveUserId(userId)}`);
}

export function getEvents(dateSegment, category, userId) {
  const query = new URLSearchParams({
    user_id: String(resolveUserId(userId)),
    date_segment: dateSegment,
    category,
  });
  return request(`/api/events?${query.toString()}`);
}

export function updateEventAlert(eventId, enabled, userId) {
  return request(`/api/events/${eventId}/alerts?user_id=${resolveUserId(userId)}`, {
    method: 'POST',
    body: JSON.stringify({ enabled }),
  });
}

export function getHeatmap(country = 'all') {
  const query = new URLSearchParams({
    market_scope: 'all',
    country,
  });
  return request(`/api/insights/heatmap?${query.toString()}`);
}

export function getMe(userId) {
  return request(`/api/me?user_id=${resolveUserId(userId)}`);
}

export function getNotificationSettings(userId) {
  return request(`/api/me/settings/notifications?user_id=${resolveUserId(userId)}`);
}

export function updateNotificationSettings(payload, userId) {
  return request(`/api/me/settings/notifications?user_id=${resolveUserId(userId)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

export function triggerAIEngine(userId) {
  return request(`/api/ai/trigger?user_id=${resolveUserId(userId)}`, {
    method: 'POST',
  });
}

export function getWatchAssetOptions() {
  return request('/api/me/watch-assets/options');
}

export function updateWatchAssets(assetNames, userId) {
  return request(`/api/me/watch-assets?user_id=${resolveUserId(userId)}`, {
    method: 'POST',
    body: JSON.stringify({ asset_names: assetNames }),
  });
}

export function refreshEvents(dateSegment, category, userId) {
  const query = new URLSearchParams({
    user_id: String(resolveUserId(userId)),
    date_segment: dateSegment,
    category,
  });

  return request(`/api/events/refresh?${query.toString()}`, {
    method: 'POST',
  });
}

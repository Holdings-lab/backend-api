const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

function toErrorMessage(data, status) {
  if (!data) return `Request failed: ${status}`;
  if (data.message) return data.message;
  if (data.error_message) return data.error_message;
  if (Array.isArray(data.field_errors) && data.field_errors.length > 0) {
    const first = data.field_errors[0];
    if (first.reason) return first.reason;
  }
  return `Request failed: ${status}`;
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
    const error = new Error(toErrorMessage(data, response.status));
    error.payload = data;
    throw error;
  }

  // Success envelope from backend can be { success, ... } or { isSuccess, ... }.
  if (data && typeof data === 'object') {
    const hasIsSuccess = Object.prototype.hasOwnProperty.call(data, 'isSuccess');
    const hasSuccess = Object.prototype.hasOwnProperty.call(data, 'success');
    if (hasIsSuccess || hasSuccess) {
      const ok = hasIsSuccess ? data.isSuccess : data.success;
      if (ok === false) {
        const error = new Error(toErrorMessage(data, response.status));
        error.payload = data;
        throw error;
      }
      return data.result;
    }
  }

  return data;
}

export function register({ username, nickname, password }) {
  return request('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, nickname, password }),
  });
}

export function login({ username, password }) {
  return request('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}

export function updateNickname(userId, nickname) {
  return request(`/api/auth/users/${userId}/nickname`, {
    method: 'PATCH',
    body: JSON.stringify({ nickname }),
  });
}

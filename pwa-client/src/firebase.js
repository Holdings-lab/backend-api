// Firebase 설정 및 FCM 초기화
import { initializeApp } from 'firebase/app';
import { getMessaging, getToken, onMessage } from 'firebase/messaging';

// Your web app's Firebase configuration
// For Firebase JS SDK v7.20.0 and later, measurementId is optional
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
  measurementId: import.meta.env.VITE_FIREBASE_MEASUREMENT_ID
};

// Firebase 초기화
const app = initializeApp(firebaseConfig);

// FCM 초기화
let messaging = null;
try {
  messaging = getMessaging(app);
} catch (error) {
  console.warn('FCM 초기화 실패:', error);
}

// FCM 토큰 요청 함수
export const requestFCMToken = async () => {
  if (!messaging) return null;

  try {
    // 알림 권한 요청
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') {
      console.warn('알림 권한이 거부되었습니다.');
      return null;
    }

    // FCM 토큰 가져오기
    const token = await getToken(messaging, {
      vapidKey: process.env.VAPID_KEY
    });

    console.log('FCM 토큰:', token);
    return token;
  } catch (error) {
    console.error('FCM 토큰 요청 실패:', error);
    return null;
  }
};

// FCM 토큰 등록 함수 (서버에 토큰 전송)
export const registerFCMToken = async (userId) => {
  try {
    console.log('FCM 토큰 등록 시작, userId:', userId);
    const token = await requestFCMToken();
    if (!token) {
      console.warn('FCM 토큰을 가져올 수 없습니다.');
      alert('알림 권한이 허용되지 않아 FCM 토큰을 발급받을 수 없습니다. 브라우저 설정에서 알림을 허용해주세요.');
      return;
    }

    console.log('FCM 토큰 획득 성공:', token.substring(0, 50) + '...');

    // 서버에 FCM 토큰 등록
    const requestBody = {
      userId: userId,
      fcmToken: token
    };
    console.log('서버 요청 데이터:', requestBody);

    const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/api/auth/register-fcm-token`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(requestBody)
    });

    console.log('서버 응답 상태:', response.status);

    if (response.ok) {
      const responseData = await response.json();
      console.log('FCM 토큰이 서버에 등록되었습니다.', responseData);
      // 로컬 스토리지에 토큰 저장
      localStorage.setItem('fcmToken', token);
    } else {
      const errorText = await response.text();
      console.error('FCM 토큰 등록 실패:', response.status, errorText);
      alert(`FCM 토큰 등록 실패: ${response.status} ${errorText}`);
    }
  } catch (error) {
    console.error('FCM 토큰 등록 중 오류:', error);
    alert('FCM 토큰 등록 중 오류가 발생했습니다. 콘솔을 확인해주세요.');
  }
};

// FCM 메시지 수신 리스너
export const onMessageListener = () =>
  new Promise((resolve) => {
    if (!messaging) return resolve(null);

    onMessage(messaging, (payload) => {
      console.log('FCM 메시지 수신:', payload);
      resolve(payload);
    });
  });

export { messaging };
import { useState, useEffect } from 'react';
import { onMessageListener, registerFCMToken } from './firebase';

function App() {
  const [events, setEvents] = useState([]);
  const [assets, setAssets] = useState(10000000);
  const [inputAsset, setInputAsset] = useState("");
  const [loading, setLoading] = useState(true);
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [currentUser, setCurrentUser] = useState(null);
  const [showAuth, setShowAuth] = useState(false);
  const [authMode, setAuthMode] = useState('login'); // 'login' or 'register'
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  const fetchEvents = async () => {
    try {
      const response = await fetch(`${import.meta.env.VITE_AI_BASE_URL}/events`);
      if (response.ok) {
        const data = await response.json();
        // Sort events by latest creation date
        data.sort((a, b) => new Date(b.created_at) - new Date(a.created_at));
        setEvents(data);
      }
    } catch (error) {
      console.error("Failed to fetch events:", error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // 로그인 상태 복원
    const savedUser = localStorage.getItem('user');
    if (savedUser) {
      try {
        const user = JSON.parse(savedUser);
        setCurrentUser(user);
        setIsLoggedIn(true);
      } catch (error) {
        console.error('Failed to parse saved user:', error);
        localStorage.removeItem('user');
      }
    }

    fetchEvents();
    // 5초마다 자동 갱신 (더미 웹훅 연동 테스트용)
    const interval = setInterval(fetchEvents, 5000);

    // FCM 메시지 리스너 설정
    const setupFCMListener = async () => {
      try {
        const messaging = await onMessageListener();
        if (messaging) {
          console.log('FCM 메시지 수신:', messaging);
          // 알림 표시
          if (Notification.permission === 'granted') {
            new Notification('새 이벤트 알림', {
              body: messaging.notification?.body || '새 이벤트가 발생했습니다.',
              icon: '/vite.svg'
            });
          }
          // 이벤트 목록 새로고침
          fetchEvents();
        }
      } catch (error) {
        console.error('FCM 리스너 설정 실패:', error);
      }
    };

    setupFCMListener();

    return () => clearInterval(interval);
  }, []);

  const handleUpdateAsset = (e) => {
    e.preventDefault();
    if (inputAsset) {
      setAssets(Number(inputAsset));
      setInputAsset("");
    }
  };

  const handleTestTrigger = async () => {
    try {
      const response = await fetch(`${import.meta.env.VITE_AI_BASE_URL}/test-trigger`);
      if (response.ok) {
        // FCM 알림이 올 때까지 기다렸다가 이벤트 목록 갱신
        setTimeout(() => {
          fetchEvents();
        }, 2000); // 2초 후 갱신 (FCM 전송 시간 고려)
      } else {
        console.error('Test trigger failed');
      }
    } catch (error) {
      console.error('Error triggering test:', error);
    }
  };

  const handleAuth = async (e) => {
    e.preventDefault();
    try {
      const endpoint = authMode === 'login' ? 'login' : 'register';
      const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/api/auth/${endpoint}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ username, password }),
      });

      const data = await response.json();

      if (response.ok) {
        setCurrentUser({ id: data.user_id, username: data.username });
        setIsLoggedIn(true);
        setShowAuth(false);
        setUsername('');
        setPassword('');

        // 로그인 상태 저장
        localStorage.setItem('user', JSON.stringify({ id: data.user_id, username: data.username }));

        // FCM 토큰 등록
        registerFCMToken(data.user_id);
        alert(data.message);
      } else {
        alert(data.message || 'Authentication failed');
      }
    } catch (error) {
      console.error('Auth error:', error);
      alert('Authentication error');
    }
  };

  const handleLogout = () => {
    setCurrentUser(null);
    setIsLoggedIn(false);
    setShowAuth(false);
    setUsername('');
    setPassword('');
    // 로그인 상태 제거
    localStorage.removeItem('user');
    localStorage.removeItem('fcmToken');
    alert('로그아웃되었습니다.');
  };

  const handleDeleteAccount = async () => {
    if (!currentUser?.id) return;

    const confirm = window.confirm('정말로 회원 탈퇴를 진행하시겠습니까? 이 작업은 되돌릴 수 없습니다.');
    if (!confirm) return;

    try {
      const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/api/auth/delete/${currentUser.id}`, {
        method: 'DELETE',
      });

      const data = await response.json();
      if (response.ok) {
        handleLogout();
        alert(data.message || '회원 탈퇴가 완료되었습니다.');
      } else {
        alert(data.message || '회원 탈퇴에 실패했습니다.');
      }
    } catch (error) {
      console.error('회원 탈퇴 중 오류:', error);
      alert('회원 탈퇴 중 오류가 발생했습니다. 콘솔을 확인하세요.');
    }
  };

  const getImpactColor = (score) => {
    if (score >= 8) return "text-red-600 bg-red-100";
    if (score >= 6) return "text-orange-600 bg-orange-100";
    return "text-blue-600 bg-blue-100";
  };

  return (
    <div className="min-h-screen bg-gray-50 text-gray-800 font-sans">
      {/* Header */}
      <header className="bg-gradient-to-r from-blue-700 to-indigo-800 text-white p-6 shadow-md">
        <div className="flex justify-between items-center">
          <h1 className="text-2xl font-bold tracking-tight">AI Policy Engine PWA</h1>
          {isLoggedIn ? (
            <div className="flex items-center gap-4">
              <span className="text-sm">환영합니다, {currentUser.username}님</span>

              {Notification.permission !== 'granted' && (
                <button
                  onClick={() => registerFCMToken(currentUser.id)}
                  className="text-sm bg-indigo-600 hover:bg-indigo-700 px-3 py-1 rounded-md transition-colors"
                >
                  알림 허용
                </button>
              )}

              <button 
                onClick={handleDeleteAccount}
                className="text-sm bg-amber-500 hover:bg-amber-600 px-3 py-1 rounded-md transition-colors"
              >
                회원탈퇴
              </button>
              <button 
                onClick={handleLogout}
                className="text-sm bg-red-600 hover:bg-red-700 px-3 py-1 rounded-md transition-colors"
              >
                로그아웃
              </button>
            </div>
          ) : (
            <button 
              onClick={() => setShowAuth(true)}
              className="text-sm bg-green-600 hover:bg-green-700 px-3 py-1 rounded-md transition-colors"
            >
              로그인
            </button>
          )}
        </div>
        <p className="text-sm opacity-80 mt-1">정책 이벤트 기반 실시간 자산 영향도 예측 시스템</p>
      </header>

      <main className="max-w-4xl mx-auto p-4 md:p-6 space-y-6">
        {!isLoggedIn ? (
          // 로그인 폼
          <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-8 max-w-md mx-auto">
            <h2 className="text-2xl font-bold text-center mb-6 text-gray-800">
              {authMode === 'login' ? '로그인' : '회원가입'}
            </h2>
            <form onSubmit={handleAuth} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">아이디</label>
                <input
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">비밀번호</label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <button 
                type="submit"
                className="w-full bg-blue-600 hover:bg-blue-700 text-white py-2 px-4 rounded-lg font-medium transition-colors"
              >
                {authMode === 'login' ? '로그인' : '회원가입'}
              </button>
            </form>
            <div className="mt-4 text-center">
              <button 
                onClick={() => setAuthMode(authMode === 'login' ? 'register' : 'login')}
                className="text-sm text-blue-600 hover:text-blue-800"
              >
                {authMode === 'login' ? '계정이 없으신가요? 회원가입' : '이미 계정이 있으신가요? 로그인'}
              </button>
            </div>
          </div>
        ) : (
          <>
            {/* Assets Section */}
            <section className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
              <h2 className="text-lg font-semibold mb-4 text-gray-700 flex items-center">
                <span className="mr-2 text-xl">💰</span>나의 자산 시뮬레이터
              </h2>
              <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="text-4xl font-extrabold text-blue-900 tracking-tight">
                  {assets.toLocaleString()} <span className="text-xl text-gray-500 font-medium">원</span>
                </div>
                
                <form onSubmit={handleUpdateAsset} className="flex gap-2">
                  <input
                    type="number"
                    value={inputAsset}
                    onChange={(e) => setInputAsset(e.target.value)}
                    placeholder="새 자산 금액 입력"
                    className="px-4 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 transition-shadow"
                  />
                  <button 
                    type="submit"
                    className="bg-blue-600 hover:bg-blue-700 text-white px-5 py-2 rounded-lg font-medium transition-colors shadow-sm"
                  >
                    자산 갱신
                  </button>
                </form>
              </div>
              <p className="text-sm text-gray-400 mt-4 italic">
                * DB 연동 없이 클라이언트 메모리에서만 동작합니다. 새로운 정책 이벤트(알림)를 확인할 때 자산 변동을 체감해보세요.
              </p>
            </section>

            {/* Events Data Section */}
            <section className="space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-semibold text-gray-700 flex items-center">
                  <span className="mr-2 text-xl">📡</span>실시간 정책 이벤트 수신 목록
                </h2>
                <div className="flex gap-2">
                  <button 
                    onClick={handleTestTrigger}
                    className="text-sm text-green-600 hover:text-green-800 font-medium px-3 py-1 bg-green-50 rounded-md transition-colors"
                  >
                    🚀 Test Trigger
                  </button>
                  <button 
                    onClick={fetchEvents}
                    className="text-sm text-blue-600 hover:text-blue-800 font-medium px-3 py-1 bg-blue-50 rounded-md transition-colors"
                  >
                    🔄 새로고침
                  </button>
                </div>
              </div>

              {loading ? (
                <div className="text-center py-10 text-gray-500 animate-pulse">
                  데이터를 불러오는 중입니다...
                </div>
              ) : events.length === 0 ? (
                <div className="bg-white rounded-2xl p-8 text-center border border-dashed border-gray-300">
                  <span className="text-3xl mb-2 block">📭</span>
                  <p className="text-gray-500 font-medium">수신된 이벤트가 없습니다.</p>
                  <p className="text-sm text-gray-400 mt-1">ai-engine에서 /test-trigger를 호출해보세요.</p>
                </div>
              ) : (
                <div className="grid gap-4 md:grid-cols-2">
                  {events.map(event => (
                    <article 
                      key={event.id} 
                      className="bg-white p-5 rounded-2xl shadow-sm border border-gray-100 hover:shadow-md transition-shadow relative overflow-hidden group"
                    >
                      <div className="absolute top-0 left-0 w-1 h-full bg-blue-500 group-hover:bg-indigo-600 transition-colors"></div>
                      
                      <div className="flex justify-between items-start mb-3 pl-2">
                        <span className="inline-block px-3 py-1 text-xs font-bold uppercase tracking-wider text-indigo-700 bg-indigo-50 rounded-full">
                          #{event.keyword}
                        </span>
                        <span className="text-xs text-gray-400 font-medium">
                          {new Date(event.created_at).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                        </span>
                      </div>
                      
                      <h3 className="text-xl font-bold text-gray-800 mb-2 pl-2 leading-tight">
                        {event.title}
                      </h3>
                      
                      <p className="text-sm text-gray-600 bg-gray-50 p-3 rounded-xl mb-4 pl-2 leading-relaxed">
                        {event.analysis_summary}
                      </p>
                      
                      <div className="flex items-center justify-between border-t border-gray-50 pt-3 pl-2">
                        <span className="text-sm text-gray-500 font-medium">AI Impact Score</span>
                        <span className={`px-3 py-1 rounded-lg font-bold text-sm flex items-center gap-1 ${getImpactColor(event.impact_score)}`}>
                          ⚡ {event.impact_score}
                        </span>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </section>
          </>
        )}
      </main>
    </div>
  );
}

export default App;

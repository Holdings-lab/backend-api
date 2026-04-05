import { useEffect, useMemo, useState } from 'react';
import './App.css';
import { login, register } from './api/authApi';
import { getEvents, getHome, getMe, getPolicyFeed } from './api/pwaApi';

const NAV_TABS = [
  { key: 'home', label: '홈', icon: '🏠' },
  { key: 'signal', label: '시그널', icon: '📶' },
  { key: 'asset', label: '내 자산', icon: '📋' },
  { key: 'league', label: '리그', icon: '🏆' },
];

const POLICY_ICON_BY_CATEGORY = {
  fomc: '🏛️',
  'white house': '⚙️',
  bis: '⚡',
};

function toTone(category) {
  const value = (category || '').toLowerCase();
  if (value.includes('fomc')) return 'blue';
  if (value.includes('white')) return 'purple';
  if (value.includes('bis')) return 'green';
  return 'blue';
}

function formatDateText(date) {
  if (!date) return '일정 미정';
  const parsed = new Date(date);
  if (Number.isNaN(parsed.getTime())) return date;
  return `${parsed.getMonth() + 1}/${parsed.getDate()}`;
}

function moneyFormat(value) {
  return new Intl.NumberFormat('ko-KR').format(Math.round(value));
}

function buildPolicyCards(feedCards) {
  return (feedCards || []).slice(0, 3).map((card, index) => {
    const category = card.category || '정책';
    const tone = toTone(category);
    const id = card.id || `policy-${index + 1}`;
    const title = card.title || '정책 업데이트';
    const date = `일정 · ${formatDateText(card.date)}`;
    const icon = POLICY_ICON_BY_CATEGORY[(category || '').toLowerCase()] || (index % 2 === 0 ? '🏛️' : '⚙️');
    return { id, icon, date, title, tone, category, detail: card };
  });
}

function buildNewsCards(feedCards) {
  return (feedCards || []).slice(0, 3).map((card, index) => {
    const tone = toTone(card.category);
    const score = card.impact?.score ?? 0;
    return {
      id: card.id || `news-${index + 1}`,
      tag: card.category || '정책',
      ago: `${index + 2}시간 전`,
      title: card.title || '정책 뉴스',
      note: `↗ 영향 점수 ${score} · ${card.modelSignal?.signal || 'hold'} 시그널`,
      tone,
    };
  });
}

function buildActionQueue(feedCards) {
  return (feedCards || []).slice(0, 3).map((card, index) => {
    const tone = toTone(card.category);
    const driver = card.features?.featureDrivers?.[0] || '핵심 피처 기반으로 변동성 신호를 확인했습니다.';
    const confidence = card.modelSignal?.confidence ?? 0;
    return {
      id: card.id || `action-${index + 1}`,
      week: `${index + 3}월 ${index + 1}주차`,
      linked: '보유자산 연관',
      title: card.title || '정책 대응 점검',
      description: card.bodyExcerpt || card.bodySummary || '정책 문서를 기반으로 자산 영향도를 해석합니다.',
      action: `${driver} (신뢰도 ${Math.round(confidence * 100)}%)`,
      tone,
    };
  });
}

function buildMatchingItems(feedCards) {
  return (feedCards || []).slice(0, 3).map((card, index) => {
    const tone = toTone(card.category);
    const score = card.impact?.score || card.modelSignal?.signalStrength || 50;
    const target = card.impact?.targetAssets?.[0] || 'QQQ';
    const avg = card.modelSignal?.predictedReturnPct;
    return {
      id: card.id || `match-${index + 1}`,
      icon: POLICY_ICON_BY_CATEGORY[(card.category || '').toLowerCase()] || '🏛️',
      policy: card.title || '정책 이벤트',
      etf: `${target} (연관 ETF)`,
      score,
      avg: `${avg === undefined || avg === null ? 0 : avg}%`,
      tone,
    };
  });
}

function HomeTab({ homeData, policyCards, newsCards }) {
  const [selectedPolicyId, setSelectedPolicyId] = useState(policyCards[0]?.id || '');

  useEffect(() => {
    if (!policyCards.some((card) => card.id === selectedPolicyId)) {
      setSelectedPolicyId(policyCards[0]?.id || '');
    }
  }, [policyCards, selectedPolicyId]);

  const selectedPolicy = policyCards.find((card) => card.id === selectedPolicyId) || policyCards[0];
  const summary = selectedPolicy?.detail?.bodySummary || selectedPolicy?.detail?.bodyExcerpt || '정책 내용을 바탕으로 핵심 요약을 생성했습니다.';
  const drivers = selectedPolicy?.detail?.features?.featureDrivers || [];

  return (
    <div className="screen-content">
      <header className="top-head">
        <div>
          <p className="date-text">{new Date().toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })}</p>
          <h1>{homeData?.userGreeting?.headline || '안녕하세요, 투자자님'}</h1>
        </div>
        <div className="avatar">{(homeData?.userGreeting?.headline || 'JK').slice(0, 2).toUpperCase()}</div>
      </header>

      <section className="section-title-row">
        <h2>이번 주 정책 일정</h2>
      </section>

      <div className="policy-row">
        {policyCards.map((card) => (
          <button
            key={card.id}
            className={`policy-card ${selectedPolicyId === card.id ? 'selected' : ''}`}
            onClick={() => setSelectedPolicyId(card.id)}
          >
            <span className={`policy-icon ${card.tone}`}>{card.icon}</span>
            <p className="policy-date">{card.date}</p>
            <p className="policy-title">{card.title}</p>
          </button>
        ))}
      </div>

      {selectedPolicy && (
        <section className="glass-card detail-card">
          <h3>내 보유 자산 기준 브리핑</h3>
          <p className="sub-copy">{summary}</p>
          <h3>정책 핵심 포인트</h3>
          <ol className="number-list">
            {drivers.length > 0
              ? drivers.slice(0, 3).map((driver, index) => <li key={`${selectedPolicy.id}-${index + 1}`}>{driver}</li>)
              : <li>모델 피처 기반 요약을 준비 중입니다.</li>}
          </ol>
          <div className="tip-box">
            <strong>발표 때 이것만 확인하세요</strong>
            <p>카테고리: {selectedPolicy.category || '정책'}</p>
            <p>예상 시그널: {(selectedPolicy.detail?.modelSignal?.signal || 'hold').toUpperCase()}</p>
          </div>
        </section>
      )}

      <section className="glass-card portfolio-card">
        <div className="row-between">
          <h3>My Portfolio</h3>
          <span className="gain-pill">↗ {(homeData?.featuredEvent?.tags || []).includes('금리') ? '+1.2%' : '+0.8%'}</span>
        </div>
        <p className="money">₩{moneyFormat(13550000)}</p>
        <div className="reason-box">
          <strong>이유 있는 변동</strong>
          <p>{homeData?.featuredEvent?.summary || '정책 이벤트와 모델 시그널을 결합해 변동 이유를 제공합니다.'}</p>
          <small>➜ 데이터 출처: merged_finbert.csv / train_regression.py 결과 반영</small>
        </div>
      </section>

      <section className="section-title-row mt-8">
        <h2>맞춤형 정책 뉴스</h2>
      </section>

      <div className="news-stack">
        {newsCards.map((item) => (
          <article className="glass-card news-card" key={item.id}>
            <div className="row-between">
              <span className={`tag ${item.tone}`}>{item.tag}</span>
              <span className="ago">{item.ago}</span>
            </div>
            <p className="news-title">{item.title}</p>
            <p className="news-note">{item.note}</p>
          </article>
        ))}
      </div>
    </div>
  );
}

function SignalTab({ actionQueue, matchingItems, trainingResult }) {
  return (
    <div className="screen-content">
      <header className="signal-header">
        <h1>시그널 센터</h1>
        <p>정책이 만드는 투자 기회를 포착하세요</p>
        {trainingResult ? <p>학습 실행 상태: {trainingResult?.exitCode === 0 ? '정상' : '확인 필요'}</p> : null}
      </header>

      <section className="glass-card">
        <h3>이번 달 액션 큐</h3>
        <p className="sub-copy">보유 ETF 기준으로 지금 확인할 정책 대응 순서예요</p>
        <div className="queue-stack">
          {actionQueue.map((item) => (
            <article className="queue-item" key={item.id}>
              <div className="row-wrap">
                <span className="tag purple">{item.week}</span>
                <span className="tag green">{item.linked}</span>
              </div>
              <h4>{item.title}</h4>
              <p>{item.description}</p>
              <div className={`recommend ${item.tone}`}>➜ {item.action}</div>
            </article>
          ))}
        </div>
      </section>

      <section className="section-title-row mt-12">
        <h2>정책-ETF 매칭</h2>
      </section>

      <div className="match-stack">
        {matchingItems.map((item) => (
          <article className="glass-card match-card" key={item.id}>
            <div className="match-main">
              <div className="match-left">
                <span className={`policy-icon ${item.tone}`}>{item.icon}</span>
                <div>
                  <p className="policy-date">{item.policy}</p>
                  <p className="match-etf">{item.etf}</p>
                  <p className="news-note">📈 예상 반응 {item.avg}</p>
                </div>
              </div>
              <div className={`score-ring ${item.tone}`} style={{ '--score': `${item.score}` }}>
                <span>{item.score}</span>
              </div>
            </div>
            <button className="apply-btn">이번 달 포트폴리오에 반영하기</button>
          </article>
        ))}
      </div>
    </div>
  );
}

function AssetTab({ meData, matchingItems }) {
  const [segment, setSegment] = useState('detail');
  const watchAssets = meData?.watchAssets || [];
  const [weights, setWeights] = useState(() => {
    const base = {};
    watchAssets.slice(0, 3).forEach((asset, index) => {
      base[index] = Math.min(45, Math.max(5, Math.round(Math.abs(asset.changePercent || 0) * 7) + 15));
    });
    return base;
  });

  useEffect(() => {
    const next = {};
    watchAssets.slice(0, 3).forEach((asset, index) => {
      next[index] = Math.min(45, Math.max(5, Math.round(Math.abs(asset.changePercent || 0) * 7) + 15));
    });
    setWeights(next);
  }, [watchAssets]);

  return (
    <div className="screen-content">
      <div className="segment-wrap">
        <button className={segment === 'detail' ? 'active' : ''} onClick={() => setSegment('detail')}>내 자산 상세</button>
        <button className={segment === 'rebalance' ? 'active' : ''} onClick={() => setSegment('rebalance')}>리밸런싱</button>
      </div>

      {segment === 'detail' ? (
        <>
          <section className="glass-card">
            <h3>내 자산 요약</h3>
            <div className="summary-grid">
              <div><small>연결 자산</small><strong>{watchAssets.length}개</strong></div>
              <div><small>ETF 비중</small><strong>{Math.min(90, watchAssets.length * 12 + 20)}%</strong></div>
              <div><small>안전자산</small><strong>{Math.max(10, 100 - (watchAssets.length * 12 + 20))}%</strong></div>
            </div>
            <p className="sub-copy">연결된 자산 현황을 한 번에 확인하고 리밸런싱으로 이동하세요.</p>
          </section>

          <section className="glass-card">
            <h3>보유 자산 상세</h3>
            <div className="asset-list">
              {watchAssets.map((item, index) => (
                <div className="asset-line" key={`${item.assetName}-${index + 1}`}>
                  <div className="asset-left">
                    <span className="asset-icon">📊</span>
                    <div>
                      <p>{item.assetName}</p>
                      <small>ETF</small>
                    </div>
                  </div>
                  <strong>{Math.max(3, Math.round(Math.abs(item.changePercent || 0) * 8) + 10)}%</strong>
                </div>
              ))}
            </div>
          </section>

          <section className="glass-card">
            <h3>증권사앱 연결</h3>
            <p className="sub-copy">실계좌 연결로 보유종목과 잔고를 자동 동기화할 수 있어요</p>
            <button className="link-manage">증권사앱 연결 관리 ›</button>
          </section>
        </>
      ) : (
        <>
          <header className="signal-header compact">
            <h1>리밸런싱</h1>
            <p>보유 ETF 전체 비중을 조절하고 추천 비중과 수수료 영향을 함께 확인하세요</p>
          </header>

          <section className="glass-card">
            <h3>투자 시뮬레이터</h3>
            <p className="sub-copy">데이터-ML 모델 신호를 반영해 리밸런싱 비중을 계산합니다.</p>
            <div className="rebalance-stack">
              {watchAssets.slice(0, 3).map((asset, index) => {
                const match = matchingItems[index];
                return (
                  <article className="rebalance-item" key={`${asset.assetName}-${index + 1}`}>
                    <div className="row-between">
                      <div>
                        <h4>{asset.assetName}</h4>
                        <p className="sub-copy">연결 ETF</p>
                      </div>
                      <div className="row-wrap">
                        <span className={`tag ${match?.tone || 'blue'}`}>추천 {Math.max(10, (weights[index] || 20) - 2)}%</span>
                        <strong className="ratio">{weights[index] || 20}%</strong>
                      </div>
                    </div>
                    <input
                      className={`weight-slider ${match?.tone || 'blue'}`}
                      type="range"
                      min="0"
                      max="45"
                      value={weights[index] || 20}
                      onChange={(event) => setWeights((prev) => ({ ...prev, [index]: Number(event.target.value) }))}
                    />
                    <div className="row-between muted-line">
                      <span>현재 {weights[index] || 20}%</span>
                      <span>최대 45%</span>
                      <span className="up">예상 {(asset.changePercent || 0).toFixed(1)}%/월</span>
                    </div>
                  </article>
                );
              })}
            </div>
          </section>
        </>
      )}
    </div>
  );
}

function LeagueTab() {
  return (
    <div className="screen-content">
      <header className="signal-header compact">
        <h1>리그</h1>
        <p>참여/투표/랭킹 기반 동기부여</p>
      </header>
    </div>
  );
}

function AuthGate({ mode, setMode, onSubmit, loading, error }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');

  return (
    <div className="app-bg">
      <main className="phone-shell auth-shell">
        <div className="dynamic-island" />
        <div className="auth-wrap">
          <p className="auth-kicker">PolicyLink</p>
          <h1>환영합니다</h1>
          <p className="auth-sub">정책 뉴스가 내 자산에 미치는 영향을 쉽게 확인해보세요.</p>

          <div className="auth-segment">
            <button className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>로그인</button>
            <button className={mode === 'signup' ? 'active' : ''} onClick={() => setMode('signup')}>회원가입</button>
          </div>

          <form
            className="auth-form"
            onSubmit={(event) => {
              event.preventDefault();
              onSubmit({ email, password, nickname });
            }}
          >
            <label>
              이메일
              <input type="text" value={email} onChange={(event) => setEmail(event.target.value)} required />
            </label>

            {mode === 'signup' && (
              <label>
                닉네임
                <input value={nickname} onChange={(event) => setNickname(event.target.value)} required />
              </label>
            )}

            <label>
              비밀번호
              <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required />
            </label>

            {error ? <p className="sub-copy">{error}</p> : null}

            <button type="submit" className="auth-submit" disabled={loading}>
              {loading ? '처리 중...' : mode === 'login' ? '로그인하고 시작하기' : '회원가입 후 시작하기'}
            </button>
          </form>
        </div>
      </main>
    </div>
  );
}

function App() {
  const [activeTab, setActiveTab] = useState('home');
  const [authMode, setAuthMode] = useState('login');
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState('');

  const [homeData, setHomeData] = useState(null);
  const [eventsData, setEventsData] = useState(null);
  const [meData, setMeData] = useState(null);
  const [feedData, setFeedData] = useState(null);
  const [trainData, setTrainData] = useState(null);
  const [loadingData, setLoadingData] = useState(true);

  useEffect(() => {
    const saved = localStorage.getItem('policy_user');
    if (saved) {
      setIsAuthenticated(true);
    }
  }, []);

  useEffect(() => {
    if (!isAuthenticated) return;

    async function bootstrap() {
      setLoadingData(true);
      try {
        const raw = localStorage.getItem('policy_user');
        const user = raw ? JSON.parse(raw) : null;
        const userId = user?.userId || 1;

        const [homeRes, eventsRes, meRes, feedRes] = await Promise.allSettled([
          getHome(userId),
          getEvents('this_week', 'all', userId),
          getMe(userId),
          getPolicyFeed({ limit: 20, category: 'all', userId }),
        ]);

        if (homeRes.status === 'fulfilled') setHomeData(homeRes.value);
        if (eventsRes.status === 'fulfilled') setEventsData(eventsRes.value);
        if (meRes.status === 'fulfilled') setMeData(meRes.value);
        if (feedRes.status === 'fulfilled') setFeedData(feedRes.value);

        [homeRes, eventsRes, meRes, feedRes].forEach((result) => {
          if (result.status === 'rejected') {
            console.warn(result.reason);
          }
        });
      } catch (error) {
        console.warn(error);
      } finally {
        setLoadingData(false);
      }
    }

    bootstrap();
  }, [isAuthenticated]);

  function handleLogout() {
    localStorage.removeItem('policy_user');
    setIsAuthenticated(false);
    setAuthMode('login');
    setAuthError('');
    setActiveTab('home');
    setHomeData(null);
    setEventsData(null);
    setMeData(null);
    setFeedData(null);
    setTrainData(null);
    setLoadingData(true);
  }

  async function handleAuthSubmit({ email, password, nickname }) {
    if (!email || !password || (authMode === 'signup' && !nickname)) {
      return;
    }

    setAuthError('');
    setAuthLoading(true);
    try {
      const response = authMode === 'login'
        ? await login({ email, password })
        : await register({ email, nickname, password });

      localStorage.setItem('policy_user', JSON.stringify({
        userId: response.userId,
        email: response.email,
        nickname: response.nickname,
      }));

      setIsAuthenticated(true);
    } catch (error) {
      setAuthError(error.message || '인증 처리에 실패했습니다.');
    } finally {
      setAuthLoading(false);
    }
  }

  const policyCards = useMemo(() => {
    const cards = buildPolicyCards(feedData?.cards);
    if (cards.length > 0) return cards;
    const events = eventsData?.items || [];
    return events.slice(0, 3).map((event, index) => ({
      id: `event-${index + 1}`,
      icon: '🏛️',
      date: event.timeText || '일정 · 미정',
      title: event.title || '정책 일정',
      tone: 'blue',
      category: '정책',
      detail: {},
    }));
  }, [feedData, eventsData]);

  const newsCards = useMemo(() => buildNewsCards(feedData?.cards), [feedData]);
  const actionQueue = useMemo(() => buildActionQueue(feedData?.cards), [feedData]);
  const matchingItems = useMemo(() => buildMatchingItems(feedData?.cards), [feedData]);

  if (!isAuthenticated) {
    return <AuthGate mode={authMode} setMode={setAuthMode} onSubmit={handleAuthSubmit} loading={authLoading} error={authError} />;
  }

  if (loadingData) {
    return (
      <div className="app-bg">
        <main className="phone-shell">
          <div className="dynamic-island" />
          <div className="scroll-frame">
            <div className="screen-content">
              <section className="glass-card">
                <h3>데이터 불러오는 중</h3>
                <p className="sub-copy">merged_finbert.csv 및 학습 결과를 동기화하고 있습니다.</p>
              </section>
            </div>
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="app-bg">
      <main className="phone-shell">
        <div className="dynamic-island" />
        <button type="button" className="logout-button" onClick={handleLogout}>로그아웃</button>
        <div className="scroll-frame">
          {activeTab === 'home' && <HomeTab homeData={homeData} policyCards={policyCards} newsCards={newsCards} />}
          {activeTab === 'signal' && <SignalTab actionQueue={actionQueue} matchingItems={matchingItems} trainingResult={trainData} />}
          {activeTab === 'asset' && <AssetTab meData={meData} matchingItems={matchingItems} />}
          {activeTab === 'league' && <LeagueTab />}
        </div>

        <nav className="bottom-nav">
          {NAV_TABS.map((tab) => (
            <button
              key={tab.key}
              className={`nav-button ${activeTab === tab.key ? 'active' : ''}`}
              onClick={() => setActiveTab(tab.key)}
            >
              <span className="nav-icon">{tab.icon}</span>
              <span className="nav-label">{tab.label}</span>
            </button>
          ))}
        </nav>
      </main>
    </div>
  );
}

export default App;

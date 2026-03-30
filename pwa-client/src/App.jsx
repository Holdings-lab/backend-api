import { useEffect, useMemo, useState } from 'react';
import {
  getEvents,
  getWatchAssetOptions,
  getHeatmap,
  getHome,
  getMe,
  refreshEvents,
  getNotificationSettings,
  updateEventAlert,
  updateNotificationSettings,
  triggerAIEngine,
  updateWatchAssets,
} from './api/pwaApi';
import { login, register, updateNickname } from './api/authApi';
import { pwaMockData } from './mocks/pwaMockData';

const TABS = [
  { key: 'home', label: '홈', icon: '⌂' },
  { key: 'events', label: '이벤트', icon: '▦' },
  { key: 'insight', label: '인사이트', icon: '▥' },
  { key: 'my', label: 'MY', icon: '◉' },
];

const SEGMENT_LABEL_TO_API = {
  오늘: 'today',
  내일: 'tomorrow',
  이번주: 'this_week',
  지난발표: 'past',
};

const SEGMENT_API_TO_LABEL = {
  today: '오늘',
  tomorrow: '내일',
  this_week: '이번주',
  past: '지난발표',
};

const CATEGORY_LABEL_TO_API = {
  전체: 'all',
  금리: 'rate',
  물가: 'inflation',
  고용: 'employment',
  환율: 'fx',
  연설: 'speech',
};

const CATEGORY_API_TO_LABEL = {
  all: '전체',
  rate: '금리',
  inflation: '물가',
  employment: '고용',
  fx: '환율',
  speech: '연설',
};

const COUNTRY_LABEL_TO_API = {
  전체: 'all',
  미국: 'us',
  한국: 'kr',
};

const COUNTRY_API_TO_LABEL = {
  all: '전체',
  us: '미국',
  kr: '한국',
};

const PRESET_ASSET_OPTIONS = [
  { assetName: '장기채 ETF', changePercent: -1.2 },
  { assetName: '나스닥 성장주 ETF', changePercent: -0.8 },
  { assetName: '달러 인덱스 ETF', changePercent: 0.6 },
  { assetName: '금 ETF', changePercent: 1.5 },
  { assetName: '비트코인 ETF', changePercent: 3.2 },
  { assetName: '코스피 ETF', changePercent: -0.5 },
  { assetName: '원유 ETF', changePercent: 1.1 },
  { assetName: '미국 반도체 ETF', changePercent: 2.4 },
  { assetName: '국내 배당 ETF', changePercent: 0.9 },
];

function normalizeAsset(rawAsset, index) {
  const assetName = rawAsset?.assetName || rawAsset?.asset_name || `임시 자산 ${index + 1}`;
  const parsedChange = Number(rawAsset?.changePercent ?? rawAsset?.change_percent);
  const changePercent = Number.isFinite(parsedChange) ? parsedChange : 0;
  return {
    assetId: rawAsset?.assetId || rawAsset?.asset_id || `${assetName}-${index + 1}`,
    assetName,
    changePercent,
  };
}

function normalizeAssetList(rawAssets, fallbackAssets = PRESET_ASSET_OPTIONS) {
  const source = Array.isArray(rawAssets) && rawAssets.length > 0 ? rawAssets : fallbackAssets;
  return source.map((asset, idx) => normalizeAsset(asset, idx));
}

function Chip({ text, tone = 'default' }) {
  return <span className={`chip chip-${tone}`}>{text}</span>;
}

function ProgressBar({ value }) {
  return (
    <div className="progress-wrap" role="progressbar" aria-valuenow={value} aria-valuemin={0} aria-valuemax={100}>
      <div className="progress-fill" style={{ width: `${value}%` }} />
    </div>
  );
}

function SectionCard({ children, className = '' }) {
  return <section className={`section-card ${className}`}>{children}</section>;
}

function HomeTab({ data }) {
  return (
    <div className="tab-page fade-in">
      <section className="hero-panel">
        <div className="hero-top">
          <strong>PolicyLink</strong>
          <div className="hero-icons">
            <button className="icon-btn" aria-label="search">⌕</button>
            <button className="icon-btn" aria-label="alarm">◌</button>
          </div>
        </div>
        <h1>{data.greeting.headline}</h1>
        <p>{data.greeting.subtext}</p>
        <button className="pill-btn">◔ 4일 연속</button>
      </section>

      <SectionCard>
        <div className="row-between tiny-label">
          <span>{data.featuredEvent.label}</span>
          <span>{data.featuredEvent.dday}</span>
        </div>
        <h2 className="event-title">{data.featuredEvent.title}</h2>
        <div className="row-wrap">
          {(data.featuredEvent.tags || []).map((tag) => (
            <Chip key={tag} text={tag} tone={tag === '매우높음' ? 'red' : tag === '금리' ? 'blue' : 'default'} />
          ))}
        </div>
        <p className="muted">{data.featuredEvent.summary}</p>
        <div className="row-wrap mt-12">
          <button className="primary-btn">왜 중요한가</button>
          <button className="ghost-btn">◌ 알림</button>
        </div>
        <p className="tiny-note">{data.featuredEvent.meta}</p>
      </SectionCard>

      <SectionCard>
        <p className="tiny-label">오늘의 3분 학습</p>
        <h3>{data.learning.title}</h3>
        <p className="muted-sm">{data.learning.progress}%</p>
        <ProgressBar value={data.learning.progress} />
        <button className="primary-btn full">이어서 학습하기</button>
      </SectionCard>

      <SectionCard>
        <h3>내 관심자산 영향</h3>
        {data.watchImpacts.map((item) => (
          <div className="asset-row" key={item.assetName}>
            <div>
              <p className="asset-name">{item.assetName}</p>
              <p className="muted-sm">민감도 높음</p>
            </div>
            <span className={`signal-badge ${item.tone}`}>{item.signalText}</span>
          </div>
        ))}
      </SectionCard>

      <SectionCard>
        <h3>세 가지 인사이트</h3>
        <div className="insight-grid">
          {data.threeInsights.map((card) => (
            <article className={`insight-mini ${card.tone}`} key={card.title}>
              <p className="insight-title">{card.title}</p>
              <p className="insight-sub">{card.subtitle}</p>
              <p className="insight-detail">{card.detail}</p>
            </article>
          ))}
        </div>
      </SectionCard>

      <SectionCard>
        <div className="row-between">
          <h3>왜 이렇게 봤나</h3>
          <p className="muted-sm">{data.reasons.source}</p>
        </div>
        {data.reasons.items.map((reason) => (
          <div className="reason-row" key={reason.rank}>
            <div className="reason-head">
              <span className="reason-rank">{reason.rank}</span>
              <span>{reason.label}</span>
            </div>
            <span className="reason-score">+{reason.score}</span>
            <ProgressBar value={reason.score * 2} />
          </div>
        ))}
        <button className="link-btn">쉬운 설명 보기 →</button>
      </SectionCard>

      <SectionCard>
        <p className="tiny-label">어제 예측 vs 실제</p>
        <div className="review-card">
          <strong>미국 CPI</strong>
          <p className="muted">핵심 물가 둔화로 장기채 반등 가능성</p>
          <p className="result-ok">● 실제 발표 후 장기채 +1.1%, 예측 적중</p>
        </div>
        <button className="link-btn">복기하기</button>
      </SectionCard>

      <p className="disclaimer">본 앱은 정책 이벤트 학습용 시뮬레이터입니다. 실제 투자 판단을 사용하지 않습니다.</p>
    </div>
  );
}

function EventsTab({ data, segment, setSegment, category, setCategory, onToggleAlert }) {
  const firstItem = data.items[0];

  return (
    <div className="tab-page fade-in">
      <header className="plain-header">
        <h1>이벤트</h1>
      </header>

      <SectionCard>
        <p className="muted-sm">날짜 세그먼트</p>
        <div className="row-wrap">
          {data.segments.map((item) => (
            <button
              key={item}
              className={`segment-btn ${segment === item ? 'active' : ''}`}
              onClick={() => setSegment(item)}
            >
              {item}
            </button>
          ))}
        </div>

        <p className="muted-sm mt-12">카테고리</p>
        <div className="row-wrap">
          {data.categories.map((item) => (
            <button
              key={item}
              className={`segment-btn compact ${category === item ? 'active dark' : ''}`}
              onClick={() => setCategory(item)}
            >
              {item}
            </button>
          ))}
        </div>
      </SectionCard>

      {firstItem && (
        <SectionCard>
          <div className="row-between">
            <strong>{firstItem.timeText}</strong>
            <span className="status-pill">● {firstItem.statusText || '예정'}</span>
          </div>
          <h2 className="event-title">{firstItem.title}</h2>
          <div className="row-wrap">
            {(firstItem.tags || []).map((tag) => (
              <Chip key={tag} text={tag} tone={tag === '고용' ? 'blue' : 'default'} />
            ))}
            <span className="stars">{'★'.repeat(firstItem.importanceStars || 0)}{'☆'.repeat(5 - (firstItem.importanceStars || 0))}</span>
          </div>
          <p className="muted">○ {firstItem.countdownText}</p>
          <div className="row-wrap">
            {(firstItem.relatedAssets || []).map((asset) => (
              <Chip key={asset} text={asset} tone="blue-soft" />
            ))}
          </div>
          <button className="primary-btn full" onClick={() => onToggleAlert(firstItem)}>
            {firstItem.alertEnabled ? '알림 해제' : '알림 설정'}
          </button>
        </SectionCard>
      )}
    </div>
  );
}

function heatmapTone(text) {
  if (text === '매우높음') return 'tone-hot';
  if (text === '높음') return 'tone-warm';
  if (text === '보통') return 'tone-mid';
  return 'tone-low';
}

function InsightTab({ data, country, setCountry }) {
  const [view, setView] = useState('히트맵');

  return (
    <div className="tab-page fade-in">
      <header className="plain-header">
        <h1>인사이트</h1>
      </header>

      <SectionCard>
        <div className="row-wrap">
          {data.views.map((item) => (
            <button
              key={item}
              className={`segment-btn compact ${view === item ? 'active' : ''}`}
              onClick={() => setView(item)}
            >
              {item}
            </button>
          ))}
        </div>

        <div className="row-wrap mt-12">
          {data.countries.map((item) => (
            <button
              key={item}
              className={`segment-btn compact ${country === item ? 'active' : ''}`}
              onClick={() => setCountry(item)}
            >
              {item}
            </button>
          ))}
        </div>
      </SectionCard>

      <SectionCard>
        <h3>이벤트 유형별 자산 민감도</h3>
        <div className="heatmap-wrap">
          <div className="heatmap-grid header">
            <span />
            {data.columns.map((col) => (
              <span key={col} className="grid-label">{col}</span>
            ))}
          </div>
          {data.rows.map((row) => (
            <div className="heatmap-grid" key={row.eventType}>
              <span className="grid-label left">{row.eventType}</span>
              {row.cells.map((cell, idx) => (
                <span key={`${row.eventType}-${idx}`} className={`heat-cell ${heatmapTone(cell)}`}>
                  {cell}
                </span>
              ))}
            </div>
          ))}
        </div>

        <div className="legend-row">
          {['매우높음', '높음', '보통', '낮음'].map((item) => (
            <span key={item} className="legend-item">
              <i className={`legend-dot ${heatmapTone(item)}`} />
              {item}
            </span>
          ))}
        </div>
      </SectionCard>
    </div>
  );
}

function MyTab({ data, onOpenNotification, onOpenAccount }) {
  return (
    <div className="tab-page fade-in">
      <SectionCard>
        <div className="profile-row">
          <div className="avatar">{data.profile.avatarText || 'JY'}</div>
          <div>
            <h3>{data.profile.name}</h3>
            <p className="muted-sm">{data.profile.summary}</p>
          </div>
        </div>
      </SectionCard>

      <SectionCard>
        <h3>★ 내 관심자산</h3>
        {data.watchAssets.map((asset) => (
          <div className="asset-row" key={asset.assetName}>
            <p className="asset-name">{asset.assetName}</p>
            <strong className={asset.changePercent > 0 ? 'up' : 'down'}>
              {asset.changePercent > 0 ? '+' : ''}{asset.changePercent}%
            </strong>
          </div>
        ))}
        <button className="link-btn">추적 중 3개 자산</button>
      </SectionCard>

      <SectionCard>
        <h3>학습 기록</h3>
        <div className="stats-grid">
          {data.studyStats.map((s) => (
            <article key={s.label}>
              <strong>{s.value}</strong>
              <p className="muted-sm">{s.label}</p>
            </article>
          ))}
        </div>
      </SectionCard>

      <SectionCard>
        <h3>설정</h3>
        {data.settings.map((item, index) => (
          <button
            key={item.title}
            className="setting-row"
            onClick={() => {
              if (item.key === 'notification' || index === 0) onOpenNotification();
              if (item.key === 'account' || index === data.settings.length - 1) onOpenAccount();
            }}
          >
            <span className="setting-left">
              <span className="setting-icon">◌</span>
              <span>
                <strong>{item.title}</strong>
                <p className="muted-sm">{item.description}</p>
              </span>
            </span>
            <span className="chevron">›</span>
          </button>
        ))}
      </SectionCard>

      <p className="disclaimer">본 앱은 정책 이벤트 학습용 시뮬레이터입니다. 실제 투자 판단을 사용하지 않습니다.</p>
    </div>
  );
}

function AuthPanel({
  mode,
  setMode,
  username,
  setUsername,
  nickname,
  setNickname,
  password,
  setPassword,
  loading,
  error,
  onSubmit,
}) {
  return (
    <div className="tab-page fade-in auth-page">
      <section className="hero-panel">
        <div className="hero-top">
          <strong>PolicyLink</strong>
        </div>
        <h1>정책 이벤트 학습</h1>
        <p>로그인 후 개인화된 인사이트를 확인하세요.</p>
      </section>

      <SectionCard className="auth-card">
        <h2>{mode === 'login' ? '로그인' : '회원가입'}</h2>
        <form
          className="auth-form"
          onSubmit={(e) => {
            e.preventDefault();
            onSubmit();
          }}
        >
          <label>
            아이디
            <input value={username} onChange={(e) => setUsername(e.target.value)} maxLength={50} />
          </label>
          {mode === 'register' && (
            <label>
              닉네임
              <input value={nickname} onChange={(e) => setNickname(e.target.value)} maxLength={20} />
            </label>
          )}
          <label>
            비밀번호
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} maxLength={100} />
          </label>
          {error && <p className="form-error">{error}</p>}
          <button className="primary-btn full" type="submit" disabled={loading}>
            {loading ? '처리 중...' : mode === 'login' ? '로그인' : '회원가입'}
          </button>
        </form>
        <button className="link-btn auth-switch" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
          {mode === 'login' ? '계정이 없나요? 회원가입' : '이미 계정이 있나요? 로그인'}
        </button>
      </SectionCard>
    </div>
  );
}

function AccountSheet({ open, currentUser, nickname, setNickname, onClose, onSave, onLogout, loading, error }) {
  return (
    <>
      <button
        className={`sheet-overlay ${open ? 'show' : ''}`}
        onClick={onClose}
        aria-hidden={!open}
        inert={open ? undefined : ''}
        tabIndex={open ? 0 : -1}
      />
      <section className={`sheet ${open ? 'show' : ''}`} aria-hidden={!open} inert={open ? undefined : ''}>
        <div className="sheet-grabber" />
        <div className="row-between">
          <h2>계정 설정</h2>
          <button className="link-btn" onClick={onClose}>닫기</button>
        </div>
        <div className="sheet-list">
          <div className="toggle-row account-row">
            <span>아이디</span>
            <strong>{currentUser?.username || '-'}</strong>
          </div>
          <div className="account-editor">
            <label>
              닉네임
              <input value={nickname} onChange={(e) => setNickname(e.target.value)} maxLength={20} />
            </label>
            {error && <p className="form-error">{error}</p>}
            <button className="primary-btn full" onClick={onSave} disabled={loading}>
              {loading ? '저장 중...' : '닉네임 저장'}
            </button>
            <button className="ghost-btn full" onClick={onLogout}>로그아웃</button>
          </div>
        </div>
      </section>
    </>
  );
}

function NotificationSheet({ open, settings, setSettings, onClose, onSave }) {
  return (
    <>
      <button
        className={`sheet-overlay ${open ? 'show' : ''}`}
        onClick={onClose}
        aria-hidden={!open}
        inert={open ? undefined : ''}
        tabIndex={open ? 0 : -1}
      />
      <section className={`sheet ${open ? 'show' : ''}`} aria-hidden={!open} inert={open ? undefined : ''}>
        <div className="sheet-grabber" />
        <div className="row-between">
          <h2>알림 설정</h2>
          <button className="link-btn" onClick={onSave}>완료</button>
        </div>
        <div className="sheet-list">
          {[
            { key: 'before30m', label: '발표 30분 전' },
            { key: 'importantEventBriefing', label: '중요 이벤트 브리핑' },
            { key: 'learningReminder', label: '학습 리마인드' },
          ].map((item) => (
            <div className="toggle-row" key={item.key}>
              <span>{item.label}</span>
              <button
                className={`toggle ${settings[item.key] ? 'on' : ''}`}
                onClick={() => setSettings((prev) => ({ ...prev, [item.key]: !prev[item.key] }))}
              >
                <span />
              </button>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}

function AssetSelectionSheet({ open, availableAssets, selectedAssets, onClose, onSelect, onConfirm }) {
  return (
    <>
      <button
        className={`sheet-overlay ${open ? 'show' : ''}`}
        onClick={onClose}
        aria-hidden={!open}
        inert={open ? undefined : ''}
        tabIndex={open ? 0 : -1}
      />
      <section className={`sheet ${open ? 'show' : ''}`} aria-hidden={!open} inert={open ? undefined : ''}>
        <div className="sheet-grabber" />
        <div className="row-between">
          <h2>관심자산 선택</h2>
          <button className="link-btn" onClick={onClose}>닫기</button>
        </div>
        <div className="sheet-list">
          {availableAssets.map((asset) => {
            const assetKey = asset.assetId || asset.assetName;
            const isSelected = selectedAssets.some((s) => (s.assetId || s.assetName) === assetKey);
            return (
              <button
                key={assetKey}
                className={`asset-select-row ${isSelected ? 'selected' : ''}`}
                onClick={() => onSelect(asset)}
              >
                <input type="checkbox" checked={isSelected} readOnly />
                <div>
                  <p className="asset-name">{asset.assetName}</p>
                  <p className="muted-sm">{asset.changePercent > 0 ? '+' : ''}{asset.changePercent}%</p>
                </div>
              </button>
            );
          })}
        </div>
        <button className="primary-btn full" onClick={onConfirm} style={{ marginTop: '16px' }}>
          선택 완료
        </button>
      </section>
    </>
  );
}

function mapHomeApiToView(data) {
  return {
    greeting: {
      headline: data.userGreeting?.headline ?? data.user_greeting?.headline ?? pwaMockData.home.greeting.headline,
      subtext: data.userGreeting?.subtext ?? data.user_greeting?.subtext ?? pwaMockData.home.greeting.subtext,
    },
    featuredEvent: {
      label: data.featuredEvent?.label ?? data.featured_event?.label ?? pwaMockData.home.featuredEvent.label,
      dday: data.featuredEvent?.dDayText ?? data.featured_event?.d_day_text ?? pwaMockData.home.featuredEvent.dday,
      title: data.featuredEvent?.title ?? data.featured_event?.title ?? pwaMockData.home.featuredEvent.title,
      tags: data.featuredEvent?.tags ?? data.featured_event?.tags ?? ['미국', '금리', '매우높음'],
      summary: data.featuredEvent?.summary ?? data.featured_event?.summary ?? pwaMockData.home.featuredEvent.summary,
      meta: data.featuredEvent?.metaText ?? data.featured_event?.meta_text ?? pwaMockData.home.featuredEvent.meta,
    },
    learning: {
      title: data.learningCard?.title ?? data.learning_card?.title ?? pwaMockData.home.learning.title,
      progress: data.learningCard?.progressPercent ?? data.learning_card?.progress_percent ?? pwaMockData.home.learning.progress,
    },
    watchImpacts: (data.watchAssetImpacts || data.watch_asset_impacts || pwaMockData.home.watchImpacts).map((item, idx) => {
      const tones = ['tone-orange', 'tone-red', 'tone-green'];
      return {
        assetName: item.asset_name || item.assetName,
        signalText: item.signal_text || item.signalText,
        tone: tones[idx] || 'tone-orange',
      };
    }),
    threeInsights: (data.threeInsights || data.three_insights || []).map((item, idx) => {
      const tones = ['pink', 'orange', 'gray'];
      return {
        title: item.title,
        subtitle: item.subtitle,
        detail: item.detail,
        tone: tones[idx] || 'gray',
      };
    }),
    reasons: {
      source: data.reasonPanel?.sourceText ?? data.reason_panel?.source_text ?? pwaMockData.home.reasons.source,
      items: data.reasonPanel?.items ?? data.reason_panel?.items ?? pwaMockData.home.reasons.items,
    },
  };
}

function mapEventsApiToView(data) {
  return {
    segments: (data.dateSegments || data.date_segments || []).map((s) => SEGMENT_API_TO_LABEL[s] || '오늘'),
    categories: (data.categories || []).map((c) => CATEGORY_API_TO_LABEL[c] || '전체'),
    items: (data.items || []).map((item) => ({
      eventId: item.eventId ?? item.event_id,
      timeText: item.timeText ?? item.time_text,
      title: item.title,
      statusText: item.statusText ?? item.status_text,
      tags: item.tags || [],
      importanceStars: item.importanceStars ?? item.importance_stars,
      countdownText: item.countdownText ?? item.countdown_text,
      relatedAssets: item.relatedAssets ?? item.related_assets ?? [],
      alertEnabled: item.alertEnabled ?? item.alert_enabled,
    })),
  };
}

function mapHeatmapApiToView(data) {
  return {
    views: (data.view_tabs || []).map((v) => {
      if (v === 'heatmap') return '히트맵';
      if (v === 'ranking') return '랭킹';
      if (v === 'network') return '연결맵';
      return v;
    }),
    countries: (data.country_filters || []).map((c) => COUNTRY_API_TO_LABEL[c] || '전체'),
    columns: data.columns || pwaMockData.insight.columns,
    rows: (data.rows || pwaMockData.insight.rows).map((row) => ({
      eventType: row.event_type || row.eventType,
      cells: row.cells,
    })),
  };
}

function mapMeApiToView(data) {
  return {
    profile: {
      avatarText: data.profile?.avatarText ?? data.profile?.avatar_text ?? 'JY',
      name: data.profile?.name ?? pwaMockData.my.profile.name,
      summary: data.profile?.summaryText ?? data.profile?.summary_text ?? pwaMockData.my.profile.summary,
    },
    watchAssets: normalizeAssetList(data.watchAssets || data.watch_assets || pwaMockData.my.watchAssets),
    studyStats: (data.studyStats || data.study_stats || pwaMockData.my.studyStats).map((stat) => ({
      label: stat.label,
      value: stat.valueText || stat.value_text || stat.value,
    })),
    settings: (data.settingsMenu || data.settings_menu || pwaMockData.my.settings).map((setting) => ({
      key: setting.key,
      title: setting.title,
      description: setting.description,
    })),
  };
}

function App() {
  const [currentUser, setCurrentUser] = useState(null);
  const [authMode, setAuthMode] = useState('login');
  const [authUsername, setAuthUsername] = useState('');
  const [authNickname, setAuthNickname] = useState('');
  const [authPassword, setAuthPassword] = useState('');
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState('');

  const [activeTab, setActiveTab] = useState('home');
  const [sheetOpen, setSheetOpen] = useState(false);
  const [accountSheetOpen, setAccountSheetOpen] = useState(false);
  const [assetSheetOpen, setAssetSheetOpen] = useState(false);
  const [profileNickname, setProfileNickname] = useState('');
  const [profileSaving, setProfileSaving] = useState(false);
  const [profileError, setProfileError] = useState('');
  const [homeData, setHomeData] = useState(pwaMockData.home);
  const [eventsData, setEventsData] = useState({ ...pwaMockData.events, items: [{ ...pwaMockData.events.item, eventId: 101, tags: ['미국', '고용'], importanceStars: 4, alertEnabled: false, statusText: '예정' }] });
  const [insightData, setInsightData] = useState(pwaMockData.insight);
  const [myData, setMyData] = useState(pwaMockData.my);
  const [segment, setSegment] = useState('내일');
  const [category, setCategory] = useState('전체');
  const [country, setCountry] = useState('전체');
  const [notificationSettings, setNotificationSettings] = useState({
    before30m: true,
    importantEventBriefing: false,
    learningReminder: true,
  });
  const [selectedAssets, setSelectedAssets] = useState(normalizeAssetList(pwaMockData.my.watchAssets));
  const [availableAssets, setAvailableAssets] = useState(normalizeAssetList(PRESET_ASSET_OPTIONS));
  const [aiLoading, setAiLoading] = useState(false);

  useEffect(() => {
    try {
      const raw = localStorage.getItem('policy_user');
      if (!raw) return;
      const parsed = JSON.parse(raw);
      if (parsed?.userId) {
        setCurrentUser(parsed);
        if (parsed.nickname) {
          setProfileNickname(parsed.nickname);
        }
      }
    } catch (error) {
      localStorage.removeItem('policy_user');
    }
  }, []);

  useEffect(() => {
    if (!currentUser?.userId) {
      return;
    }

    async function bootstrap() {
      try {
        const [homeRes, meRes, settingsRes] = await Promise.all([
          getHome(currentUser.userId),
          getMe(currentUser.userId),
          getNotificationSettings(currentUser.userId),
        ]);

        setHomeData(mapHomeApiToView(homeRes));
        const mappedMe = mapMeApiToView(meRes);
        setMyData(mappedMe);
        setSelectedAssets(normalizeAssetList(mappedMe.watchAssets || []));
        setProfileNickname(mappedMe.profile.name || currentUser.nickname || '');
        setNotificationSettings({
          before30m: settingsRes.before30m ?? settingsRes.before_30m,
          importantEventBriefing: settingsRes.importantEventBriefing ?? settingsRes.important_event_briefing,
          learningReminder: settingsRes.learningReminder ?? settingsRes.learning_reminder,
        });

        const assetsRes = await getWatchAssetOptions();
        if (assetsRes?.assets?.length) {
          setAvailableAssets(normalizeAssetList(assetsRes.assets));
        } else {
          setAvailableAssets(normalizeAssetList(PRESET_ASSET_OPTIONS));
        }
      } catch (error) {
        setAvailableAssets(normalizeAssetList(PRESET_ASSET_OPTIONS));
        console.warn('bootstrap fallback to mock data:', error);
      }
    }

    bootstrap();
  }, [currentUser]);

  useEffect(() => {
    if (!currentUser?.userId) {
      return;
    }

    async function loadEvents() {
      try {
        const res = await getEvents(
          SEGMENT_LABEL_TO_API[segment] || 'today',
          CATEGORY_LABEL_TO_API[category] || 'all',
          currentUser.userId,
        );
        const mapped = mapEventsApiToView(res);
        if (mapped.segments.length === 0 || mapped.categories.length === 0 || mapped.items.length === 0) {
          return;
        }
        setEventsData(mapped);
      } catch (error) {
        console.warn('events fallback to mock data:', error);
      }
    }

    loadEvents();
  }, [segment, category, currentUser]);

  useEffect(() => {
    async function loadHeatmap() {
      try {
        const res = await getHeatmap(COUNTRY_LABEL_TO_API[country] || 'all');
        setInsightData(mapHeatmapApiToView(res));
      } catch (error) {
        console.warn('insight fallback to mock data:', error);
      }
    }

    loadHeatmap();
  }, [country]);

  useEffect(() => {
    if (sheetOpen || accountSheetOpen || assetSheetOpen) {
      return;
    }

    const active = document.activeElement;
    if (active instanceof HTMLElement && active.closest('.sheet')) {
      active.blur();
    }
  }, [sheetOpen, accountSheetOpen, assetSheetOpen]);

  const tabContent = useMemo(() => {
    if (activeTab === 'home') return <HomeTab data={homeData} />;
    if (activeTab === 'events') {
      return (
        <EventsTab
          data={eventsData}
          segment={segment}
          setSegment={setSegment}
          category={category}
          setCategory={setCategory}
          onToggleAlert={async (item) => {
            try {
              await updateEventAlert(item.eventId, !item.alertEnabled, currentUser?.userId);
              setEventsData((prev) => ({
                ...prev,
                items: prev.items.map((it) => (it.eventId === item.eventId ? { ...it, alertEnabled: !it.alertEnabled } : it)),
              }));
            } catch (error) {
              console.warn('failed to update event alert:', error);
            }
          }}
        />
      );
    }
    if (activeTab === 'insight') {
      return <InsightTab data={insightData} country={country} setCountry={setCountry} />;
    }
    return <MyTab data={myData} onOpenNotification={() => setSheetOpen(true)} onOpenAccount={() => setAccountSheetOpen(true)} />;
  }, [activeTab, homeData, eventsData, segment, category, insightData, country, myData, currentUser]);

  async function handleAuthSubmit() {
    setAuthError('');
    setAuthLoading(true);
    try {
      const data = authMode === 'login'
        ? await login({ username: authUsername, password: authPassword })
        : await register({ username: authUsername, nickname: authNickname, password: authPassword });

      const user = {
        userId: data.userId ?? data.user_id,
        username: data.username,
        nickname: data.nickname,
      };
      localStorage.setItem('policy_user', JSON.stringify(user));
      setCurrentUser(user);
      setProfileNickname(data.nickname || '');
      setAuthPassword('');
      setAuthError('');
    } catch (error) {
      setAuthError(error.message || '인증에 실패했습니다.');
    } finally {
      setAuthLoading(false);
    }
  }

  async function handleNicknameSave() {
    if (!currentUser?.userId) {
      return;
    }
    setProfileError('');
    setProfileSaving(true);
    try {
      const res = await updateNickname(currentUser.userId, profileNickname);
      const updatedUser = {
        userId: res.userId ?? res.user_id,
        username: res.username,
        nickname: res.nickname,
      };
      localStorage.setItem('policy_user', JSON.stringify(updatedUser));
      setCurrentUser(updatedUser);
      setMyData((prev) => ({
        ...prev,
        profile: {
          ...prev.profile,
          name: res.nickname,
        },
      }));
      setAccountSheetOpen(false);
    } catch (error) {
      setProfileError(error.message || '닉네임 저장에 실패했습니다.');
    } finally {
      setProfileSaving(false);
    }
  }

  function handleLogout() {
    localStorage.removeItem('policy_user');
    setCurrentUser(null);
    setActiveTab('home');
    setAccountSheetOpen(false);
    setAuthPassword('');
    setAuthError('');
  }

  async function handleTriggerAI() {
    setAiLoading(true);
    try {
      const result = await triggerAIEngine(currentUser?.userId);
      const [homeRes, meRes, eventsRes] = await Promise.all([
        getHome(currentUser?.userId),
        getMe(currentUser?.userId),
        getEvents(SEGMENT_LABEL_TO_API[segment] || 'today', CATEGORY_LABEL_TO_API[category] || 'all', currentUser?.userId),
      ]);

      setHomeData(mapHomeApiToView(homeRes));
      const mappedMe = mapMeApiToView(meRes);
      setMyData(mappedMe);
      setSelectedAssets(normalizeAssetList(mappedMe.watchAssets || []));
      setEventsData(mapEventsApiToView(eventsRes));
      alert(result?.message || 'AI 엔진 트리거 완료! 알림을 전송했습니다.');
    } catch (error) {
      console.warn('AI trigger failed:', error);
      alert('AI 엔진 트리거 실패. 콘솔을 확인해주세요.');
    } finally {
      setAiLoading(false);
    }
  }

  function handleSelectAssets(asset) {
    setSelectedAssets((prev) => {
      const targetKey = asset.assetId || asset.assetName;
      const isSelected = prev.some((a) => (a.assetId || a.assetName) === targetKey);
      if (isSelected) {
        return prev.filter((a) => (a.assetId || a.assetName) !== targetKey);
      } else {
        return [...prev, asset];
      }
    });
  }

  function toWatchImpacts(assets) {
    return assets.map((asset) => ({
      assetName: asset.assetName,
      signalText: asset.changePercent >= 0 ? '상승확률 68%' : '하락확률 62%',
      tone: asset.changePercent >= 0 ? 'tone-green' : 'tone-red',
    }));
  }

  async function handleUpdateAssets() {
    if (!selectedAssets.length) {
      alert('최소 1개 자산을 선택해주세요.');
      return;
    }

    // API 응답 실패와 무관하게 화면은 즉시 반영한다.
    setMyData((prev) => ({
      ...prev,
      watchAssets: selectedAssets,
    }));
    setHomeData((prev) => ({
      ...prev,
      watchImpacts: toWatchImpacts(selectedAssets),
      featuredEvent: {
        ...prev.featuredEvent,
        meta: `약 45초 · 4번의 탭 · 내 자산 ${selectedAssets.length}개 연결`,
      },
    }));
    setAssetSheetOpen(false);

    try {
      await updateWatchAssets(selectedAssets.map((asset) => asset.assetName), currentUser?.userId);
      const [homeRes, meRes] = await Promise.all([
        getHome(currentUser?.userId),
        getMe(currentUser?.userId),
      ]);
      setHomeData(mapHomeApiToView(homeRes));
      const mappedMe = mapMeApiToView(meRes);
      setMyData(mappedMe);
      setSelectedAssets(normalizeAssetList(mappedMe.watchAssets || []));
      alert('관심자산이 업데이트되었습니다.');
    } catch (error) {
      console.warn('watch assets update failed:', error);
      alert('관심자산이 로컬로 반영되었습니다. 서버 저장은 실패했습니다.');
    }
  }

  async function handleUpdateEvents() {
    try {
      const res = await refreshEvents(
        SEGMENT_LABEL_TO_API[segment] || 'today',
        CATEGORY_LABEL_TO_API[category] || 'all',
        currentUser?.userId,
      );
      setEventsData(mapEventsApiToView(res));
      alert('이벤트가 업데이트되었습니다.');
    } catch (error) {
      console.warn('event refresh failed:', error);
      alert('이벤트 업데이트 실패');
    }
  }

  if (!currentUser?.userId) {
    return (
      <div className="app-bg">
        <main className="app-shell">
          <div className="scroll-area">
            <AuthPanel
              mode={authMode}
              setMode={setAuthMode}
              username={authUsername}
              setUsername={setAuthUsername}
              nickname={authNickname}
              setNickname={setAuthNickname}
              password={authPassword}
              setPassword={setAuthPassword}
              loading={authLoading}
              error={authError}
              onSubmit={handleAuthSubmit}
            />
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="app-bg">
      <main className="app-shell">
        <div className="scroll-area">{tabContent}</div>
        <nav className="bottom-nav">
          {TABS.map((tab) => (
            <button
              key={tab.key}
              className={`nav-item ${activeTab === tab.key ? 'active' : ''}`}
              onClick={() => setActiveTab(tab.key)}
            >
              <span className="nav-icon">{tab.icon}</span>
              <span>{tab.label}</span>
            </button>
          ))}
        </nav>
      </main>
      <div className="side-btns">
        <button
          className="side-btn"
          onClick={handleTriggerAI}
          disabled={aiLoading}
          title="AI 엔진 트리거"
        >
          ⚡
        </button>
        <button
          className="side-btn"
          onClick={() => setAssetSheetOpen(true)}
          title="관심자산 선택"
        >
          ★
        </button>
        <button
          className="side-btn"
          onClick={handleUpdateEvents}
          title="이벤트 업데이트"
        >
          📅
        </button>
      </div>

      <NotificationSheet
        open={sheetOpen}
        settings={notificationSettings}
        setSettings={setNotificationSettings}
        onClose={() => setSheetOpen(false)}
        onSave={async () => {
          try {
            await updateNotificationSettings({
              before30m: notificationSettings.before30m,
              importantEventBriefing: notificationSettings.importantEventBriefing,
              learningReminder: notificationSettings.learningReminder,
            }, currentUser?.userId);
          } catch (error) {
            console.warn('failed to save notification settings:', error);
          } finally {
            setSheetOpen(false);
          }
        }}
      />

      <AccountSheet
        open={accountSheetOpen}
        currentUser={currentUser}
        nickname={profileNickname}
        setNickname={setProfileNickname}
        onClose={() => setAccountSheetOpen(false)}
        onSave={handleNicknameSave}
        onLogout={handleLogout}
        loading={profileSaving}
        error={profileError}
      />

      <AssetSelectionSheet
        open={assetSheetOpen}
        availableAssets={availableAssets}
        selectedAssets={selectedAssets}
        onClose={() => setAssetSheetOpen(false)}
        onSelect={handleSelectAssets}
        onConfirm={handleUpdateAssets}
      />
    </div>
  );
}

export default App;

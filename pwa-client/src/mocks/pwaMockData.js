export const pwaMockData = {
  home: {
    greeting: {
      headline: '안녕하세요, 지웅님',
      subtext: '오늘 정책 이벤트와 연결된 자산 흐름을 빠르게 확인해보세요.',
    },
    featuredEvent: {
      label: '오늘의 핵심 이벤트',
      dday: 'D-0 02:15',
      title: 'FOMC 금리결정',
      summary: '예상 5.25% · 발표 전',
      meta: '약 45초 · 4번의 탭 · 내 자산 1개 연결',
    },
    learning: {
      title: '금리 인상 시 장기채가 흔들리는 이유',
      progress: 64,
    },
    watchImpacts: [
      { assetName: '장기채 ETF', signalText: '변동성↑ 74%', tone: 'tone-orange' },
      { assetName: '나스닥 성장주 ETF', signalText: '하락확률 62%', tone: 'tone-red' },
      { assetName: '달러 인덱스 ETF', signalText: '상승확률 68%', tone: 'tone-green' },
    ],
    threeInsights: [
      { title: '62%', subtitle: '방향성', detail: '성장주 하락', tone: 'pink' },
      { title: '74%', subtitle: '변동성', detail: '장기채 확대', tone: 'orange' },
      { title: '중간', subtitle: '신뢰도', detail: '유사 18건', tone: 'gray' },
    ],
    reasons: {
      source: 'Federal Reserve · 오늘 18:45 갱신',
      items: [
        { rank: 1, label: '점도표 상향 가능성', score: 40 },
        { rank: 2, label: '서비스 물가 끈적임', score: 28 },
        { rank: 3, label: '고용 탄탄함', score: 18 },
      ],
    },
  },
  events: {
    segments: ['오늘', '내일', '이번주', '지난발표'],
    categories: ['전체', '금리', '물가', '고용', '환율', '연설'],
    item: {
      timeText: '21:30',
      title: '미국 비농업고용',
      countdownText: '발표까지 23시간 10분',
      relatedAssets: ['달러 인덱스 ETF', '나스닥 성장주 ETF'],
    },
  },
  insight: {
    views: ['히트맵', '랭킹', '연결맵'],
    countries: ['전체', '미국', '한국'],
    columns: ['주식', '장기채', '달러', '금'],
    rows: [
      { eventType: '금리', cells: ['높음', '매우높음', '높음', '보통'] },
      { eventType: '물가', cells: ['보통', '높음', '보통', '높음'] },
      { eventType: '고용', cells: ['보통', '보통', '높음', '낮음'] },
      { eventType: '환율', cells: ['보통', '낮음', '매우높음', '보통'] },
      { eventType: '정책연설', cells: ['보통', '보통', '높음', '낮음'] },
    ],
  },
  my: {
    profile: {
      name: '지웅',
      summary: '이번 주 학습 6회 · 퀴즈 정답률 82%',
    },
    watchAssets: [
      { assetName: '장기채 ETF', changePercent: -1.2 },
      { assetName: '나스닥 성장주 ETF', changePercent: -0.8 },
      { assetName: '달러 인덱스 ETF', changePercent: 0.6 },
    ],
    studyStats: [
      { label: '이번 주 학습', value: '6회' },
      { label: '퀴즈 정답률', value: '82%' },
      { label: '가장 약한 단원', value: '환율' },
    ],
    settings: [
      { title: '알림 설정', description: '발표 직전, 브리핑 거점, 학습 퀴즈' },
      { title: '쉬운 설명 기본값', description: '켜짐' },
      { title: '색상 모드', description: '한국식 (빨강=상승)' },
      { title: '시장/국가 선호', description: '미국, 한국' },
      { title: '데이터 출처/모델 투명성', description: '출처, 모델 버전 확인' },
      { title: '계정 설정', description: '보안, 닉네임, 로그아웃' },
    ],
  },
};

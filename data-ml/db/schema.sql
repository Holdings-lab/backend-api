CREATE TABLE IF NOT EXISTS policy_documents (
    id BIGSERIAL PRIMARY KEY,
    source TEXT NOT NULL,
    category TEXT NOT NULL,
    doc_type TEXT NOT NULL,
    published_date DATE,
    release_date TEXT,
    title TEXT,
    url TEXT NOT NULL UNIQUE,
    body TEXT,
    matched_keyword_groups TEXT,
    matched_keywords TEXT,
    collected_at TIMESTAMPTZ,
    raw_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS policy_document_features (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL UNIQUE REFERENCES policy_documents(id) ON DELETE CASCADE,
    body_summary TEXT,
    title_ko TEXT,
    body_summary_ko TEXT,
    body_original_length INTEGER,
    title_positive_prob DOUBLE PRECISION,
    title_negative_prob DOUBLE PRECISION,
    title_neutral_prob DOUBLE PRECISION,
    title_sentiment_score DOUBLE PRECISION,
    body_positive_prob DOUBLE PRECISION,
    body_negative_prob DOUBLE PRECISION,
    body_neutral_prob DOUBLE PRECISION,
    body_sentiment_score DOUBLE PRECISION,
    body_n_chunks DOUBLE PRECISION,
    body_summary_embedding JSONB,
    feature_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS crawler_run_logs (
    id BIGSERIAL PRIMARY KEY,
    run_type TEXT NOT NULL,
    status TEXT NOT NULL,
    counts JSONB NOT NULL DEFAULT '{}'::jsonb,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS policy_prediction_runs (
    id BIGSERIAL PRIMARY KEY,
    model_version TEXT,
    model_target TEXT,
    best_horizon_days INTEGER,
    best_threshold DOUBLE PRECISION,
    policy_score DOUBLE PRECISION,
    direction_accuracy DOUBLE PRECISION,
    top_label TEXT,
    top_label_probability DOUBLE PRECISION,
    summary_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS article_insights (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL UNIQUE REFERENCES policy_documents(id) ON DELETE CASCADE,
    insight_date DATE NOT NULL,
    summary TEXT NOT NULL,
    keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    asset_impacts JSONB NOT NULL DEFAULT '[]'::jsonb,
    llm_provider TEXT NOT NULL,
    llm_model TEXT NOT NULL,
    prompt_version TEXT NOT NULL,
    insight_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS article_insights_insight_date_idx
    ON article_insights(insight_date DESC, id DESC);

CREATE TABLE IF NOT EXISTS home_briefings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    briefing_date DATE NOT NULL,
    briefing_headline TEXT NOT NULL,
    briefing_paragraphs JSONB NOT NULL DEFAULT '[]'::jsonb,
    push_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    llm_provider TEXT NOT NULL,
    llm_model TEXT NOT NULL,
    prompt_version TEXT NOT NULL,
    briefing_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, briefing_date)
);

CREATE INDEX IF NOT EXISTS home_briefings_user_date_idx
    ON home_briefings(user_id, briefing_date DESC, id DESC);

CREATE TABLE IF NOT EXISTS pca_artifacts (
    id BIGSERIAL PRIMARY KEY,
    artifact_name TEXT NOT NULL UNIQUE,
    artifact_path TEXT,
    feature_name TEXT,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sector_daily_summaries (
    id BIGSERIAL PRIMARY KEY,
    sector TEXT NOT NULL,
    release_date DATE NOT NULL,
    window_days INTEGER NOT NULL DEFAULT 1,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    image_url TEXT,
    source_count INTEGER NOT NULL DEFAULT 0,
    llm_provider TEXT,
    llm_model TEXT,
    summary_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (sector, release_date, window_days)
);

CREATE INDEX IF NOT EXISTS sector_daily_summaries_date_idx
    ON sector_daily_summaries(release_date DESC, sector);

CREATE TABLE IF NOT EXISTS sector_ai_briefings (
    id BIGSERIAL PRIMARY KEY,
    sector TEXT NOT NULL,
    as_of_date DATE NOT NULL,
    horizon_days INTEGER NOT NULL DEFAULT 0,
    title TEXT NOT NULL,
    headline TEXT NOT NULL,
    reason TEXT NOT NULL,
    alignment TEXT,
    used_news_urls JSONB NOT NULL DEFAULT '[]'::jsonb,
    disclaimer TEXT,
    llm_provider TEXT,
    llm_model TEXT,
    briefing_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (sector, as_of_date, horizon_days)
);

CREATE INDEX IF NOT EXISTS sector_ai_briefings_date_idx
    ON sector_ai_briefings(as_of_date DESC, sector);

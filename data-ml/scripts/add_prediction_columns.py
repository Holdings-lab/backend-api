import psycopg2
from db.db import _db_config
conf=_db_config()
print('connecting to', conf)
conn=psycopg2.connect(**conf)
cur=conn.cursor()
stmt='''
ALTER TABLE policy_prediction_runs
    ADD COLUMN IF NOT EXISTS model_version TEXT,
    ADD COLUMN IF NOT EXISTS model_target TEXT,
    ADD COLUMN IF NOT EXISTS best_horizon_days INTEGER,
    ADD COLUMN IF NOT EXISTS best_threshold DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS policy_score DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS direction_accuracy DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS top_label TEXT,
    ADD COLUMN IF NOT EXISTS top_label_probability DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS summary_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS metadata_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
'''
cur.execute(stmt)
conn.commit()
print('ALTER policy_prediction_runs executed')
cur.close()
conn.close()

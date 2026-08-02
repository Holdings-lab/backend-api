from __future__ import annotations

from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DATA_DIR = PROJECT_ROOT / "data"
CRAWLER_DATA_DIR = DATA_DIR / "crawler"
COLLECTED_DIR = CRAWLER_DATA_DIR / "collected"
SUMMARIZED_DIR = CRAWLER_DATA_DIR / "summarized"
FEATURES_DIR = CRAWLER_DATA_DIR / "features"


def _ensure_dir(path: Path) -> Path:
    path.mkdir(parents=True, exist_ok=True)
    return path


def ensure_data_dir() -> Path:
    return _ensure_dir(DATA_DIR)


def collected_csv_path(filename: str) -> str:
    """
    수집기(raw collector output)가 저장되는 위치.
    """
    return str(_ensure_dir(COLLECTED_DIR) / filename)


def summarized_csv_path(filename: str) -> str:
    """
    요약이 끝난 크롤링 산출물이 저장되는 위치.
    """
    return str(_ensure_dir(SUMMARIZED_DIR) / filename)


def feature_csv_path(filename: str) -> str:
    """
    병합, 피처 엔지니어링, 감성 점수화가 끝난 학습용 CSV가 저장되는 위치.
    """
    return str(_ensure_dir(FEATURES_DIR) / filename)

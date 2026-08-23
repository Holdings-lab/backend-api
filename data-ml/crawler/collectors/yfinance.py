import csv
import time
import re
import sys
from datetime import datetime, timedelta
from pathlib import Path
import requests
from bs4 import BeautifulSoup
from zoneinfo import ZoneInfo

PROJECT_ROOT = Path(__file__).resolve().parents[2]
PROJECT_ROOT_STR = str(PROJECT_ROOT)

if PROJECT_ROOT_STR not in sys.path:
    sys.path.insert(0, PROJECT_ROOT_STR)

from crawler.support_legacy.data_paths import collected_csv_path

# 미국 뉴욕 시간대 고정 (서머타임 자동 계산)
NY_TZ = ZoneInfo('America/New_York')
# 기본 타겟 날짜: 뉴욕 시간 기준 어제 (YYYY-MM-DD)
TARGET_DATE = (datetime.now(NY_TZ) - timedelta(days=1)).strftime("%Y-%m-%d")
TICKERS = ["QQQ", "XLF", "XLE"]

def _save_results(records, target_date):
    csv_path = Path(collected_csv_path(f"yahoo_market_news_{target_date}.csv"))

    with csv_path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(
            f, fieldnames=["sector", "title", "release_date", "url", "body", "thumbnail_url"]
        )
        writer.writeheader()
        writer.writerows(records)

    return csv_path

def _extract_thumbnail_from_api(content: dict) -> str:
    """Yahoo news v2 응답에서 썸네일 URL을 꺼낸다. 없거나 형식이 달라도 빈 문자열."""
    try:
        thumbnail = content.get("thumbnail")
        if isinstance(thumbnail, str) and thumbnail.startswith("http"):
            return thumbnail
        if not isinstance(thumbnail, dict):
            return ""

        original = thumbnail.get("originalUrl") or thumbnail.get("original")
        if isinstance(original, str) and original.startswith("http"):
            return original

        resolutions = thumbnail.get("resolutions") or []
        if not isinstance(resolutions, list) or not resolutions:
            return ""

        def _width(item: dict) -> int:
            try:
                return int(item.get("width") or 0)
            except (TypeError, ValueError):
                return 0

        best = max(
            (item for item in resolutions if isinstance(item, dict)),
            key=_width,
            default=None,
        )
        url = (best or {}).get("url")
        if isinstance(url, str) and url.startswith("http"):
            return url
    except Exception as error:
        title = (content or {}).get("title") or ""
        print(f"[-] API 썸네일 추출 실패 ({title[:28]}...): {error}")
    return ""


def _extract_og_image(soup: BeautifulSoup) -> str:
    """기사 HTML의 og:image를 폴백으로 사용한다. 실패해도 빈 문자열."""
    try:
        tag = soup.find("meta", property="og:image") or soup.find("meta", attrs={"name": "og:image"})
        if not tag:
            return ""
        image_url = (tag.get("content") or "").strip()
        if image_url.startswith("http"):
            return image_url
    except Exception as error:
        print(f"[-] og:image 추출 실패: {error}")
    return ""


def convert_to_et_date(utc_time_str: str) -> str:
    """
    UTC 시간 문자열을 받아서 미국 동부 시간(ET)으로 변환 후 YYYY-MM-DD 형식으로 반환
    """
    if not utc_time_str:
        return "N/A"
    
    try:
        utc_time_str = utc_time_str.replace('Z', '+00:00')

        dt_utc = datetime.fromisoformat(utc_time_str)
        dt_et = dt_utc.astimezone(ZoneInfo("America/New_York"))
        
        return dt_et.strftime("%Y-%m-%d")

    except Exception as e:
        print(f"[-] 날짜 타임존 변환 실패: {e}")
        return "N/A"

def scrape_news_sync(ticker, target_date=TARGET_DATE):
    # 1. API 엔드포인트 URL 설정
    api_url = "https://apidojo-yahoo-finance-v1.p.rapidapi.com/news/v2/list"

    # 2. 헤더 설정 (본문 추출을 위한 헤더) 
    yahoo_headers = {
        "x-rapidapi-key": "e7d6fe9de4msh8ae2ecfc141b9b5p1c3136jsna6f5a8efe5b5",
        "x-rapidapi-host": "apidojo-yahoo-finance-v1.p.rapidapi.com",
        "Content-Type": "application/json"
    }

    
    request_headers = {
        "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/125.0.0.0 Safari/537.36"
        ),
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9",
    }

    # 2. 파라미터 및 바디 설정
    # s: 뉴스 검색 키워드 (예: QQQ, XLF, XLE 등)
    # snippetCount: 뉴스 개수 (최대 20)
    querystring = {"s": ticker, "region": "US", "snippetCount": "20"}

    # 3. API 요청 보내기 (빈 바디라도 post 형식이면 빈 딕셔너리를 json으로 던져주는 것이 좋습니다)
    response = requests.post(api_url, headers=yahoo_headers, params=querystring, json={})

    save_results = []

    # 4. 응답 결과 정상 여부 확인 및 데이터 추출
    if response.status_code == 200:
        response_json = response.json()
        
        news_list = response_json.get('data', {}).get('main', {}).get('stream', [])
        
        for news in news_list:
            try:
                content = news.get('content', {}) or {}
                finance = content.get('finance', {}) or {}
                
                # premium 여부 (premium 이면 본문 추출이 불가하므로 PASS)
                premium_info = finance.get('premiumFinance', {}) or {}
                is_premium = premium_info.get('isPremiumNews', True)

                if(is_premium):
                    print(f"[!] 프리미엄 뉴스 패스: {str(content.get('title') or '')[:28]}...")
                    continue
                    
                release_date = content.get('pubDate')
                if release_date:
                    release_date = convert_to_et_date(release_date)
                    if release_date < target_date:
                        print(f"[!] 과거 기사 발견 ({release_date}), {ticker} 수집 조기 종료.")
                        break

                click_through = content.get('clickThroughUrl') or {}
                body_url = click_through.get('url') if isinstance(click_through, dict) else None
                thumbnail_url = _extract_thumbnail_from_api(content)

                body_text = ""
                if body_url:
                    try:
                        article_response = requests.get(body_url, headers=request_headers, timeout=20)
                        article_response.raise_for_status()
                        soup = BeautifulSoup(article_response.text, "html.parser")
                        body_tag = soup.find("div", class_="bodyItems-wrapper")
                        body_text = body_tag.get_text(" ", strip=True) if body_tag else ""
                        if not thumbnail_url:
                            thumbnail_url = _extract_og_image(soup)
                    except Exception as error:
                        print(f"[-] 본문/og:image 수집 실패 ({str(content.get('title') or '')[:28]}...): {error}")
                elif not thumbnail_url:
                    print(f"[!] 썸네일/본문 URL 없음: {str(content.get('title') or '')[:28]}...")

                save_results.append({
                    "sector": ticker,
                    "title": content.get('title'),
                    "release_date": release_date,
                    "url": body_url,
                    "body": body_text,
                    "thumbnail_url": thumbnail_url or "",
                })
            except Exception as error:
                print(f"[-] 뉴스 항목 처리 실패, 건너뜀: {error}")
                continue
        return save_results
    else:
        print(f"API 요청 실패 (Error Code: {response.status_code})")
        print(response.text)

def main():
    news_results = scrape_news_sync(ticker="QQQ")

    if news_results:
        target_date = news_results[0]["release_date"] if news_results[0].get("release_date") else TARGET_DATE
        output_csv_path = _save_results(news_results, target_date)
        print(
            f"\n[✔] 배치가 완벽히 완료되었습니다. 총 {len(news_results)}개 적재 완료"
            f"\n    - CSV : {output_csv_path}"
        )
    else:
        print("\n[!] 저장할 데이터가 없어 출력 파일 생성을 건너뜁니다.")

if __name__ == "__main__":
    main()
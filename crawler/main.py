import requests
from bs4 import BeautifulSoup
import time
import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore

if not firebase_admin._apps:
    cred = credentials.Certificate("serviceAccountKey.json")
    firebase_admin.initialize_app(cred)

db = firestore.client()

TARGET_URL = "https://quasarzone.com/bbs/qb_tsy"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}


# DB에서 키워드 및 구독자 정보 가져오기 (다중 유저 대응)
def get_keywords_info():
    print("DB에서 구독 정보(키워드+구독자)를 가져오기")
    keyword_map = {}

    docs = db.collection('keywords').stream()

    for doc in docs:
        keyword = doc.id  # 문서 ID 자체를 검색어로 둠 (예: "5070")
        data = doc.to_dict()
        subscribers = data.get('subscribers', [])  # 구독자 리스트 가져오기

        if subscribers:  # 구독자가 한 명이라도 있을 때만 검색
            keyword_map[keyword] = subscribers

    return keyword_map


# 크롤링
def check_new_deals(keyword_map):
    target_keywords = list(keyword_map.keys())

    if not target_keywords:
        print("등록된 키워드(구독자 포함)가 없습니다.")
        return []

    print(f"🔍 검색 대상 키워드: {target_keywords}")

    found_items = []

    try:
        response = requests.get(TARGET_URL, headers=HEADERS)
        if response.status_code != 200:
            print(f"접속 실패: {response.status_code}")
            return []

        soup = BeautifulSoup(response.text, 'html.parser')
        rows = soup.select(".market-info-list tr")

        for row in rows:
            title_tag = row.select_one(".tit .subject-link")
            if not title_tag: continue

            title = title_tag.get_text(strip=True)
            link = "https://quasarzone.com" + title_tag['href']

            status_tag = row.select_one(".label")
            status = status_tag.get_text(strip=True) if status_tag else ""

            if "종료" in status or "완료" in status:
                continue

            # 키워드 매칭 확인
            for keyword, subscribers in keyword_map.items():
                if keyword in title:

                    item = {
                        "keyword": keyword,
                        "title": title,
                        "link": link,
                        "status": status,
                        "subscribers_to_notify": subscribers
                    }
                    found_items.append(item)

        return found_items

    except Exception as e:
        print(f"에러 발생: {e}")
        return []


if __name__ == "__main__":
    while True:
        keyword_map = get_keywords_info()

        # 크롤링 수행
        results = check_new_deals(keyword_map)

        # 3. 결과 처리
        if results:
            print(f"총 {len(results)}건의 제품 발견")
            for item in results:
                print(f"--- 발견된 키워드: [{item['keyword']}] ---")
                print(f"제목: {item['title']}")
                print(f"알림 보낼 대상: {item['subscribers_to_notify']}")
                # todo: FCM 전송 코드
                print(f"링크: {item['link']}")
        else:
            print("조건에 맞는 새로운 제품이 없습니다.")

        print("\n60초 뒤 다시 검색")
        time.sleep(60)
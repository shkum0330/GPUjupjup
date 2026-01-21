import requests
from bs4 import BeautifulSoup
import time
import firebase_admin
from firebase_admin import credentials, firestore, messaging
from datetime import datetime

# firebase 설정
if not firebase_admin._apps:
    cred = credentials.Certificate("serviceAccountKey.json")
    firebase_admin.initialize_app(cred)

db = firestore.client()

TARGET_URL = "https://quasarzone.com/bbs/qb_tsy"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}

def get_post_id(link):
    return link.split('/')[-1].split('?')[0]


def is_already_sent(post_id):
    # DB에 이미 보낸 기록이 있는지 확인
    doc_ref = db.collection('sent_logs').document(post_id)
    doc = doc_ref.get()
    return doc.exists


def mark_as_sent(post_id, title):
    # 보낸 기록을 DB에 저장
    try:
        db.collection('sent_logs').document(post_id).set({
            "title": title,
            "sent_at": datetime.now()
        })
    except Exception as e:
        print(f"   ㄴ ⚠️ DB 저장 실패: {e}")


def send_fcm_notification(tokens, title, body, link):
    # FCM 알림 발송 함수 (키워드가 여러 개 겹쳐도 한 번만 호출됨)
    if not tokens:
        return

    # 토큰이 500개가 넘어가면 끊어서 보내야 하지만, 현재 규모에선 패스
    try:
        message = messaging.MulticastMessage(
            notification=messaging.Notification(
                title=title,
                body=body
            ),
            data={
                "url": link,
            },
            tokens=tokens
        )

        response = messaging.send_multicast(message)
        print(f"   ㄴ 🚀 알림 발송 완료! (성공: {response.success_count}건)")

    except Exception as e:
        print(f"   ㄴ ❌ 알림 발송 실패: {e}")


def get_keywords_info():
    keyword_map = {}
    docs = db.collection('keywords').stream()
    for doc in docs:
        data = doc.to_dict()
        subscribers = data.get('subscribers', [])
        if subscribers:
            keyword_map[doc.id] = subscribers
    return keyword_map


def check_new_deals(keyword_map):
    target_keywords = list(keyword_map.keys())
    if not target_keywords:
        print("❌ 등록된 키워드가 없습니다.")
        return

    print(f"🔍 검색 키워드: {target_keywords}")

    try:
        response = requests.get(TARGET_URL, headers=HEADERS)
        if response.status_code != 200:
            print(f"접속 실패: {response.status_code}")
            return

        soup = BeautifulSoup(response.text, 'html.parser')
        rows = soup.select(".market-info-list tr")

        # 상위 10개만 탐색
        for row in rows[:10]:
            title_tag = row.select_one(".tit .subject-link")
            if not title_tag: continue

            title = title_tag.get_text(strip=True)
            link = "https://quasarzone.com" + title_tag['href']

            post_id = get_post_id(link)

            # 이미 보낸 글이면 패스
            if is_already_sent(post_id):
                continue

            # 판매 종료/완료 확인
            status_tag = row.select_one(".label")
            status = status_tag.get_text(strip=True) if status_tag else ""
            if "종료" in status or "완료" in status:
                continue

            # 중복 알림 방지
            # 이 게시글에 대해 알림을 받아야 할 모든 사람의 토큰을 먼저 수집
            target_tokens = set()
            matched_keywords = []

            for keyword, subscribers in keyword_map.items():
                if keyword in title:
                    matched_keywords.append(keyword)
                    target_tokens.update(subscribers)

            # 매칭된 키워드가 하나라도 있다면 알림 발송
            if matched_keywords:
                print(f"\n🔥 [신규 발견] {title}")
                print(f"   ㄴ 매칭 키워드: {matched_keywords}")


                keywords_str = ", ".join(matched_keywords)
                noti_title = f"키워드 발견! [{keywords_str}]"

                send_fcm_notification(list(target_tokens), noti_title, title, link)

                mark_as_sent(post_id, title)

    except Exception as e:
        print(f"에러 발생: {e}")


if __name__ == "__main__":
    print("크롤러 가동")

    while True:
        keyword_map = get_keywords_info()
        check_new_deals(keyword_map)

        print("\n60초 뒤 다시 검색합니다...")
        time.sleep(60)
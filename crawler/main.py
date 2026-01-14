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
    """URL에서 게시글의 고유 번호 추출"""
    return link.split('/')[-1]


def is_already_sent(post_id):
    """DB에 이미 보낸 기록이 있는지 확인"""
    doc_ref = db.collection('sent_logs').document(post_id)
    doc = doc_ref.get()
    return doc.exists


def mark_as_sent(post_id, title):
    """보낸 기록을 DB에 저장"""
    try:
        db.collection('sent_logs').document(post_id).set({
            "title": title,
            "sent_at": datetime.now()  # 언제 보냈는지 기록
        })
    except Exception as e:
        print(f"   ㄴ DB 저장 실패: {e}")


# --- 3. 알림 발송 함수 ---
def send_fcm_notification(tokens, keyword, title, link):
    if not tokens:
        return

    try:
        # MulticastMessage: 한 번에 여러 기기로 발송
        message = messaging.MulticastMessage(
            notification=messaging.Notification(
                title=f"'{keyword}' 발견!",
                body=title
            ),
            data={
                "url": link,
                "keyword": keyword
            },
            tokens=tokens
        )

        response = messaging.send_multicast(message)
        print(f"알림 발송 완료(성공: {response.success_count}건)")

    except Exception as e:
        print(f"알림 발송 실패: {e}")


# DB 정보 가져오기
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

    print(f"검색 중: {target_keywords}")

    try:
        response = requests.get(TARGET_URL, headers=HEADERS)
        if response.status_code != 200:
            print(f"접속 실패: {response.status_code}")
            return

        soup = BeautifulSoup(response.text, 'html.parser')
        rows = soup.select(".market-info-list tr")

        # 중복 방지가 있으므로 넉넉히 봐도 됨
        for row in rows[:10]:
            title_tag = row.select_one(".tit .subject-link")
            if not title_tag: continue

            title = title_tag.get_text(strip=True)
            link = "https://quasarzone.com" + title_tag['href']

            # 게시글 ID 추출
            post_id = get_post_id(link)

            # 이미 보낸 글인지 DB에서 확인
            if is_already_sent(post_id):
                continue

            # 판매 상태 확인
            status_tag = row.select_one(".label")
            status = status_tag.get_text(strip=True) if status_tag else ""
            if "종료" in status or "완료" in status:
                continue

            # 키워드 매칭 확인
            matched = False
            for keyword, subscribers in keyword_map.items():
                if keyword in title:
                    print(f"\n🔥 [신규 발견] {title}")
                    # 알림 발송
                    send_fcm_notification(subscribers, keyword, title, link)
                    matched = True

            # 알림을 보냈든 안 보냈든, 이 글은 '확인한 글'로 처리하여 다음 턴에 다시 알림이 가지 않도록 저장함
            if matched:
                mark_as_sent(post_id, title)

    except Exception as e:
        print(f"에러 발생: {e}")


if __name__ == "__main__":
    print("줍줍 크롤러 가동")

    while True:
        keyword_map = get_keywords_info()
        check_new_deals(keyword_map)

        print("\n60초 뒤 다시 검색합니다...")
        time.sleep(60)
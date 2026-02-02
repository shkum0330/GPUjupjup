# 🛒 줍줍

- 퀘이사존의 타세요 게시판을 실시간으로 모니터링하여, 사용자가 등록한 키워드(예: 5090 등)가 포함된 게시글이 올라오면 앱으로 푸시 알림을 보내주는 안드로이드 앱

## ✨ 주요 기능

* **실시간 크롤링**: 파이썬 크롤러가 1분 간격으로 대상 사이트의 새 글을 확인합
* **키워드 알림**: 사용자가 앱에서 설정한 키워드가 제목에 포함된 경우에만 알림 발송
* **푸시 알림**: FCM을 통해 즉각적인 모바일 알림을 제공
* **중복 방지**: 이미 알림을 보낸 게시글이나, '종료/완료'된 게시글은 필터링

## 🛠 Tech Stack

### Client (Android)

* **Language**: Kotlin
* **UI Framework**: Jetpack Compose
* **Build System**: Gradle (Kotlin DSL)
* **Libraries**
  * Firebase Analytics / Firestore / Messaging
  * AndroidX Core / Lifecycle / Activity



### Server (Crawler)

* **Language**: Python 3
* **Libraries**
  * `requests`, `beautifulsoup4` (크롤링)
  * `firebase-admin` (DB 연동 및 푸시 발송)



### Database & Infrastructure

* **Database**: Google Cloud Firestore (NoSQL)
* **Notification**: Firebase Cloud Messaging



## 📱 앱 사용법

1. 앱을 실행하면 자동으로 FCM 토큰이 발급됩니다.
2. **줍줍 설정** 화면에서 원하는 키워드(예: '5080' 등)를 입력하고 **등록** 버튼을 누릅니다.
3. 등록된 키워드는 하단 리스트에서 확인할 수 있으며, 휴지통 아이콘을 눌러 삭제할 수 있습니다.
4. 크롤러가 실행 중일 때, 해당 키워드가 포함된 새 글이 올라오면 알림이 도착합니다.
5. 알림을 클릭하면 해당 게시글로 바로 이동합니다.

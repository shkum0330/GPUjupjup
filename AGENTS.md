## 프로젝트 개요
**GPUjupjup (줍줍)**은 퀘이사존의 타세요 게시판을 실시간으로 크롤링(1분 간격)하여, 사용자가 등록한 키워드가 포함된 게시글이 올라오면 앱으로 푸시 알림을 보내주는 서비스입니다.

## 프로젝트 구조
이 저장소는 두 개의 주요 컴포넌트로 나뉘어져 있습니다.
- `/client`: 안드로이드 클라이언트 앱
- `/crawler`: 파이썬 기반 서버 크롤러

## 개발 환경 및 기술 스택
### Client (Android)
- **언어**: Kotlin (JVM Target 11)
- **UI 프레임워크**: Jetpack Compose (Material 3)
- **빌드 시스템**: Gradle (Kotlin DSL 사용)
- **SDK 버전**: Min SDK 24, Compile/Target SDK 36
- **주요 라이브러리**: Firebase (Analytics, Firestore, Messaging)

### Server (Crawler)
- **언어**: Python 3
- **주요 모듈**: `requests`, `beautifulsoup4` (크롤링), `firebase-admin` (DB 연동 및 푸시)
- **인프라**: Google Cloud Firestore (NoSQL), Firebase Cloud Messaging

## 명령어 (Commands)
### Android Client
- 해당 프로젝트는 `/client` 폴더 내에 Gradle Wrapper가 포함되어 있습니다.
- **프로젝트 빌드**: `cd client && ./gradlew assembleDebug`
- **테스트 실행**: `cd client && ./gradlew test`

### Python Crawler
- **권장 Python 버전**: Python 3.11
- **가상환경 및 의존성 설치**: `cd crawler && python -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt`
- **크롤러 실행**: `cd crawler && python main.py`

## 사용자 확인 필수 (절대 임의 변경 금지)
- 아래 항목은 수정이 필요할 경우 반드시 사용자에게 먼저 확인을 요청하세요.
- Firestore 컬렉션/필드 구조: `keywords`, `sent_logs`, `subscribers`
- `keywords` 문서 ID를 키워드 문자열로 사용하는 규칙
- 크롤러 루프의 60초 대기 로직: `time.sleep(60)`

## 크롤러 요청 정책
- 대상 사이트 요청에는 타임아웃을 반드시 설정합니다. (권장: 10~15초)
- 일시적 네트워크 실패 시에만 제한된 재시도(권장 최대 3회)와 점진적 백오프를 사용합니다.
- 서비스 식별 가능한 `User-Agent`를 유지하고, 과도한 병렬 요청/고빈도 재시도를 금지합니다.

## 코딩 컨벤션 및 구현 가이드
- **Kotlin/Android**:
  - `KOTLIN_OFFICIAL` 코드 스타일을 철저히 준수합니다.
  - 기존의 XML 기반 레이아웃을 배제하고, 모든 UI 컴포넌트는 **Jetpack Compose**만을 사용하여 구현합니다.
  - Compose Preview 사용 시, Preview 어노테이션은 최상위 함수(Top-Level Function)에만 사용해야 하는 등 IDE Inspection 규칙을 위반하지 않도록 유의합니다.
- **데이터베이스 (Firestore)**:
  - `keywords` 컬렉션은 문서 ID가 사용자가 등록한 '키워드' 자체이며, 내부 `subscribers` 필드(배열)에 FCM 토큰을 저장하는 구조로 되어있습니다.
  - `sent_logs` 컬렉션은 푸시 알림 중복 발송을 방지하기 위해 발송 기록을 남기는 용도입니다.
  - 이 데이터베이스 구조나 필드명(`subscribers`, `sent_logs` 등)을 임의로 변경할 경우 클라이언트와 서버 양측이 모두 고장 날 수 있으므로, 수정이 필요할 경우 반드시 사용자에게 먼저 확인을 요청하세요.

## 푸시 중복 방지 기준 (`sent_logs`)
- 중복 판단 키는 최소 `keyword + post_id` 조합을 포함해야 합니다.
- 게시글 URL 또는 게시 시각 등 보조 식별자를 추가할 수 있으나, 기존 중복 판정 규칙 변경 시 반드시 사용자 확인 후 진행합니다.
- 발송 성공/실패 로그 포맷은 운영 추적이 가능하도록 일관되게 유지합니다.

## 변경 완료 기준 (Definition of Done)
- **Client 변경 시**
  - `cd client && ./gradlew assembleDebug` 성공
  - `cd client && ./gradlew test` 성공 (가능한 범위 내)
- **Crawler 변경 시**
  - `cd crawler && python main.py` 실행 시 즉시 예외 없이 시작
  - 크롤링/매칭/푸시/로그 주요 경로에서 치명 오류가 없는지 확인
  - 60초 대기 로직(`time.sleep(60)`)이 유지되는지 확인

## 제약 사항 및 경계 (Boundaries)
- `local.properties`, `serviceAccountKey.json`, `google-services.json` 과 같은 중요 환경 변수나 키 파일은 코드 변경 또는 새로 작성 시 포함해서는 안 됩니다. (이 파일들은 이미 `.gitignore`에 의해 제외 처리되어 있습니다.)
- 크롤러 코드를 갱신하거나 최적화할 때, 대상 웹사이트에 디도스 수준의 부하를 주지 않기 위해 루프 내 60초 대기(`time.sleep(60)`) 로직은 반드시 유지해야 합니다.
- FCM 토큰, 서비스 계정 정보, 사용자 식별 정보 등 민감정보를 로그에 평문으로 출력하지 않습니다.
- 예제 코드/문서/커밋 메시지에도 실제 키/토큰/비밀값을 포함하지 않습니다.

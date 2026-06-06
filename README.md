# ANU_EcoSortBleClient

GKNU 국립경국대학교 컴퓨터공학과 ICBM 기반 캡스톤디자인 스마트 분리수거 시스템의 Android BLE 클라이언트 앱입니다.

이 앱은 Raspberry Pi에서 실행되는 EcoSort 장치의 BLE 알림을 수신하고, 분류 결과 또는 분류함 가득참 상태를 Android 알림과 화면 이미지로 표시합니다.

## 연관 저장소

- Raspberry Pi / YOLO / 센서 / 모터 서버: [gknu-smart-waste-icbm](https://github.com/devbyhwang/gknu-smart-waste-icbm)

## 주요 기능

- `RaspberryPi_BLE` 장치 BLE 스캔
- GATT characteristic notify 구독
- Raspberry Pi에서 전송하는 JSON payload 수신
- `BIN_FULL`, `OUTPUT_EXCEPTION` 이벤트 알림 표시
- 분류 결과 메시지에 따라 캔, 플라스틱, 유리, 종이 이미지 표시
- Foreground Service를 통한 BLE 연결 유지 및 재연결 시도
- Android 12 이상 BLE 권한, Android 13 이상 알림 권한 처리

## BLE 연동 정보

Raspberry Pi 서버와 Android 앱은 아래 BLE UUID를 공유합니다.

```text
Device Name: RaspberryPi_BLE
Service UUID: f82d9a22-3dc9-430e-875d-583c9ced1904
Characteristic UUID: 2c5bba85-ac1c-46c2-a8d3-db389101a028
CCCD UUID: 00002902-0000-1000-8000-00805f9b34fb
```

앱은 notify characteristic을 구독하고, Raspberry Pi가 보내는 payload를 UTF-8 문자열로 읽습니다.

예시 payload:

```json
{
  "event": "BIN_FULL",
  "message": "Can 분류함 비움 필요",
  "ts": "2026-06-06T00:00:00+00:00"
}
```

이벤트 처리:

| event | 앱 표시 |
| --- | --- |
| `BIN_FULL` | `분류함 가득 참` 알림 |
| `OUTPUT_EXCEPTION` | `출력 장치 예외` 알림 |
| 기타 이벤트 | `EcoSort 알림` |

## 프로젝트 구조

```text
.
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/camellon/anu_ecosortbleclient/
│       │   ├── MainActivity.kt        # Compose UI, 권한 요청, BLE 스캔 시작
│       │   ├── BleClient.kt           # GATT 연결, notify 구독, payload 처리
│       │   ├── BleService.kt          # Foreground Service, 재연결 관리
│       │   └── NotificationHelper.kt  # Android 알림 채널 및 알림 표시
│       └── res/drawable/              # 분류 결과 이미지
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/libs.versions.toml
```

## 개발 환경

- Android Studio
- Kotlin
- Jetpack Compose
- Gradle Wrapper
- minSdk 26
- targetSdk 36
- compileSdk 36
- Java 17

## 빌드

Android Studio에서 프로젝트를 열거나 Gradle Wrapper를 사용합니다.

```bash
./gradlew assembleDebug
```

테스트:

```bash
./gradlew test
```

## 사용 방법

1. Raspberry Pi에서 EcoSort 서버를 실행합니다.
2. Android 기기에서 Bluetooth를 켭니다.
3. 앱을 실행하고 필요한 BLE/알림 권한을 허용합니다.
4. `라즈베리 파이 연결하기` 버튼을 누릅니다.
5. 앱이 `RaspberryPi_BLE` 장치를 찾으면 Foreground Service를 시작하고 notify를 구독합니다.
6. Raspberry Pi에서 분류함 가득참 또는 출력 예외 이벤트가 발생하면 Android 알림이 표시됩니다.

## 권한

앱은 Android 버전에 따라 다음 권한을 사용합니다.

- `BLUETOOTH_SCAN`
- `BLUETOOTH_CONNECT`
- `BLUETOOTH`
- `BLUETOOTH_ADMIN`
- `ACCESS_FINE_LOCATION`
- `POST_NOTIFICATIONS`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_CONNECTED_DEVICE`

Android 12 이상에서는 BLE 스캔/연결 권한이 필요하고, Android 13 이상에서는 알림 권한이 필요합니다.

## 팀

- 소속: GKNU 국립경국대학교 컴퓨터공학과
- 과목/분야: ICBM 기반 캡스톤디자인
- 구성: 4명 팀 프로젝트

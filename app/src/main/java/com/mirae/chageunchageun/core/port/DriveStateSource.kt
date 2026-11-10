package com.mirae.chageunchageun.core.port

import com.mirae.chageunchageun.core.model.Gear
import com.mirae.chageunchageun.core.model.Signal
import kotlinx.coroutines.flow.Flow

/*
 * core/port: 앱이 바깥 세계(차량, 위치, 음성)를 보는 유일한 창구입니다. 포트 하나에 파일 하나.
 * - 구현은 platform/(실제)과 demo/(가짜) 두 벌이고, runtime/AppGraph가 고릅니다.
 * - feature/와 domain/은 이 인터페이스만 씁니다.
 * - 이 폴더를 바꾸려면 PR 리뷰를 받습니다.
 */

/** 주행 상태 판단에 쓰는 바탕 신호. DriveRuntime이 구독합니다. 담당: 후 */
interface DriveStateSource {
    val gear: Flow<Signal<Gear>>

    /** CarUxRestrictions의 주행 규제 여부. true면 상세 화면을 숨깁니다. */
    val uxRestricted: Flow<Signal<Boolean>>
}

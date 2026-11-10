package com.mirae.chageunchageun.platform

import com.mirae.chageunchageun.core.model.Signal

/**
 * 아직 본문을 채우지 않은 어댑터가 내보내는 값.
 * 어댑터를 구현하면 이 값 대신 실제 값(또는 실패 사유를 담은 Unavailable)을 내보냅니다.
 */
internal val NOT_IMPLEMENTED: Signal<Nothing> = Signal.Unavailable("adapter not implemented")

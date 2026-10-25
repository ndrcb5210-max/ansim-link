package io.github.ndrcb5210max.ansimlink

import android.app.Application

/**
 * 앱 시작점. [AppContainer] 를 하나 만들어 두기만 한다.
 *
 * 컨테이너 안의 Firebase 객체는 모두 지연 생성이라 여기서는 아무것도 초기화하지 않는다.
 * 설정이 없어도 앱이 죽지 않아야 하기 때문이다.
 */
class AnsimLinkApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}

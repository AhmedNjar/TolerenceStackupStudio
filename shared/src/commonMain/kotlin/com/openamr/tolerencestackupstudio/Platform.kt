package com.openamr.tolerencestackupstudio

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
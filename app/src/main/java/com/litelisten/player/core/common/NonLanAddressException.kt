package com.litelisten.player.core.common

import java.io.IOException

/** 公网地址被 LanOnlyInterceptor 拒绝；P3/P4 会把它转成 SourceError.Protocol 给用户看。 */
class NonLanAddressException(host: String) : IOException("NON_LAN_HOST_REJECTED:$host")

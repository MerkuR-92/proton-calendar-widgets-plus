package me.proton.android.calendar.common.utils

import me.proton.android.calendar.data.api.ApiResponse


fun ApiResponse.Error.isTimeout(): Boolean {
    // TODO hardcoded string because there is no dedicated code for timeout
    return this.httpCode == 0 && this.errorCode == 0 && this.error == "timeout"
}

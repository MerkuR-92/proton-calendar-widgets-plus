package me.proton.android.calendar.common.provider

import android.content.res.Resources
import androidx.annotation.StringRes
import me.proton.android.calendar.domain.ResourceProvider

class ResourceProviderImpl(
    private val resources: Resources
): ResourceProvider {

    override fun provideString(@StringRes resId: Int, vararg formatArgs: Any?): String {
        return resources.getString(resId, *formatArgs)
    }
}

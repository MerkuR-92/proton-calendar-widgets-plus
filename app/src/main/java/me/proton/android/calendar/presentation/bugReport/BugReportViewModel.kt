package me.proton.android.calendar.presentation.bugReport

import androidx.activity.result.ActivityResultCaller
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.report.presentation.ReportOrchestrator
import me.proton.core.report.presentation.entity.BugReportInput
import me.proton.core.report.presentation.entity.BugReportOutput
import me.proton.core.user.domain.UserManager
import javax.inject.Inject

@HiltViewModel
class BugReportViewModel @Inject constructor(
    private val accountManager: AccountManager,
    private val reportOrchestrator: ReportOrchestrator,
    private val userManager: UserManager
) : ViewModel() {
    private val _bugReportSent = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Flow that produces a Success Message after a bug report has been sent. */
    val bugReportSent: Flow<String> = _bugReportSent.asSharedFlow()

    fun register(caller: ActivityResultCaller) {
        reportOrchestrator.register(caller) {
            if (it is BugReportOutput.SuccessfullySent) {
                viewModelScope.launch { _bugReportSent.emit(it.successMessage) }
            }
        }
    }

    override fun onCleared() {
        reportOrchestrator.unregister()
        super.onCleared()
    }

    fun reportBugs() = viewModelScope.launch {
        val userId = accountManager.getPrimaryUserId().first()
        val user = userId?.let { userManager.getUser(it) }
        val email = requireNotNull(user?.email) { "Missing user email" }
        val username = requireNotNull(user?.name) { "Missing username" }
        val input = BugReportInput(email = email, username = username, finishAfterReportIsEnqueued = true)
        reportOrchestrator.startBugReport(input)
    }
}

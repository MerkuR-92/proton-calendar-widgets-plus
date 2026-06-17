package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import me.proton.android.calendar.common.utils.KotlinUtilsImpl.debounceExceptFirst
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventOccurrenceEntity
import me.proton.android.calendar.domain.indicators.MetadataIndicatorsCalculator
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.domain.model.filterVisibleCalendars
import me.proton.core.domain.entity.UserId
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class GetUiEventsUseCase @Inject constructor(
    private val database: AppDatabase,
    private val eventDecryptor: EventDecryptor,
    private val calendarsRepository: CalendarsRepository,
    private val getUserInfoUseCase: GetUserInfoUseCase,
    private val loadingStateUseCase: LoadingStateUseCase,
    private val priorityRunner: PriorityDecryptionRunner,
    private val skeletonCalculator: MetadataIndicatorsCalculator,
    private val expansionCache: UiEventExpansionCache,
) : UseCase {

    fun execute(
        userId: UserId,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
        onlyVisibleCalendars: Boolean = true,
        priority: DecryptionPriority = DecryptionPriority.Offscreen,
    ): Flow<CalendarsRepository.GetEventsResult<UiEvent>> {
        val eventsWindow = CalendarsRepository.EventsWindow(fromDate, toDate, timeZoneId)
        // set when the flow is collected to measure how long inputs take to arrive
        val executeStartNanos = System.nanoTime()
        Timber.d("getUiEventsUseCase: $eventsWindow")

        // skip the skeleton for windows already loaded this session since its expansion is wasteful on re-views
        val windowKey = UiEventExpansionCache.WindowKey(userId.id, fromDate, toDate, timeZoneId)
        val skipSkeleton = expansionCache.hasSeenWindow(windowKey)

        val zoneId = ZoneId.of(timeZoneId)
        val fromEpochZdt = fromDate.atStartOfDay(zoneId)
        val toEpochZdt = toDate.plusDays(1).atStartOfDay(zoneId)
        val fromEpochSec = fromEpochZdt.toEpochSecond()
        val toEpochSec = toEpochZdt.toEpochSecond()

        val realFlow = calendarsRepository.flowAllCalendars(userId.id)
            .map { if (onlyVisibleCalendars) it.filterVisibleCalendars() else it }
            .onEach { eventDecryptor.setCalendars(it) }
            .distinctUntilChanged()
            .debounceExceptFirst(1.seconds)
            .flatMapLatest { calendars ->
                val calendarIds = calendars.map { it.id }
                val calendarColorById = calendars.associate { it.id to it.color }
                combine(
                    getUserInfoUseCase().debounceExceptFirst(1.seconds).distinctUntilChanged(),
                    // trivial case, only 1 row for each event, start + end times are well defined
                    database.eventOccurrencesDao().selectNonRecurringBetweenInclusive(
                        userId.id,
                        calendarIds,
                        fromEpochSec,
                        toEpochSec
                    ).debounceExceptFirst(1.seconds).distinctUntilChanged(),
                    // we know first and last occurrence time, it can be selected like non-recurring above
                    database.eventOccurrencesDao().selectFiniteRecurring(
                        userId.id,
                        calendarIds,
                        fromEpochSec,
                        toEpochSec
                    ).debounceExceptFirst(1.seconds).distinctUntilChanged(),
                    // we don't know when the last occurrence happens, so we have to select all events
                    // except for the ones that start after our window
                    database.eventOccurrencesDao().selectInfiniteRecurring(
                        userId.id,
                        calendarIds,
                        toEpochSec
                    ).debounceExceptFirst(1.seconds).distinctUntilChanged(),
                    // signal to retry decryption when passphrases change (e.g. after onResetAll restores them)
                    database.passphrasesDao().flowCountByCalendarIds(calendarIds)
                        .debounceExceptFirst(1.seconds)
                ) { userInfo, nonRecurring, finiteRecurring, infiniteRecurring, _ ->
                    val inputsMs = (System.nanoTime() - executeStartNanos) / 1_000_000
                    Timber.d("getUiEvents inputs $eventsWindow @${inputsMs}ms: nonRec=${nonRecurring.size} finRec=${finiteRecurring.size} infRec=${infiniteRecurring.size}")
                    withContext(Dispatchers.Default) {
                        // submit heavy work into a priority runner
                        val submitNanos = System.nanoTime()
                        priorityRunner.submit(priority) {
                            val started = System.nanoTime()
                            val queueMs = (started - submitNanos) / 1_000_000
                            // local cache hits vs real decryptions for this pass
                            val cacheHits = AtomicInteger(0)
                            val decrypts = AtomicInteger(0)
                            // process-wide decryptor delta for crypto runs vs cache serves
                            val statsBefore = eventDecryptor.decryptionStats()
                            // potential optimization: we have rrule, we can generate occurrences quickly without decrypting the event
                            // to filter out even more events here, before decryption takes place

                            // potential optimizations, but debatable if we're not dealing with huge amount of infinitely recurring events:
                            //  - select from by windowStart and windowEnd, only the rows that overlap with eventsWindow (problem: false negatives if the window was never generated for those events)
                            //  - groupBy events before performing discarding below and early-return for each event that for sure happens or doesn't happen in the window (problem: higher memory usage)

                            // infinite recurring that don't occur in <from,to>
                            val notOccurring = infiniteRecurring.filter { occurrence ->
                                val windowPair = fromEpochSec to toEpochSec
                                val occPair = occurrence.windowStartTime to occurrence.windowEndTime
                                val isSelectedWindowFullyInOccurrenceWindow = isFullyBetween(windowPair, occPair)
                                val isEventNotHappeningInOccurrenceWindow = occurrence.startTime == null
                                val isFirstEventOccurenceAfterSelectedWindow = (occurrence.startTime ?: 0) > toEpochSec

                                isSelectedWindowFullyInOccurrenceWindow && (isEventNotHappeningInOccurrenceWindow || isFirstEventOccurenceAfterSelectedWindow)
                            }

                            val filteredInfiniteRecurring = infiniteRecurring.filterNot { recurring ->
                                notOccurring.any {
                                    recurring.userId == it.userId &&
                                            recurring.calendarId == it.calendarId &&
                                            recurring.eventId == it.eventId
                                }
                            }

                            val neededUids: Set<String> =
                                (finiteRecurring.asSequence().map { it.eventUid } +
                                        filteredInfiniteRecurring.asSequence().map { it.eventUid })
                                    .distinct()
                                    .toSet()

                            val selByUidsStart = System.nanoTime()
                            val byUidMap = calendarsRepository.selectEventEntitiesByUids(neededUids)
                            val selByUidsMs = (System.nanoTime() - selByUidsStart) / 1_000_000

                            suspend fun fetchEventFor(occ: EventOccurrenceEntity): Event? {
                                val cached = eventDecryptor.getFromCache(occ.eventId, occ.calendarId, occ.modifyTime)
                                val event = if (cached != null) {
                                    cacheHits.incrementAndGet()
                                    cached
                                } else {
                                    database.eventsDao().selectById(occ.eventId)?.let {
                                        decrypts.incrementAndGet()
                                        eventDecryptor.decrypt(it)
                                    }
                                }
                                return event?.takeIf { it.decryptionStatus != Event.DecryptionStatus.Failure.NoAddressKey }
                            }

                            val isFreeUser = userInfo.hasSubscriptionForMail.not()
                            val userEmailsHash = userInfo.emails.hashCode()

                            // cache expanded ui events since expansion dominates warm re-views and isn't covered by the decrypt cache
                            suspend fun expandWithCache(occ: EventOccurrenceEntity): List<UiEvent>? {
                                val siblingEntities = byUidMap[occ.eventUid].orEmpty()
                                val calendarColor = calendarColorById[occ.calendarId]
                                val key = if (calendarColor != null) UiEventExpansionCache.Key(
                                    originalEventId = occ.eventId,
                                    originalCalendarId = occ.calendarId,
                                    originalModifyTime = occ.modifyTime,
                                    calendarColor = calendarColor,
                                    siblings = siblingEntities
                                        .map { UiEventExpansionCache.SiblingId(it.id, it.modifyTime) }
                                        .sortedBy { it.eventId },
                                    fromDate = eventsWindow.fromDate,
                                    toDate = eventsWindow.toDate,
                                    timeZoneId = eventsWindow.timeZoneId,
                                    userEmailsHash = userEmailsHash,
                                    isFreeUser = isFreeUser,
                                ) else null

                                if (key != null) expansionCache.get(key)?.let { return it }

                                val decr = fetchEventFor(occ) ?: return null
                                val siblings = siblingEntities.mapNotNull { eventDecryptor.decrypt(it) }
                                val computed = calendarsRepository
                                    .expandOccurrencesWithSingleEditsAndExDatesToUiEvents(
                                        originalEvent = decr,
                                        eventsSharingUid = siblings,
                                        fromDate = eventsWindow.fromDate,
                                        toDate = eventsWindow.toDate,
                                        timeZoneId = eventsWindow.timeZoneId,
                                        userEmails = userInfo.emails,
                                        isFreeUser = isFreeUser,
                                    ) ?: return null
                                if (key != null) expansionCache.put(key, computed)
                                return computed
                            }

                            val nonRecStart = System.nanoTime()
                            val transformedNonRecurring =
                                nonRecurring.parallelMap { occ ->
                                    fetchEventFor(occ)?.toUiEvent(
                                        userEmails = userInfo.emails,
                                        timeZoneId = timeZoneId,
                                        isFreeUser = userInfo.hasSubscriptionForMail.not()
                                    )
                                }
                            val nonRecMs = (System.nanoTime() - nonRecStart) / 1_000_000

                            val finRecStart = System.nanoTime()
                            val transformedFiniteRecurring =
                                finiteRecurring.distinctBy { it.eventId }.parallelMap { occ ->
                                    expandWithCache(occ)
                                }.flatten()
                            val finRecMs = (System.nanoTime() - finRecStart) / 1_000_000

                            val infRecStart = System.nanoTime()
                            val transformedInfiniteRecurring =
                                filteredInfiniteRecurring.distinctBy { it.eventId }.parallelMap { occ ->
                                    expandWithCache(occ)
                                }.flatten()
                            val infRecMs = (System.nanoTime() - infRecStart) / 1_000_000

                            val sortStart = System.nanoTime()
                            val uiEvents =
                                (transformedNonRecurring + transformedFiniteRecurring + transformedInfiniteRecurring)
                                    .distinct()
                                    // deterministic order (independend of db row order)
                                    .sortedWith(compareBy({ it.dateStart }, { it.id }, { it.occurrenceNumber }))
                            val sortMs = (System.nanoTime() - sortStart) / 1_000_000

                            val elapsedMs = (System.nanoTime() - started) / 1_000_000
                            val statsAfter = eventDecryptor.decryptionStats()
                            val cryptoRuns = statsAfter.cryptoRuns - statsBefore.cryptoRuns
                            val cacheServes = statsAfter.cacheServes - statsBefore.cacheServes
                            // per-pass timing breakdown
                            Timber.d(
                                "getUiEvents timing $eventsWindow: total=${elapsedMs}ms queue=${queueMs}ms " +
                                    "selByUids=${selByUidsMs}ms nonRec=${nonRecMs}ms/${nonRecurring.size} " +
                                    "finRec=${finRecMs}ms/${finiteRecurring.size} " +
                                    "infRec=${infRecMs}ms/${filteredInfiniteRecurring.size} sort=${sortMs}ms " +
                                    "cacheHits=${cacheHits.get()} decrypts=${decrypts.get()} " +
                                    "crypto=$cryptoRuns served=$cacheServes cacheSize=${statsAfter.cacheSize} ui=${uiEvents.size}"
                            )

                            // base events expected in this window but not rendered
                            val expectedEventIds = (
                                nonRecurring.asSequence().map { it.eventId } +
                                    finiteRecurring.asSequence().map { it.eventId } +
                                    filteredInfiniteRecurring.asSequence().map { it.eventId }
                                ).toSet()
                            val missingEventIds = expectedEventIds - uiEvents.asSequence().map { it.id }.toSet()
                            if (missingEventIds.isNotEmpty()) {
                                Timber.d("getUiEvents missing: ${missingEventIds.size} of $eventsWindow")
                            }
                            uiEvents
                        }
                    }
                }
            }
            .combine(loadingStateUseCase.invoke().map { it.inProgress() }
                .distinctUntilChanged()) { events, inProgress ->
                Timber.d("getUiEvents emit: ${events.size} ui-events for $fromDate..$toDate (fullyLoaded=${inProgress.not()})")
                CalendarsRepository.GetEventsResult.Success(events, fullyLoaded = inProgress.not())
            }
            .onEach { expansionCache.markWindowSeen(windowKey) }

        return flow {
            if (!skipSkeleton) {
                val skeletonStart = System.nanoTime()
                val skeleton = try {
                    skeletonCalculator.computeSkeletonUiEvents(userId.id, fromDate, toDate, timeZoneId)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Timber.w(t, "skeleton calculator threw for $fromDate..$toDate")
                    emptyList()
                }
                val skeletonMs = (System.nanoTime() - skeletonStart) / 1_000_000
                Timber.d("getUiEvents skeleton: ${skeleton.size} placeholders in ${skeletonMs}ms $eventsWindow")
                emit(CalendarsRepository.GetEventsResult.Success(skeleton, fullyLoaded = false, isSkeleton = true))
            }
            emitAll(realFlow)
        }
    }

    /**
     * If [fromEpoch] -- [toEpoch] is fully between [windowStartTime] -- [windowEndTime]
     */
    private fun isFullyBetween(smallerWindow: Pair<Long, Long>, largerWindow: Pair<Long, Long>): Boolean {
        return smallerWindow.first >= largerWindow.first && smallerWindow.second <= largerWindow.second
    }

    private fun defaultConcurrency() = when (val cores = Runtime.getRuntime().availableProcessors()) {
        1, 2, 3, 4 -> cores
        // likely a big.LITTLE cluster of 2 big + N small.
        // In practice it works better to constrain this, for different reasons -
        // i.e. we need to await the first "long" call, which may "land" on a little core, and also the cluster shares resources amongst cores
        else -> cores / 2
    }.coerceAtLeast(1)

    private suspend fun <T, R> List<T>.parallelMap(
        concurrency: Int = defaultConcurrency(),
        dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(concurrency),
        transform: suspend (T) -> R?,
    ): List<R> = coroutineScope {
        if (this@parallelMap.isEmpty()) return@coroutineScope emptyList()
        val chunkSize = (size + concurrency - 1) / concurrency
        chunked(chunkSize).map { chunk ->
            async(dispatcher) { chunk.mapNotNull { transform(it) } }
        }.awaitAll().flatten()
    }
}

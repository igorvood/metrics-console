package ru.vood.metrics.metricsconsole

import groovy.transform.CompileStatic
import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.vood.metrics.metricsconsole.model.RateStatus
import ru.vood.metrics.metricsconsole.service.FetchRatesService

import java.util.concurrent.CompletableFuture

import static java.lang.String.format
import static java.util.concurrent.CompletableFuture.allOf
import static org.fusesource.jansi.Ansi.Color.BLACK
import static org.fusesource.jansi.Ansi.ansi

@Slf4j
@Component
@CompileStatic
@RequiredArgsConstructor
class ConsoleDashboard {
    public static final int PAD = 20

    @Autowired
    FetchRatesService fetchRatesService
    @Autowired
    DashboardProperties properties

    @Scheduled(fixedDelay = 50L)
    void run() {
        def each = properties.serviceMap.entrySet()
                .collect { entry ->
                    fetchRatesService.getRateStatus(entry.value).thenApply({ status -> status.setComponent(entry.key) })
                }.toList()
//        def whenDone = each

//        def whenDone = sequence([
//                fetchRatesService.getRateStatus(properties.getLetterGrabberUrl()).thenApply({ status -> status.setComponent("pechkin-service") }),
//                fetchRatesService.getRateStatus(properties.getLetterSignatureUrl()).thenApply({ status -> status.setComponent("big-brother-service") }),
//                fetchRatesService.getRateStatus(properties.getGuardUrl()).thenApply({ status -> status.setComponent("agent-smith-service") })
//        ])
        def whenDone = sequence(each)


        def builder = new StringBuilder()
        whenDone.thenAccept { statuses ->
            int COL0_MAXSIZE = statuses*.component*.size().max()
            int TABLE_LINESIZE = PAD * 2 + COL0_MAXSIZE

            ansi().restoreCursorPosition()

            (statuses.size() + 3).times {
                builder.append ansi().cursorUpLine().eraseLine()
            }

            builder.append("┏${('━' * TABLE_LINESIZE)}┓\n")
            builder.append '┃'
            builder.append ansi().fgBright(BLACK)
            builder.append 'service name'.padRight(COL0_MAXSIZE + 2)
            builder.append 'speed'.center(8)
            builder.append 'buffers'.center(12)
            builder.append 'workers'.center(4)
            builder.append ' '.padLeft(11)
            builder.append ansi().reset()
            builder.append '┃\n'

            statuses.each { status ->
                builder.append '┃'
                builder.append formatComponent(status, COL0_MAXSIZE)
                builder.append "${formatRate(status)} ${formatBuffer(status)}".padLeft(PAD * 2 - 8, '.')
                builder.append '┃\n'.padLeft(PAD - 7)
            }

            builder.append("┗${('━' * TABLE_LINESIZE)}┛\n")

            print builder
        }.join()
    }

    private String formatBuffer(RateStatus status) {
        def buffer = status.buffers?.get(0)
        return buffer?.with {
            def result = ansi()

            if (remaining <= maxSize * 0.75) {
                result.fgBrightRed()
            } else {
                result.fgBrightGreen()
            }

            result.format('%6d/%-6d', remaining, maxSize)
            result.format('%2d/%-2d', activeWorker, workersCount)
            return result.reset().toString()
        } ?: format('%6d/%-6d%2d/%-2d', 0, 0, 0, 0)
    }

    private String formatRate(RateStatus status) {
        ansi().fgBrightCyan().format('%8.2f', status.getLetterRps()).reset().toString()
    }

    private String formatComponent(RateStatus status, int colSize) {
        ansi().fgBrightGreen().a(status.getComponent().padRight(colSize + 2, '.')).reset().toString()
    }

    static CompletableFuture<List<RateStatus>> sequence(List<CompletableFuture<RateStatus>> futures) {
        allOf(futures as CompletableFuture[]).thenApply({ ignored ->
            return futures.collect { it.join() }
        })
    }
}

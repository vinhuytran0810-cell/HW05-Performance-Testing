import groovy.json.JsonOutput
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** Stress-only adapter. Parsing this file never sends requests.
 * Reuses frozen Load contracts, ownership journal and independent recovery.
 * Setup is scenario-specific because the Load initializer deliberately caps its envelope.
 */
class Hw05CategoryStress extends Hw05CategoryLoad {
    static Map setupStress(Properties p, File planDir) {
        need(p.get(KEY) == null, 'ANOTHER_RUNTIME_ALREADY_REGISTERED')
        need(p.getProperty('hw05.stress_reviewed', 'false') == 'true', 'STRESS_REVIEW_REQUIRED')
        need(p.getProperty('hw05.allow_mutation', 'false') == 'true', 'REVIEW_REQUIRED_ALLOW_MUTATION')
        need(p.getProperty('hw05.isolated_sut', 'false') == 'true', 'REVIEW_REQUIRED_ISOLATED_SUT')
        need(p.getProperty('protocol', 'http') == 'http' && p.getProperty('host', 'localhost') in ['localhost', '127.0.0.1', '::1'], 'ONLY_APPROVED_LOCAL_SUT')
        File root = new File(p.getProperty('project_root', new File(planDir, '..').canonicalPath)).canonicalFile
        String runId = p.getProperty('run_id', Instant.now().toString().replaceAll('[^0-9]', '') + '_' + UUID.randomUUID().toString().take(8))
        String engineId = p.getProperty('engine_id', 'local')
        need(runId ==~ /[A-Za-z0-9_-]{1,64}/ && engineId ==~ /[A-Za-z0-9_-]{1,32}/, 'UNSAFE_RUN_ENGINE_NAMESPACE')
        long minFree = Long.parseLong(p.getProperty('min_free_mib', '2048'))
        need(minFree >= 2048, 'MINIMUM_MEMORY_GUARD_CANNOT_BE_LOWERED')
        long freeMiB = freePhysicalMiB()
        need(freeMiB >= minFree, 'INSUFFICIENT_FREE_RAM')
        int thinkMin = Integer.parseInt(p.getProperty('think_min_ms', '1000'))
        int thinkRange = Integer.parseInt(p.getProperty('think_range_ms', '1000'))
        need(thinkMin == 1000 && thinkRange == 1000, 'KEEP_APPROVED_THINK_TIME')
        String mode = p.getProperty('stress_mode', 'official')
        need(mode in ['official', 'pilot'], 'INVALID_STRESS_MODE')
        boolean pilot = mode == 'pilot'
        int cohortThreads = pilot ? 1 : 10
        int ramp = pilot ? 1 : 20
        List delays = pilot ? [0,3,6,9,12] : [0,80,160,240,320]
        List durations = pilot ? [30,27,24,21,18] : [400,320,240,160,80]
        need(p.getProperty('stress_cohort_threads', '10') == cohortThreads.toString(), 'COHORT_SIZE_NOT_REVIEWED')
        need(p.getProperty('stress_ramp_seconds', '20') == ramp.toString(), 'RAMP_NOT_REVIEWED')
        (0..4).each { i ->
            need(p.getProperty('stress_delay_' + i, (i*80).toString()) == delays[i].toString(), 'DELAY_NOT_REVIEWED')
            need(p.getProperty('stress_duration_' + i, (400-i*80).toString()) == durations[i].toString(), 'DURATION_NOT_REVIEWED')
        }
        File csv = new File(p.getProperty('csv_file', '../data/categories.csv'))
        if (!csv.absolute) csv = new File(planDir, csv.path)
        csv = csv.canonicalFile
        byte[] bytes = csv.bytes
        need(sha(bytes) == CSV_SHA256, 'APPROVED_CSV_HASH_CHANGED')
        String csvText = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        List lines = csvText.readLines()
        need(lines.size() == 21 && lines[0] == 'category_base' && lines.drop(1).toSet().size() == 20, 'CSV_SCHEMA_FAILED')
        need(lines.drop(1).every { it ==~ /[A-Za-z][A-Za-z0-9_]*/ }, 'CSV_BASE_NAME_INVALID')
        Map cfg = [root: root.path, runId: runId, engineId: engineId, csvPath: csv.path,
                   baseUrl: 'http://' + p.getProperty('host', 'localhost') + ':' + p.getProperty('port', '3000'),
                   connectTimeout: Integer.parseInt(p.getProperty('connect_timeout_ms', '3000')),
                   responseTimeout: Integer.parseInt(p.getProperty('response_timeout_ms', '10000')),
                   cohortThreads: cohortThreads, minFreeMiB: minFree, abort: new AtomicBoolean(false), ready: false, journalMutex: new Object(), acceptQuiescent: false]
        need(cfg.connectTimeout > 0 && cfg.responseTimeout > 0, 'TIMEOUTS_MUST_BE_POSITIVE')
        Map auth = authenticate(cfg)
        cfg.adminId = auth.adminId
        cfg.adminBefore = adminState(cfg, auth.token as String)
        cfg.baselineCategories = list(cfg, '/api/categories')
        cfg.baselineProducts = list(cfg, '/api/products')
        categoriesValid(cfg.baselineCategories as List)
        cfg.baselineIds = cfg.baselineCategories.collect { id(it.id) }.toSet()
        cfg.referenceIds = cfg.baselineProducts.findAll { it.category_id != null }.collect { id(it.category_id) }.toSet()
        cfg.baselineHash = sha(canon(cfg.baselineCategories as List).getBytes(StandardCharsets.UTF_8))
        cfg.dir = new File(root, 'audit/manifests/' + runId + '/' + engineId).canonicalPath
        File dir = new File(cfg.dir as String)
        need(!dir.exists() && dir.mkdirs(), 'MANIFEST_RUN_DIRECTORY_MUST_BE_NEW')
        cfg.activeChannel = FileChannel.open(new File(dir, 'active.lock').toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        cfg.activeLock = cfg.activeChannel.tryLock()
        need(cfg.activeLock != null, 'RUN_ALREADY_ACTIVE')
        try {
            List sources = ['eshop-sut/backend/server.js', 'eshop-sut/backend/database.js', 'eshop-sut/frontend-admin/src/App.jsx',
                            'data/categories.csv', 'test-plans/23120274_Load_20261118.jmx',
                            'scripts/category-load-support.groovy', 'scripts/recover-category-load.groovy',
                            'scripts/category-stress-support.groovy', 'test-plans/23120274_Stress_20261119.jmx',
                            'scripts/run-category-stress.ps1', 'scripts/check-category-stress-run.py']
            cfg.sourceHashes = sources.collectEntries { f -> [(f): sha(new File(root, f).bytes)] }
            Map saved = [schema: 1, createdAt: Instant.now().toString(), runId: runId, engineId: engineId,
                         root: cfg.root, dir: cfg.dir, baseUrl: cfg.baseUrl, connectTimeout: cfg.connectTimeout,
                         responseTimeout: cfg.responseTimeout, minFreeMiB: minFree, adminId: cfg.adminId, adminBefore: cfg.adminBefore,
                         baselineCategories: cfg.baselineCategories, baselineProducts: cfg.baselineProducts,
                         baselineHash: cfg.baselineHash, sourceHashes: cfg.sourceHashes,
                         workload: [scenario: 'Stress', mode: mode, levels: (1..5).collect { it * cohortThreads },
                                    cohortThreads: cohortThreads, cohortDelaysSeconds: delays, cohortDurationsSeconds: durations,
                                    rampSeconds: ramp, holdSeconds: pilot ? null : 60, mainSeconds: pilot ? 30 : 400,
                                    quietRecoverySeconds: 60, thinkMinMs: thinkMin, thinkRangeMs: thinkRange],
                         runtimeHardware: [logicalProcessors: Runtime.runtime.availableProcessors(), freeRAMMiB: freeMiB],
                         assumptions: ['isolated SUT', 'no product/category changes by other actors', 'no backend restart',
                                       'no read-after-create in measured workflow; exact-name proof deferred to recovery if needed']]
            writeNew(new File(dir, 'baseline.json'), saved)
            cfg.softFailures = new java.util.concurrent.atomic.AtomicLong(0)
            cfg.ready = true
            p.put(KEY, cfg)
            cfg
        } catch (Exception ignored) {
            cfg.activeLock?.release(); cfg.activeChannel?.close()
            throw new IllegalStateException('PREFLIGHT_MANIFEST_FAILED')
        }
    }

    static void gateStress(Binding b) {
        Map runtime = cfg(b)
        if (runtime != null && runtime.ready && !runtime.abort.get()) {
            try {
                synchronized (runtime.journalMutex) {
                    if (runtime.mainAnchorEpochMs == null) {
                        runtime.mainAnchorEpochMs = System.currentTimeMillis()
                        append(new File(runtime.dir as String, 'stress-observations.jsonl'),
                            [timestamp: Instant.now().toString(), epochMs: runtime.mainAnchorEpochMs,
                             stepName: 'MAIN_WORKLOAD_START'], runtime.journalMutex)
                    }
                }
            } catch (Exception ignored) { runtime.abort.set(true) }
        }
        Hw05CategoryLoad.gate(b)
        def v = b.getVariable('vars')
        if (v.get('do_login') != 'true') return
        Map c = cfg(b)
        try {
            int cohort = Integer.parseInt(v.get('cohort_index'))
            need(cohort >= 0 && cohort < 5, 'INVALID_COHORT')
            Map owner = v.getObject('owner') as Map
            owner.thread = cohort * (c.cohortThreads as int) + b.getVariable('ctx').getThreadNum() + 1
            owner.categoryName = "HW05_23120274_${c.runId}_${c.engineId}_T${owner.thread}_I${owner.iteration}_${v.get('category_base')}_${owner.intentId.replace('-', '')}"
            v.put('category_name', owner.categoryName)
            v.put('category_body', JsonOutput.toJson([name: owner.categoryName]))
        } catch (Exception ignored) {
            c.abort.set(true); v.put('do_login', 'false')
            b.getVariable('ctx').getThread().stop()
        }
    }
    static void assertStress(Binding b, String step) {
        def v = b.getVariable('vars'), prev = b.getVariable('prev')
        Map c = cfg(b)
        String status = prev.responseCode
        // Only a failed read/auth transport BEFORE a mutation can continue safely.
        // 401/403, account/identity/schema violations and all uncertain writes remain fatal.
        boolean transport = status in [
            'Non HTTP response code: java.net.SocketTimeoutException',
            'Non HTTP response code: java.net.ConnectException',
            'Non HTTP response code: org.apache.http.conn.HttpHostConnectException',
            'Non HTTP response code: org.apache.http.NoHttpResponseException',
            'Non HTTP response code: java.net.SocketException']
        boolean soft = step in ['LOGIN', 'VIEW'] && v.get('pending') != 'true' &&
                       (status in ['429','500','502','503','504'] || transport)
        if (!soft) {
            Hw05CategoryLoad.assertStep(b, step)
            // Dashboard requires responseMessage. Keep an allowlisted synthetic diagnostic only.
            prev.setResponseMessage(status ==~ /[0-9]{3}/ ? 'HW05_HTTP_' + status : 'HW05_TRANSPORT_FAILURE')
            return
        }
        try {
            ['do_login','do_view','do_prepare','do_create','do_delete','do_verify'].each { v.put(it, 'false') }
            c.softFailures.incrementAndGet()
            append(new File(c.dir as String, 'stress-observations.jsonl'),
                [timestamp: Instant.now().toString(), stepName: 'SOFT_PRE_MUTATION_FAILURE',
                 step: step, status: transport ? 'TRANSPORT_FAILURE' : status,
                 worker: (v.getObject('owner') as Map).thread], c.journalMutex)
            b.getVariable('AssertionResult').setFailure(true)
            b.getVariable('AssertionResult').setFailureMessage('HW05_' + step + '_PRE_MUTATION_AVAILABILITY_FAILURE')
            prev.setSuccessful(false)
        } catch (Exception ignored) {
            c.abort.set(true)
            b.getVariable('AssertionResult').setFailure(true)
            b.getVariable('AssertionResult').setFailureMessage('HW05_STRESS_JOURNAL_FAILED')
            prev.setSuccessful(false)
        } finally {
            scrub(prev)
            prev.setResponseMessage('HW05_PRE_MUTATION_AVAILABILITY_FAILURE')
            ['token','login_body','category_body','category_id'].each { v.remove(it) }
        }
    }
    static void teardownStress(Binding b) {
        b.getVariable('SampleResult').setIgnore()
        Map c = cfg(b)
        if (c == null) return
        c.ready = false
        try {
            append(new File(c.dir as String, 'stress-observations.jsonl'),
                [timestamp: Instant.now().toString(), stepName: 'QUIET_RECOVERY_START',
                 softFailures: c.softFailures.get(), stoppedBySafetyGuard: c.abort.get()], c.journalMutex)
            // No business requests in this 60s quiet period. This is NOT proof of server quiescence.
            Thread.sleep(60000L)
            append(new File(c.dir as String, 'stress-observations.jsonl'),
                [timestamp: Instant.now().toString(), stepName: 'QUIET_RECOVERY_END'], c.journalMutex)
        } catch (Exception ignored) {
            c.abort.set(true)
        } finally { Hw05CategoryLoad.teardown(b) }
    }
}

import groovy.json.JsonOutput
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.jmeter.threads.JMeterContextService

/** Spike adapter: only setup and schedule/phase handling differ.
 * All HTTP contracts, soft-vs-fatal error policy, durable intents and guarded recovery
 * are inherited from the frozen Load/Stress helpers. No action on class parsing.
 */
class Hw05CategorySpike extends Hw05CategoryStress {
    static Map setupSpike(Properties p, File planDir) {
        need(p.get(KEY) == null, 'ANOTHER_RUNTIME_ALREADY_REGISTERED')
        need(p.getProperty('hw05.spike_reviewed', 'false') == 'true', 'SPIKE_REVIEW_REQUIRED')
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
        Map profiles = (Map) json(new File(root, 'data/spike-profiles.json').getText('UTF-8'))
        String mode = p.getProperty('spike_mode', 'official')
        need(mode in ['official','pilot'], 'INVALID_SPIKE_MODE')
        Map profile = (Map) profiles[mode]
        profile.each { key,value ->
            need(p.getProperty('spike_' + key, String.valueOf(profiles.official[key])) == String.valueOf(value), 'SPIKE_PROFILE_NOT_SYNCHRONIZED')
        }
        need(profile.burst_start_seconds == profile.base_ramp_seconds + profile.baseline_hold_seconds &&
             profile.burst_duration_seconds == profile.rise_seconds + profile.peak_hold_seconds &&
             profile.burst_stop_seconds == profile.burst_start_seconds + profile.burst_duration_seconds,
             'SPIKE_PROFILE_INTERNAL_MISMATCH')
        need(profile.burst_scheduler_seconds == profile.burst_duration_seconds + profile.drain_watchdog_seconds + 2, 'SPIKE_SCHEDULER_GUARD_MISMATCH')
        // This file must be loaded with -q before JMeter creates sample collectors.
        need(p.getProperty('sample_variables', '') == 'spike_phase,spike_cohort,spike_boundary_crossed', 'SPIKE_JTL_PROPERTIES_REQUIRED')
        ['jmeter.save.saveservice.timestamp_format':'ms', 'sampleresult.timestamp.start':'true',
         'jmeter.save.saveservice.thread_counts':'true', 'jmeter.save.saveservice.response_data':'false',
         'jmeter.save.saveservice.response_data.on_error':'false', 'jmeter.save.saveservice.samplerData':'false',
         'jmeter.save.saveservice.requestHeaders':'false', 'jmeter.save.saveservice.responseHeaders':'false'].each { k,v ->
            need(p.getProperty(k, '') == v, 'UNREVIEWED_SPIKE_RESULT_CONFIGURATION')
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
                   profile: profile, mode: mode, minFreeMiB: minFree, abort: new AtomicBoolean(false), ready: false, journalMutex: new Object(), acceptQuiescent: false]
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
                            'scripts/run-category-stress.ps1', 'scripts/check-category-stress-run.py',
                            'scripts/category-spike-support.groovy', 'test-plans/23120274_Spike_20261119.jmx',
                            'test-plans/spike-results.properties', 'data/spike-profiles.json',
                            'scripts/run-category-spike.ps1', 'scripts/check-category-spike-run.py', 'scripts/check-spike-graph.groovy']
            cfg.sourceHashes = sources.collectEntries { f -> [(f): sha(new File(root, f).bytes)] }
            Map saved = [schema: 1, createdAt: Instant.now().toString(), runId: runId, engineId: engineId,
                         root: cfg.root, dir: cfg.dir, baseUrl: cfg.baseUrl, connectTimeout: cfg.connectTimeout,
                         responseTimeout: cfg.responseTimeout, minFreeMiB: minFree, adminId: cfg.adminId, adminBefore: cfg.adminBefore,
                         baselineCategories: cfg.baselineCategories, baselineProducts: cfg.baselineProducts,
                         baselineHash: cfg.baselineHash, sourceHashes: cfg.sourceHashes,
                         workload: [scenario: 'Spike', mode: mode, profile: profile, recoveryStartsAfterObservedBurstDrain: true, thinkMinMs: thinkMin, thinkRangeMs: thinkRange],
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

    static long elapsed(Map c) { System.currentTimeMillis() - (c.mainAnchorEpochMs as long) }
    static void observation(Map c, String name, Map detail = [:]) {
        append(new File(c.dir as String, 'spike-phases.jsonl'),
               [stepName: name, timestamp: Instant.now().toString(), epochMs: System.currentTimeMillis()] + detail,
               c.journalMutex)
    }
    static void gateSpike(Binding b) {
        def v = b.getVariable('vars')
        Map c = cfg(b)
        try {
            if (c != null && c.ready && !c.abort.get()) {
                synchronized (c.journalMutex) {
                    if (c.mainAnchorEpochMs == null) {
                        c.mainAnchorEpochMs = System.currentTimeMillis()
                        observation(c, 'MAIN_WORKLOAD_START', [anchorEpochMs: c.mainAnchorEpochMs,
                            mode:c.mode, profile:c.profile])
                    }
                    long t = elapsed(c)
                    int active = JMeterContextService.getNumberOfThreads()
                    c.maxActiveObserved = Math.max((c.maxActiveObserved ?: 0) as int, active)
                    if (v.get('spike_cohort') == 'baseline' && t >= (c.profile.burst_stop_seconds as long)*1000L && c.recoveryEpochMs == null) {
                        if (active == (c.profile.baseline_threads as int)) {
                            c.recoveryEpochMs = System.currentTimeMillis()
                            c.recoveryEndEpochMs = (c.recoveryEpochMs as long) + (c.profile.recovery_seconds as long)*1000L
                            observation(c, 'RECOVERY_OBSERVED_START', [activeThreads:active,
                                recoveryEndEpochMs:c.recoveryEndEpochMs])
                        } else if (t > ((c.profile.burst_stop_seconds as long)+(c.profile.drain_watchdog_seconds as long))*1000L) {
                            c.abort.set(true)
                            observation(c, 'DRAIN_WATCHDOG_SAFETY_STOP', [activeThreads:active])
                        }
                    }
                    boolean normalEnd = v.get('spike_cohort') == 'burst' ? t >= (c.profile.burst_stop_seconds as long)*1000L :
                        (c.recoveryEndEpochMs != null && System.currentTimeMillis() >= (c.recoveryEndEpochMs as long))
                    if (normalEnd) {
                        ['do_login','do_view','do_prepare','do_create','do_delete','do_verify'].each { v.put(it,'false') }
                        if (v.get('pending') == 'true') c.abort.set(true)
                        b.getVariable('SampleResult').setIgnore()
                        b.getVariable('ctx').getThread().stop()
                        return
                    }
                }
            }
            Hw05CategoryLoad.gate(b)
            if (v.get('do_login') != 'true') return
            Map owner = v.getObject('owner') as Map
            String cohort = v.get('spike_cohort')
            need(cohort in ['baseline','burst'], 'INVALID_SPIKE_COHORT')
            owner.thread = (cohort == 'baseline' ? 0 : (c.profile.baseline_threads as int)) + b.getVariable('ctx').getThreadNum() + 1
            need(owner.thread >= 1 && owner.thread <= (c.profile.baseline_threads as int)+(c.profile.burst_threads as int), 'INVALID_SPIKE_WORKER')
            owner.categoryName = "HW05_23120274_${c.runId}_${c.engineId}_T${owner.thread}_I${owner.iteration}_${v.get('category_base')}_${owner.intentId.replace('-', '')}"
            v.put('category_name', owner.categoryName)
            v.put('category_body', JsonOutput.toJson([name:owner.categoryName]))
        } catch (Exception ignored) {
            c?.abort?.set(true)
            ['do_login','do_view','do_prepare','do_create','do_delete','do_verify'].each { v.put(it,'false') }
            b.getVariable('SampleResult').setIgnore(); b.getVariable('ctx').getThread().stop()
            b.getVariable('log').error('HW05 Spike scheduling/ownership guard failed; no new intent permitted')
        }
    }
    static void prepareSpike(Binding b) {
        def v = b.getVariable('vars'); Map c = cfg(b)
        if (c != null && v.get('spike_cohort') == 'burst' && c.mainAnchorEpochMs != null && elapsed(c) >= (c.profile.burst_stop_seconds as long)*1000L) {
            b.getVariable('SampleResult').setIgnore(); v.put('do_create','false'); v.put('do_prepare','false')
            return // Do not start a new write after burst cutoff; existing confirmed ownership may still drain.
        }
        Hw05CategoryLoad.prepare(b)
    }
    static String phase(Map c, long epochMs) {
        long t = epochMs - (c.mainAnchorEpochMs as long)
        if (t < (c.profile.base_ramp_seconds as long)*1000L) return 'WARMUP'
        if (t < (c.profile.burst_start_seconds as long)*1000L) return 'BASELINE'
        if (t < ((c.profile.burst_start_seconds as long)+(c.profile.rise_seconds as long))*1000L) return 'SPIKE_RAMP'
        if (t < (c.profile.burst_stop_seconds as long)*1000L) return 'SPIKE_HOLD'
        if (c.recoveryEpochMs == null || epochMs < (c.recoveryEpochMs as long)) return 'RECOVERY_TRANSITION'
        if (epochMs < (c.recoveryEndEpochMs as long)) return 'RECOVERY'
        'RECOVERY_TAIL'
    }
    static void tagSample(Binding b) {
        Map c = cfg(b); def prev = b.getVariable('prev'), v = b.getVariable('vars')
        try {
            need(c != null && c.mainAnchorEpochMs != null, 'MISSING_SPIKE_PHASE_ANCHOR')
            String startPhase = phase(c, prev.getStartTime())
            String endPhase = phase(c, prev.getEndTime())
            v.put('spike_phase', startPhase)
            v.put('spike_boundary_crossed', (startPhase != endPhase).toString())
        } catch (Exception ignored) {
            c?.abort?.set(true); v.put('spike_phase','UNRESOLVED'); v.put('spike_boundary_crossed','true')
            prev.setSuccessful(false)
        }
    }
    static void teardownSpike(Binding b) {
        b.getVariable('SampleResult').setIgnore(); Map c = cfg(b)
        if (c == null) return
        try {
            observation(c, 'MAIN_WORKLOAD_END', [softFailures:c.softFailures.get(),
                stoppedBySafetyGuard:c.abort.get(), maxActiveObserved:c.maxActiveObserved ?: 0,
                recoveryObserved:c.recoveryEpochMs != null, recoveryEndEpochMs:c.recoveryEndEpochMs])
        } catch (Exception ignored) { c.abort.set(true) }
        finally { Hw05CategoryLoad.teardown(b) }
        // Recovery60s already ran under10VU; do not substitute Stress's quiet60s teardown.
    }
}

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** Shared implementation for the Load JMX and independent, opt-in recovery.
 * No execution occurs when this class is parsed/compiled. No direct SQLite access.
 * Runtime artifacts are real observations, created only when a future run is authorized.
 */
class Hw05CategoryLoad {
    static final String CSV_SHA256 = 'c237523a3f44a5d1471b3d4483bcb25c76495e9972e6a0f2b6bf9bec0dc1a23e'
    static final String KEY = 'hw05.categoryLoad.runtime'
    static void need(boolean ok, String code) { if (!ok) throw new IllegalStateException(code) }
    static Object json(String text) { new JsonSlurper().parseText(text) }
    static String sha(byte[] bytes) { MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString() }
    static long freePhysicalMiB() {
        def os = java.lang.management.ManagementFactory.operatingSystemMXBean
        need(os instanceof com.sun.management.OperatingSystemMXBean, 'MEMORY_PREFLIGHT_UNAVAILABLE')
        (long) (((com.sun.management.OperatingSystemMXBean) os).freePhysicalMemorySize / 1048576L)
    }
    static long id(Object value) {
        String s = String.valueOf(value)
        need(s ==~ /[1-9][0-9]*/, 'INVALID_ID')
        long n = Long.parseLong(s)
        need(n <= 9007199254740991L, 'UNSAFE_ID')
        n
    }
    static Object ordered(Object x) {
        if (x instanceof Map) return new TreeMap(x.collectEntries { k, v -> [(k.toString()): ordered(v)] })
        if (x instanceof List) return x.collect { ordered(it) }
        x
    }
    static String canon(List rows) { JsonOutput.toJson(ordered(rows.sort(false) { id(it.id) })) }
    static Map credentials() {
        String email = System.getenv('ESHOP_ADMIN_EMAIL'), password = System.getenv('ESHOP_ADMIN_PASSWORD')
        need(email != null && !email.trim().isEmpty() && password != null && !password.isEmpty(), 'MISSING_ADMIN_ENV')
        [email: email.trim(), password: password]
    }
    static Map http(Map cfg, String method, String endpoint, Object body = null, String token = null) {
        HttpURLConnection c = (HttpURLConnection) new URL(cfg.baseUrl + endpoint).openConnection()
        c.instanceFollowRedirects = false
        c.requestMethod = method
        c.connectTimeout = cfg.connectTimeout as int
        c.readTimeout = cfg.responseTimeout as int
        c.setRequestProperty('Accept', 'application/json')
        if (token) c.setRequestProperty('Authorization', 'Bearer ' + token)
        try {
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty('Content-Type', 'application/json')
                byte[] bytes = JsonOutput.toJson(body).getBytes(StandardCharsets.UTF_8)
                c.outputStream.withCloseable { it.write(bytes) }
            }
            int status = c.responseCode
            InputStream stream = status >= 400 ? c.errorStream : c.inputStream
            String text = stream == null ? '' : stream.withCloseable { it.getText('UTF-8') }
            Object data
            try { data = json(text) } catch (Exception ignored) { throw new IllegalStateException('NON_JSON_RESPONSE') }
            [status: status, data: data]
        } finally { c.disconnect() }
    }
    static List list(Map cfg, String endpoint, String token = null) {
        Map r = http(cfg, 'GET', endpoint, null, token)
        need(r.status == 200 && r.data instanceof List, 'LIST_CONTRACT_FAILED')
        (List) r.data
    }
    static void categoriesValid(List rows) {
        Set ids = [] as Set
        rows.each { row ->
            need(row instanceof Map && row.name instanceof String && !row.name.trim().isEmpty(), 'CATEGORY_SHAPE')
            need(ids.add(id(row.id)), 'DUPLICATE_CATEGORY_ID')
        }
    }
    static void originalCategoriesIntact(Map cfg, List current) {
        categoriesValid(current)
        cfg.baselineCategories.each { old -> need(current.find { id(it.id) == id(old.id) } == old, 'BASELINE_CATEGORY_CHANGED') }
    }
    static Map authenticate(Map cfg) {
        Map creds = credentials()
        Map r = http(cfg, 'POST', '/api/login', creds)
        need(r.status == 200 && r.data instanceof Map, 'ADMIN_LOGIN_FAILED')
        Map d = r.data
        need(d.message == 'Login successful' && d.token instanceof String && d.token ==~ /[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/, 'LOGIN_CONTRACT_FAILED')
        need(d.user instanceof Map && d.user.role == 'admin' && d.user.email == creds.email, 'ADMIN_IDENTITY_FAILED')
        need(d.user.login_attempts == 0 && d.user.locked_until == null, 'ADMIN_NOT_AT_CLEAN_BASELINE')
        long adminId = id(d.user.id)
        if (cfg.adminId != null) need(adminId == (cfg.adminId as long), 'ADMIN_ID_CHANGED')
        // Decode is an identity check only; server-side protected requests verify the signature.
        Map claims = (Map) json(new String(Base64.urlDecoder.decode(d.token.split('\\.')[1]), StandardCharsets.UTF_8))
        need(id(claims.id) == adminId && claims.role == 'admin', 'JWT_IDENTITY_FAILED')
        [token: d.token, adminId: adminId]
    }
    static Map adminState(Map cfg, String token) {
        List users = list(cfg, '/api/admin/users', token)
        Map u = users.find { id(it.id) == (cfg.adminId as long) } as Map
        need(u != null && u.role == 'admin' && u.login_attempts == 0 && u.locked_until == null, 'ADMIN_FINAL_STATE_FAILED')
        [id: id(u.id), role: u.role, login_attempts: u.login_attempts, locked_until: u.locked_until]
    }
    static void writeNew(File file, Object data) {
        byte[] bytes = (JsonOutput.prettyPrint(JsonOutput.toJson(data)) + '\n').getBytes(StandardCharsets.UTF_8)
        FileChannel.open(file.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).withCloseable { ch ->
            ByteBuffer buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) ch.write(buffer)
            ch.force(true)
        }
    }
    static void append(File file, Map record, Object mutex) {
        // One JSON line per event, serialized writers and force-to-storage before mutation.
        synchronized (mutex) {
            FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND).withCloseable { ch ->
                ByteBuffer buffer = ByteBuffer.wrap((JsonOutput.toJson(record) + '\n').getBytes(StandardCharsets.UTF_8))
                while (buffer.hasRemaining()) ch.write(buffer)
                ch.force(true)
            }
        }
    }
    static void event(Map cfg, Map owner, String stepName, Map details = [:]) {
        need(details.keySet().every { it in ['actualId', 'code'] }, 'EVENT_PAYLOAD_NOT_ALLOWLISTED')
        Map record = [schema: 1, timestamp: Instant.now().toString(), stepName: stepName,
                      runId: cfg.runId, engineId: cfg.engineId, intentId: owner.intentId,
                      thread: owner.thread, iteration: owner.iteration, categoryName: owner.categoryName,
                      baselineHash: cfg.baselineHash] + details
        append(new File(cfg.dir as String, String.format('thread-%04d.jsonl', owner.thread as int)), record, cfg.journalMutex)
    }
    /** Pure validation; no network, credentials, files or engine actions. */
    static Map validateWorkload(Properties p) {
        boolean endurance = p.getProperty('hw05.endurance', 'false') == 'true'
        int threads = Integer.parseInt(p.getProperty('load_threads', '10'))
        int ramp = Integer.parseInt(p.getProperty('ramp_seconds', '20'))
        int duration = Integer.parseInt(p.getProperty('duration_seconds', '180'))
        int thinkMin = Integer.parseInt(p.getProperty('think_min_ms', '1000'))
        int thinkRange = Integer.parseInt(p.getProperty('think_range_ms', '1000'))
        if (endurance) {
            need(p.getProperty('hw05.endurance_reviewed', 'false') == 'true', 'ENDURANCE_REVIEW_REQUIRED')
            need(threads == 50 && ramp == 20 && duration == 900 && thinkMin == 1000 && thinkRange == 1000,
                 'ENDURANCE_WORKLOAD_MUST_BE_50_20_900_1000_1000')
        } else {
            need(threads >= 1 && threads <= 20 && ramp >= threads && duration > ramp, 'LOAD_WORKLOAD_OUTSIDE_REVIEWED_ENVELOPE')
        }
        need(thinkMin >= 1000 && thinkRange >= 0, 'LOAD_PACING_TOO_AGGRESSIVE')
        [endurance:endurance, threads:threads, ramp:ramp, duration:duration, thinkMin:thinkMin, thinkRange:thinkRange]
    }
    static Map setup(Properties p, File planDir) {
        p.remove(KEY)
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
        Map workload = validateWorkload(p)
        int threads = workload.threads, ramp = workload.ramp, duration = workload.duration
        int thinkMin = workload.thinkMin, thinkRange = workload.thinkRange
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
                   minFreeMiB: minFree, abort: new AtomicBoolean(false), ready: false, journalMutex: new Object(), acceptQuiescent: false]
        need(cfg.connectTimeout > 0 && cfg.responseTimeout > 0, 'TIMEOUTS_MUST_BE_POSITIVE')
        if (workload.endurance) { cfg.endurance = true; cfg.durationSeconds = duration; cfg.rampSeconds = ramp }
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
                            'scripts/category-load-support.groovy', 'scripts/recover-category-load.groovy']
            if (workload.endurance) sources += ['scripts/run-category-load.ps1', 'scripts/check-category-load-run.py']
            cfg.sourceHashes = sources.collectEntries { f -> [(f): sha(new File(root, f).bytes)] }
            Map saved = [schema: 1, createdAt: Instant.now().toString(), runId: runId, engineId: engineId,
                         root: cfg.root, dir: cfg.dir, baseUrl: cfg.baseUrl, connectTimeout: cfg.connectTimeout,
                         responseTimeout: cfg.responseTimeout, minFreeMiB: minFree, adminId: cfg.adminId, adminBefore: cfg.adminBefore,
                         baselineCategories: cfg.baselineCategories, baselineProducts: cfg.baselineProducts,
                         baselineHash: cfg.baselineHash, sourceHashes: cfg.sourceHashes,
                         workload: [threads: threads, rampSeconds: ramp, durationSeconds: duration,
                                    thinkMinMs: thinkMin, thinkRangeMs: thinkRange],
                         runtimeHardware: [logicalProcessors: Runtime.runtime.availableProcessors(), freeRAMMiB: freeMiB],
                         assumptions: ['isolated SUT', 'no product/category changes by other actors', 'no backend restart',
                                       'no read-after-create in measured workflow; exact-name proof deferred to recovery if needed']]
            if (workload.endurance) saved.workload.scenario = 'Endurance'
            writeNew(new File(dir, 'baseline.json'), saved)
            cfg.ready = true
            p.put(KEY, cfg)
            cfg
        } catch (Exception ignored) {
            cfg.activeLock?.release(); cfg.activeChannel?.close()
            throw new IllegalStateException('PREFLIGHT_MANIFEST_FAILED')
        }
    }
    static Map cfg(Binding b) { (Map) b.getVariable('props').get(KEY) }
    static void scrub(Object result) {
        if (result == null) return
        result.setResponseData(new byte[0])
        result.setSamplerData('[REDACTED: request data not retained]')
        result.setRequestHeaders(''); result.setResponseHeaders('')
    }
    static void gate(Binding b) {
        def v = b.getVariable('vars'), sample = b.getVariable('SampleResult')
        sample.setIgnore()
        Map c = cfg(b)
        ['do_login','do_view','do_prepare','do_create','do_delete','do_verify'].each { v.put(it, 'false') }
        if (c == null || !c.ready || c.abort.get() || v.get('pending') == 'true') {
            if (c != null) c.abort.set(true)
            b.getVariable('ctx').getThread().stop()
            return // Any unfinished intent remains in the manifest for out-of-window recovery.
        }
        try {
            if (c.endurance) {
                synchronized (c.journalMutex) {
                    if (c.mainAnchorEpochMs == null) {
                        c.scheduledEndEpochMs = b.getVariable('ctx').getThread().getEndTime()
                        need((c.scheduledEndEpochMs as long) > 0, 'ENDURANCE_SCHEDULER_END_MISSING')
                        c.mainAnchorEpochMs = (c.scheduledEndEpochMs as long) - (c.durationSeconds as long)*1000L
                        writeNew(new File(c.dir as String, 'endurance-timing.json'),
                            [anchorEpochMs:c.mainAnchorEpochMs, scheduledEndEpochMs:c.scheduledEndEpochMs,
                             observedFirstGateEpochMs:System.currentTimeMillis(), durationSeconds:c.durationSeconds,
                             rampSeconds:c.rampSeconds, timestamp:Instant.now().toString(),
                             definition:'Scheduler end minus declared duration; raw JTL timestamps are actual HTTP starts'])
                    }
                }
            }
            need(freePhysicalMiB() >= (c.minFreeMiB as long), 'FREE_RAM_BELOW_REVIEWED_FLOOR')
            ['token','category_id','login_user_id','login_role','login_body','category_body'].each { v.remove(it) }
            v.put('pending', 'false')
            v.put('create_valid', 'false')
            String base = v.get('category_base')
            need(base != null && base ==~ /[A-Za-z][A-Za-z0-9_]*/, 'CSV_EMPTY_OR_EOF')
            int iteration = Integer.parseInt(v.get('iteration_no') ?: '0') + 1
            int thread = b.getVariable('ctx').getThreadNum() + 1
            String intentId = UUID.randomUUID().toString()
            String name = "HW05_23120274_${c.runId}_${c.engineId}_T${thread}_I${iteration}_${base}_${intentId.replace('-', '')}"
            Map owner = [intentId: intentId, thread: thread, iteration: iteration, categoryName: name]
            v.putObject('owner', owner)
            v.put('iteration_no', iteration.toString()); v.put('category_name', name)
            v.put('login_body', JsonOutput.toJson(credentials()))
            v.put('category_body', JsonOutput.toJson([name: name]))
            v.put('do_login', 'true')
        } catch (Exception ignored) {
            b.getVariable('log').error('HW05: iteration memory/input/credential guard failed; no dependent requests permitted')
            c.abort.set(true); b.getVariable('ctx').getThread().stop()
        }
    }
    static void prepare(Binding b) {
        b.getVariable('SampleResult').setIgnore()
        def v = b.getVariable('vars'); Map c = cfg(b)
        v.put('do_create', 'false')
        if (v.get('do_prepare') != 'true' || c.abort.get()) return
        try {
            Map owner = v.getObject('owner') as Map
            need(owner != null && owner.categoryName == v.get('category_name'), 'INTENT_OWNERSHIP_MISMATCH')
            event(c, owner, 'CREATE_INTENT') // Flush+force succeeds BEFORE the HTTP Create becomes eligible.
            v.put('pending', 'true'); v.put('do_create', 'true')
        } catch (Exception ignored) {
            c.abort.set(true)
            b.getVariable('log').error('HW05: durable Create intent could not be established; Create blocked')
        }
    }
    static void deleteGate(Binding b) {
        b.getVariable('SampleResult').setIgnore()
        def v = b.getVariable('vars'); Map c = cfg(b)
        v.put('do_delete', 'false')
        if (v.get('create_valid') != 'true') return
        try {
            Map owner = v.getObject('owner') as Map
            long actual = id(v.get('category_id'))
            need(v.get('pending') == 'true' && owner?.categoryName == v.get('category_name') && owner.actualId == actual, 'DELETE_OWNERSHIP_MISMATCH')
            need(!c.baselineIds.contains(actual) && !c.referenceIds.contains(actual), 'DELETE_BASELINE_OR_REFERENCE_ID')
            // No extra guard GET inside the five-request workflow. This requires isolated SUT ownership.
            event(c, owner, 'DELETE_INTENT', [actualId: actual])
            v.put('do_delete', 'true')
        } catch (Exception ignored) {
            c.abort.set(true)
            b.getVariable('log').error('HW05: owned Delete guard failed; manifest retained for recovery')
        }
    }
    static void assertStep(Binding b, String step) {
        def v = b.getVariable('vars'), prev = b.getVariable('prev'), ar = b.getVariable('AssertionResult')
        Map c = cfg(b); Map owner = v.getObject('owner') as Map
        if (step == 'DELETE') v.put('do_verify', 'true') // Even an uncertain DELETE needs absence verification.
        try {
            need(prev.responseCode == '200', step + '_HTTP_STATUS')
            Object data = json(prev.responseDataAsString)
            switch (step) {
                case 'LOGIN':
                    need(data instanceof Map && data.message == 'Login successful', 'LOGIN_MESSAGE')
                    String token = v.get('token')
                    need(token != null && token == data.token && token ==~ /[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/, 'LOGIN_TOKEN')
                    Map credentials = credentials()
                    need(data.user instanceof Map && data.user.email == credentials.email && id(data.user.id) == (c.adminId as long), 'LOGIN_IDENTITY')
                    need(v.get('login_role') == 'admin' && data.user.role == 'admin' && id(v.get('login_user_id')) == (c.adminId as long), 'LOGIN_ADMIN_ROLE')
                    need(data.user.login_attempts == 0 && data.user.locked_until == null, 'LOGIN_ACCOUNT_STATE')
                    Map claims = (Map) json(new String(Base64.urlDecoder.decode(token.split('\\.')[1]), StandardCharsets.UTF_8))
                    need(id(claims.id) == (c.adminId as long) && claims.role == 'admin', 'LOGIN_CLAIMS')
                    v.put('do_view', 'true')
                    break
                case 'VIEW':
                    need(data instanceof List, 'VIEW_ARRAY'); originalCategoriesIntact(c, data as List)
                    v.put('do_prepare', 'true')
                    break
                case 'CREATE':
                    need(data instanceof Map && data.message == 'Category created', 'CREATE_MESSAGE')
                    long actual = id(v.get('category_id'))
                    need(actual == id(data.id) && !c.baselineIds.contains(actual) && !c.referenceIds.contains(actual), 'CREATE_ID_OWNERSHIP')
                    owner.actualId = actual
                    event(c, owner, 'CREATE_CONFIRMED', [actualId: actual])
                    v.put('create_valid', 'true')
                    break
                case 'DELETE':
                    need(data instanceof Map && data.message == 'Category deleted', 'DELETE_MESSAGE')
                    event(c, owner, 'DELETE_ACKNOWLEDGED', [actualId: id(v.get('category_id'))])
                    break
                case 'VERIFY':
                    need(data instanceof List, 'VERIFY_ARRAY'); originalCategoriesIntact(c, data as List)
                    long actual = id(v.get('category_id'))
                    need(!data.any { id(it.id) == actual || it.name == owner.categoryName }, 'VERIFY_OWNED_CATEGORY_STILL_PRESENT')
                    event(c, owner, 'ABSENCE_VERIFIED', [actualId: actual])
                    v.put('pending', 'false'); v.put('create_valid', 'false')
                    break
                default: throw new IllegalStateException('UNKNOWN_ASSERTION_STEP')
            }
        } catch (Exception ignored) {
            c?.abort?.set(true)
            ar.setFailure(true); ar.setFailureMessage('HW05_' + step + '_CONTRACT_OR_GUARD_FAILED')
            prev.setSuccessful(false)
            if (step == 'CREATE') v.put('create_valid', 'false')
            try { if (v.get('pending') == 'true') event(c, owner, 'RECOVERY_REQUIRED', [code: step + '_FAILED']) } catch (Exception ignoredAgain) { }
        } finally {
            if (c?.endurance) prev.setResponseMessage(prev.responseCode ==~ /[0-9]{3}/ ? 'HW05_HTTP_' + prev.responseCode : 'HW05_TRANSPORT_FAILURE')
            scrub(prev) // Extractors have already run. Never retain raw Login/Authorization data in listeners/JTL.
            if (step == 'LOGIN') v.remove('login_body')
            if (step == 'VERIFY') { v.remove('token'); v.remove('category_body') }
        }
    }
    static Map readRun(File dir) {
        Map saved = (Map) json(new File(dir, 'baseline.json').getText('UTF-8'))
        need(saved.schema == 1 && dir.canonicalPath == new File(saved.dir as String).canonicalPath, 'RECOVERY_RUN_PATH_MISMATCH')
        need(sha(canon(saved.baselineCategories as List).getBytes(StandardCharsets.UTF_8)) == saved.baselineHash, 'RECOVERY_BASELINE_HASH_MISMATCH')
        need(saved.runId ==~ /[A-Za-z0-9_-]{1,64}/ && saved.engineId ==~ /[A-Za-z0-9_-]{1,32}/, 'RECOVERY_NAMESPACE_INVALID')
        saved.baselineIds = saved.baselineCategories.collect { id(it.id) }.toSet()
        saved.referenceIds = saved.baselineProducts.findAll { it.category_id != null }.collect { id(it.category_id) }.toSet()
        saved.journalMutex = new Object()
        saved
    }
    static Map intents(Map c) {
        Map records = [:]
        new File(c.dir as String).listFiles().findAll { it.name ==~ /thread-[0-9]+\.jsonl/ }.sort { it.name }.each { file ->
            file.getText('UTF-8').readLines().each { line ->
                need(!line.trim().isEmpty(), 'MANIFEST_EMPTY_OR_TORN_LINE')
                Map e
                try { e = (Map) json(line) } catch (Exception ignored) { throw new IllegalStateException('MANIFEST_TORN_LINE_REVIEW_REQUIRED') }
                need(e.schema == 1 && e.runId == c.runId && e.engineId == c.engineId && e.baselineHash == c.baselineHash, 'MANIFEST_CONTEXT_MISMATCH')
                need(e.intentId instanceof String && e.categoryName instanceof String && e.categoryName.startsWith('HW05_23120274_' + c.runId + '_' + c.engineId + '_'), 'MANIFEST_OWNER_INVALID')
                if (!records.containsKey(e.intentId)) {
                    need(e.stepName == 'CREATE_INTENT', 'MANIFEST_MISSING_INTENT')
                    records[e.intentId] = [intentId: e.intentId, categoryName: e.categoryName, thread: e.thread, iteration: e.iteration]
                }
                Map owner = records[e.intentId]
                need(owner.categoryName == e.categoryName && owner.thread == e.thread && owner.iteration == e.iteration, 'MANIFEST_OWNER_CHANGED')
                if (e.actualId != null) {
                    long actual = id(e.actualId)
                    need(owner.actualId == null || owner.actualId == actual, 'MANIFEST_ID_CHANGED')
                    need(!c.baselineIds.contains(actual), 'MANIFEST_BASELINE_ID')
                    owner.actualId = actual
                }
                if (e.stepName == 'ABSENCE_VERIFIED') owner.verified = true
            }
        }
        need(records.values().collect { it.categoryName }.toSet().size() == records.size(), 'MANIFEST_DUPLICATE_NAME')
        records
    }
    static Map recover(Map c, boolean acceptQuiescent) {
        Map records = intents(c)
        // Fail closed before DELETE if source/config, baseline originals or products changed.
        c.sourceHashes.each { f, expected -> need(sha(new File(c.root as String, f.toString()).bytes) == expected, 'RECOVERY_SOURCE_OR_CONFIG_CHANGED') }
        Map auth = authenticate(c)
        List products = list(c, '/api/products', auth.token as String)
        need(canon(products) == canon(c.baselineProducts as List), 'RECOVERY_PRODUCTS_CHANGED')
        List categories = list(c, '/api/categories', auth.token as String)
        originalCategoriesIntact(c, categories)
        Set productRefs = products.findAll { it.category_id != null }.collect { id(it.category_id) }.toSet()
        List unresolved = []
        int deleted = 0
        records.values().each { Map owner ->
            categories = list(c, '/api/categories', auth.token as String)
            originalCategoriesIntact(c, categories)
            List matches = categories.findAll { it.name == owner.categoryName }
            need(matches.size() <= 1, 'RECOVERY_AMBIGUOUS_NAME')
            if (owner.actualId != null) {
                Map idRow = categories.find { id(it.id) == (owner.actualId as long) }
                need(idRow == null || idRow.name == owner.categoryName, 'RECOVERY_ID_REUSED_OR_RESTART')
            }
            if (matches) {
                Map row = matches[0]; long actual = id(row.id)
                need(owner.actualId == null || owner.actualId == actual, 'RECOVERY_ID_NAME_MISMATCH')
                need(!c.baselineIds.contains(actual) && !productRefs.contains(actual), 'RECOVERY_BASELINE_OR_PRODUCT_REFERENCE')
                // Re-read products immediately before a guarded recovery DELETE, outside measured workload.
                List currentProducts = list(c, '/api/products', auth.token as String)
                need(canon(currentProducts) == canon(c.baselineProducts as List) && !currentProducts.any { it.category_id != null && id(it.category_id) == actual }, 'RECOVERY_REFERENCE_CHANGED')
                append(new File(c.dir as String, 'recovery.jsonl'), [timestamp: Instant.now().toString(), stepName: 'GUARDED_RECOVERY_DELETE', intentId: owner.intentId, categoryName: owner.categoryName, actualId: actual], c.journalMutex)
                Map r = http(c, 'DELETE', '/api/categories/' + actual, null, auth.token as String)
                need(r.status == 200 && r.data.message == 'Category deleted', 'RECOVERY_DELETE_FAILED')
                deleted++
                categories = list(c, '/api/categories', auth.token as String)
                need(!categories.any { id(it.id) == actual || it.name == owner.categoryName }, 'RECOVERY_DELETE_NOT_VERIFIED')
            } else if (owner.actualId == null && !acceptQuiescent) {
                // Finite observation does not rule out late writes. Independent recovery needs an explicit quiescence acknowledgement.
                unresolved << owner.intentId
            }
        }
        List finalCats = list(c, '/api/categories', auth.token as String)
        List finalProducts = list(c, '/api/products', auth.token as String)
        Map account = adminState(c, auth.token as String)
        boolean categoriesSame = canon(finalCats) == canon(c.baselineCategories as List)
        boolean productsSame = canon(finalProducts) == canon(c.baselineProducts as List)
        boolean ownedAbsent = !finalCats.any { row -> records.values().any { it.categoryName == row.name } }
        Map result = [timestamp: Instant.now().toString(), cleanupStatus: categoriesSame && productsSame && ownedAbsent && unresolved.isEmpty() ? 'VERIFIED' : 'RECOVERY_REQUIRED',
                      categoriesUnchanged: categoriesSame, productsUnchanged: productsSame, ownedAbsent: ownedAbsent,
                      adminAfter: account, unresolvedIntents: unresolved, recoveryDeletes: deleted,
                      quiescenceAcknowledged: acceptQuiescent]
        append(new File(c.dir as String, 'recovery.jsonl'), [stepName: 'RECOVERY_RESULT'] + result, c.journalMutex)
        result
    }
    static void teardown(Binding b) {
        b.getVariable('SampleResult').setIgnore()
        Map c = cfg(b)
        if (c == null) return
        c.ready = false
        if (c.endurance) {
            try {
                writeNew(new File(c.dir as String, 'endurance-stop.json'),
                    [mainStoppedEpochMs:System.currentTimeMillis(), timestamp:Instant.now().toString(),
                     stoppedByGuard:c.abort.get(), scheduledEndEpochMs:c.scheduledEndEpochMs])
            } catch (Exception ignored) { c.abort.set(true) }
        }
        try {
            Map result = recover(c, false)
            b.getVariable('props').put('hw05.cleanup_status', result.cleanupStatus)
            append(new File(c.dir as String, 'recovery.jsonl'),
                   [timestamp: Instant.now().toString(), stepName: 'RUN_SUMMARY',
                    performanceStatus: c.abort.get() ? 'STOPPED_BY_GUARD' : 'SCHEDULE_FINISHED_NOT_A_PERFORMANCE_PASS',
                    cleanupStatus: result.cleanupStatus], c.journalMutex)
            b.getVariable('log').info('HW05 cleanup=' + result.cleanupStatus + '; performanceFailed=' + c.abort.get() + '; manifest=' + c.dir)
        } catch (Exception ignored) {
            b.getVariable('props').put('hw05.cleanup_status', 'RECOVERY_REQUIRED')
            b.getVariable('log').error('HW05: post-run recovery/baseline/admin verification failed; inspect preserved manifest')
        } finally {
            c.activeLock?.release(); c.activeChannel?.close()
        }
    }
}

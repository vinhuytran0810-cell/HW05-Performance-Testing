// Independent recovery entry point. Not run by static validation.
// Preview (no HTTP): java -cp "<JMETER_HOME>/lib/*" groovy.ui.GroovyMain scripts/recover-category-load.groovy RUN_DIR
// Apply only AFTER the SUT is quiescent and isolated: add --apply --quiescent --isolated.
// Credentials are read exclusively from ESHOP_ADMIN_EMAIL / ESHOP_ADMIN_PASSWORD.
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

if (args.length < 1) throw new IllegalArgumentException('Expected manifest run directory')
File runDir = new File(args[0]).canonicalFile
// Run from the project root, or override the helper path for packaged deployments.
String helperOverride = System.getProperty('hw05.support_file')
File support = helperOverride ? new File(helperOverride) : new File('scripts/category-load-support.groovy')
if (!support.isFile()) throw new IllegalStateException('Set -Dhw05.support_file to category-load-support.groovy')
Class supportClass = new GroovyClassLoader(getClass().classLoader).parseClass(support)
Map cfg = supportClass.readRun(runDir)
Map records = supportClass.intents(cfg)
if (!args.contains('--apply')) {
    println('PREVIEW ONLY; no HTTP or deletion. Owned intents=' + records.size() + '; run=' + cfg.runId + '; engine=' + cfg.engineId)
    return
}
if (!args.contains('--quiescent') || !args.contains('--isolated')) {
    throw new IllegalArgumentException('Apply requires explicit --quiescent and --isolated acknowledgements')
}
FileChannel channel = FileChannel.open(new File(runDir, 'active.lock').toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
def lock = channel.tryLock()
if (lock == null) { channel.close(); throw new IllegalStateException('Run/recovery is still active; refusing cleanup') }
int exitCode = 0
try {
    Map result = supportClass.recover(cfg, true)
    println('Recovery status=' + result.cleanupStatus + '; admin/baseline checks performed; details appended to recovery.jsonl')
    if (result.cleanupStatus != 'VERIFIED') exitCode = 2
} catch (Exception ignored) {
    System.err.println('RECOVERY_FAILED: no broad deletion or database reset attempted; inspect manifest and request review')
    exitCode = 2
} finally { lock.release(); channel.close() }
if (exitCode != 0) System.exit(exitCode)

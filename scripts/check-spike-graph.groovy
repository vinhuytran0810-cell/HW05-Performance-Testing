import groovy.json.JsonOutput
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import org.apache.jmeter.util.JMeterUtils
import org.apache.jmeter.visualizers.RespTimeGraphVisualizer
import org.apache.jmeter.reporters.ResultCollector
import javax.swing.SwingUtilities

// Offline JTL rendering validation only. No test engine, HTTP sampler, window or screenshot.
int exitCode = 2
Map result = [kind:'offline_response_time_graph_check', checkedAt:java.time.Instant.now().toString(),
              status:'REVIEW_REQUIRED', noEngineOrSutRequests:true]
File out = null
try {
    if (args.length != 2) throw new IllegalArgumentException('ARGUMENTS_REQUIRED')
    File runDir = new File(args[0]).canonicalFile
    File jm = new File(args[1]).canonicalFile
    out = new File(runDir, 'graph-check.json')
    if (out.exists()) out = new File(runDir, 'graph-recheck-' + System.currentTimeMillis() + '.json')
    JMeterUtils.setJMeterHome(jm.path)
    JMeterUtils.loadJMeterProperties(new File(jm, 'bin/jmeter.properties').path)
    JMeterUtils.initLocale()
    Properties props = new Properties()
    new File('test-plans/spike-results.properties').withInputStream { props.load(it) }
    JMeterUtils.getJMeterProperties().putAll(props)
    def button
    button = { component, String text ->
        if (component instanceof javax.swing.JButton && component.text == text) return component
        if (component instanceof java.awt.Container) {
            for (child in component.components) { def found = button(child, text); if (found != null) return found }
        }
        null
    }
    def graph
    SwingUtilities.invokeAndWait {
        graph = new RespTimeGraphVisualizer()
        ResultCollector collector = (ResultCollector) graph.createTestElement()
        collector.setProperty('RespTimeGraph.interval','1000')
        collector.setFilename(new File(runDir, 'samples.jtl').path)
        graph.configure(collector)
        button(graph, JMeterUtils.getResString('graph_resp_time_interval_reload')).doClick()
        collector.setListener(graph)
        collector.loadExistingFile()
    }
    // Graph.add queues updates on the Swing event queue; the next barrier drains those updates.
    SwingUtilities.invokeAndWait {
        button(graph, JMeterUtils.getResString('aggregate_graph_display')).doClick()
        double[][] data = graph.getData()
        if (data == null || data.length == 0 || !data.any { it != null && it.length > 0 })
            throw new IllegalStateException('GRAPH_HAS_NO_DATA')
        result.series = data.length
        result.pointsPerSeries = data.collect { it.length }
        result.intervalMs = 1000
        result.status = 'RAW_JTL_LOADED_AND_GRAPH_BUILT'
    }
    exitCode = 0
} catch (Throwable failure) {
    def cause = failure
    while (cause.cause != null) cause = cause.cause
    result.failureClass = cause.class.name
    result.failureFrames = cause.stackTrace.take(5).collect { it.className + '.' + it.methodName }
    result.reason = 'Offline graph load/build failed; inspect preserved raw JTL and local JMeter configuration.'
} finally {
    if (out != null) {
        try { Files.write(out.toPath(), (JsonOutput.prettyPrint(JsonOutput.toJson(result))+'\n').getBytes('UTF-8'), StandardOpenOption.CREATE_NEW) }
        catch (Throwable ignored) { exitCode = 2 }
    }
}
println('OFFLINE_GRAPH_CHECK: ' + result.status)
System.exit(exitCode)

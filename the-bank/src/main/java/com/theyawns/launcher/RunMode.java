package com.theyawns.launcher;

/** RunMode is used to control various aspects of how the code runs.
 *  Implemented features:
 *      NONE
 *  Planned features:
 *     Run Duration: Demo will run continuously, Benchmark will run for a fixed but configurable length
 *     Dashboard:  Demo will pump stats to Grafana, benchmark will not
 *                 (It is intended that a separate reduction task will run after benchmark completion)
 *     Work distribution: It's possible that in benchmark mode, the same rules will be run in
 *                 IMDG pipelines and Jet executors to get comparative performance results.
 *                 An alternative would be to collect these results from separate runs.
 *     Result maps:  Because the demo can be long-running (all day at a trade show, for example),
 *                 transaction results are discarded after the result is calculated.  For benchmarks
 *                 the data is kept as input to the data reduction task (as yet unwritten)
 */
public enum RunMode {
    Demo,
    Benchmark
}

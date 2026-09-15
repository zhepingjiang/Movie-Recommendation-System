package com.movierec.streaming;

import java.time.Instant;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * Phase 0 scaffolding: proves a job can be submitted to and run continuously on the Flink
 * cluster with no Kafka/JDBC involved yet -- those are isolated to later phases so a
 * connector-version mismatch doesn't get tangled up with a cluster-wiring problem.
 */
public class HelloWorldJob {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStreamSource<Long> ticks = env.fromSequence(1, Long.MAX_VALUE);
        ticks.map(
                        tick -> {
                            Thread.sleep(1000);
                            return "hello from Flink, tick " + tick + " at " + Instant.now();
                        })
                .print();

        env.execute("hello-world-job");
    }
}

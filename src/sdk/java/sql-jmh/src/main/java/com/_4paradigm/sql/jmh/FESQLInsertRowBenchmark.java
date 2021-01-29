package com._4paradigm.sql.jmh;

import com._4paradigm.sql.BenchmarkConfig;
import com._4paradigm.sql.ResultSet;
import com._4paradigm.sql.SQLInsertRow;
import com._4paradigm.sql.SQLInsertRows;
import com._4paradigm.sql.sdk.SdkOption;
import com._4paradigm.sql.sdk.SqlExecutor;
import com._4paradigm.sql.sdk.impl.SqlClusterExecutor;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Threads(10)
@Fork(value = 1, jvmArgs = {"-Xms4G", "-Xmx4G"})
@Warmup(iterations = 1)
public class FESQLInsertRowBenchmark {
    private SqlExecutor executor;
    private SdkOption option;
    private String db = "db" + System.nanoTime();
    private String ddl = "create table perf (col1 string, col2 bigint, " +
            "col3 float," +
            "col4 double," +
            "col5 string," +
            "index(key=col1, ts=col2));";
    private String ddl1 = "create table perf2 (col1 string, col2 bigint, " +
            "col3 float," +
            "col4 double," +
            "col5 string," +
            "index(key=col1, ts=col2));";
    private boolean setupOk = false;
    private int recordSize = 10000;
    private String format = "insert into perf values(?, ?, 100.0, 200.0, 'hello world');";
    private String format2 = "insert into perf2 values('%s', %d, 100.0, 200.0, 'hello world');";
    private long counter = 0;
    public FESQLInsertRowBenchmark() {
        SdkOption sdkOption = new SdkOption();
        sdkOption.setSessionTimeout(30000);
        sdkOption.setZkCluster(BenchmarkConfig.ZK_CLUSTER);
        sdkOption.setZkPath(BenchmarkConfig.ZK_PATH);
        this.option = sdkOption;
        try {
            executor = new SqlClusterExecutor(option);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Setup
    public void setup() {
        setupOk = executor.createDB(db);
        if (!setupOk) {
            return;
        }
        setupOk = executor.executeDDL(db, ddl);
        if (!setupOk) {
            return;
        }
        setupOk = executor.executeDDL(db, ddl1);
        if (!setupOk) {
            return;
        }
        for (int i = 0; i < recordSize/100; i++) {
            String sql = String.format(format2, "pkxxx" + i, System.currentTimeMillis());
            executor.executeInsert(db, sql);
        }
    }

    @Benchmark
    public void insertBm() {
        long idx = counter;
        /*
        String s1 = "pkxxx" + idx;
        SQLInsertRow row = executor.getInsertRow(db, format);
        row.Init(s1.length());
        row.AppendString(s1);
        row.AppendInt64(System.currentTimeMillis());

         */
        SQLInsertRows rows = executor.getInsertRows(db, format);
        for (int i = 0; i < 10; i++) {
            String s1 = "pkxxx" + idx + i;
            SQLInsertRow row = rows.NewRow();
            row.Init(s1.length());
            row.AppendString(s1);
            row.AppendInt64(System.currentTimeMillis());
        }
        try {
            executor.executeInsert(db, format, rows);
            //counter ++;
            counter += 10;
        } catch (Exception e) {

        }

    }

    @Benchmark
    public void selectSimpleBm() {
        String sql = "select col1, col2, col3 from perf2 limit 10;";
        ResultSet rs = executor.executeSQL(db, sql);
    }

    @Benchmark
    public void select150Feature() {
        String sql = "select col1, col2, col3";
        for (int i = 0; i < 50; i++) {
            sql += String.format(", col1 as col1%d, col2 as col2%d, col3 as col3%d", i, i, i);
        }
        sql += " from perf2 limit 1;";
        ResultSet rs = executor.executeSQL(db, sql);
    }

    @Benchmark
    public void select510Feature() {
        String sql = "select col1, col2, col3";
        for (int i = 0; i < 170; i++) {
            sql += String.format(", col1 as col1%d, col2 as col2%d, col3 as col3%d", i, i, i);
        }
        sql += " from perf2 limit 1;";
        ResultSet rs = executor.executeSQL(db, sql);
    }

    public static void main(String[] args) throws RunnerException {

        Options opt = new OptionsBuilder()
                .include(FESQLInsertRowBenchmark.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
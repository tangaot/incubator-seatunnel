/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.core.starter.seatunnel.jvm;

import org.apache.seatunnel.common.exception.CommonErrorCode;

import lombok.SneakyThrows;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses JVM options from a file and prints a single line with all JVM options to standard output.
 */
@SuppressWarnings("checkstyle:InnerTypeLast")
final class JvmOptionsParser {

    /**
     * The main entry point. The exit code is 0 if the JVM options were successfully parsed,
     * otherwise the exit code is 1. If an improperly formatted line is discovered, the line is
     * output to standard error.
     */
    @SuppressWarnings("checkstyle:RegexpSingleline")
    public static void main(final String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Expected one arguments specifying path to PATH_CONF, but was "
                            + Arrays.toString(args));
        }

        final JvmOptionsParser parser = new JvmOptionsParser();
        final List<String> jvmOptions = parser.readJvmOptionsFiles(Paths.get(args[0]));
        System.out.println(String.join(" ", jvmOptions));
    }

    @SneakyThrows
    List<String> readJvmOptionsFiles(final Path config) {
        final ArrayList<Path> jvmOptionsFiles = new ArrayList<>();
        jvmOptionsFiles.add(config.resolve("jvm_options"));

        final List<String> jvmOptions = new ArrayList<>();

        for (final Path jvmOptionsFile : jvmOptionsFiles) {
            final SortedMap<Integer, String> invalidLines = new TreeMap<>();
            try (InputStream is = Files.newInputStream(jvmOptionsFile);
                    Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(reader)) {
                parse(
                        JavaVersion.majorVersion(JavaVersion.CURRENT),
                        br,
                        jvmOptions::add,
                        invalidLines::put);
            }
            if (!invalidLines.isEmpty()) {
                throw new JvmOptionsParserException(
                        CommonErrorCode.IMPROPERLY_FORMATTED_JVM_OPTION,
                        String.format(
                                Locale.ROOT,
                                "encountered [%d] error%s parsing [%s]",
                                invalidLines.size(),
                                invalidLines.size() == 1 ? "" : "s",
                                jvmOptionsFile));
            }
        }
        return jvmOptions;
    }

    /** Callback for valid JVM options. */
    interface JvmOptionConsumer {
        /**
         * Invoked when a line in the JVM options file matches the specified syntax and the
         * specified major version.
         *
         * @param jvmOption the matching JVM option
         */
        void accept(String jvmOption);
    }

    /** Callback for invalid lines in the JVM options. */
    interface InvalidLineConsumer {
        /** Invoked when a line in the JVM options does not match the specified syntax. */
        void accept(int lineNumber, String line);
    }

    private static final Pattern PATTERN =
            Pattern.compile("((?<start>\\d+)(?<range>-)?(?<end>\\d+)?:)?(?<option>-.*)$");

    /**
     * Parse the line-delimited JVM options from the specified buffered reader for the specified
     * Java major version. Valid JVM options are:
     *
     * <ul>
     *   <li>a line starting with a dash is treated as a JVM option that applies to all versions
     *   <li>a line starting with a number followed by a colon is treated as a JVM option that
     *       applies to the matching Java major version only
     *   <li>a line starting with a number followed by a dash followed by a colon is treated as a
     *       JVM option that applies to the matching Java specified major version and all larger
     *       Java major versions
     *   <li>a line starting with a number followed by a dash followed by a number followed by a
     *       colon is treated as a JVM option that applies to the specified range of matching Java
     *       major versions
     * </ul>
     *
     * For example, if the specified Java major version is 8, the following JVM options will be
     * accepted:
     *
     * <ul>
     *   <li>{@code -XX:+PrintGCDateStamps}
     *   <li>{@code 8:-XX:+PrintGCDateStamps}
     *   <li>{@code 8-:-XX:+PrintGCDateStamps}
     *   <li>{@code 7-8:-XX:+PrintGCDateStamps}
     * </ul>
     *
     * and the following JVM options will not be accepted:
     *
     * <ul>
     *   <li>{@code
     *       9:-Xlog:age*=trace,gc*,safepoint:file=logs/gc.log:utctime,pid,tags:filecount=32,filesize=64m}
     *   <li>{@code
     *       9-:-Xlog:age*=trace,gc*,safepoint:file=logs/gc.log:utctime,pid,tags:filecount=32,filesize=64m}
     *   <li>{@code
     *       9-10:-Xlog:age*=trace,gc*,safepoint:file=logs/gc.log:utctime,pid,tags:filecount=32,filesize=64m}
     * </ul>
     *
     * If the version syntax specified on a line matches the specified JVM options, the JVM option
     * callback will be invoked with the JVM option. If the line does not match the specified syntax
     * for the JVM options, the invalid line callback will be invoked with the contents of the
     * entire line.
     *
     * @param javaMajorVersion the Java major version to match JVM options against
     * @param br the buffered reader to read line-delimited JVM options from
     * @param jvmOptionConsumer the callback that accepts matching JVM options
     * @param invalidLineConsumer a callback that accepts invalid JVM options
     * @throws IOException if an I/O exception occurs reading from the buffered reader
     */
    @SneakyThrows
    static void parse(
            final int javaMajorVersion,
            final BufferedReader br,
            final JvmOptionConsumer jvmOptionConsumer,
            final InvalidLineConsumer invalidLineConsumer) {
        int lineNumber = 0;
        while (true) {
            final String line = br.readLine();
            lineNumber++;
            if (line == null) {
                break;
            }
            if (line.startsWith("#")) {
                // lines beginning with "#" are treated as comments
                continue;
            }
            if (line.matches("\\s*")) {
                // skip blank lines
                continue;
            }
            final Matcher matcher = PATTERN.matcher(line);
            if (matcher.matches()) {
                final String start = matcher.group("start");
                final String end = matcher.group("end");
                if (start == null) {
                    // no range present, unconditionally apply the JVM option
                    jvmOptionConsumer.accept(line);
                } else {
                    final int lower;
                    try {
                        lower = Integer.parseInt(start);
                    } catch (final NumberFormatException e) {
                        invalidLineConsumer.accept(lineNumber, line);
                        continue;
                    }
                    final int upper;
                    if (matcher.group("range") == null) {
                        // no range is present, apply the JVM option to the specified major version
                        // only
                        upper = lower;
                    } else if (end == null) {
                        // a range of the form \\d+- is present, apply the JVM option to all major
                        // versions larger than the specified one
                        upper = Integer.MAX_VALUE;
                    } else {
                        // a range of the form \\d+-\\d+ is present, apply the JVM option to the
                        // specified range of major versions
                        try {
                            upper = Integer.parseInt(end);
                        } catch (final NumberFormatException e) {
                            invalidLineConsumer.accept(lineNumber, line);
                            continue;
                        }
                        if (upper < lower) {
                            invalidLineConsumer.accept(lineNumber, line);
                            continue;
                        }
                    }
                    if (lower <= javaMajorVersion && javaMajorVersion <= upper) {
                        jvmOptionConsumer.accept(matcher.group("option"));
                    }
                }
            } else {
                invalidLineConsumer.accept(lineNumber, line);
            }
        }
    }
}

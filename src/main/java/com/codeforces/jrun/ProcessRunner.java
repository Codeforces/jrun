package com.codeforces.jrun;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * The only public class in library with the only public method run().
 *
 * @author Mike Mirzayanov (mirzayanovmr@gmail.com)
 */
public class ProcessRunner {
    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final int TRUNCATE_LIMIT = 5 * 1024 * 1024;

    /**
     * @param directory   Directory.
     * @param commandLine Command line.
     * @return Returns string array. Each element is a single item in terms of command line argument.
     *         Works with quotes correct.
     */
    private static String[] parseCommandLine(File directory, String commandLine) {
        // Seems to be file name path?
        if (new File(directory, commandLine).exists()) {
            return new String[]{commandLine};
        }

        // Hack to generalize situation.
        commandLine += " ";

        // Number of slashes in the last block modulo 2.
        int slashes = 0;

        // If we are inside quotes.
        boolean quoted = false;

        // Current item.
        StringBuilder current = new StringBuilder();

        // Result.
        List<String> items = new ArrayList<>();

        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);

            if (c == '\\') {
                slashes ^= 1;
                if (slashes == 0) {
                    current.append('\\');
                }
            } else {
                if (c == '\"') {
                    if (slashes == 0) {
                        quoted = !quoted;
                    } else {
                        current.append('\"');
                    }
                } else {
                    if (slashes == 1) {
                        current.append('\\');
                    }
                    if (c <= ' ' && !quoted) {
                        if (current.length() > 0) {
                            items.add(current.toString());
                            current.setLength(0);
                        }
                    } else {
                        current.append(c);
                    }
                }
                slashes = 0;
            }
        }

        return items.toArray(new String[items.size()]);
    }

    private static <T> T timedCall(Callable<T> c, long timeout, TimeUnit timeUnit)
            throws InterruptedException, ExecutionException, TimeoutException {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            FutureTask<T> task = new FutureTask<>(c);
            pool.execute(task);
            return task.get(timeout, timeUnit);
        } finally {
            pool.shutdown();
        }
    }

    /**
     * <p>
     * Executes command line and returns its exitCode, output
     * (stdout) and error (stderr). Output and error will be
     * truncated to 5MB if needed. Exit code will be equal
     * to -1 if some error happened and it is impossible to
     * run process. You may check comment in order to get
     * details.
     * </p>
     * <p>
     * It will use ProcessBuilder.
     * </p>
     * <p>
     * Doesn't support non-ASCII characters in directory
     * or commandLine.
     * </p>
     *
     * @param commandLine Process command line in
     *                    form "exec_file param_1 param_2 ... param_n".
     * @param params      Encapsulates directory, time limit (in milliseconds), redirection files.
     * @return Result: exitCode, output, error and comment.
     */
    public static Outcome run(String commandLine, final Params params) {
        String[] tokens = parseCommandLine(params.getDirectory(), commandLine);

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(params.getDirectory());
        processBuilder.command(tokens);

        final StringBuilder output = new StringBuilder();
        final StringBuilder error = new StringBuilder();
        final Process process;

        final List<String> errors = new ArrayList<>();
        int exitCode = -1;

        try {
            process = processBuilder.start();

            Thread writeInputThread = null;
            if (params.getRedirectInputFile() != null) {
                writeInputThread = new Thread() {
                    @Override
                    public void run() {
                        OutputStream outputStream = new BufferedOutputStream(process.getOutputStream());
                        InputStream inputStream = null;

                        try {
                            inputStream = new BufferedInputStream(new FileInputStream(params.getRedirectInputFile()));

                            byte[] buffer = new byte[BUFFER_SIZE];
                            while (true) {
                                int readByteCount = inputStream.read(buffer);

                                if (readByteCount == -1) {
                                    break;
                                } else {
                                    outputStream.write(buffer, 0, readByteCount);
                                    outputStream.flush();
                                }
                            }
                        } catch (FileNotFoundException ignored) {
                            errors.add("Can't find input file " + params.getRedirectInputFile() + '.');
                        } catch (IOException ignored) {
                            errors.add("Can't read input file " + params.getRedirectInputFile() + '.');
                        } finally {
                            try {
                                if (inputStream != null) {
                                    inputStream.close();
                                }
                            } catch (IOException ignored) {
                                // No operations.
                            }

                            try {
                                outputStream.close();
                            } catch (IOException ignored) {
                                // No operations.
                            }
                        }
                    }
                };
            } else {
                process.getOutputStream().close();
            }

            Thread readOutputThread = new Thread() {
                @Override
                public void run() {
                    final InputStream outputInputStream = process.getInputStream();
                    BufferedReader reader = null;
                    if (params.getRedirectOutputFile() == null) {
                        reader = new BufferedReader(new InputStreamReader(outputInputStream));
                    }

                    FileOutputStream redirectOutputStream;
                    try {
                        redirectOutputStream = params.getRedirectOutputFile() == null
                                ? null
                                : new FileOutputStream(params.getRedirectOutputFile());
                    } catch (IOException ignored) {
                        errors.add("Can't write output file " + params.getRedirectOutputFile() + '.');
                        return;
                    }

                    try {
                        final char[] charBuffer = new char[BUFFER_SIZE];
                        final byte[] byteBuffer = new byte[BUFFER_SIZE];

                        while (true) {
                            if (params.getRedirectOutputFile() == null) {
                                int readCharCount = reader.read(charBuffer);
                                if (readCharCount == -1) {
                                    break;
                                }
                                if (output.length() < TRUNCATE_LIMIT) {
                                    readCharCount = Math.min(readCharCount, TRUNCATE_LIMIT - output.length());
                                    output.append(charBuffer, 0, readCharCount);
                                }
                            } else {
                                int readByteCount = outputInputStream.read(byteBuffer);
                                if (readByteCount == -1) {
                                    break;
                                }
                                redirectOutputStream.write(byteBuffer, 0, readByteCount);
                            }
                        }
                    } catch (IOException ignored) {
                        errors.add("Can't handle output of the process.");
                    } finally {
                        try {
                            if (redirectOutputStream != null) {
                                redirectOutputStream.close();
                            }
                        } catch (IOException ignored) {
                            // No operations.
                        }

                        try {
                            if (reader != null) {
                                reader.close();
                            }
                        } catch (IOException ignored) {
                            // No operations.
                        }

                        try {
                            if (outputInputStream != null) {
                                outputInputStream.close();
                            }
                        } catch (IOException ignored) {
                            // No operations.
                        }
                    }
                }
            };

            Thread readErrorThread = new Thread() {
                @Override
                public void run() {
                    final InputStream errorInputStream = process.getErrorStream();
                    BufferedReader reader = null;
                    if (params.getRedirectErrorFile() == null) {
                        reader = new BufferedReader(new InputStreamReader(errorInputStream));
                    }

                    FileOutputStream redirectErrorStream;
                    try {
                        redirectErrorStream = params.getRedirectErrorFile() == null
                                ? null
                                : new FileOutputStream(params.getRedirectErrorFile());
                    } catch (IOException ignored) {
                        errors.add("Can't write error file " + params.getRedirectErrorFile() + '.');
                        return;
                    }

                    try {
                        final char[] charBuffer = new char[BUFFER_SIZE];
                        final byte[] byteBuffer = new byte[BUFFER_SIZE];

                        while (true) {
                            if (params.getRedirectErrorFile() == null) {
                                int readCharCount = reader.read(charBuffer);
                                if (readCharCount == -1) {
                                    break;
                                }
                                if (error.length() < TRUNCATE_LIMIT) {
                                    readCharCount = Math.min(readCharCount, TRUNCATE_LIMIT - error.length());
                                    error.append(charBuffer, 0, readCharCount);
                                }
                            } else {
                                int readByteCount = errorInputStream.read(byteBuffer);
                                if (readByteCount == -1) {
                                    break;
                                }
                                redirectErrorStream.write(byteBuffer, 0, readByteCount);
                            }
                        }
                    } catch (IOException ignored) {
                        errors.add("Can't handle error of the process.");
                    } finally {
                        try {
                            if (redirectErrorStream != null) {
                                redirectErrorStream.close();
                            }
                        } catch (IOException ignored) {
                            // No operations.
                        }

                        try {
                            if (reader != null) {
                                reader.close();
                            }
                        } catch (IOException ignored) {
                            // No operations.
                        }

                        try {
                            if (errorInputStream != null) {
                                errorInputStream.close();
                            }
                        } catch (IOException ignored) {
                            // No operations.
                        }
                    }
                }
            };

            if (writeInputThread != null) {
                writeInputThread.start();
            }
            readOutputThread.start();
            readErrorThread.start();

            try {
                exitCode = timedCall(new Callable<Integer>() {
                    public Integer call() throws Exception {
                        return process.waitFor();
                    }
                }, params.getTimeLimit() == 0 ? Integer.MAX_VALUE : params.getTimeLimit(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
                errors.add("Process timed out [timeLimit=" + params.getTimeLimit() + ']');
            } catch (ExecutionException ignored) {
                errors.add("Process failed [commandLine=" + commandLine + ']');
            } finally {
                if (writeInputThread != null) {
                    writeInputThread.join(TimeUnit.MINUTES.toMillis(5));
                }
                readOutputThread.join(TimeUnit.MINUTES.toMillis(5));
                readErrorThread.join(TimeUnit.MINUTES.toMillis(5));
                process.destroy();
            }

            return new Outcome(exitCode, output.toString(), error.toString(), errors);
        } catch (Exception e) {
            return new Outcome(
                    -1,
                    output.toString(),
                    error.toString(),
                    Arrays.asList(e.getMessage())
            );
        }
    }
}

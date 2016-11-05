package com.codeforces.jrun;

import junit.framework.TestCase;

import java.io.*;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class ProcessRunnerTest extends TestCase {
    public void testPing() {
        Outcome outcome = ProcessRunner.run("ping",
                new Params.Builder().setDirectory(new File(".")).newInstance());

        assertTrue(outcome.getExitCode() > 0);

        if (isWindows()) {
            assertTrue(outcome.getOutput().trim().startsWith("Usage: ping "));
        } else {
            assertTrue(outcome.getError().trim().startsWith("Usage: ping "));
        }
    }

    public void testStderrorHandling() {
        Outcome outcome = ProcessRunner.run("timeout aaa",
                new Params.Builder().setDirectory(new File(".")).newInstance());

        assertTrue(outcome.getExitCode() > 0);
        assertTrue(outcome.getError().contains("99999"));
    }

    public void testOutputAndErrorRedirection() throws IOException {
        /* Testing stdout. */
        {
            File tempFile = File.createTempFile(ProcessRunnerTest.class.getName(), "");

            Outcome outcome = ProcessRunner.run("echo 123",
                    new Params.Builder().setDirectory(new File(".")).setRedirectOutputFile(tempFile).newInstance());

            assertTrue(outcome.getExitCode() == 0);
            assertTrue(outcome.getOutput().isEmpty());
            assertTrue(tempFile.isFile());
            assertTrue(readFile(tempFile).equals("123\n"));
            tempFile.delete();
        }

        /* Testing stderr. */
        {
            File tempFile = File.createTempFile(ProcessRunnerTest.class.getName(), "");

            Outcome outcome = ProcessRunner.run("timeout aaa",
                    new Params.Builder().setDirectory(new File(".")).setRedirectErrorFile(tempFile).newInstance());

            assertTrue(outcome.getExitCode() > 0);
            assertTrue(outcome.getError().isEmpty());
            assertTrue(tempFile.isFile());
            assertTrue(readFile(tempFile).contains("99999"));
            tempFile.delete();
        }
    }

    public void testExtremeIo() throws IOException {
        String grayProgramBinaryFileName = "gray";
        if (isWindows()) {
            grayProgramBinaryFileName += ".exe";
        }

        File grayFile = File.createTempFile(ProcessRunnerTest.class.getName(), grayProgramBinaryFileName);
        InputStream inputStream = ProcessRunnerTest.class.getResourceAsStream("/" + grayProgramBinaryFileName);
        writeInputStreamToFile(inputStream, grayFile);
        grayFile.setExecutable(true);

        {
            File outputFile = File.createTempFile(ProcessRunnerTest.class.getName(), "output.txt");
            File errorFile = File.createTempFile(ProcessRunnerTest.class.getName(), "error.txt");
            Outcome outcome = ProcessRunner.run(grayFile.getCanonicalPath() + " 0 26 27", new Params.Builder()
                    .setDirectory(grayFile.getParentFile())
                    .setRedirectOutputFile(outputFile)
                    .setRedirectErrorFile(errorFile)
                    .newInstance());
            assertTrue(outcome.getExitCode() == 0);
            assertEquals(134217727, outputFile.length());
            assertEquals(getGrayString(26), readFile(outputFile));
            assertEquals(268435455, errorFile.length());
            assertEquals(getGrayString(27), readFile(errorFile));
            outputFile.delete();
            errorFile.delete();
        }

        grayFile.delete();
    }

    private void assertFilesAreEqual(File a, File b) throws IOException {
        InputStream inputStreamA = new BufferedInputStream(new FileInputStream(a));
        InputStream inputStreamB = new BufferedInputStream(new FileInputStream(b));

        while (true) {
            int byteA = inputStreamA.read();
            int byteB = inputStreamB.read();

            assertEquals(byteA, byteB);

            if (byteA == -1) {
                break;
            }
        }

        inputStreamA.close();
        inputStreamB.close();
    }

    public void testInputRedirection() throws IOException {
        String redirectProgramBinaryFileName = "redirect2";
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            redirectProgramBinaryFileName += ".exe";
        }

        File redirectFile = File.createTempFile(ProcessRunnerTest.class.getName(), redirectProgramBinaryFileName);
        InputStream inputStream = ProcessRunnerTest.class.getResourceAsStream("/" + redirectProgramBinaryFileName);
        writeInputStreamToFile(inputStream, redirectFile);
        redirectFile.setExecutable(true);

        {
            Random random = new Random();
            File inputFile = File.createTempFile(ProcessRunnerTest.class.getName(), "input.txt");
            OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(inputFile));
            final int INPUT_SIZE = 123456789;
            for (int i = 0; i < INPUT_SIZE; i++) {
                outputStream.write(33 + random.nextInt(50));
            }
            outputStream.close();

            File outputFile = File.createTempFile(ProcessRunnerTest.class.getName(), "output.txt");
            File errorFile = File.createTempFile(ProcessRunnerTest.class.getName(), "error.txt");

            Outcome outcome = ProcessRunner.run(redirectFile.getCanonicalPath(), new Params.Builder()
                    .setDirectory(redirectFile.getParentFile())
                    .setRedirectInputFile(inputFile)
                    .setRedirectOutputFile(outputFile)
                    .setRedirectErrorFile(errorFile)
                    .newInstance());

            assertTrue(outcome.getExitCode() == 0);
            assertEquals(INPUT_SIZE, outputFile.length());
            assertEquals(INPUT_SIZE, errorFile.length());

            assertFilesAreEqual(inputFile, outputFile);
            assertFilesAreEqual(inputFile, errorFile);

            inputFile.delete();
            outputFile.delete();
            errorFile.delete();
        }

        redirectFile.delete();
    }

    public void testExtreme() throws IOException {
        String grayProgramBinaryFileName = "gray";
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            grayProgramBinaryFileName += ".exe";
        }

        File grayFile = File.createTempFile(ProcessRunnerTest.class.getName(), grayProgramBinaryFileName);
        InputStream inputStream = ProcessRunnerTest.class.getResourceAsStream("/" + grayProgramBinaryFileName);
        writeInputStreamToFile(inputStream, grayFile);
        grayFile.setExecutable(true);

        {
            Outcome outcome = ProcessRunner.run(grayFile.getCanonicalPath(), new Params.Builder()
                    .setDirectory(grayFile.getParentFile()
                    ).newInstance());
            assertTrue(outcome.getExitCode() == 1);
        }

        {
            Outcome outcome = ProcessRunner.run(grayFile.getCanonicalPath() + " 16 20 20", new Params.Builder()
                    .setDirectory(grayFile.getParentFile()
                    ).newInstance());
            assertTrue(outcome.getExitCode() == 0);
            assertTrue(outcome.getOutput().length() == 2097151);
            assertEquals(getGrayString(20), outcome.getOutput());
            assertTrue(outcome.getError().length() == 2097151);
            assertEquals(getGrayString(20), outcome.getError());
        }

        {
            Outcome outcome = ProcessRunner.run(grayFile.getCanonicalPath() + " 16 20 20", new Params.Builder()
                    .setDirectory(grayFile.getParentFile())
                    .setTimeLimit(500)
                    .newInstance());
            assertTrue(outcome.getExitCode() == -1);
            assertEquals("Process timed out [timeLimit=500]", outcome.getComment());
        }

        {
            Outcome outcome = ProcessRunner.run(grayFile.getCanonicalPath() + " 0 25 25", new Params.Builder()
                    .setDirectory(grayFile.getParentFile())
                    .newInstance());
            assertTrue(outcome.getExitCode() == 0);
            assertEquals(5242880, outcome.getOutput().length());
            assertEquals(5242880, outcome.getError().length());
        }

        {
            Outcome outcome = ProcessRunner.run(grayFile.getCanonicalPath() + " 0 10 25", new Params.Builder()
                    .setDirectory(grayFile.getParentFile())
                    .newInstance());
            assertTrue(outcome.getExitCode() == 0);
            assertEquals(2047, outcome.getOutput().length());
            assertEquals(5242880, outcome.getError().length());
        }

        {
            Outcome outcome = ProcessRunner.run(grayFile.getCanonicalPath() + " 0 25 10", new Params.Builder()
                    .setDirectory(grayFile.getParentFile())
                    .newInstance());
            assertTrue(outcome.getExitCode() == 0);
            assertEquals(5242880, outcome.getOutput().length());
            assertEquals(2047, outcome.getError().length());
        }

        grayFile.delete();
    }

    private void writeInputStreamToFile(InputStream inputStream, File grayFile) throws IOException {
        OutputStream outputStream = new FileOutputStream(grayFile);
        byte[] buffer = new byte[1024 * 1024];
        while (true) {
            int readByteCount = inputStream.read(buffer);
            if (readByteCount == -1) {
                break;
            }
            outputStream.write(buffer, 0, readByteCount);
        }
        inputStream.close();
        outputStream.close();
    }

    public void testUnexistedFile() {
        Outcome outcome = ProcessRunner.run("someunexistingfile",
                new Params.Builder().setDirectory(new File(".")).newInstance());
        assertTrue(outcome.getExitCode() == -1);
    }

    public void testStress() {
        for (int round = 1; round <= 15; round++) {
            long start = System.currentTimeMillis();
            for (int i = 0; i < 100; i++) {
                testPing();
            }
            System.out.println("[Round #" + round + "]: Passed " + (System.currentTimeMillis() - start) + " ms.");
        }
    }

    public void testStressConcurrent() throws InterruptedException {
        final int MUL = 4;

        ExecutorService executorService = Executors.newFixedThreadPool(MUL);

        for (int round = 1; round <= 3 * MUL; round++) {
            System.out.println("[Round #" + round + "]: Scheduling.");
            final int finalRound = round;
            executorService.submit(new Runnable() {
                public void run() {
                    System.out.println("[Round #" + finalRound + "]: Started.");
                    long start = System.currentTimeMillis();
                    for (int i = 0; i < 40 * MUL; i++) {
                        testPing();
                    }
                    System.out.println("[Round #" + finalRound + "]: Passed " + (System.currentTimeMillis() - start) + " ms.");
                }
            });
        }

        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.DAYS);
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private String readFile(File file) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[65536];

        while (true) {
            int readCount = reader.read(buffer);
            if (readCount == -1) {
                break;
            }

            result.append(buffer, 0, readCount);
        }

        reader.close();
        return result.toString();
    }

    private String getGrayString(int n) {
        String current = "";

        for (char c = 'a'; c <= 'a' + n; c++) {
            current = current + c + current;
        }

        return current;
    }
}

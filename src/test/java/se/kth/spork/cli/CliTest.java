package se.kth.spork.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import kotlin.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import picocli.CommandLine;
import se.kth.spork.Util;
import se.kth.spork.exception.MergeException;
import se.kth.spork.spoon.Parser;
import se.kth.spork.spoon.Spoon3dmMerge;
import spoon.reflect.declaration.CtModule;

class CliTest {

    @ParameterizedTest
    @ArgumentsSource(Util.ConflictSourceProvider.class)
    void merge_shouldExitNonZero_onConflict(Util.TestSources sources) {
        String[] args = {
            sources.left.toString(), sources.base.toString(), sources.right.toString()
        };

        int exitCode = new CommandLine(new Cli.Merge()).execute(args);

        assertTrue(exitCode > 0);
    }

    @ParameterizedTest
    @ArgumentsSource(Util.BothModifiedSourceProvider.class)
    void merge_shouldExitZero_onCleanMerge(Util.TestSources sources) {
        String[] args = {
            sources.left.toString(), sources.base.toString(), sources.right.toString()
        };

        int exitCode = new CommandLine(new Cli.Merge()).execute(args);

        assertEquals(0, exitCode);
    }

    @Test
    void merge_shouldThrowOnMissingType_whenGlobalFallbackIsDisabled() {
        Util.TestSources sources =
                Util.TestSources.fromTestDirectoryWithoutExpected(
                        Util.MISSING_TYPE_SCENARIO.toFile());

        assertThrows(
                MergeException.class,
                () ->
                        Cli.merge(
                                sources.base,
                                sources.left,
                                sources.right,
                                /* exitOnError= */ true,
                                /* diff3= */ false),
                "Merge contained no types and global line-based fallback is disabled");
    }

    @Test
    void merge_shouldMexgeCorrectlyOnMissingType_whenGlobalFallbackIsEnabled() {
        Util.TestSources sources =
                Util.TestSources.fromTestDirectory(Util.MISSING_TYPE_SCENARIO.toFile());
        String expected = Parser.INSTANCE.read(sources.expected);

        Pair<String, Integer> merge =
                Cli.merge(
                        sources.base,
                        sources.left,
                        sources.right,
                        /* exitOnError= */ false,
                        /* diff3= */ false);

        assertEquals(0, merge.getSecond());
        assertEquals(expected, merge.getFirst());
    }

    @ParameterizedTest
    @ArgumentsSource(Util.LeftModifiedSourceProvider.class)
    void mergeTreeShouldEqualReParsedPrettyPrint_whenLeftIsModified(
            Util.TestSources sources, @TempDir Path tempDir) throws IOException {
        runTestMerge(sources, tempDir);
    }

    @ParameterizedTest
    @ArgumentsSource(Util.RightModifiedSourceProvider.class)
    void mergeTreeShouldEqualReParsedPrettyPrint_whenRightIsModified(
            Util.TestSources sources, @TempDir Path tempDir) throws IOException {
        runTestMerge(sources, tempDir);
    }

    @ParameterizedTest
    @ArgumentsSource(Util.BothModifiedSourceProvider.class)
    void mergeTreeShouldEqualReParsedPrettyPrent_whenBothRevisionsAreModified(
            Util.TestSources sources, @TempDir Path tempDir) throws IOException {
        runTestMerge(sources, tempDir);
    }

    @ParameterizedTest
    @ArgumentsSource(Util.ConflictSourceProvider.class)
    void prettyPrint_shouldContainConflict(Util.TestSources sources) throws IOException {
        List<Util.Conflict> expectedConflicts = Util.parseConflicts(sources.expected);

        Spoon3dmMerge.INSTANCE.setDiff3(false);
        Pair<CtModule, Integer> merged =
                Spoon3dmMerge.INSTANCE.merge(sources.base, sources.left, sources.right);
        CtModule mergeTree = merged.getFirst();
        Integer numConflicts = merged.getSecond();

        String prettyPrint = Cli.prettyPrint(mergeTree);

        List<Util.Conflict> actualConflicts = Util.parseConflicts(prettyPrint);

        System.out.println(prettyPrint);

        assertEquals(expectedConflicts, actualConflicts);
        assertTrue(numConflicts > 0);
    }

    @ParameterizedTest
    @ArgumentsSource(Util.ConflictSourceProvider.class)
    void prettyPrint_shouldContainConflictDiff3(Util.TestSources sources) throws IOException {
        if (!sources.expectedDiff3.toFile().exists()) {
            return;
        }
        List<Util.Conflict> expectedConflicts = Util.parseConflicts(sources.expectedDiff3);

        Spoon3dmMerge.INSTANCE.setDiff3(true);
        Pair<CtModule, Integer> merged =
                Spoon3dmMerge.INSTANCE.merge(sources.base, sources.left, sources.right);
        CtModule mergeTree = merged.getFirst();
        Integer numConflicts = merged.getSecond();

        String prettyPrint = Cli.prettyPrint(mergeTree);

        List<Util.Conflict> actualConflicts = Util.parseConflicts(prettyPrint);

        System.out.println(prettyPrint);

        assertEquals(expectedConflicts, actualConflicts);
        assertTrue(numConflicts > 0);
    }

    @ParameterizedTest
    @ArgumentsSource(Util.ConflictSourceProvider.class)
    void prettyPrint_shouldParseToExpectedTree_whenConflictHasBeenStrippedOut(
            Util.TestSources sources) throws IOException {
        CtModule expected = Parser.INSTANCE.parse(Util.keepLeftConflict(sources.expected));

        Pair<CtModule, Integer> merged =
                Spoon3dmMerge.INSTANCE.merge(sources.base, sources.left, sources.right);
        CtModule mergeTree = merged.getFirst();

        String prettyPrint = Cli.prettyPrint(mergeTree);
        CtModule actual = Parser.INSTANCE.parse(Util.keepLeftConflict(prettyPrint));

        assertEquals(expected, actual);
    }

    /**
     * Test the CLI by running merging the sources to a merge AST A, pretty printing A to a file and
     * parsing that file into a control tree B. If A and B are equal, no information has been lost
     * in the pretty print as far as Spoon knows (apart from import statements).
     *
     * @param sources
     */
    private static void runTestMerge(Util.TestSources sources, Path tempDir) throws IOException {
        Pair<CtModule, Integer> merged =
                Spoon3dmMerge.INSTANCE.merge(sources.base, sources.left, sources.right);
        CtModule mergeTree = merged.getFirst();

        Object expectedImports = mergeTree.getMetadata(Parser.IMPORT_STATEMENTS);
        assert expectedImports != null;
        Object expectedCuComment = mergeTree.getMetadata(Parser.COMPILATION_UNIT_COMMENT);
        assert expectedCuComment != null;

        String expectedPrettyPrint = Cli.prettyPrint(mergeTree);

        Path outFile = tempDir.resolve("Merge.java");
        Files.write(outFile, expectedPrettyPrint.getBytes(), StandardOpenOption.CREATE);

        CtModule reParsedMerge = Parser.INSTANCE.parse(outFile);
        Object reParsedImports = reParsedMerge.getMetadata(Parser.IMPORT_STATEMENTS);
        Object reparsedCuComment = reParsedMerge.getMetadata(Parser.COMPILATION_UNIT_COMMENT);

        System.out.println(Cli.prettyPrint(mergeTree));
        assertEquals(Cli.prettyPrint(mergeTree), expectedPrettyPrint);

        assertEquals(mergeTree, reParsedMerge);

        assertEquals(reParsedImports, expectedImports);
        assertEquals(reparsedCuComment, expectedCuComment);
    }
}
